package com.mustafaqasimov.fitforge.dto.response;

import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
    UUID id,
    String email,
    String displayName,
    String timezone,
    String weightUnit,
    int weeklyGoal,
    Instant createdAt,
    Instant updatedAt) {}
