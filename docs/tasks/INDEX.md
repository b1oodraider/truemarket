# Реестр задач TrueMarket

> Single source of truth для статусов всех технических задач.
> При создании новой задачи — добавь строку в нужную секцию.
> При смене статуса — обнови этот файл в том же PR.

Статусы:
- 🔵 TODO — не начата
- 🟡 IN_PROGRESS — в работе
- 🟢 DONE — завершена и смержена
- ⏸️ BLOCKED — заблокирована (см. описание в TASK-файле)
- ❌ CANCELLED — отменена (см. описание в TASK-файле)

---

## Phase 0 — Фундамент

| ID | Название | Статус | Артефакт |
|---|---|---|---|
| [TASK-001](./TASK-001.md) | ADR-001: выбор языка бэкенда | 🟢 DONE | [ADR-001](../adr/ADR-001-backend-language.md) |
| [TASK-002](./TASK-002.md) | ADR-002: выбор поискового движка | 🟢 DONE | [ADR-002](../adr/ADR-002-search-engine.md) |
| [TASK-003](./TASK-003.md) | ADR-003: выбор очереди сообщений | 🟢 DONE | [ADR-003](../adr/ADR-003-message-queue.md) |
| [TASK-004](./TASK-004.md) | Скелет репозитория + Spring Boot основа | 🟢 DONE | `backend/`, корневые файлы |
| [TASK-005](./TASK-005.md) | Docker Compose локальный стек | 🟢 DONE | `infra/docker-compose.yml` |
| [TASK-006](./TASK-006.md) | CI/CD: lint + test пайплайны | 🟢 DONE | `.github/workflows/` |
| [TASK-007](./TASK-007.md) | ERD в DBML | 🟢 DONE | [erd.dbml](../erd.dbml) |
| [TASK-008](./TASK-008.md) | OpenAPI скелет | 🟢 DONE | [openapi.yaml](../api/openapi.yaml) |

**Дополнительные ADR Phase 0 (не были в исходном списке, добавлены по ходу):**

| ID | Название | Статус |
|---|---|---|
| — | [ADR-004](../adr/ADR-004-commission-bonus-stacking.md): правила суммирования бонусов | 🟢 DONE |
| — | [ADR-005](../adr/ADR-005-tracking-aggregator.md): стратегия трекинга | 🟢 DONE |
| — | [ADR-006](../adr/ADR-006-modular-monolith.md): модульный монолит | 🟢 DONE |
| — | [ADR-007](../adr/ADR-007-db-migrations.md): миграции БД | 🟢 DONE |
| — | [ADR-008](../adr/ADR-008-fiscalization-strategy.md): фискализация 54-ФЗ | 🟢 DONE |

---

## Phase 1 — Ядро (MVP auth + catalog)

> **Это roadmap.** Полные `TASK-NNN.md` пишутся в момент начала работы по правилу CLAUDE.md §16. Сейчас существует только этот список с зависимостями и статусами.

| ID | Название | Статус |
|---|---|---|
| [TASK-101](./TASK-101.md) | Auth: миграции БД (users, refresh_tokens, user_consents) | 🟡 IN_PROGRESS |
| TASK-102 | Auth: регистрация покупателя | 🔵 TODO |
| TASK-103 | Auth: логин + JWT (access/refresh) | 🔵 TODO |
| TASK-104 | Auth: ротация refresh-токенов | 🔵 TODO |
| TASK-105 | Auth: пароли — argon2id + haveibeenpwned check | 🔵 TODO |
| TASK-106 | Auth: middleware валидации JWT, RBAC | 🔵 TODO |
| TASK-107 | Auth: rate-limit на login/register | 🔵 TODO |
| TASK-108 | Catalog: миграции (categories, products, product_images) | 🔵 TODO |
| TASK-109 | Catalog: CRUD категорий (admin) | 🔵 TODO |
| TASK-110 | Catalog: CRUD товаров (seller) | 🔵 TODO |
| TASK-111 | Catalog: загрузка фото в S3/MinIO | 🔵 TODO |
| TASK-112 | Catalog: индексация в Meilisearch | 🔵 TODO |
| TASK-113 | Catalog: поиск, фасеты, фильтры | 🔵 TODO |
| TASK-114 | Sellers: регистрация ИП/ООО | 🔵 TODO |
| TASK-115 | Sellers: загрузка верификационных документов | 🔵 TODO |
| TASK-116 | Sellers: согласие на агентский договор | 🔵 TODO |
| TASK-117 | Catalog: маркировка (mark_codes) для requires_marking категорий | 🔵 TODO |

(детализация Phase 1 — после Чекпоинта 0)

---

## Phase 2 — Транзакции

Список задач формируется после Чекпоинта 1. Главные блоки: orders.*, payments.*, fiscal_receipts, commissions, notifications (push/email).

## Phase 3 — Доверие

(после Чекпоинта 2)

## Phase 4 — Мобильные

(после Чекпоинта 3)

## Phase 5 — Аналитика и мониторинг

(после Чекпоинта 4)
