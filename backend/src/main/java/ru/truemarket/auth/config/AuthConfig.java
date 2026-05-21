package ru.truemarket.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Включает биндинг {@link AuthProperties} (TASK-102) и клиент HIBP (TASK-105). */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfig {

  /** RestClient к haveibeenpwned с baseUrl и таймаутами connect/read (TASK-105). */
  @Bean
  RestClient pwnedPasswordsClient(AuthProperties props) {
    var hibp = props.password().hibp();
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(hibp.timeout());
    factory.setReadTimeout(hibp.timeout());
    return RestClient.builder().baseUrl(hibp.baseUrl()).requestFactory(factory).build();
  }
}
