package com.dynamicdock

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class DockAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()

        serviceInfo = serviceInfo.apply {
            flags = flags or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }

        Log.d("DynamicDock", 
            "Accessibility connected: interactive windows enabled"
        )
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

        DockService.updateActivePackage(
            packageName
        )

        val windows = windows

        Log.d("DynamicDock", 
            "WINDOWS: count=${windows.size}"
        )

        windows.forEach { window ->

            val bounds =
                android.graphics.Rect()

            window.getBoundsInScreen(bounds)

            Log.d("DynamicDock", 
                "WINDOW: type=${window.type} " +
                "package=${window.root?.packageName} " +
                "bounds=$bounds"
            )
        }
    }

    override fun onInterrupt() {
    }
}
