package com.dynamicdock

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout

class DockService : Service() {

    companion object {

        private var instance: DockService? = null

        fun updateActivePackage(packageName: String) {
            instance?.updatePackage(packageName)
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var appContainer: LinearLayout

    private var dockView: View? = null

    private lateinit var appInfoRepository: AppInfoRepository

    private val registry = RunningAppRegistry()

    override fun onCreate() {
        super.onCreate()

        instance = this

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        appInfoRepository =
            AppInfoRepository(this)

        windowManager =
            getSystemService(WINDOW_SERVICE)
                    as WindowManager

        appContainer = LinearLayout(this).apply {

            orientation =
                LinearLayout.HORIZONTAL

            gravity = Gravity.CENTER

            setBackgroundColor(
                Color.argb(
                    220,
                    30,
                    30,
                    30
                )
            )

            setPadding(
                12,
                8,
                12,
                8
            )
        }

        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                90,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )

        params.gravity =
            Gravity.BOTTOM

        dockView =
            appContainer

        windowManager.addView(
            appContainer,
            params
        )
    }

    private fun updatePackage(
        packageName: String
    ) {

        if (
            packageName ==
            this.packageName
        ) {
            return
        }

        val appInfo =
            appInfoRepository
                .getAppInfo(packageName)
                ?: return

        val runningApp =
            RunningApp(
                packageName =
                    appInfo.packageName,
                appName =
                    appInfo.appName,
                icon =
                    appInfo.icon
            )

        registry.activate(
            runningApp
        )

        appContainer.post {
            rebuildDock()
        }
    }

    private fun rebuildDock() {

        appContainer.removeAllViews()

        registry.getApps().forEach {
            app ->

            val iconView =
                ImageView(this).apply {

                    setImageDrawable(
                        app.icon
                    )

                    contentDescription =
                        app.appName

                    scaleType =
                        ImageView.ScaleType.CENTER_INSIDE

                    setPadding(
                        10,
                        10,
                        10,
                        10
                    )
                }

            val params =
                LinearLayout.LayoutParams(
                    72,
                    72
                )

            params.gravity =
                Gravity.CENTER

            appContainer.addView(
                iconView,
                params
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

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }
}
