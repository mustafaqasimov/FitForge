package com.mustafaqasimov.fitforge.exception;

import java.util.*;

public class ApiException extends RuntimeException {
  public final int status;
  public final String code;
  public final Map<String, Object> extensions;

  public ApiException(int status, String code) {
    this(status, code, Map.of());
  }

  public ApiException(int status, String code, Map<String, Object> extensions) {
    super(code);
    this.status = status;
    this.code = code;
    this.extensions = extensions;
  }
}
