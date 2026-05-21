id: TASK-105
title: "Auth: breach-check паролей (haveibeenpwned) при регистрации + минимальная политика"
phase: 1
module: auth
priority: high
depends_on: [TASK-102]
estimated_hours: 5
status: completed
started_at: 2026-05-21
completed_at: 2026-05-21

context: |
  CLAUDE.md §13.1: "Проверка на утечки через haveibeenpwned API при регистрации
  и смене пароля". TASK-102 ввёл регистрацию (argon2id + длина ≥12 на DTO), но
  breach-check отсутствует. TASK-105 добавляет проверку нового пароля по базе
  утечек HIBP (Pwned Passwords) при регистрации.

  Ре-скоуп (одобрено 2026-05-21):
   - Смена пароля (/auth/change-password) ОТЛОЖЕНА в TASK-106: требует аутентификацию
     пользователя по Bearer, а JWT-фильтр появляется только в TASK-106. Перенос
     избегает временного email+password-эндпоинта, который пришлось бы переделывать.
   - Политика паролей — МИНИМАЛЬНАЯ по §13.1 (NIST 800-63B-подход): длина ≥12
     (уже на DTO) + проверка на утечки. Без навязанной символьной сложности.

design: |
  Pwned Passwords k-anonymity (без API-ключа): SHA-1(password) hex upper →
  prefix=первые 5 hex, suffix=остаток. GET https://api.pwnedpasswords.com/range/{prefix}
  (заголовок Add-Padding: true против анализа размера ответа). Тело — строки
  "SUFFIX:count". Пароль скомпрометирован, если наш suffix присутствует с count>0
  (count=0 — padding-записи, игнорируются).

  Ports-and-adapters (изоляция внешней интеграции за интерфейсом, CLAUDE.md §5.2):
   - port  auth.service.PwnedPasswordChecker { boolean isCompromised(String) }
   - adapter auth.external.HibpPwnedPasswordChecker (RestClient к HIBP).
  RegistrationService зависит ТОЛЬКО от порта (DIP) — для будущего split auth
  в микросервис внешняя интеграция уже изолирована.

  Fail-open (одобрено): при недоступности/таймауте HIBP isCompromised возвращает
  false + WARN-лог. Доступность регистрации важнее блокировки на сбое 3rd-party;
  пароль всё равно argon2id-хешируется и длина ≥12 гарантирована.

  Сам пароль наружу не уходит — только первые 5 hex-символов SHA-1 (k-anonymity);
  пароль/полный хеш в логах отсутствуют (§13.5).

acceptance_criteria:
  - given: "пароль присутствует в базе HIBP (suffix с count>0)"
    when: "POST /auth/register"
    then: "400 (password breached); пользователь НЕ создан, токены не выданы"
  - given: "пароль отсутствует в базе HIBP"
    when: "POST /auth/register"
    then: "201; пользователь создан, токены выданы (как в TASK-102)"
  - given: "HIBP недоступен (таймаут/5xx/сетевая ошибка)"
    when: "POST /auth/register"
    then: "201 (fail-open); инцидент в WARN-логе; пароль не утёк в лог"
  - given: "pwned-check-enabled=false (тест-профиль/feature-flag)"
    when: "регистрация"
    then: "HIBP не вызывается; регистрация по прежней логике"

technical_notes: |
  - PwnedPasswordChecker (port, auth.service): isCompromised(String rawPassword).
  - HibpPwnedPasswordChecker (adapter, auth.external): RestClient (инжектируется
    готовый bean pwnedPasswordsClient с baseUrl+timeout). SHA-1 hex локально
    (не в TokenService — другая ответственность/алгоритм). Disabled-флаг и любая
    RuntimeException → false (fail-open, WARN). Парсинг тела — по строкам.
  - AuthProperties.Password: + boolean pwnedCheckEnabled, + Hibp(baseUrl, timeout).
  - AuthConfig: @Bean pwnedPasswordsClient (RestClient.Builder + baseUrl + таймауты
    через SimpleClientHttpRequestFactory(Duration)).
  - RegistrationService: перед encode — if checker.isCompromised(password) →
    PasswordBreachedException. Порядок: после конфликт-проверок email/phone (чтобы
    не звать HIBP до отсева дублей) — но ДО создания User.
  - PasswordBreachedException → AuthExceptionHandler → 400 (отдельный slug
    "breached-password", сообщение прямое: не анти-энумерация — речь о пароле,
    не о существовании аккаунта; клиент должен подсказать выбрать другой пароль).
  - application.yaml: hibp.base-url, hibp.timeout (PWNED_CHECK_ENABLED уже есть).
  - application-test.yaml: pwned-check-enabled=false уже задан — IT не ходят в сеть.
  - Стек Java 25/Boot 4; RestClient из spring-web; MockRestServiceServer (spring-test).

api_changes:
  - { method: POST, path: /api/v1/auth/register, description: "пароль проверяется по HIBP; утечка → 400 (контракт 400 уже есть)" }

db_changes: []  # схема не меняется

test_requirements:
  unit:
    - "HibpPwnedPasswordChecker: suffix c count>0 → true (compromised)"
    - "HibpPwnedPasswordChecker: suffix отсутствует → false"
    - "HibpPwnedPasswordChecker: count=0 (padding) → false"
    - "HibpPwnedPasswordChecker: 5xx/таймаут → false (fail-open)"
    - "HibpPwnedPasswordChecker: disabled-флаг → false, HTTP не вызывается"
    - "RegistrationService: compromised → PasswordBreachedException, user/consent не сохранены"
  integration:
    - "register с pwned-check-enabled=false (test-профиль) → 201 как прежде (регресс TASK-102/104 зелёные)"

definition_of_done:
  - "☑ Компиляция release 25, Spotless зелёный"
  - "☑ Unit+IT зелёные (./mvnw -P integration verify), JaCoCo ≥80%"
  - "☑ ModularityTest зелёный (external — приватный подпакет auth)"
  - "☑ Пароль/полный SHA-1 не в логах; наружу только 5-символьный префикс (k-anonymity)"
  - "☑ openapi: register-описание упоминает breach-check (400 уже в контракте)"
  - "☑ INDEX.md TASK-105 → DONE после merge; ре-скоуп (change-password→106) зафиксирован"
  - "☑ Нет TODO/FIXME без тикета"
