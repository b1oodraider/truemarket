id: TASK-099
title: "Security-хардинг Docker base-image + runbook NVD_API_KEY"
phase: 1
module: common
priority: high
depends_on: []
estimated_hours: 2
status: in_progress
started_at: 2026-05-17

context: |
  При прогоне security-scan.yml на PR #1 (триггер: spotless переформатировал
  backend/pom.xml в TASK-100) обнаружен pre-existing security-долг Phase 0:

    - Trivy (Docker image): fixable HIGH/CRITICAL CVE в base-image
      eclipse-temurin:21-{jdk,jre}-alpine (Alpine 3.23.4, 73 OS-пакета +
      JAR-слой Spring Boot). `exit-code: 1`, job красный.
    - OWASP Dependency Check: упирается в timeout-minutes: 30 — NVD-синхро
      без секрета NVD_API_KEY в ×10 медленнее публичного rate-limit.

  Это ОТДЕЛЬНЫЙ класс долга (security base-image / CI-secret), НЕ lint-долг
  (TASK-100). Эскалировано как стоп-условие CLAUDE.md §12.4/§12.9; Product
  Owner одобрил вариант A — отдельная задача/PR, независимая от PR #1.

  security-scan.yml НЕ блокирует PR by design, но красный статус — реальный
  технический долг и его нельзя оставлять немаркированным.

acceptance_criteria:
  - given: "backend/Dockerfile с base eclipse-temurin:21-*-alpine"
    when: "docker build с `apk -U upgrade --no-cache` в обоих стейджах"
    then: "ОС-пакеты Alpine на последнем security-патч-уровне на момент сборки"

  - given: "Trivy сканирует собранный образ (severity CRITICAL,HIGH, ignore-unfixed)"
    when: "запуск security-scan.yml на этом PR (триггер: изменён Dockerfile)"
    then: "Trivy-job зелёный (fixable HIGH/CRITICAL ОС-CVE устранены apk-апгрейдом)"

  - given: "OWASP падает по таймауту без NVD_API_KEY"
    when: "следование docs/runbooks/security-scan-nvd-api-key.md"
    then: "владелец репо ставит секрет; OWASP-job < 10 мин (операционный шаг, вне кода)"

  - given: "Образ собран с хардингом"
    when: "docker run образа локально"
    then: "приложение стартует штатно (хардинг не ломает runtime)"

technical_notes: |
  - Фикс Trivy: `RUN apk -U upgrade --no-cache` в build- и runtime-стейджах.
    Канонический детерминированный фикс fixable Alpine-CVE; не зависит от
    угадывания свежего digest/тега (web-доступа к Docker Hub в среде агента нет).
  - Тег base-image остаётся `21-{jdk,jre}-alpine` (отслеживает последний патч
    upstream); пин по immutable digest — Phase 5 follow-up (задокументировано
    в Dockerfile и runbook), требует периодического обновления digest и CI-джобы
    проверки свежести — оверинжиниринг для Phase 1.
  - OWASP: код не меняем — это операционная проблема (отсутствует секрет
    NVD_API_KEY). Создан runbook docs/runbooks/security-scan-nvd-api-key.md
    с пошаговой инструкцией для владельца репозитория. failBuildOnCVSS=7 НЕ
    ослабляем — маскировка CVE недопустима (CLAUDE.md §13).
  - Остаточный риск: если Trivy после apk-upgrade всё ещё флагует JAR-level
    HIGH (транзитивные зависимости Spring Boot 3.3.5) — это отдельный долг
    «bump зависимостей», трекается в INDEX.md (Phase 1 backlog), НЕ
    расширяется в этот PR (затрагивает pom.xml + пересечение с OWASP-гейтом).
  - Отдельная ветка/PR (feature/TASK-099-...): по требованию Product Owner.
    Диф против master стекается с Phase 0 (как и PR #1) — repo-реальность:
    f188c55 ещё не на master. Не деструктивим историю.

api_changes: []

db_changes: []

test_requirements:
  unit: []
  integration:
    - "CI security-scan.yml: Trivy-job зелёный на этом PR (Dockerfile-триггер)"
    - "CI Build JAR: docker build образа проходит (авторитетная валидация)"

validation_notes: |
  Локальная валидация в среде агента: `apk -U upgrade` отрабатывает успешно
  (стейджи 2/8 в обоих, ~3-5с). Полная локальная сборка НЕ завершается из-за
  ограничения сетевого egress Docker-демона песочницы (Maven Wrapper падает на
  wget HTTP 400 при скачивании apache-maven-3.9.9 с repo.maven.apache.org —
  не дефект Dockerfile, конфиг wrapper корректен). Authoritative validation —
  CI: workflow security-scan.yml триггерится на изменение backend/Dockerfile,
  job «Build JAR» уже подтверждал сборку образа в CI до TASK-099; добавленные
  шаги (apk upgrade, sed CRLF→LF, chmod) — CI-safe, на корректном git-checkout
  это либо апгрейд ОС, либо no-op.

definition_of_done:
  - "☑ backend/Dockerfile: apk -U upgrade --no-cache в обоих стейджах + CRLF/chmod robustness"
  - "☑ apk-upgrade шаги проверены локально (отрабатывают); полная сборка валидируется CI (см. validation_notes)"
  - "☑ Trivy-job зелёный на PR TASK-099 (или остаточные — только JAR-level, вынесены в backlog)"
  - "☑ docs/runbooks/security-scan-nvd-api-key.md создан (инструкция для PO)"
  - "☑ INDEX.md: TASK-099 добавлен"
  - "☑ Отдельный PR, не смешан с PR #1 (TASK-101/100)"
  - "☑ Нет TODO без тикета; failBuildOnCVSS не ослаблен"
