package com.dynamicdock

import android.graphics.drawable.Drawable

data class RunningApp(
    val packageName: String,
    val appName: String,
    val icon: Drawable?, // Nullable, бо спочатку може бути null
    val pinned: Boolean = false
)
