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
            val root =
                rootInActiveWindow

            android.util.Log.d(
                "DynamicDockRecents",
                "Recents root package=${root?.packageName} " +
                    "class=${root?.className}"
            )

            fun dumpNode(
                node: android.view.accessibility.AccessibilityNodeInfo?,
                depth: Int = 0
            ) {
                if (node == null || depth > 6) {
                    return
                }

                android.util.Log.d(
                    "DynamicDockRecents",
                    "node depth=$depth " +
                        "class=${node.className} " +
                        "package=${node.packageName} " +
                        "text=${node.text} " +
                        "desc=${node.contentDescription}"
                )

                for (i in 0 until node.childCount) {
                    dumpNode(
                        node.getChild(i),
                        depth + 1
                    )
                }
            }

            dumpNode(root)
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
            packageName == "com.zte.mifavor.launcher" &&
            event.className?.toString() == "android.widget.ListView"
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
