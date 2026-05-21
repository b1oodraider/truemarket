package ru.truemarket.auth.security;

import java.time.Duration;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;

/**
 * Распределённый rate-limit на Redis (bucket4j, CAS), TASK-107.
 *
 * <p>Счёт ведётся в Redis — общий по всем pod'ам (в отличие от in-memory Resilience4j).
 * Token-bucket с greedy-пополнением: capacity токенов на окно. Ведро живёт под ключом {@code key} в
 * Redis.
 */
class Bucket4jRateLimiter implements RateLimiter {

  private final LettuceBasedProxyManager<String> proxyManager;

  Bucket4jRateLimiter(LettuceBasedProxyManager<String> proxyManager) {
    this.proxyManager = proxyManager;
  }

  @Override
  public Verdict tryAcquire(String key, int capacity, Duration window) {
    BucketConfiguration configuration =
        BucketConfiguration.builder()
            .addLimit(limit -> limit.capacity(capacity).refillGreedy(capacity, window))
            .build();
    Bucket bucket = proxyManager.getProxy(key, () -> configuration);

    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    if (probe.isConsumed()) {
      return Verdict.pass();
    }
    long retryAfter = (long) Math.ceil(probe.getNanosToWaitForRefill() / 1_000_000_000.0);
    return Verdict.block(Math.max(retryAfter, 1));
  }
}
