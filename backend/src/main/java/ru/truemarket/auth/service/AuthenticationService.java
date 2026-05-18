package ru.truemarket.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.truemarket.auth.api.dto.TokenPair;
import ru.truemarket.auth.domain.User;
import ru.truemarket.auth.repository.UserRepository;

/**
 * Вход и обновление токена (TASK-103). Stateless: refresh валидируется по подписи/exp/typ.
 * Персистентная ротация + replay-detection — TASK-104.
 *
 * <p>Anti-enumeration: {@link InvalidCredentialsException} одинаков для несуществующего email и
 * неверного пароля — существование аккаунта не раскрывается.
 */
@Service
public class AuthenticationService {

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final TokenService tokenService;
  private final RefreshTokenService refreshTokens;

  public AuthenticationService(
      UserRepository users,
      PasswordEncoder passwordEncoder,
      TokenService tokenService,
      RefreshTokenService refreshTokens) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.tokenService = tokenService;
    this.refreshTokens = refreshTokens;
  }

  @Transactional
  public TokenPair login(String email, String rawPassword, String deviceInfo) {
    User user =
        users.findByEmailAndDeletedAtIsNull(email).orElseThrow(InvalidCredentialsException::new);
    if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
      throw new InvalidCredentialsException();
    }
    user.touchLastLogin();
    TokenPair pair = tokenService.issueFor(user);
    refreshTokens.persistIssued(user.getId(), pair.refreshToken(), deviceInfo, null);
    return pair;
  }

  /** Делегирует персистентной ротации с replay-detection (TASK-104). */
  public TokenPair refresh(String refreshToken, String deviceInfo) {
    return refreshTokens.rotate(refreshToken, deviceInfo);
  }
}
