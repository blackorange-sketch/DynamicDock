package com.dynamicdock

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
    }

    private lateinit var windowManager: WindowManager
    private lateinit var appContainer: LinearLayout
    private lateinit var appInfoRepository: AppInfoRepository

    private var dockView: View? = null

    private val hideHandler =
        Handler(Looper.getMainLooper())

    private var isDockHidden = false

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

                val position =
                    DockSettings(this).dockPosition

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

        val settings =
            DockSettings(this)

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

        params.width =
            if (isVertical)
                dp(settings.dockHeightDp)
            else
                dp(settings.dockLengthDp)

        params.height =
            if (isVertical)
                dp(settings.dockLengthDp)
            else
                dp(settings.dockHeightDp)

        if (isVertical) {
            params.gravity =
                if (position == "left") {
                    Gravity.TOP or Gravity.LEFT
                } else {
                    Gravity.TOP or Gravity.RIGHT
                }

            val displayMetrics = resources.displayMetrics
            val screenHeight = displayMetrics.heightPixels

            val dockHeight =
                dp(settings.dockLengthDp)

            val maxY =
                (screenHeight - dockHeight).coerceAtLeast(0)

            val percent =
                settings.verticalPositionPercent.coerceIn(0, 100)

            params.y =
                (maxY * percent / 100f).toInt()
        } else {
            params.gravity =
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL

            params.y = 0
        }

        windowManager.updateViewLayout(
            appContainer,
            params
        )

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
            dp(settings.dockLengthDp)

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
            rebuildDock()
        }
    }

    fun updateIconSize(sizeDp: Int) {
        appContainer.post {
            rebuildDock()
        }
    }

    fun updatePadding() {
        appContainer.post {
            rebuildDock()
        }
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

        appContainer = DockContainer(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = getDrawable(R.drawable.dock_background)

}

        val settings = DockSettings(this)

        val isVertical =
            settings.dockPosition == "left" ||
            settings.dockPosition == "right"

        val params = WindowManager.LayoutParams(
            if (isVertical)
                dp(settings.dockHeightDp)
            else
                dp(settings.dockLengthDp),

            if (isVertical)
                dp(settings.dockLengthDp)
            else
                dp(settings.dockHeightDp),

            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity =
            when (settings.dockPosition) {
                "left" ->
                    Gravity.CENTER_VERTICAL or Gravity.LEFT

                "right" ->
                    Gravity.CENTER_VERTICAL or Gravity.RIGHT

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

        scheduleAutoHide()
    }

    private fun hideDock() {
        if (isDockHidden) {
            return
        }

        appContainer.visibility = View.GONE
        isDockHidden = true
    }

    private fun showDock() {
        if (!isDockHidden) {
            resetAutoHideTimer()
            return
        }

        appContainer.visibility = View.VISIBLE
        isDockHidden = false
        scheduleAutoHide()
    }

    private fun scheduleAutoHide() {

        hideHandler.removeCallbacks(
            hideRunnable
        )

        val settings =
            DockSettings(this)

        if (!settings.reservedSpace) {
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
            rebuildDock()
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
                setMargins(
                    dp(3),
                    dp(3),
                    dp(3),
                    dp(3)
                )
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
                    DockSettings(this).dockPosition == "left" ||
                    DockSettings(this).dockPosition == "right"

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
                    true
                }
            }

            val icon = ImageView(this).apply {
                setImageDrawable(app.icon)
                contentDescription = app.appName
            }

            val iconSize =
                DockSettings(this).iconSizeDp

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
                    gravity =
                        if (isVerticalDock &&
                            paddingSettings.dockPosition == "right") {
                            Gravity.START or
                                Gravity.CENTER_VERTICAL
                        } else {
                            Gravity.START or
                                Gravity.TOP
                        }

                    if (isVerticalDock &&
                        paddingSettings.dockPosition == "right") {
                        leftMargin = dp(3)
                    }
                }

            iconContainer.addView(
                icon,
                iconParams
            )

            if (app.packageName == activePackageName) {
                val indicator = View(this).apply {
                    setBackgroundColor(Color.WHITE)
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

            if (DockSettings(this).showAppLabels) {
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
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )

            if (index > 0) {
                if (isVerticalDock) {
                    itemParams.topMargin =
                        dp(paddingSettings.verticalPaddingDp)
                } else {
                    itemParams.leftMargin =
                        dp(paddingSettings.horizontalPaddingDp)
                }
            }

            appContainer.addView(
                item,
                itemParams
            )
        }

    }

    override fun onDestroy() {

        instance = null

        dockView?.let {
            windowManager.removeView(it)
        }

        dockView = null

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

}
