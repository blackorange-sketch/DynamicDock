package com.dynamicdock

import android.graphics.drawable.Drawable

data class RunningApp(
    val packageName: String,
    val appName: String,
    val icon: Drawable?, // Змінено на Nullable
    val pinned: Boolean = false
)
