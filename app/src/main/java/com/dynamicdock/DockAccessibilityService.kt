package com.dynamicdock

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

class DockAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    private var lastAppPackage: String? = null
    private var waitingForNextApp = false
    private var homeDetected = false

    private val removePreviousApp = Runnable {
        if (
            waitingForNextApp &&
            !homeDetected
        ) {
            val previousPackage = lastAppPackage

            if (previousPackage != null) {
                android.util.Log.d(
                    "DynamicDockBack",
                    "Back detected, removing $previousPackage"
                )

                DockService.removeDynamicPackage(
                    previousPackage
                )
            }
        }

        waitingForNextApp = false
        homeDetected = false
    }

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

        if (
            packageName ==
            "com.google.android.inputmethod.latin"
        ) {
            return
        }

        if (packageName == this.packageName) {
            return
        }

        if (packageName == "com.android.systemui") {
            return
        }

        if (packageName == "com.zte.mifavor.launcher") {
            homeDetected = true

            handler.removeCallbacks(
                removePreviousApp
            )

            waitingForNextApp = false

            android.util.Log.d(
                "DynamicDockBack",
                "Home detected"
            )

            return
        }

        if (packageName == "com.google.android.googlequicksearchbox") {
            return
        }

        if (packageName == lastAppPackage) {
            return
        }

        val previousPackage = lastAppPackage

        if (previousPackage != null) {
            handler.removeCallbacks(
                removePreviousApp
            )

            homeDetected = false
            waitingForNextApp = true

            handler.postDelayed(
                removePreviousApp,
                400
            )
        }

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
