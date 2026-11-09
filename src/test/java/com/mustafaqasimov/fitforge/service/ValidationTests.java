package com.mustafaqasimov.fitforge.service;

import static org.junit.jupiter.api.Assertions.*;

import com.mustafaqasimov.fitforge.exception.ApiException;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class ValidationTests {
  @Test
  void weightsRejectStringsPrecisionAndNegativeValues() {
    for (Object v : List.of("60", new BigDecimal("1.001"), -1, 1001))
      assertThrows(ApiException.class, () -> FitForgeService.weight(v));
    assertDoesNotThrow(() -> FitForgeService.weight(null));
    assertDoesNotThrow(() -> FitForgeService.weight(0));
    assertDoesNotThrow(() -> FitForgeService.weight(new BigDecimal("1000.00")));
  }

  @Test
  void integersDoNotTruncate() {
    assertThrows(ApiException.class, () -> FitForgeService.number(1.2, 1, 1000));
    assertThrows(ApiException.class, () -> FitForgeService.number("10", 1, 1000));
  }

  @Test
  void unknownFieldsAreRejected() {
    var e =
        assertThrows(
            ApiException.class,
            () -> FitForgeService.fields(Map.of("userId", UUID.randomUUID()), "name"));
    assertEquals(400, e.status);
  }

  @Test
  void canonicalBodiesIgnorePropertyOrderAndNumericScale() {
    assertEquals(
        FitForgeService.canonical(Map.of("a", 1, "b", List.of(2))),
        FitForgeService.canonical(
            Map.of("b", List.of(new BigDecimal("2.0")), "a", new BigDecimal("1.00"))));
    assertNotEquals(
        FitForgeService.canonical(Map.of("a", "x, b=y")),
        FitForgeService.canonical(Map.of("a", "x", "b", "y")));
  }

  @Test
  void timezoneRequiresValidZone() {
    assertDoesNotThrow(() -> FitForgeService.zone("Asia/Baku"));
    assertThrows(ApiException.class, () -> FitForgeService.zone("invalid-zone"));
  }

  @Test
  void snapshotCopyIsIndependent() {
    var original =
        FitForgeService.map("sets", new ArrayList<>(List.of(FitForgeService.map("reps", 10))));
    Map<String, Object> copy = FitForgeService.deep(original);
    FitForgeService.rows(original, "sets").getFirst().put("reps", 20);
    assertEquals(10, FitForgeService.rows(copy, "sets").getFirst().get("reps"));
  }
}
