package ru.truemarket.common.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 401 для запросов без валидного access-токена к защищённому эндпоинту (RFC7807, shared, TASK-096).
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
      throws IOException {
    // Анти-энумерация: единое сообщение, причина (нет/битый/просрочен токен) не раскрывается.
    ProblemWriter.write(
        response, HttpStatus.UNAUTHORIZED, "authentication required", "unauthorized");
  }
}
