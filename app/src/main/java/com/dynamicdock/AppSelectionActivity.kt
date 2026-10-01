package com.dynamicdock

import android.app.Activity
import android.os.Bundle
import android.content.pm.ApplicationInfo
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch

class AppSelectionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val packageManager = packageManager

        val apps = packageManager
            .getInstalledApplications(0)
            .filter {
                it.flags and ApplicationInfo.FLAG_SYSTEM == 0
            }
            .sortedBy {
                packageManager
                    .getApplicationLabel(it)
                    .toString()
                    .lowercase()
            }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(20),
                dp(16),
                dp(20),
                dp(32)
            )
        }

        val title = android.widget.TextView(this).apply {
            text = "Програми Dock"
            textSize = 24f
        }

        layout.addView(title)

        for (app in apps) {

            val name =
                packageManager
                    .getApplicationLabel(app)
                    .toString()

            val switch = Switch(this).apply {
                text = name
                textSize = 17f

                setPadding(
                    dp(8),
                    dp(10),
                    dp(8),
                    dp(10)
                )
            }

            layout.addView(
                switch,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        val scrollView = ScrollView(this)
        scrollView.addView(layout)

        setContentView(scrollView)
    }

    private fun dp(value: Int): Int {
        return (
            value *
                resources.displayMetrics.density
        ).toInt()
    }
}
