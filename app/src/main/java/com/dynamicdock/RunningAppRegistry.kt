package com.dynamicdock

class RunningAppRegistry {

    private val apps = mutableListOf<RunningApp>()

    fun activate(packageName: String) {
        apps.removeAll { it.packageName == packageName }
        apps.add(RunningApp(packageName))
    }

    fun getApps(): List<RunningApp> {
        return apps.toList()
    }

    fun clear() {
        apps.clear()
    }
}
