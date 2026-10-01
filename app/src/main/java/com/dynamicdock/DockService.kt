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
import android.widget.TextView

class DockService : Service() {

    companion object {

        private var instance: DockService? = null

        fun updateActivePackage(packageName: String) {
            instance?.updatePackage(packageName)
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var appContainer: LinearLayout
    private lateinit var appInfoRepository: AppInfoRepository

    private var dockView: View? = null

    private val registry = RunningAppRegistry()

    override fun onCreate() {
        super.onCreate()

        instance = this

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

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            110,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.BOTTOM

        dockView = appContainer

        windowManager.addView(
            appContainer,
            params
        )
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
        }
    }

    private fun rebuildDock() {

        registry.removeUnavailable(this)

        appContainer.removeAllViews()

        registry.getApps().forEach { app ->

            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(8, 0, 8, 0)

                setOnClickListener {
                    val launchIntent =
                        packageManager.getLaunchIntentForPackage(
                            app.packageName
                        )

                    launchIntent?.let { intent ->
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                    }
                }
            }

            val icon = ImageView(this).apply {
                setImageDrawable(app.icon)
                contentDescription = app.appName
            }

            val iconParams = LinearLayout.LayoutParams(
                52,
                52
            )

            val name = TextView(this).apply {
                text = app.appName
                textSize = 10f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                maxLines = 1
            }

            val nameParams = LinearLayout.LayoutParams(
                80,
                35
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
