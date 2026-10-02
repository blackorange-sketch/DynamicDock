package com.dynamicdock

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class DockAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

        if (event == null) {
            return
        }

        val packageName =
            event.packageName?.toString()
                ?: "null"

        android.util.Log.d(
            "DynamicDockA11y",
            "type=${event.eventType} package=$packageName class=${event.className} windowId=${event.windowId}"
        )

        if (
            event.eventType !=
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            return
        }

        DockService.updateActivePackage(
            packageName
        )
    }

    override fun onInterrupt() {
    }
}
