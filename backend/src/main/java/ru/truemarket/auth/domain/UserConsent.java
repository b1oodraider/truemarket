package ru.truemarket.auth.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Согласие на обработку ПДн, 152-ФЗ (схема {@code auth.user_consents}, TASK-101).
 *
 * <p>Версионируется: при смене текста согласия — новая запись с новой {@code version}. {@code
 * acceptedIp} пишется в PG-тип {@code inet} через явный каст.
 */
@Entity
@Table(name = "user_consents", schema = "auth")
public class UserConsent {

  /** Тип согласия на обработку ПДн при регистрации (CLAUDE.md §5.3.3). */
  public static final String TYPE_PDN_PROCESSING = "pdn-processing";

  /** Текущая версия текста согласия на обработку ПДн. */
  public static final String PDN_VERSION_V1 = "pdn-v1.0";

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "consent_type", nullable = false)
  private String consentType;

  @Column(nullable = false)
  private String version;

  @CreationTimestamp
  @Column(name = "accepted_at", nullable = false, updatable = false)
  private Instant acceptedAt;

  @Column(name = "accepted_ip", nullable = false, columnDefinition = "inet")
  @ColumnTransformer(write = "?::inet")
  private String acceptedIp;

  @Column(name = "accepted_ua")
  private String acceptedUa;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  protected UserConsent() {}

  private UserConsent(
      UUID id,
      UUID userId,
      String consentType,
      String version,
      String acceptedIp,
      String acceptedUa) {
    this.id = id;
    this.userId = userId;
    this.consentType = consentType;
    this.version = version;
    this.acceptedIp = acceptedIp;
    this.acceptedUa = acceptedUa;
  }

  /** Согласие на обработку ПДн, принятое при регистрации (TASK-102). */
  public static UserConsent pdnProcessing(UUID userId, String acceptedIp, String acceptedUa) {
    return new UserConsent(
        UUID.randomUUID(), userId, TYPE_PDN_PROCESSING, PDN_VERSION_V1, acceptedIp, acceptedUa);
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getConsentType() {
    return consentType;
  }

  public String getVersion() {
    return version;
  }
}
