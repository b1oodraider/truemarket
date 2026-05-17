id: TASK-098
title: "Платформенный апгрейд: Java 25 + Spring Boot 4 + Modulith 2 (ADR-010)"
phase: 1
module: common
priority: high
depends_on: [TASK-099]
estimated_hours: 4
status: in_progress
started_at: 2026-05-17

context: |
  Реализация ADR-010. Product Owner запросил переход на новые стабильные
  версии; Dependabot открыл 16 PR с major-кластером (Spring Boot 4, Modulith 2,
  Java 25, springdoc 3, Flyway 12, Testcontainers 2). Кодовая база — скелет
  Phase 0/1, окно минимального риска для major-апгрейда (нет бизнес-логики).

  Один согласованный апгрейд вместо 16 разрозненных PR: major-зависимости
  взаимосвязаны (Boot 4 ↔ Modulith 2 ↔ springdoc 3 ↔ Java baseline) и
  по-отдельности дают красный CI.

acceptance_criteria:
  - given: "pom.xml с parent 4.0.6, java 25, обновлёнными properties"
    when: "CI lint.yml (Spotless на JDK 25)"
    then: "BUILD SUCCESS — код компилируется и отформатирован под новый стек"

  - given: "Spring Boot 4 + Modulith 2"
    when: "CI test-backend.yml: ModularityTest + TrueMarketApplicationTests"
    then: "verify() границ модулей зелёный; 11 модулей (incl. common) обнаружены"

  - given: "Flyway 12 + Testcontainers 2 на JDK 25"
    when: "CI Integration: AuthMigrationIT"
    then: "13/13 зелёных — миграции применяются на новом стеке"

  - given: "Dockerfile eclipse-temurin:25-*-alpine (поверх TASK-099 apk-хардинга)"
    when: "CI Build JAR + Trivy"
    then: "образ собирается; Trivy не хуже базлайна TASK-099"

technical_notes: |
  Изменения (ADR-010):
  - backend/pom.xml: parent 3.3.5→4.0.6; java/release 21→25;
    spring-modulith 1.2.5→2.0.6; flyway.maven 10.20.1→12.6.1;
    spotless 2.43.0→3.5.1 (+ googleJavaFormat 1.23.0→1.27.0);
    jacoco 0.8.12→0.8.14; owasp-dc 11.1.0→12.2.2; jjwt 0.12.6→0.13.0;
    logstash-logback 8.0→9.0; springdoc 2.6.0→3.0.3;
    testcontainers 1.20.3→2.0.5; postgresql(flyway-plugin dep) 42.7.4→42.7.11.
  - backend/Dockerfile: temurin 21→25 (apk-хардинг TASK-099 сохранён).
  - .tool-versions: java temurin-25 LTS.
  - .github/workflows: java-version 21→25 (lint, test-backend×3,
    security-scan×2, deploy-staging, release). build-android — Java 17
    (Android Gradle), НЕ трогаем.

  ВАЛИДАЦИЯ — ТОЛЬКО ЧЕРЕЗ CI. В dev-среде агента нет JDK 25, локальная
  компиляция release=25 невозможна (честно зафиксировано; ADR-010 = Proposed
  до зелёного CI). CI runners провиженят JDK 25 через setup-java@v4.

  ИЗВЕСТНЫЕ РИСКИ (валидируются сборкой, не замаскированы):
  - resilience4j: артефакт `resilience4j-spring-boot3` v2.2.0 таргетит
    Spring Boot 3. Под Boot 4 может потребоваться смена артефакта/версии
    (resilience4j-spring-boot4 / новый релиз). Если CI красный здесь —
    зафиксировать в этом TASK, решить точечно.
  - springdoc 3.0.3: проверить генерацию /v3/api-docs на Boot 4.
  - Spotless 3.x + google-java-format 1.27: возможен ре-формат; при красном
    Spotless — `./mvnw spotless:apply` отдельным коммитом в этой ветке.
  - Flyway 12: проверить отсутствие breaking changes в применении миграций
    (покрыто AuthMigrationIT).
  - jjwt 0.13.0: API-совместимость (в коде пока не используется — Phase 1
    TASK-103; риск низкий сейчас).

  Резолюция при неустранимой несовместимости критичного модуля: откат
  соответствующей версии + фиксация в ADR-010 (триггер пересмотра) — НЕ
  ослабление тестов/гейтов.

  РЕЗОЛЮЦИЯ РИСКОВ (валидировано локально JDK 26 → release 25,
  ./mvnw -P integration verify = BUILD SUCCESS):
  - Testcontainers 2.0: артефакты переименованы — добавлен префикс
    `testcontainers-` (junit-jupiter → testcontainers-junit-jupiter и т.д.).
    Исправлены координаты в <dependencies>; BOM-импорт оставлен явным
    (Spring Boot 4.0.6 хоть и пинит testcontainers.version=2.0.5, но импорт
    BOM явный — детерминированнее).
  - Spotless 3.5.1 sortPom нормализует пустые теги <x></x> → <x/>;
    применён spotless:apply (pom.xml). Java-файлы под gjf 1.27 не менялись.
  - resilience4j-spring-boot3 2.2.0: РИСК НЕ РЕАЛИЗОВАЛСЯ — резолвится и
    компилируется под Spring Boot 4, тесты контекста зелёные.
  - springdoc 3.0.3 / Flyway 12 / jjwt 0.13: ОК (AuthMigrationIT 13/13;
    springdoc-бин компилируется).
  - JVM-warning JDK 26 "Mutating final fields will be blocked" —
    предупреждение библиотек рефлексии, не ошибка; на release 25 / CI JDK 25
    не блокирует. Отслеживать при будущих апгрейдах.

api_changes: []

db_changes: []

test_requirements:
  unit:
    - "CI: ModularityTest.verify() зелёный на Spring Modulith 2 / Boot 4"
    - "CI: TrueMarketApplicationTests 11 модулей (incl. common) на новом стеке"
  integration:
    - "CI: AuthMigrationIT 13/13 на Flyway 12 + Testcontainers 2 + JDK 25"
    - "CI: Build JAR — Docker-образ на temurin:25 собирается"

definition_of_done:
  - "☑ pom.xml/Dockerfile/.tool-versions/workflows обновлены по ADR-010"
  - "☑ ADR-010 создан (Proposed → Accepted после зелёного CI)"
  - "☑ CI lint.yml зелёный (Spotless на JDK 25; spotless:apply при необходимости)"
  - "☑ CI test-backend.yml зелёный (Unit+Arch+Integration на новом стеке)"
  - "☑ CI Build JAR зелёный (temurin:25 образ)"
  - "☑ Известные риски (resilience4j/springdoc/flyway) — резолюция зафиксирована"
  - "☑ INDEX.md: TASK-098 добавлен; ADR-010 → Accepted при зелёном CI"
  - "☑ Тесты/гейты НЕ ослаблены ради зелёного"
