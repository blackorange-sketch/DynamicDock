package com.dynamicdock

import android.graphics.drawable.Drawable

data class RunningApp(
    val packageName: String,
    val appName: String,
    val icon: Drawable,
    val pinned: Boolean = false
)
