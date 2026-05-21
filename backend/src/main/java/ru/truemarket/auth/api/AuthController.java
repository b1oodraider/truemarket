package ru.truemarket.auth.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ru.truemarket.auth.api.dto.ChangePasswordRequest;
import ru.truemarket.auth.api.dto.LoginRequest;
import ru.truemarket.auth.api.dto.RefreshRequest;
import ru.truemarket.auth.api.dto.RegisterRequest;
import ru.truemarket.auth.api.dto.TokenPair;
import ru.truemarket.auth.security.AuthenticatedUser;
import ru.truemarket.auth.security.RateLimitGuard;
import ru.truemarket.auth.service.AuthenticationService;
import ru.truemarket.auth.service.PasswordChangeService;
import ru.truemarket.auth.service.RefreshTokenService;
import ru.truemarket.auth.service.RegistrationService;

/**
 * Аутентификация. Контракт — docs/api/openapi.yaml (single source of truth).
 *
 * <p>Реализовано: register (TASK-102), login + refresh (TASK-103), logout + ротация (TASK-104),
 * change-password (TASK-106, authenticated). rate-limit/429 — TASK-107.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final RegistrationService registrationService;
  private final AuthenticationService authenticationService;
  private final RefreshTokenService refreshTokenService;
  private final PasswordChangeService passwordChangeService;
  private final RateLimitGuard rateLimitGuard;

  public AuthController(
      RegistrationService registrationService,
      AuthenticationService authenticationService,
      RefreshTokenService refreshTokenService,
      PasswordChangeService passwordChangeService,
      RateLimitGuard rateLimitGuard) {
    this.registrationService = registrationService;
    this.authenticationService = authenticationService;
    this.refreshTokenService = refreshTokenService;
    this.passwordChangeService = passwordChangeService;
    this.rateLimitGuard = rateLimitGuard;
  }

  @PostMapping("/register")
  public ResponseEntity<TokenPair> register(
      @Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
    String ip = clientIp(http);
    rateLimitGuard.checkRegister(ip);
    TokenPair tokens = registrationService.register(request, ip, http.getHeader("User-Agent"));
    return ResponseEntity.status(HttpStatus.CREATED).body(tokens);
  }

  @PostMapping("/login")
  public TokenPair login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
    rateLimitGuard.checkLogin(clientIp(http), request.email());
    return authenticationService.login(
        request.email(), request.password(), http.getHeader("User-Agent"));
  }

  @PostMapping("/refresh")
  public TokenPair refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest http) {
    return authenticationService.refresh(request.refreshToken(), http.getHeader("User-Agent"));
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(@Valid @RequestBody RefreshRequest request) {
    refreshTokenService.logout(request.refreshToken());
  }

  @PostMapping("/change-password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void changePassword(
      @Valid @RequestBody ChangePasswordRequest request,
      @AuthenticationPrincipal AuthenticatedUser user) {
    passwordChangeService.changePassword(
        user.id(), request.currentPassword(), request.newPassword());
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
