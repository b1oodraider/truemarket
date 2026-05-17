package ru.truemarket.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Базовый skeleton Spring Security: stateless, всё закрыто кроме actuator/health и swagger.
 *
 * <p><b>Phase 0 — заглушка.</b> JWT-фильтр, RBAC и rate-limiting по эндпоинтам реализуются в Фазе 1
 * (TASK-101 и далее).
 */
@Configuration
public class SecurityConfig {

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth
                    // Открытые служебные пути
                    .requestMatchers(
                        "/actuator/health/**",
                        "/actuator/info",
                        "/actuator/prometheus",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**")
                    .permitAll()
                    // Phase 0: всё остальное закрыто. В Phase 1 здесь появится JWT-валидация.
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
