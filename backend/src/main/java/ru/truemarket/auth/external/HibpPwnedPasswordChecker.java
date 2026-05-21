package ru.truemarket.auth.external;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import ru.truemarket.auth.config.AuthProperties;
import ru.truemarket.auth.service.PwnedPasswordChecker;

/**
 * Адаптер haveibeenpwned Pwned Passwords (k-anonymity range API), TASK-105.
 *
 * <p>Наружу уходит только первые 5 hex-символов SHA-1 пароля (k-anonymity) — сам пароль и полный
 * хеш не покидают сервис и не пишутся в лог (§13.5). Fail-open: любая ошибка/недоступность HIBP →
 * {@code false} + WARN (доступность регистрации важнее блокировки на сбое 3rd-party).
 */
@Component
class HibpPwnedPasswordChecker implements PwnedPasswordChecker {

  private static final Logger log = LoggerFactory.getLogger(HibpPwnedPasswordChecker.class);
  private static final int PREFIX_LENGTH = 5;

  private final RestClient client;
  private final boolean enabled;

  HibpPwnedPasswordChecker(RestClient pwnedPasswordsClient, AuthProperties props) {
    this.client = pwnedPasswordsClient;
    this.enabled = props.password().pwnedCheckEnabled();
  }

  @Override
  public boolean isCompromised(String rawPassword) {
    if (!enabled) {
      return false;
    }
    String sha1 = sha1Hex(rawPassword);
    String prefix = sha1.substring(0, PREFIX_LENGTH);
    String suffix = sha1.substring(PREFIX_LENGTH);
    try {
      String body =
          client
              .get()
              .uri("/range/{prefix}", prefix)
              .header("Add-Padding", "true")
              .retrieve()
              .body(String.class);
      return containsBreachedSuffix(body, suffix);
    } catch (RuntimeException e) {
      log.warn("HIBP pwned-passwords check unavailable, failing open", e);
      return false;
    }
  }

  /** Строки ответа — {@code SUFFIX:count}; компрометация — наличие suffix с count > 0. */
  private static boolean containsBreachedSuffix(String body, String suffix) {
    if (body == null) {
      return false;
    }
    return body.lines()
        .anyMatch(
            line -> {
              int sep = line.indexOf(':');
              return sep == suffix.length()
                  && suffix.equalsIgnoreCase(line.substring(0, sep))
                  && !"0".equals(line.substring(sep + 1).trim());
            });
  }

  private static String sha1Hex(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest).toUpperCase(Locale.ROOT);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-1 unavailable", e); // не достижимо на стандартной JVM
    }
  }
}
