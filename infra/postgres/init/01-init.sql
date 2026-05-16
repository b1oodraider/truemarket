-- Init-скрипт PostgreSQL для локального dev. Выполняется один раз при создании volume.
-- Реальные миграции — через Flyway (см. backend/src/main/resources/db/migration/).
-- Этот скрипт только настраивает БД и роли.

ALTER DATABASE truemarket SET timezone TO 'UTC';

-- Расширения создаются Flyway-миграцией V202605071200, но дублируем здесь
-- чтобы dev-окружение поднималось предсказуемо.
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
