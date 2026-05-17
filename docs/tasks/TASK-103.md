id: TASK-103
title: "Auth: /login + /refresh (stateless JWT)"
phase: 1
module: auth
priority: critical
depends_on: [TASK-102]
estimated_hours: 6
status: completed
started_at: 2026-05-18
completed_at: 2026-05-18

context: |
  Пере-скоуп одобрен PO (2026-05-18, см. TASK-102.md). TASK-102 заложил
  TokenService (jjwt 0.13 HS512), PasswordEncoder (argon2id), User/UserRepository.
  TASK-103 добавляет вход и обновление токена, переиспользуя инфраструктуру 102.

  Контракт openapi.yaml (не меняется): POST /auth/login {email,password} → 200
  TokenPair / 401 / 429; POST /auth/refresh {refresh_token} → 200 TokenPair / 401.

  ГРАНИЦА scope:
   - TASK-103: stateless /login + /refresh. Refresh-токен валидируется по
     подписи+exp+claim typ=refresh; при /refresh выпускается НОВАЯ пара.
   - TASK-104: персистентное хранилище refresh (auth.refresh_tokens, TASK-101),
     ротация с rotated_from chain, replay-detection, /logout (revoke).
     Stateless refresh из 103 — временно, заменяется персистентным в 104.
   - /logout (openapi 204): в 103 НЕ реализуется (нужен persistent store →
     TASK-104). Отсутствие endpoint — не breaking (клиентов нет).
   - rate-limit/429 на /login — TASK-107 (в контракте остаётся).

acceptance_criteria:
  - given: "зарегистрирован пользователь (TASK-102), верный email+password"
    when: "POST /api/v1/auth/login"
    then: "200 + валидный TokenPair (access TTL 15м, refresh); last_login_at обновлён"

  - given: "неверный пароль ИЛИ несуществующий email ИЛИ soft-deleted user"
    when: "POST /api/v1/auth/login"
    then: "401 Unauthorized, application/problem+json; без раскрытия какой именно фактор неверен (anti-enumeration)"

  - given: "валидный refresh-токен (подпись ок, не истёк, typ=refresh)"
    when: "POST /api/v1/auth/refresh"
    then: "200 + НОВАЯ пара access/refresh (role-claim актуализирован из БД)"

  - given: "refresh-токен с битой подписью / истёкший / typ=access"
    when: "POST /api/v1/auth/refresh"
    then: "401 Unauthorized, problem+json"

  - given: "/login и /refresh"
    when: "запрос без аутентификации"
    then: "доступны (openapi security: []) — SecurityConfig permitAll"

technical_notes: |
  - DTO LoginRequest {email,password} (api/dto) по openapi; RefreshRequest
    {refresh_token} (@JsonProperty).
  - TokenService: + parseRefresh(token) → userId (verify signWith key, exp,
    typ=refresh; иначе InvalidTokenException). Переиспользует issueFor(User).
  - UserRepository: + findByEmailAndDeletedAtIsNull (login только активные).
  - AuthenticationService: login(email,password,ip,ua?) — найти активного
    пользователя, passwordEncoder.matches; обновить last_login_at;
    tokenService.issueFor. refresh(token) — parseRefresh → load User →
    issueFor. Невалидно → InvalidCredentialsException/InvalidTokenException.
  - Anti-enumeration: одинаковый 401 «invalid credentials» для unknown email
    и wrong password (не раскрывать существование аккаунта). Время ответа —
    допустимо разное (timing-атаки вне scope Phase 1, отметить для TASK-105).
  - AuthExceptionHandler: + 401 (InvalidCredentials/InvalidToken) RFC7807.
  - SecurityConfig: permitAll + POST /api/v1/auth/login, /api/v1/auth/refresh.
  - last_login_at: добавить setter/доменный метод в User (touchLastLogin()).
    @Transactional в сервисе для flush.
  - Границы модулей: всё в auth.*; ModularityTest зелёный.
  - Стек: Java 25 / Spring Boot 4 / Modulith 2 (ADR-010). ddl-auto=none.

api_changes:
  - method: POST
    path: /api/v1/auth/login
    description: "Реализация (контракт уже в openapi.yaml)"
  - method: POST
    path: /api/v1/auth/refresh
    description: "Реализация (контракт уже в openapi.yaml)"

db_changes: []  # таблицы из TASK-101; auth.refresh_tokens задействуется в TASK-104

test_requirements:
  unit:
    - "AuthenticationService.login: успех — токены выпущены, last_login_at обновлён"
    - "login: неверный пароль → InvalidCredentials (тот же тип, что unknown email)"
    - "login: несуществующий email → InvalidCredentials"
    - "login: soft-deleted user → InvalidCredentials"
    - "refresh: валидный refresh → новая пара"
    - "refresh: истёкший/битый/typ=access → InvalidToken"
    - "TokenService.parseRefresh: корректно извлекает userId; отвергает access-токен"
  integration:
    - "POST /login верные creds → 200 TokenPair (Testcontainers PG; user из /register)"
    - "POST /login неверный пароль → 401 problem+json"
    - "POST /login несуществующий email → 401 problem+json (тот же ответ)"
    - "POST /refresh валидным refresh из /login → 200 новая пара"
    - "POST /refresh access-токеном/мусором → 401"
    - "security: /login и /refresh доступны без авторизации"

definition_of_done:
  - "☑ Код компилируется (release 25), Spotless 3.5.1 зелёный"
  - "☑ Unit + IT зелёные локально (./mvnw -P integration verify, JDK26→r25)"
  - "☑ JaCoCo check проходит; auth-логика покрыта"
  - "☑ ModularityTest зелёный"
  - "☑ Контракт openapi соблюдён; /logout явно отложен в TASK-104 (в TASK-103.md)"
  - "☑ Anti-enumeration: одинаковый 401; пароль/токены не в логах"
  - "☑ INDEX.md TASK-103 → DONE после merge"
  - "☑ Нет TODO/FIXME без тикета"
