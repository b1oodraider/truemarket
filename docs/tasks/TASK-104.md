id: TASK-104
title: "Auth: персистентная ротация refresh-токенов + replay-detection + /logout"
phase: 1
module: auth
priority: critical
depends_on: [TASK-103]
estimated_hours: 8
status: in_progress
started_at: 2026-05-18

context: |
  TASK-103 выпускал refresh stateless (только подпись/exp). Это времянка:
  украденный refresh нельзя отозвать, нет обнаружения повторного использования.
  TASK-104 делает refresh персистентным (таблица auth.refresh_tokens, TASK-101)
  с ротацией и replay-detection через цепочку rotated_from (CLAUDE.md §13.1:
  "ротация при использовании").

  Схема (TASK-101, неизменна): auth.refresh_tokens(id, user_id FK→users
  ON DELETE CASCADE, token_hash sha-256 unique, device_info, expires_at,
  revoked_at, rotated_from FK→self ON DELETE SET NULL, created_at).

design: |
  Refresh-токен клиенту = тот же подписанный JWT (typ=refresh, jti, exp);
  сервер дополнительно хранит строку refresh_tokens с token_hash=SHA-256(jwt).
  Гибрид: подпись/exp проверяются stateless (быстро), отзыв/ротация —
  по хранимой строке.

  Выпуск (register TASK-102 / login TASK-103 / refresh): создаётся строка
  refresh_tokens (token_hash, user_id, expires_at = exp JWT, device_info=UA,
  rotated_from = id предыдущей при ротации).

  /refresh:
   1. parseRefresh (подпись/exp/typ) → userId.
   2. hash = SHA-256(token); найти строку по token_hash.
   3. строки нет → 401 (никогда не выпускался / вычищен).
   4. revoked_at != null ИЛИ уже есть потомок (этот токен уже ротирован) →
      REPLAY: отозвать ВСЮ цепочку этого user (revoke all active) → 401.
   5. иначе: revoked_at=now текущей; создать новую (rotated_from=текущая.id);
      выдать новую пару access+refresh.

  /logout (POST /api/v1/auth/logout, body {refresh_token}): hash → строка →
  revoked_at=now → 204. Идемпотентно (повтор/неизвестный → 204, без утечки).
  Bearer-защита и logout-all — TASK-106 (нет JWT-фильтра до 106); отзыв по
  предъявленному refresh безопасен (нужно владеть токеном). openapi-skeleton
  тело logout не фиксирует — не нарушение контракта.

acceptance_criteria:
  - given: "валидный refresh, строка активна, не ротирована"
    when: "POST /auth/refresh"
    then: "200 новая пара; старая строка revoked_at!=null; новая rotated_from=старая.id"
  - given: "refresh, который уже был использован (ротирован) — replay"
    when: "POST /auth/refresh повторно тем же токеном"
    then: "401; все активные refresh пользователя отозваны (chain revocation)"
  - given: "refresh с валидной подписью, но строки нет в БД"
    when: "POST /auth/refresh"
    then: "401"
  - given: "истёкший refresh (exp в прошлом)"
    when: "POST /auth/refresh"
    then: "401 (parseRefresh отвергает)"
  - given: "активный refresh"
    when: "POST /auth/logout {refresh_token}"
    then: "204; строка revoked_at!=null; повторный /refresh этим токеном → 401"
  - given: "logout неизвестным/уже отозванным токеном"
    when: "POST /auth/logout"
    then: "204 (идемпотентно, без раскрытия)"
  - given: "register/login"
    when: "успех"
    then: "создана строка refresh_tokens (token_hash, expires_at, device_info)"

technical_notes: |
  - domain RefreshToken (@Entity auth.refresh_tokens): фабрика issued(userId,
    tokenHash, expiresAt, deviceInfo, rotatedFromId|null); revoke(); helpers
    isActive()/isRevoked(). rotated_from — как UUID-колонка (не @ManyToOne:
    избегаем ленивых графов; ON DELETE SET NULL на стороне БД).
  - RefreshTokenRepository: findByTokenHash; existsByRotatedFrom (есть ли
    потомок → токен уже ротирован); @Modifying bulk revoke активных по userId
    (chain/replay revocation); deleteByExpiresAtBefore (очистка — крон позже).
  - TokenService: + hash(token) (SHA-256 hex); issueFor оставить чистым
    (только JWT). Персистенция — в RefreshTokenService (новый), чтобы
    TokenService не знал про БД (SRP, тестируемость).
  - RefreshTokenService (@Transactional): persistIssued(user, refreshJwt,
    deviceInfo, rotatedFromId); rotate(refreshJwt, deviceInfo) →
    {newPair}; replay → ReplayDetectedException(revoke all)→401;
    logout(refreshJwt).
  - RegistrationService/AuthenticationService: после issueFor — persist
    строки (передать device_info=UA). AuthenticationService.refresh →
    делегирует RefreshTokenService.rotate.
  - AuthController: /logout endpoint; пробросить UA в register/login/refresh
    для device_info.
  - SecurityConfig: permitAll + /api/v1/auth/logout (bearer/RBAC — TASK-106).
  - AuthExceptionHandler: ReplayDetectedException → 401 (тот же ответ
    "invalid credentials", anti-enumeration).
  - Транзакционность: ротация атомарна (revoke old + insert new в одной
    @Transactional); гонка двойного refresh → unique(token_hash) или
    повторная ротация ловится replay-логикой.
  - Стек Java 25/Boot 4/Modulith 2; ddl-auto=none (схема — Flyway, TASK-101).

api_changes:
  - { method: POST, path: /api/v1/auth/refresh, description: "теперь персистентная ротация (контракт не меняется)" }
  - { method: POST, path: /api/v1/auth/logout, description: "реализация (revoke refresh)" }

db_changes: []  # таблица из TASK-101; новых миграций нет

test_requirements:
  unit:
    - "RefreshTokenService.rotate: успех — old revoked, new с rotated_from, новая пара"
    - "rotate: повтор ротированного токена → ReplayDetected + revoke all"
    - "rotate: токена нет в БД → InvalidToken"
    - "logout: активный → revoked; неизвестный → идемпотентно ок"
    - "TokenService.hash: детерминирован, SHA-256 hex"
  integration:
    - "register → строка refresh_tokens создана"
    - "login → /refresh: 200, ротация (old revoked, new active) в БД"
    - "повторный /refresh старым токеном → 401 + все refresh user отозваны"
    - "/refresh строкой не из БД (валидная подпись чужого секрета? — нет; берём валидный, удаляем строку) → 401"
    - "/logout активным → 204, далее /refresh → 401"
    - "/logout повторно → 204 (идемпотентно)"
    - "security: /logout доступен (permitAll до TASK-106)"

definition_of_done:
  - "☑ Компиляция release 25, Spotless зелёный"
  - "☑ Unit+IT зелёные (./mvnw -P integration verify, JDK26→r25), JaCoCo"
  - "☑ ModularityTest зелёный"
  - "☑ Контракт openapi соблюдён; /logout задокументирован"
  - "☑ token_hash хранит только SHA-256 (не сам токен); токены/пароли не в логах"
  - "☑ INDEX.md TASK-104 → DONE после merge; stateless-времянка TASK-103 заменена"
  - "☑ Нет TODO/FIXME без тикета"
