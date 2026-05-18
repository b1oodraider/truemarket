package ru.truemarket.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ru.truemarket.auth.api.dto.TokenPair;
import ru.truemarket.auth.domain.User;
import ru.truemarket.auth.repository.UserRepository;

/** Unit-тесты входа/refresh (TASK-103/104). */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

  @Mock private UserRepository users;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private TokenService tokenService;
  @Mock private RefreshTokenService refreshTokens;
  @InjectMocks private AuthenticationService service;

  private static User buyer() {
    return User.newBuyer("buyer@example.com", null, "$argon2id$hash");
  }

  @Test
  void login_success_issuesAndPersistsRefresh_andTouchesLastLogin() {
    User u = buyer();
    when(users.findByEmailAndDeletedAtIsNull("buyer@example.com")).thenReturn(Optional.of(u));
    when(passwordEncoder.matches("verylongpassword12", "$argon2id$hash")).thenReturn(true);
    when(tokenService.issueFor(u)).thenReturn(TokenPair.bearer("a", "r", 900));

    TokenPair p = service.login("buyer@example.com", "verylongpassword12", "JUnit-UA");

    assertThat(p.accessToken()).isEqualTo("a");
    verify(refreshTokens).persistIssued(eq(u.getId()), eq("r"), eq("JUnit-UA"), isNull());
  }

  @Test
  void login_wrongPassword_throwsInvalidCredentials_noPersist() {
    when(users.findByEmailAndDeletedAtIsNull(any())).thenReturn(Optional.of(buyer()));
    when(passwordEncoder.matches(any(), any())).thenReturn(false);

    assertThatThrownBy(() -> service.login("buyer@example.com", "bad-password-1", "ua"))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(refreshTokens, never()).persistIssued(any(), any(), any(), any());
  }

  @Test
  void login_unknownEmail_throwsInvalidCredentials() {
    when(users.findByEmailAndDeletedAtIsNull(any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.login("nobody@example.com", "verylongpassword12", "ua"))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(passwordEncoder, never()).matches(any(), any());
  }

  @Test
  void refresh_delegatesToRefreshTokenService() {
    when(refreshTokens.rotate("rt", "ua")).thenReturn(TokenPair.bearer("a2", "r2", 900));

    TokenPair p = service.refresh("rt", "ua");

    assertThat(p.refreshToken()).isEqualTo("r2");
    verify(refreshTokens).rotate("rt", "ua");
  }
}
