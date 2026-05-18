package ru.truemarket.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

import ru.truemarket.auth.api.dto.TokenPair;
import ru.truemarket.auth.domain.RefreshToken;
import ru.truemarket.auth.domain.User;
import ru.truemarket.auth.repository.RefreshTokenRepository;
import ru.truemarket.auth.repository.UserRepository;

/** Unit-тесты ротации refresh + replay-detection (TASK-104). */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

  @Mock private RefreshTokenRepository tokens;
  @Mock private UserRepository users;
  @Mock private TokenService tokenService;
  @Mock private RefreshTokenRevoker revoker;
  @InjectMocks private RefreshTokenService service;

  private static RefreshToken row(UUID userId) {
    return RefreshToken.issued(userId, "hash", Instant.now().plusSeconds(3600), "ua", null);
  }

  @Test
  void persistIssued_savesRowWithHashAndExpiry() {
    UUID uid = UUID.randomUUID();
    Instant exp = Instant.now().plusSeconds(1000);
    when(tokenService.hash("jwt")).thenReturn("h");
    when(tokenService.refreshExpiry("jwt")).thenReturn(exp);

    service.persistIssued(uid, "jwt", "ua", null);

    verify(tokens).save(any(RefreshToken.class));
  }

  @Test
  void rotate_success_revokesOld_issuesNewPair() {
    UUID uid = UUID.randomUUID();
    User user = User.newBuyer("a@b.com", null, "$argon2id$h");
    RefreshToken current = row(uid);
    when(tokenService.parseRefresh("old")).thenReturn(uid);
    when(tokenService.hash("old")).thenReturn("h-old");
    when(tokens.findByTokenHash("h-old")).thenReturn(Optional.of(current));
    when(tokens.existsByRotatedFrom(current.getId())).thenReturn(false);
    when(users.findByIdAndDeletedAtIsNull(uid)).thenReturn(Optional.of(user));
    when(tokenService.issueFor(user)).thenReturn(TokenPair.bearer("a2", "r2", 900));
    when(tokenService.hash("r2")).thenReturn("h-new");
    when(tokenService.refreshExpiry("r2")).thenReturn(Instant.now().plusSeconds(3600));

    TokenPair p = service.rotate("old", "ua");

    assertThat(p.refreshToken()).isEqualTo("r2");
    assertThat(current.isRevoked()).isTrue();
    verify(tokens).save(any(RefreshToken.class)); // новая строка
    verify(tokens, never()).revokeAllActiveByUser(any(), any());
  }

  @Test
  void rotate_revokedToken_triggersReplay_revokeAll() {
    UUID uid = UUID.randomUUID();
    RefreshToken revoked = row(uid);
    revoked.revoke();
    when(tokenService.parseRefresh("old")).thenReturn(uid);
    when(tokenService.hash("old")).thenReturn("h");
    when(tokens.findByTokenHash("h")).thenReturn(Optional.of(revoked));

    assertThatThrownBy(() -> service.rotate("old", "ua"))
        .isInstanceOf(ReplayDetectedException.class);
    verify(revoker).revokeAllActive(uid);
  }

  @Test
  void rotate_alreadyRotated_triggersReplay() {
    UUID uid = UUID.randomUUID();
    RefreshToken current = row(uid);
    when(tokenService.parseRefresh("old")).thenReturn(uid);
    when(tokenService.hash("old")).thenReturn("h");
    when(tokens.findByTokenHash("h")).thenReturn(Optional.of(current));
    when(tokens.existsByRotatedFrom(current.getId())).thenReturn(true);

    assertThatThrownBy(() -> service.rotate("old", "ua"))
        .isInstanceOf(ReplayDetectedException.class);
    verify(revoker).revokeAllActive(uid);
  }

  @Test
  void rotate_tokenNotInDb_throwsInvalidToken_noRevokeAll() {
    when(tokenService.parseRefresh("old")).thenReturn(UUID.randomUUID());
    when(tokenService.hash("old")).thenReturn("h");
    when(tokens.findByTokenHash("h")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.rotate("old", "ua")).isInstanceOf(InvalidTokenException.class);
    verify(revoker, never()).revokeAllActive(any());
  }

  @Test
  void logout_activeToken_revoked() {
    RefreshToken t = row(UUID.randomUUID());
    when(tokenService.hash("jwt")).thenReturn("h");
    when(tokens.findByTokenHash("h")).thenReturn(Optional.of(t));

    service.logout("jwt");

    assertThat(t.isRevoked()).isTrue();
  }

  @Test
  void logout_unknownToken_isIdempotentNoop() {
    when(tokenService.hash("jwt")).thenReturn("h");
    when(tokens.findByTokenHash("h")).thenReturn(Optional.empty());

    service.logout("jwt"); // не бросает
  }
}
