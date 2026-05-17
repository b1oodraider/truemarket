package ru.truemarket.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ru.truemarket.auth.api.dto.TokenPair;
import ru.truemarket.auth.domain.User;
import ru.truemarket.auth.repository.UserRepository;

/** Unit-тесты входа/refresh (TASK-103). */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

  @Mock private UserRepository users;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private TokenService tokenService;
  @InjectMocks private AuthenticationService service;

  private static User buyer() {
    return User.newBuyer("buyer@example.com", null, "$argon2id$hash");
  }

  @Test
  void login_success_issuesTokens_andTouchesLastLogin() {
    User u = buyer();
    when(users.findByEmailAndDeletedAtIsNull("buyer@example.com")).thenReturn(Optional.of(u));
    when(passwordEncoder.matches("verylongpassword12", "$argon2id$hash")).thenReturn(true);
    when(tokenService.issueFor(u)).thenReturn(TokenPair.bearer("a", "r", 900));

    TokenPair p = service.login("buyer@example.com", "verylongpassword12");

    assertThat(p.accessToken()).isEqualTo("a");
    verify(tokenService).issueFor(u);
  }

  @Test
  void login_wrongPassword_throwsInvalidCredentials() {
    User u = buyer();
    when(users.findByEmailAndDeletedAtIsNull(any())).thenReturn(Optional.of(u));
    when(passwordEncoder.matches(any(), any())).thenReturn(false);

    assertThatThrownBy(() -> service.login("buyer@example.com", "bad-password-1"))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(tokenService, never()).issueFor(any());
  }

  @Test
  void login_unknownEmail_throwsInvalidCredentials_sameAsWrongPassword() {
    when(users.findByEmailAndDeletedAtIsNull(any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.login("nobody@example.com", "verylongpassword12"))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(passwordEncoder, never()).matches(any(), any());
  }

  @Test
  void refresh_validToken_issuesNewPair() {
    User u = buyer();
    UUID id = u.getId();
    when(tokenService.parseRefresh("good")).thenReturn(id);
    when(users.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(u));
    when(tokenService.issueFor(u)).thenReturn(TokenPair.bearer("a2", "r2", 900));

    TokenPair p = service.refresh("good");

    assertThat(p.refreshToken()).isEqualTo("r2");
  }

  @Test
  void refresh_invalidToken_propagatesInvalidToken() {
    when(tokenService.parseRefresh("bad")).thenThrow(new InvalidTokenException("invalid"));

    assertThatThrownBy(() -> service.refresh("bad")).isInstanceOf(InvalidTokenException.class);
    verify(users, never()).findByIdAndDeletedAtIsNull(any());
  }

  @Test
  void refresh_userDeleted_throwsInvalidToken() {
    UUID id = UUID.randomUUID();
    when(tokenService.parseRefresh("good")).thenReturn(id);
    when(users.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.refresh("good")).isInstanceOf(InvalidTokenException.class);
  }
}
