package com.dynamicdock

class RunningAppRegistry {

    private val apps = mutableListOf<RunningApp>()

    fun activate(app: RunningApp) {
        apps.removeAll {
            it.packageName == app.packageName
        }

        apps.add(app)
    }

    fun getApps(): List<RunningApp> {
        return apps.toList()
    }

    fun clear() {
        apps.clear()
    }
}
