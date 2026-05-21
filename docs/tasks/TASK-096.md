id: TASK-096
title: "Извлечь shared security (JWT-аутентификация) в common-модуль"
phase: 1
module: common
priority: high
depends_on: [TASK-106]
estimated_hours: 5
status: completed
started_at: 2026-05-21
completed_at: 2026-05-21

context: |
  Backlog TASK-096 материализовался: catalog (TASK-109) — 2-й потребитель
  JWT-аутентификации (Rule of Three). В TASK-106 JWT-фильтр живёт приватно в
  auth.security и подключён только к auth-цепочке (securityMatcher /api/v1/auth/**),
  поэтому запросы к /api/v1/categories/** не аутентифицируются — RBAC в catalog
  невозможен. JWT-аутентификация — сквозная инфраструктура (нужна всем модулям),
  в отличие от per-module authorization-правил (TASK-095).

  Решение (одобрено 2026-05-21): вынести JWT-аутентификацию в shared-модуль common
  ДО TASK-109, чтобы 109 встал чисто сверху. Чистый рефакторинг без смены поведения.

design: |
  common — shared Modulith-модуль (@Modulithic(sharedModules="common")): доступен
  всем модулям без перечисления в allowedDependencies. DIP, чтобы common НЕ зависел
  от auth:
   - common.security.AccessTokenAuthenticator (порт): Optional<AuthenticatedUser>
     authenticate(token). Реализация — в auth (адаптер над TokenService).
   - common.security.AuthenticatedUser(UUID id, String role) + authority()
     (role как String — common не знает auth.domain.UserRole).
   - common.security.JwtAuthenticationFilter (использует порт; success → principal
     в SecurityContext; invalid/нет токена → аноним).
   - common.security.{ProblemWriter, RestAuthenticationEntryPoint,
     RestAccessDeniedHandler} (RFC7807; перенесены из auth).
   - auth.security.JwtAccessTokenAuthenticator implements AccessTokenAuthenticator
     (TokenService.parseAccess → AuthenticatedUser; InvalidToken → Optional.empty()).
   - TokenService.parseAccess теперь возвращает common AuthenticatedUser (role.name()).
   - auth.security.AuthSecurityConfig использует public-бины фильтра/обработчиков
     из common (свою цепочку и правила auth сохраняет).

  Поведение не меняется (фильтр по-прежнему добавляется в auth-цепочку). Глобальная
  доступность для catalog — в TASK-109 (его цепочка добавит тот же common-фильтр).

acceptance_criteria:
  - given: "рефакторинг security в common"
    when: "сборка и тесты"
    then: "поведение auth не изменилось; все существующие auth unit+IT зелёные"
  - given: "ModularityTest"
    when: "verify()"
    then: "зелёный: common — shared-модуль; auth зависит от common легально; common НЕ зависит от auth"
  - given: "валидный access-токен на /api/v1/auth/change-password"
    when: "запрос"
    then: "аутентификация работает (200/204) — фильтр из common"
  - given: "невалидный/отсутствующий токен на защищённом auth-эндпоинте"
    when: "запрос"
    then: "401 RFC7807 (entry point из common)"

technical_notes: |
  - @Modulithic(systemName="TrueMarket", sharedModules="common") на TrueMarketApplication.
  - Перенос (delete из auth.security, create в common.security): JwtAuthenticationFilter,
    AuthenticatedUser (role→String), ProblemWriter, RestAuthenticationEntryPoint,
    RestAccessDeniedHandler. + новый порт AccessTokenAuthenticator (common).
  - auth: новый JwtAccessTokenAuthenticator (адаптер); TokenService.parseAccess →
    common AuthenticatedUser; AuthController импорт common.security.AuthenticatedUser.
  - AuthSecurityConfig: фильтр/entryPoint/accessDenied — public-бины из common.
  - Тесты: JwtAuthenticationFilterTest → common.security (мок порта AccessTokenAuthenticator);
    TokenServiceTest.parseAccess — проверка common AuthenticatedUser (id + authority).
  - Стек Java 25/Boot 4/Modulith 2.0.6.

api_changes: []  # контракт не меняется

db_changes: []

test_requirements:
  unit:
    - "JwtAuthenticationFilter (common): валидный токен → principal+ROLE_; invalid/нет → аноним"
    - "TokenService.parseAccess: валидный access → AuthenticatedUser(id, role); refresh/битый → InvalidToken"
    - "JwtAccessTokenAuthenticator (auth): valid → Optional с AuthenticatedUser; invalid → Optional.empty"
  integration:
    - "Все auth-IT зелёные без изменений (поведение сохранено)"
    - "ModularityTest зелёный (common shared, без цикла common↔auth)"

definition_of_done:
  - "☑ Компиляция release 25, Spotless зелёный"
  - "☑ Unit+IT зелёные (./mvnw -P integration verify), JaCoCo ≥80%"
  - "☑ ModularityTest зелёный; common — shared-модуль; нет common→auth"
  - "☑ Поведение auth не изменилось (контракт/коды ответов прежние)"
  - "☑ INDEX.md TASK-096 → DONE (backlog закрыт)"
  - "☑ Нет TODO/FIXME без тикета"
