package ru.truemarket.auth.service;

/**
 * Превышен лимит запросов на auth-эндпоинт → HTTP 429 + Retry-After (CLAUDE.md §13.4, TASK-107).
 */
public class RateLimitExceededException extends RuntimeException {

  private final long retryAfterSeconds;

  public RateLimitExceededException(long retryAfterSeconds) {
    super("rate limit exceeded");
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
