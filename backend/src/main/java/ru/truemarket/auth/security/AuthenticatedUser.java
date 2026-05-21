package ru.truemarket.auth.security;

import java.util.UUID;

import ru.truemarket.auth.domain.UserRole;

/**
 * Аутентифицированный пользователь — principal в {@code SecurityContext} (TASK-106).
 *
 * <p>Строится из валидного access-JWT (id из subject, role из claim). Доступен в контроллерах через
 * {@code @AuthenticationPrincipal}. Пароль/хеш сюда не попадают.
 */
public record AuthenticatedUser(UUID id, UserRole role) {

  /** Authority для RBAC: Spring-конвенция {@code ROLE_<UPPER>} (для {@code hasRole}). */
  public String authority() {
    return "ROLE_" + role.name().toUpperCase(java.util.Locale.ROOT);
  }
}
