package com.dynamicdock

import android.content.Context

class DockSettings(
    context: Context
) {

    private val preferences =
        context.getSharedPreferences(
            "dynamic_dock",
            Context.MODE_PRIVATE
        )

    var dockHeightDp: Int
        get() = preferences.getInt(
            "dock_height_dp",
            40
        )
        set(value) {
            preferences.edit()
                .putInt("dock_height_dp", value)
                .apply()
        }


    var autoHideDelaySeconds: Int
        get() = preferences.getInt(
            "auto_hide_delay_seconds",
            3
        )
        set(value) {
            preferences.edit()
                .putInt("auto_hide_delay_seconds", value)
                .apply()
        }


    var dockPosition: String
        get() = preferences.getString(
            "dock_position",
            "bottom"
        ) ?: "bottom"
        set(value) {
            preferences.edit()
                .putString("dock_position", value)
                .apply()
        }

    var dockLengthDp: Int
        get() = preferences.getInt(
            "dock_length_dp",
            280
        )
        set(value) {
            preferences.edit()
                .putInt("dock_length_dp", value)
                .apply()
        }

    var showAppLabels: Boolean
        get() = preferences.getBoolean(
            "show_app_labels",
            true
        )
        set(value) {
            preferences.edit()
                .putBoolean("show_app_labels", value)
                .apply()
        }

    var iconSizeDp: Int
        get() = preferences.getInt(
            "icon_size_dp",
            30
        )
        set(value) {
            preferences.edit()
                .putInt("icon_size_dp", value)
                .apply()
        }

    var horizontalPaddingDp: Int
        get() = preferences.getInt(
            "horizontal_padding_dp",
            6
        )
        set(value) {
            preferences.edit()
                .putInt("horizontal_padding_dp", value)
                .apply()
        }

    var verticalPaddingDp: Int
        get() = preferences.getInt(
            "vertical_padding_dp",
            0
        )
        set(value) {
            preferences.edit()
                .putInt("vertical_padding_dp", value)
                .apply()
        }

    var reservedSpace: Boolean
        get() = preferences.getBoolean(
            "reserved_space",
            false
        )
        set(value) {
            preferences.edit()
                .putBoolean("reserved_space", value)
                .apply()
        }
}
