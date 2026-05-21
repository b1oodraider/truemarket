package ru.truemarket.common.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Аутентификация по {@code Authorization: Bearer <access-jwt>} (shared, stateless, TASK-096).
 *
 * <p>Валидный токен → {@link AuthenticatedUser} в {@code SecurityContext} с authority {@code
 * ROLE_<ROLE>}. При отсутствии/невалидности токена контекст не заполняется и запрос продолжается:
 * для permitAll-путей это норма, для защищённых — authorization вернёт 401 через entry point.
 *
 * <p>В common (shared): один и тот же фильтр подключается к цепочкам всех модулей. Валидацию токена
 * делегирует порту {@link AccessTokenAuthenticator} (реализация — в auth), поэтому common не
 * зависит от auth.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final AccessTokenAuthenticator authenticator;

  public JwtAuthenticationFilter(AccessTokenAuthenticator authenticator) {
    this.authenticator = authenticator;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith(BEARER_PREFIX)) {
      authenticator
          .authenticate(header.substring(BEARER_PREFIX.length()))
          .ifPresentOrElse(this::setAuthentication, SecurityContextHolder::clearContext);
    }
    chain.doFilter(request, response);
  }

  private void setAuthentication(AuthenticatedUser user) {
    var authentication =
        new UsernamePasswordAuthenticationToken(
            user, null, List.of(new SimpleGrantedAuthority(user.authority())));
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }
}
