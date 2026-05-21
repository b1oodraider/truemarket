package ru.truemarket.auth.security;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;

/**
 * Бины распределённого rate-limit на Redis (bucket4j/Lettuce), TASK-107.
 *
 * <p>Активны только при {@code truemarket.rate-limit.enabled=true} (по умолчанию). Когда выключено
 * (тест-профиль) — поднимается {@link NoOpRateLimiter} из {@link NoOpRateLimitConfig}, и
 * подключение к Redis не требуется. Lettuce-клиент строится из {@code spring.data.redis.*}.
 */
@Configuration
@ConditionalOnProperty(
    name = "truemarket.rate-limit.enabled",
    havingValue = "true",
    matchIfMissing = true)
class RateLimitConfig {

  // Верхняя граница TTL ключа в Redis (>= самого длинного окна, register 1ч). Само окно лимита
  // задаётся в Bandwidth; стратегия лишь не даёт ключам жить вечно.
  private static final Duration KEY_TTL = Duration.ofHours(1);

  @Bean(destroyMethod = "shutdown")
  RedisClient rateLimitRedisClient(
      @Value("${spring.data.redis.host:localhost}") String host,
      @Value("${spring.data.redis.port:6379}") int port,
      @Value("${spring.data.redis.password:}") String password) {
    RedisURI.Builder uri = RedisURI.builder().withHost(host).withPort(port);
    if (password != null && !password.isBlank()) {
      uri.withPassword(password.toCharArray());
    }
    return RedisClient.create(uri.build());
  }

  @Bean(destroyMethod = "close")
  StatefulRedisConnection<String, byte[]> rateLimitRedisConnection(RedisClient client) {
    return client.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
  }

  @Bean
  LettuceBasedProxyManager<String> rateLimitProxyManager(
      StatefulRedisConnection<String, byte[]> connection) {
    return Bucket4jLettuce.casBasedBuilder(connection)
        .expirationAfterWrite(
            ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(KEY_TTL))
        .build();
  }

  @Bean
  RateLimiter rateLimiter(LettuceBasedProxyManager<String> proxyManager) {
    return new Bucket4jRateLimiter(proxyManager);
  }
}
