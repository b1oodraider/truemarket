package ru.truemarket.auth.api;

import java.net.URI;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import ru.truemarket.auth.service.IncorrectPasswordException;
import ru.truemarket.auth.service.InvalidCredentialsException;
import ru.truemarket.auth.service.InvalidTokenException;
import ru.truemarket.auth.service.PasswordBreachedException;
import ru.truemarket.auth.service.RegistrationConflictException;
import ru.truemarket.auth.service.ReplayDetectedException;

/**
 * RFC 7807 (application/problem+json) для auth-эндпоинтов (TASK-102, CLAUDE.md §11 формат ошибок).
 *
 * <p>400 — Bean Validation; 409 — конфликт уникальности (предпроверка или гонка на UNIQUE). {@code
 * trace_id} — корреляция для логов/поддержки.
 */
@RestControllerAdvice(assignableTypes = AuthController.class)
class AuthExceptionHandler {

  private static final String ERR_BASE = "https://docs.truemarket.ru/errors/";

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail onValidation(MethodArgumentNotValidException ex) {
    String detail =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining("; "));
    return problem(HttpStatus.BAD_REQUEST, "Validation Failed", detail, "validation");
  }

  @ExceptionHandler(RegistrationConflictException.class)
  ProblemDetail onConflict(RegistrationConflictException ex) {
    return problem(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), "conflict");
  }

  @ExceptionHandler(PasswordBreachedException.class)
  ProblemDetail onPasswordBreached(PasswordBreachedException ex) {
    // Не анти-энумерация: причина в свойстве пароля, клиент должен выбрать другой.
    return problem(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), "breached-password");
  }

  @ExceptionHandler(IncorrectPasswordException.class)
  ProblemDetail onIncorrectPassword(IncorrectPasswordException ex) {
    // Пользователь аутентифицирован токеном — не анти-энумерация; сообщение прямое.
    return problem(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), "incorrect-password");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ProblemDetail onDataIntegrity(DataIntegrityViolationException ex) {
    return problem(HttpStatus.CONFLICT, "Conflict", "unique constraint violation", "conflict");
  }

  @ExceptionHandler({
    InvalidCredentialsException.class,
    InvalidTokenException.class,
    ReplayDetectedException.class
  })
  ProblemDetail onUnauthorized(RuntimeException ex) {
    // Единое сообщение (anti-enumeration): не раскрываем причину 401 (TASK-103).
    return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", "invalid credentials", "unauthorized");
  }

  private static ProblemDetail problem(
      HttpStatus status, String title, String detail, String slug) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
    pd.setTitle(title);
    pd.setType(URI.create(ERR_BASE + slug));
    pd.setProperty("trace_id", UUID.randomUUID().toString());
    return pd;
  }
}
