```yaml
id: TASK-008
title: "OpenAPI 3.1 — контракт API v1 (скелет)"
phase: 0
module: meta
priority: high
depends_on: [TASK-007]
estimated_hours: 6
status: completed
completed_at: 2026-05-07

context: |
  Single source of truth для API — OpenAPI-спецификация в docs/api/openapi.yaml.
  На Phase 0 фиксируем все endpoints и базовые схемы. Полное наполнение тел запросов
  и ответов происходит по мере реализации в Phase 1+.

  Принципы (зафиксированы в info.description спецификации):
    - UUID v4 для всех ID
    - numeric(12,2) для сумм
    - ISO 8601 UTC для timestamps
    - cursor-based пагинация (limit + next_cursor)
    - RFC 7807 (application/problem+json) для ошибок
    - Bearer JWT для аутентификации

acceptance_criteria:
  - given: "openapi.yaml"
    when: "валидируется через swagger-cli validate"
    then: "проходит без ошибок"

  - given: "openapi.yaml"
    when: "загружается в Swagger UI или Postman"
    then: "корректно рендерится, можно сгенерировать клиент"

  - given: "локально запущен бэкенд"
    when: "переходим на http://localhost:8080/swagger-ui.html"
    then: "видна интерактивная документация (springdoc генерирует из аннотаций,
           openapi.yaml — отдельная истина для контракта)"

  - given: "все ресурсы из ERD"
    when: "проверяется покрытие в openapi.yaml"
    then: "есть endpoints для auth, users, sellers, categories, products, cart,
           orders, payments, delivery, reviews, admin, webhooks"

technical_notes: |
  Размер: ~700 строк YAML. Структура:
    - info + servers + security + tags
    - paths (~50 endpoints на 12 тегов)
    - components.schemas (~35 схем)
    - components.responses (стандартные 400/401/403/404/409/429)
    - components.parameters (IdempotencyKey)
    - components.securitySchemes (bearerAuth)

  Идемпотентность — заголовок Idempotency-Key обязателен для POST /orders, POST /payments/*.
  Webhooks (ЮKassa, СДЭК) — отдельный тег и эндпоинты /webhooks/*.

  ID для ссылок на TASK в описаниях endpoints — например POST /products ссылается
  на CLAUDE.md 5.3.2 и TASK-042 как образец валидации маркировки.

api_changes:
  - method: POST
    path: /auth/{register,login,refresh,logout}
    description: "Аутентификация (Phase 1)"
  - method: POST
    path: /sellers, /sellers/me/documents
    description: "Регистрация и верификация продавцов"
  - method: POST
    path: /products
    description: "Создание товара, требует mark_code для requires_marking категорий"
  - method: POST
    path: /orders, /orders/{id}/{cancel,ship,confirm-delivery}
    description: "Жизненный цикл заказа (Phase 2)"
  - method: POST
    path: /payments/{order_id}/init
    description: "Инициация платежа (Phase 2)"
  - method: POST
    path: /webhooks/{yookassa,cdek}
    description: "Входящие webhooks"

db_changes: []

test_requirements:
  unit:
    - "OpenApiConfig.openAPI() возвращает корректную конфигурацию"
  integration:
    - "GET /v3/api-docs возвращает JSON-схему"
    - "GET /swagger-ui.html отдаёт HTML"
    - "Сгенерированная схема соответствует написанному openapi.yaml в ключевых полях"

definition_of_done:
  - "☐ openapi.yaml версии 3.1.0"
  - "☐ Все основные ресурсы покрыты"
  - "☐ Схемы Problem (RFC 7807) + стандартные responses"
  - "☐ bearerAuth + публичные endpoints (login, register, categories, products GET)"
  - "☐ Cursor-based пагинация на коллекциях"
  - "☐ Описание принципов в info.description"
  - "☐ OpenApiConfig.java + springdoc настроены"
  - "☐ Swagger UI работает локально"
```

## Артефакты

- [docs/api/openapi.yaml](../api/openapi.yaml)
- [backend/.../common/config/OpenApiConfig.java](../../backend/src/main/java/ru/truemarket/common/config/OpenApiConfig.java)
- [application.yaml — springdoc настройки](../../backend/src/main/resources/application.yaml)

## Итог

Полный контракт API на ~50 endpoints. На Phase 1+ детали схем достраиваются параллельно с реализацией. Все breaking changes в openapi.yaml требуют отметки в PR template (раздел "Чеклист → OpenAPI обновлён").

## Замечания

Соотношение между **openapi.yaml** и **springdoc-аннотациями**:
- `openapi.yaml` — единый источник истины для контракта.
- springdoc-аннотации в коде — для удобства разработки (Swagger UI на localhost).
- В Phase 1+ можно настроить **contract-first**: генерировать DTO из openapi.yaml через `openapi-generator-maven-plugin`. Решается отдельным ADR при необходимости.
