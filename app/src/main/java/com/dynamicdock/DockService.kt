package com.dynamicdock

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.PixelFormat
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class DockService : Service() {

    companion object {
        @Volatile var instance: DockService? = null
        fun updateActivePackage(packageName: String) { instance?.updatePackage(packageName) }
        fun updateVisiblePackages(packages: Set<String>) { instance?.removeMissingDynamicApps(packages) }
        fun removeDynamicPackage(packageName: String) { instance?.removeDynamicPackage(packageName) }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var appContainer: LinearLayout
    private lateinit var registry: RunningAppRegistry
    private lateinit var appInfoRepository: AppInfoRepository
    private lateinit var vibrator: Vibrator
    private lateinit var contextMenu: DockContextMenu
    
    private var activePackageName: String? = null
    private val indicators = mutableMapOf<String, View>()
    
    private var isHidden = false
    private var hideHandle: View? = null
    private var dockWindowAttached = false
    private var hideHandleParams: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoHideRunnable = Runnable { hideDock() }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }

        registry = RunningAppRegistry(this)
        registry.restorePinned(this)
        appInfoRepository = AppInfoRepository(this)
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        
        contextMenu = DockContextMenu(this, windowManager) { action, app ->
            when (action) {
                DockContextMenu.Action.PIN -> registry.pin(app.packageName)
                DockContextMenu.Action.UNPIN -> registry.unpin(app.packageName)
            }
            rebuildDock()
            resetAutoHide()
        }

        setupContainer()
        refreshDock()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)

        android.util.Log.d(
            "DynamicDock",
            "ROTATION: orientation=${newConfig.orientation} " +
                "screen=${newConfig.screenWidthDp}x${newConfig.screenHeightDp} " +
                "metrics=${resources.displayMetrics.widthPixels}x${resources.displayMetrics.heightPixels} " +
                "hidden=$isHidden attached=$dockWindowAttached"
        )

        if (isHidden) {
            handler.post {
                if (!isHidden) return@post

                android.util.Log.d(
                    "DynamicDock",
                    "ROTATION HIDDEN: recreating handle " +
                        "metrics=${resources.displayMetrics.widthPixels}x${resources.displayMetrics.heightPixels}"
                )

                recreateHandle()
            }

            return
        }

        if (!dockWindowAttached) return

        rebuildDock()

        if (!dockWindowAttached || isHidden) return

        applyDockSettingsLayout()
    }

    fun reloadPinnedApps() {
        registry.reloadPinnedApps()
        refreshDock()
    }

    fun refreshDock() {
        appContainer.post {
            rebuildDock()

            if (!dockWindowAttached || isHidden) return@post

            applyDockSettingsLayout()
            appContainer.visibility = View.VISIBLE
        }
    }

    fun updateDockHeight(heightDp: Int) {
        val settings = DockSettings(this)
        settings.dockHeightDp = heightDp

        if (!dockWindowAttached || isHidden) return

        applyDockSettingsLayout()
    }

    fun updateDockPosition(position: String) {
        val settings = DockSettings(this)
        settings.dockPosition = position

        if (!dockWindowAttached || isHidden) {
            return
        }

        appContainer.animate().cancel()
        appContainer.translationX = 0f
        appContainer.translationY = 0f

        rebuildDock()

        applyDockSettingsLayout()
    }

    private fun applyDockSettingsLayout() {
        if (!dockWindowAttached || isHidden) return

        val settings = DockSettings(this)
        val position = settings.dockPosition
        val isVertical = position == "left" || position == "right"

        val params =
            appContainer.layoutParams as? WindowManager.LayoutParams
                ?: return

        appContainer.orientation =
            if (isVertical)
                LinearLayout.VERTICAL
            else
                LinearLayout.HORIZONTAL

        if (isVertical) {
            val width = dp(settings.dockHeightDp)

            appContainer.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )

            val dockHeight = appContainer.measuredHeight
            val screenHeight =
                resources.displayMetrics.heightPixels

            val maxY =
                (screenHeight - dockHeight).coerceAtLeast(0)

            params.width = width
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.gravity =
                if (position == "left")
                    Gravity.TOP or Gravity.LEFT
                else
                    Gravity.TOP or Gravity.RIGHT

            params.y =
                (
                    maxY *
                        settings.verticalPositionPercent.coerceIn(0, 100) /
                        100f
                ).toInt().coerceIn(0, maxY)

        } else {
            appContainer.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(
                    dp(settings.dockHeightDp),
                    View.MeasureSpec.EXACTLY
                )
            )

            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = dp(settings.dockHeightDp)
            params.gravity =
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            params.y = 0
        }

        try {
            windowManager.updateViewLayout(appContainer, params)
        } catch (_: Exception) {
        }
    }

    fun updateVerticalPosition(percent: Int) {
        val settings = DockSettings(this)

        if (
            settings.dockPosition != "left" &&
            settings.dockPosition != "right"
        ) {
            return
        }

        settings.verticalPositionPercent =
            percent.coerceIn(0, 100)

        if (isHidden) {
            recreateHandle()
            return
        }

        if (!dockWindowAttached) return

        applyDockSettingsLayout()
    }

    fun updateIconSize() {
        rebuildDock()

        if (!dockWindowAttached || isHidden) return

        applyDockSettingsLayout()
    }

    fun updatePadding() {
        rebuildDock()

        if (!dockWindowAttached || isHidden) return

        applyDockSettingsLayout()
    }

    fun refreshHideHandle() {
        if (isHidden) {
            recreateHandle()
        }
    }

    private fun setupContainer() {
        val settings = DockSettings(this)
        val isVertical = settings.dockPosition == "left" || settings.dockPosition == "right"

        appContainer = LinearLayout(this).apply {
            orientation = if (isVertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.dock_background)
            visibility = View.INVISIBLE
        }

        val lp = WindowManager.LayoutParams(
            if (isVertical) dp(settings.dockHeightDp) else WindowManager.LayoutParams.WRAP_CONTENT,
            if (isVertical) WindowManager.LayoutParams.WRAP_CONTENT else dp(settings.dockHeightDp),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        
        lp.gravity = when (settings.dockPosition) {
            "left" -> Gravity.TOP or Gravity.LEFT
            "right" -> Gravity.TOP or Gravity.RIGHT
            else -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }

        try {
            windowManager.addView(appContainer, lp)
            dockWindowAttached = true
        } catch (_: Exception) {
            dockWindowAttached = false
        }
    }

    private fun rebuildDock() {
        indicators.clear()
        appContainer.removeAllViews()
        
        val apps = registry.getApps()
        val settings = DockSettings(this)
        val position = settings.dockPosition
        val isVertical = position == "left" || position == "right"

        appContainer.setPadding(
            if (isVertical) 0 else dp(settings.horizontalPaddingDp),
            if (isVertical) dp(settings.verticalPaddingDp) else 0,
            if (isVertical) 0 else dp(settings.horizontalPaddingDp),
            if (isVertical) dp(settings.verticalPaddingDp) else 0
        )

        val closeBtn = TextView(this).apply {
            text = "\u00d7"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(110, 40, 40, 40))
                setStroke(dp(1), Color.argb(160, 255, 255, 255))
            }
            setOnClickListener { hideDock() }
        }
        appContainer.addView(closeBtn, LinearLayout.LayoutParams(dp(28), dp(28)))

        val dragThresholdSquared = run {
            val threshold = dp(10)
            threshold * threshold
        }

        val removeThreshold = dp(80)
        val removeThresholdSquared = removeThreshold * removeThreshold

        val iconSizeDp = settings.iconSizeDp
        val showLabels = settings.showAppLabels
        val iconSizePx = dp(iconSizeDp)
        val indicatorLengthPx = (iconSizePx * 0.8f).toInt()
        val horizontalPaddingPx = dp(settings.horizontalPaddingDp)
        val verticalPaddingPx = dp(settings.verticalPaddingDp)
        val iconContainerWidth =
            if (isVertical) dp(iconSizeDp + 6) else iconSizePx
        val iconContainerHeight =
            if (isVertical) iconSizePx else dp(iconSizeDp + 6)

        apps.forEachIndexed { idx, app ->
            if (idx > 0 && apps[idx-1].pinned && !app.pinned) {
                val sep = View(this).apply { setBackgroundColor(Color.argb(90, 255, 255, 255)) }
                val sepLp = if (isVertical) 
                    LinearLayout.LayoutParams(dp(28), dp(1)).apply { setMargins(0, dp(4), 0, dp(4)) } 
                else 
                    LinearLayout.LayoutParams(dp(1), dp(28)).apply { setMargins(dp(4), 0, dp(4), 0) }
                appContainer.addView(sep, sepLp)
            }

            lateinit var iconImg: ImageView
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                tag = app

                setOnClickListener {
                    showDock()
                    animate().scaleX(0.88f).scaleY(0.88f).setDuration(120).withEndAction {
                        animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    }.start()
                    packageManager.getLaunchIntentForPackage(app.packageName)?.let {
                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(it)
                    }
                }

                setOnLongClickListener {
                    val wasHidden = isHidden
                    showDock()

                    if (vibrator.hasVibrator()) {
                        vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                    }

                    val act = if (app.pinned) DockContextMenu.Action.UNPIN else DockContextMenu.Action.PIN

                    iconImg.postDelayed({
                        contextMenu.show(iconImg, app, act, position)
                    }, if (wasHidden) 260L else 0L)

                    resetAutoHide()
                    true
                }

                setOnTouchListener(object : View.OnTouchListener {
                    private var tStart = 0L
                    private var xStart = 0f
                    private var yStart = 0f
                    private var ox = 0f
                    private var oy = 0f
                    private var dragging = false
                    private var currentPinnedIndex = -1

                    override fun onTouch(v: View, e: MotionEvent): Boolean {
                        when (e.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                tStart = System.currentTimeMillis()
                                xStart = e.rawX
                                yStart = e.rawY
                                ox = v.x
                                oy = v.y
                                dragging = false
                                currentPinnedIndex = -1
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val dx = e.rawX - xStart
                                val dy = e.rawY - yStart
                                val distanceSquared = dx * dx + dy * dy
                                if (
                                    distanceSquared > dragThresholdSquared &&
                                    app.pinned &&
                                    System.currentTimeMillis() - tStart > 300
                                ) {
                                    dragging = true

                                    if (currentPinnedIndex == -1) {
                                        currentPinnedIndex = registry.getPinnedApps()
                                            .indexOfFirst {
                                                it.packageName == app.packageName
                                            }
                                    }

                                    v.translationX = dx
                                    v.translationY = dy
                                    v.alpha = 0.7f
                                    v.scaleX = 1.1f
                                    v.scaleY = 1.1f
                                    reorderPinnedWhileDragging(v, app, e)
                                }
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                if (dragging) {
                                    handleDrop(v, e)
                                } else {
                                    val dx = e.rawX - xStart
                                    val dy = e.rawY - yStart

                                    if (
                                        !app.pinned &&
                                        dx * dx + dy * dy > removeThresholdSquared
                                    ) {
                                        removeDynamicPackage(app.packageName)
                                    } else {
                                        v.performClick()
                                    }
                                }

                                dragging = false
                                currentPinnedIndex = -1
                            }
                        return true
                    }

                    private fun reorderPinnedWhileDragging(
                        dragged: View,
                        draggedApp: RunningApp,
                        e: MotionEvent
                    ) {
                        if (appContainer.indexOfChild(dragged) == -1) return

                        val isVertical =
                            settings.dockPosition == "left" ||
                            settings.dockPosition == "right"

                        val pointer = if (isVertical) e.rawY else e.rawX

                        val pinnedViews = mutableListOf<View>()

                        for (i in 0 until appContainer.childCount) {
                            val child = appContainer.getChildAt(i)

                            if (
                                child !== dragged &&
                                child.tag is RunningApp &&
                                (child.tag as RunningApp).pinned
                            ) {
                                pinnedViews.add(child)
                            }
                        }

                        if (pinnedViews.isEmpty()) return

                        var targetPinnedIndex = 0

                        for (target in pinnedViews) {
                            val loc = IntArray(2)
                            target.getLocationOnScreen(loc)

                            val center =
                                if (isVertical) {
                                    loc[1] + target.height / 2f
                                } else {
                                    loc[0] + target.width / 2f
                                }

                            if (pointer >= center) {
                                targetPinnedIndex++
                            }
                        }

                        if (targetPinnedIndex > currentPinnedIndex) {
                            targetPinnedIndex++
                        }

                        val pinnedCount = pinnedViews.size + 1
                        targetPinnedIndex =
                            targetPinnedIndex.coerceIn(0, pinnedCount - 1)

                        if (currentPinnedIndex == targetPinnedIndex) return

                        val firstPinnedIndex =
                            (0 until appContainer.childCount)
                                .firstOrNull {
                                    val child = appContainer.getChildAt(it)
                                    child.tag is RunningApp &&
                                        (child.tag as RunningApp).pinned
                                }
                                ?: return

                        appContainer.removeView(dragged)

                        val insertIndex =
                            (firstPinnedIndex + targetPinnedIndex)
                                .coerceIn(
                                    firstPinnedIndex,
                                    appContainer.childCount
                                )

                        appContainer.addView(dragged, insertIndex)

                        registry.movePinnedToIndex(
                            draggedApp.packageName,
                            targetPinnedIndex
                        )

                        currentPinnedIndex = targetPinnedIndex
                    }

                    private fun handleDrop(dragged: View, e: MotionEvent) {
                        dragged.animate()
                            .translationX(0f)
                            .translationY(0f)
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(120)
                            .start()
                    }
                })
            }

            iconImg = ImageView(this).apply {
                setImageDrawable(app.icon)
                contentDescription = app.appName
            }
            
            
            val frame = FrameLayout(this)
            frame.addView(iconImg, FrameLayout.LayoutParams(iconSizePx, iconSizePx).apply { gravity = Gravity.CENTER })

            val ind = View(this).apply {
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dp(100).toFloat()
                }
                alpha = if (app.packageName == activePackageName) 1f else 0f
            }
            
            val indLp = if (isVertical) {
                FrameLayout.LayoutParams(dp(3), indicatorLengthPx).apply {
                    gravity = Gravity.CENTER_VERTICAL or (if (position=="left") Gravity.END else Gravity.START)
                }
            } else {
                FrameLayout.LayoutParams(indicatorLengthPx, dp(3)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                }
            }
            frame.addView(ind, indLp)
            indicators[app.packageName] = ind

            item.addView(frame, LinearLayout.LayoutParams(iconContainerWidth, iconContainerHeight))

            if (showLabels) {
                val tv = TextView(this).apply {
                    text = app.appName
                    textSize = 10f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    maxLines = 1
                }
                item.addView(tv, LinearLayout.LayoutParams(dp(40), dp(24)))
            }

            val itemLp = if (isVertical) 
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            else 
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            
            if (isVertical) itemLp.topMargin = verticalPaddingPx else itemLp.leftMargin = horizontalPaddingPx
            
            appContainer.addView(item, itemLp)
        }
    }

    private fun swapAnim(src: View, tgt: View, sApp: RunningApp, tApp: RunningApp) {
        val lS = IntArray(2)
        val lT = IntArray(2)
        src.getLocationInWindow(lS)
        tgt.getLocationInWindow(lT)
        
        val dSX = (lT[0]-lS[0]).toFloat()
        val dSY = (lT[1]-lS[1]).toFloat()
        val dTX = (lS[0]-lT[0]).toFloat()
        val dTY = (lS[1]-lT[1]).toFloat()

        src.animate().translationX(dSX).translationY(dSY).setDuration(250).start()
        tgt.animate().translationX(dTX).translationY(dTY).setDuration(250).withEndAction {
            src.animate().cancel()
            tgt.animate().cancel()
            src.translationX=0f; src.translationY=0f
            tgt.translationX=0f; tgt.translationY=0f
            
            val iS = appContainer.indexOfChild(src)
            val iT = appContainer.indexOfChild(tgt)
            appContainer.removeView(src)
            appContainer.removeView(tgt)
            
            if (iS < iT) {
                appContainer.addView(tgt, iS)
                appContainer.addView(src, iT)
            } else {
                appContainer.addView(src, iT)
                appContainer.addView(tgt, iS)
            }
            
            registry.swapApps(sApp.packageName, tApp.packageName)
        }.start()
    }

    private fun detachDockWindow() {
        if (!dockWindowAttached) return

        try {
            windowManager.removeViewImmediate(appContainer)
        } catch (_: Exception) {
        }

        dockWindowAttached = false
    }

    private fun attachDockWindow(): Boolean {
        if (dockWindowAttached) return true

        val params =
            appContainer.layoutParams
                as? WindowManager.LayoutParams
                ?: return false

        return try {
            windowManager.addView(appContainer, params)
            dockWindowAttached = true
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun hideDock() {
        if (isHidden) return

        appContainer.animate().cancel()

        appContainer.animate()
            .alpha(0f)
            .setDuration(120)
            .withEndAction {
                appContainer.visibility = View.GONE
                detachDockWindow()
                isHidden = true
                createHandle()
            }
            .start()
    }

    private fun configureDockLayout(
        params: WindowManager.LayoutParams
    ) {
        val settings = DockSettings(this)
        val isVertical =
            settings.dockPosition == "left" ||
            settings.dockPosition == "right"

        appContainer.orientation =
            if (isVertical)
                LinearLayout.VERTICAL
            else
                LinearLayout.HORIZONTAL

        if (isVertical) {
            params.width = dp(settings.dockHeightDp)
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.gravity =
                if (settings.dockPosition == "left")
                    Gravity.TOP or Gravity.LEFT
                else
                    Gravity.TOP or Gravity.RIGHT
        } else {
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = dp(settings.dockHeightDp)
            params.gravity =
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            params.y = 0
        }
    }

    private fun showDock() {
        if (!isHidden) {
            resetAutoHide()
            return
        }

        val settings = DockSettings(this)

        hideHandle?.let { handle ->
            try {
                windowManager.removeView(handle)
            } catch (_: Exception) {
            }
        }

        hideHandle = null
        hideHandleParams = null

        val params =
            appContainer.layoutParams
                as? WindowManager.LayoutParams
                ?: return

        configureDockLayout(params)

        if (!attachDockWindow()) {
            createHandle()
            return
        }

        appContainer.visibility = View.INVISIBLE

        appContainer.post {
            if (!dockWindowAttached) return@post

            applyDockSettingsLayout()

            val dockWidth = appContainer.width.toFloat()
            val dockHeight = appContainer.height.toFloat()

            if (dockWidth <= 0f || dockHeight <= 0f) {
                appContainer.scaleX = 1f
                appContainer.scaleY = 1f
                appContainer.alpha = 1f
                appContainer.visibility = View.VISIBLE
                isHidden = false
                scheduleAutoHide()
                return@post
            }

            appContainer.scaleX = 1f
            appContainer.scaleY = 1f
            appContainer.alpha = 0f
            appContainer.visibility = View.VISIBLE
            isHidden = false

            appContainer.animate()
                .alpha(1f)
                .setDuration(160)
                .setInterpolator(
                    android.view.animation
                        .DecelerateInterpolator()
                )
                .withEndAction {
                    appContainer.pivotX = dockWidth / 2f
                    appContainer.pivotY = dockHeight / 2f
                    scheduleAutoHide()
                }
                .start()
        }
    }

    private fun recreateHandle() {
        if (!isHidden) return

        hideHandle?.let { handle ->
            try {
                windowManager.removeViewImmediate(handle)
            } catch (_: Exception) {
            }
        }

        hideHandle = null
        hideHandleParams = null

        createHandle()
    }

    private fun createHandle() {
        val settings = DockSettings(this)

        val isVertical =
            settings.dockPosition == "left" ||
            settings.dockPosition == "right"

        val handleLength =
            dp(settings.hideHandleLengthDp)

        val handleThickness =
            dp(settings.hideHandleThicknessDp)

        val touchSize =
            dp(24)

        val margin =
            dp(settings.hideHandleMarginDp)

        val container =
            FrameLayout(this).apply {
                setBackgroundColor(Color.TRANSPARENT)
                isClickable = true

                setOnClickListener {
                    showDock()
                }
            }

        val visibleHandle =
            View(this).apply {
                background =
                    GradientDrawable().apply {
                        setColor(Color.argb(140, 255, 255, 255))
                        cornerRadius = dp(100).toFloat()
                    }
            }

        if (isVertical) {
            val containerWidth =
                margin + touchSize

            container.addView(
                visibleHandle,
                FrameLayout.LayoutParams(
                    handleThickness,
                    handleLength
                ).apply {
                    gravity =
                        if (settings.dockPosition == "left") {
                            Gravity.START or Gravity.CENTER_VERTICAL
                        } else {
                            Gravity.END or Gravity.CENTER_VERTICAL
                        }

                    if (settings.dockPosition == "left") {
                        leftMargin = margin
                    } else {
                        rightMargin = margin
                    }
                }
            )

            val dockWidth = dp(settings.dockHeightDp)

            appContainer.orientation = LinearLayout.VERTICAL

            appContainer.measure(
                View.MeasureSpec.makeMeasureSpec(
                    dockWidth,
                    View.MeasureSpec.EXACTLY
                ),
                View.MeasureSpec.makeMeasureSpec(
                    0,
                    View.MeasureSpec.UNSPECIFIED
                )
            )

            val dockHeight = appContainer.measuredHeight
            val screenHeight =
                resources.displayMetrics.heightPixels

            val maxY =
                (screenHeight - dockHeight)
                    .coerceAtLeast(0)

            val percent =
                settings.verticalPositionPercent
                    .coerceIn(0, 100)

            val dockY =
                (
                    maxY * percent / 100f
                ).toInt()
                    .coerceIn(0, maxY)

            val handleOffset =
                ((dockHeight - handleLength) / 2)
                    .coerceAtLeast(0)

            val y =
                dockY + handleOffset

            android.util.Log.d(
                "DynamicDock",
                "HANDLE GEOMETRY: screen=$screenHeight dock=$dockHeight maxY=$maxY dockY=$dockY handleY=$y percent=$percent"
            )

            val params =
                WindowManager.LayoutParams(
                    containerWidth,
                    handleLength,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity =
                        if (settings.dockPosition == "right") {
                            Gravity.TOP or Gravity.RIGHT
                        } else {
                            Gravity.TOP or Gravity.LEFT
                        }

                    this.y = y
                }

            hideHandle = container
            hideHandleParams = params

            try {
                windowManager.addView(
                    container,
                    params
                )
            } catch (_: Exception) {
                hideHandle = null
                hideHandleParams = null
                return
            }

        } else {
            val containerHeight =
                margin + touchSize

            container.addView(
                visibleHandle,
                FrameLayout.LayoutParams(
                    handleLength,
                    handleThickness
                ).apply {
                    gravity =
                        Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM

                    bottomMargin = margin
                }
            )

            val params =
                WindowManager.LayoutParams(
                    handleLength,
                    containerHeight,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity =
                        Gravity.BOTTOM or
                            Gravity.CENTER_HORIZONTAL
                }

            hideHandle = container
            hideHandleParams = params

            try {
                windowManager.addView(
                    container,
                    params
                )
            } catch (_: Exception) {
                hideHandle = null
                hideHandleParams = null
                return
            }
        }

        container.alpha = 0f
        container.scaleX = 0.7f
        container.scaleY = 0.7f

        container.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(160)
            .setInterpolator(
                android.view.animation.DecelerateInterpolator()
            )
            .start()
    }

    private fun scheduleAutoHide() {
        handler.removeCallbacks(autoHideRunnable)
        val settings = DockSettings(this)
        if (settings.autoHide) {
            handler.postDelayed(autoHideRunnable, settings.autoHideDelaySeconds * 1000L)
        }
    }

    private fun resetAutoHide() {
        if (!isHidden) {
            scheduleAutoHide()
        }
    }

    private fun updateActiveIndicator(packageName: String?) {
        if (activePackageName == packageName) return

        indicators[activePackageName]?.let {
            it.animate().cancel()
            it.alpha = 0f
        }

        indicators[packageName]?.let {
            it.animate().cancel()
            it.alpha = 1f
        }

        activePackageName = packageName
    }

    private fun updatePackage(pkg: String) {
        if (
            pkg == packageName ||
            pkg == "com.android.systemui" ||
            pkg == "com.google.android.inputmethod.latin" ||
            pkg == "com.google.android.googlequicksearchbox" ||
            pkg == "com.zte.mifavor.launcher" ||
            pkg == "com.google.android.packageinstaller" ||
            pkg == "com.google.android.permissioncontroller"
        ) return
        val info = appInfoRepository.getAppInfo(pkg)
        val dockOrderChanged =
            registry.activate(RunningApp(info.packageName, info.appName, info.icon))

        appContainer.post {
            if (dockOrderChanged) {
                rebuildDock()
            } else {
                updateActiveIndicator(pkg)
            }

            scheduleAutoHide()
        }
    }

    private fun removeDynamicPackage(pkg: String) {
        if (registry.removeDynamic(pkg)) {
            appContainer.post {
                rebuildDock()
                scheduleAutoHide()
            }
        }
    }

    private fun removeMissingDynamicApps(pkgs: Set<String>) {
        if (registry.removeMissingDynamicApps(pkgs)) {
            appContainer.post {
                rebuildDock()
                scheduleAutoHide()
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        instance = null
        handler.removeCallbacks(autoHideRunnable)

        if (dockWindowAttached) {
            try {
                windowManager.removeView(appContainer)
            } catch (_: Exception) {
            }
            dockWindowAttached = false
        }

        hideHandle?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }

        hideHandle = null
        hideHandleParams = null

        super.onDestroy()
    }
}
