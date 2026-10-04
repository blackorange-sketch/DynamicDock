import base64

# ==========================================
# 1. Виправлення DockService.kt (Рядок 546)
# Помилка: Condition type mismatch (Unit vs Boolean)
# Причина: scheduleAutoHide() повертає Unit, але використовується в if()
# Рішення: Повністю замінюємо метод scheduleAutoHide на правильний
# ==========================================

service_file = 'app/src/main/java/com/dynamicdock/DockService.kt'

with open(service_file, 'r', encoding='utf-8') as f:
    content = f.read()

# Знаходимо старий зламаний блок scheduleAutoHide та замінюємо його
# Ми шукаємо текст між "private fun scheduleAutoHide()" і наступним "private fun" або кінцем класу
import re

pattern_schedule = r'(private fun scheduleAutoHide\(\)\s*\{[\s\S]*?\})'

new_schedule_code = '''    private fun scheduleAutoHide() {
        handler.removeCallbacks(autoHideRunnable)
        val settings = DockSettings(this)
        // Явно перевіряємо boolean, а не виклик функції
        if (settings.autoHide) {
            handler.postDelayed(autoHideRunnable, settings.autoHideDelaySeconds * 1000L)
        }
    }'''

if re.search(pattern_schedule, content):
    content = re.sub(pattern_schedule, new_schedule_code, content)
    with open(service_file, 'w', encoding='utf-8') as f:
        f.write(content)
    print("✓ DockService.kt: виправлено scheduleAutoHide")
else:
    print("⚠ DockService.kt: не знайдено старий scheduleAutoHide. Перевір файл.")


# ==========================================
# 2. Виправлення SettingsActivity.kt (Рядок 102)
# Помилка: No value passed for parameter 'max'
# Причина: addSlider очікує 5 параметрів (layout, label, current, min, MAX), але передав тільки 4
# Рішення: Додаємо параметр max=200 для слайдерів зміщення меню
# ==========================================

settings_file = 'app/src/main/java/com/dynamicdock/SettingsActivity.kt'

with open(settings_file, 'r', encoding='utf-8') as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    # Шукаємо рядки з menuYOffset або menuXOffset у контексті addSlider
    if ('menuYOffset' in line or 'menuXOffset' in line) and 'addSlider' in line:
        # Якщо в рядку вже є ',' після offset, можливо max вже є. 
        # Але зазвичай помилка виникає, коли ми пишемо: addSlider(..., settings.menuYOffset, -50)
        # Треба зробити: addSlider(..., settings.menuYOffset, -50, 200)
        
        # Проста евристична заміна: якщо бачимо "-50)" або "50)", додаємо ", 200" перед ")"
        if '-50)' in line:
            line = line.replace('-50)', '-50, 200)')
        elif '50)' in line and ', 200)' not in line:
            line = line.replace('50)', '50, 200)')
            
    new_lines.append(line)

with open(settings_file, 'w', encoding='utf-8') as f:
    f.writelines(new_lines)

print("✓ SettingsActivity.kt: виправлено параметри max для слайдерів")

print("\n✅ ГОТОВО! Файли виправлені через безпечну заміну тексту.")
