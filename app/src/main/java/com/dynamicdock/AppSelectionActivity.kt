package com.dynamicdock

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ListView
import android.widget.LinearLayout
import android.widget.Button
import android.widget.TextView
import android.graphics.Color
import android.graphics.drawable.Drawable
import org.json.JSONArray

class AppSelectionActivity : Activity() {

    private lateinit var listView: ListView
    private lateinit var adapter: AppListAdapter
    private val selectedApps = mutableSetOf<String>()
    private lateinit var allApps: List<AppItem>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = "Оберіть додатки"

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }

        listView = ListView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setDividerHeight(dp(1))
        }

        layout.addView(listView)

        val okButton = Button(this).apply {
            text = "Готово"
        }

        layout.addView(
            okButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(layout)

        selectedApps.addAll(loadPinnedPackages())

        allApps = getAllInstalledApps()
        adapter = AppListAdapter(allApps)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val packageName = allApps[position].packageName

            if (selectedApps.contains(packageName)) {
                selectedApps.remove(packageName)
            } else {
                selectedApps.add(packageName)
            }

            adapter.notifyDataSetChanged()
            okButton.text = "Готово (${selectedApps.size})"
        }

        okButton.text = "Готово (${selectedApps.size})"

        okButton.setOnClickListener {
            finishWithResult()
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
        val icon: Drawable
    )

    inner class AppListAdapter(
        private val list: List<AppItem>
    ) : android.widget.BaseAdapter() {

        private val inflater = layoutInflater

        override fun getCount(): Int = list.size

        override fun getItem(position: Int): AppItem = list[position]

        override fun getItemId(position: Int): Long =
            position.toLong()

        override fun getView(
            position: Int,
            convertView: View?,
            parent: android.view.ViewGroup?
        ): View {
            val view = convertView ?: inflater.inflate(
                android.R.layout.simple_list_item_multiple_choice,
                parent,
                false
            )

            val item = list[position]

            val textView =
                view.findViewById<TextView>(android.R.id.text1)

            textView.text = item.appName
            textView.setTextColor(Color.WHITE)

            val imageView =
                view.findViewById<android.widget.ImageView>(
                    android.R.id.icon
                )

            imageView?.setImageDrawable(item.icon)

            view.setBackgroundColor(
                if (selectedApps.contains(item.packageName)) {
                    0x40FFFFFF
                } else {
                    Color.TRANSPARENT
                }
            )

            return view
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
