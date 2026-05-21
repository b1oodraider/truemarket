package ru.truemarket.auth.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Fallback rate-limit, когда {@code truemarket.rate-limit.enabled=false}: no-op, без Redis. */
@Configuration
@ConditionalOnProperty(name = "truemarket.rate-limit.enabled", havingValue = "false")
class NoOpRateLimitConfig {

  @Bean
  RateLimiter rateLimiter() {
    return new NoOpRateLimiter();
  }
}
