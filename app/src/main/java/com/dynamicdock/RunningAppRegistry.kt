package com.dynamicdock

import android.content.Context

class RunningAppRegistry {

    private val apps = mutableListOf<RunningApp>()

    fun activate(app: RunningApp) {

        val existing =
            apps.firstOrNull {
                it.packageName == app.packageName
            }

        val pinned =
            existing?.pinned ?: app.pinned

        apps.removeAll {
            it.packageName == app.packageName
        }

        apps.add(
            app.copy(
                pinned = pinned
            )
        )
    }

    fun pin(packageName: String) {

        val index =
            apps.indexOfFirst {
                it.packageName == packageName
            }

        if (index == -1) return

        apps[index] =
            apps[index].copy(
                pinned = true
            )
    }

    fun unpin(packageName: String) {

        val index =
            apps.indexOfFirst {
                it.packageName == packageName
            }

        if (index == -1) return

        apps[index] =
            apps[index].copy(
                pinned = false
            )
    }

    fun removeUnavailable(context: Context) {

        val packageManager =
            context.packageManager

        apps.removeAll { app ->

            if (app.pinned) {
                false
            } else {
                packageManager.getLaunchIntentForPackage(
                    app.packageName
                ) == null
            }
        }
    }

    fun getApps(): List<RunningApp> {
        return apps.toList()
    }

    fun clear() {
        apps.clear()
    }
}
