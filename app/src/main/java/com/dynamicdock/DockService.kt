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
import kotlin.math.sqrt

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

        appContainer.post {
            if (!dockWindowAttached) return@post

            if (isHidden) {
                createHandle()
            } else {
                rebuildDock()

                appContainer.post {
                    if (!dockWindowAttached || isHidden) return@post
                    applyDockSettingsLayout()
                }
            }
        }
    }

    fun reloadPinnedApps() {
        registry.reloadPinnedApps()
        refreshDock()
    }

    fun refreshDock() {
        appContainer.post {
            rebuildDock()
            adjustPosition()
        }
    }

    fun updateDockHeight(heightDp: Int) {
        val settings = DockSettings(this)
        settings.dockHeightDp = heightDp

        if (!dockWindowAttached || isHidden) return

        appContainer.post {
            if (!dockWindowAttached || isHidden) return@post
            rebuildDock()
            appContainer.post {
                if (!dockWindowAttached || isHidden) return@post
                applyDockSettingsLayout()
            }
        }
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

        appContainer.orientation =
            if (position == "left" || position == "right")
                LinearLayout.VERTICAL
            else
                LinearLayout.HORIZONTAL

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

        if (!dockWindowAttached || isHidden) return

        appContainer.post {
            if (!dockWindowAttached || isHidden) return@post
            applyDockSettingsLayout()
        }
    }

    private fun adjustPosition() {
        if (!dockWindowAttached || isHidden) return

        appContainer.post {
            if (!dockWindowAttached || isHidden) return@post
            applyDockSettingsLayout()
            appContainer.visibility = View.VISIBLE
        }
    }

    fun updateIconSize(sizeDp: Int) {
        rebuildDock()

        if (!dockWindowAttached || isHidden) return

        appContainer.post {
            if (!dockWindowAttached || isHidden) return@post
            applyDockSettingsLayout()
        }
    }

    fun updatePadding() {
        rebuildDock()

        if (!dockWindowAttached || isHidden) return

        appContainer.post {
            if (!dockWindowAttached || isHidden) return@post
            applyDockSettingsLayout()
        }
    }

    fun refreshHideHandle() {
        if (isHidden) {
            createHandle()
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

        windowManager.addView(appContainer, lp)
        dockWindowAttached = true
    }

    private fun rebuildDock() {
        indicators.clear()
        appContainer.removeAllViews()
        
        val apps = registry.getApps()
        val settings = DockSettings(this)
        val isVertical = settings.dockPosition == "left" || settings.dockPosition == "right"

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
                        contextMenu.show(iconImg, app, act, settings.dockPosition)
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

                    override fun onTouch(v: View, e: MotionEvent): Boolean {
                        when (e.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                tStart = System.currentTimeMillis()
                                xStart = e.rawX
                                yStart = e.rawY
                                ox = v.x
                                oy = v.y
                                dragging = false
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val dx = e.rawX - xStart
                                val dy = e.rawY - yStart
                                val dist = sqrt(dx*dx + dy*dy)
                                if (dist > dp(10) && app.pinned && System.currentTimeMillis() - tStart > 300) {
                                    dragging = true
                                    v.x = ox + dx
                                    v.y = oy + dy
                                    v.alpha = 0.7f
                                    v.scaleX = 1.1f
                                    v.scaleY = 1.1f
                                }
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                if (dragging) {
                                    handleDrop(v, e)
                                } else {
                                    val dx = e.rawX - xStart
                                    val dy = e.rawY - yStart
                                    if (!app.pinned && sqrt(dx*dx + dy*dy) > dp(80)) {
                                        removeDynamicPackage(app.packageName)
                                    }
                                }
                                dragging = false
                            }
                        }
                        return false
                    }

                    private fun handleDrop(dragged: View, e: MotionEvent) {
                        var swapped = false
                        for (i in 0 until appContainer.childCount) {
                            val child = appContainer.getChildAt(i)
                            if (child != dragged && child.tag is RunningApp) {
                                val target = child.tag as RunningApp
                                if (target.pinned) {
                                    val loc = IntArray(2)
                                    child.getLocationOnScreen(loc)
                                    if (e.rawX >= loc[0] && e.rawX <= loc[0]+child.width && e.rawY >= loc[1] && e.rawY <= loc[1]+child.height) {
                                        swapAnim(dragged, child, app, target)
                                        swapped = true
                                        break
                                    }
                                }
                            }
                        }
                        if (!swapped) {
                            dragged.animate().x(ox).y(oy).alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start()
                        }
                    }
                })
            }

            iconImg = ImageView(this).apply {
                setImageDrawable(app.icon)
                contentDescription = app.appName
            }
            
            val size = settings.iconSizeDp
            val contW = if (isVertical) dp(size+6) else dp(size)
            val contH = if (isVertical) dp(size) else dp(size+6)
            
            val frame = FrameLayout(this)
            frame.addView(iconImg, FrameLayout.LayoutParams(dp(size), dp(size)).apply { gravity = Gravity.CENTER })

            val ind = View(this).apply {
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dp(100).toFloat()
                }
                alpha = if (app.packageName == activePackageName) 1f else 0f
            }
            
            val indLp = if (isVertical) {
                FrameLayout.LayoutParams(dp(3), dp((size*0.8f).toInt())).apply {
                    gravity = Gravity.CENTER_VERTICAL or (if (settings.dockPosition=="left") Gravity.END else Gravity.START)
                }
            } else {
                FrameLayout.LayoutParams(dp((size*0.8f).toInt()), dp(3)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                }
            }
            frame.addView(ind, indLp)
            indicators[app.packageName] = ind

            item.addView(frame, LinearLayout.LayoutParams(contW, contH))

            if (settings.showAppLabels) {
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
            
            if (isVertical) itemLp.topMargin = dp(settings.verticalPaddingDp) else itemLp.leftMargin = dp(settings.horizontalPaddingDp)
            
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
        position: String,
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

        configureDockLayout(settings.dockPosition, params)

        if (!attachDockWindow()) {
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

            val handleLength =
                dp(settings.hideHandleLengthDp).toFloat()

            val handleThickness =
                dp(settings.hideHandleThicknessDp).toFloat()

            val isVertical =
                settings.dockPosition == "left" ||
                settings.dockPosition == "right"

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

    private fun createHandle() {
        hideHandle?.let { handle ->
            try {
                windowManager.removeView(handle)
            } catch (_: Exception) {
            }
        }

        val settings = DockSettings(this)

        val isVertical =
            settings.dockPosition == "left" ||
            settings.dockPosition == "right"

        val handleLength =
            dp(settings.hideHandleLengthDp)

        val handleThickness =
            dp(settings.hideHandleThicknessDp)

        val touchSize = dp(24)

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

            val visibleParams =
                FrameLayout.LayoutParams(
                    handleThickness,
                    handleLength
                ).apply {
                    gravity =
                        if (settings.dockPosition == "left") {
                            Gravity.START or
                                Gravity.CENTER_VERTICAL
                        } else {
                            Gravity.END or
                                Gravity.CENTER_VERTICAL
                        }

                    if (settings.dockPosition == "left") {
                        leftMargin = margin
                    } else {
                        rightMargin = margin
                    }
                }

            container.addView(
                visibleHandle,
                visibleParams
            )

            hideHandle = container

            val handleParams =
                WindowManager.LayoutParams(
                    containerWidth,
                    handleLength,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity =
                        if (settings.dockPosition == "right") {
                            Gravity.TOP or Gravity.RIGHT
                        } else {
                            Gravity.TOP or Gravity.LEFT
                        }

                    val screenHeight =
                        resources.displayMetrics.heightPixels

                    val maxY =
                        (screenHeight - handleLength)
                            .coerceAtLeast(0)

                    y =
                        (
                            maxY *
                                settings.verticalPositionPercent
                                    .coerceIn(0, 100) /
                                100f
                        ).toInt()
                }

            hideHandleParams = handleParams

            windowManager.addView(
                container,
                handleParams
            )

            container.alpha = 0f
            container.scaleX = 0.7f
            container.scaleY = 0.7f

            container.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(160)
                .setInterpolator(
                    android.view.animation
                        .DecelerateInterpolator()
                )
                .start()

        } else {
            val containerHeight =
                margin + touchSize

            val visibleParams =
                FrameLayout.LayoutParams(
                    handleLength,
                    handleThickness
                ).apply {
                    gravity =
                        Gravity.CENTER_HORIZONTAL or
                            Gravity.BOTTOM

                    bottomMargin = margin
                }

            container.addView(
                visibleHandle,
                visibleParams
            )

            hideHandle = container

            val handleParams =
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

            hideHandleParams = handleParams

            windowManager.addView(
                container,
                handleParams
            )

            container.alpha = 0f
            container.scaleX = 0.7f
            container.scaleY = 0.7f

            container.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(160)
                .setInterpolator(
                    android.view.animation
                        .DecelerateInterpolator()
                )
                .start()
        }
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
        activePackageName = pkg
        val info = AppInfoRepository(this).getAppInfo(pkg)
        registry.activate(RunningApp(info.packageName, info.appName, info.icon))
        appContainer.post {
            rebuildDock()
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
