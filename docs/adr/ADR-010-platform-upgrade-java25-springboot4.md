# ADR-010 — Платформенный апгрейд: Java 25 + Spring Boot 4 + Spring Modulith 2

- **Статус:** Proposed
- **Дата:** 2026-05-17
- **Автор:** Tech Lead
- **Supersedes:** ADR-001 (в части версий: Java 21 → 25, Spring Boot 3 → 4; выбор языка/фреймворка Java+Spring сохраняется)
- **Amends:** ADR-006 (линейка Spring Modulith 1.2.x → 2.0.x), ADR-007 (Flyway 10 → 12)

## Контекст

ADR-001 зафиксировал Java 21 + Spring Boot 3.3.5, ADR-006 — Spring Modulith 1.2.5,
ADR-007 — Flyway 10+. С момента фиксации (2026-05-07) вышли новые стабильные
релизы; Dependabot открыл 16 PR, из них кластер major-обновлений:

- Spring Boot 3.3.5 → **4.0.6** (GA-линейка 4.0.x);
- Spring Modulith 1.2.5 → **2.0.6** (линейка под Spring Boot 4 / Spring Framework 7);
- eclipse-temurin 21 → **25** (Java 25 — текущий LTS, сентябрь 2025);
- springdoc-openapi 2.6.0 → **3.0.3** (линейка под Spring Boot 4);
- Flyway 10.20.1 → **12.6.1**, Testcontainers 1.20.3 → **2.0.5**, и др.

Силы, давящие на решение:
- **Окно минимального риска.** Кодовая база сейчас — скелет Phase 0/Phase 1
  (`TrueMarketApplication`, `package-info`, `SecurityConfig`, `OpenApiConfig`,
  `AuthMigrationIT`, одна миграция). Бизнес-логики нет. Чем позже апгрейд —
  тем дороже миграция (Jakarta, Security DSL, removed APIs).
- **Срок поддержки.** Spring Boot 3.3.x — OSS-поддержка истекает; 4.0.x —
  актуальная линейка с длинным горизонтом. Java 25 LTS — поддержка до 2031+.
- **CLAUDE.md §обязательства:** смена Spring Boot/Modulith — только отдельным
  ADR. Это он и есть; Product Owner явно запросил переход на новые стабильные
  версии.
- **Безопасность.** Часть HIGH/CRITICAL CVE (TASK-099) — транзитивные deps,
  закрываются актуальными BOM Spring Boot 4 / minor-patch группой.

## Решение

Переходим на следующий зафиксированный стек бэкенда:

| Компонент | Было | Стало |
|---|---|---|
| Java (runtime + compile) | 21 (temurin-21.0.5) | **25 LTS** (temurin-25) |
| Spring Boot parent | 3.3.5 | **4.0.6** |
| Spring Modulith BOM | 1.2.5 | **2.0.6** |
| springdoc-openapi | 2.6.0 | **3.0.3** |
| Flyway (core + maven-plugin) | 10.20.1 | **12.6.1** |
| Testcontainers BOM | 1.20.3 | **2.0.5** |
| OWASP dependency-check | 11.1.0 | **12.2.2** |
| Spotless | 2.43.0 | **3.5.1** |
| JaCoCo | 0.8.12 | **0.8.14** |
| logstash-logback-encoder | 8.0 | **9.0** |
| jjwt | 0.12.6 | **0.13.0** |
| postgresql JDBC | 42.7.4 | **42.7.11** |
| CI actions | checkout@4, codeql@3, cache@4, docker/login@3, gh-release@2 | **@6 / @4 / @5 / @4 / @3** |

Резолюция: выбор языка/фреймворка из ADR-001 (Java + Spring, не Go) **остаётся
в силе**; меняются только мажорные версии. Spring Modulith 2.x — прямой
преемник линейки из ADR-006, архитектурные правила (границы модулей,
`@ApplicationModule`, `ModularityTest.verify()`) **не меняются**.

Артефактные координаты сохраняются (`spring-boot-starter-*`,
`spring-modulith-starter-core`, `springdoc-openapi-starter-webmvc-ui`).
Resilience4j: переход на артефакт под Spring Boot 4 (`resilience4j-spring-boot3`
→ актуальный совместимый), версия — по факту валидации.

**Условие принятия:** решение валидируется зелёным `./mvnw -P integration verify`
(Spotless + ModularityTest + TrueMarketApplicationTests + AuthMigrationIT 13/13
+ JaCoCo) на новом стеке. Без зелёной сборки ADR остаётся Proposed.

## Обоснование

