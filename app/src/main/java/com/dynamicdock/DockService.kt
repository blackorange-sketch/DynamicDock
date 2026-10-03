package com.dynamicdock

import android.app.Service
import android.util.Log
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.PixelFormat
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class DockService : Service() {

    companion object {

        var instance: DockService? = null

        fun updateActivePackage(packageName: String) {
            instance?.updatePackage(packageName)
        }

        fun updateVisiblePackages(packages: Set<String>) {
            instance?.removeMissingDynamicApps(packages)
        }

        fun removeDynamicPackage(packageName: String) {
            instance?.removeDynamicPackage(packageName)
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var appContainer: LinearLayout
    private lateinit var appInfoRepository: AppInfoRepository

    private var dockView: View? = null

    private val hideHandler =
        Handler(Looper.getMainLooper())

    private var isDockHidden = false
    private var dockWindowAttached = false
    private var hideHandle: View? = null
    private var hideHandleParams: WindowManager.LayoutParams? = null

    private var gestureStartX = 0f
    private var gestureStartY = 0f

    private fun handleDockGesture(event: MotionEvent): Boolean {

        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {
                gestureStartX = event.rawX
                gestureStartY = event.rawY
                return false
            }

            MotionEvent.ACTION_UP -> {

                val deltaX =
                    event.rawX - gestureStartX

                val deltaY =
                    event.rawY - gestureStartY

                val threshold = dp(20)

                val settings =
                    DockSettings(this)

                val position =
                    settings.dockPosition

                when (position) {

                    "bottom" -> {
                        if (deltaY > threshold) {
                            hideDock()
                        }
                    }

                    "left" -> {
                        if (deltaX < -threshold) {
                            hideDock()
                        }
                    }

                    "right" -> {
                        if (deltaX > threshold) {
                            hideDock()
                        }
                    }
                }

                return true
            }
        }

        return true
    }


    private inner class DockContainer(
        context: android.content.Context
    ) : LinearLayout(context) {

        private var gestureTriggered = false
        private var touchStartedOnHideButton = false

        override fun onInterceptTouchEvent(
            event: MotionEvent
        ): Boolean {

            when (event.actionMasked) {

                MotionEvent.ACTION_DOWN -> {
                    gestureStartX = event.rawX
                    gestureStartY = event.rawY
                    gestureTriggered = false

                    val hideButton = getChildAt(0)

                    touchStartedOnHideButton =
                        hideButton != null &&
                        event.x >= hideButton.left &&
                        event.x <= hideButton.right &&
                        event.y >= hideButton.top &&
                        event.y <= hideButton.bottom

                    return false
                }

                MotionEvent.ACTION_MOVE -> {

                    val deltaX =
                        event.rawX - gestureStartX

                    val deltaY =
                        event.rawY - gestureStartY

                    val threshold = dp(20)

                    val position =
                        DockSettings(this@DockService).dockPosition

                    val shouldHide =
                        when (position) {
                            "bottom" ->
                                deltaY > threshold

                            "left" ->
                                deltaX < -threshold

                            "right" ->
                                deltaX > threshold

                            else ->
                                false
                        }

                    if (
                        shouldHide &&
                        !gestureTriggered &&
                        !touchStartedOnHideButton
                    ) {
                        gestureTriggered = true
                        hideDock()
                        return true
                    }
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    gestureTriggered = false
                    touchStartedOnHideButton = false
                }
            }

            return false
        }
    }

    private var activePackageName: String? = null

    private var dockHeightDp = 40

    private val hiddenHeightDp = 6

    private val hideRunnable = Runnable {
        hideDock()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    fun updateDockPosition(position: String) {
        val params =
            appContainer.layoutParams
                as? WindowManager.LayoutParams
                ?: return

        val settings = DockSettings(this)

        dockHeightDp =
            settings.dockHeightDp

        val isVertical =
            position == "left" ||
            position == "right"

        appContainer.orientation =
            if (isVertical)
                LinearLayout.VERTICAL
            else
                LinearLayout.HORIZONTAL

        if (isVertical) {
            params.width =
                dp(settings.dockHeightDp)

            params.height =
                WindowManager.LayoutParams.WRAP_CONTENT

            params.gravity =
                if (position == "left") {
                    Gravity.TOP or Gravity.LEFT
                } else {
                    Gravity.TOP or Gravity.RIGHT
                }

            windowManager.updateViewLayout(
                appContainer,
                params
            )

            appContainer.post {
                val screenHeight =
                    resources.displayMetrics.heightPixels

                val dockHeight =
                    appContainer.height

                val maxY =
                    (screenHeight - dockHeight)
                        .coerceAtLeast(0)

                val percent =
                    settings.verticalPositionPercent
                        .coerceIn(0, 100)

                params.y =
                    (maxY * percent / 100f).toInt()

                windowManager.updateViewLayout(
                    appContainer,
                    params
                )
            }
        } else {
            params.width =
                WindowManager.LayoutParams.WRAP_CONTENT

            params.height =
                dp(settings.dockHeightDp)

            params.gravity =
                Gravity.BOTTOM or
                Gravity.CENTER_HORIZONTAL

            params.y = 0

            windowManager.updateViewLayout(
                appContainer,
                params
            )
        }

        rebuildDock()
    }

    fun updateVerticalPosition(percent: Int) {
        val params =
            appContainer.layoutParams
                as? WindowManager.LayoutParams
                ?: return

        val settings =
            DockSettings(this)

        val isVertical =
            settings.dockPosition == "left" ||
            settings.dockPosition == "right"

        if (!isVertical) {
            return
        }

        val screenHeight =
            resources.displayMetrics.heightPixels

        val dockHeight =
            appContainer.height

        val maxY =
            (screenHeight - dockHeight)
                .coerceAtLeast(0)

        val clampedPercent =
            percent.coerceIn(0, 100)

        params.y =
            (maxY * clampedPercent / 100f).toInt()

        windowManager.updateViewLayout(
            appContainer,
            params
        )
    }

    fun updateDockLength(lengthDp: Int) {

        val params =
            appContainer.layoutParams
                as? WindowManager.LayoutParams
                ?: return

        val settings =
            DockSettings(this)

        val isVertical =
            settings.dockPosition == "left" ||
            settings.dockPosition == "right"

        if (isVertical) {
            params.height = dp(lengthDp)
        } else {
            params.width = dp(lengthDp)
        }

        windowManager.updateViewLayout(
            appContainer,
            params
        )
    }

    fun refreshDock() {
        appContainer.post {
            registry.trimDynamicAppsToLimit()
            rebuildDock()

            val params =
                appContainer.layoutParams
                    as? WindowManager.LayoutParams
                    ?: return@post

            val settings = DockSettings(this)

            val isVertical =
                settings.dockPosition == "left" ||
                settings.dockPosition == "right"

            if (isVertical) {
                params.width =
                    dp(settings.dockHeightDp)

                params.height =
                    WindowManager.LayoutParams.WRAP_CONTENT

                params.gravity =
                    if (settings.dockPosition == "left") {
                        Gravity.TOP or Gravity.LEFT
                    } else {
                        Gravity.TOP or Gravity.RIGHT
                    }
            } else {
                params.width =
                    dp(settings.dockLengthDp)

                params.height =
                    dp(settings.dockHeightDp)

                params.y = 0
            }

            windowManager.updateViewLayout(
                appContainer,
                params
            )

            if (isVertical) {
                appContainer.requestLayout()

                appContainer.postOnAnimation {
                    appContainer.postOnAnimation {
                        val currentHeight =
                            appContainer.height

                        val screenHeight =
                            resources.displayMetrics.heightPixels

                        val maxY =
                            (
                                screenHeight -
                                    currentHeight
                            ).coerceAtLeast(0)

                        val percent =
                            settings.verticalPositionPercent
                                .coerceIn(0, 100)

                        params.y =
                            (
                                maxY *
                                    percent /
                                    100f
                            ).toInt()

                        windowManager.updateViewLayout(
                            appContainer,
                            params
                        )
                    }
                }
            }
        }
    }

    fun updateIconSize(sizeDp: Int) {
        refreshDock()
    }

    fun updatePadding() {
        refreshDock()
    }

    fun updateDockHeight(heightDp: Int) {
        val params = appContainer.layoutParams
            as? WindowManager.LayoutParams
            ?: return

        val settings = DockSettings(this)

        val isVertical =
            settings.dockPosition == "left" ||
            settings.dockPosition == "right"

        if (isVertical) {
            params.width = dp(heightDp)
        } else {
            params.height = dp(heightDp)
        }

        windowManager.updateViewLayout(
            appContainer,
            params
        )
    }

    private lateinit var registry: RunningAppRegistry

    override fun onCreate() {
        super.onCreate()


        instance = this

        registry = RunningAppRegistry(this)
        registry.restorePinned(this)

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        appInfoRepository = AppInfoRepository(this)

        windowManager =
            getSystemService(WINDOW_SERVICE) as WindowManager

        val settings = DockSettings(this)

        appContainer = DockContainer(this).apply {
            orientation =
                if (
                    settings.dockPosition == "left" ||
                    settings.dockPosition == "right"
                ) {
                    LinearLayout.VERTICAL
                } else {
                    LinearLayout.HORIZONTAL
                }

            gravity = Gravity.CENTER
            background = getDrawable(R.drawable.dock_background)
        }

        val isVertical =
            settings.dockPosition == "left" ||
            settings.dockPosition == "right"

        val params = WindowManager.LayoutParams(
            if (isVertical)
                dp(settings.dockHeightDp)
            else
                WindowManager.LayoutParams.WRAP_CONTENT,

            if (isVertical)
                WindowManager.LayoutParams.WRAP_CONTENT
            else
                dp(settings.dockHeightDp),

            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity =
            when (settings.dockPosition) {
                "left" ->
                    Gravity.TOP or Gravity.LEFT

                "right" ->
                    Gravity.TOP or Gravity.RIGHT

                else ->
                    Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            }

        dockView = appContainer

        dockHeightDp =
            DockSettings(this).dockHeightDp

        appContainer.setOnClickListener {
            if (isDockHidden) {
                showDock()
            }
        }

        windowManager.addView(
            appContainer,
            params
        )
        dockWindowAttached = true

        refreshDock()
                scheduleAutoHide()
    }

    fun refreshHideHandle() {
        if (isDockHidden) {
            createHideHandle()
        }
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
            windowManager.addView(
                appContainer,
                params
            )
            dockWindowAttached = true
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun hideDock() {
        if (isDockHidden) {
            return
        }

        val settings = DockSettings(this)

        val isVertical =
            settings.dockPosition == "left" ||
            settings.dockPosition == "right"

        appContainer.animate().cancel()

        appContainer.post {
            val dockWidth = appContainer.width.toFloat()
            val dockHeight = appContainer.height.toFloat()

            if (dockWidth <= 0f || dockHeight <= 0f) {
                appContainer.visibility = View.GONE
                detachDockWindow()
                isDockHidden = true
                createHideHandle()
                return@post
            }

            val handleLength =
                dp(settings.hideHandleLengthDp).toFloat()

            val handleThickness =
                dp(settings.hideHandleThicknessDp).toFloat()

            appContainer.pivotX = dockWidth / 2f
            appContainer.pivotY = dockHeight / 2f

            if (isVertical) {
                val targetScaleX =
                    handleThickness / dockWidth

                val targetScaleY =
                    handleLength / dockHeight

                appContainer
                    .animate()
                    .scaleX(targetScaleX)
                    .scaleY(targetScaleY)
                    .alpha(0f)
                    .setDuration(180)
                    .setInterpolator(
                        android.view.animation.AccelerateDecelerateInterpolator()
                    )
                    .withEndAction {
                        appContainer.visibility = View.GONE
                        detachDockWindow()

                        appContainer.alpha = 1f
                        appContainer.scaleX = 1f
                        appContainer.scaleY = 1f

                        isDockHidden = true

                        createHideHandle()
                    }
                    .start()
            } else {
                val targetScaleX =
                    handleLength / dockWidth

                val targetScaleY =
                    handleThickness / dockHeight

                appContainer
                    .animate()
                    .scaleX(targetScaleX)
                    .scaleY(targetScaleY)
                    .alpha(0f)
                    .setDuration(180)
                    .setInterpolator(
                        android.view.animation.AccelerateDecelerateInterpolator()
                    )
                    .withEndAction {
                        appContainer.visibility = View.GONE
                        detachDockWindow()

                        appContainer.alpha = 1f
                        appContainer.scaleX = 1f
                        appContainer.scaleY = 1f

                        isDockHidden = true

                        createHideHandle()
                    }
                    .start()
            }
        }
    }

    private fun createHideHandle() {
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

        val touchSize =
            dp(24)

        val margin =
            dp(settings.hideHandleMarginDp)

        val container =
            android.widget.FrameLayout(this).apply {
                setBackgroundColor(Color.TRANSPARENT)
                isClickable = true

                setOnClickListener {
                    showDock()
                }
            }

        val visibleHandle =
            View(this).apply {
                background =
                    android.graphics.drawable.GradientDrawable().apply {
                        setColor(Color.WHITE)
                        cornerRadius = dp(100).toFloat()
                    }
            }

        if (isVertical) {

            val containerWidth =
                margin + touchSize

            val visibleParams =
                android.widget.FrameLayout.LayoutParams(
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
                      android.view.animation.DecelerateInterpolator()
                  )
                  .start()

        } else {

            val containerHeight =
                margin + touchSize

            val visibleParams =
                android.widget.FrameLayout.LayoutParams(
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
                    android.view.animation.DecelerateInterpolator()
                )
                .start()
        }
    }

    private fun showDock() {
        if (!isDockHidden) {
            resetAutoHideTimer()
            return
        }

        val settings = DockSettings(this)

        val isVertical =
            settings.dockPosition == "left" ||
            settings.dockPosition == "right"

        hideHandle?.let { handle ->
            try {
                windowManager.removeView(handle)
            } catch (_: Exception) {
            }
        }

        hideHandle = null
        hideHandleParams = null

        if (!attachDockWindow()) {
            return
        }

        appContainer.visibility =
            View.INVISIBLE

        appContainer.post {
            val dockWidth =
                appContainer.width.toFloat()

            val dockHeight =
                appContainer.height.toFloat()

            val handleLength =
                dp(settings.hideHandleLengthDp).toFloat()

            val handleThickness =
                dp(settings.hideHandleThicknessDp).toFloat()

            if (dockWidth <= 0f || dockHeight <= 0f) {
                appContainer.scaleX = 1f
                appContainer.scaleY = 1f
                appContainer.pivotX =
                    dockWidth / 2f
                appContainer.pivotY =
                    dockHeight / 2f

                isDockHidden = false
                scheduleAutoHide()
                return@post
            }

            if (isVertical) {
                val pivotX =
                    if (settings.dockPosition == "left") {
                        dp(settings.hideHandleMarginDp).toFloat() +
                            handleThickness / 2f
                    } else {
                        dockWidth -
                            dp(settings.hideHandleMarginDp).toFloat() -
                            handleThickness / 2f
                    }

                appContainer.pivotX =
                    pivotX.coerceIn(
                        0f,
                        dockWidth
                    )

                appContainer.pivotY =
                    dockHeight / 2f

                val startScaleX =
                    (
                        handleThickness /
                            dockWidth
                    ).coerceAtLeast(0.02f)

                val startScaleY =
                    (
                        handleLength /
                            dockHeight
                    ).coerceAtLeast(0.02f)

                appContainer.scaleX =
                    startScaleX

                appContainer.scaleY =
                    startScaleY

                appContainer.visibility =
                    View.VISIBLE

                isDockHidden = false

                appContainer
                    .animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(240)
                    .setInterpolator(
                        android.view.animation.AccelerateDecelerateInterpolator()
                    )
                    .withEndAction {
                        appContainer.pivotX =
                            dockWidth / 2f

                        appContainer.pivotY =
                            dockHeight / 2f

                        scheduleAutoHide()
                    }
                    .start()
            } else {
                appContainer.pivotX =
                    dockWidth / 2f

                appContainer.pivotY =
                    dockHeight -
                        dp(settings.hideHandleMarginDp).toFloat() -
                        handleThickness / 2f

                val startScaleX =
                    (
                        handleLength /
                            dockWidth
                    ).coerceAtLeast(0.02f)

                val startScaleY =
                    (
                        handleThickness /
                            dockHeight
                    ).coerceAtLeast(0.02f)

                appContainer.scaleX =
                    startScaleX

                appContainer.scaleY =
                    startScaleY

                appContainer.visibility =
                    View.VISIBLE

                isDockHidden = false

                appContainer
                    .animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(240)
                    .setInterpolator(
                        android.view.animation.AccelerateDecelerateInterpolator()
                    )
                    .withEndAction {
                        appContainer.pivotX =
                            dockWidth / 2f

                        appContainer.pivotY =
                            dockHeight / 2f

                        scheduleAutoHide()
                    }
                    .start()
            }
        }
    }

    private fun scheduleAutoHide() {

        hideHandler.removeCallbacks(
            hideRunnable
        )

        val settings =
            DockSettings(this)

        if (!settings.autoHide) {
            return
        }

        hideHandler.postDelayed(
            hideRunnable,
            settings.autoHideDelaySeconds * 1000L
        )
    }

    private fun resetAutoHideTimer() {

        if (isDockHidden) {
            return
        }

        scheduleAutoHide()
    }

    private fun updatePackage(packageName: String) {

        if (packageName in setOf(
            "com.dynamicdock",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.zte.zdmdaemon.install"
        )) {
            return
        }

        if (packageName == this.packageName) {
            return
        }

        activePackageName = packageName

        val appInfo =
            appInfoRepository.getAppInfo(packageName)

        val runningApp = RunningApp(
            packageName = appInfo.packageName,
            appName = appInfo.appName,
            icon = appInfo.icon
        )

        registry.activate(runningApp)

        appContainer.post {
            refreshDock()
            scheduleAutoHide()
        }
    }

    private fun removeDynamicPackage(packageName: String) {
        val removed = registry.removeDynamic(packageName)

        if (!removed) {
            return
        }

        appContainer.post {
            refreshDock()
            scheduleAutoHide()
        }
    }

    private fun removeMissingDynamicApps(visiblePackages: Set<String>) {
        val removed = registry.removeMissingDynamicApps(visiblePackages)

        if (!removed) {
            return
        }

        appContainer.post {
            refreshDock()
            scheduleAutoHide()
        }
    }

    private fun rebuildDock() {

        registry.removeUnavailable(this)

        appContainer.removeAllViews()

        val apps = registry.getApps()

        val paddingSettings =
            DockSettings(this)

        val isVerticalDock =
            paddingSettings.dockPosition == "left" ||
            paddingSettings.dockPosition == "right"

        appContainer.setPadding(
            if (isVerticalDock) {
                0
            } else {
                dp(paddingSettings.horizontalPaddingDp)
            },
            if (isVerticalDock) {
                dp(paddingSettings.verticalPaddingDp)
            } else {
                0
            },
            if (isVerticalDock) {
                0
            } else {
                dp(paddingSettings.horizontalPaddingDp)
            },
            if (isVerticalDock) {
                dp(paddingSettings.verticalPaddingDp)
            } else {
                0
            }
        )

        val hideButton = TextView(this).apply {
            text = "×"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)

            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(110, 40, 40, 40))
                setStroke(
                    dp(1),
                    Color.argb(160, 255, 255, 255)
                )
            }

            contentDescription = "Сховати Dock"

            setOnClickListener {
                hideDock()
            }
        }

        val buttonSize = dp(28)

        appContainer.addView(
            hideButton,
            LinearLayout.LayoutParams(
                buttonSize,
                buttonSize
            ).apply {
                setMargins(0, 0, 0, 0)
            }
        )

        apps.forEachIndexed { index, app ->

            if (
                index > 0 &&
                apps[index - 1].pinned &&
                !app.pinned
            ) {
                val separator = View(this).apply {
                    setBackgroundColor(
                        Color.argb(90, 255, 255, 255)
                    )
                }

                val isVertical =
                    paddingSettings.dockPosition == "left" ||
                    paddingSettings.dockPosition == "right"

                val separatorParams =
                    if (isVertical) {
                        LinearLayout.LayoutParams(
                            dp(28),
                            dp(1)
                        ).apply {
                            setMargins(
                                0,
                                dp(4),
                                0,
                                dp(4)
                            )
                        }
                    } else {
                        LinearLayout.LayoutParams(
                            dp(1),
                            dp(28)
                        ).apply {
                            setMargins(
                                dp(4),
                                0,
                                dp(4),
                                0
                            )
                        }
                    }

                appContainer.addView(
                    separator,
                    separatorParams
                )
            }

            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER

                setOnClickListener {

                    showDock()
                    animate()
                        .scaleX(0.88f)
                        .scaleY(0.88f)
                        .setDuration(120)
                        .withEndAction {
                            animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(120)
                                .start()
                        }
                        .start()


                    val launchIntent =
                        packageManager.getLaunchIntentForPackage(
                            app.packageName
                        )

                    launchIntent?.let { intent ->
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                    }
                }

                setOnLongClickListener {

                    showDock()

                    if (app.pinned) {
                        registry.unpin(app.packageName)
                    } else {
                        registry.pin(app.packageName)
                    }

                    val vibrator =
                        getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator

                    if (vibrator.hasVibrator()) {
                        vibrator.vibrate(
                            android.os.VibrationEffect.createOneShot(
                                100,
                                android.os.VibrationEffect.DEFAULT_AMPLITUDE
                            )
                        )
                    }

                    rebuildDock()
                    resetAutoHideTimer()


        setOnTouchListener(object : View.OnTouchListener {
            private var downX = 0f
            private var downY = 0f

            override fun onTouch(
                v: View,
                event: android.view.MotionEvent
            ): Boolean {
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                    }

                    android.view.MotionEvent.ACTION_UP -> {
                        val dx = event.rawX - downX
                        val dy = event.rawY - downY
                        val distance = kotlin.math.sqrt(dx * dx + dy * dy)

                        if (distance > dp(80) && !app.pinned) {
                            removeDynamicPackage(app.packageName)
                            return true
                        }
                    }
                }

                return false
            }
        })
                    true
                }
            }

            val icon = ImageView(this).apply {
                setImageDrawable(app.icon)
                contentDescription = app.appName
            }

            val iconSize =
                paddingSettings.iconSizeDp

            val iconContainer =
                android.widget.FrameLayout(this)

            val containerWidth =
                if (isVerticalDock) {
                    dp(iconSize + 6)
                } else {
                    dp(iconSize)
                }

            val containerHeight =
                if (isVerticalDock) {
                    dp(iconSize)
                } else {
                    dp(iconSize + 6)
                }

            val iconParams =
                android.widget.FrameLayout.LayoutParams(
                    dp(iconSize),
                    dp(iconSize)
                ).apply {
                    gravity = Gravity.CENTER
                }

iconContainer.addView(
                icon,
                iconParams
            )

            if (app.packageName == activePackageName) {
                val indicator = View(this).apply {
                    background =
                        android.graphics.drawable.GradientDrawable().apply {
                            setColor(Color.WHITE)
                            cornerRadius = dp(100).toFloat()
                        }
                }

                val indicatorParams =
                    if (isVerticalDock) {
                        android.widget.FrameLayout.LayoutParams(
                            dp(3),
                            dp((iconSize * 0.8f).toInt())
                        ).apply {
                            gravity =
                                Gravity.CENTER_VERTICAL or
                                    if (paddingSettings.dockPosition == "left") {
                                        Gravity.END
                                    } else {
                                        Gravity.START
                                    }
                        }
                    } else {
                        android.widget.FrameLayout.LayoutParams(
                            dp((iconSize * 0.8f).toInt()),
                            dp(3)
                        ).apply {
                            gravity =
                                Gravity.CENTER_HORIZONTAL or
                                    Gravity.BOTTOM
                        }
                    }

                iconContainer.addView(
                    indicator,
                    indicatorParams
                )
            }

            item.addView(
                iconContainer,
                LinearLayout.LayoutParams(
                    containerWidth,
                    containerHeight
                )
            )



            if (paddingSettings.showAppLabels) {
                val name = TextView(this).apply {
                    text = app.appName
                    textSize = 10f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    maxLines = 1
                }

                val nameParams = LinearLayout.LayoutParams(
                    dp(40),
                    dp(24)
                )

                item.addView(name, nameParams)
            }

            val itemParams =
                if (isVerticalDock) {
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                } else {
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

            if (isVerticalDock) {
            itemParams.topMargin =
                dp(paddingSettings.verticalPaddingDp)
        } else {
            itemParams.leftMargin =
                dp(paddingSettings.horizontalPaddingDp)
        }

        appContainer.addView(
                item,
                itemParams
            )
        }

    }

    override fun onDestroy() {

        instance = null

        if (dockWindowAttached) {
            try {
                windowManager.removeView(appContainer)
            } catch (_: Exception) {
            }
            dockWindowAttached = false
        }

        dockView = null

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

}
