import re

file_path = 'app/src/main/java/com/dynamicdock/SettingsActivity.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Шукаємо виклики addSlider для menuXOffset/menuYOffset без параметра max
# Паттерн: addSlider(..., settings.menuXOffset, MIN_VAL)
# Замінюємо на: addSlider(..., settings.menuXOffset, MIN_VAL, 200)

def add_max_param(match):
    prefix = match.group(1)
    min_val = match.group(2)
    # Додаємо , 200 перед закриваючою дужкою
    return f"{prefix}{min_val}, 200)"

# Регулярний вираз для пошуку таких випадків
pattern = r'(addSlider\([^)]*?,\s*settings\.menu[YX]Offset,\s*-?\d+)\)'
new_content = re.sub(pattern, add_max_param, content)

if new_content != content:
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(new_content)
    print("✓ SettingsActivity.kt: додано параметр max до слайдерів зміщення")
else:
    print("⚠ Не знайдено проблемних викликів addSlider. Перевір файл вручну.")
