package com.dynamicdock

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class DockAccessibilityService : AccessibilityService() {

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
    }

    override fun onInterrupt() {
    }
}
