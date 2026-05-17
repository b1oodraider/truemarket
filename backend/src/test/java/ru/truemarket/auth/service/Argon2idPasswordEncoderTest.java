package ru.truemarket.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import ru.truemarket.auth.config.AuthProperties;

/** Unit-тесты argon2id (TASK-102, CLAUDE.md §13.1). Использует реальную argon2-jvm. */
class Argon2idPasswordEncoderTest {

  private final PasswordEncoder encoder =
      new Argon2idPasswordEncoder(
          new AuthProperties(
              new AuthProperties.Jwt(null, null, null, null),
              new AuthProperties.Password(new AuthProperties.Password.Argon2(2, 16384, 1))));

  @Test
  void encode_thenMatches_true() {
    String hash = encoder.encode("verylongpassword12");
    assertThat(encoder.matches("verylongpassword12", hash)).isTrue();
  }

  @Test
  void wrongPassword_matches_false() {
    String hash = encoder.encode("verylongpassword12");
    assertThat(encoder.matches("wrong-password-xx", hash)).isFalse();
  }

  @Test
  void hash_isNotPlaintext_andSalted() {
    String h1 = encoder.encode("verylongpassword12");
    String h2 = encoder.encode("verylongpassword12");
    assertThat(h1).doesNotContain("verylongpassword12").startsWith("$argon2id$");
    assertThat(h1).isNotEqualTo(h2); // разная соль
  }
}
