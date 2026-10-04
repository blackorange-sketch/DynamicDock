import re

file_path = 'app/src/main/java/com/dynamicdock/DockService.kt'

with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Знаходимо весь блок private fun scheduleAutoHide() { ... }
# Використовуємо нелагідний пошук, щоб знайти навіть якщо є зайві пробіли
pattern = r'(private\s+fun\s+scheduleAutoHide\s*\(\s*\)\s*\{)([\s\S]*?)(^\s*\})'

new_body = '''    private fun scheduleAutoHide() {
        handler.removeCallbacks(autoHideRunnable)
        val settings = DockSettings(this)
        if (settings.autoHide) {
            handler.postDelayed(autoHideRunnable, settings.autoHideDelaySeconds * 1000L)
        }
    }'''

match = re.search(pattern, content, re.MULTILINE)
if match:
    # Замінюємо весь знайдений блок на новий
    start_idx = match.start()
    end_idx = match.end()
    
    # Перевіряємо, чи це дійсно повний блок (знаходить першу закриваючу дужку рівня класу)
    # Для безпеки, ми просто перезапишемо файл, видаливши старий блок і вставивши новий
    
    # Простий підхід: знайти початок функції і кінець (наступна функція або кінець класу)
    # Але regex вище може бути ненадійним для вкладених дужок.
    
    # Тому давай використаємо інший підхід: просто замінимо текст між маркерами, якщо вони унікальні.
    # Оскільки я не знаю точного контексту, найбезпечніше — повністю перезаписати файл DockService.kt 
    # останньою стабільною версією, яку я надавав раніше (вона містить правильний scheduleAutoHide).
    
    print("⚠ Regex пошук складний через вкладеність. Рекомендую повний перезапис DockService.kt.")
else:
    print("⚠ Не знайдено метод scheduleAutoHide. Можливо, він вже виправлений або названий інакше.")

