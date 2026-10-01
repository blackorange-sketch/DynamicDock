package com.dynamicdock

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.Color

class AppInfoRepository(
    private val context: Context
) {

    private val packageManager =
        context.packageManager

    fun getAppInfo(
        packageName: String
    ): AppInfo {

        val applicationInfo =
            packageManager.getApplicationInfo(
                packageName,
                0
            )

        val appName =
            try {
                packageManager
                    .getApplicationLabel(
                        applicationInfo
                    )
                    .toString()
            } catch (e: Exception) {
                packageName
            }

        val icon =
            try {
                packageManager
                    .getApplicationIcon(
                        applicationInfo
                    )
            } catch (e: Exception) {
                ColorDrawable(
                    Color.DKGRAY
                )
            }

        return AppInfo(
            packageName = packageName,
            appName = appName,
            icon = icon
        )
    }
}
