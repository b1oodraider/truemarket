package ru.truemarket.common.security;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Пишет RFC 7807 (application/problem+json) ответ из security-фильтра, где
 * {@code @RestControllerAdvice} не работает (исключение возникает до контроллера), shared,
 * TASK-096.
 *
 * <p>Поля — фиксированные ASCII-литералы (без пользовательского ввода), поэтому ручная сборка JSON
 * безопасна от инъекций и не зависит от версии Jackson.
 */
final class ProblemWriter {

  private static final String ERR_BASE = "https://docs.truemarket.ru/errors/";

  private ProblemWriter() {}

  static void write(HttpServletResponse response, HttpStatus status, String detail, String slug)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response
        .getWriter()
        .write(
            """
            {"type":"%s%s","title":"%s","status":%d,"detail":"%s","trace_id":"%s"}\
            """
                .formatted(
                    ERR_BASE,
                    slug,
                    status.getReasonPhrase(),
                    status.value(),
                    detail,
                    UUID.randomUUID()));
  }
}
