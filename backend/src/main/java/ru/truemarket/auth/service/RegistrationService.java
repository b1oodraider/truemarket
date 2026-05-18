package ru.truemarket.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.truemarket.auth.api.dto.RegisterRequest;
import ru.truemarket.auth.api.dto.TokenPair;
import ru.truemarket.auth.domain.User;
import ru.truemarket.auth.domain.UserConsent;
import ru.truemarket.auth.repository.UserConsentRepository;
import ru.truemarket.auth.repository.UserRepository;

/**
 * Регистрация покупателя (TASK-102).
 *
 * <p>Атомарно: создаётся {@link User} (role=buyer, argon2id-хеш) + {@link UserConsent} на обработку
 * ПДн (152-ФЗ) с IP/UA. Конфликт уникальности → {@link RegistrationConflictException} (409).
 * Формат-валидация (email, длина пароля, accept_pdn_consent) — Bean Validation на DTO (400).
 */
@Service
public class RegistrationService {

  private final UserRepository users;
  private final UserConsentRepository consents;
  private final PasswordEncoder passwordEncoder;
  private final TokenService tokenService;
  private final RefreshTokenService refreshTokens;

  public RegistrationService(
      UserRepository users,
      UserConsentRepository consents,
      PasswordEncoder passwordEncoder,
      TokenService tokenService,
      RefreshTokenService refreshTokens) {
    this.users = users;
    this.consents = consents;
    this.passwordEncoder = passwordEncoder;
    this.tokenService = tokenService;
    this.refreshTokens = refreshTokens;
  }

  @Transactional
  public TokenPair register(RegisterRequest request, String acceptedIp, String acceptedUa) {
    if (users.existsByEmail(request.email())) {
      throw new RegistrationConflictException("email already registered");
    }
    if (request.phone() != null && users.existsByPhone(request.phone())) {
      throw new RegistrationConflictException("phone already registered");
    }

    String hash = passwordEncoder.encode(request.password());
    User user = users.save(User.newBuyer(request.email(), request.phone(), hash));
    consents.save(UserConsent.pdnProcessing(user.getId(), acceptedIp, acceptedUa));

    TokenPair pair = tokenService.issueFor(user);
    refreshTokens.persistIssued(user.getId(), pair.refreshToken(), acceptedUa, null);
    return pair;
  }
}
