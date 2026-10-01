package com.dynamicdock

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(this).apply {
            text = "Dynamic Dock"
            textSize = 28f
        }

        val overlayButton = Button(this).apply {
            text = "Enable Dock"

            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } else {
                    startService(
                        Intent(this@MainActivity, DockService::class.java)
                    )

                    checkAccessibilityPermission()
                }
            }
        }

        layout.addView(title)
        layout.addView(overlayButton)

        setContentView(layout)

        if (!isAccessibilityEnabled()) {
            checkAccessibilityPermission()
        }
    }

    private fun checkAccessibilityPermission() {
        if (isAccessibilityEnabled()) {
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Увімкнути Dynamic Dock")
            .setMessage(
                "Dynamic Dock потребує дозволу Accessibility " +
                "для визначення активних застосунків та швидкого " +
                "перемикання між ними."
            )
            .setPositiveButton("Увімкнути") { _, _ ->
                startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                )
            }
            .setNegativeButton("Пізніше", null)
            .show()
    }

    private fun isAccessibilityEnabled(): Boolean {
        val manager = getSystemService(ACCESSIBILITY_SERVICE)
                as AccessibilityManager

        val enabledServices =
            manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )

        val expectedComponent = ComponentName(
            this,
            DockAccessibilityService::class.java
        )

        return enabledServices.any { service ->
            service.resolveInfo.serviceInfo.let {
                ComponentName(
                    it.packageName,
                    it.name
                ) == expectedComponent
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (isAccessibilityEnabled()) {
            // Accessibility вже увімкнено.
            // Тут пізніше будемо оновлювати стан Dock.
        }
    }
}
