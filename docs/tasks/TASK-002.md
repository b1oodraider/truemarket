```yaml
id: TASK-002
title: "ADR-002: выбор поискового движка (Elasticsearch vs Meilisearch)"
phase: 0
module: catalog
priority: high
depends_on: [TASK-001]
estimated_hours: 1
status: completed
completed_at: 2026-05-07

context: |
  Каталог Phase 1 будет содержать ≤ 100 000 SKU, требуется typo-tolerance, фасеты,
  релевантность для русскоязычных запросов. На дистанции возможны семантический поиск
  и сложные агрегации. Нужен баланс между сложностью эксплуатации и функциональностью.

acceptance_criteria:
  - given: "сравнение Meilisearch и Elasticsearch/OpenSearch"
    when: "критерии оценены по 10+ параметрам"
    then: "выбран движок и зафиксирован в ADR-002"

  - given: "решение принято"
    when: "пишется код catalog"
    then: "поиск изолирован за интерфейсом ProductSearchEngine с одной реализацией"

  - given: "потребуется миграция"
    when: "будут достигнуты триггеры (1M+ SKU, сложные агрегации)"
    then: "миграция возможна без переписывания catalog-модуля — только новая реализация интерфейса"

technical_notes: |
  Решение: Meilisearch v1.10 на старте.
  - Простота операционной поддержки (1 контейнер vs JVM-кластер).
  - Typo-tolerance из коробки.
  - Низкое потребление памяти (~500 MB vs 2-4 GB).
  - Слабее в русской морфологии — компенсируется стеммингом и пользовательскими синонимами.

  Архитектурное требование — пакет `ru.truemarket.catalog.search` с интерфейсом
  `ProductSearchEngine`. Реализация `MeilisearchProductSearchEngine` — единственная на старте.

api_changes: []
db_changes: []

test_requirements:
  unit: []
  integration: []

definition_of_done:
  - "☐ ADR-002 создан"
  - "☐ Альтернативы перечислены (ES/OpenSearch, Typesense, PostgreSQL FTS)"
  - "☐ Триггеры миграции зафиксированы"
  - "☐ Архитектурный паттерн (интерфейс ProductSearchEngine) описан"
```

## Артефакт

[ADR-002 — Выбор поискового движка](../adr/ADR-002-search-engine.md)

## Итог

Принято: **Meilisearch v1.10** + абстракция `ProductSearchEngine` для безболезненной миграции на OpenSearch при росте.
