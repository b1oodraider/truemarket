-- ============================================================================
-- Initial migration: расширения и схемы по модулям.
-- ADR-007: один модуль = одна схема в PostgreSQL (на старте).
-- ============================================================================

-- ===== PostgreSQL extensions =====
CREATE EXTENSION IF NOT EXISTS pgcrypto;     -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS citext;       -- case-insensitive text (email)
CREATE EXTENSION IF NOT EXISTS pg_trgm;      -- GIN-индексы для подстрочного поиска

-- ===== Schemas (по одной на модуль) =====
CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS catalog;
CREATE SCHEMA IF NOT EXISTS orders;
CREATE SCHEMA IF NOT EXISTS payments;
CREATE SCHEMA IF NOT EXISTS delivery;
CREATE SCHEMA IF NOT EXISTS verification;
CREATE SCHEMA IF NOT EXISTS reviews;
CREATE SCHEMA IF NOT EXISTS analytics;
CREATE SCHEMA IF NOT EXISTS notifications;
CREATE SCHEMA IF NOT EXISTS admin;

COMMENT ON SCHEMA auth          IS 'Аутентификация и авторизация';
COMMENT ON SCHEMA catalog       IS 'Товары, категории, маркировка';
COMMENT ON SCHEMA orders        IS 'Заказы и lifecycle';
COMMENT ON SCHEMA payments      IS 'Эскроу, комиссии, фискализация (54-ФЗ)';
COMMENT ON SCHEMA delivery      IS 'Трекинг, уровни продавца';
COMMENT ON SCHEMA verification  IS 'Верификация продавцов и товаров';
COMMENT ON SCHEMA reviews       IS 'Отзывы и анти-накрутка';
COMMENT ON SCHEMA analytics     IS 'Метрики продавца (read-only)';
COMMENT ON SCHEMA notifications IS 'Push, email, sms — история отправлений';
COMMENT ON SCHEMA admin         IS 'Модерация, споры, audit-log';

-- ===== Search path для приложения =====
-- Приложение работает с явными именами схем (catalog.products, orders.orders, ...)
-- search_path остаётся public для системных таблиц (Flyway, Spring Modulith events).
