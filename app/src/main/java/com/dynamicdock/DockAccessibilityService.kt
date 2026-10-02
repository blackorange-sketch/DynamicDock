package com.dynamicdock

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

class DockAccessibilityService : AccessibilityService() {

    private val handler =
        Handler(Looper.getMainLooper())

    private val checkLauncherWindows =
        Runnable {
            val visiblePackages =
                windows
                    ?.mapNotNull {
                        it.root?.packageName?.toString()
                    }
                    ?.toSet()
                    ?: emptySet()

            android.util.Log.d(
                "DynamicDockWindows",
                "Launcher check visiblePackages=$visiblePackages"
            )

            DockService.updateVisiblePackages(
                visiblePackages
            )
        }


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

        if (
            packageName == "com.zte.mifavor.launcher" &&
            event.className?.toString() == "android.widget.ListView"
        ) {
            android.util.Log.d(
                "DynamicDockRecents",
                "RECENTS detected"
            )
        }

        if (
            packageName == "com.zte.mifavor.launcher"
        ) {
            handler.removeCallbacks(
                checkLauncherWindows
            )

            handler.postDelayed(
                checkLauncherWindows,
                500
            )
        }

        DockService.updateActivePackage(
            packageName
        )
    }

    override fun onInterrupt() {
    }
}
