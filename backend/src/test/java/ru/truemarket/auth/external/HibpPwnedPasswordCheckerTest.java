package ru.truemarket.auth.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import ru.truemarket.auth.config.AuthProperties;
import ru.truemarket.auth.config.AuthProperties.Password;

/** Unit-тесты адаптера haveibeenpwned (k-anonymity, fail-open), TASK-105. */
class HibpPwnedPasswordCheckerTest {

  private static final String PASSWORD = "verylongpassword12";
  private static final String SHA1 = sha1Hex(PASSWORD);
  private static final String PREFIX = SHA1.substring(0, 5);
  private static final String SUFFIX = SHA1.substring(5);

  private MockRestServiceServer server;

  private HibpPwnedPasswordChecker checker(boolean enabled) {
    RestClient.Builder builder = RestClient.builder().baseUrl("https://api.pwnedpasswords.com");
    server = MockRestServiceServer.bindTo(builder).build();
    var props = new AuthProperties(null, new Password(null, enabled, null));
    return new HibpPwnedPasswordChecker(builder.build(), props);
  }

  @Test
  void compromised_whenSuffixPresentWithPositiveCount() {
    HibpPwnedPasswordChecker c = checker(true);
    server
        .expect(requestTo(endsWith("/range/" + PREFIX)))
        .andRespond(
            withSuccess(
                SUFFIX + ":42\r\n0123456789012345678901234567890123:7", MediaType.TEXT_PLAIN));

    assertThat(c.isCompromised(PASSWORD)).isTrue();
    server.verify();
  }

  @Test
  void notCompromised_whenSuffixAbsent() {
    HibpPwnedPasswordChecker c = checker(true);
    server
        .expect(requestTo(endsWith("/range/" + PREFIX)))
        .andRespond(withSuccess("0123456789012345678901234567890123:7", MediaType.TEXT_PLAIN));

    assertThat(c.isCompromised(PASSWORD)).isFalse();
  }

  @Test
  void notCompromised_whenSuffixIsPaddingWithZeroCount() {
    HibpPwnedPasswordChecker c = checker(true);
    server
        .expect(requestTo(endsWith("/range/" + PREFIX)))
        .andRespond(withSuccess(SUFFIX + ":0", MediaType.TEXT_PLAIN));

    assertThat(c.isCompromised(PASSWORD)).isFalse();
  }

  @Test
  void failOpen_whenHibpReturnsServerError() {
    HibpPwnedPasswordChecker c = checker(true);
    server.expect(requestTo(endsWith("/range/" + PREFIX))).andRespond(withServerError());

    assertThat(c.isCompromised(PASSWORD)).isFalse();
  }

  @Test
  void disabled_returnsFalseWithoutHttpCall() {
    HibpPwnedPasswordChecker c = checker(false);

    assertThat(c.isCompromised(PASSWORD)).isFalse();
    server.verify(); // ни одного запроса не ожидалось
  }

  private static String sha1Hex(String value) {
    try {
      byte[] d = MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(d).toUpperCase(Locale.ROOT);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
