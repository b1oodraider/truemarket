-- ============================================================================
-- TASK-108: catalog-модуль — таблицы categories, products, product_images.
--
-- Источник истины: docs/erd.dbml (Section: SCHEMA catalog).
-- ADR-007: Flyway + expand-contract; именование V<YYYYMMDDHHmm>__<module>_<desc>.
-- Схема catalog создана в init-миграции (V202605071200).
-- mark_codes (Честный ЗНАК) — отдельной миграцией в TASK-117.
-- ============================================================================

-- ============================================================================
-- TABLE: catalog.categories
-- Дерево категорий (self-FK parent_id). commission_base — базовая комиссия
-- (CLAUDE.md §3). requires_marking — обязательность Честного ЗНАКа (§5.3.2).
-- ============================================================================
CREATE TABLE catalog.categories (
    id                uuid          NOT NULL DEFAULT gen_random_uuid(),
    parent_id         uuid,
    slug              varchar(128)  NOT NULL,
    name              varchar(256)  NOT NULL,
    commission_base   numeric(5, 4) NOT NULL,
    requires_marking  boolean       NOT NULL DEFAULT false,
    sort_order        integer       NOT NULL DEFAULT 0,
    created_at        timestamptz   NOT NULL DEFAULT now(),
    updated_at        timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT categories_pkey      PRIMARY KEY (id),
    CONSTRAINT categories_slug_key  UNIQUE (slug),
    CONSTRAINT categories_parent_id_fkey FOREIGN KEY (parent_id)
        REFERENCES catalog.categories (id) ON DELETE RESTRICT,
    CONSTRAINT categories_commission_base_check
        CHECK (commission_base >= 0 AND commission_base <= 1)
);

COMMENT ON TABLE catalog.categories
    IS 'Категории товаров (дерево через parent_id). commission_base — базовая комиссия (CLAUDE.md §3).';

COMMENT ON COLUMN catalog.categories.commission_base
    IS 'Базовая комиссия, доля [0..1]. Напр. 0.12 = 12% (одежда/текстиль).';

COMMENT ON COLUMN catalog.categories.requires_marking
    IS 'Честный ЗНАК (CLAUDE.md §5.3.2): при true товар нельзя создать без mark_code (TASK-117).';

CREATE INDEX idx_categories_parent_id ON catalog.categories (parent_id);

-- ============================================================================
-- TABLE: catalog.products
-- Товары продавца. soft-delete (deleted_at), optimistic locking (version).
-- seller_id — БЕЗ FK: verification.sellers создаётся в TASK-114 (expand-contract).
-- requires_marking — денормализация из categories для скорости фильтрации.
-- ============================================================================
CREATE TABLE catalog.products (
    id                uuid          NOT NULL DEFAULT gen_random_uuid(),
    seller_id         uuid          NOT NULL,
    category_id       uuid          NOT NULL,
    title             varchar(256)  NOT NULL,
    description       text,
    price_amount      numeric(12, 2) NOT NULL,
    price_currency    char(3)       NOT NULL DEFAULT 'RUB',
    stock             integer       NOT NULL DEFAULT 0,
    requires_marking  boolean       NOT NULL,
    gtin              varchar(14),
    status            varchar(32)   NOT NULL DEFAULT 'draft',
    verified_at       timestamptz,
    created_at        timestamptz   NOT NULL DEFAULT now(),
    updated_at        timestamptz   NOT NULL DEFAULT now(),
    deleted_at        timestamptz,
    version           integer       NOT NULL DEFAULT 0,

    CONSTRAINT products_pkey PRIMARY KEY (id),
    CONSTRAINT products_category_id_fkey FOREIGN KEY (category_id)
        REFERENCES catalog.categories (id) ON DELETE RESTRICT,
    CONSTRAINT products_status_check
        CHECK (status IN ('draft', 'published', 'archived')),
    CONSTRAINT products_price_amount_check CHECK (price_amount >= 0),
    CONSTRAINT products_stock_check        CHECK (stock >= 0)
);

COMMENT ON TABLE catalog.products
    IS 'Товары. soft-delete (deleted_at, 152-ФЗ/аудит), optimistic locking (version).';

COMMENT ON COLUMN catalog.products.seller_id
    IS 'FK → verification.sellers.id. Ограничение добавляется в TASK-114 (таблица ещё не создана; expand-contract, ADR-007).';

COMMENT ON COLUMN catalog.products.requires_marking
    IS 'Денормализация categories.requires_marking для скорости (Честный ЗНАК, CLAUDE.md §5.3.2).';

COMMENT ON COLUMN catalog.products.version
    IS 'Optimistic locking (ADR-007). Инкрементируется при каждом UPDATE.';

CREATE INDEX idx_products_seller_id      ON catalog.products (seller_id);
CREATE INDEX idx_products_category_id    ON catalog.products (category_id);
CREATE INDEX idx_products_status_deleted ON catalog.products (status, deleted_at);
CREATE INDEX idx_products_gtin           ON catalog.products (gtin) WHERE gtin IS NOT NULL;

-- ============================================================================
-- TABLE: catalog.product_images
-- Фото товара (S3-ключи). Каскадное удаление вместе с товаром.
-- ============================================================================
CREATE TABLE catalog.product_images (
    id          uuid          NOT NULL DEFAULT gen_random_uuid(),
    product_id  uuid          NOT NULL,
    s3_key      varchar(512)  NOT NULL,
    alt_text    varchar(256),
    width       integer,
    height      integer,
    size_bytes  integer,
    position    integer       NOT NULL DEFAULT 0,
    is_primary  boolean       NOT NULL DEFAULT false,
    created_at  timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT product_images_pkey PRIMARY KEY (id),
    CONSTRAINT product_images_product_id_fkey FOREIGN KEY (product_id)
        REFERENCES catalog.products (id) ON DELETE CASCADE
);

COMMENT ON TABLE catalog.product_images
    IS 'Фото товаров. s3_key — путь в bucket truemarket-products (хранение вне БД, CLAUDE.md §13.3).';

CREATE INDEX idx_product_images_product_position
    ON catalog.product_images (product_id, position);
