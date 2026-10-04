import re

path = 'app/src/main/java/com/dynamicdock/DockService.kt'
with open(path, encoding='utf-8') as f:
    lines = f.read().split('\n')

depth = 0
removed = []
out = []

for i, l in enumerate(lines):
    # Прибираємо строки та коментарі, щоб не рахувати дужки всередині них
    cleaned = re.sub(r'"(?:[^"\\]|\\.)*"', '""', l)
    cleaned = re.sub(r"'(?:[^'\\]|\\.)*'", "''", cleaned)
    cleaned = re.sub(r'//.*$', '', cleaned)

    opens = cleaned.count('{')
    closes = cleaned.count('}')
    prospective = depth + opens - closes

    # Якщо глибина падає до 0 (клас закрився), але ПОПЕРЕДУ ще є код —
    # ця дужка зайва, видаляємо її
    if prospective == 0 and depth == 1 and closes > 0:
        rest = '\n'.join(lines[i+1:]).strip()
        if rest:
            j = l.rfind('}')
            l = l[:j] + l[j+1:]
            closes -= 1
            prospective = depth + opens - closes
            removed.append(i + 1)

    depth = prospective
    out.append(l)

# Страховка: якщо після видалення клас все ще не закритий — додати дужки в кінець
if depth > 0:
    out.append('}' * depth)

with open(path, 'w', encoding='utf-8') as f:
    f.write('\n'.join(out))

print('Видалено зайві дужки на рядках:', removed if removed else 'НЕ ЗНАЙДЕНО')
print('Фінальна глибина (має бути 0):', depth)
