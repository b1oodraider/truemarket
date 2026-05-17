```yaml
id: TASK-001
title: "ADR-001: выбор языка бэкенда (Go vs Java)"
phase: 0
module: meta
priority: critical
depends_on: []
estimated_hours: 2
status: completed
completed_at: 2026-05-07

context: |
  CLAUDE.md раздел 4 фиксирует выбор между Go и Java 21 + Spring Boot 3.
  Без этого решения нельзя приступать к структуре репозитория, Makefile, CI,
  Dockerfile и любому коду бэкенда. Это блокер для всех остальных задач Phase 0.

  Критерии из CLAUDE.md:
    - производительность под нагрузкой;
    - скорость разработки;
    - экосистема (особенно интеграции для РФ-compliance);
    - стоимость найма команды.

acceptance_criteria:
  - given: "выбор между Go и Java"
    when: "проанализированы все четыре критерия"
    then: "решение зафиксировано в /docs/adr/ADR-001-backend-language.md с обоснованием"

  - given: "выбран язык"
    when: "проектируется структура backend/"
    then: "структура и команды Makefile соответствуют выбору"

  - given: "ADR принят"
    when: "появляются последующие ADR (002, 003)"
    then: "они учитывают выбранный язык (например, для Java выбираем не golang-migrate, а Flyway)"

technical_notes: |
  Решение принимается Product Owner'ом (Kamal) после анализа Claude'ом.
  Решение от 2026-05-07: Java 21 + Spring Boot 3. Обоснование — в ADR-001.
  Ключевой довод — экосистема российских интеграций (ЮKassa, ОФД, Честный ЗНАК)
  и доступность найма Java-специалистов на рынке РФ.

api_changes: []
db_changes: []

test_requirements:
  unit: []
  integration: []

definition_of_done:
  - "☐ Файл /docs/adr/ADR-001-backend-language.md создан"
  - "☐ Указаны все 4 критерия из CLAUDE.md"
  - "☐ Принято обоснованное решение"
  - "☐ Описаны последствия (структура backend/, инструменты, риски)"
  - "☐ Перечислены альтернативы и причины отклонения"
```

## Артефакт

[ADR-001 — Выбор языка бэкенда](../adr/ADR-001-backend-language.md)

## Итог

Принято: **Java 21 + Spring Boot 3.3.5**. Maven Wrapper, Spotless + Google Java Format, Flyway, Spring Modulith 1.2.x.
