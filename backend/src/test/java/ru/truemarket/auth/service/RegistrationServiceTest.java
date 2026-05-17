package ru.truemarket.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ru.truemarket.auth.api.dto.RegisterRequest;
import ru.truemarket.auth.api.dto.TokenPair;
import ru.truemarket.auth.domain.User;
import ru.truemarket.auth.domain.UserConsent;
import ru.truemarket.auth.repository.UserConsentRepository;
import ru.truemarket.auth.repository.UserRepository;

/** Unit-тесты бизнес-логики регистрации (TASK-102), без БД. */
@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

  @Mock private UserRepository users;
  @Mock private UserConsentRepository consents;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private TokenService tokenService;
  @InjectMocks private RegistrationService service;

  private static RegisterRequest req() {
    return new RegisterRequest("Buyer@Example.com", "+79991234567", "verylongpassword12", true);
  }

  @Test
  void success_createsUserAndConsent_andIssuesTokens() {
    when(users.existsByEmail("Buyer@Example.com")).thenReturn(false);
    when(users.existsByPhone("+79991234567")).thenReturn(false);
    when(passwordEncoder.encode("verylongpassword12")).thenReturn("$argon2id$hash");
    when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    when(tokenService.issueFor(any(User.class))).thenReturn(TokenPair.bearer("acc", "ref", 900));

    TokenPair result = service.register(req(), "203.0.113.7", "JUnit-UA");

    assertThat(result.accessToken()).isEqualTo("acc");
    assertThat(result.tokenType()).isEqualTo("Bearer");
    verify(users).save(any(User.class));
    verify(consents).save(any(UserConsent.class));
  }

  @Test
  void duplicateEmail_throwsConflict_andDoesNotPersist() {
    when(users.existsByEmail("Buyer@Example.com")).thenReturn(true);

    assertThatThrownBy(() -> service.register(req(), "1.2.3.4", "ua"))
        .isInstanceOf(RegistrationConflictException.class)
        .hasMessageContaining("email");

    verify(users, never()).save(any());
    verify(consents, never()).save(any());
    verify(passwordEncoder, never()).encode(any());
  }

  @Test
  void duplicatePhone_throwsConflict_andDoesNotPersist() {
    when(users.existsByEmail(any())).thenReturn(false);
    when(users.existsByPhone("+79991234567")).thenReturn(true);

    assertThatThrownBy(() -> service.register(req(), "1.2.3.4", "ua"))
        .isInstanceOf(RegistrationConflictException.class)
        .hasMessageContaining("phone");

    verify(users, never()).save(any());
  }

  @Test
  void password_isHashed_neverStoredRaw() {
    when(users.existsByEmail(any())).thenReturn(false);
    when(users.existsByPhone(any())).thenReturn(false);
    when(passwordEncoder.encode("verylongpassword12")).thenReturn("$argon2id$hash");
    when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    when(tokenService.issueFor(any())).thenReturn(TokenPair.bearer("a", "r", 900));

    service.register(req(), "1.2.3.4", "ua");

    verify(passwordEncoder).encode("verylongpassword12");
    verify(users)
        .save(
            org.mockito.ArgumentMatchers.argThat(
                u -> u.getPasswordHash().equals("$argon2id$hash")));
  }
}
