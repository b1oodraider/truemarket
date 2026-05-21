package ru.truemarket.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

import ru.truemarket.auth.api.dto.TokenPair;
import ru.truemarket.auth.config.AuthProperties;
import ru.truemarket.auth.domain.User;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/** Unit-тесты выпуска JWT (TASK-102). */
class TokenServiceTest {

  private static final String SECRET =
      "unit-test-secret-must-be-at-least-64-bytes-long-for-hs512-aaaaaaaaaaaaa";

  private final TokenService tokenService =
      new TokenService(
          new AuthProperties(
              new AuthProperties.Jwt(
                  SECRET, Duration.ofMinutes(15), Duration.ofDays(30), "truemarket"),
              new AuthProperties.Password(
                  new AuthProperties.Password.Argon2(2, 16384, 1), false, null)));

  @Test
  void issuedAccessToken_isParseable_withCorrectClaims() {
    User user = User.newBuyer("a@b.com", null, "$argon2id$h");

    TokenPair pair = tokenService.issueFor(user);

    assertThat(pair.tokenType()).isEqualTo("Bearer");
    assertThat(pair.expiresIn()).isEqualTo(900);
    assertThat(pair.accessToken()).isNotBlank();
    assertThat(pair.refreshToken()).isNotBlank();

    SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    var claims =
        Jwts.parser().verifyWith(key).build().parseSignedClaims(pair.accessToken()).getPayload();

    assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
    assertThat(claims.getIssuer()).isEqualTo("truemarket");
    assertThat(claims.get("role", String.class)).isEqualTo("buyer");
    assertThat(claims.get("typ", String.class)).isEqualTo("access");

    Instant exp = claims.getExpiration().toInstant();
    Instant now = Instant.now();
    assertThat(exp).isAfter(now.plus(Duration.ofMinutes(14)));
    assertThat(exp).isBefore(now.plus(Duration.ofMinutes(16)));
  }

  @Test
  void refreshToken_hasRefreshType_andNoRole() {
    User user = User.newBuyer("a@b.com", null, "$argon2id$h");
    TokenPair pair = tokenService.issueFor(user);

    SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    var claims =
        Jwts.parser().verifyWith(key).build().parseSignedClaims(pair.refreshToken()).getPayload();

    assertThat(claims.get("typ", String.class)).isEqualTo("refresh");
    assertThat(claims.get("role", String.class)).isNull();
  }

  @Test
  void parseRefresh_validRefresh_returnsUserId() {
    User user = User.newBuyer("a@b.com", null, "$argon2id$h");
    TokenPair pair = tokenService.issueFor(user);

    assertThat(tokenService.parseRefresh(pair.refreshToken())).isEqualTo(user.getId());
  }

  @Test
  void parseRefresh_accessToken_rejected() {
    User user = User.newBuyer("a@b.com", null, "$argon2id$h");
    TokenPair pair = tokenService.issueFor(user);

    assertThatThrownBy(() -> tokenService.parseRefresh(pair.accessToken()))
        .isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void parseRefresh_garbage_rejected() {
    assertThatThrownBy(() -> tokenService.parseRefresh("not.a.jwt"))
        .isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void parseAccess_validAccess_returnsIdAndRole() {
    User user = User.newBuyer("a@b.com", null, "$argon2id$h");
    TokenPair pair = tokenService.issueFor(user);

    var principal = tokenService.parseAccess(pair.accessToken());

    assertThat(principal.id()).isEqualTo(user.getId());
    assertThat(principal.role()).isEqualTo(user.getRole().name());
    assertThat(principal.authority()).isEqualTo("ROLE_BUYER");
  }

  @Test
  void parseAccess_refreshToken_rejected() {
    User user = User.newBuyer("a@b.com", null, "$argon2id$h");
    TokenPair pair = tokenService.issueFor(user);

    assertThatThrownBy(() -> tokenService.parseAccess(pair.refreshToken()))
        .isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void parseAccess_garbage_rejected() {
    assertThatThrownBy(() -> tokenService.parseAccess("not.a.jwt"))
        .isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void hash_isDeterministicSha256Hex() {
    String h1 = tokenService.hash("some-token");
    String h2 = tokenService.hash("some-token");
    assertThat(h1).isEqualTo(h2).hasSize(64).matches("[0-9a-f]{64}");
    assertThat(tokenService.hash("other")).isNotEqualTo(h1);
  }

  @Test
  void refreshExpiry_matchesTokenExp() {
    User user = User.newBuyer("a@b.com", null, "$argon2id$h");
    TokenPair pair = tokenService.issueFor(user);

    Instant exp = tokenService.refreshExpiry(pair.refreshToken());
    assertThat(exp).isAfter(Instant.now().plus(Duration.ofDays(29)));
  }

  @Test
  void shortSecret_isRejected() {
    assertThatThrownBy(
            () ->
                new TokenService(
                    new AuthProperties(
                        new AuthProperties.Jwt(
                            "too-short", Duration.ofMinutes(15), Duration.ofDays(30), "tm"),
                        new AuthProperties.Password(
                            new AuthProperties.Password.Argon2(2, 16384, 1), false, null))))
        .isInstanceOf(IllegalStateException.class);
  }
}
