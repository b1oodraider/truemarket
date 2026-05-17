id: TASK-100
title: "Устранить lint/test-долг Phase 0, блокирующий CI Phase 1"
phase: 1
module: common
priority: critical
depends_on: []
estimated_hours: 1
status: in_progress
started_at: 2026-05-17

context: |
  При прогоне make ci-local в рамках TASK-101 обнаружено, что код Phase 0
  (коммит f188c55) не проходит собственные CI-gate'ы — независимо от TASK-101:

    1. spotless:check падает на 6 файлах Phase 0 (Google Java Format не был
       применён при коммите Phase 0): pom.xml, TrueMarketApplication.java,
       common/config/OpenApiConfig.java, auth/package-info.java,
       architecture/ModularityTest.java, TrueMarketApplicationTests.java.

    2. TrueMarketApplicationTests.modulesAreDiscovered падает: Spring Modulith
       обнаруживает 11 модулей (включая common), а хардкод-список в тесте
       ожидает ровно 10 без common.

  Без устранения этого долга lint.yml и test-backend.yml будут красными на
  ЛЮБОМ Phase 1 PR. Долг вынесен в отдельную задачу (CLAUDE.md §16: один PR =
  одна задача; §12.4 стоп-условие — эскалировано Product Owner, вариант A одобрен).

  Анализ причины #2: common/package-info.java документирует пакет как
  «Не модуль в смысле Spring Modulith», но не имеет @ApplicationModule и лежит
  прямо под базовым пакетом ru.truemarket — Spring Modulith авто-обнаруживает
  его как модуль. При этом ModularityTest.verify() (настоящий архитектурный
  gate) ПРОХОДИТ с common, и ADR-006 §«Карта модулей» ЯВНО перечисляет common
  отдельной строкой. Следовательно, баг — в smoke-тесте, который противоречит
  ADR-006. Корректное решение — привести ожидаемый список к ADR-006 (+ common),
  НЕ менять архитектуру (ModularityTest и Modulith уже согласованы).

acceptance_criteria:
  - given: "Чистый checkout ветки с применённым TASK-100"
    when: "./mvnw spotless:check"
    then: "BUILD SUCCESS — нарушений форматирования нет ни в одном файле"

  - given: "Spring Modulith обнаруживает 11 модулей включая common"
    when: "Запуск TrueMarketApplicationTests.modulesAreDiscovered"
    then: "Тест зелёный — ожидаемый список включает common (согласован с ADR-006)"

  - given: "Полный CI-прогон бэкенда"
    when: "./mvnw -P integration verify"
    then: "BUILD SUCCESS: spotless + surefire (3/3) + failsafe AuthMigrationIT (13/13) + jacoco"

  - given: "TASK-100 — только форматирование + 1 ассерт"
    when: "Ревью диффа"
    then: "Нет изменений поведения: spotless:apply (формат) + список модулей в smoke-тесте"

technical_notes: |
  - spotless:apply применяется ко всему backend-модулю (Google Java Format,
    GOOGLE style, reflowLongStrings, sortPom). Изменения — чисто косметические:
    переносы строк в javadoc, схлопывание fluent-цепочек, 2-space indent в pom.xml.
    Поведение кода и схема БД НЕ меняются.
  - TrueMarketApplicationTests: в containsExactlyInAnyOrder добавляется "common".
    Обоснование: ADR-006 «Карта модулей» перечисляет common как модуль
    («common | Утилиты, общие типы | — (никаких бизнес-зависимостей)»);
    ModularityTest.verify() уже проходит с common — архитектура согласована,
    рассинхронизирован только хардкод smoke-теста.
  - Документационная неточность: javadoc в common/package-info.java гласит
    «Не модуль в смысле Spring Modulith» — это не соответствует ADR-006 и
    поведению Modulith. НЕ исправляется в TASK-100 (вне scope «lint-долг»;
    не блокирует CI). Вынести отдельным doc-фиксом при ревизии ADR-006 при
    необходимости — не критично.
  - Версии Spring Boot 3.3.5 / Modulith 1.2.5 НЕ меняются (нет ADR).

api_changes: []

db_changes: []

test_requirements:
  unit:
    - "TrueMarketApplicationTests.modulesAreDiscovered: зелёный с 11 модулями (incl. common)"
    - "ModularityTest.verifyModularStructure: остаётся зелёным (не затронут)"
  integration:
    - "AuthMigrationIT: остаётся 13/13 зелёным (регрессии нет)"

definition_of_done:
  - "☑ ./mvnw spotless:check — BUILD SUCCESS"
  - "☑ ./mvnw -P integration verify — BUILD SUCCESS (surefire 3/3, failsafe 13/13, jacoco OK)"
  - "☑ Дифф — только формат + 1 ассерт-список; нет изменений поведения/схемы"
  - "☑ INDEX.md: TASK-100 → 🟢 DONE"
  - "☑ Коммит изолирован (chore/style), отделён от TASK-101"
  - "☑ Нет TODO без тикета"
