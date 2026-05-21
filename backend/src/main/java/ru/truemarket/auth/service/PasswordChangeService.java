package ru.truemarket.auth.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.truemarket.auth.domain.User;
import ru.truemarket.auth.repository.RefreshTokenRepository;
import ru.truemarket.auth.repository.UserRepository;

/**
 * Смена пароля аутентифицированным пользователем (TASK-106, перенесено из TASK-105).
 *
 * <p>Подтверждает владение текущим паролем, проверяет новый по базе утечек (TASK-105), сохраняет
 * argon2id-хеш и отзывает ВСЕ refresh-токены пользователя (logout everywhere — смена пароля
 * инвалидирует прежние сессии). Отзыв идёт напрямую через репозиторий в ТОЙ ЖЕ транзакции (атомарно
 * с изменением пароля) — в отличие от replay-сценария, где нужен REQUIRES_NEW.
 */
@Service
public class PasswordChangeService {

  private final UserRepository users;
  private final RefreshTokenRepository refreshTokens;
  private final PasswordEncoder passwordEncoder;
  private final PwnedPasswordChecker pwnedPasswordChecker;

  public PasswordChangeService(
      UserRepository users,
      RefreshTokenRepository refreshTokens,
      PasswordEncoder passwordEncoder,
      PwnedPasswordChecker pwnedPasswordChecker) {
    this.users = users;
    this.refreshTokens = refreshTokens;
    this.passwordEncoder = passwordEncoder;
    this.pwnedPasswordChecker = pwnedPasswordChecker;
  }

  @Transactional
  public void changePassword(UUID userId, String currentPassword, String newPassword) {
    User user =
        users
            .findByIdAndDeletedAtIsNull(userId)
            .orElseThrow(() -> new InvalidTokenException("user not found or deleted"));
    if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
      throw new IncorrectPasswordException();
    }
    if (pwnedPasswordChecker.isCompromised(newPassword)) {
      throw new PasswordBreachedException();
    }
    user.changePassword(passwordEncoder.encode(newPassword));
    refreshTokens.revokeAllActiveByUser(userId, Instant.now());
  }
}
