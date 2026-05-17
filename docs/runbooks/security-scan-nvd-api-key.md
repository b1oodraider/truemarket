# Runbook — OWASP Dependency Check: NVD_API_KEY и таймаут

> Связано: TASK-099, `.github/workflows/security-scan.yml`, ADR-нет (операционный runbook).

## Симптом

Job **«OWASP Dependency Check (Maven deps → CVE)»** в workflow `security-scan.yml`
падает ровно на `~30m` (`timeout-minutes: 30`), шаг
`Upload SARIF to GitHub Security` сообщает
`Path does not exist: backend/target/dependency-check-report.sarif`
(SARIF не сгенерирован, т.к. шаг сканирования не завершился).

## Причина

OWASP Dependency Check скачивает локальную копию базы NVD (National Vulnerability
Database). Без API-ключа NVD публичный rate-limit — **5 запросов / 30 секунд**;
полная синхронизация занимает 30–60+ минут и не укладывается в `timeout-minutes: 30`.
С ключом — **50 запросов / 30 секунд** (≈ ×10), синхронизация 3–7 минут.

Workflow уже:
- кэширует БД (`actions/cache@v4`, ключ по `hashFiles('backend/pom.xml')`);
- читает ключ из `secrets.NVD_API_KEY` (env в шаге `Run OWASP Dependency Check`).

Проблема возникает, когда **секрет `NVD_API_KEY` не задан** в репозитории
и кэш холодный (первый прогон или смена `pom.xml` → новый cache key).

## Устранение (одноразовое, выполняет владелец репозитория)

1. Получить бесплатный API-ключ:
   https://nvd.nist.gov/developers/request-an-api-key
   (на email приходит UUID-токен; активация мгновенная).

2. Добавить секрет в GitHub:
   - `Settings → Secrets and variables → Actions → New repository secret`
   - Name: `NVD_API_KEY`
   - Secret: `<полученный UUID>`

   Либо через gh CLI:
   ```bash
   gh secret set NVD_API_KEY --repo b1oodraider/truemarket
   # вставить значение по запросу (не передавать в командной строке — попадёт в history)
   ```

3. Перезапустить упавший workflow:
   ```bash
   gh run rerun <run-id> --repo b1oodraider/truemarket --failed
   ```

После установки ключа первый прогон строит кэш (3–7 мин), последующие —
секунды (cache hit по `pom.xml`).

## Деградация / временный обход

`security-scan.yml` **не блокирует PR by design** (см. шапку workflow). Если ключ
ещё не выдан, а PR нужно мержить:

- Required-проверки (`lint.yml`, `test-backend.yml`) от этого не зависят — мерж не блокируется.
- OWASP-результат смотреть в артефакте `owasp-dc-report` (HTML, retention 30 дней)
  с последнего успешного scheduled-прогона (еженедельно, Пн 03:00 UTC).
- НЕ ослаблять `failBuildOnCVSS=7` (профиль `security` в `pom.xml`) ради «зелёного» —
  это маскирует реальные CVE. Лучше временно принять, что job красный, чем снизить порог.

## Проверка после фикса

```bash
gh run list --repo b1oodraider/truemarket --workflow security-scan.yml --limit 1
# job OWASP должен завершаться < 10 мин и быть success (или fail ТОЛЬКО при реальных CVE CVSS≥7)
```

## Связанный долг

- Trivy (Docker image) хардинг — TASK-099 (`apk upgrade` в `backend/Dockerfile`).
- Phase 5: пин base-image по digest, оценка перехода на distroless/native AOT.
