# ADR-007 — Стратегия миграций БД и совместимости

- **Статус:** Accepted
- **Дата:** 2026-05-07
- **Автор:** Tech Lead

## Контекст

CLAUDE.md раздел 8.1 запрещает изменения схемы БД вручную и требует применения только через миграции. Раздел 12 (стоп-условия) пункт 1 — изменение схемы для уже работающего модуля требует явной остановки и согласования.

Нужно зафиксировать инструмент, формат именования, и правила совместимости (zero-downtime для продакшена).

## Решение

1. **Инструмент: Flyway 10+** (Community Edition).
2. **Размещение:** `backend/src/main/resources/db/migration/`.
3. **Именование:** `V<YYYYMMDDHHmm>__<module>_<short_desc>.sql`, например:
   `V202605071230__catalog_create_products.sql`.
4. **Шифрование секретов миграций отключено** (никаких секретов в SQL).
5. **Repeatable migrations** — для функций/представлений: `R__<name>.sql`.
6. **Стратегия совместимости — expand-contract** для продакшена.
7. **Один модуль = одна схема** в PostgreSQL (`auth.users`, `catalog.products` и т. д.) — на старте; при split на сервисы это превращается в отдельные БД.

## Структура папок миграций

```
backend/src/main/resources/db/
└── migration/
    ├── V202605071200__init_schemas.sql            // CREATE SCHEMA auth, catalog, orders, ...
    ├── V202605071201__init_extensions.sql         // pgcrypto, uuid-ossp, citext
    ├── V202605071202__auth_create_users.sql
    ├── V202605071203__auth_create_refresh_tokens.sql
    ├── V202605071204__catalog_create_categories.sql
    ├── V202605071205__catalog_create_products.sql
    └── ...
```

## Правила безопасной миграции (expand-contract)

Любое изменение, потенциально несовместимое с работающим приложением, разбивается на **минимум две миграции**:

### Этап 1 (expand) — расширение схемы
Например, переименование колонки `price` в `price_amount`:
```sql
-- V_expand: добавить новую колонку как nullable, скопировать данные
ALTER TABLE catalog.products ADD COLUMN price_amount NUMERIC(12,2);
UPDATE catalog.products SET price_amount = price WHERE price_amount IS NULL;
```
Параллельно — релиз приложения, которое **пишет в обе** колонки.

### Этап 2 (contract) — сужение схемы
После того как все инстансы приложения обновлены и больше не читают `price`:
```sql
-- V_contract: удалить старую колонку
ALTER TABLE catalog.products DROP COLUMN price;
ALTER TABLE catalog.products ALTER COLUMN price_amount SET NOT NULL;
```

**Никогда** в одной миграции:
- `DROP COLUMN` + переименование используемой колонки.
- `ALTER COLUMN ... TYPE` несовместимым типом.
- Удаление таблицы, на которую идут запросы.

## Запреты

- Запрещено в продакшене: `pg_advisory_lock` на длинные операции без таймаута.
- Запрещено: `CREATE INDEX` без `CONCURRENTLY` на таблицах > 100 МБ.
- Запрещено: миграции, которые блокируют таблицу более 5 секунд (используй `lock_timeout`).
- Запрещено: ручные `psql` в проде.

Шаблон CREATE INDEX:
```sql
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_orders_buyer_status
    ON orders.orders (buyer_id, status)
    WHERE status IN ('paid', 'confirmed', 'shipped');
```
(`CONCURRENTLY` не работает внутри транзакции — Flyway-миграция должна быть помечена `-- flyway:transactional=false`.)

## Обратимость миграций

Flyway Community **не поддерживает** автоматический rollback. Стратегия:
1. Каждый CHANGE-релиз сопровождается **down-скриптом** в `docs/runbooks/db-rollback/`. Это не Flyway-миграция, а runbook.
2. На staging обязательная проверка `make db-migrate && make db-rollback-test` (применение reverse-скрипта на тестовой БД).
3. В продакшене rollback осуществляется через **новую** миграцию (накатываемую вперёд), а не через откат предыдущей.

## Версионирование данных продавца

Сущности с долгой историей (`Product`, `Seller`, `Order`) используют:
- `created_at`, `updated_at` — обязательно.
- `version` (integer, optimistic locking) — для сущностей с возможностью гонок.
- `deleted_at` (soft delete) — для пользовательских данных (требование 152-ФЗ для "права на удаление" — см. CLAUDE.md 5.3.3).

## Тесты миграций

- Каждая миграция запускается на чистой БД через Testcontainers перед merge.
- Нагрузочное тестирование миграций (на снапшоте размера прода) — обязательно для ALTER на таблицах > 100k строк.

## Правило для `payment_log` и `commission_log`

CLAUDE.md раздел 13.5 требует immutable audit-таблицы. Реализация:
```sql
CREATE TABLE payments.commission_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL,
    base_rate NUMERIC(5,4) NOT NULL,
    bonuses_jsonb JSONB NOT NULL,
    final_rate NUMERIC(5,4) NOT NULL,
    computed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

REVOKE UPDATE, DELETE ON payments.commission_log FROM PUBLIC;
REVOKE UPDATE, DELETE ON payments.commission_log FROM truemarket_app;

-- триггер-защита от UPDATE/DELETE даже от суперпользователя
CREATE OR REPLACE FUNCTION payments.deny_modification()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'commission_log is append-only';
END $$;

CREATE TRIGGER commission_log_no_update
    BEFORE UPDATE OR DELETE ON payments.commission_log
    FOR EACH ROW EXECUTE FUNCTION payments.deny_modification();
```

## Последствия

### Положительные
- Безопасные миграции в проде.
- Нет «магических» ALTER в продакшене.
- Audit-таблицы защищены на уровне БД.

### Отрицательные / риски
- Каждая семантически «простая» миграция превращается в 2–3 шага (expand-contract). Это инвестиция в безопасность.
- Соло-разработчик может соблазниться объединить шаги. Митигация: pre-commit hook + checklist в PR.

## Альтернативы

- **Liquibase** — альтернатива Flyway. Отклонён: формат XML/YAML многословнее, а DSL-преимущества не компенсируют нативного SQL.
- **golang-migrate** — отклонён по совокупности с ADR-001 (выбран Java).

## История

- v1 (2026-05-07): Принят. Flyway + expand-contract + immutable audit.
