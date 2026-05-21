package ru.truemarket.auth.security;

import java.util.Optional;

import org.springframework.stereotype.Component;

import ru.truemarket.auth.service.InvalidTokenException;
import ru.truemarket.auth.service.TokenService;
import ru.truemarket.common.security.AccessTokenAuthenticator;
import ru.truemarket.common.security.AuthenticatedUser;

/**
 * Адаптер порта {@link AccessTokenAuthenticator} (TASK-096): валидация access-токена через {@link
 * TokenService}. Связывает shared common-фильтр с JWT-логикой auth, не делая common зависимым от
 * auth.
 */
@Component
class JwtAccessTokenAuthenticator implements AccessTokenAuthenticator {

  private final TokenService tokenService;

  JwtAccessTokenAuthenticator(TokenService tokenService) {
    this.tokenService = tokenService;
  }

  @Override
  public Optional<AuthenticatedUser> authenticate(String accessToken) {
    try {
      return Optional.of(tokenService.parseAccess(accessToken));
    } catch (InvalidTokenException e) {
      return Optional.empty();
    }
  }
}
