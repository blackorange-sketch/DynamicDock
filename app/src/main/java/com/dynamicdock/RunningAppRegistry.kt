package com.dynamicdock

import android.content.Context

class RunningAppRegistry {

    private val apps = mutableListOf<RunningApp>()

    fun activate(app: RunningApp) {
        apps.removeAll {
            it.packageName == app.packageName
        }

        apps.add(app)
    }

    fun removeUnavailable(context: Context) {
        val packageManager = context.packageManager

        apps.removeAll { app ->
            packageManager.getLaunchIntentForPackage(
                app.packageName
            ) == null
        }
    }

    fun getApps(): List<RunningApp> {
        return apps.toList()
    }

    fun clear() {
        apps.clear()
    }
}
