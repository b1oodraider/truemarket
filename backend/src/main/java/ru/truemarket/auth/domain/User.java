package ru.truemarket.auth.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * Учётная запись (схема {@code auth.users}, миграция V202605170001, TASK-101).
 *
 * <p>152-ФЗ: soft-delete через {@code deletedAt}. {@code passwordHash} — argon2id, НИКОГДА не
 * возвращается в API. {@code role} мапится на PG named enum {@code user_role}.
 */
@Entity
@Table(name = "users", schema = "auth")
public class User {

  @Id private UUID id;

  @Column(nullable = false)
  private String email;

  private String phone;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(nullable = false, columnDefinition = "user_role")
  private UserRole role;

  @Column(name = "email_verified", nullable = false)
  private boolean emailVerified;

  @Column(name = "phone_verified", nullable = false)
  private boolean phoneVerified;

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Version
  @Column(nullable = false)
  private int version;

  protected User() {}

  private User(UUID id, String email, String phone, String passwordHash, UserRole role) {
    this.id = id;
    this.email = email;
    this.phone = phone;
    this.passwordHash = passwordHash;
    this.role = role;
  }

  /** Фабрика регистрации покупателя (TASK-102): role=buyer, не верифицирован. */
  public static User newBuyer(String email, String phone, String passwordHash) {
    return new User(UUID.randomUUID(), email, phone, passwordHash, UserRole.buyer);
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getPhone() {
    return phone;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public UserRole getRole() {
    return role;
  }

  public boolean isEmailVerified() {
    return emailVerified;
  }

  public boolean isPhoneVerified() {
    return phoneVerified;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
