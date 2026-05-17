id: TASK-102
title: "Auth: регистрация покупателя (POST /auth/register)"
phase: 1
module: auth
priority: critical
depends_on: [TASK-101]
estimated_hours: 10
status: in_progress
started_at: 2026-05-17

context: |
  Первая бизнес-фича Phase 1 на новом стеке (Java 25 + Spring Boot 4 + Modulith 2,
  ADR-010). Регистрация покупателя — точка входа всего пользовательского пути.

  openapi.yaml (single source of truth, §12.2 — менять контракт нельзя без
  отдельного решения) фиксирует: POST /api/v1/auth/register, тело RegisterRequest
  {email, phone?, password(min 12), accept_pdn_consent}, ответ 201 TokenPair
  {access_token, refresh_token, expires_in, token_type}; 400 / 409 / 429.

  152-ФЗ (§5.3.3): согласие на обработку ПДн — версионированная сущность
  auth.user_consents (создана миграцией TASK-101). При регистрации фиксируется
  consent с accepted_ip/accepted_ua.

  SCOPE-РЕЗОЛЮЦИЯ (конфликт roadmap ↔ контракт, утверждено перед кодом):
  Контракт требует TokenPair, а хеш пароля и JWT в roadmap были отнесены к
  TASK-105/103. Регистрация без них нереализуема. Решение:
   - TASK-102 включает МИНИМУМ: argon2id-хеширование (только хеш) + выпуск
     access/refresh JWT (stateless), чтобы /register соответствовал контракту.
   - TASK-103 пере-скоупится: POST /auth/login + POST /auth/refresh
     (переиспользует TokenService из 102) + ротация refresh-токенов.
   - TASK-104: персистентная ротация refresh (auth.refresh_tokens) +
     replay-detection (rotated_from chain).
   - TASK-105 пере-скоупится: проверка пароля на утечки (haveibeenpwned),
     смена пароля, политика. В TASK-102 breach-check НЕ входит.
   - TASK-107: rate-limit на /register (429). В TASK-102 — 400/409 только;
     429 в контракте остаётся, реализуется в 107.

acceptance_criteria:
  - given: "валидный RegisterRequest (email уникален, password ≥ 12, accept_pdn_consent=true)"
    when: "POST /api/v1/auth/register"
    then: "201; в auth.users создан пользователь role=buyer, password_hash=argon2id; в auth.user_consents — запись pdn-processing с accepted_ip/ua; тело — валидный TokenPair (access JWT TTL 15м, refresh)"

  - given: "email уже существует (citext, регистронезависимо)"
    when: "POST /api/v1/auth/register"
    then: "409 Conflict, application/problem+json (RFC 7807), пользователь не создан"

  - given: "password короче 12 символов ИЛИ невалидный email ИЛИ accept_pdn_consent=false"
    when: "POST /api/v1/auth/register"
    then: "400 Bad Request, application/problem+json с detail; транзакция не начата"

  - given: "ошибка на этапе создания consent"
    when: "POST /api/v1/auth/register"
    then: "вся операция откатывается (user не создан) — атомарность"

  - given: "password_hash в БД"
    when: "проверка хранения"
    then: "никогда не plaintext; argon2id (params из application.yaml); хеш не возвращается в API"

technical_notes: |
  Стек: Java 25, Spring Boot 4.0.6, Spring Modulith 2.0.6 (ADR-006 границы).
  Пакет ru.truemarket.auth, слои api/domain/service/repository/external/config.

  - domain: User (@Entity → auth.users), UserRole (@Enumerated STRING → user_role),
    UserConsent (@Entity → auth.user_consents). Маппинг enum на PG-тип user_role
    через hibernate (columnDefinition / @JdbcType при необходимости).
  - repository: UserRepository, UserConsentRepository (Spring Data JPA).
    existsByEmail (citext — сравнение регистронезависимое на стороне БД).
  - service: RegistrationService (@Transactional, оркестрация), PasswordEncoder
    — argon2id обёртка над de.mkammerer:argon2-jvm, параметры из
    truemarket.auth.password.argon2 (iterations/memory-kb/parallelism).
  - service: TokenService — выпуск access(HS512, TTL PT15M)/refresh JWT через
    io.jsonwebtoken (jjwt 0.13). Секрет из truemarket.auth.jwt.secret (env).
    ВАЖНО: jjwt 0.13 — проверить API (0.12→0.13 могли быть изменения).
  - api: AuthController POST /api/v1/auth/register; DTO RegisterRequest,
    TokenPair, Problem строго по openapi.yaml. Bean Validation (@Email,
    @Size(min=12), @AssertTrue для accept_pdn_consent).
  - config: SecurityConfig (common) сейчас denyAll — добавить permitAll для
    POST /api/v1/auth/register (security: [] в openapi). НЕ расширять Security
    сверх этого (JWT-фильтр/RBAC — TASK-106).
  - Ошибки: @RestControllerAdvice → RFC 7807 (application/problem+json),
    400 (валидация), 409 (DataIntegrityViolation/предпроверка email|phone).
    trace_id в Problem.
  - Идемпотентность/429 — не в scope (TASK-107).
  - Граница модулей: всё внутри auth.*; common только утилиты. ModularityTest
    должен оставаться зелёным.

api_changes:
  - method: POST
    path: /api/v1/auth/register
    description: "Реализация (контракт уже в openapi.yaml — НЕ меняется)"

db_changes: []  # таблицы из TASK-101; новых миграций нет

test_requirements:
  unit:
    - "RegistrationService: успех — user(role=buyer)+consent создаются, пароль хешируется"
    - "RegistrationService: дубль email → конфликт, БД не мутируется"
    - "RegistrationService: accept_pdn_consent=false → ошибка валидации"
    - "RegistrationService: откат транзакции при ошибке создания consent"
    - "Argon2idPasswordEncoder: encode→matches true; разный соль → разный хеш; не plaintext"
    - "TokenService: выпущенный access JWT парсится, exp ≈ now+15м, sub/claims корректны"
  integration:
    - "POST /register валидный → 201 + TokenPair + строки в auth.users/user_consents (Testcontainers PG 16)"
    - "POST /register дубль email (разный регистр) → 409 problem+json"
    - "POST /register password<12 / bad email / consent=false → 400 problem+json"
    - "Транзакционный откат при сбое consent (user отсутствует)"
    - "Security: /auth/register доступен без аутентификации (permitAll)"

definition_of_done:
  - "☑ Код компилируется (release 25), spotless:check зелёный"
  - "☑ Unit + integration тесты зелёные (./mvnw -P integration verify)"
  - "☑ Покрытие auth-бизнес-логики ≥ 80% (CLAUDE.md §9.2/§9.3 п.6)"
  - "☑ ModularityTest зелёный (границы модулей не нарушены)"
  - "☑ Контракт openapi.yaml соблюдён (без изменений контракта)"
  - "☑ Пароль только argon2id; секреты только из env; нет PII в логах"
  - "☑ INDEX.md TASK-102 → DONE после merge; TASK-103/105 пере-скоуп зафиксирован"
  - "☑ Нет TODO/FIXME без тикета"
