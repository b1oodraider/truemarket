id: TASK-108
title: "Catalog: миграции БД (categories, products, product_images)"
phase: 1
module: catalog
priority: high
depends_on: [TASK-101]
estimated_hours: 4
status: completed
started_at: 2026-05-21
completed_at: 2026-05-21

context: |
  Старт модуля catalog. Нужны таблицы каталога под последующие задачи: CRUD
  категорий (TASK-109), CRUD товаров (TASK-110), фото в S3 (TASK-111),
  индексация в Meilisearch (TASK-112). Источник истины схемы — docs/erd.dbml
  (SCHEMA catalog). Только миграции (как TASK-101 для auth) — JPA-сущности
  появятся в CRUD-задачах.

  mark_codes (Честный ЗНАК) — отдельной миграцией в TASK-117 (не здесь).

design: |
  Flyway-миграция V202605210001__catalog_create_tables.sql (ADR-007,
  expand-contract; именование V<YYYYMMDDHHmm>__<module>_<desc>). Схема catalog
  уже создана в init-миграции. Таблицы строго по ERD:
   - catalog.categories: self-FK parent_id (ON DELETE RESTRICT), slug UNIQUE,
     commission_base numeric(5,4) с CHECK [0..1], requires_marking.
   - catalog.products: category_id FK→categories (RESTRICT), денормализованный
     requires_marking, price numeric(12,2), status CHECK(draft|published|archived),
     soft-delete deleted_at, optimistic version. seller_id — uuid БЕЗ FK: таблица
     verification.sellers ещё не существует (TASK-114). FK добавится отдельной
     миграцией в TASK-114 (expand-contract, ADR-007) — column-now, constraint-later.
   - catalog.product_images: product_id FK→products (ON DELETE CASCADE), s3_key.

  CHECK-ограничения (status, цена≥0, stock≥0, commission_base) — целостность на
  уровне БД (defense-in-depth, не только в сервисе).

acceptance_criteria:
  - given: "чистый PostgreSQL"
    when: "Flyway применяет миграции"
    then: "созданы catalog.categories, catalog.products, catalog.product_images со всеми колонками ERD"
  - given: "миграция применена"
    when: "проверка ограничений"
    then: "self-FK categories.parent_id; FK products.category_id; FK product_images.product_id (CASCADE); CHECK status/цены"
  - given: "миграция применена"
    when: "проверка индексов"
    then: "индексы по ERD (parent_id; seller_id, category_id, (status,deleted_at), gtin; (product_id,position))"
  - given: "verification.sellers ещё не создана"
    when: "миграция catalog"
    then: "products.seller_id создан как uuid без FK (FK — TASK-114); миграция не падает"

technical_notes: |
  - Файл: backend/src/main/resources/db/migration/V202605210001__catalog_create_tables.sql.
  - gen_random_uuid() (pgcrypto уже есть). timestamptz + now() как в auth.
  - products.status varchar(32) + CHECK IN ('draft','published','archived').
  - Индекс gtin — partial WHERE gtin IS NOT NULL (как idx_users_phone).
  - slug — UNIQUE (отдельный idx не нужен, покрыт unique-индексом).
  - CatalogMigrationIT (Testcontainers, прямой JDBC к information_schema/pg_catalog,
    БЕЗ @SpringBootTest — JPA-сущностей ещё нет; как AuthMigrationIT).
  - Catalog main-кода пока нет → @ApplicationModule/ModularityTest не затрагиваются.
  - Стек Java 25/Boot 4; ddl-auto=none (схема — Flyway).

api_changes: []  # API — в TASK-109/110

db_changes:
  - { type: migration, description: "catalog.categories, catalog.products, catalog.product_images (V202605210001)" }

test_requirements:
  integration:
    - "Flyway применяется на чистом PG без ошибок"
    - "Существуют таблицы categories/products/product_images"
    - "Полный набор колонок каждой таблицы (по ERD)"
    - "self-FK categories.parent_id; FK products.category_id; FK product_images.product_id"
    - "CHECK status товара; индексы products/(product_id,position)/categories.parent_id"
    - "products.seller_id есть, FK на verification.sellers отсутствует (TASK-114)"

definition_of_done:
  - "☑ Миграция применяется (Flyway) на чистом PG"
  - "☑ CatalogMigrationIT зелёный (./mvnw -P integration verify)"
  - "☑ Spotless зелёный; ModularityTest зелёный (catalog main-кода ещё нет)"
  - "☑ Схема строго соответствует docs/erd.dbml (catalog)"
  - "☑ Отложенный FK seller_id задокументирован (TASK-114)"
  - "☑ INDEX.md TASK-108 → DONE"
  - "☑ Нет TODO/FIXME без тикета"
