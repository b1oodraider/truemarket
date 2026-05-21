package ru.truemarket.auth.security;

import java.time.Duration;

/**
 * Порт rate-limit по ключу (TASK-107). Реализация — Redis-backed token-bucket (распределённый,
 * bucket4j) или no-op (когда лимит выключен флагом). DIP: вызывающий не знает о Redis/bucket4j.
 */
public interface RateLimiter {

  /** Попытаться израсходовать 1 токен из ведра {@code key} (capacity на окно window). */
  Verdict tryAcquire(String key, int capacity, Duration window);

  /** Решение лимитера: разрешено, либо отказ с числом секунд до пополнения (для Retry-After). */
  record Verdict(boolean allowed, long retryAfterSeconds) {

    static Verdict pass() {
      return new Verdict(true, 0);
    }

    static Verdict block(long retryAfterSeconds) {
      return new Verdict(false, retryAfterSeconds);
    }
  }
}
