package com.mustafaqasimov.fitforge.dto.response;

public record AuthResponse(
    UserProfileResponse user,
    String accessToken,
    String refreshToken,
    String tokenType,
    int expiresIn) {}
