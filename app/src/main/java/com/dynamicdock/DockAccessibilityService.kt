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
            "type=${event.eventType} " +
                "package=$packageName " +
                "class=${event.className} " +
                "windowId=${event.windowId} " +
                "windowChanges=${event.windowChanges}"
        )

        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            windows?.forEach { window ->
                android.util.Log.d(
                    "DynamicDockWindows",
                    "id=${window.id} " +
                        "type=${window.type} " +
                        "package=${window.root?.packageName} " +
                        "title=${window.title}"
                )
            }
        }

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
