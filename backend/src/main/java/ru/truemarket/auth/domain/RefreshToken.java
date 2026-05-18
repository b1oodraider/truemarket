package ru.truemarket.auth.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

/**
 * Персистентный refresh-токен с цепочкой ротации (схема {@code auth.refresh_tokens}, TASK-101).
 *
 * <p>Хранится ТОЛЬКО {@code token_hash} = SHA-256 от выданного JWT (сам токен не хранится, §13.5).
 * {@code rotatedFrom} — id предыдущего токена в цепочке (для replay-detection, TASK-104).
 */
@Entity
@Table(name = "refresh_tokens", schema = "auth")
public class RefreshToken {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "token_hash", nullable = false)
  private String tokenHash;

  @Column(name = "device_info")
  private String deviceInfo;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "rotated_from")
  private UUID rotatedFrom;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected RefreshToken() {}

  private RefreshToken(
      UUID userId, String tokenHash, Instant expiresAt, String deviceInfo, UUID rotatedFrom) {
    this.id = UUID.randomUUID();
    this.userId = userId;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
    this.deviceInfo = deviceInfo;
    this.rotatedFrom = rotatedFrom;
  }

  /** Новая выданная строка (rotatedFrom=null при login/register, иначе id предыдущего). */
  public static RefreshToken issued(
      UUID userId, String tokenHash, Instant expiresAt, String deviceInfo, UUID rotatedFrom) {
    return new RefreshToken(userId, tokenHash, expiresAt, deviceInfo, rotatedFrom);
  }

  public void revoke() {
    if (revokedAt == null) {
      this.revokedAt = Instant.now();
    }
  }

  public boolean isRevoked() {
    return revokedAt != null;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }
}
