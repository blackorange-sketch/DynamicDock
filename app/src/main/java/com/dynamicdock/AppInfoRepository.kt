package com.dynamicdock

import android.content.Context

class AppInfoRepository(
    private val context: Context
) {

    private val packageManager = context.packageManager

    fun getAppInfo(packageName: String): AppInfo? {
        return try {
            val applicationInfo =
                packageManager.getApplicationInfo(
                    packageName,
                    0
                )

            val appName =
                packageManager.getApplicationLabel(
                    applicationInfo
                ).toString()

            val icon =
                packageManager.getApplicationIcon(
                    applicationInfo
                )

            AppInfo(
                packageName = packageName,
                appName = appName,
                icon = icon
            )

        } catch (e: Exception) {
            null
        }
    }
}
