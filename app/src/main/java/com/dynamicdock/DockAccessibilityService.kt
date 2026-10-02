package com.dynamicdock

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

class DockAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    private var lastAppPackage: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }

        if (packageName == "com.zte.mifavor.launcher") return
        if (packageName == this.packageName) return
        if (packageName == "com.google.android.inputmethod.latin") return
        if (packageName == "com.android.systemui") return

        if (packageName != lastAppPackage) {
            val previousPackage = lastAppPackage

            if (previousPackage != null) {
                handler.postDelayed({
                    if (lastAppPackage != previousPackage) {
                        DockService.removeDynamicPackage(previousPackage)
                    }
                }, 700)
            }

            lastAppPackage = packageName

            DockService.updateActivePackage(packageName)
        }
    }

    override fun onKeyEvent(event: android.view.KeyEvent): Boolean {
        return false
    }

    override fun onGesture(gestureId: Int): Boolean {
        return false
    }

    override fun onInterrupt() {
    }
}
