package com.mustafaqasimov.fitforge.controller;

import com.mustafaqasimov.fitforge.dto.request.*;
import com.mustafaqasimov.fitforge.dto.response.AuthResponse;
import com.mustafaqasimov.fitforge.service.FitForgeService;
import com.mustafaqasimov.fitforge.service.interfaces.AuthService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  final FitForgeService service;
  final AuthService authService;
  final com.mustafaqasimov.fitforge.service.RateLimitService limits;

  public AuthController(
      FitForgeService service,
      AuthService authService,
      com.mustafaqasimov.fitforge.service.RateLimitService limits) {
    this.service = service;
    this.authService = authService;
    this.limits = limits;
  }

  void check(jakarta.servlet.http.HttpServletRequest r, Map<String, Object> b) {
    String email = String.valueOf(b.get("email")).trim().toLowerCase(java.util.Locale.ROOT);
    String account;
    try {
      account =
          java.util.HexFormat.of()
              .formatHex(
                  java.security.MessageDigest.getInstance("SHA-256")
                      .digest(email.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    if (!limits.allow("ip:" + r.getRemoteAddr(), 100) || !limits.allow("account:" + account, 10))
      throw new com.mustafaqasimov.fitforge.exception.ApiException(429, "RATE_LIMITED");
  }

  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(
      @Valid @RequestBody RegisterRequest b, jakarta.servlet.http.HttpServletRequest request) {
    check(request, Map.of("email", b.getEmail()));
    return ResponseEntity.status(201)
        .cacheControl(CacheControl.noStore())
        .body(authService.register(b));
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(
      @Valid @RequestBody LoginRequest b, jakarta.servlet.http.HttpServletRequest request) {
    check(request, Map.of("email", b.getEmail()));
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(authService.login(b));
  }

  @PostMapping("/refresh")
  public ResponseEntity<?> refresh(@RequestBody Map<String, Object> b) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.refresh(b));
  }

  @PostMapping("/logout")
  public ResponseEntity<?> logout(@RequestBody Map<String, Object> b) {
    service.logout(b);
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }
}
