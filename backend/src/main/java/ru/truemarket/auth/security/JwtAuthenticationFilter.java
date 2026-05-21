package ru.truemarket.auth.security;

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

import ru.truemarket.auth.service.InvalidTokenException;
import ru.truemarket.auth.service.TokenService;

/**
 * Аутентификация по {@code Authorization: Bearer <access-jwt>} (stateless, TASK-106).
 *
 * <p>Валидный токен → {@link AuthenticatedUser} в {@code SecurityContext} с authority {@code
 * ROLE_<ROLE>}. При отсутствии/невалидности токена контекст не заполняется и запрос продолжается:
 * для permitAll-путей это норма, для защищённых — authorization вернёт 401 через entry point. Сам
 * фильтр не отвергает запрос (не ломает публичные пути присланным просроченным токеном).
 */
@Component
class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final TokenService tokenService;

  JwtAuthenticationFilter(TokenService tokenService) {
    this.tokenService = tokenService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith(BEARER_PREFIX)) {
      authenticate(header.substring(BEARER_PREFIX.length()));
    }
    chain.doFilter(request, response);
  }

  private void authenticate(String token) {
    try {
      AuthenticatedUser user = tokenService.parseAccess(token);
      var authentication =
          new UsernamePasswordAuthenticationToken(
              user, null, List.of(new SimpleGrantedAuthority(user.authority())));
      SecurityContextHolder.getContext().setAuthentication(authentication);
    } catch (InvalidTokenException e) {
      SecurityContextHolder.clearContext(); // невалидный токен → аноним, решает authorization
    }
  }
}
