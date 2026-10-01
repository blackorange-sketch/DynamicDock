package com.dynamicdock

import android.app.Service
import android.content.Intent
import android.graphics.Color
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

        params.gravity =
            when (position) {
                "left" ->
                    Gravity.CENTER_VERTICAL or Gravity.LEFT

                "right" ->
                    Gravity.CENTER_VERTICAL or Gravity.RIGHT

                else ->
                    Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            }

        windowManager.updateViewLayout(
            appContainer,
            params
        )

        rebuildDock()
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

    fun updateDockHeight(heightDp: Int) {
        val params = appContainer.layoutParams
            as? WindowManager.LayoutParams
            ?: return

        params.height = dp(heightDp)

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

        appContainer = LinearLayout(this).apply {
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

        val params =
            appContainer.layoutParams
                as? WindowManager.LayoutParams
                ?: return

        params.height = dp(hiddenHeightDp)

        windowManager.updateViewLayout(
            appContainer,
            params
        )

        isDockHidden = true
    }

    private fun showDock() {

        if (!isDockHidden) {
            resetAutoHideTimer()
            return
        }

        val params =
            appContainer.layoutParams
                as? WindowManager.LayoutParams
                ?: return

        params.height = dp(dockHeightDp)

        windowManager.updateViewLayout(
            appContainer,
            params
        )

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
                setPadding(dp(6), 0, dp(6), 0)

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

            val iconParams = LinearLayout.LayoutParams(
                dp(30),
                dp(30)
            )

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

            item.addView(icon, iconParams)
            item.addView(name, nameParams)

            appContainer.addView(item)
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
