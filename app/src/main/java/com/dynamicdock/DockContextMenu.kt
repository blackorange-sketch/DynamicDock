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

        val buttonSize = dp(36)
        val margin = dp(6)

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

        val anchorCenterX =
            anchorX + anchor.width / 2

        val anchorCenterY =
            anchorY + anchor.height / 2

        val menuWidth =
            buttonSize + margin * 2

        val menuHeight =
            buttonSize + margin * 2

        val gap = dp(6)
        val screenMargin = dp(8)

        val displayMetrics =
            service.resources.displayMetrics

        val screenWidth =
            displayMetrics.widthPixels

        val screenHeight =
            displayMetrics.heightPixels

        var menuX: Int
        var menuY: Int

        when (dockPosition) {

            "bottom" -> {
                menuX =
                    anchorCenterX -
                        menuWidth / 2

                menuY =
                    anchorY -
                        menuHeight -
                        gap
            }

            "top" -> {
                menuX =
                    anchorCenterX -
                        menuWidth / 2

                menuY =
                    anchorY +
                        anchor.height +
                        gap
            }

            "left" -> {
                menuX =
                    anchorX +
                        anchor.width +
                        gap

                menuY =
                    anchorCenterY -
                        menuHeight / 2
            }

            "right" -> {
                menuX =
                    anchorX -
                        menuWidth -
                        gap

                menuY =
                    anchorCenterY -
                        menuHeight / 2
            }

            else -> {
                menuX =
                    anchorCenterX -
                        menuWidth / 2

                menuY =
                    anchorY -
                        menuHeight -
                        gap
            }
        }

        menuX =
            menuX.coerceIn(
                screenMargin,
                screenWidth -
                    menuWidth -
                    screenMargin
            )

        menuY =
            menuY.coerceIn(
                screenMargin,
                screenHeight -
                    menuHeight -
                    screenMargin
            )

        val params = WindowManager.LayoutParams(
            menuWidth,
            menuHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            android.graphics.PixelFormat.TRANSLUCENT
        )

        params.gravity =
            Gravity.TOP or Gravity.LEFT

        params.x = menuX
        params.y = menuY

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
