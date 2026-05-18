package ru.truemarket.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Базовый Spring Security: stateless; открыты actuator/health, swagger и публичные auth-эндпоинты
 * (register/login/refresh, openapi security: []), остальное denyAll.
 *
 * <p>JWT-фильтр валидации и RBAC по эндпоинтам — TASK-106 (там же — модульная подача правил
 * безопасности, чтобы common не знал путей каждого модуля). rate-limit — TASK-107.
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
                    // Публичные auth-эндпоинты (openapi security: []).
                    // TASK-102: register; TASK-103: login, refresh.
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/logout")
                    .permitAll()
                    // Остальное закрыто. JWT-валидация/RBAC — TASK-106.
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
