package com.dynamicdock

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout

class DockContextMenu(
    private val service: DockService,
    private val windowManager: WindowManager,
    private val onAction: (Action, RunningApp) -> Unit
) {

    enum class Action {
        PIN,
        UNPIN
    }

    private var menuView: LinearLayout? = null
    private var menuParams: WindowManager.LayoutParams? = null

    fun show(
        anchor: View,
        app: RunningApp,
        action: Action,
        dockPosition: String
    ) {
        dismiss()

        val buttonSize = dp(44)
        val margin = dp(8)

        val button = ImageButton(service).apply {
            setImageResource(
                if (action == Action.PIN) {
                    android.R.drawable.ic_menu_add
                } else {
                    android.R.drawable.ic_menu_close_clear_cancel
                }
            )

            contentDescription =
                if (action == Action.PIN) {
                    "Прикріпити"
                } else {
                    "Відкріпити"
                }

            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(220, 45, 45, 45))
                setStroke(
                    dp(1),
                    Color.argb(180, 255, 255, 255)
                )
            }

            setColorFilter(
                android.graphics.PorterDuffColorFilter(
                    Color.WHITE,
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
            )

            setOnClickListener {
                onAction(action, app)
                dismiss()
            }
        }

        val container = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(margin, margin, margin, margin)

            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.argb(235, 25, 25, 25))
                setStroke(
                    dp(1),
                    Color.argb(130, 255, 255, 255)
                )
            }

            addView(
                button,
                LinearLayout.LayoutParams(
                    buttonSize,
                    buttonSize
                )
            )

            alpha = 0f
            scaleX = 0.8f
            scaleY = 0.8f
        }

        val location = IntArray(2)
        anchor.getLocationOnScreen(location)

        val anchorX = location[0]
        val anchorY = location[1]

        val anchorWidth = anchor.width
        val anchorHeight = anchor.height

        val menuWidth = buttonSize + margin * 2
        val menuHeight = buttonSize + margin * 2

        val params = WindowManager.LayoutParams(
            menuWidth,
            menuHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        )

        when (dockPosition) {

            "bottom" -> {
                params.gravity =
                    Gravity.TOP or Gravity.LEFT

                params.x =
                    anchorX +
                        (anchorWidth - menuWidth) / 2

                params.y =
                    anchorY -
                        menuHeight -
                        dp(6)
            }

            "top" -> {
                params.gravity =
                    Gravity.TOP or Gravity.LEFT

                params.x =
                    anchorX +
                        (anchorWidth - menuWidth) / 2

                params.y =
                    anchorY +
                        anchorHeight +
                        dp(6)
            }

            "left" -> {
                params.gravity =
                    Gravity.TOP or Gravity.LEFT

                params.x =
                    anchorX +
                        anchorWidth +
                        dp(6)

                params.y =
                    anchorY +
                        (anchorHeight - menuHeight) / 2
            }

            "right" -> {
                params.gravity =
                    Gravity.TOP or Gravity.LEFT

                params.x =
                    anchorX -
                        menuWidth -
                        dp(6)

                params.y =
                    anchorY +
                        (anchorHeight - menuHeight) / 2
            }

            else -> {
                params.gravity =
                    Gravity.TOP or Gravity.LEFT

                params.x =
                    anchorX +
                        (anchorWidth - menuWidth) / 2

                params.y =
                    anchorY -
                        menuHeight -
                        dp(6)
            }
        }

        menuView = container
        menuParams = params

        windowManager.addView(
            container,
            params
        )

        container.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(120)
            .start()
    }

    fun dismiss() {
        val view = menuView ?: return

        try {
            view.animate().cancel()
            windowManager.removeView(view)
        } catch (_: Exception) {
        }

        menuView = null
        menuParams = null
    }

    private fun dp(value: Int): Int {
        return (
            value *
                service.resources.displayMetrics.density
            ).toInt()
    }
}
