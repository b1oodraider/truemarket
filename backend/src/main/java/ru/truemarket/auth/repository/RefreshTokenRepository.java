package ru.truemarket.auth.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ru.truemarket.auth.domain.RefreshToken;

/** Репозиторий персистентных refresh-токенов (TASK-104). */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  /** true, если у токена уже есть потомок — значит он уже был использован (ротирован). */
  boolean existsByRotatedFrom(UUID rotatedFrom);

  /** Отзыв всех активных токенов пользователя (replay/chain revocation, logout-all). */
  @Modifying
  @Query(
      "update RefreshToken t set t.revokedAt = :now"
          + " where t.userId = :userId and t.revokedAt is null")
  int revokeAllActiveByUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
