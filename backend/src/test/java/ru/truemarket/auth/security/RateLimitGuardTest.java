package ru.truemarket.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import ru.truemarket.auth.config.RateLimitProperties;
import ru.truemarket.auth.config.RateLimitProperties.Auth;
import ru.truemarket.auth.config.RateLimitProperties.Register;
import ru.truemarket.auth.security.RateLimiter.Verdict;
import ru.truemarket.auth.service.RateLimitExceededException;

/** Unit-тесты применения лимитов (TASK-107). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RateLimitGuardTest {

  @Mock private RateLimiter rateLimiter;

  private RateLimitGuard guard() {
    var props = new RateLimitProperties(true, new Auth(5, 5), new Register(3));
    return new RateLimitGuard(rateLimiter, props);
  }

  @Test
  void underLimit_doesNotThrow() {
    when(rateLimiter.tryAcquire(any(), anyInt(), any())).thenReturn(Verdict.pass());

    assertThatCode(() -> guard().checkLogin("1.2.3.4", "a@b.com")).doesNotThrowAnyException();
  }

  @Test
  void loginPerIp_exceeded_throwsWithRetryAfter() {
    when(rateLimiter.tryAcquire(startsWith("rl:login:ip:"), anyInt(), any()))
        .thenReturn(Verdict.block(42));

    assertThatThrownBy(() -> guard().checkLogin("1.2.3.4", "a@b.com"))
        .isInstanceOf(RateLimitExceededException.class)
        .satisfies(
            e -> assertThat(((RateLimitExceededException) e).getRetryAfterSeconds()).isEqualTo(42));
  }

  @Test
  void loginPerLogin_exceeded_throws() {
    when(rateLimiter.tryAcquire(startsWith("rl:login:ip:"), anyInt(), any()))
        .thenReturn(Verdict.pass());
    when(rateLimiter.tryAcquire(startsWith("rl:login:user:"), anyInt(), any()))
        .thenReturn(Verdict.block(10));

    assertThatThrownBy(() -> guard().checkLogin("1.2.3.4", "a@b.com"))
        .isInstanceOf(RateLimitExceededException.class);
  }

  @Test
  void register_exceeded_throws() {
    when(rateLimiter.tryAcquire(startsWith("rl:register:ip:"), anyInt(), any()))
        .thenReturn(Verdict.block(3600));

    assertThatThrownBy(() -> guard().checkRegister("1.2.3.4"))
        .isInstanceOf(RateLimitExceededException.class);
  }

  @Test
  void loginKey_isCaseInsensitiveByEmail() {
    when(rateLimiter.tryAcquire(any(), anyInt(), any())).thenReturn(Verdict.pass());

    guard().checkLogin("1.2.3.4", "MiXeD@Example.COM");

    org.mockito.Mockito.verify(rateLimiter)
        .tryAcquire(
            org.mockito.ArgumentMatchers.eq("rl:login:user:mixed@example.com"),
            anyInt(),
            any(Duration.class));
  }

  @Test
  void noOpRateLimiter_alwaysPasses() {
    RateLimiter noOp = new NoOpRateLimiter();
    assertThat(noOp.tryAcquire("any", 1, Duration.ofMinutes(1)).allowed()).isTrue();
  }
}
