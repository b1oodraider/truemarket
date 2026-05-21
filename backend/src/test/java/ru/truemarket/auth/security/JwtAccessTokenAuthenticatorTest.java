package ru.truemarket.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ru.truemarket.auth.service.InvalidTokenException;
import ru.truemarket.auth.service.TokenService;
import ru.truemarket.common.security.AuthenticatedUser;

/** Unit-тесты адаптера порта аутентификации (TASK-096). */
@ExtendWith(MockitoExtension.class)
class JwtAccessTokenAuthenticatorTest {

  @Mock private TokenService tokenService;

  @Test
  void validToken_returnsPrincipal() {
    var principal = new AuthenticatedUser(UUID.randomUUID(), "buyer");
    when(tokenService.parseAccess("good")).thenReturn(principal);

    assertThat(new JwtAccessTokenAuthenticator(tokenService).authenticate("good"))
        .contains(principal);
  }

  @Test
  void invalidToken_returnsEmpty() {
    when(tokenService.parseAccess("bad")).thenThrow(new InvalidTokenException("invalid token"));

    assertThat(new JwtAccessTokenAuthenticator(tokenService).authenticate("bad")).isEmpty();
  }
}
