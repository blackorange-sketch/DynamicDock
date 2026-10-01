package com.dynamicdock

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = TextView(this).apply {
            text = "Dynamic Dock\n\nPrototype 0.1.0"
            textSize = 24f
            setPadding(32, 32, 32, 32)
        }

        setContentView(text)
    }
}
