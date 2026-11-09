package com.mustafaqasimov.fitforge.controller;

import com.mustafaqasimov.fitforge.service.FitForgeService;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  final FitForgeService service;
  final com.mustafaqasimov.fitforge.service.RateLimitService limits;

  public AuthController(
      FitForgeService service, com.mustafaqasimov.fitforge.service.RateLimitService limits) {
    this.service = service;
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
  public ResponseEntity<?> register(
      @RequestBody Map<String, Object> b, jakarta.servlet.http.HttpServletRequest request) {
    check(request, b);
    return ResponseEntity.status(201)
        .cacheControl(CacheControl.noStore())
        .body(service.register(b));
  }

  @PostMapping("/login")
  public ResponseEntity<?> login(
      @RequestBody Map<String, Object> b, jakarta.servlet.http.HttpServletRequest request) {
    check(request, b);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.login(b));
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
