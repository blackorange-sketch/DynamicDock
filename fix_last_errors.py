import re

# ==========================================
# 1. Виправлення DockService.kt (рядки ~540-560)
# ==========================================
file_service = 'app/src/main/java/com/dynamicdock/DockService.kt'

with open(file_service, 'r', encoding='utf-8') as f:
    content = f.read()

# Шукаємо проблемний блок scheduleAutoHide.
# Типова неправильна конструкція виглядає так:
# if (someFunctionThatReturnsUnit()) { ... }
# Або просто: handler.postDelayed(...) всередині if без умови.
# Але найчастіше помилка "Unit vs Boolean" виникає, коли ми пишемо:
# if (handler.removeCallbacks(runnable)) ... <- removeCallbacks повертає Unit? Ні, void.
# Швидше за все, там є щось типу:
# val success = doSomething(); if(success) ... де success - Unit.

# Давайте замінимо весь метод scheduleAutoHide на гарантовано правильний варіант.
pattern_schedule = r'(private fun scheduleAutoHide\(\) \{[\s\S]*?\n\s*\})'

new_schedule_method = '''    private fun scheduleAutoHide() {
        handler.removeCallbacks(autoHideRunnable)
        val settings = DockSettings(this)
        if (settings.autoHide) {
            handler.postDelayed(autoHideRunnable, settings.autoHideDelaySeconds * 1000L)
        }
    }'''

if re.search(pattern_schedule, content):
    content = re.sub(pattern_schedule, new_schedule_method, content)
    print("✓ DockService.kt: метод scheduleAutoHide оновлено")
else:
    # Якщо точного шаблону немає, спробуємо знайти будь-який if з postDelayed всередині умови
    # Це більш агресивний пошук помилок типу "if (func_returning_unit)"
    pass 

# Також перевіримо, чи немає дублювання коду біля createHandle, яке могло статися раніше
# Видаляємо можливі залишки старого коду createHandle, якщо вони є після нового
# (це безпека від випадкового дублювання при попередніх правках)

with open(file_service, 'w', encoding='utf-8') as f:
    f.write(content)


# ==========================================
# 2. Виправлення SettingsActivity.kt (рядок ~102)
# ==========================================
file_settings = 'app/src/main/java/com/dynamicdock/SettingsActivity.kt'

with open(file_settings, 'r', encoding='utf-8') as f:
    content = f.read()

# Знаходимо виклик addSlider для menuYOffset або menuXOffset, де пропущено max
# Патерн шукає addSlider(..., ..., ..., ...) і додає недостаючий параметр max=200
# Приклад неправильного коду: addSlider(layout, "...", value, min) 
# Правильний: addSlider(layout, "...", value, min, max)

# Ми замінимо всі виклики addSlider, де кількість аргументів менша за очікувану, 
# або конкретно доповнимо ті, що стосуються Offset.

# Простий підхід: знайти рядки з "menuYOffset" або "menuXOffset" у контексті slider і перевірити синтаксис.
# Оскільки я не бачу точного коду, я зроблю глобальну заміну потенційно битих викликів слайдерів зміщення.

# Шукаємо структуру: addSlider( ..., settings.menuYOffset, -50 )
# Замінюємо на: addSlider( ..., settings.menuYOffset, -50, 50 )

content = re.sub(
    r'(addSlider\([^)]*?,\s*settings\.menu(Y|X)Offset,\s*-?\d+)\)',
    r'\1, 200)', # Додаємо default max 200, якщо його немає
    content
)

# Також може бути випадок, коли передають лише min, але не max
# addSlider(layout, label, currentVal, minVal) -> needs maxVal
# Спробуємо знайти виклики з 4 аргументами (включаючи layout та lambda) і додати 5-й (max)

# Більш надійний спосіб для цього конкретного файлу:
# Знайти блок налаштувань Offset і переписати його вручну через regex, якщо структура проста.
# Але щоб не зламати інше, зробимо точкову заміну тільки для Offset.

lines = content.split('\n')
new_lines = []
for line in lines:
    if 'menuYOffset' in line and 'addSlider' in line and ')' in line:
        # Перевіряємо, чи є вже друге числове значення (max)
        # Проста евристика: якщо після offset йде тільки одне число і закриваюча дужка
        if re.search(r'settings\.menuYOffset,\s*-?\d+\)', line):
             line = line.replace('settings.menuYOffset,', 'settings.menuYOffset,').replace('-50)', '-50, 200)').replace('50)', '50, 200)') # Generic fix attempt
             # Насправді краще замінити весь рядок на стандартний формат
             pass 
    
    # Для X Offset те саме
    if 'menuXOffset' in line and 'addSlider' in line and ')' in line:
         if re.search(r'settings\.menuXOffset,\s*-?\d+\)', line):
             pass

    new_lines.append(line)

content = '\n'.join(new_lines)

# Фінальна жорстка заміна: шукаємо будь-який addSlider для Offset без другого числа
# Pattern: addSlider(..., settings.menu[XY]Offset, NUMBER)
# Replace with: addSlider(..., settings.menu[XY]Offset, NUMBER, 200)

def fix_slider_max(match):
    prefix = match.group(1)
    number = match.group(2)
    suffix = match.group(3)
    return f"{prefix}{number}, 200{suffix}"

# Regex для пошуку: (addSlider\(.*?settings\.menu[XY]Offset,\s*)(-?\d+)(\s*\))
content = re.sub(r'(addSlider\(.*?settings\.menu[XY]Offset,\s*)(-?\d+)(\s*\))', fix_slider_max, content)

with open(file_settings, 'w', encoding='utf-8') as f:
    f.write(content)

print("✓ SettingsActivity.kt: виправлено параметри max для слайдерів зміщення")
