package ru.truemarket.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ru.truemarket.auth.domain.User;
import ru.truemarket.auth.repository.RefreshTokenRepository;
import ru.truemarket.auth.repository.UserRepository;

/** Unit-тесты смены пароля (TASK-106). */
@ExtendWith(MockitoExtension.class)
class PasswordChangeServiceTest {

  @Mock private UserRepository users;
  @Mock private RefreshTokenRepository refreshTokens;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private PwnedPasswordChecker pwnedPasswordChecker;
  @InjectMocks private PasswordChangeService service;

  private final UUID uid = UUID.randomUUID();
  private final User user = User.newBuyer("a@b.com", null, "$argon2id$old");

  @Test
  void success_encodesNew_savesViaEntity_revokesAllRefresh() {
    when(users.findByIdAndDeletedAtIsNull(uid)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("current12chars", "$argon2id$old")).thenReturn(true);
    when(pwnedPasswordChecker.isCompromised("newpassword123")).thenReturn(false);
    when(passwordEncoder.encode("newpassword123")).thenReturn("$argon2id$new");

    service.changePassword(uid, "current12chars", "newpassword123");

    assertThat(user.getPasswordHash()).isEqualTo("$argon2id$new");
    verify(refreshTokens).revokeAllActiveByUser(eq(uid), any(Instant.class));
  }

  @Test
  void wrongCurrentPassword_throws_noChangeNoRevoke() {
    when(users.findByIdAndDeletedAtIsNull(uid)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("wrong-current1", "$argon2id$old")).thenReturn(false);

    assertThatThrownBy(() -> service.changePassword(uid, "wrong-current1", "newpassword123"))
        .isInstanceOf(IncorrectPasswordException.class);

    assertThat(user.getPasswordHash()).isEqualTo("$argon2id$old");
    verify(passwordEncoder, never()).encode(any());
    verify(refreshTokens, never()).revokeAllActiveByUser(any(), any());
  }

  @Test
  void breachedNewPassword_throws_noChangeNoRevoke() {
    when(users.findByIdAndDeletedAtIsNull(uid)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("current12chars", "$argon2id$old")).thenReturn(true);
    when(pwnedPasswordChecker.isCompromised("breachedpass12")).thenReturn(true);

    assertThatThrownBy(() -> service.changePassword(uid, "current12chars", "breachedpass12"))
        .isInstanceOf(PasswordBreachedException.class);

    assertThat(user.getPasswordHash()).isEqualTo("$argon2id$old");
    verify(passwordEncoder, never()).encode(any());
    verify(refreshTokens, never()).revokeAllActiveByUser(any(), any());
  }

  @Test
  void unknownUser_throwsInvalidToken() {
    when(users.findByIdAndDeletedAtIsNull(uid)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.changePassword(uid, "current12chars", "newpassword123"))
        .isInstanceOf(InvalidTokenException.class);
  }
}
