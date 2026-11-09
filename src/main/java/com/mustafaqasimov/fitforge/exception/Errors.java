package com.mustafaqasimov.fitforge.exception;

import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class Errors {
  public static Map<String, Object> body(int status, String code, String path) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("type", "about:blank");
    m.put("title", code);
    m.put("status", status);
    m.put("code", code);
    m.put("detail", code.replace('_', ' '));
    m.put("instance", path);
    m.put(
        "traceId",
        java.util.Objects.requireNonNullElseGet(
            org.slf4j.MDC.get("traceId"), () -> UUID.randomUUID().toString()));
    return m;
  }

  @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
  ResponseEntity<?> validation(
      org.springframework.web.bind.MethodArgumentNotValidException e, HttpServletRequest request) {
    var errors =
        e.getBindingResult().getFieldErrors().stream()
            .map(
                f ->
                    Map.of(
                        "field",
                        f.getField(),
                        "code",
                        Objects.toString(f.getCode(), "INVALID_VALUE"),
                        "message",
                        Objects.toString(f.getDefaultMessage(), "Invalid value")))
            .toList();
    return api(new ApiException(422, "VALIDATION_ERROR", Map.of("errors", errors)), request);
  }

  @ExceptionHandler(ApiException.class)
  ResponseEntity<?> api(ApiException e, HttpServletRequest r) {
    var b = body(e.status, e.code, r.getRequestURI());
    b.putAll(e.extensions);
    if (e.status == 422 && !b.containsKey("errors"))
      b.put(
          "errors",
          List.of(
              Map.of(
                  "field",
                  "request",
                  "code",
                  "INVALID_VALUE",
                  "message",
                  "Request validation failed")));
    return ResponseEntity.status(e.status)
        .header("Cache-Control", "no-store")
        .header("Retry-After", e.status == 429 ? "900" : "0")
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(b);
  }

  @ExceptionHandler({
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
    IllegalArgumentException.class
  })
  ResponseEntity<?> bad(Exception e, HttpServletRequest r) {
    return api(new ApiException(400, "BAD_REQUEST"), r);
  }

  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  ResponseEntity<?> conflict(Exception e, HttpServletRequest r) {
    return api(new ApiException(409, "CONFLICT"), r);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> other(Exception e, HttpServletRequest r) {
    return api(new ApiException(500, "INTERNAL_ERROR"), r);
  }
}
