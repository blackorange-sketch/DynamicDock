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
    private var packageText: TextView? = null

    override fun onCreate() {
        super.onCreate()

        instance = this

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(220, 30, 30, 30))
        }

        packageText = TextView(this).apply {
            text = "Waiting..."
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(24, 0, 24, 0)
        }

        dock.addView(packageText)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            80,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.BOTTOM

        dockView = dock
        windowManager.addView(dock, params)
    }

    private fun updatePackage(packageName: String) {
        packageText?.post {
            packageText?.text = packageName
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

    override fun onBind(intent: Intent?): IBinder? = null
}
