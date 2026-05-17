id: TASK-101
title: "Создать Flyway-миграцию auth-таблиц (users, refresh_tokens, user_consents)"
phase: 1
module: auth
priority: critical
depends_on: [TASK-004, TASK-007]
estimated_hours: 4
status: completed
started_at: 2026-05-17
completed_at: 2026-05-17

context: |
  Phase 1 начинается с auth-модуля как критического пути для всего остального.
  Без таблиц auth.users, auth.refresh_tokens и auth.user_consents невозможно реализовать
  регистрацию (TASK-102), JWT (TASK-103) и любую другую бизнес-логику, требующую
  авторизации.

  Таблицы строго соответствуют ERD (docs/erd.dbml). Поля, помеченные как compliance-критичные
  по 152-ФЗ (email, phone, deleted_at, accepted_ip), документируются через COMMENT ON COLUMN
  для аудита и onboarding новых разработчиков.

  Задача создаёт только схему БД — JPA-сущности и сервисный слой выносятся в TASK-102/103.
  Инициализирующие миграции схем и расширений (V202605071200) уже существуют с Phase 0.

acceptance_criteria:
  - given: "Чистый PostgreSQL 16 без таблиц auth-модуля"
    when: "Flyway применяет V202605170001__auth_create_tables.sql"
    then: "Таблицы auth.users, auth.refresh_tokens, auth.user_consents успешно созданы без ошибок"

  - given: "auth.users создана"
    when: "Проверяем наличие всех колонок по ERD"
    then: "Все 12 колонок существуют: id, email, phone, password_hash, role, email_verified, phone_verified, last_login_at, created_at, updated_at, deleted_at, version"

  - given: "auth.refresh_tokens создана"
    when: "Проверяем self-referencing FK на rotated_from"
    then: "FK refresh_tokens_rotated_from_fkey существует и ссылается на auth.refresh_tokens(id) с ON DELETE SET NULL"

  - given: "Enum user_role не существует в БД"
    when: "Применяется миграция"
    then: "CREATE TYPE user_role AS ENUM ('buyer','seller','moderator','admin') успешно выполнен"

  - given: "Таблицы auth-модуля созданы"
    when: "Проверяем индексы по ERD"
    then: "Все 7 индексов существуют (3 для users, 2 для refresh_tokens, 2 для user_consents)"

  - given: "Интеграционный тест AuthMigrationIT"
    when: "./mvnw verify -P integration"
    then: "Все 13 тест-методов зелёные на Testcontainers PostgreSQL 16"

technical_notes: |
  - Файл миграции: V202605170001__auth_create_tables.sql (ADR-007 naming: V<YYYYMMDDHHmm>)
  - user_role enum создаётся в public-схеме (без префикса схемы) — доступен всем модулям
    без cross-schema type reference. Альтернатива (auth.user_role) отклонена: при split auth
    в микросервис другие модули потеряют тип при неправильной настройке search_path.
  - citext-тип для email требует расширения citext, созданного в V202605071200.
  - inet-тип для user_consents.accepted_ip — нативный PostgreSQL, расширение не нужно.
  - FK user_consents → auth.users: ON DELETE CASCADE — при soft-delete users запись в БД
    остаётся (deleted_at проставлен), CASCADE не срабатывает; при физическом удалении
    (GDPR/152-ФЗ, ≥30 дней) согласия удаляются вместе с пользователем.
  - FK refresh_tokens.rotated_from → refresh_tokens(id): ON DELETE SET NULL — при отзыве
    старого токена цепочка ротации не обрывается; ссылка обнуляется.
  - COMMENT ON COLUMN для 152-ФЗ compliance-полей (email, phone, deleted_at, accepted_ip,
    password_hash) — требование для onboarding и compliance-аудита.
  - Тест AuthMigrationIT: чистый Testcontainers + Flyway без Spring Boot context.
    Причина: на этом этапе нет JPA-сущностей, и Hibernate ddl-auto: validate падал бы.
    Используем прямые JDBC-запросы к information_schema и pg_catalog.
  - Тест именован *IT.java → подхватывается maven-failsafe, запускается через -P integration.

api_changes: []

db_changes:
  - type: migration
    file: backend/src/main/resources/db/migration/V202605170001__auth_create_tables.sql
    description: >
      CREATE TYPE user_role AS ENUM; CREATE TABLE auth.users (12 колонок, PK, 2 UNIQUE);
      CREATE TABLE auth.refresh_tokens (8 колонок, self-FK на rotated_from);
      CREATE TABLE auth.user_consents (8 колонок, FK на auth.users);
      7 индексов; COMMENT ON TABLE/COLUMN для compliance-полей

test_requirements:
  unit: []
  integration:
    - "AuthMigrationIT: таблица auth.users существует после миграции"
    - "AuthMigrationIT: таблица auth.refresh_tokens существует после миграции"
    - "AuthMigrationIT: таблица auth.user_consents существует после миграции"
    - "AuthMigrationIT: auth.users содержит все 12 колонок по ERD"
    - "AuthMigrationIT: auth.refresh_tokens содержит все 8 колонок по ERD"
    - "AuthMigrationIT: auth.user_consents содержит все 8 колонок по ERD"
    - "AuthMigrationIT: enum user_role существует в pg_type"
    - "AuthMigrationIT: enum user_role имеет ровно 4 значения в правильном порядке"
    - "AuthMigrationIT: все 3 индекса auth.users существуют"
    - "AuthMigrationIT: все 2 индекса auth.refresh_tokens существуют"
    - "AuthMigrationIT: все 2 индекса auth.user_consents существуют"
    - "AuthMigrationIT: UNIQUE-ограничение users_email_key существует"
    - "AuthMigrationIT: self-FK refresh_tokens_rotated_from_fkey существует"

definition_of_done:
  - "☑ SQL-миграция применяется без ошибок на чистом Postgres 16"
  - "☑ Код проходит ./mvnw spotless:check"
  - "☑ AuthMigrationIT: 13 тест-методов зелёные (./mvnw verify -P integration)"
  - "☑ Все поля соответствуют ERD (docs/erd.dbml) — расхождений нет"
  - "☑ COMMENT ON COLUMN присутствует для 152-ФЗ-критичных полей"
  - "☑ INDEX.md обновлён: TASK-101 → 🟢 DONE"
  - "☑ PR description заполнен по шаблону .github/pull_request_template.md"
  - "☑ Нет TODO без ссылки на тикет"
