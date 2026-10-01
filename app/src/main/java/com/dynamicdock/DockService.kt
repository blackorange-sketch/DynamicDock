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
    private var dockView: View? = null
    private lateinit var appContainer: LinearLayout

    private val registry = RunningAppRegistry()

    override fun onCreate() {
        super.onCreate()

        instance = this

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager =
            getSystemService(WINDOW_SERVICE) as WindowManager

        appContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(
                Color.argb(220, 30, 30, 30)
            )
            setPadding(16, 0, 16, 0)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            100,
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

        if (packageName == packageNameOfSelf()) {
            return
        }

        registry.activate(packageName)

        appContainer.post {
            rebuildDock()
        }
    }

    private fun rebuildDock() {

        appContainer.removeAllViews()

        registry.getApps().forEach { app ->

            val item = TextView(this).apply {

                text = app.packageName

                textSize = 12f

                setTextColor(Color.WHITE)

                gravity = Gravity.CENTER

                setPadding(
                    24,
                    0,
                    24,
                    0
                )
            }

            appContainer.addView(item)
        }
    }

    private fun packageNameOfSelf(): String {
        return packageName
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
