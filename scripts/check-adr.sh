#!/usr/bin/env bash
# ============================================================================
# Проверка целостности ADR (запускается локально через `make adr-check` и в CI).
#
# Что проверяется:
#   1. Все обязательные секции присутствуют (Контекст, Решение, Последствия,
#      Альтернативы, История).
#   2. Поле "Статус" задано.
#   3. В файле нет невычищенных плейсхолдеров шаблона ({{NUMBER}} и т.д.).
#   4. Нумерация ADR без пропусков и дублей.
#   5. Имя файла соответствует ADR-NNN-kebab-case.md.
#
# Шаблон (docs/adr/0000-template.md) исключается из проверок 1-3,
# но участвует в проверке 4-5.
# ============================================================================
set -euo pipefail

ADR_DIR="docs/adr"
TEMPLATE="$ADR_DIR/0000-template.md"
FAILED=0
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Сигнатуры обязательных секций (regex для grep -E)
REQUIRED_SECTIONS=(
    "^## Контекст$"
    "^## Решение$"
    "^## Последствия$"
    "^## Альтернативы"
    "^## История$"
)

# ----- 1-3. Проверка содержимого каждого ADR -----
shopt -s nullglob
for f in "$ADR_DIR"/ADR-*.md; do
    file_name=$(basename "$f")
    echo "→ $file_name"

    # Имя файла: ADR-NNN-kebab-case.md (минимум 3 цифры)
    if ! [[ "$file_name" =~ ^ADR-[0-9]{3,}-[a-z0-9]([a-z0-9-]*[a-z0-9])?\.md$ ]]; then
        echo -e "  ${RED}✗${NC} имя файла не соответствует ADR-NNN-kebab-case.md"
        FAILED=1
    fi

    # Обязательные секции
    for section_re in "${REQUIRED_SECTIONS[@]}"; do
        if ! grep -qE "$section_re" "$f"; then
            echo -e "  ${RED}✗${NC} нет секции, соответствующей: ${section_re}"
            FAILED=1
        fi
    done

    # Поле "Статус"
    if ! grep -qE '^\- \*\*Статус:\*\*' "$f"; then
        echo -e "  ${RED}✗${NC} нет поля '- **Статус:**'"
        FAILED=1
    fi

    # Невычищенные плейсхолдеры
    if grep -qE '\{\{(NUMBER|TITLE|DATE)\}\}' "$f"; then
        echo -e "  ${RED}✗${NC} остались плейсхолдеры шаблона ({{NUMBER}}/{{TITLE}}/{{DATE}})"
        FAILED=1
    fi
done

# ----- 4. Нумерация без пропусков и дублей -----
echo ""
echo "→ Проверка нумерации"
numbers=$(
    for f in "$ADR_DIR"/ADR-*.md; do
        [ -e "$f" ] || continue
        basename "$f"
    done | grep -oE 'ADR-[0-9]+' | sed 's/ADR-//' | sort -n | uniq -c | awk '{print $1,$2}')

duplicates=$(echo "$numbers" | awk '$1>1{print $2}')
if [ -n "$duplicates" ]; then
    echo -e "  ${RED}✗${NC} дубликаты номеров: $duplicates"
    FAILED=1
fi

# Проверка пропусков (например, есть ADR-001 и ADR-003, но нет ADR-002)
sorted=$(echo "$numbers" | awk '{print $2}' | sort -n)
prev=0
for n in $sorted; do
    n_int=$((10#$n))
    if [ "$prev" -ne 0 ] && [ "$n_int" -ne $((prev + 1)) ]; then
        echo -e "  ${YELLOW}⚠${NC}  пропуск между ADR-$(printf '%03d' $prev) и ADR-$(printf '%03d' $n_int) (это ОК, если ADR был отменён)"
    fi
    prev=$n_int
done

# ----- Итог -----
echo ""
if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}✓ Все ADR корректны${NC}"
    exit 0
else
    echo -e "${RED}✗ Обнаружены проблемы в ADR. Шаблон: $TEMPLATE${NC}"
    exit 1
fi
