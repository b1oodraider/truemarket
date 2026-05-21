package ru.truemarket.auth.security;

import java.time.Duration;

/** Лимитер-заглушка (когда {@code truemarket.rate-limit.enabled=false}): всегда пропускает. */
class NoOpRateLimiter implements RateLimiter {

  @Override
  public Verdict tryAcquire(String key, int capacity, Duration window) {
    return Verdict.pass();
  }
}
