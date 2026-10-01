package com.dynamicdock

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settings = DockSettings(this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        val title = TextView(this).apply {
            text = "Налаштування Dynamic Dock"
            textSize = 24f
        }

        val heightLabel = TextView(this).apply {
            text = "Висота дока: ${settings.dockHeightDp} dp"
            textSize = 18f
            setPadding(0, 32, 0, 8)
        }

        val heightSeekBar = SeekBar(this).apply {
            min = 32
            max = 80
            progress = settings.dockHeightDp

            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {

                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        settings.dockHeightDp = progress
                        heightLabel.text =
                            "Висота дока: $progress dp"

                        DockService.instance?.updateDockHeight(progress)
                    }

                    override fun onStartTrackingTouch(
                        seekBar: SeekBar?
                    ) {
                    }

                    override fun onStopTrackingTouch(
                        seekBar: SeekBar?
                    ) {
                    }
                }
            )
        }

        layout.addView(title)
        layout.addView(heightLabel)
        layout.addView(heightSeekBar)

        setContentView(layout)
    }
}
