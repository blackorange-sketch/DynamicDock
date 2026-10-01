package com.dynamicdock

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.Switch
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

                        DockService.instance
                            ?.updateDockHeight(progress)
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

        val positionLabel = TextView(this).apply {
            text = "Положення Dock"
            textSize = 18f
            setPadding(0, 24, 0, 8)
        }

        val positionSpinner = Spinner(this).apply {

            val positions = arrayOf(
                "Знизу",
                "Зліва",
                "Справа"
            )

            adapter = ArrayAdapter(
                this@SettingsActivity,
                android.R.layout.simple_spinner_dropdown_item,
                positions
            )

            setSelection(
                when (settings.dockPosition) {
                    "left" -> 1
                    "right" -> 2
                    else -> 0
                }
            )

            onItemSelectedListener =
                object : android.widget.AdapterView.OnItemSelectedListener {

                    override fun onItemSelected(
                        parent: android.widget.AdapterView<*>?,
                        view: android.view.View?,
                        position: Int,
                        id: Long
                    ) {
                        val dockPosition =
                            when (position) {
                                1 -> "left"
                                2 -> "right"
                                else -> "bottom"
                            }

                        settings.dockPosition = dockPosition

                        DockService.instance
                            ?.updateDockPosition(dockPosition)
                    }

                    override fun onNothingSelected(
                        parent: android.widget.AdapterView<*>?
                    ) {
                    }
                }
        }

        val lengthLabel = TextView(this).apply {
            text =
                "Довжина Dock: " +
                "${settings.dockLengthDp} dp"
            textSize = 18f
            setPadding(0, 24, 0, 8)
        }

        val lengthSeekBar = SeekBar(this).apply {
            min = 160
            max = 600
            progress = settings.dockLengthDp

            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {

                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        settings.dockLengthDp = progress

                        lengthLabel.text =
                            "Довжина Dock: " +
                            "$progress dp"
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

        val autoHideSwitch = Switch(this).apply {
            text = "Автоматично ховати док"
            textSize = 16f
            isChecked = settings.reservedSpace
        }

        val autoHideLabel = TextView(this).apply {
            text =
                "Час до приховування: " +
                "${settings.autoHideDelaySeconds} с"
            textSize = 18f
            setPadding(0, 24, 0, 8)
        }

        val autoHideSeekBar = SeekBar(this).apply {
            min = 1
            max = 10
            progress = settings.autoHideDelaySeconds

            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {

                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        settings.autoHideDelaySeconds = progress

                        autoHideLabel.text =
                            "Час до приховування: " +
                            "$progress с"
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

        autoHideSwitch.setOnCheckedChangeListener { _, checked ->
            settings.reservedSpace = checked
        }

        layout.addView(title)
        layout.addView(heightLabel)
        layout.addView(heightSeekBar)
        layout.addView(positionLabel)
        layout.addView(positionSpinner)
        layout.addView(lengthLabel)
        layout.addView(lengthSeekBar)
        layout.addView(autoHideSwitch)
        layout.addView(autoHideLabel)
        layout.addView(autoHideSeekBar)

        setContentView(layout)
    }
}
