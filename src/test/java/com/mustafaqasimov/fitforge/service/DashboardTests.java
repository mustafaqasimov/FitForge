package com.mustafaqasimov.fitforge.service;

import static com.mustafaqasimov.fitforge.service.FitForgeService.map;
import static org.junit.jupiter.api.Assertions.*;

import com.mustafaqasimov.fitforge.entity.Resource;
import com.mustafaqasimov.fitforge.repository.ResourceRepository;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class DashboardTests {
  @Autowired FitForgeService service;
  @Autowired ResourceRepository repo;

  @Test
  void bakuPeriodUsesEndedAtAndExcludesUpperBoundary() {
    var user =
        repo.saveAndFlush(
            new Resource(
                "user",
                null,
                map(
                    "email",
                    UUID.randomUUID() + "@example.test",
                    "timezone",
                    "Asia/Baku",
                    "weeklyGoal",
                    3)));
    for (String ended :
        List.of(
            "2026-11-08T19:59:59Z",
            "2026-11-08T20:00:00Z",
            "2026-11-09T19:59:59Z",
            "2026-11-09T20:00:00Z")) {
      repo.save(
          new Resource(
              "workout",
              user.id,
              map(
                  "status",
                  "completed",
                  "startedAt",
                  "2026-11-08T19:00:00Z",
                  "endedAt",
                  ended,
                  "routineNameSnapshot",
                  null,
                  "exercises",
                  List.of(
                      map(
                          "sets",
                          List.of(map("isCompleted", true, "reps", 10, "weightKg", 60)))))));
    }
    repo.flush();
    var d = service.dashboard(user.id, Map.of("from", "2026-11-09", "to", "2026-11-10"));
    assertEquals(2, d.get("completedWorkouts"));
    assertEquals(2, d.get("completedSets"));
    assertNull(d.get("goalProgress"));
    assertEquals(4, ((List<?>) d.get("recentWorkouts")).size());
    assertEquals(
        0,
        ((java.math.BigDecimal) d.get("totalVolumeKg"))
            .compareTo(new java.math.BigDecimal("1200")));
  }
}
