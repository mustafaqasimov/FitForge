package com.mustafaqasimov.fitforge.service;

import static org.junit.jupiter.api.Assertions.*;

import com.mustafaqasimov.fitforge.dto.request.*;
import com.mustafaqasimov.fitforge.exception.ApiException;
import com.mustafaqasimov.fitforge.repository.ResourceRepository;
import com.mustafaqasimov.fitforge.service.interfaces.AuthService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AuthServiceTests {
  @Autowired AuthService auth;
  @Autowired ResourceRepository repo;

  @Test
  void registrationNormalizesEmailAndReturnsTypedTokens() {
    String email = UUID.randomUUID() + "@EXAMPLE.TEST";
    var r = auth.register(new RegisterRequest(email, "long-password-123", " Tester ", "Asia/Baku"));
    assertEquals(email.toLowerCase(), r.user().email());
    assertEquals("Tester", r.user().displayName());
    assertEquals("Asia/Baku", r.user().timezone());
    assertEquals("Bearer", r.tokenType());
    assertEquals(900, r.expiresIn());
    assertNotNull(r.accessToken());
    assertNotNull(r.refreshToken());
    var stored = repo.byEmail(email.toLowerCase()).orElseThrow();
    assertNotEquals("long-password-123", stored.data.get("passwordHash"));
    assertTrue(stored.data.get("passwordHash").toString().startsWith("$2a$12$"));
    var login = auth.login(new LoginRequest(email, "long-password-123", "Android"));
    assertEquals(r.user().id(), login.user().id());
  }

  @Test
  void invalidCredentialsHaveSameResponse() {
    var error =
        assertThrows(
            ApiException.class,
            () ->
                auth.login(
                    new LoginRequest(UUID.randomUUID() + "@example.test", "wrong-password", null)));
    assertEquals(401, error.status);
    assertEquals("INVALID_CREDENTIALS", error.code);
  }

  @Test
  void duplicateEmailIsRejected() {
    String email = UUID.randomUUID() + "@example.test";
    auth.register(new RegisterRequest(email, "long-password-123", "Tester", "UTC"));
    var error =
        assertThrows(
            ApiException.class,
            () ->
                auth.register(
                    new RegisterRequest(
                        email.toUpperCase(), "other-password-123", "Tester", "UTC")));
    assertEquals("EMAIL_IN_USE", error.code);
  }
}
