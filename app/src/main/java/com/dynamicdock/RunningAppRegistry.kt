package com.dynamicdock

import android.content.Context

class RunningAppRegistry(
    context: Context
) {
    private val context = context

    private val apps =
        mutableListOf<RunningApp>()

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

    fun isSelected(packageName: String): Boolean {
        return preferences
            .getStringSet(
                "selected_packages",
                emptySet()
            )
            ?.contains(packageName)
            ?: false
    }

    fun setSelected(
        packageName: String,
        selected: Boolean
    ) {
        val selectedPackages =
            preferences
                .getStringSet(
                    "selected_packages",
                    emptySet()
                )
                ?.toMutableSet()
                ?: mutableSetOf()

        if (selected) {
            selectedPackages.add(packageName)

            if (
                !apps.any {
                    it.packageName == packageName
                }
            ) {
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
                            pinned = pinnedPackages.contains(
                                packageName
                            )
                        )
                    )
                } catch (e: Exception) {
                    // Application is no longer available.
                }
            }
        } else {
            selectedPackages.remove(packageName)

            if (!pinnedPackages.contains(packageName)) {
                apps.removeAll {
                    it.packageName == packageName
                }
            }
        }

        preferences.edit()
            .putStringSet(
                "selected_packages",
                selectedPackages
            )
            .apply()
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

        val pinned =
            pinnedOrder.mapNotNull { packageName ->
                apps.find {
                    it.packageName == packageName &&
                        it.pinned
                }
            }

        val remainingPinned =
            apps.filter {
                it.pinned &&
                    !pinnedOrder.contains(
                        it.packageName
                    )
            }

        val selected =
            apps.filter {
                !it.pinned &&
                    isSelected(it.packageName)
            }

        return pinned +
            remainingPinned +
            selected
    }

    fun clear() {
        apps.clear()
    }
}
