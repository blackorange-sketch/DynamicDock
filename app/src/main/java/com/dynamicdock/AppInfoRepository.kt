package com.dynamicdock

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable

class AppInfoRepository(
    private val context: Context
) {

    private val packageManager =
        context.packageManager

    fun getAppInfo(
        packageName: String
    ): AppInfo {

        var appName = packageName

        try {
            val applicationInfo =
                packageManager.getApplicationInfo(
                    packageName,
                    0
                )

            appName =
                packageManager
                    .getApplicationLabel(
                        applicationInfo
                    )
                    .toString()

            val icon =
                try {
                    packageManager.getApplicationIcon(
                        applicationInfo
                    )
                } catch (e: Exception) {
                    ColorDrawable(Color.DKGRAY)
                }

            return AppInfo(
                packageName = packageName,
                appName = appName,
                icon = icon
            )

        } catch (e: Exception) {
            return AppInfo(
                packageName = packageName,
                appName = packageName,
                icon = ColorDrawable(Color.DKGRAY)
            )
        }
    }
}
