package ru.truemarket.auth.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ru.truemarket.auth.api.dto.RegisterRequest;
import ru.truemarket.auth.api.dto.TokenPair;
import ru.truemarket.auth.service.RegistrationService;

/**
 * Аутентификация (TASK-102). Контракт — docs/api/openapi.yaml (single source of truth).
 *
 * <p>Phase 1 scope: только регистрация покупателя. /login, /refresh, /logout — TASK-103/104.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final RegistrationService registrationService;

  public AuthController(RegistrationService registrationService) {
    this.registrationService = registrationService;
  }

  @PostMapping("/register")
  public ResponseEntity<TokenPair> register(
      @Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
    TokenPair tokens =
        registrationService.register(request, clientIp(http), http.getHeader("User-Agent"));
    return ResponseEntity.status(HttpStatus.CREATED).body(tokens);
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
