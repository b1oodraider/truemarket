package ru.truemarket.auth.service;

import java.util.UUID;

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

  public AuthenticationService(
      UserRepository users, PasswordEncoder passwordEncoder, TokenService tokenService) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.tokenService = tokenService;
  }

  @Transactional
  public TokenPair login(String email, String rawPassword) {
    User user =
        users.findByEmailAndDeletedAtIsNull(email).orElseThrow(InvalidCredentialsException::new);
    if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
      throw new InvalidCredentialsException();
    }
    user.touchLastLogin();
    return tokenService.issueFor(user);
  }

  @Transactional(readOnly = true)
  public TokenPair refresh(String refreshToken) {
    UUID userId = tokenService.parseRefresh(refreshToken);
    User user =
        users
            .findByIdAndDeletedAtIsNull(userId)
            .orElseThrow(() -> new InvalidTokenException("user not found or deleted"));
    return tokenService.issueFor(user);
  }
}