| Критерий | Остаться на 3.3.5/Java 21 | Перейти на 4.0.6/Java 25 |
|---|---|---|
| Стоимость миграции сейчас | 0 | Низкая (скелет, нет бизнес-кода) |
| Стоимость миграции в Phase 3+ | — | Высокая (Jakarta, Security 7, сотни классов) |
| Срок поддержки | Истекает | Длинный горизонт |
| CVE-поверхность | Растёт (старые BOM) | Свежие управляемые версии |
| Риск регресса | 0 | Управляемый: 3 тест-класса + context load — полная поверхность |

Главный довод — **сейчас уникальное окно**: апгрейд major-версий на скелете
почти бесплатен и полностью покрывается существующими тестами. Отложить =
заплатить кратно дороже на наполненной кодовой базе.

## Последствия

### Положительные
- Длинный горизонт поддержки (Boot 4.0.x, Java 25 LTS).
- Снижение CVE-поверхности (свежие BOM, minor-patch группа, TASK-099 синергия).
- Spring Modulith 2.x: улучшённый event-store/externalization (нужно в Phase 2).
- Закрывается 16 Dependabot-PR одним согласованным изменением.

### Отрицательные / риски
- Spring Boot 4 / Spring Framework 7: возможны removed/relocated API. Митигация:
  код-поверхность минимальна (Security lambda-DSL и springdoc bean —
  стабильные между 3↔4), полный прогон тестов перед merge.
- Resilience4j под Boot 4 может потребовать смены артефакта/версии. Митигация:
  валидация сборкой; при несовместимости — зафиксировать факт в TASK-098.
- Spotless 3.x (Google Java Format новее) может переформатировать код.
  Митигация: `spotless:apply` в рамках TASK-098, единый стиль.
- Flyway 12: проверить применение существующих миграций (AuthMigrationIT).
- Java 25 на self-hosted/CI: обновить `.tool-versions`, базовый Docker-образ.

### Что меняется в проекте
- `backend/pom.xml`: parent 4.0.6, `java.version`/`maven.compiler.release` = 25,
  версии-properties (modulith, flyway, springdoc, testcontainers, jjwt, jacoco,
  owasp, logstash, spotless).
- `backend/Dockerfile`: `eclipse-temurin:25-jdk-alpine` / `25-jre-alpine`
  (поверх TASK-099 хардинга — `apk upgrade` сохраняется).
- `.tool-versions`: `java temurin-25...`.
- CI workflow actions: checkout@6, codeql@4, cache@5, docker/login@4,
  gh-release@3 (отдельные мелкие правки или Dependabot-мерж после ADR).
- CLAUDE.md §4/§17 и история — обновляются отдельным `chore(claude-md)` PR
  (этот ADR имеет приоритет до синхронизации текста CLAUDE.md).
- Реализация — TASK-098 (отдельная ветка/PR, валидация сборкой).

## Альтернативы (рассмотрены и отклонены)

- **Остаться на Spring Boot 3.3.5 / Java 21.** Отклонено: истекающая
  поддержка, растущая CVE-поверхность, кратный рост стоимости миграции позже.
- **Поэтапно мержить 16 Dependabot-PR по одному.** Отклонено: major-обновления
  взаимозависимы (Boot 4 ↔ Modulith 2 ↔ springdoc 3 ↔ Java baseline);
  по-отдельности дают красный CI и конфликты. Один согласованный апгрейд
  детерминированнее.
- **Java 25 без Spring Boot 4 (только LTS-рантайм).** Отклонено: половинчато,
  не снимает CVE/поддержку Boot 3.x; Boot 4 всё равно неизбежен.
- **Spring Boot 4 на Java 21 (без перехода на Java 25).** Допустимо (Boot 4
  baseline — Java 17), но упускает окно бесплатного перехода на актуальный LTS;
  две миграции вместо одной.

## Триггеры пересмотра

- Невозможность совместить Resilience4j / иной критичный модуль со Spring
  Boot 4 без существенного рефакторинга → откат до Boot 3.3.x фиксируется
  новым ADR с обоснованием.
- Выход Spring Boot 4.1.x LTS / Java следующего LTS — плановый minor-апгрейд
  без нового ADR (в рамках этого решения).

## История

- v1 (2026-05-17): **Proposed.** Java 25 + Spring Boot 4.0.6 + Modulith 2.0.6 +
  сопутствующий апгрейд. Локальная валидация невозможна (в dev-среде нет
  JDK 25). Условие принятия (→ Accepted) — зелёный CI-прогон TASK-098 на
  JDK 25 (lint + test-backend + integration). До этого решение не финально.
