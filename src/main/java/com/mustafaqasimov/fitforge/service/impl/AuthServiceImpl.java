package com.mustafaqasimov.fitforge.service.impl;

import static com.mustafaqasimov.fitforge.service.FitForgeService.map;

import com.mustafaqasimov.fitforge.dto.request.*;
import com.mustafaqasimov.fitforge.dto.response.*;
import com.mustafaqasimov.fitforge.entity.Resource;
import com.mustafaqasimov.fitforge.exception.ApiException;
import com.mustafaqasimov.fitforge.repository.ResourceRepository;
import com.mustafaqasimov.fitforge.service.FitForgeService;
import com.mustafaqasimov.fitforge.service.interfaces.AuthService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthServiceImpl implements AuthService {
  private static final String DUMMY_HASH =
      "$2a$12$yhnofnEDmL7otif7y1unQuNw8JFM4eFe4GafBW78DVKyA9Gpil4SS";
  private final ResourceRepository users;
  private final FitForgeService sessions;
  private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);

  public AuthServiceImpl(ResourceRepository users, FitForgeService sessions) {
    this.users = users;
    this.sessions = sessions;
  }

  private static String normalizedEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private static String prehash(String password) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(password.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public AuthResponse register(RegisterRequest request) {
    String email = normalizedEmail(request.getEmail());
    String timezone = request.getTimezone();
    try {
      ZoneId.of(timezone);
    } catch (Exception e) {
      throw new ApiException(
          422,
          "VALIDATION_ERROR",
          Map.of(
              "errors",
              List.of(
                  Map.of(
                      "field",
                      "timezone",
                      "code",
                      "INVALID_ZONE",
                      "message",
                      "Use a valid IANA timezone"))));
    }
    if (users.byEmail(email).isPresent()) throw new ApiException(409, "EMAIL_IN_USE");
    String time = Instant.now().toString();
    Resource user =
        new Resource(
            "user",
            null,
            map(
                "email",
                email,
                "passwordHash",
                passwords.encode(prehash(request.getPassword())),
                "displayName",
                request.getDisplayName().trim(),
                "timezone",
                timezone,
                "weightUnit",
                "kg",
                "weeklyGoal",
                3,
                "createdAt",
                time,
                "updatedAt",
                time));
    try {
      users.saveAndFlush(user);
    } catch (org.springframework.dao.DataIntegrityViolationException e) {
      throw new ApiException(409, "EMAIL_IN_USE");
    }
    return response(user, null);
  }

  @Override
  public AuthResponse login(LoginRequest request) {
    Resource user = users.byEmail(normalizedEmail(request.getEmail())).orElse(null);
    boolean matches =
        passwords.matches(
            prehash(request.getPassword()),
            user == null ? DUMMY_HASH : (String) user.data.get("passwordHash"));
    if (user == null || !matches) throw new ApiException(401, "INVALID_CREDENTIALS");
    return response(user, request.getDeviceName());
  }

  private AuthResponse response(Resource user, String device) {
    var tokens = sessions.tokens(user, UUID.randomUUID().toString(), device);
    var d = user.data;
    var profile =
        new UserProfileResponse(
            user.id,
            (String) d.get("email"),
            (String) d.get("displayName"),
            (String) d.get("timezone"),
            (String) d.get("weightUnit"),
            ((Number) d.get("weeklyGoal")).intValue(),
            Instant.parse(d.get("createdAt").toString()),
            Instant.parse(d.get("updatedAt").toString()));
    return new AuthResponse(
        profile,
        (String) tokens.get("accessToken"),
        (String) tokens.get("refreshToken"),
        "Bearer",
        900);
  }
}
