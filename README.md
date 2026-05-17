# TrueMarket

> B2C маркетплейс физических товаров с фокусом на честность алгоритмов, верифицированные отзывы и единую оплату.
> Целевой сегмент старта: швейные производства, текстильные цеха, ювелирные и рукодельные мастерские (РФ).

**Главный документ проекта:** [`CLAUDE.md`](./CLAUDE.md). Все правила, договорённости и архитектурные принципы — там.

---

## Быстрый старт для разработчика

### Требования

- Java 21 (Temurin / Liberica) — рекомендуется через [asdf](https://asdf-vm.com/) или [SDKMAN!](https://sdkman.io/)
- Docker 24+ и Docker Compose v2
- Maven Wrapper (`./mvnw`) — Maven устанавливать отдельно не нужно
- Node.js 20+ и pnpm (для веб-админки)
- Flutter 3.22+ (для мобильных приложений)
- Make

Версии всех инструментов закреплены в [`.tool-versions`](./.tool-versions).

### Первый запуск

```bash
git clone <repo>
cd truemarket

# Установить версии инструментов (если используется asdf)
asdf install

# Поднять локальный стек (Postgres + Redis + MinIO + Meilisearch + RabbitMQ)
make infra-up

# Применить миграции БД
make db-migrate

# Засеять тестовые данные
make db-seed

# Запустить бэкенд локально
make backend-run
```

После этого:
- API: http://localhost:8080/api/v1
- Swagger UI: http://localhost:8080/swagger-ui.html
- MinIO console: http://localhost:9001 (login: `minioadmin` / `minioadmin`)
- RabbitMQ management: http://localhost:15672 (login: `guest` / `guest`)
- Meilisearch: http://localhost:7700

### Проверка перед PR

```bash
make ci-local
```

Запускает всё, что запустит CI: линтер, unit-тесты, интеграционные тесты.

---

## Структура репозитория

```
truemarket/
├── CLAUDE.md              # главный документ проекта (single source of truth)
├── README.md              # этот файл
├── .tool-versions         # фиксация версий тулов
├── docs/
│   ├── adr/               # Architecture Decision Records
│   ├── api/               # OpenAPI спецификации
│   ├── runbooks/          # инструкции для инцидентов
│   ├── tasks/             # технические задания (TASK-NNN.md)
│   └── erd.dbml           # схема БД в DBML
├── backend/               # Spring Boot 3 + Java 21
│   ├── src/main/java/ru/truemarket/
│   │   ├── auth/          # аутентификация, JWT, роли
│   │   ├── catalog/       # товары, категории, поиск, маркировка
│   │   ├── orders/        # заказы и жизненный цикл
│   │   ├── payments/      # эскроу, комиссии, фискализация
│   │   ├── delivery/      # трекинг, уровни продавца
│   │   ├── verification/  # верификация продавцов и товаров
│   │   ├── reviews/       # отзывы и анти-накрутка
│   │   ├── analytics/     # метрики продавца
│   │   ├── notifications/ # push, email, sms
│   │   ├── admin/         # модерация, споры
│   │   └── common/        # общие классы (без бизнес-логики)
│   ├── src/main/resources/db/migration/  # Flyway миграции
│   └── src/test/                          # тесты
├── mobile/
│   ├── buyer/             # Flutter — покупатель
│   ├── seller/            # Flutter — продавец
│   └── shared/            # общие пакеты Flutter
├── web-admin/             # React 18 + TypeScript 5 (админ-панель)
├── infra/
│   ├── docker-compose.yml      # локальный dev-стек
│   ├── docker-compose.test.yml # стек для интеграционных тестов
│   ├── k8s/                    # манифесты Kubernetes
│   └── monitoring/             # Prometheus, Grafana, Loki
└── .github/workflows/     # CI/CD
```

---

## Архитектурные решения

Все решения зафиксированы в [`docs/adr/`](./docs/adr/):

- [ADR-001](./docs/adr/ADR-001-backend-language.md) — Java 21 + Spring Boot 3
- [ADR-002](./docs/adr/ADR-002-search-engine.md) — Meilisearch (на старте)
- [ADR-003](./docs/adr/ADR-003-message-queue.md) — RabbitMQ
- [ADR-004](./docs/adr/ADR-004-commission-bonus-stacking.md) — правила суммирования бонусов
- [ADR-005](./docs/adr/ADR-005-tracking-aggregator.md) — прямые API российских курьеров
- [ADR-006](./docs/adr/ADR-006-modular-monolith.md) — модульный монолит на старте
- [ADR-007](./docs/adr/ADR-007-db-migrations.md) — Flyway, expand-contract стратегия
- [ADR-008](./docs/adr/ADR-008-fiscalization-strategy.md) — стратегия 54-ФЗ через ЮKassa.Чеки

---

## Compliance

Платформа подчиняется законодательству РФ и обязана соблюдать:

- **152-ФЗ** — персональные данные хранятся на серверах в РФ
- **54-ФЗ** — фискализация чеков через ОФД (на стороне ЮKassa)
- **«Честный ЗНАК»** — обязательная маркировка одежды, обуви, ювелирных изделий, текстиля

Подробности — в разделе 5.3 и 13.6 `CLAUDE.md`.

---

## Как поставить задачу

1. Создать `docs/tasks/TASK-NNN.md` по шаблону из раздела 16 `CLAUDE.md`.
2. Добавить запись в `docs/tasks/INDEX.md`.
3. Создать ветку `feature/TASK-NNN-short-desc`.
4. После реализации — PR со ссылкой `Closes TASK-NNN`.

---

## Известные риски (зафиксированы)

| # | Риск | Митигация |
|---|---|---|
| R-01 | iOS-сборка завязана на single MacBook M2 | План миграции на GitHub macOS runners при росте команды (см. [ADR-001](./docs/adr/ADR-001-backend-language.md) последствия) |
| R-02 | Зависимость от ЮKassa (single payment provider) | Архитектурная изоляция через `payments.PaymentGateway` interface, миграция на альтернативу займёт ≤ 2 недель |
| R-03 | Маркировка «Честный ЗНАК» — внешний моно-сервис, downtime блокирует продажи маркированных товаров | Графовый retry + queue, fallback на ручную модерацию |
| R-04 | 152-ФЗ — персональные данные | Хранилище в Yandex Cloud / Selectel (РФ), отдельные согласия на трансграничную передачу для опц. сервисов |

---

## Лицензия

Проприетарное ПО. Использование, копирование и распространение без явного разрешения правообладателя запрещены.
