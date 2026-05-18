package ru.truemarket.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import ru.truemarket.auth.api.dto.TokenPair;
import ru.truemarket.auth.config.AuthProperties;
import ru.truemarket.auth.domain.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Выпуск JWT (HS512) — access (TTL ≤ 15 мин) + refresh (TTL 30 дней), CLAUDE.md §13.1.
 *
 * <p>TASK-102 — минимальный stateless-выпуск под контракт /auth/register. Персистентная ротация
 * refresh и /login/refresh-эндпоинты — TASK-103/104. Секрет — только из env (§13.5).
 */
@Service
public class TokenService {

  private static final String CLAIM_ROLE = "role";
  private static final String CLAIM_TYPE = "typ";
  private static final String TYPE_ACCESS = "access";
  private static final String TYPE_REFRESH = "refresh";

  private final SecretKey key;
  private final String issuer;
  private final Duration accessTtl;
  private final Duration refreshTtl;

  public TokenService(AuthProperties props) {
    var jwt = props.jwt();
    if (jwt.secret() == null || jwt.secret().getBytes(StandardCharsets.UTF_8).length < 64) {
      throw new IllegalStateException(
          "truemarket.auth.jwt.secret must be set and ≥ 64 bytes for HS512 (env JWT_SECRET)");
    }
    this.key = Keys.hmacShaKeyFor(jwt.secret().getBytes(StandardCharsets.UTF_8));
    this.issuer = jwt.issuer();
    this.accessTtl = jwt.accessTokenTtl();
    this.refreshTtl = jwt.refreshTokenTtl();
  }

  /** Пара токенов для только что зарегистрированного пользователя. */
  public TokenPair issueFor(User user) {
    Instant now = Instant.now();
    String access = build(user, now, accessTtl, TYPE_ACCESS, true);
    String refresh = build(user, now, refreshTtl, TYPE_REFRESH, false);
    return TokenPair.bearer(access, refresh, accessTtl.toSeconds());
  }

  /**
   * Валидирует refresh-токен (подпись, exp, claim typ=refresh) и возвращает userId. Любая
   * невалидность → {@link InvalidTokenException} (TASK-103). Персистентная ротация/replay-detection
   * — TASK-104.
   */
  public UUID parseRefresh(String refreshToken) {
    return UUID.fromString(verifiedRefreshClaims(refreshToken).getSubject());
  }

  /** Момент истечения refresh-токена (для персистенции, TASK-104). */
  public Instant refreshExpiry(String refreshToken) {
    return verifiedRefreshClaims(refreshToken).getExpiration().toInstant();
  }

  /** SHA-256 hex от токена — хранится вместо самого токена (§13.5, TASK-104). */
  public String hash(String token) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e); // не достижимо на стандартной JVM
    }
  }

  private Claims verifiedRefreshClaims(String refreshToken) {
    try {
      Claims claims =
          Jwts.parser().verifyWith(key).build().parseSignedClaims(refreshToken).getPayload();
      if (!TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class))) {
        throw new InvalidTokenException("not a refresh token");
      }
      return claims;
    } catch (InvalidTokenException e) {
      throw e;
    } catch (JwtException | IllegalArgumentException e) {
      throw new InvalidTokenException("invalid refresh token");
    }
  }

  private String build(User user, Instant now, Duration ttl, String type, boolean withRole) {
    var builder =
        Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject(user.getId().toString())
            .issuer(issuer)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(ttl)))
            .claim(CLAIM_TYPE, type);
    if (withRole) {
      builder.claim(CLAIM_ROLE, user.getRole().name());
    }
    return builder.signWith(key, Jwts.SIG.HS512).compact();
  }
}
