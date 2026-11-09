package com.mustafaqasimov.fitforge.security;

import com.auth0.jwt.*;
import com.auth0.jwt.algorithms.Algorithm;
import com.mustafaqasimov.fitforge.exception.Errors;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfig {
  private final Algorithm algorithm;

  public SecurityConfig(@Value("${app.security.jwt.secret}") String secret) {
    if (secret.length() < 32)
      throw new IllegalArgumentException("JWT key must contain at least 32 characters");
    algorithm = Algorithm.HMAC256(secret);
  }

  public String token(UUID user) {
    Instant now = Instant.now();
    return JWT.create()
        .withSubject(user.toString())
        .withIssuer("fitforge")
        .withAudience("fitforge-api")
        .withIssuedAt(now)
        .withExpiresAt(now.plusSeconds(900))
        .withJWTId(UUID.randomUUID().toString())
        .sign(algorithm);
  }

  @Bean
  org.springframework.security.core.userdetails.UserDetailsService users() {
    return username -> {
      throw new org.springframework.security.core.userdetails.UsernameNotFoundException(
          "Bearer authentication required");
    };
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http) throws Exception {
    var filter =
        new OncePerRequestFilter() {
          protected void doFilterInternal(
              HttpServletRequest req, HttpServletResponse res, FilterChain chain)
              throws ServletException, IOException {
            String h = req.getHeader("Authorization");
            if (h != null && h.startsWith("Bearer ")) {
              try {
                var jwt =
                    JWT.require(algorithm)
                        .withIssuer("fitforge")
                        .withAudience("fitforge-api")
                        .withClaimPresence("exp")
                        .withClaimPresence("iat")
                        .withClaimPresence("jti")
                        .build()
                        .verify(h.substring(7));
                UUID id = UUID.fromString(jwt.getSubject());
                SecurityContextHolder.getContext()
                    .setAuthentication(
                        new UsernamePasswordAuthenticationToken(id, null, List.of()));
              } catch (Exception e) {
                res.setStatus(401);
                res.setContentType("application/problem+json");
                res.getWriter()
                    .write(
                        new tools.jackson.databind.ObjectMapper()
                            .writeValueAsString(
                                Errors.body(401, "TOKEN_EXPIRED", req.getRequestURI())));
                return;
              }
            }
            chain.doFilter(req, res);
          }
        };
    return http.csrf(c -> c.disable())
        .cors(c -> {})
        .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            c ->
                c.requestMatchers("/api/v1/auth/**", "/health")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            c ->
                c.authenticationEntryPoint(
                    (req, res, e) -> {
                      res.setStatus(401);
                      res.setContentType("application/problem+json");
                      res.getWriter()
                          .write(
                              new tools.jackson.databind.ObjectMapper()
                                  .writeValueAsString(
                                      Errors.body(
                                          401, "INVALID_CREDENTIALS", req.getRequestURI())));
                    }))
        .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  @Bean
  CorsConfigurationSource cors(@Value("${app.cors.allowed-origins}") String origins) {
    var c = new CorsConfiguration();
    c.setAllowedOrigins(Arrays.asList(origins.split(",")));
    c.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
    c.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
    c.setExposedHeaders(List.of("Location", "Retry-After"));
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", c);
    return source;
  }
}
