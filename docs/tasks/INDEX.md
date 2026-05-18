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

**ADR Phase 1:**

| ID | Название | Статус |
|---|---|---|
| — | [ADR-009](../adr/ADR-009-encrypt-sensitive-db-fields.md): шифрование чувствительных полей БД | 🟢 DONE |
| — | [ADR-010](../adr/ADR-010-platform-upgrade-java25-springboot4.md): платформенный апгрейд Java 25 + Spring Boot 4 | 🟢 ACCEPTED |

---

## Phase 1 — Ядро (MVP auth + catalog)

> **Это roadmap.** Полные `TASK-NNN.md` пишутся в момент начала работы по правилу CLAUDE.md §16. Сейчас существует только этот список с зависимостями и статусами.

| ID | Название | Статус |
|---|---|---|
| [TASK-098](./TASK-098.md) | Платформенный апгрейд Java 25 + Spring Boot 4 + Modulith 2 (ADR-010) | 🟢 DONE |
| [TASK-099](./TASK-099.md) | Security-хардинг Docker base-image + runbook NVD_API_KEY | 🟢 DONE |
| [TASK-100](./TASK-100.md) | Устранить lint/test-долг Phase 0, блокирующий CI Phase 1 | 🟢 DONE |
| [TASK-101](./TASK-101.md) | Auth: миграции БД (users, refresh_tokens, user_consents) | 🟢 DONE |
| [TASK-102](./TASK-102.md) | Auth: регистрация покупателя (argon2id + JWT-выпуск) | 🟢 DONE |
| [TASK-103](./TASK-103.md) | Auth: `/login` + `/refresh` (stateless, переиспользует TokenService из 102) | 🟢 DONE |
| [TASK-104](./TASK-104.md) | Auth: персистентная ротация refresh + replay-detection + /logout | 🟢 DONE |
| TASK-105 | Auth: breach-check (haveibeenpwned) + смена пароля + политика | 🔵 TODO |
| TASK-106 | Auth: middleware валидации JWT, RBAC | 🔵 TODO |
| TASK-107 | Auth: rate-limit/429 на `/login` + `/register` | 🔵 TODO |

> Пере-скоуп TASK-103/104/105/107 одобрен PO (2026-05-18): минимум argon2id+JWT
> уже сделан в TASK-102 под контракт openapi; см. TASK-102.md `context`.
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

### Backlog (трекинг, не Phase 1)

| ID | Название | Статус | Прим. |
|---|---|---|---|
| TASK-097 | Security-scan долг Phase 5: (а) Trivy — остаточные fixable CRITICAL/HIGH CVE в base-image `eclipse-temurin:25-jre-alpine`; (б) OWASP — fast-fail на стеке Boot 4 (генерация SARIF/конфиг плагина после ADR-010) | 🔵 BACKLOG (Phase 5) | Решение PO (1.B): принять как non-blocking (`security-scan.yml` не блокирует PR by design). NVD_API_KEY установлен — 30-мин таймаут устранён (rerun ~1мин); остаточный OWASP fast-fail и Trivy CVE — в Phase 5: pin digest/distroless, разбор OWASP-плагина на Boot 4, обоснованные suppressions с тикетами. Гейты НЕ ослабляются. |
| — | OWASP `NVD_API_KEY` секрет | 🟢 DONE | Установлен владельцем; таймаут устранён (см. [runbook](../runbooks/security-scan-nvd-api-key.md)). |
| TASK-096 | Извлечь shared web-инфраструктуру в `common`: RFC7807 ProblemDetail-фабрика (CLAUDE.md §11, нужна всем модулям) + XFF-aware ClientIp-резолвер | 🔵 BACKLOG | Сейчас в auth (1 потребитель) — экстракция была бы премат-абстракцией + перестройка модулей (нужен shared/open Modulith-модуль). Делать при 2-м потребителе (Rule of Three): тогда — `common.web`, `@Modulithic(sharedModules="common")`. |
| TASK-095 | Модульная подача Spring Security правил | 🔵 BACKLOG (TASK-106) | Сейчас `common.SecurityConfig` хардкодит auth-пути — при росте модулей станет god-config. Перепроектировать в TASK-106 (JWT-фильтр/RBAC) — каждый модуль вносит свои правила. |
