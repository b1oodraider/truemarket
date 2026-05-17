-- ============================================================================
-- TASK-101: auth-модуль — таблицы users, refresh_tokens, user_consents.
--
-- Источник истины: docs/erd.dbml (Section: SCHEMA auth).
-- ADR-007: Flyway + expand-contract; именование V<YYYYMMDDHHmm>__<module>_<desc>.
-- 152-ФЗ: поля ПДн помечены COMMENT ON COLUMN.
-- CLAUDE.md §13.1: password_hash = argon2id, token_hash = SHA-256.
-- ============================================================================

-- ===== Enum: user_role =====
-- Создаётся в public-схеме (без префикса) — доступен всем модулям.
-- При split auth в отдельный сервис тип останется доступен через search_path.
CREATE TYPE user_role AS ENUM (
    'buyer',
    'seller',
    'moderator',
    'admin'
);

-- ============================================================================
-- TABLE: auth.users
-- Учётные записи. soft delete через deleted_at (152-ФЗ: право на удаление).
-- ============================================================================
CREATE TABLE auth.users (
    id              uuid          NOT NULL DEFAULT gen_random_uuid(),
    email           citext        NOT NULL,
    phone           varchar(20),
    password_hash   varchar(255)  NOT NULL,
    role            user_role     NOT NULL DEFAULT 'buyer',
    email_verified  boolean       NOT NULL DEFAULT false,
    phone_verified  boolean       NOT NULL DEFAULT false,
    last_login_at   timestamptz,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz   NOT NULL DEFAULT now(),
    deleted_at      timestamptz,
    version         integer       NOT NULL DEFAULT 0,

    CONSTRAINT users_pkey          PRIMARY KEY (id),
    CONSTRAINT users_email_key     UNIQUE (email),
    CONSTRAINT users_phone_key     UNIQUE (phone)
);

COMMENT ON TABLE auth.users
    IS 'Учётные записи пользователей. 152-ФЗ: soft-delete через deleted_at + анонимизация в 30 дней.';

COMMENT ON COLUMN auth.users.email
    IS '152-ФЗ: персональные данные. citext — регистронезависимое сравнение.';

COMMENT ON COLUMN auth.users.phone
    IS '152-ФЗ: персональные данные. Опционально; нормализованный формат E.164.';

COMMENT ON COLUMN auth.users.password_hash
    IS 'argon2id (CLAUDE.md §13.1, iterations=3, mem=65536KB). Никогда не возвращается в API-ответах.';

COMMENT ON COLUMN auth.users.deleted_at
    IS '152-ФЗ: право на удаление. Soft-delete; физическая анонимизация ≤ 30 дней через scheduled job.';

COMMENT ON COLUMN auth.users.version
    IS 'Optimistic locking (ADR-007). Инкрементируется при каждом UPDATE.';

-- Индексы по ERD
CREATE INDEX idx_users_email        ON auth.users (email);
CREATE INDEX idx_users_phone        ON auth.users (phone) WHERE phone IS NOT NULL;
CREATE INDEX idx_users_role_deleted ON auth.users (role, deleted_at);

-- ============================================================================
-- TABLE: auth.refresh_tokens
-- Refresh-токены с ротацией. TTL 30 дней (CLAUDE.md §13.1).
-- rotated_from — self-FK: ссылка на предыдущий токен в цепочке ротации.
-- ============================================================================
CREATE TABLE auth.refresh_tokens (
    id            uuid          NOT NULL DEFAULT gen_random_uuid(),
    user_id       uuid          NOT NULL,
    token_hash    varchar(255)  NOT NULL,
    device_info   text,
    expires_at    timestamptz   NOT NULL,
    revoked_at    timestamptz,
    rotated_from  uuid,
    created_at    timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT refresh_tokens_pkey             PRIMARY KEY (id),
    CONSTRAINT refresh_tokens_token_hash_key   UNIQUE (token_hash),
    CONSTRAINT refresh_tokens_user_id_fkey     FOREIGN KEY (user_id)
        REFERENCES auth.users (id) ON DELETE CASCADE,
    CONSTRAINT refresh_tokens_rotated_from_fkey FOREIGN KEY (rotated_from)
        REFERENCES auth.refresh_tokens (id) ON DELETE SET NULL
);

COMMENT ON TABLE auth.refresh_tokens
    IS 'Refresh-токены с ротацией при использовании. Replay-attack detection через rotated_from chain.';

COMMENT ON COLUMN auth.refresh_tokens.token_hash
    IS 'SHA-256 хэш токена. Сам токен не хранится — только хэш для сравнения.';

COMMENT ON COLUMN auth.refresh_tokens.device_info
    IS 'User-Agent + IP + платформа в момент выпуска. Для отображения активных сессий пользователю.';

COMMENT ON COLUMN auth.refresh_tokens.rotated_from
    IS 'ID предыдущего токена в цепочке ротации. При повторном использовании старого токена — отзыв всей цепочки (replay-attack).';

-- Индексы по ERD
CREATE INDEX idx_refresh_tokens_user_id    ON auth.refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON auth.refresh_tokens (expires_at);

-- ============================================================================
-- TABLE: auth.user_consents
-- Согласия на обработку ПДн (152-ФЗ). Версионированы.
-- ============================================================================
CREATE TABLE auth.user_consents (
    id            uuid          NOT NULL DEFAULT gen_random_uuid(),
    user_id       uuid          NOT NULL,
    consent_type  varchar(64)   NOT NULL,
    version       varchar(32)   NOT NULL,
    accepted_at   timestamptz   NOT NULL DEFAULT now(),
    accepted_ip   inet          NOT NULL,
    accepted_ua   text,
    revoked_at    timestamptz,

    CONSTRAINT user_consents_pkey          PRIMARY KEY (id),
    CONSTRAINT user_consents_user_id_fkey  FOREIGN KEY (user_id)
        REFERENCES auth.users (id) ON DELETE CASCADE
);

COMMENT ON TABLE auth.user_consents
    IS '152-ФЗ: согласия пользователя версионированы. При изменении текста согласия — новая запись с новой version.';

COMMENT ON COLUMN auth.user_consents.consent_type
    IS 'Тип согласия: pdn-processing, agent-offer, marketing, tracking-share, ...';

COMMENT ON COLUMN auth.user_consents.version
    IS 'Версия текста согласия: pdn-v1.0, agent-offer-v1, ... При смене текста — новая version; старая не удаляется.';

COMMENT ON COLUMN auth.user_consents.accepted_ip
    IS '152-ФЗ: IP-адрес в момент принятия согласия. Фиксируется для compliance-аудита.';

COMMENT ON COLUMN auth.user_consents.accepted_ua
    IS 'User-Agent браузера или мобильного приложения в момент принятия согласия.';

COMMENT ON COLUMN auth.user_consents.revoked_at
    IS '152-ФЗ: право на отзыв согласия. При revoked_at IS NOT NULL — согласие отозвано; запись не удаляется.';

-- Индексы по ERD
CREATE INDEX idx_user_consents_user_consent    ON auth.user_consents (user_id, consent_type);
CREATE INDEX idx_user_consents_type_version    ON auth.user_consents (consent_type, version);
