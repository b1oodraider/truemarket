id: TASK-107
title: "Auth: rate-limit/429 на /login + /register (Redis-backed, распределённый)"
phase: 1
module: auth
priority: high
depends_on: [TASK-102, TASK-103]
estimated_hours: 8
status: completed
started_at: 2026-05-21
completed_at: 2026-05-21

context: |
  CLAUDE.md §13.4: защита auth-эндпоинтов от перебора/ботов.
   - /auth/login: 5/мин на IP И 5/мин на login(email) — оба измерения, срабатывает любое.
   - /auth/register: 3/час на IP.
   - После исчерпания → 429 + заголовок Retry-After (секунды).
  Сейчас лимитов нет (TASK-103 отметил rate-limit как TASK-107).

  Уточнение §13.4 (одобрено 2026-05-21): механизм — Redis-backed token-bucket
  (bucket4j-redis), НЕ Resilience4j. Причина: RateLimiter Resilience4j in-memory
  и не распределён — в мульти-pod prod (k8s) лимит умножался бы на число pod'ов.
  bucket4j-redis ведёт общий счёт по всем инстансам (CAS в Redis). §13.4 в CLAUDE.md
  поправлен соответствующим коммитом chore(claude-md).

design: |
  Лимиты применяются в контроллере (там доступны и IP из запроса, и email из DTO —
  не нужно читать тело в фильтре). RateLimitGuard.checkLogin(ip, email) /
  checkRegister(ip) → при исчерпании RateLimitExceededException(retryAfterSeconds)
  → 429 + Retry-After (AuthExceptionHandler).

  Порт RateLimiter { Verdict tryAcquire(key, capacity, window) } (DIP):
   - Bucket4jRateLimiter (Redis/Lettuce, @ConditionalOnProperty rate-limit.enabled=true):
     Bucket4jLettuce.casBasedBuilder(connection) → proxyManager.getProxy(key, cfg);
     tryConsumeAndReturnRemaining → isConsumed / getNanosToWaitForRefill.
   - NoOpRateLimiter (когда rate-limit.enabled=false): всегда allow. Тест-профиль
     выключает лимит → существующие IT не требуют Redis (как pwned-check в TASK-105).

  Ключи: "rl:login:ip:<ip>", "rl:login:user:<email-lower>", "rl:register:ip:<ip>".
  Bandwidth: capacity=N, refillGreedy(N, window) (login 5/1м, register 3/1ч).

  Изоляция: всё в auth (эндпоинты auth). Generalize в common — при 2-м потребителе
  (Rule of Three, TASK-096). Redis-клиент (Lettuce) поднимается из spring.data.redis.*.

acceptance_criteria:
  - given: "6 запросов /login с одного IP за минуту"
    when: "6-й запрос"
    then: "429 + Retry-After (секунды до пополнения)"
  - given: "5 запросов /login на один email с РАЗНЫХ IP"
    when: "6-й запрос тем же email"
    then: "429 (лимит per-login, защита целевого аккаунта)"
  - given: "4 запроса /register с одного IP за час"
    when: "4-й запрос"
    then: "429 + Retry-After"
  - given: "лимит не исчерпан"
    when: "/login или /register"
    then: "обрабатывается штатно (200/201/401 и т.д.)"
  - given: "rate-limit.enabled=false (тест-профиль)"
    when: "любой объём запросов"
    then: "лимит не применяется (NoOp), Redis не требуется"

technical_notes: |
  - pom: com.bucket4j:bucket4j_jdk17-lettuce:8.14.0 (тянет core+redis-common);
    Lettuce — из spring-boot-starter-data-redis. Testcontainers redis (test scope).
  - RateLimitProperties (@ConfigurationProperties truemarket.rate-limit): auth
    (perIpPerMinute, perLoginPerMinute), register (perIpPerHour), enabled (флаг).
  - RateLimitConfig (@ConditionalOnProperty enabled=true): RedisClient +
    StatefulRedisConnection<String,byte[]> (codec String/ByteArray) +
    LettuceBasedProxyManager<String> из spring.data.redis (host/port/password).
  - RateLimiter (порт, auth.security) + Bucket4jRateLimiter / NoOpRateLimiter
    (по флагу; NoOp — @ConditionalOnMissingBean). Verdict(allowed, retryAfterSeconds).
  - RateLimitGuard (@Component): строит ключи/лимиты, бросает
    RateLimitExceededException(retryAfter). email → lower-case для ключа.
  - AuthController: checkLogin/checkRegister в начале login/register (до argon2).
  - RateLimitExceededException → AuthExceptionHandler → ResponseEntity<ProblemDetail>
    429 + header Retry-After.
  - application.yaml: + truemarket.rate-limit.enabled (${RATE_LIMIT_ENABLED:true});
    application-test.yaml: enabled=false.
  - Стек Java 25/Boot 4; Redis (Lettuce). Конфиг лимитов уже в application.yaml.

api_changes:
  - { method: POST, path: /api/v1/auth/login, description: "429 + Retry-After при превышении (per-IP и per-login)" }
  - { method: POST, path: /api/v1/auth/register, description: "429 + Retry-After при превышении (per-IP/час)" }

db_changes: []  # Redis, не PG; схема не меняется

test_requirements:
  unit:
    - "RateLimitGuard: allow (RateLimiter не блокирует) → без исключения"
    - "RateLimitGuard: login per-ip исчерпан → RateLimitExceededException(retryAfter)"
    - "RateLimitGuard: login per-login исчерпан → RateLimitExceededException"
    - "RateLimitGuard: register per-ip исчерпан → RateLimitExceededException"
    - "NoOpRateLimiter: всегда allowed"
  integration:
    - "Testcontainers Redis+PG, rate-limit.enabled=true: /login 6× с IP → 6-й 429 + Retry-After"
    - "/register 4× с IP → 4-й 429"
    - "лимит не исчерпан → штатные коды (401 на неверный логин и т.п.)"
    - "существующие auth-IT (enabled=false) зелёные без Redis"

definition_of_done:
  - "☑ Компиляция release 25, Spotless зелёный"
  - "☑ Unit+IT зелёные (./mvnw -P integration verify), JaCoCo ≥80%"
  - "☑ ModularityTest зелёный (rate-limit в auth, без auth→common)"
  - "☑ Контракт openapi: 429 на /login и /register (TooManyRequests уже в компонентах)"
  - "☑ CLAUDE.md §13.4 поправлен (bucket4j-redis вместо Resilience4j) — chore(claude-md)"
  - "☑ INDEX.md TASK-107 → DONE"
  - "☑ Нет TODO/FIXME без тикета"
