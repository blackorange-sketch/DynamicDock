package com.dynamicdock

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ListView
import android.widget.Toast
import java.util.ArrayList

class AppSelectionActivity : Activity() {

    private lateinit var listView: ListView
    private lateinit var adapter: AppListAdapter
    private val selectedApps = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Простий layout без XML для швидкості
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        
        listView = ListView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f
            )
            setDividerHeight(dp(1))
        }
        
        layout.addView(listView)
        setContentView(layout)

        title = "Оберіть додатки"
        
        // Завантажуємо список всіх встановлених додатків
        val allApps = getAllInstalledApps()
        adapter = AppListAdapter(this, allApps)
        listView.adapter = adapter
        
        listView.setOnItemClickListener { _, _, position, _ ->
            val appInfo = allApps[position]
            if (selectedApps.contains(appInfo.packageName)) {
                selectedApps.remove(appInfo.packageName)
            } else {
                selectedApps.add(appInfo.packageName)
            }
            adapter.notifyDataSetChanged()
        }
        
        // Додаємо кнопку ОК внизу
        val okButton = android.widget.Button(this).apply {
            text = "Готово (${selectedApps.size})"
            setOnClickListener {
                finishWithResult(selectedApps)
            }
        }
        layout.addView(okButton)
    }

    private fun finishWithResult(packages: List<String>) {
        val resultIntent = Intent().putExtra("SELECTED_PACKAGES", packages.toTypedArray())
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    private fun getAllInstalledApps(): List<AppItem> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        
        return resolveInfos.mapNotNull { info ->
            try {
                AppItem(
                    packageName = info.activityInfo.packageName,
                    appName = info.loadLabel(pm).toString(),
                    icon = info.loadIcon(pm)
                )
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.appName.lowercase() }
    }

    data class AppItem(val packageName: String, val appName: String, val icon: android.graphics.drawable.Drawable)

    inner class AppListAdapter(context: Activity, items: List<AppItem>) : 
        android.widget.BaseAdapter() {
        
        private val inflater = context.layoutInflater
        private val list = items

        override fun getCount() = list.size
        override fun getItem(position: Int) = list[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup?): View {
            val view = convertView ?: inflater.inflate(android.R.layout.simple_list_item_multiple_choice, parent, false)
            val item = list[position]
            
            val textView = view.findViewById<android.widget.TextView>(android.R.id.text1)
            textView.text = item.appName
            
            val imageView = view.findViewById<android.widget.ImageView>(android.R.id.icon)
            if (imageView != null) {
                imageView.setImageDrawable(item.icon)
            } else {
                // Якщо немає стандартного ImageView, створюємо його вручну або ігноруємо
            }
            
            // Візуалізація вибору
            view.setBackgroundColor(if (selectedApps.contains(item.packageName)) 0x40FFFFFF else 0)
            
            return view
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
