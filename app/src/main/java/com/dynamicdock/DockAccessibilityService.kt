package com.dynamicdock

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class DockAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "DynamicDock"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

        if (
            event?.eventType !=
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            return
        }

        val packageName =
            event.packageName?.toString()
                ?: return

        Log.d(
            TAG,
            "WINDOW: $packageName"
        )

        DockService.updateActivePackage(
            packageName
        )
    }

    override fun onInterrupt() {
    }
}
