```yaml
id: TASK-006
title: "CI/CD: lint + test + build пайплайны"
phase: 0
module: meta
priority: high
depends_on: [TASK-004, TASK-005]
estimated_hours: 4
status: completed
completed_at: 2026-05-07

context: |
  CLAUDE.md раздел 10 фиксирует семь воркфлоу: lint, test-backend, test-mobile,
  build-android, build-ios, deploy-staging, release.

  На Phase 0 нужны рабочие lint и test-backend (это блокер для PR). Остальные
  создаются как заготовки с auto-skip пока соответствующие артефакты не появятся
  (мобильные приложения, k8s-манифесты).

acceptance_criteria:
  - given: "открыт PR с изменениями в backend/"
    when: "запускается CI"
    then: "выполняются: lint (Spotless, EditorConfig, YAML, Shell, Markdown) и test-backend
           (unit + architecture + integration через Testcontainers)"

  - given: "тесты прошли"
    when: "формируется JAR"
    then: "артефакт truemarket-backend.jar доступен 7 дней"

  - given: "интеграционные тесты упали"
    when: "формируется отчёт"
    then: "Surefire/Failsafe reports + JaCoCo report доступны как артефакты"

  - given: "PR без изменений в mobile/"
    when: "запускается CI"
    then: "test-mobile.yml не запускается (paths-filter)"

  - given: "появится mobile/buyer/pubspec.yaml"
    when: "следующий PR"
    then: "test-mobile.yml автоматически активируется"

technical_notes: |
  Файлы:
    - .github/workflows/lint.yml           — линтер всех языков
    - .github/workflows/test-backend.yml   — unit + integration + build, 3 параллельных job
    - .github/workflows/test-mobile.yml    — с детектом существования pubspec.yaml
    - .github/workflows/build-android.yml  — APK + AAB по тегу v*
    - .github/workflows/build-ios.yml      — self-hosted runner macos arm64 + fastlane
    - .github/workflows/deploy-staging.yml — заготовка, активируется при infra/k8s/staging/
    - .github/workflows/release.yml        — заготовка для тега v*, GitHub Environment 'production'

  Runners:
    - lint, test, build: ubuntu-latest
    - build-ios: [self-hosted, macos, arm64, ios] (CLAUDE.md 9.2 / 10.2 — MacBook M2)

  Кэш Maven через actions/setup-java@v4 с cache: maven.
  PR template: .github/pull_request_template.md по форме CLAUDE.md 8.7.

api_changes: []
db_changes: []

test_requirements:
  unit: []
  integration:
    - "Push в feature-ветку запускает lint и test-backend"
    - "Тэг v1.0.0 запускает build-android (skipped если нет pubspec) и release"

definition_of_done:
  - "☐ 7 workflow-файлов созданы"
  - "☐ lint.yml — все языки и форматы покрыты"
  - "☐ test-backend.yml — три job (unit+arch / integration / build)"
  - "☐ PR template создан"
  - "☐ Concurrency настроена (cancel-in-progress=true для PR)"
  - "☐ Заглушки build-ios/deploy-staging/release корректно скипаются без артефактов"
```

## Артефакты

- [.github/workflows/lint.yml](../../.github/workflows/lint.yml)
- [.github/workflows/test-backend.yml](../../.github/workflows/test-backend.yml)
- [.github/workflows/test-mobile.yml](../../.github/workflows/test-mobile.yml)
- [.github/workflows/build-android.yml](../../.github/workflows/build-android.yml)
- [.github/workflows/build-ios.yml](../../.github/workflows/build-ios.yml)
- [.github/workflows/deploy-staging.yml](../../.github/workflows/deploy-staging.yml)
- [.github/workflows/release.yml](../../.github/workflows/release.yml)
- [.github/pull_request_template.md](../../.github/pull_request_template.md)

## Известный риск

**R-01:** Self-hosted runner для iOS (MacBook M2) — single point of failure. Runbook восстановления — в `docs/runbooks/ios-runner-recovery.md` (создаётся в Phase 4).

## Итог

Полный CI/CD до уровня MVP. На Phase 0 активны lint и test-backend; остальные пайплайны самоактивируются с появлением соответствующих артефактов (мобильные приложения, k8s).
