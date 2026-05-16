```yaml
id: TASK-004
title: "Скелет репозитория + Spring Boot основа"
phase: 0
module: meta
priority: critical
depends_on: [TASK-001]
estimated_hours: 6
status: completed
completed_at: 2026-05-07

context: |
  Без структуры репозитория и Maven-скелета бэкенда нельзя начать никакую разработку.
  Структура должна соответствовать CLAUDE.md раздел 6 (с поправками после выбора Java
  в ADR-001) и закладывать модульность под Spring Modulith из ADR-006.

acceptance_criteria:
  - given: "пустой git-репозиторий после клонирования"
    when: "выполняется make help"
    then: "выводится список доступных команд (включая backend-run, infra-up, db-migrate)"

  - given: "выполняется make backend-build после make bootstrap"
    when: "у разработчика установлен только asdf + Docker"
    then: "сборка JAR проходит без ошибок"

  - given: "выполняется make backend-test"
    when: "нет внешних зависимостей (БД, очередей)"
    then: "архитектурный тест ModularityTest проходит, проверяя границы 10 модулей"

  - given: "каждый из 10 модулей (auth, catalog, orders, payments, delivery,
            verification, reviews, analytics, notifications, admin)"
    when: "проверяется package-info.java"
    then: "содержит @ApplicationModule с явным allowedDependencies"

technical_notes: |
  Структура backend/ для Java:
    - pom.xml — единый, multi-module не используем (модулярность через Spring Modulith).
    - Maven Wrapper (mvnw, mvnw.cmd, .mvn/wrapper/maven-wrapper.properties).
    - src/main/java/ru/truemarket/{module}/ — один пакет на модуль.
    - src/main/resources/application.yaml — defaults без секретов.
    - src/main/resources/application-{local,test}.yaml — оверрайды.
    - src/main/resources/logback-spring.xml — JSON-логирование.
    - src/main/resources/db/migration/V*.sql — Flyway-миграции.

  Корневые файлы: README.md, .tool-versions, .editorconfig, .gitignore, Makefile, CLAUDE.md.

  Зависимости: spring-boot-starter-{web,validation,data-jpa,data-redis,security,amqp,
  websocket,actuator}, spring-modulith-starter-core, flyway-core + flyway-database-postgresql,
  jjwt (api+impl+jackson), argon2-jvm, logstash-logback-encoder, resilience4j-spring-boot3,
  springdoc-openapi, meilisearch-java, aws-sdk s3.

api_changes: []

db_changes:
  - type: migration
    description: "V202605071200__init_schemas_and_extensions.sql — расширения pgcrypto/citext/pg_trgm и 10 схем"

test_requirements:
  unit:
    - "ModularityTest.verifyModularStructure() — Spring Modulith verify() без ошибок"
    - "ModularityTest.writeDocumentationSnapshots() — генерация PlantUML"
    - "TrueMarketApplicationTests.modulesAreDiscovered() — все 10 модулей детектируются"

  integration: []

definition_of_done:
  - "☐ README.md содержит инструкции бутстрапа"
  - "☐ .tool-versions фиксирует версии Java/Maven/Node/Flutter"
  - "☐ .editorconfig + .gitignore созданы"
  - "☐ backend/pom.xml собран, все зависимости разрешаются"
  - "☐ Maven Wrapper работает"
  - "☐ TrueMarketApplication.java + 10 package-info.java"
  - "☐ application.yaml без секретов, есть профили local/test"
  - "☐ logback-spring.xml — JSON-логи на проде, текст локально"
  - "☐ SecurityConfig — stateless, всё закрыто кроме health/swagger"
  - "☐ Базовая Flyway-миграция создаёт схемы"
  - "☐ ModularityTest зелёный"
```

## Артефакты

- Корневые: [README.md](../../README.md), [.tool-versions](../../.tool-versions), [.editorconfig](../../.editorconfig), [.gitignore](../../.gitignore)
- Backend: [pom.xml](../../backend/pom.xml), [Dockerfile](../../backend/Dockerfile)
- Скелет: `backend/src/main/java/ru/truemarket/`
- Миграция: `backend/src/main/resources/db/migration/V202605071200__init_schemas_and_extensions.sql`

## Итог

Полный Maven-скелет с 10 пакетами-модулями под Spring Modulith. Архитектурные тесты на границы модулей. Готов к Phase 1.
