package com.dynamicdock

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView

class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settings = DockSettings(this)

        val scrollView = ScrollView(this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(20),
                dp(16),
                dp(20),
                dp(32)
            )
        }

        val title = TextView(this).apply {
            text = "Dynamic Dock"
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(8))
        }

        val subtitle = TextView(this).apply {
            text = "Налаштування Dock"
            textSize = 14f
            alpha = 0.7f
            setPadding(0, 0, 0, dp(24))
        }

        layout.addView(title)
        layout.addView(subtitle)

        addSectionTitle(
            layout,
            "Вигляд Dock"
        )

        val heightLabel = addSlider(
            layout,
            "Висота Dock",
            settings.dockHeightDp,
            32,
            80
        ) { progress ->
            settings.dockHeightDp = progress

            DockService.instance
                ?.updateDockHeight(progress)
        }

        addSpinner(
            layout,
            "Положення Dock",
            arrayOf("Знизу", "Зліва", "Справа"),
            when (settings.dockPosition) {
                "left" -> 1
                "right" -> 2
                else -> 0
            }
        ) { position ->
            val dockPosition =
                when (position) {
                    1 -> "left"
                    2 -> "right"
                    else -> "bottom"
                }

            settings.dockPosition = dockPosition

            DockService.instance
                ?.updateDockPosition(dockPosition)

            DockService.instance
                ?.refreshHideHandle()
        }

        val lengthLabel = addSlider(
            layout,
            "Довжина Dock",
            settings.dockLengthDp,
            160,
            600
        ) { progress ->
            settings.dockLengthDp = progress

            DockService.instance
                ?.updateDockLength(progress)
        }

        val verticalPositionLabel = addSlider(
            layout,
            "Положення по вертикалі",
            settings.verticalPositionPercent,
            0,
            100,
            suffix = "%"
        ) { progress ->
            settings.verticalPositionPercent = progress

            DockService.instance
                ?.updateVerticalPosition(progress)
        }

        addSectionTitle(
            layout,
            "Іконки"
        )

        addSwitch(
            layout,
            "Показувати назви програм",
            settings.showAppLabels
        ) { checked ->
            settings.showAppLabels = checked

            DockService.instance
                ?.refreshDock()
        }

        addSlider(
            layout,
            "Розмір іконок",
            settings.iconSizeDp,
            24,
            48
        ) { progress ->
            settings.iconSizeDp = progress

            DockService.instance
                ?.updateIconSize(progress)
        }

        addSlider(
            layout,
            "Горизонтальний відступ",
            settings.horizontalPaddingDp,
            0,
            16
        ) { progress ->
            settings.horizontalPaddingDp = progress

            DockService.instance
                ?.updatePadding()
        }

        addSlider(
            layout,
            "Вертикальний відступ",
            settings.verticalPaddingDp,
            0,
            16
        ) { progress ->
            settings.verticalPaddingDp = progress

            DockService.instance
                ?.updatePadding()
        }

        addSectionTitle(
            layout,
            "Програми"
        )

        addSlider(
            layout,
            "Максимум динамічних програм",
            settings.maxDynamicApps,
            1,
            20
        ) { progress ->
            settings.maxDynamicApps = progress
            DockService.instance?.refreshDock()
        }

        val appSelectionButton = android.widget.Button(this).apply {
            text = "Програми Dock"

            setOnClickListener {
                startActivity(
                    android.content.Intent(
                        this@SettingsActivity,
                        AppSelectionActivity::class.java
                    )
                )
            }
        }

        layout.addView(
            appSelectionButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        addSectionTitle(
            layout,
            "Приховування"
        )

        addSwitch(
            layout,
            "Автоматично ховати Dock",
            settings.autoHide
        ) { checked ->
            settings.autoHide = checked
        }

        addSlider(
            layout,
            "Час до приховування",
            settings.autoHideDelaySeconds,
            1,
            10,
            suffix = " с"
        ) { progress ->
            settings.autoHideDelaySeconds = progress
        }

        addSlider(
            layout,
            "Довжина риски",
            settings.hideHandleLengthDp,
            24,
            120,
            suffix = " dp"
        ) { progress ->
            settings.hideHandleLengthDp = progress
            DockService.instance?.refreshHideHandle()
        }

        addSlider(
            layout,
            "Товщина риски",
            settings.hideHandleThicknessDp,
            2,
            12,
            suffix = " dp"
        ) { progress ->
            settings.hideHandleThicknessDp = progress
            DockService.instance?.refreshHideHandle()
        }

        addSlider(
            layout,
            "Відступ риски",
            settings.hideHandleMarginDp,
            0,
            24,
            suffix = " dp"
        ) { progress ->
            settings.hideHandleMarginDp = progress
            DockService.instance?.refreshHideHandle()
        }

        scrollView.addView(layout)

        setContentView(scrollView)
    }

    private fun addSectionTitle(
        layout: LinearLayout,
        text: String
    ) {
        val title = TextView(this).apply {
            this.text = text
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(
                0,
                dp(20),
                0,
                dp(8)
            )
        }

        layout.addView(title)
    }

    private fun addSwitch(
        layout: LinearLayout,
        text: String,
        checked: Boolean,
        onChanged: (Boolean) -> Unit
    ) {
        val switch = Switch(this).apply {
            this.text = text
            textSize = 16f
            isChecked = checked

            setOnCheckedChangeListener { _, value ->
                onChanged(value)
            }

            setPadding(
                0,
                dp(6),
                0,
                dp(6)
            )
        }

        layout.addView(
            switch,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun addSlider(
        layout: LinearLayout,
        title: String,
        value: Int,
        min: Int,
        max: Int,
        suffix: String = " dp",
        onChanged: (Int) -> Unit
    ): TextView {

        val label = TextView(this).apply {
            text = "$title: $value$suffix"
            textSize = 16f
            setPadding(
                0,
                dp(12),
                0,
                dp(4)
            )
        }

        val seekBar = SeekBar(this).apply {
            this.min = min
            this.max = max
            progress = value

            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {

                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        label.text =
                            "$title: $progress$suffix"

                        onChanged(progress)
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

        layout.addView(label)

        layout.addView(
            seekBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        return label
    }

    private fun addSpinner(
        layout: LinearLayout,
        title: String,
        items: Array<String>,
        selected: Int,
        onSelected: (Int) -> Unit
    ) {
        val label = TextView(this).apply {
            text = title
            textSize = 16f
            setPadding(
                0,
                dp(12),
                0,
                dp(4)
            )
        }

        val spinner = Spinner(this)

        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            items
        ).also {
            it.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
            )
        }

        spinner.setSelection(selected)

        spinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    onSelected(position)
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {
                }
            }

        layout.addView(label)

        layout.addView(
            spinner,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density)
            .toInt()
    }
}
