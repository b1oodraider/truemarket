package ru.truemarket.auth.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.truemarket.auth.api.dto.TokenPair;
import ru.truemarket.auth.domain.RefreshToken;
import ru.truemarket.auth.domain.User;
import ru.truemarket.auth.repository.RefreshTokenRepository;
import ru.truemarket.auth.repository.UserRepository;

/**
 * Персистентная ротация refresh-токенов + replay-detection (TASK-104).
 *
 * <p>Отделён от {@link TokenService} (тот не знает про БД — SRP/тестируемость). TokenService
 * выпускает/проверяет подпись JWT; здесь — жизненный цикл строки {@code auth.refresh_tokens}.
 */
@Service
public class RefreshTokenService {

  private final RefreshTokenRepository tokens;
  private final UserRepository users;
  private final TokenService tokenService;
  private final RefreshTokenRevoker revoker;

  public RefreshTokenService(
      RefreshTokenRepository tokens,
      UserRepository users,
      TokenService tokenService,
      RefreshTokenRevoker revoker) {
    this.tokens = tokens;
    this.users = users;
    this.tokenService = tokenService;
    this.revoker = revoker;
  }

  /** Сохранить выданный refresh (login/register: rotatedFromId=null; ротация: id предыдущего). */
  @Transactional
  public void persistIssued(UUID userId, String refreshJwt, String deviceInfo, UUID rotatedFromId) {
    tokens.save(
        RefreshToken.issued(
            userId,
            tokenService.hash(refreshJwt),
            tokenService.refreshExpiry(refreshJwt),
            deviceInfo,
            rotatedFromId));
  }

  /**
   * Ротация: валидирует строку, при повторном использовании — отзывает всю цепочку пользователя
   * ({@link ReplayDetectedException}), иначе отзывает текущую и выдаёт новую пару.
   */
  @Transactional
  public TokenPair rotate(String refreshJwt, String deviceInfo) {
    UUID userId = tokenService.parseRefresh(refreshJwt);
    RefreshToken current =
        tokens
            .findByTokenHash(tokenService.hash(refreshJwt))
            .orElseThrow(() -> new InvalidTokenException("refresh token not recognized"));

    if (current.isRevoked() || tokens.existsByRotatedFrom(current.getId())) {
      revoker.revokeAllActive(userId); // REQUIRES_NEW: коммитится до rollback от throw ниже
      throw new ReplayDetectedException();
    }

    User user =
        users
            .findByIdAndDeletedAtIsNull(userId)
            .orElseThrow(() -> new InvalidTokenException("user not found or deleted"));

    current.revoke();
    TokenPair pair = tokenService.issueFor(user);
    persistIssued(userId, pair.refreshToken(), deviceInfo, current.getId());
    return pair;
  }

  /** Идемпотентный отзыв предъявленного refresh (logout). Неизвестный/уже отозванный — ok. */
  @Transactional
  public void logout(String refreshJwt) {
    tokens.findByTokenHash(tokenService.hash(refreshJwt)).ifPresent(RefreshToken::revoke);
  }
}
