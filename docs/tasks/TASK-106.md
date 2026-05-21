id: TASK-106
title: "Auth: JWT-фильтр валидации access + RBAC + модульная security + /change-password"
phase: 1
module: auth
priority: critical
depends_on: [TASK-103, TASK-104, TASK-105]
estimated_hours: 10
status: completed
started_at: 2026-05-21
completed_at: 2026-05-21

context: |
  TASK-102..105 выпускали/ротировали токены и проверяли пароли, но access-токен
  нигде не валидировался на входящих запросах — защищённых эндпоинтов не было
  (SecurityConfig: permitAll auth-пути + denyAll остальное). TASK-106 вводит
  аутентификацию по Bearer (access JWT) + RBAC (роли buyer/seller/moderator/admin,
  CLAUDE.md §13.1) и реализует перенесённую из TASK-105 смену пароля
  (нужна аутентификация пользователя, которой до 106 не было).

  Также закрывает backlog TASK-095 (модульная подача security-правил): common
  больше НЕ хардкодит пути auth — каждый модуль вносит свою SecurityFilterChain.

design: |
  Модульная security через per-module SecurityFilterChain (идиоматичный Spring,
  без кастомных common-интерфейсов и без auth→common импорта — границы Modulith
  соблюдены, allowedDependencies auth остаётся пустым):
   - auth.config.AuthSecurityConfig: @Order(1) chain, securityMatcher /api/v1/auth/**;
     JWT-фильтр + правила (register/login/refresh/logout — permitAll; change-password —
     authenticated) + RFC7807 entryPoint/accessDenied. Security «едет» с auth при split.
   - common.config.SecurityConfig: @Order(LOWEST) catch-all; infra permitAll
     (actuator/health, swagger), всё прочее denyAll. Auth-пути отсюда УДАЛЕНЫ.

  JWT-фильтр (auth.security.JwtAuthenticationFilter, OncePerRequestFilter):
  Authorization: Bearer <token> → TokenService.parseAccess (подпись/exp/typ=access,
  claim role) → AuthenticatedUser(id, role) как principal + authority ROLE_<ROLE>
  в SecurityContext. Невалидный токен → 401 (RFC7807). Отсутствует → аноним
  (authorization решает 401/403). Stateless, без сессий.

  /change-password (POST, authenticated): body {current_password, new_password}.
  userId — из SecurityContext (principal). PasswordChangeService:
  verify current (encoder.matches) иначе IncorrectPasswordException(400);
  new_password — длина ≥12 (DTO) + breach-check (PwnedPasswordChecker из TASK-105,
  PasswordBreachedException 400); encode+save; revoke ВСЕХ refresh пользователя
  (logout everywhere, переиспользует RefreshTokenRevoker.revokeAllActive). 204.

acceptance_criteria:
  - given: "запрос к /auth/change-password без Authorization"
    when: "POST"
    then: "401 (RFC7807 problem+json)"
  - given: "валидный access-токен"
    when: "POST /auth/change-password с верным current_password и валидным new"
    then: "204; пароль обновлён (argon2id); все refresh пользователя отозваны"
  - given: "валидный токен, но неверный current_password"
    when: "POST /auth/change-password"
    then: "400 (current password incorrect)"
  - given: "валидный токен, new_password из утечки HIBP"
    when: "POST /auth/change-password"
    then: "400 (breached); пароль НЕ изменён"
  - given: "истёкший/битый/refresh-токен в Authorization"
    when: "запрос к защищённому эндпоинту"
    then: "401 (parseAccess отвергает не-access/невалидные)"
  - given: "публичные auth-эндпоинты и infra-пути (actuator/health, swagger)"
    when: "запрос без токена"
    then: "доступны (permitAll сохранён в модульной конфигурации)"

technical_notes: |
  - TokenService: + parseAccess(token) → AuthenticatedUser(id, role); валидирует
    typ=access (симметрично parseRefresh для typ=refresh), читает claim role.
  - auth.security: JwtAuthenticationFilter, AuthenticatedUser(record principal),
    RestAuthenticationEntryPoint + RestAccessDeniedHandler (RFC7807, общий
    ProblemWriter — без дублирования; всё auth-local).
  - RBAC: authority = "ROLE_" + role.name().toUpperCase(). Правила ролей в
    AuthSecurityConfig (.hasRole(...)) — пример задела; пока эндпоинтов под роли нет,
    кроме authenticated change-password.
  - User: + changePassword(newHash) (updated_at — @UpdateTimestamp).
  - PasswordChangeService (@Transactional, auth.service): deps UserRepository,
    PasswordEncoder, PwnedPasswordChecker, RefreshTokenRevoker (все в auth).
  - IncorrectPasswordException → AuthExceptionHandler → 400. PasswordBreachedException
    (TASK-105) переиспользуется для new_password.
  - AuthController: /change-password; userId из @AuthenticationPrincipal AuthenticatedUser.
  - common.SecurityConfig: убрать auth-пути; @Order(LOWEST); catch-all denyAll + infra.
  - Стек Java 25/Boot 4/Spring Security 7; stateless; ddl-auto=none.

api_changes:
  - { method: POST, path: /api/v1/auth/change-password, description: "смена пароля (authenticated, breach-check, revoke-all)" }

db_changes: []  # схема не меняется (password_hash уже есть, TASK-101)

test_requirements:
  unit:
    - "TokenService.parseAccess: валидный access → (id, role); refresh-токен → InvalidToken; битый → InvalidToken"
    - "JwtAuthenticationFilter: валидный Bearer → SecurityContext с ROLE_; невалидный → 401; без заголовка → продолжает аноним"
    - "PasswordChangeService: успех — encode+save+revokeAllActive; неверный current → IncorrectPassword (без save/revoke); breached new → PasswordBreached"
  integration:
    - "/auth/change-password без токена → 401 (RFC7807)"
    - "login → /change-password с верным current → 204; затем старый refresh → 401 (отозван)"
    - "/change-password неверный current → 400"
    - "/change-password new из HIBP-мока → 400 (в test-профиле pwned выключен → отдельный unit покрывает breach)"
    - "infra: /actuator/health доступен; неизвестный путь → denyAll"

definition_of_done:
  - "☑ Компиляция release 25, Spotless зелёный"
  - "☑ Unit+IT зелёные (./mvnw -P integration verify), JaCoCo ≥80%"
  - "☑ ModularityTest зелёный (auth не импортирует common; allowedDependencies пуст)"
  - "☑ Контракт openapi: /auth/change-password задокументирован (bearerAuth)"
  - "☑ Пароли/токены не в логах; access проверяется stateless"
  - "☑ INDEX.md TASK-106 → DONE; TASK-095 → закрыт (модульная security)"
  - "☑ Нет TODO/FIXME без тикета"
