package com.mustafaqasimov.fitforge.controller;

import com.mustafaqasimov.fitforge.service.FitForgeService;
import java.net.URI;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ApiController {
  final FitForgeService service;

  public ApiController(FitForgeService service) {
    this.service = service;
  }

  static ResponseEntity<?> created(String route, Map<String, Object> b) {
    return ResponseEntity.created(URI.create("/api/v1/" + route + "/" + b.get("id"))).body(b);
  }

  @GetMapping("/me")
  public Object me(@AuthenticationPrincipal UUID u) {
    return service.profile(u);
  }

  @PatchMapping("/me")
  public Object me(@AuthenticationPrincipal UUID u, @RequestBody Map<String, Object> b) {
    return service.profile(u, b);
  }

  @GetMapping("/exercises")
  public Object exercises(@AuthenticationPrincipal UUID u, @RequestParam Map<String, String> q) {
    return service.listExercises(u, q);
  }

  @GetMapping("/exercises/{id}")
  public Object exercise(@AuthenticationPrincipal UUID u, @PathVariable UUID id) {
    return service.exerciseView(u, id);
  }

  @PostMapping("/exercises")
  public Object exercise(@AuthenticationPrincipal UUID u, @RequestBody Map<String, Object> b) {
    return created("exercises", service.createExercise(u, b));
  }

  @PatchMapping("/exercises/{id}")
  public Object exercise(
      @AuthenticationPrincipal UUID u, @PathVariable UUID id, @RequestBody Map<String, Object> b) {
    return service.patchExercise(u, id, b);
  }

  @GetMapping("/routines")
  public Object routines(@AuthenticationPrincipal UUID u, @RequestParam Map<String, String> q) {
    return service.routines(u, q);
  }

  @GetMapping("/routines/{id}")
  public Object routine(@AuthenticationPrincipal UUID u, @PathVariable UUID id) {
    return service.routine(u, id);
  }

  @PostMapping("/routines")
  public Object routine(@AuthenticationPrincipal UUID u, @RequestBody Map<String, Object> b) {
    return created("routines", service.createRoutine(u, b));
  }

  @PatchMapping("/routines/{id}")
  public Object routine(
      @AuthenticationPrincipal UUID u, @PathVariable UUID id, @RequestBody Map<String, Object> b) {
    return service.patchRoutine(u, id, b);
  }

  @DeleteMapping("/routines/{id}")
  public ResponseEntity<?> archive(@AuthenticationPrincipal UUID u, @PathVariable UUID id) {
    service.archive(u, id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/routines/{id}/duplicate")
  public Object duplicate(
      @AuthenticationPrincipal UUID u, @PathVariable UUID id, @RequestBody Map<String, Object> b) {
    return created("routines", service.duplicate(u, id, b));
  }

  @PutMapping("/routines/order")
  public Object order(@AuthenticationPrincipal UUID u, @RequestBody Map<String, Object> b) {
    return service.order(u, b);
  }

  @PostMapping("/workouts")
  public Object start(
      @AuthenticationPrincipal UUID u,
      @RequestBody Map<String, Object> b,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    return created("workouts", service.start(u, b, key));
  }

  @GetMapping("/workouts/active")
  public Object active(@AuthenticationPrincipal UUID u) {
    return service.active(u);
  }

  @GetMapping("/workouts/{id}")
  public Object workout(@AuthenticationPrincipal UUID u, @PathVariable UUID id) {
    return service.workout(u, id);
  }

  @GetMapping("/workouts")
  public Object workouts(@AuthenticationPrincipal UUID u, @RequestParam Map<String, String> q) {
    return service.workouts(u, q);
  }

  @PatchMapping("/workouts/{id}")
  public Object workout(
      @AuthenticationPrincipal UUID u, @PathVariable UUID id, @RequestBody Map<String, Object> b) {
    return service.patchWorkout(u, id, b);
  }

  @PostMapping("/workouts/{id}/exercises")
  public Object addExercise(
      @AuthenticationPrincipal UUID u, @PathVariable UUID id, @RequestBody Map<String, Object> b) {
    var r = service.addExercise(u, id, b);
    return ResponseEntity.created(
            URI.create(
                "/api/v1/workouts/"
                    + id
                    + "/exercises/"
                    + ((Map<?, ?>) r.get("exercise")).get("id")))
        .body(r);
  }

  @DeleteMapping("/workouts/{id}/exercises/{exerciseId}")
  public Object removeExercise(
      @AuthenticationPrincipal UUID u,
      @PathVariable UUID id,
      @PathVariable UUID exerciseId,
      @RequestParam int version) {
    return service.removeExercise(u, id, exerciseId, version);
  }

  @PostMapping("/workouts/{id}/exercises/{exerciseId}/sets")
  public Object addSet(
      @AuthenticationPrincipal UUID u,
      @PathVariable UUID id,
      @PathVariable UUID exerciseId,
      @RequestBody Map<String, Object> b,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    var result = service.addSet(u, id, exerciseId, b, key);
    return ResponseEntity.created(
            URI.create(
                "/api/v1/workouts/"
                    + id
                    + "/exercises/"
                    + exerciseId
                    + "/sets/"
                    + ((Map<?, ?>) result.get("set")).get("id")))
        .body(result);
  }

  @PatchMapping("/workouts/{id}/exercises/{exerciseId}/sets/{setId}")
  public Object patchSet(
      @AuthenticationPrincipal UUID u,
      @PathVariable UUID id,
      @PathVariable UUID exerciseId,
      @PathVariable UUID setId,
      @RequestBody Map<String, Object> b) {
    return service.patchSet(u, id, exerciseId, setId, b);
  }

  @DeleteMapping("/workouts/{id}/exercises/{exerciseId}/sets/{setId}")
  public Object removeSet(
      @AuthenticationPrincipal UUID u,
      @PathVariable UUID id,
      @PathVariable UUID exerciseId,
      @PathVariable UUID setId,
      @RequestParam int version) {
    return service.removeSet(u, id, exerciseId, setId, version);
  }

  @PostMapping("/workouts/{id}/complete")
  public Object complete(
      @AuthenticationPrincipal UUID u, @PathVariable UUID id, @RequestBody Map<String, Object> b) {
    return service.terminal(u, id, b, true);
  }

  @PostMapping("/workouts/{id}/cancel")
  public Object cancel(
      @AuthenticationPrincipal UUID u, @PathVariable UUID id, @RequestBody Map<String, Object> b) {
    return service.terminal(u, id, b, false);
  }

  @GetMapping("/dashboard")
  public Object dashboard(@AuthenticationPrincipal UUID u, @RequestParam Map<String, String> q) {
    return service.dashboard(u, q);
  }
}
