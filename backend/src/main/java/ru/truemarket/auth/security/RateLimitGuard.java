package ru.truemarket.auth.security;

import java.time.Duration;
import java.util.Locale;

import org.springframework.stereotype.Component;

import ru.truemarket.auth.config.RateLimitProperties;
import ru.truemarket.auth.service.RateLimitExceededException;

/**
 * Применение лимитов к auth-эндпоинтам (CLAUDE.md §13.4, TASK-107).
 *
 * <p>Вызывается из контроллера (там доступны и IP, и email — не нужно читать тело в фильтре). При
 * исчерпании любого измерения → {@link RateLimitExceededException} (429 + Retry-After).
 */
@Component
public class RateLimitGuard {

  private static final Duration MINUTE = Duration.ofMinutes(1);
  private static final Duration HOUR = Duration.ofHours(1);

  private final RateLimiter rateLimiter;
  private final RateLimitProperties props;

  public RateLimitGuard(RateLimiter rateLimiter, RateLimitProperties props) {
    this.rateLimiter = rateLimiter;
    this.props = props;
  }

  /** /login: по IP И по login(email) — срабатывает любой (защита от ботов и таргета). */
  public void checkLogin(String ip, String email) {
    enforce("rl:login:ip:" + ip, props.auth().perIpPerMinute(), MINUTE);
    enforce(
        "rl:login:user:" + email.toLowerCase(Locale.ROOT),
        props.auth().perLoginPerMinute(),
        MINUTE);
  }

  /** /register: по IP в час. */
  public void checkRegister(String ip) {
    enforce("rl:register:ip:" + ip, props.register().perIpPerHour(), HOUR);
  }

  private void enforce(String key, int capacity, Duration window) {
    RateLimiter.Verdict verdict = rateLimiter.tryAcquire(key, capacity, window);
    if (!verdict.allowed()) {
      throw new RateLimitExceededException(verdict.retryAfterSeconds());
    }
  }
}
