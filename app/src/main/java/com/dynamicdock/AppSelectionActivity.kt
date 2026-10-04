package com.dynamicdock

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

class AppSelectionActivity : Activity() {

    private val selectedApps = linkedSetOf<String>()
    private val allApps = mutableListOf<ApplicationInfo>()

    private lateinit var adapter: AppAdapter

    private val backgroundColor = Color.rgb(18, 18, 18)
    private val rowColor = Color.rgb(30, 30, 30)
    private val primaryTextColor = Color.WHITE
    private val secondaryTextColor = Color.rgb(150, 150, 150)
    private val buttonColor = Color.rgb(45, 45, 45)
    private val accentColor = Color.rgb(100, 180, 255)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val savedPackages = intent
            .getStringArrayExtra("SELECTED_PACKAGES")
            ?.toSet()

        if (savedPackages != null) {
            selectedApps.addAll(savedPackages)
        } else {
            val prefs = getSharedPreferences(
                "dynamic_dock_registry",
                MODE_PRIVATE
            )

            val json = prefs.getString(
                "pinned_apps_json",
                "[]"
            ) ?: "[]"

            try {
                val array = org.json.JSONArray(json)

                for (i in 0 until array.length()) {
                    selectedApps.add(
                        array.getString(i)
                    )
                }
            } catch (e: org.json.JSONException) {
                e.printStackTrace()
            }
        }

        loadApps()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            setPadding(
                dp(16),
                dp(12),
                dp(16),
                dp(12)
            )
        }

        val title = TextView(this).apply {
            text = "Програми Dock"
            textSize = 24f
            setTextColor(primaryTextColor)
            setPadding(0, dp(4), 0, dp(12))
        }

        root.addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val description = TextView(this).apply {
            text = "Виберіть програми, які завжди будуть закріплені в Dock."
            textSize = 14f
            setTextColor(secondaryTextColor)
            setPadding(0, 0, 0, dp(12))
        }

        root.addView(
            description,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val listView = ListView(this).apply {
            divider = null
            dividerHeight = dp(6)
            clipToPadding = false
            setPadding(0, 0, 0, dp(8))
            isClickable = true
            isFocusable = true
        }

        adapter = AppAdapter()
        listView.adapter = adapter

        root.addView(
            listView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val doneButton = Button(this).apply {
            text = "Готово"
            textSize = 16f
            setTextColor(primaryTextColor)
            isAllCaps = false

            background = roundedBackground(
                buttonColor,
                dp(12)
            )

            stateListAnimator = null

            setOnClickListener {
                finishWithResult()
            }
        }

        root.addView(
            doneButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            ).apply {
                topMargin = dp(4)
            }
        )

        setContentView(root)
    }

    private fun loadApps() {
        val pm = packageManager

        allApps.clear()

        val apps = pm.getInstalledApplications(0)
            .filter {
                pm.getLaunchIntentForPackage(it.packageName) != null
            }
            .sortedBy {
                pm.getApplicationLabel(it)
                    .toString()
                    .lowercase()
            }

        allApps.addAll(apps)
    }

    private fun toggleApp(packageName: String) {
        if (selectedApps.contains(packageName)) {
            selectedApps.remove(packageName)
        } else {
            selectedApps.add(packageName)
        }

        adapter.notifyDataSetChanged()
    }

    private fun finishWithResult() {
        val resultIntent = Intent()

        resultIntent.putExtra(
            "SELECTED_PACKAGES",
            selectedApps.toTypedArray()
        )

        setResult(
            Activity.RESULT_OK,
            resultIntent
        )

        finish()
    }

    private fun roundedBackground(
        color: Int,
        radius: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
        }
    }

    private fun dp(value: Int): Int {
        return (
            value * resources.displayMetrics.density
        ).toInt()
    }

    private inner class AppAdapter : BaseAdapter() {

        override fun getCount(): Int {
            return allApps.size
        }

        override fun getItem(position: Int): ApplicationInfo {
            return allApps[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getView(
            position: Int,
            convertView: View?,
            parent: ViewGroup
        ): View {

            val app = allApps[position]
            val pm = packageManager

            val row = LinearLayout(
                this@AppSelectionActivity
            ).apply {

                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL

                setPadding(
                    dp(14),
                    dp(10),
                    dp(10),
                    dp(10)
                )

                background = roundedBackground(
                    rowColor,
                    dp(12)
                )

                isClickable = true
                isFocusable = true

                setOnClickListener {
                    toggleApp(app.packageName)
                }
            }

            val icon = ImageView(
                this@AppSelectionActivity
            ).apply {
                setImageDrawable(
                    pm.getApplicationIcon(app)
                )
            }

            row.addView(
                icon,
                LinearLayout.LayoutParams(
                    dp(42),
                    dp(42)
                ).apply {
                    rightMargin = dp(14)
                }
            )

            val textContainer = LinearLayout(
                this@AppSelectionActivity
            ).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val appName = TextView(
                this@AppSelectionActivity
            ).apply {
                text = pm.getApplicationLabel(app).toString()
                textSize = 16f
                setTextColor(primaryTextColor)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }

            val packageName = TextView(
                this@AppSelectionActivity
            ).apply {
                text = app.packageName
                textSize = 12f
                setTextColor(secondaryTextColor)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }

            textContainer.addView(appName)
            textContainer.addView(packageName)

            row.addView(
                textContainer,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )

            val checkBox = CheckBox(
                this@AppSelectionActivity
            ).apply {

                isChecked =
                    selectedApps.contains(app.packageName)

                isClickable = false
                isFocusable = false

                buttonTintList = ColorStateList(
                    arrayOf(
                        intArrayOf(
                            android.R.attr.state_checked
                        ),
                        intArrayOf()
                    ),
                    intArrayOf(
                        accentColor,
                        Color.rgb(120, 120, 120)
                    )
                )
            }

            row.addView(
                checkBox,
                LinearLayout.LayoutParams(
                    dp(48),
                    dp(48)
                )
            )

            return row
        }
    }
}
