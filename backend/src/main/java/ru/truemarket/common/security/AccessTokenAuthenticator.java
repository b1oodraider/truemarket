package ru.truemarket.common.security;

import java.util.Optional;

/**
 * Порт валидации access-токена (shared, TASK-096).
 *
 * <p>DIP: common-фильтр аутентификации зависит от этого порта, а не от auth. Реализацию (валидация
 * JWT, знание секрета/claims) предоставляет модуль auth — common НЕ зависит от auth.
 */
public interface AccessTokenAuthenticator {

  /** Валидировать токен и вернуть principal; {@code Optional.empty()} при любой невалидности. */
  Optional<AuthenticatedUser> authenticate(String accessToken);
}
