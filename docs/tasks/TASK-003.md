```yaml
id: TASK-003
title: "ADR-003: выбор очереди сообщений (RabbitMQ vs Kafka)"
phase: 0
module: meta
priority: high
depends_on: [TASK-001]
estimated_hours: 1
status: completed
completed_at: 2026-05-07

context: |
  Сценарии использования в TrueMarket:
    - notifications (push/email) — fire-and-forget
    - события заказа между модулями
    - резервирование/передача mark_codes в Честный ЗНАК с ретраями
    - входящие webhooks от ЮKassa
    - audit-log как поток событий

  Нужен выбор между RabbitMQ (work-queues, pub/sub) и Kafka (event-streaming).

acceptance_criteria:
  - given: "сравнение RabbitMQ и Kafka"
    when: "проанализированы 10+ параметров"
    then: "выбор зафиксирован в ADR-003"

  - given: "решение принято"
    when: "появляются интеграции (catalog, payments, delivery)"
    then: "очереди именуются по конвенции <module>.<event>.<version>, есть DLQ"

  - given: "появится event-streaming use-case"
    when: "будут достигнуты триггеры"
    then: "Kafka вводится отдельным ADR — RabbitMQ остаётся как work-queue для команд"

technical_notes: |
  Решение: RabbitMQ 3.13+ с плагинами `rabbitmq_management` и `rabbitmq_prometheus`.
  Отложенные сообщения — TTL + DLX (нативно), без community-плагина.

  Правила:
    - все очереди durable, сообщения persistent;
    - DLQ + max-retries по умолчанию 5;
    - Idempotency-key обязателен для команд (не для событий);
    - сериализация JSON через Jackson, миграция на Avro/Protobuf — по необходимости.

api_changes: []
db_changes: []

test_requirements:
  unit: []
  integration: []

definition_of_done:
  - "☐ ADR-003 создан"
  - "☐ Сравнительная таблица RabbitMQ vs Kafka заполнена"
  - "☐ Правила именования и конфигурации зафиксированы"
  - "☐ Триггеры перехода на Kafka описаны"
```

## Артефакт

[ADR-003 — Выбор очереди сообщений](../adr/ADR-003-message-queue.md)

## Итог

Принято: **RabbitMQ 3.13**. Отложенные сообщения через TTL + DLX. Kafka — при появлении настоящего event-streaming use-case.
