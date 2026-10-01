package com.dynamicdock

import android.content.Context

class RunningAppRegistry(
    context: Context
) {

    private val apps = mutableListOf<RunningApp>()

    private val preferences =
        context.getSharedPreferences(
            "dynamic_dock",
            Context.MODE_PRIVATE
        )

    private val pinnedPackages =
        preferences.getStringSet(
            "pinned_packages",
            emptySet()
        )?.toMutableSet()
            ?: mutableSetOf()

    private val pinnedOrder =
        preferences.getString(
            "pinned_order",
            ""
        )
            ?.split("|")
            ?.filter { it.isNotBlank() }
            ?.toMutableList()
            ?: mutableListOf()

    fun activate(app: RunningApp) {

        val pinned =
            pinnedPackages.contains(app.packageName)

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

        pinnedPackages.add(packageName)

        if (!pinnedOrder.contains(packageName)) {
            pinnedOrder.add(packageName)
        }

        savePinned()

        val index =
            apps.indexOfFirst {
                it.packageName == packageName
            }

        if (index != -1) {
            apps[index] =
                apps[index].copy(
                    pinned = true
                )
        }
    }

    fun unpin(packageName: String) {

        pinnedPackages.remove(packageName)
        pinnedOrder.remove(packageName)

        savePinned()

        val index =
            apps.indexOfFirst {
                it.packageName == packageName
            }

        if (index != -1) {
            apps[index] =
                apps[index].copy(
                    pinned = false
                )
        }
    }

    private fun savePinned() {
        preferences.edit()
            .putStringSet(
                "pinned_packages",
                pinnedPackages
            )
            .putString(
                "pinned_order",
                pinnedOrder.joinToString("|")
            )
            .apply()
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

    fun restorePinned(context: Context) {

        pinnedPackages.forEach { packageName ->

            if (apps.any { it.packageName == packageName }) {
                return@forEach
            }

            try {
                val packageManager =
                    context.packageManager

                val applicationInfo =
                    packageManager.getApplicationInfo(
                        packageName,
                        0
                    )

                val appName =
                    packageManager
                        .getApplicationLabel(
                            applicationInfo
                        )
                        .toString()

                val icon =
                    packageManager.getApplicationIcon(
                        applicationInfo
                    )

                apps.add(
                    RunningApp(
                        packageName = packageName,
                        appName = appName,
                        icon = icon,
                        pinned = true
                    )
                )

            } catch (e: Exception) {
                // Application is no longer available.
            }
        }
    }

    fun getApps(): List<RunningApp> {
        val pinned = pinnedOrder.mapNotNull { packageName ->
            apps.find { it.packageName == packageName && it.pinned }
        }

        val remainingPinned = apps.filter {
            it.pinned && !pinnedOrder.contains(it.packageName)
        }

        val active = apps.filter { !it.pinned }

        return pinned + remainingPinned + active
    }

    fun clear() {
        apps.clear()
    }
}
