package ru.truemarket.auth.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ru.truemarket.auth.api.dto.LoginRequest;
import ru.truemarket.auth.api.dto.RefreshRequest;
import ru.truemarket.auth.api.dto.RegisterRequest;
import ru.truemarket.auth.api.dto.TokenPair;
import ru.truemarket.auth.service.AuthenticationService;
import ru.truemarket.auth.service.RegistrationService;

/**
 * Аутентификация. Контракт — docs/api/openapi.yaml (single source of truth).
 *
 * <p>Реализовано: register (TASK-102), login + refresh (TASK-103). Отложено: /logout и
 * персистентная ротация refresh — TASK-104; rate-limit/429 — TASK-107.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final RegistrationService registrationService;
  private final AuthenticationService authenticationService;

  public AuthController(
      RegistrationService registrationService, AuthenticationService authenticationService) {
    this.registrationService = registrationService;
    this.authenticationService = authenticationService;
  }

  @PostMapping("/register")
  public ResponseEntity<TokenPair> register(
      @Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
    TokenPair tokens =
        registrationService.register(request, clientIp(http), http.getHeader("User-Agent"));
    return ResponseEntity.status(HttpStatus.CREATED).body(tokens);
  }

  @PostMapping("/login")
  public TokenPair login(@Valid @RequestBody LoginRequest request) {
    return authenticationService.login(request.email(), request.password());
  }

  @PostMapping("/refresh")
  public TokenPair refresh(@Valid @RequestBody RefreshRequest request) {
    return authenticationService.refresh(request.refreshToken());
  }

  /** Реальный IP за reverse-proxy (server.forward-headers-strategy=framework уже учитывает XFF). */
  private static String clientIp(HttpServletRequest http) {
    String xff = http.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
      return xff.split(",")[0].trim();
    }
    return http.getRemoteAddr();
  }
}
