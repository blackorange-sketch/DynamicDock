package com.dynamicdock

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class LogActivity : Activity() {

    private lateinit var logText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        val title = TextView(this).apply {
            text = "Dynamic Dock — Логи"
            textSize = 24f
        }

        val copyButton = Button(this).apply {
            text = "Копіювати весь лог"

            setOnClickListener {
                val log = DockLogger.read(this@LogActivity)

                val clipboard =
                    getSystemService(Context.CLIPBOARD_SERVICE)
                        as ClipboardManager

                clipboard.setPrimaryClip(
                    ClipData.newPlainText(
                        "DynamicDock log",
                        log
                    )
                )

                Toast.makeText(
                    this@LogActivity,
                    "Лог скопійовано",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val clearButton = Button(this).apply {
            text = "Очистити лог"

            setOnClickListener {
                DockLogger.clear(this@LogActivity)
                loadLog()
            }
        }

        logText = TextView(this).apply {
            textSize = 12f
            setTextIsSelectable(true)
        }

        val scrollView = ScrollView(this).apply {
            addView(logText)
        }

        layout.addView(title)
        layout.addView(copyButton)
        layout.addView(clearButton)

        layout.addView(
            scrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(layout)
    }

    override fun onResume() {
        super.onResume()
        loadLog()
    }

    private fun loadLog() {
        logText.text =
            DockLogger.read(this).ifEmpty {
                "Лог порожній."
            }
    }
}
