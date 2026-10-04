package com.dynamicdock

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import org.json.JSONArray

class AppSelectionActivity : Activity() {

    private lateinit var listView: ListView
    private lateinit var adapter: AppListAdapter
    private lateinit var selectedCountText: TextView

    private val selectedApps = linkedSetOf<String>()
    private lateinit var allApps: List<AppItem>

    private val backgroundColor = Color.rgb(18, 18, 18)
    private val cardColor = Color.rgb(30, 30, 30)
    private val selectedColor = Color.rgb(45, 45, 45)
    private val primaryTextColor = Color.WHITE
    private val secondaryTextColor = Color.rgb(170, 170, 170)
    private val accentColor = Color.rgb(80, 140, 255)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        selectedApps.addAll(loadPinnedPackages())
        allApps = getAllInstalledApps()

        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(12))
        }

        val title = TextView(this).apply {
            text = "Програми Dock"
            setTextColor(primaryTextColor)
            textSize = 22f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        selectedCountText = TextView(this).apply {
            setTextColor(secondaryTextColor)
            textSize = 14f
            setPadding(0, dp(5), 0, 0)
        }

        header.addView(title)
        header.addView(selectedCountText)

        root.addView(
            header,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        listView = ListView(this).apply {
            divider = null
            dividerHeight = 0
            clipToPadding = false
            setPadding(dp(10), dp(4), dp(10), dp(8))
            cacheColorHint = Color.TRANSPARENT
        }

        adapter = AppListAdapter(allApps)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            toggleApp(allApps[position].packageName)
        }

        root.addView(
            listView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val bottomBar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(14))
        }

        val doneButton = TextView(this).apply {
            text = "Готово"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(20), dp(12), dp(20), dp(12))

            background = GradientDrawable().apply {
                setColor(accentColor)
                cornerRadius = dp(12).toFloat()
            }

            setOnClickListener {
                finishWithResult()
            }
        }

        bottomBar.addView(
            doneButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            bottomBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(root)

        updateSelectedCount()
    }

    private fun toggleApp(packageName: String) {
        if (selectedApps.contains(packageName)) {
            selectedApps.remove(packageName)
        } else {
            selectedApps.add(packageName)
        }

        adapter.notifyDataSetChanged()
        updateSelectedCount()
    }

    private fun updateSelectedCount() {
        selectedCountText.text =
            if (selectedApps.isEmpty()) {
                "Немає закріплених програм"
            } else {
                "Закріплено: ${selectedApps.size}"
            }
    }

    private fun finishWithResult() {
        val resultIntent = Intent().apply {
            putExtra(
                "SELECTED_PACKAGES",
                selectedApps.toTypedArray()
            )
        }

        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    private fun loadPinnedPackages(): Set<String> {
        val prefs = getSharedPreferences(
            "dynamic_dock_registry",
            MODE_PRIVATE
        )

        val jsonString =
            prefs.getString("pinned_apps_json", "[]") ?: "[]"

        return try {
            val jsonArray = JSONArray(jsonString)

            buildSet {
                for (i in 0 until jsonArray.length()) {
                    add(jsonArray.getString(i))
                }
            }
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun getAllInstalledApps(): List<AppItem> {
        val pm = packageManager

        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = pm.queryIntentActivities(intent, 0)

        return resolveInfos
            .mapNotNull { info ->
                try {
                    AppItem(
                        packageName = info.activityInfo.packageName,
                        appName = info.loadLabel(pm).toString(),
                        icon = info.loadIcon(pm)
                    )
                } catch (e: Exception) {
                    null
                }
            }
            .distinctBy { it.packageName }
            .sortedBy { it.appName.lowercase() }
    }

    data class AppItem(
        val packageName: String,
        val appName: String,
        val icon: android.graphics.drawable.Drawable
    )

    inner class AppListAdapter(
        private val list: List<AppItem>
    ) : BaseAdapter() {

        override fun getCount(): Int = list.size

        override fun getItem(position: Int): AppItem =
            list[position]

        override fun getItemId(position: Int): Long =
            position.toLong()

        override fun getView(
            position: Int,
            convertView: View?,
            parent: ViewGroup?
        ): View {
            val item = list[position]
            val selected = selectedApps.contains(item.packageName)

            val row = convertView as? LinearLayout
                ?: createRow()

            val icon = row.getChildAt(0) as ImageView
            val textContainer = row.getChildAt(1) as LinearLayout
            val name = textContainer.getChildAt(0) as TextView
            val packageText = textContainer.getChildAt(1) as TextView
            val check = row.getChildAt(2) as TextView

            icon.setImageDrawable(item.icon)

            name.text = item.appName
            packageText.text = item.packageName

            if (selected) {
                row.background = createRoundedBackground(selectedColor)
                check.text = "✓"
                check.setTextColor(accentColor)
            } else {
                row.background = createRoundedBackground(cardColor)
                check.text = "○"
                check.setTextColor(secondaryTextColor)
            }

            return row
        }

        private fun createRow(): LinearLayout {
            val row = LinearLayout(this@AppSelectionActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(
                    dp(14),
                    dp(10),
                    dp(14),
                    dp(10)
                )
            }

            val icon = ImageView(this@AppSelectionActivity).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
            }

            row.addView(
                icon,
                LinearLayout.LayoutParams(
                    dp(42),
                    dp(42)
                )
            )

            val textContainer = LinearLayout(this@AppSelectionActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), 0, dp(8), 0)
            }

            val name = TextView(this@AppSelectionActivity).apply {
                setTextColor(primaryTextColor)
                textSize = 16f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }

            val packageText = TextView(this@AppSelectionActivity).apply {
                setTextColor(secondaryTextColor)
                textSize = 11f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, dp(2), 0, 0)
            }

            textContainer.addView(name)
            textContainer.addView(packageText)

            row.addView(
                textContainer,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )

            val check = TextView(this@AppSelectionActivity).apply {
                textSize = 24f
                gravity = Gravity.CENTER
                minWidth = dp(32)
            }

            row.addView(
                check,
                LinearLayout.LayoutParams(
                    dp(36),
                    dp(48)
                )
            )

            val params = AbsListView.LayoutParams(
                AbsListView.LayoutParams.MATCH_PARENT,
                dp(64)
            )

            row.layoutParams = params

            return row
        }
    }

    private fun createRoundedBackground(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(12).toFloat()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
