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

        val preferences =
            getSharedPreferences(
                "dynamic_dock",
                MODE_PRIVATE
            )

        val registry =
            RunningAppRegistry(this)

        val selectedPackages =
            preferences.getStringSet(
                "selected_packages",
                emptySet()
            ) ?: emptySet()

        val pinnedPackages =
            preferences.getStringSet(
                "pinned_packages",
                emptySet()
            ) ?: emptySet()

        val apps = packageManager
            .getInstalledApplications(0)
            .filter {
                it.flags and ApplicationInfo.FLAG_SYSTEM == 0
            }
            .sortedWith(
                compareByDescending<ApplicationInfo> {
                    pinnedPackages.contains(it.packageName)
                }.thenBy {
                    packageManager
                        .getApplicationLabel(it)
                        .toString()
                        .lowercase()
                }
            )

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
                isChecked =
                    pinnedPackages.contains(app.packageName) ||
                        selectedPackages.contains(app.packageName)

                isEnabled =
                    !pinnedPackages.contains(
                        app.packageName
                    )

                setPadding(
                    dp(8),
                    dp(10),
                    dp(8),
                    dp(10)
                )

                setOnCheckedChangeListener { _, checked ->
                    registry.setSelected(
                        app.packageName,
                        checked
                    )

                    DockService.instance?.refreshDock()
                }
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
