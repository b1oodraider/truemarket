package ru.truemarket.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Catch-all SecurityFilterChain (наименьший приоритет): открыты служебные пути (actuator/health,
 * swagger), всё прочее denyAll. Stateless + security-заголовки.
 *
 * <p>Модульная security (TASK-106, закрыт backlog TASK-095): пути каждого модуля настраиваются в
 * его собственной цепочке (см. {@code auth.security.AuthSecurityConfig}) — common их больше НЕ
 * хардкодит. Эта цепочка ловит только запросы вне модульных матчеров. rate-limit — TASK-107.
 */
@Configuration
public class SecurityConfig {

  @Bean
  @Order(Integer.MAX_VALUE)
  SecurityFilterChain catchAllSecurityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/actuator/health/**",
                        "/actuator/info",
                        "/actuator/prometheus",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**")
                    .permitAll()
                    .anyRequest()
                    .denyAll())
        .headers(
            h ->
                h.frameOptions(f -> f.deny())
                    .contentTypeOptions(c -> {})
                    .httpStrictTransportSecurity(
                        hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000)));
    return http.build();
  }
}
