package ru.truemarket.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import ru.truemarket.auth.domain.UserRole;
import ru.truemarket.auth.service.InvalidTokenException;
import ru.truemarket.auth.service.TokenService;

/** Unit-тесты JWT-фильтра аутентификации (TASK-106). */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

  @Mock private TokenService tokenService;
  @Mock private FilterChain chain;

  private JwtAuthenticationFilter filter() {
    return new JwtAuthenticationFilter(tokenService);
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void validBearer_populatesSecurityContext_andContinues() throws Exception {
    var principal = new AuthenticatedUser(UUID.randomUUID(), UserRole.buyer);
    when(tokenService.parseAccess("good")).thenReturn(principal);
    var request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer good");
    var response = new MockHttpServletResponse();

    filter().doFilter(request, response, chain);

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isNotNull();
    assertThat(auth.getPrincipal()).isEqualTo(principal);
    assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_BUYER");
    verify(chain).doFilter(request, response);
  }

  @Test
  void invalidToken_clearsContext_butContinues() throws Exception {
    when(tokenService.parseAccess("bad")).thenThrow(new InvalidTokenException("invalid token"));
    var request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer bad");
    var response = new MockHttpServletResponse();

    filter().doFilter(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(chain).doFilter(request, response);
  }

  @Test
  void noAuthorizationHeader_continuesAnonymous() throws Exception {
    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();

    filter().doFilter(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(chain).doFilter(request, response);
  }
}
