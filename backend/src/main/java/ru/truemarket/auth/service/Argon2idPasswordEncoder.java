package ru.truemarket.auth.service;

import org.springframework.stereotype.Component;

import ru.truemarket.auth.config.AuthProperties;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;

/**
 * argon2id-реализация (CLAUDE.md §13.1). Параметры (iterations/memory/parallelism) — из {@code
 * truemarket.auth.password.argon2}. Сырой пароль обнуляется в char[] после хеширования (де-факто
 * best practice argon2-jvm).
 */
@Component
public class Argon2idPasswordEncoder implements PasswordEncoder {

  private final Argon2 argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);
  private final AuthProperties.Password.Argon2 cfg;

  public Argon2idPasswordEncoder(AuthProperties props) {
    this.cfg = props.password().argon2();
  }

  @Override
  public String encode(String rawPassword) {
    char[] chars = rawPassword.toCharArray();
    try {
      return argon2.hash(cfg.iterations(), cfg.memoryKb(), cfg.parallelism(), chars);
    } finally {
      argon2.wipeArray(chars);
    }
  }

  @Override
  public boolean matches(String rawPassword, String encodedHash) {
    char[] chars = rawPassword.toCharArray();
    try {
      return argon2.verify(encodedHash, chars);
    } finally {
      argon2.wipeArray(chars);
    }
  }
}
