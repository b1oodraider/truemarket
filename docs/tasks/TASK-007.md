```yaml
id: TASK-007
title: "ERD: полная схема БД в DBML"
phase: 0
module: meta
priority: high
depends_on: [TASK-001, TASK-002, TASK-003]
estimated_hours: 4
status: completed
completed_at: 2026-05-07

context: |
  CLAUDE.md 5.4 даёт верхнеуровневый эскиз ERD. Этот эскиз неполон:
  отсутствуют Address, Cart/CartItem, Dispute, Refund, RefreshToken, MarkCode,
  FiscalReceipt и др. — без них нельзя проектировать API и миграции Phase 1.

  Формат — DBML (https://dbml.dbdiagram.io). Преимущества:
    - удобная визуализация через dbdiagram.io / dbdocs.io;
    - текстовый формат, версионируется в git;
    - чистый язык, без привязки к конкретной СУБД.

acceptance_criteria:
  - given: "файл docs/erd.dbml"
    when: "загружается в dbdiagram.io"
    then: "рендерится валидная схема без синтаксических ошибок"

  - given: "ERD"
    when: "сверяется с CLAUDE.md 5.4"
    then: "присутствуют все упомянутые сущности + восполнены пробелы"

  - given: "ERD"
    when: "проверяется в контексте 152-ФЗ"
    then: "у пользовательских сущностей есть deleted_at (soft delete для права на удаление)"

  - given: "ERD"
    when: "проверяется в контексте 54-ФЗ + ADR-008"
    then: "присутствуют payments + fiscal_receipts + fiscal_receipt_items"

  - given: "ERD"
    when: "проверяется в контексте Честного ЗНАКа + CLAUDE.md 5.3.2"
    then: "присутствует catalog.mark_codes с резервированием/передачей"

  - given: "ERD"
    when: "проверяется в контексте ADR-004 + 13.5 (immutable audit)"
    then: "присутствует payments.commission_log с полем bonuses_jsonb"

technical_notes: |
  Схемы соответствуют разделению модулей в CLAUDE.md 5.2 + ADR-007 (одна схема на модуль).
  Enum'ы вынесены в начало DBML для переиспользования.

  Сущности, которых нет в CLAUDE.md 5.4, но добавлены:
    - auth.refresh_tokens — для JWT refresh с ротацией (CLAUDE.md 13.1)
    - orders.addresses, orders.carts/cart_items
    - orders.status_transitions — append-only audit переходов
    - verification.verification_documents, product_verifications
    - payments.fiscal_receipts/fiscal_receipt_items (54-ФЗ + ADR-008)
    - payments.payouts (выплаты продавцам)
    - payments.refunds (возвраты)
    - delivery.tracking_events
    - reviews.review_photos, review_votes
    - admin.disputes, admin.audit_log
    - notifications.notification_log
    - catalog.product_images, catalog.mark_codes

  Поля, обязательные для всех бизнес-сущностей:
    - id UUID (gen_random_uuid())
    - created_at, updated_at (для большинства)
    - version (для optimistic locking где есть гонки)
    - deleted_at (soft delete для пользовательских данных)

api_changes: []

db_changes:
  - type: documentation
    description: "Файл docs/erd.dbml — single source of truth для схемы БД на Phase 0"
  - type: deferred
    description: "Реальные миграции по этой схеме — задачи Phase 1+ (TASK-101, TASK-108, ...)"

test_requirements:
  unit: []
  integration: []

definition_of_done:
  - "☐ Файл docs/erd.dbml создан"
  - "☐ Все 10 модулей покрыты"
  - "☐ Enum'ы вынесены наверх"
  - "☐ Внешние ключи на все relations"
  - "☐ Индексы на FK и поля частых запросов"
  - "☐ Заметки (Note:) объясняют compliance-критичные таблицы"
  - "☐ Синтаксис валиден (проверка через dbdiagram.io)"
```

## Артефакт

[docs/erd.dbml](../erd.dbml)

## Итог

Полная схема БД с 30+ таблицами, разнесёнными по 9 схемам PostgreSQL. Покрывает все известные на Phase 0 бизнес-сценарии, включая compliance-критичные сущности (commission_log, fiscal_receipts, mark_codes, user_consents, audit_log).

## Замечания

ERD — **снапшот на Phase 0**. Изменения в Phase 1+ требуют:
1. Новой Flyway-миграции в `backend/src/main/resources/db/migration/`.
2. Обновления `docs/erd.dbml` в том же PR.
3. ADR при значительных изменениях (например, добавление новой таблицы, влияющей на несколько модулей).
