package com.dynamicdock

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class DockAccessibilityService : AccessibilityService() {

    private var lastAppPackage: String? = null

    override fun onAccessibilityEvent(
        event: AccessibilityEvent?
    ) {
        if (event == null) {
            return
        }

        if (
            event.eventType !=
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            return
        }

        val packageName =
            event.packageName?.toString()
                ?: return

        if (packageName == this.packageName) {
            return
        }

        if (
            packageName ==
            "com.google.android.inputmethod.latin"
        ) {
            return
        }

        if (
            packageName ==
            "com.android.systemui"
        ) {
            return
        }

        if (
            packageName ==
            "com.zte.mifavor.launcher"
        ) {
            android.util.Log.d(
                "DynamicDockBack",
                "Launcher/Home detected"
            )

            return
        }

        if (
            packageName ==
            "com.google.android.googlequicksearchbox"
        ) {
            return
        }

        if (
            packageName ==
            "com.google.android.packageinstaller" ||
            packageName ==
            "com.google.android.permissioncontroller"
        ) {
            return
        }

        if (packageName == lastAppPackage) {
            return
        }

        android.util.Log.d(
            "DynamicDockA11y",
            "Active app: $packageName"
        )

        lastAppPackage = packageName

        DockService.updateActivePackage(
            packageName
        )
    }

    override fun onKeyEvent(
        event: android.view.KeyEvent
    ): Boolean {
        return false
    }

    override fun onGesture(
        gestureId: Int
    ): Boolean {
        return false
    }

    override fun onInterrupt() {
    }
}
