package ru.truemarket.auth.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import ru.truemarket.common.security.JwtAuthenticationFilter;
import ru.truemarket.common.security.RestAccessDeniedHandler;
import ru.truemarket.common.security.RestAuthenticationEntryPoint;

/**
 * SecurityFilterChain модуля auth (модульная security, TASK-106, закрывает backlog TASK-095).
 *
 * <p>Каждый модуль вносит свою цепочку — common больше не хардкодит пути auth. Цепочка ограничена
 * {@code securityMatcher(/api/v1/auth/**)} и имеет приоритет над catch-all common. При будущем
 * split security «едет» вместе с модулем auth.
 */
@Configuration
class AuthSecurityConfig {

  static final String AUTH_PATHS = "/api/v1/auth/**";

  @Bean
  @Order(1)
  SecurityFilterChain authSecurityFilterChain(
      HttpSecurity http,
      JwtAuthenticationFilter jwtFilter,
      RestAuthenticationEntryPoint authenticationEntryPoint,
      RestAccessDeniedHandler accessDeniedHandler)
      throws Exception {
    http.securityMatcher(AUTH_PATHS)
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/logout")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/change-password")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
        .headers(
            h ->
                h.frameOptions(f -> f.deny())
                    .contentTypeOptions(c -> {})
                    .httpStrictTransportSecurity(
                        hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000)));
    return http.build();
  }
}
