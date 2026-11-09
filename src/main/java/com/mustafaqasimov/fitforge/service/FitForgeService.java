package com.mustafaqasimov.fitforge.service;

import com.mustafaqasimov.fitforge.entity.Resource;
import com.mustafaqasimov.fitforge.exception.ApiException;
import com.mustafaqasimov.fitforge.repository.ResourceRepository;
import com.mustafaqasimov.fitforge.security.SecurityConfig;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FitForgeService {
  final ResourceRepository repo;
  final SecurityConfig jwt;

  public FitForgeService(ResourceRepository repo, SecurityConfig jwt) {
    this.repo = repo;
    this.jwt = jwt;
  }

  public static Map<String, Object> map(Object... pairs) {
    var m = new LinkedHashMap<String, Object>();
    for (int i = 0; i < pairs.length; i += 2) m.put((String) pairs[i], pairs[i + 1]);
    return m;
  }

  static String now() {
    return Instant.now().toString();
  }

  static UUID uuid(Object x) {
    try {
      return UUID.fromString((String) x);
    } catch (Exception e) {
      throw new ApiException(400, "INVALID_UUID");
    }
  }

  static void fail(boolean condition, String code) {
    if (condition) throw new ApiException(422, code);
  }

  static void fields(Map<String, Object> b, String allowed) {
    var set = Set.of(allowed.split(","));
    if (!set.containsAll(b.keySet())) throw new ApiException(400, "UNKNOWN_FIELD");
  }

  static String text(Object v, int max) {
    fail(
        !(v instanceof String)
            || ((String) v).trim().isEmpty()
            || ((String) v).trim().length() > max,
        "VALIDATION_ERROR");
    return ((String) v).trim();
  }

  static int number(Object v, int min, int max) {
    fail(!(v instanceof Number), "VALIDATION_ERROR");
    var n = new BigDecimal(v.toString());
    fail(
        n.stripTrailingZeros().scale() > 0
            || n.compareTo(BigDecimal.valueOf(min)) < 0
            || n.compareTo(BigDecimal.valueOf(max)) > 0,
        "VALIDATION_ERROR");
    return n.intValue();
  }

  static void weight(Object v) {
    if (v == null) return;
    fail(!(v instanceof Number), "VALIDATION_ERROR");
    var n = new BigDecimal(v.toString());
    fail(
        n.scale() > 2 || n.signum() < 0 || n.compareTo(BigDecimal.valueOf(1000)) > 0,
        "VALIDATION_ERROR");
  }

  static String choice(Object v, String options) {
    fail(!(v instanceof String) || !List.of(options.split(",")).contains(v), "VALIDATION_ERROR");
    return (String) v;
  }

  static void bool(Object v) {
    fail(!(v instanceof Boolean), "VALIDATION_ERROR");
  }

  static void zone(Object v) {
    try {
      ZoneId.of((String) v);
    } catch (Exception e) {
      throw new ApiException(422, "VALIDATION_ERROR");
    }
  }

  static void notes(Object v) {
    fail(v != null && (!(v instanceof String) || ((String) v).length() > 2000), "VALIDATION_ERROR");
  }

  @SuppressWarnings("unchecked")
  static List<Map<String, Object>> rows(Map<String, Object> b, String key) {
    Object value = b.get(key);
    fail(!(value instanceof List<?>), "VALIDATION_ERROR");
    for (Object x : (List<?>) value) fail(!(x instanceof Map), "VALIDATION_ERROR");
    return (List<Map<String, Object>>) value;
  }

  Resource get(String kind, UUID id, UUID owner) {
    var r = repo.findById(id).orElseThrow(() -> new ApiException(404, "RESOURCE_NOT_FOUND"));
    if (!r.kind.equals(kind) || !Objects.equals(r.owner, owner))
      throw new ApiException(404, "RESOURCE_NOT_FOUND");
    return r;
  }

  Resource user(UUID id) {
    return get("user", id, null);
  }

  void lock(UUID id) {
    repo.lock(id).orElseThrow(() -> new ApiException(401, "INVALID_CREDENTIALS"));
  }

  Map<String, Object> view(Resource r) {
    var m = new LinkedHashMap<>(r.data);
    m.put("id", r.id.toString());
    return m;
  }

  Resource save(String kind, UUID owner, Map<String, Object> b) {
    return repo.saveAndFlush(new Resource(kind, owner, b));
  }

  void version(Resource r, Object v) {
    int n = number(v, 1, Integer.MAX_VALUE);
    if (n != ((Number) r.data.get("version")).intValue())
      throw new ApiException(409, "VERSION_CONFLICT", map("currentVersion", r.data.get("version")));
  }

  void bump(Resource r) {
    r.data.put("version", ((Number) r.data.get("version")).intValue() + 1);
    repo.save(r);
  }

  public Map<String, Object> profile(UUID id) {
    var m = view(user(id));
    m.remove("passwordHash");
    return m;
  }

  public Map<String, Object> profile(UUID id, Map<String, Object> b) {
    lock(id);
    fields(b, "displayName,timezone,weightUnit,weeklyGoal");
    if (b.containsKey("displayName")) b.put("displayName", text(b.get("displayName"), 60));
    if (b.containsKey("timezone")) zone(b.get("timezone"));
    if (b.containsKey("weightUnit")) choice(b.get("weightUnit"), "kg,lb");
    if (b.containsKey("weeklyGoal")) number(b.get("weeklyGoal"), 1, 14);
    var u = user(id);
    u.data.putAll(b);
    u.data.put("updatedAt", now());
    return profile(id);
  }

  static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public Map<String, Object> tokens(Resource u, String family, String device) {
    byte[] bytes = new byte[48];
    new SecureRandom().nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    save(
        "refresh",
        u.id,
        map(
            "hash",
            hash(token),
            "family",
            family,
            "expiresAt",
            Instant.now().plusSeconds(2592000).toString(),
            "revoked",
            false,
            "deviceName",
            device));
    return map(
        "user",
        profile(u.id),
        "accessToken",
        jwt.token(u.id),
        "refreshToken",
        token,
        "tokenType",
        "Bearer",
        "expiresIn",
        900);
  }

  @Transactional(noRollbackFor = ApiException.class)
  public Map<String, Object> refresh(Map<String, Object> b) {
    fields(b, "refreshToken");
    var r =
        repo.byHash(hash(text(b.get("refreshToken"), 512)))
            .orElseThrow(() -> new ApiException(401, "INVALID_CREDENTIALS"));
    lock(r.owner);
    r = repo.lock(r.id).orElseThrow();
    if (Boolean.TRUE.equals(r.data.get("revoked"))) {
      for (var s : repo.findByKindAndOwner("refresh", r.owner))
        if (s.data.get("family").equals(r.data.get("family"))) s.data.put("revoked", true);
      throw new ApiException(401, "INVALID_CREDENTIALS");
    }
    if (Instant.parse((String) r.data.get("expiresAt")).isBefore(Instant.now()))
      throw new ApiException(401, "TOKEN_EXPIRED");
    r.data.put("revoked", true);
    return tokens(user(r.owner), (String) r.data.get("family"), (String) r.data.get("deviceName"));
  }

  public void logout(Map<String, Object> b) {
    fields(b, "refreshToken");
    if (b.get("refreshToken") instanceof String t)
      repo.byHash(hash(t))
          .ifPresent(
              r -> {
                lock(r.owner);
                r.data.put("revoked", true);
              });
  }

  Resource exercise(UUID owner, UUID id) {
    var r = repo.findById(id).orElseThrow(() -> new ApiException(404, "RESOURCE_NOT_FOUND"));
    if (!r.kind.equals("exercise") || (r.owner != null && !r.owner.equals(owner)))
      throw new ApiException(404, "RESOURCE_NOT_FOUND");
    return r;
  }

  public Map<String, Object> exerciseView(UUID owner, UUID id) {
    return view(exercise(owner, id));
  }

  void exerciseFields(Map<String, Object> b) {
    if (b.containsKey("name")) b.put("name", text(b.get("name"), 100));
    if (b.containsKey("muscleGroup"))
      choice(b.get("muscleGroup"), "chest,back,shoulders,legs,arms,core,full_body");
    if (b.containsKey("equipment"))
      choice(b.get("equipment"), "barbell,dumbbell,machine,cable,bodyweight,other");
    if (b.containsKey("isArchived")) bool(b.get("isArchived"));
  }

  public Map<String, Object> createExercise(UUID owner, Map<String, Object> b) {
    fields(b, "name,muscleGroup,equipment,trackingType");
    for (String f : List.of("name", "muscleGroup", "equipment", "trackingType"))
      fail(!b.containsKey(f), "VALIDATION_ERROR");
    exerciseFields(b);
    choice(b.get("trackingType"), "weight_reps");
    b.put("isCustom", true);
    b.put("isArchived", false);
    return view(save("exercise", owner, b));
  }

  public Map<String, Object> patchExercise(UUID owner, UUID id, Map<String, Object> b) {
    lock(owner);
    fields(b, "name,muscleGroup,equipment,isArchived");
    exerciseFields(b);
    var r = get("exercise", id, owner);
    r.data.putAll(b);
    return view(r);
  }

  public Map<String, Object> listExercises(UUID owner, Map<String, String> q) {
    var all =
        repo.findByKind("exercise").stream()
            .filter(r -> r.owner == null || r.owner.equals(owner))
            .filter(r -> !Boolean.TRUE.equals(r.data.get("isArchived")))
            .map(this::view)
            .toList();
    if (q.containsKey("muscleGroup"))
      choice(q.get("muscleGroup"), "chest,back,shoulders,legs,arms,core,full_body");
    if (q.containsKey("equipment"))
      choice(q.get("equipment"), "barbell,dumbbell,machine,cable,bodyweight,other");
    if (q.containsKey("q")) fail(q.get("q").length() > 100, "VALIDATION_ERROR");
    return page(
        all.stream()
            .filter(
                r ->
                    (!q.containsKey("q")
                            || r.get("name")
                                .toString()
                                .toLowerCase(Locale.ROOT)
                                .contains(q.get("q").toLowerCase(Locale.ROOT)))
                        && matches(r, q, "muscleGroup")
                        && matches(r, q, "equipment"))
            .toList(),
        q,
        "name,asc",
        Set.of("name"));
  }

  static boolean matches(Map<String, Object> m, Map<String, String> q, String key) {
    return !q.containsKey(key) || Objects.equals(m.get(key), q.get(key));
  }

  Map<String, Object> page(
      List<Map<String, Object>> values,
      Map<String, String> q,
      String defaultSort,
      Set<String> allowed) {
    int p = Integer.parseInt(q.getOrDefault("page", "0")),
        size = Integer.parseInt(q.getOrDefault("size", "20"));
    fail(p < 0 || size < 1 || size > 100, "VALIDATION_ERROR");
    String[] sort = q.getOrDefault("sort", defaultSort).split(",");
    fail(
        sort.length != 2 || !allowed.contains(sort[0]) || !Set.of("asc", "desc").contains(sort[1]),
        "VALIDATION_ERROR");
    Comparator<Map<String, Object>> cmp =
        (a, b) -> {
          Object x = a.get(sort[0]), y = b.get(sort[0]);
          int c =
              x instanceof Number && y instanceof Number
                  ? new BigDecimal(x.toString()).compareTo(new BigDecimal(y.toString()))
                  : Objects.toString(x, "").compareTo(Objects.toString(y, ""));
          return c;
        };
    if (sort[1].equals("desc")) cmp = cmp.reversed();
    cmp = cmp.thenComparing(m -> m.get("id").toString());
    var sorted = values.stream().sorted(cmp).toList();
    long start = (long) p * size;
    return map(
        "items",
        sorted.subList(
            (int) Math.min(start, sorted.size()), (int) Math.min(start + size, sorted.size())),
        "page",
        p,
        "size",
        size,
        "totalElements",
        sorted.size(),
        "totalPages",
        (sorted.size() + size - 1) / size);
  }

  void routineFields(UUID owner, Map<String, Object> b, Resource previous) {
    if (b.containsKey("name")) b.put("name", text(b.get("name"), 100));
    if (b.containsKey("splitType")) choice(b.get("splitType"), "ppl,upper_lower,full_body,custom");
    if (b.containsKey("daysPerWeek")) number(b.get("daysPerWeek"), 1, 7);
    if (b.containsKey("estimatedMinutes")) number(b.get("estimatedMinutes"), 1, 300);
    if (b.containsKey("notes")) notes(b.get("notes"));
    if (b.containsKey("exercises")) {
      var list = rows(b, "exercises");
      fail(list.isEmpty() || list.size() > 50, "VALIDATION_ERROR");
      Set<String> ids = new HashSet<>();
      Set<String> old =
          previous == null
              ? Set.of()
              : rows(previous.data, "exercises").stream()
                  .map(m -> m.get("id").toString())
                  .collect(java.util.stream.Collectors.toSet());
      for (int i = 0; i < list.size(); i++) {
        var row = list.get(i);
        fields(row, "id,exerciseId,position,defaultSets,targetReps,defaultWeightKg,restSeconds");
        if (row.containsKey("id"))
          fail(
              !old.contains(row.get("id")) || !ids.add(row.get("id").toString()),
              "VALIDATION_ERROR");
        else row.put("id", UUID.randomUUID().toString());
        fail(number(row.get("position"), 0, 49) != i, "VALIDATION_ERROR");
        number(row.get("defaultSets"), 1, 20);
        number(row.get("targetReps"), 1, 1000);
        weight(row.get("defaultWeightKg"));
        row.putIfAbsent("defaultWeightKg", null);
        number(row.get("restSeconds"), 0, 900);
        var ex = exercise(owner, uuid(row.get("exerciseId")));
        if (Boolean.TRUE.equals(ex.data.get("isArchived")))
          throw new ApiException(409, "EXERCISE_ARCHIVED");
        row.put("name", ex.data.get("name"));
      }
    }
  }

  Map<String, Object> routineView(Resource r) {
    var m = view(r);
    var lines = rows(r.data, "exercises");
    m.put("exerciseCount", lines.size());
    m.put("exerciseNames", lines.stream().map(e -> e.get("name")).toList());
    m.put(
        "lastPerformedAt",
        repo.findByKindAndOwner("workout", r.owner).stream()
            .filter(
                w ->
                    "completed".equals(w.data.get("status"))
                        && r.id.toString().equals(w.data.get("routineId")))
            .map(w -> w.data.get("endedAt").toString())
            .max(String::compareTo)
            .orElse(null));
    return m;
  }

  public Map<String, Object> createRoutine(UUID owner, Map<String, Object> b) {
    lock(owner);
    fields(b, "name,splitType,daysPerWeek,estimatedMinutes,notes,exercises");
    for (String k : List.of("name", "splitType", "daysPerWeek", "estimatedMinutes", "exercises"))
      fail(!b.containsKey(k), "VALIDATION_ERROR");
    routineFields(owner, b, null);
    b.put("version", 1);
    b.put("createdAt", now());
    b.put(
        "position",
        (int)
            repo.findByKindAndOwner("routine", owner).stream()
                .filter(r -> r.data.get("archivedAt") == null)
                .count());
    b.put("archivedAt", null);
    b.putIfAbsent("notes", null);
    return routineView(save("routine", owner, b));
  }

  public Map<String, Object> routine(UUID owner, UUID id) {
    return routineView(get("routine", id, owner));
  }

  public Map<String, Object> patchRoutine(UUID owner, UUID id, Map<String, Object> b) {
    lock(owner);
    fields(b, "version,name,splitType,daysPerWeek,estimatedMinutes,notes,exercises");
    var r = get("routine", id, owner);
    version(r, b.get("version"));
    routineFields(owner, b, r);
    b.remove("version");
    r.data.putAll(b);
    bump(r);
    return routineView(r);
  }

  public void archive(UUID owner, UUID id) {
    lock(owner);
    var r = get("routine", id, owner);
    if (r.data.get("archivedAt") == null) {
      r.data.put("archivedAt", now());
      bump(r);
    }
  }

  public Map<String, Object> duplicate(UUID owner, UUID id, Map<String, Object> b) {
    fields(b, "name");
    var r = get("routine", id, owner);
    var m = new LinkedHashMap<>(r.data);
    m.keySet()
        .retainAll(
            Set.of("name", "splitType", "daysPerWeek", "estimatedMinutes", "notes", "exercises"));
    m.put("name", b.getOrDefault("name", r.data.get("name") + " surət"));
    var list = new ArrayList<Map<String, Object>>();
    for (var row : rows(r.data, "exercises")) {
      var copy = new LinkedHashMap<>(row);
      copy.remove("id");
      copy.remove("name");
      list.add(copy);
    }
    m.put("exercises", list);
    return createRoutine(owner, m);
  }

  public Map<String, Object> order(UUID owner, Map<String, Object> b) {
    lock(owner);
    fields(b, "routineIds");
    fail(!(b.get("routineIds") instanceof List<?>), "VALIDATION_ERROR");
    var ids = (List<?>) b.get("routineIds");
    var active =
        repo.findByKindAndOwner("routine", owner).stream()
            .filter(r -> r.data.get("archivedAt") == null)
            .toList();
    Set<UUID> seen = new HashSet<>();
    for (Object id : ids) {
      var r = get("routine", uuid(id), owner);
      fail(r.data.get("archivedAt") != null || !seen.add(r.id), "VALIDATION_ERROR");
    }
    fail(active.size() != ids.size(), "VALIDATION_ERROR");
    for (int i = 0; i < ids.size(); i++) {
      var r = get("routine", uuid(ids.get(i)), owner);
      r.data.put("position", i);
      bump(r);
    }
    return b;
  }

  public Map<String, Object> routines(UUID owner, Map<String, String> q) {
    if (q.containsKey("splitType")) choice(q.get("splitType"), "ppl,upper_lower,full_body,custom");
    String archive = q.getOrDefault("archived", "false");
    choice(archive, "true,false");
    var list =
        repo.findByKindAndOwner("routine", owner).stream()
            .filter(
                r ->
                    (r.data.get("archivedAt") != null) == Boolean.parseBoolean(archive)
                        && matches(r.data, q, "splitType"))
            .map(this::routineView)
            .map(
                m -> {
                  m.remove("exercises");
                  m.remove("notes");
                  return m;
                })
            .toList();
    return page(list, q, "position,asc", Set.of("position", "name", "createdAt"));
  }

  static Map<String, Object> blankSet(int position) {
    return map(
        "id",
        UUID.randomUUID().toString(),
        "position",
        position,
        "weightKg",
        null,
        "reps",
        null,
        "isCompleted",
        false,
        "completedAt",
        null);
  }

  Resource activeResource(UUID owner) {
    return repo.findByKindAndOwner("workout", owner).stream()
        .filter(r -> "active".equals(r.data.get("status")))
        .findFirst()
        .orElse(null);
  }

  public Map<String, Object> active(UUID owner) {
    var w = activeResource(owner);
    return map("workout", w == null ? null : view(w));
  }

  static Object normalized(Object value) {
    if (value instanceof Map<?, ?> m) {
      var sorted = new TreeMap<String, Object>();
      m.forEach((k, v) -> sorted.put(k.toString(), normalized(v)));
      return sorted;
    }
    if (value instanceof List<?> l) return l.stream().map(FitForgeService::normalized).toList();
    if (value instanceof Number n) return new BigDecimal(n.toString()).stripTrailingZeros();
    return value;
  }

  static String canonical(Object value) {
    return new tools.jackson.databind.ObjectMapper().writeValueAsString(normalized(value));
  }

  @SuppressWarnings("unchecked")
  Map<String, Object> replay(UUID owner, String route, String key, Map<String, Object> b) {
    uuid(key);
    for (var r : repo.findByKindAndOwner("idempotency", owner)) {
      if (route.equals(r.data.get("route")) && key.equals(r.data.get("key"))) {
        if (Instant.parse(r.data.get("expiresAt").toString()).isBefore(Instant.now())) {
          repo.delete(r);
          repo.flush();
          return null;
        }
        if (!hash(canonical(b)).equals(r.data.get("bodyHash")))
          throw new ApiException(409, "IDEMPOTENCY_KEY_REUSED");
        return (Map<String, Object>) r.data.get("response");
      }
    }
    return null;
  }

  void remember(
      UUID owner, String route, String key, Map<String, Object> b, Map<String, Object> response) {
    save(
        "idempotency",
        owner,
        map(
            "route",
            route,
            "key",
            key,
            "bodyHash",
            hash(canonical(b)),
            "response",
            deep(response),
            "expiresAt",
            Instant.now().plusSeconds(86400).toString()));
  }

  @SuppressWarnings("unchecked")
  static <T> T deep(T o) {
    if (o instanceof Map<?, ?> m) {
      var copy = new LinkedHashMap<String, Object>();
      m.forEach((k, v) -> copy.put(k.toString(), deep(v)));
      return (T) copy;
    }
    if (o instanceof List<?> l)
      return (T) new ArrayList<>(l.stream().map(FitForgeService::deep).toList());
    return o;
  }

  public Map<String, Object> start(UUID owner, Map<String, Object> b, String key) {
    lock(owner);
    fields(b, "routineId");
    var replay = replay(owner, "POST /workouts", key, b);
    if (replay != null) return replay;
    var active = activeResource(owner);
    if (active != null)
      throw new ApiException(
          409, "ACTIVE_WORKOUT_EXISTS", map("activeWorkoutId", active.id.toString()));
    Resource routine =
        b.get("routineId") == null ? null : get("routine", uuid(b.get("routineId")), owner);
    if (routine != null && routine.data.get("archivedAt") != null)
      throw new ApiException(409, "ROUTINE_ARCHIVED");
    var exercises = new ArrayList<Map<String, Object>>();
    if (routine != null)
      for (var row : rows(routine.data, "exercises")) {
        var sets = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < ((Number) row.get("defaultSets")).intValue(); i++)
          sets.add(blankSet(i));
        exercises.add(
            map(
                "id",
                UUID.randomUUID().toString(),
                "exerciseId",
                row.get("exerciseId"),
                "nameSnapshot",
                row.get("name"),
                "position",
                exercises.size(),
                "restSeconds",
                row.get("restSeconds"),
                "sets",
                sets));
      }
    var w =
        save(
            "workout",
            owner,
            map(
                "routineId",
                routine == null ? null : routine.id.toString(),
                "routineNameSnapshot",
                routine == null ? null : routine.data.get("name"),
                "status",
                "active",
                "version",
                1,
                "startedAt",
                now(),
                "endedAt",
                null,
                "notes",
                null,
                "exercises",
                exercises));
    var response = view(w);
    remember(owner, "POST /workouts", key, b, response);
    return response;
  }

  public Map<String, Object> workout(UUID owner, UUID id) {
    return view(get("workout", id, owner));
  }

  Resource mutable(UUID owner, UUID id, Object version) {
    lock(owner);
    var w = get("workout", id, owner);
    if (!"active".equals(w.data.get("status"))) throw new ApiException(409, "WORKOUT_NOT_ACTIVE");
    version(w, version);
    return w;
  }

  static Map<String, Object> child(List<Map<String, Object>> rows, UUID id) {
    return rows.stream()
        .filter(m -> id.toString().equals(m.get("id")))
        .findFirst()
        .orElseThrow(() -> new ApiException(404, "RESOURCE_NOT_FOUND"));
  }

  static void positions(List<Map<String, Object>> rows) {
    for (int i = 0; i < rows.size(); i++) rows.get(i).put("position", i);
  }

  public Map<String, Object> addExercise(UUID owner, UUID id, Map<String, Object> b) {
    fields(b, "version,exerciseId");
    var w = mutable(owner, id, b.get("version"));
    var e = exercise(owner, uuid(b.get("exerciseId")));
    if (Boolean.TRUE.equals(e.data.get("isArchived")))
      throw new ApiException(409, "EXERCISE_ARCHIVED");
    var list = rows(w.data, "exercises");
    fail(list.size() >= 50, "VALIDATION_ERROR");
    var row =
        map(
            "id",
            UUID.randomUUID().toString(),
            "exerciseId",
            e.id.toString(),
            "nameSnapshot",
            e.data.get("name"),
            "position",
            list.size(),
            "restSeconds",
            90,
            "sets",
            new ArrayList<>());
    list.add(row);
    bump(w);
    return map("exercise", row, "workoutVersion", w.data.get("version"));
  }

  public Map<String, Object> removeExercise(UUID owner, UUID id, UUID exerciseId, int version) {
    var w = mutable(owner, id, version);
    var list = rows(w.data, "exercises");
    var row = child(list, exerciseId);
    if (rows(row, "sets").stream().anyMatch(s -> Boolean.TRUE.equals(s.get("isCompleted"))))
      throw new ApiException(409, "EXERCISE_HAS_COMPLETED_SETS");
    list.remove(row);
    positions(list);
    bump(w);
    return map("workoutVersion", w.data.get("version"));
  }

  public Map<String, Object> addSet(
      UUID owner, UUID id, UUID exerciseId, Map<String, Object> b, String key) {
    lock(owner);
    fields(b, "version,weightKg,reps");
    String route = "POST /workouts/" + id + "/exercises/" + exerciseId + "/sets";
    var replay = replay(owner, route, key, b);
    if (replay != null) return replay;
    var w = mutable(owner, id, b.get("version"));
    var e = child(rows(w.data, "exercises"), exerciseId);
    var sets = rows(e, "sets");
    fail(sets.size() >= 50, "VALIDATION_ERROR");
    weight(b.get("weightKg"));
    if (b.get("reps") != null) number(b.get("reps"), 1, 1000);
    var s = blankSet(sets.size());
    s.put("weightKg", b.get("weightKg"));
    s.put("reps", b.get("reps"));
    sets.add(s);
    bump(w);
    var result = map("set", s, "workoutVersion", w.data.get("version"));
    remember(owner, route, key, b, result);
    return result;
  }

  public Map<String, Object> patchSet(
      UUID owner, UUID id, UUID exerciseId, UUID setId, Map<String, Object> b) {
    fields(b, "version,weightKg,reps,isCompleted");
    var w = mutable(owner, id, b.get("version"));
    var s = child(rows(child(rows(w.data, "exercises"), exerciseId), "sets"), setId);
    if (b.containsKey("weightKg")) weight(b.get("weightKg"));
    if (b.get("reps") != null) number(b.get("reps"), 1, 1000);
    if (b.containsKey("isCompleted")) bool(b.get("isCompleted"));
    boolean was = Boolean.TRUE.equals(s.get("isCompleted"));
    b.remove("version");
    s.putAll(b);
    if (Boolean.TRUE.equals(s.get("isCompleted"))) {
      fail(s.get("weightKg") == null || s.get("reps") == null, "VALIDATION_ERROR");
      if (!was) s.put("completedAt", now());
    } else s.put("completedAt", null);
    bump(w);
    return map("set", s, "workoutVersion", w.data.get("version"));
  }

  public Map<String, Object> removeSet(
      UUID owner, UUID id, UUID exerciseId, UUID setId, int version) {
    var w = mutable(owner, id, version);
    var sets = rows(child(rows(w.data, "exercises"), exerciseId), "sets");
    var s = child(sets, setId);
    if (Boolean.TRUE.equals(s.get("isCompleted")))
      throw new ApiException(409, "SET_ALREADY_COMPLETED");
    sets.remove(s);
    positions(sets);
    bump(w);
    return map("workoutVersion", w.data.get("version"));
  }

  public Map<String, Object> patchWorkout(UUID owner, UUID id, Map<String, Object> b) {
    fields(b, "version,notes");
    var w = mutable(owner, id, b.get("version"));
    if (b.containsKey("notes")) {
      notes(b.get("notes"));
      w.data.put("notes", b.get("notes"));
    }
    bump(w);
    return view(w);
  }

  Map<String, Object> summary(Resource w) {
    int count = 0, exercises = 0, reps = 0;
    BigDecimal volume = BigDecimal.ZERO;
    for (var e : rows(w.data, "exercises")) {
      boolean any = false;
      for (var s : rows(e, "sets"))
        if (Boolean.TRUE.equals(s.get("isCompleted"))) {
          count++;
          any = true;
          int n = ((Number) s.get("reps")).intValue();
          reps += n;
          volume =
              volume.add(
                  new BigDecimal(s.get("weightKg").toString()).multiply(BigDecimal.valueOf(n)));
        }
      if (any) exercises++;
    }
    long duration =
        w.data.get("endedAt") == null
            ? 0
            : Duration.between(
                    Instant.parse(w.data.get("startedAt").toString()),
                    Instant.parse(w.data.get("endedAt").toString()))
                .getSeconds();
    return map(
        "completedSetCount",
        count,
        "exerciseCount",
        exercises,
        "totalReps",
        reps,
        "totalVolumeKg",
        volume,
        "durationSeconds",
        duration);
  }

  public Map<String, Object> terminal(
      UUID owner, UUID id, Map<String, Object> b, boolean complete) {
    lock(owner);
    fields(b, complete ? "version,discardIncompleteSets" : "version");
    var w = get("workout", id, owner);
    String target = complete ? "completed" : "cancelled";
    if (target.equals(w.data.get("status"))) return view(w);
    if (!"active".equals(w.data.get("status")))
      throw new ApiException(409, "INVALID_WORKOUT_STATE");
    version(w, b.get("version"));
    if (complete) {
      bool(b.get("discardIncompleteSets"));
      var incomplete = new ArrayList<String>();
      int done = 0;
      for (var e : rows(w.data, "exercises"))
        for (var s : rows(e, "sets")) {
          if (Boolean.TRUE.equals(s.get("isCompleted"))) done++;
          else incomplete.add(s.get("id").toString());
        }
      if (done == 0) throw new ApiException(409, "NO_COMPLETED_SETS");
      if (!incomplete.isEmpty() && !Boolean.TRUE.equals(b.get("discardIncompleteSets")))
        throw new ApiException(409, "INCOMPLETE_SETS", map("incompleteSetIds", incomplete));
      for (var e : rows(w.data, "exercises")) {
        var sets = rows(e, "sets");
        sets.removeIf(s -> !Boolean.TRUE.equals(s.get("isCompleted")));
        positions(sets);
      }
    }
    w.data.put("status", target);
    w.data.put("endedAt", now());
    w.data.put("summary", summary(w));
    bump(w);
    return view(w);
  }

  Map<String, Object> workoutSummary(Resource w) {
    var m = view(w);
    m.remove("exercises");
    m.putAll(summary(w));
    return m;
  }

  public Map<String, Object> workouts(UUID owner, Map<String, String> q) {
    String status = q.getOrDefault("status", "completed");
    choice(status, "completed,cancelled,active");
    Instant from = q.containsKey("from") ? Instant.parse(q.get("from")) : null,
        to = q.containsKey("to") ? Instant.parse(q.get("to")) : null;
    if (from != null && to != null)
      fail(
          !from.isBefore(to) || Duration.between(from, to).compareTo(Duration.ofDays(366)) > 0,
          "VALIDATION_ERROR");
    if (q.containsKey("routineId")) uuid(q.get("routineId"));
    return page(
        repo.findByKindAndOwner("workout", owner).stream()
            .filter(w -> status.equals(w.data.get("status")) && matches(w.data, q, "routineId"))
            .filter(
                w -> {
                  var t = Instant.parse(w.data.get("startedAt").toString());
                  return (from == null || !t.isBefore(from)) && (to == null || t.isBefore(to));
                })
            .map(this::workoutSummary)
            .toList(),
        q,
        "startedAt,desc",
        Set.of("startedAt"));
  }

  public Map<String, Object> dashboard(UUID owner, Map<String, String> q) {
    var u = user(owner);
    ZoneId zone = ZoneId.of(u.data.get("timezone").toString());
    fail(q.containsKey("from") != q.containsKey("to"), "VALIDATION_ERROR");
    LocalDate from =
        q.containsKey("from")
            ? LocalDate.parse(q.get("from"))
            : LocalDate.now(zone)
                .with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    LocalDate to = q.containsKey("to") ? LocalDate.parse(q.get("to")) : from.plusDays(7);
    long days = java.time.temporal.ChronoUnit.DAYS.between(from, to);
    fail(days < 1 || days > 366, "VALIDATION_ERROR");
    Instant a = from.atStartOfDay(zone).toInstant(), b = to.atStartOfDay(zone).toInstant();
    var completed =
        repo.findByKindAndOwner("workout", owner).stream()
            .filter(w -> "completed".equals(w.data.get("status")))
            .sorted(
                Comparator.comparing((Resource w) -> w.data.get("endedAt").toString()).reversed())
            .toList();
    int count = 0, sets = 0;
    long duration = 0;
    BigDecimal volume = BigDecimal.ZERO;
    for (var w : completed) {
      Instant t = Instant.parse(w.data.get("endedAt").toString());
      if (!t.isBefore(a) && t.isBefore(b)) {
        count++;
        var s = summary(w);
        sets += ((Number) s.get("completedSetCount")).intValue();
        duration += ((Number) s.get("durationSeconds")).longValue();
        volume = volume.add((BigDecimal) s.get("totalVolumeKg"));
      }
    }
    var active = activeResource(owner);
    int goal = ((Number) u.data.get("weeklyGoal")).intValue();
    return map(
        "period",
        map("from", from.toString(), "to", to.toString(), "timezone", zone.toString()),
        "completedWorkouts",
        count,
        "completedSets",
        sets,
        "totalVolumeKg",
        volume,
        "totalDurationSeconds",
        duration,
        "weeklyGoal",
        goal,
        "goalProgress",
        days == 7 ? Math.min((double) count / goal, 1) : null,
        "recentWorkouts",
        completed.stream().limit(5).map(this::workoutSummary).toList(),
        "activeWorkoutId",
        active == null ? null : active.id.toString());
  }
}
