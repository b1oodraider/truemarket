```yaml
id: TASK-005
title: "Docker Compose: локальный dev-стек"
phase: 0
module: meta
priority: high
depends_on: [TASK-001, TASK-002, TASK-003]
estimated_hours: 3
status: completed
completed_at: 2026-05-07

context: |
  Локальный стек должен подниматься одной командой `make infra-up` и включать
  все внешние сервисы, нужные для разработки: PostgreSQL, Redis, RabbitMQ, MinIO, Meilisearch.

  Все credentials — для локальной разработки. В CI используются Testcontainers
  (отдельный стек не нужен). В docker-compose.test.yml — лёгкая версия с tmpfs для
  отладки интеграционных тестов локально, не обязательная для команды.

acceptance_criteria:
  - given: "чистая машина с установленным Docker"
    when: "выполняется make infra-up"
    then: "поднимаются 5 сервисов, healthchecks становятся healthy за ≤ 90 секунд"

  - given: "стек поднят"
    when: "выполняется make infra-status"
    then: "видны все контейнеры truemarket-* в состоянии running (healthy)"

  - given: "MinIO поднят"
    when: "стартует init-контейнер"
    then: "автоматически создаются bucket'ы truemarket-products и truemarket-documents"

  - given: "стек поднят"
    when: "выполняется make db-migrate"
    then: "Flyway-миграции применяются к локальной БД"

  - given: "выполнен make infra-clean"
    when: "проверяются volumes"
    then: "все persistent volumes удалены, при следующем infra-up БД создаётся с нуля"

technical_notes: |
  - PostgreSQL 16-alpine, timezone=UTC, max_connections=100
  - Redis 7-alpine, appendonly=yes, maxmemory=512mb, allkeys-lru
  - RabbitMQ 3.13-management-alpine, plugin rabbitmq_prometheus
  - MinIO RELEASE.2024-10-13, healthcheck через /minio/health/live
  - Meilisearch v1.10 + DEV-режим, MASTER_KEY=masterKey

  Порты по умолчанию: 5432 (pg), 6379 (redis), 5672+15672 (rabbit),
  9000+9001 (minio), 7700 (meili).

  docker-compose.test.yml использует порты +1 (5433, 6380, ...) чтобы не конфликтовать.

api_changes: []

db_changes:
  - type: init-script
    description: "infra/postgres/init/01-init.sql — set timezone, create extensions"

test_requirements:
  unit: []
  integration:
    - "Команда make infra-up завершается без ошибок"
    - "Все healthchecks становятся healthy"
    - "make db-migrate применяет миграции после infra-up"
    - "minio-init создаёт оба бакета и завершается"

definition_of_done:
  - "☐ infra/docker-compose.yml создан"
  - "☐ infra/docker-compose.test.yml создан"
  - "☐ Init-скрипты Postgres и enabled_plugins для RabbitMQ"
  - "☐ Все healthchecks работают"
  - "☐ Make-команды infra-* документированы и работают"
  - "☐ В README.md описаны порты и UI-адреса"
```

## Артефакты

- [docker-compose.yml](../../infra/docker-compose.yml)
- [docker-compose.test.yml](../../infra/docker-compose.test.yml)
- [infra/postgres/init/01-init.sql](../../infra/postgres/init/01-init.sql)
- [infra/rabbitmq/enabled_plugins](../../infra/rabbitmq/enabled_plugins)
- [Makefile](../../Makefile) — таргеты `infra-*`

## Итог

5 сервисов поднимаются одной командой. MinIO автоматически инициализирует бакеты. Стек для интеграционных тестов — отдельным compose-файлом с tmpfs.
