package com.dynamicdock

import android.content.Context
import org.json.JSONArray
import org.json.JSONException

class RunningAppRegistry(private val context: Context) {

    private val prefs = context.getSharedPreferences("dynamic_dock_registry", Context.MODE_PRIVATE)
    // Список усіх додатків (закріплені + динамічні)
    private var apps: MutableList<RunningApp> = mutableListOf()

    init {
        loadPinnedApps()
    }

    fun getApps(): List<RunningApp> = apps.toList()

    /**
     * Додає або оновлює динамічний додаток
     */
    fun activate(app: RunningApp) {
        val existingIndex = apps.indexOfFirst { it.packageName == app.packageName }
        
        if (existingIndex != -1) {
            val current = apps[existingIndex]
            if (!current.pinned) {
                // Оновлюємо дані динамічного додатка, переміщуємо на початок
                apps.removeAt(existingIndex)
                apps.add(0, app.copy(pinned = false))
            } else {
                // Закріплений додаток залишається на місці, тільки оновлюємо метадані
                apps[existingIndex] = app.copy(pinned = true)
            }
        } else {
            // Новий динамічний додаток
            apps.add(0, app.copy(pinned = false))
        }
        
        trimDynamicAppsToLimit()
        saveState()
    }

    fun pin(packageName: String) {
        val index = apps.indexOfFirst { it.packageName == packageName }
        if (index != -1) {
            val app = apps[index]
            apps.removeAt(index)
            
            // Знаходимо місце для вставки серед закріплених (на початок списку непотрібних або в кінець закріплених)
            // Логіка: закріплені завжди йдуть першими
            val firstUnpinnedIndex = apps.indexOfFirst { !it.pinned }
            if (firstUnpinnedIndex == -1) {
                apps.add(app.copy(pinned = true))
            } else {
                apps.add(firstUnpinnedIndex, app.copy(pinned = true))
            }
            saveState()
        }
    }

    fun unpin(packageName: String) {
        val index = apps.indexOfFirst { it.packageName == packageName }
        if (index != -1) {
            val app = apps[index]
            apps.removeAt(index)
            // Ставимо в кінець як динамічний
            apps.add(app.copy(pinned = false))
            saveState()
        }
    }

    fun removeDynamic(packageName: String): Boolean {
        val index = apps.indexOfFirst { it.packageName == packageName && !it.pinned }
        if (index != -1) {
            apps.removeAt(index)
            return true
        }
        return false
    }

    fun swapApps(pkg1: String, pkg2: String) {
        val idx1 = apps.indexOfFirst { it.packageName == pkg1 }
        val idx2 = apps.indexOfFirst { it.packageName == pkg2 }
        
        if (idx1 != -1 && idx2 != -1) {
            val temp = apps[idx1]
            apps[idx1] = apps[idx2]
            apps[idx2] = temp
            saveState()
        }
    }

    fun trimDynamicAppsToLimit() {
        val settings = DockSettings(context)
        val limit = settings.maxDynamicApps
        
        var dynamicCount = 0
        for (i in apps.indices.reversed()) {
            if (!apps[i].pinned) {
                dynamicCount++
                if (dynamicCount > limit) {
                    apps.removeAt(i)
                }
            }
        }
    }

    fun removeMissingDynamicApps(visiblePackages: Set<String>) {
        val iterator = apps.iterator()
        while (iterator.hasNext()) {
            val app = iterator.next()
            if (!app.pinned && app.packageName !in visiblePackages) {
                iterator.remove()
            }
        }
    }

    fun restorePinned(serviceContext: Context) {
        loadPinnedApps()
    }

    private fun loadPinnedApps() {
        val jsonStr = prefs.getString("pinned_apps_json", "[]") ?: "[]"
        try {
            val jsonArray = JSONArray(jsonStr)
            val newPinned = mutableListOf<RunningApp>()
            
            for (i in 0 until jsonArray.length()) {
                val pkg = jsonArray.getString(i)
                // Іконку та назву довантажимо пізніше через Repository у Service
                // Тут створюємо заглушку з pinned=true
                newPinned.add(RunningApp(packageName = pkg, appName = "", icon = null, pinned = true))
            }
            
            apps.clear()
            apps.addAll(newPinned)
            
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    private fun saveState() {
        val pinnedPackages = apps.filter { it.pinned }.map { it.packageName }
        val jsonArray = JSONArray(pinnedPackages)
        prefs.edit().putString("pinned_apps_json", jsonArray.toString()).apply()
    }
}
