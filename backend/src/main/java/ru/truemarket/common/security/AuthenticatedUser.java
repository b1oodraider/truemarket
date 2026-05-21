package ru.truemarket.common.security;

import java.util.Locale;
import java.util.UUID;

/**
 * Аутентифицированный пользователь — principal в {@code SecurityContext} (shared, TASK-096).
 *
 * <p>В common (shared-модуль), чтобы быть доступным всем модулям. {@code role} — строка (common не
 * знает доменных enum'ов конкретных модулей); конкретный модуль маппит свою роль в строку.
 */
public record AuthenticatedUser(UUID id, String role) {

  /** Authority для RBAC: Spring-конвенция {@code ROLE_<UPPER>} (для {@code hasRole}). */
  public String authority() {
    return "ROLE_" + role.toUpperCase(Locale.ROOT);
  }
}
