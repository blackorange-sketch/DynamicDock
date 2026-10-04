import re

file_path = 'app/src/main/java/com/dynamicdock/DockService.kt'

with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Знаходимо старий createHandle і видаляємо його разом з тілом
# Паттерн шукає від початку функції до закриваючої дужки перед наступною fun або кінцем класу
pattern = r'(private fun createHandle\(\) \{[\s\S]*?^\s*\})'

new_method = '''    private fun createHandle() {
        val settings = DockSettings(this)
        val len = dp(settings.hideHandleLengthDp)
        val thick = dp(settings.hideHandleThicknessDp)
        val mgn = dp(settings.hideHandleMarginDp)
        
        val c = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = true
            setOnClickListener { showDock() }
        }
        
        val bar = View(this).apply { 
            background = GradientDrawable().apply { 
                setColor(Color.WHITE)
                cornerRadius = dp(100).toFloat() 
            } 
        }
        
        val isVert = settings.dockPosition == "left" || settings.dockPosition == "right"
        
        if (isVert) {
            // Вертикальний док
            val lpBar = FrameLayout.LayoutParams(thick, len).apply { 
                gravity = if (settings.dockPosition == "left") Gravity.START or Gravity.CENTER_VERTICAL else Gravity.END or Gravity.CENTER_VERTICAL
                leftMargin = if (settings.dockPosition == "left") mgn else 0
                rightMargin = if (settings.dockPosition == "right") mgn else 0
            }
            c.addView(bar, lpBar)
            
            val p = WindowManager.LayoutParams(mgn + dp(24), len).apply {
                type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                format = PixelFormat.TRANSLUCENT
                gravity = if (settings.dockPosition == "right") Gravity.TOP or Gravity.RIGHT else Gravity.TOP or Gravity.LEFT
                
                // Безпека ділення та множення
                val screenH = resources.displayMetrics.heightPixels
                val maxY = (screenH - len).coerceAtLeast(0)
                y = (maxY * settings.verticalPositionPercent / 100f).toInt()
            }
            windowManager.addView(c, p)
            hideHandle = c
        } else {
            // Горизонтальний док
            val lpBar = FrameLayout.LayoutParams(len, thick).apply { 
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                bottomMargin = mgn
            }
            c.addView(bar, lpBar)
            
            val p = WindowManager.LayoutParams(len, mgn + dp(24)).apply {
                type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                format = PixelFormat.TRANSLUCENT
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            }
            windowManager.addView(c, p)
            hideHandle = c
        }
        
        c.alpha = 0f
        c.animate().alpha(1f).setDuration(150).start()
    }'''

if re.search(pattern, content):
    content = re.sub(pattern, new_method, content)
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(content)
    print("✓ DockService.kt: метод createHandle() успішно оновлено")
else:
    print("⚠ Не вдалося знайти старий createHandle(). Перевір файл вручну.")
