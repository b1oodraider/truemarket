package ru.truemarket.auth.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация auth-модуля (TASK-102). Биндинг {@code truemarket.auth.*}.
 *
 * <p>Секреты (jwt.secret) приходят ТОЛЬКО из переменных окружения (CLAUDE.md §8.1, §13.5) —
 * application.yaml содержит лишь плейсхолдер {@code ${JWT_SECRET:}}.
 */
@ConfigurationProperties(prefix = "truemarket.auth")
public record AuthProperties(Jwt jwt, Password password) {

  /** JWT: HS512, access TTL ≤ 15 мин (CLAUDE.md §13.1), refresh 30 дней. */
  public record Jwt(
      String secret, Duration accessTokenTtl, Duration refreshTokenTtl, String issuer) {}

  /** Политика паролей: argon2id + breach-check haveibeenpwned (CLAUDE.md §13.1). */
  public record Password(Argon2 argon2, boolean pwnedCheckEnabled, Hibp hibp) {
    public record Argon2(int iterations, int memoryKb, int parallelism) {}

    /** Pwned Passwords range API: базовый URL и таймаут на запрос. */
    public record Hibp(String baseUrl, Duration timeout) {}
  }
}
