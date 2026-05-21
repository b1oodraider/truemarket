package ru.truemarket.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация rate-limit (CLAUDE.md §13.4, TASK-107). Биндинг {@code truemarket.rate-limit}.
 *
 * <p>{@code enabled} — фича-флаг (тест-профиль выключает, чтобы IT не требовали Redis). Значения
 * лимитов — из application.yaml.
 */
@ConfigurationProperties(prefix = "truemarket.rate-limit")
public record RateLimitProperties(boolean enabled, Auth auth, Register register) {

  /** /auth/login: лимиты по IP и по login(email) — оба измерения (§13.4). */
  public record Auth(int perIpPerMinute, int perLoginPerMinute) {}

  /** /auth/register: лимит по IP в час (§13.4). */
  public record Register(int perIpPerHour) {}
}
