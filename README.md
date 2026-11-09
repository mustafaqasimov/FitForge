# FitForge Backend

Java 21 / Spring Boot 4 / PostgreSQL 16 backend implementing the FitForge MVP API at `/api/v1`. Backend only; no frontend files are included or modified. Controller, service, repository and security boundaries follow the supplied WatchList reference. The API contract comes from the supplied Azerbaijani FitForge document.

## Run

Install a full JDK 21 and Docker with Compose, then:

```sh
export DB_USERNAME=fitness DB_PASSWORD=fitness
# Only local development credentials; use environment-managed secrets in production.
docker compose up -d postgres
export SPRING_DOCKER_COMPOSE_ENABLED=false
bash mvnw verify
bash mvnw spring-boot:run
```

Defaults use `jdbc:postgresql://localhost:5432/fitness_dev`. Flyway creates the schema automatically; Hibernate uses validation, never production schema creation. `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, and `CORS_ALLOWED_ORIGINS` configure deployment. Set `SPRING_PROFILES_ACTIVE=prod` in production; all these settings are required. Use HTTPS at the deployment ingress. No default demo users or fabricated statistics are seeded.

In the prepared cloud environment, `bash /workspace/fitforge-setup/mvn.sh verify` uses the installed JDK and proxy settings. This helper lives outside the repository and is not a deployment requirement.

## API

See [OpenAPI 3.1](docs/openapi.json). The API supports registration/login, rotating refresh sessions, profile updates, custom exercise catalog, routine CRUD/archive/duplicate/order, workout snapshots, nested exercises/sets, completion/cancellation, history and dashboard. Optional future features (password recovery, sharing, schedules, push, offline sync) are not implemented.

POST workout and POST set require a UUID `Idempotency-Key`. Replays last 24 hours and retain their original response. Every workout mutation must supply the parent workout version. Complete/cancel repeats return the existing terminal resource. Resource ownership comes exclusively from the verified JWT subject.

## Persistence and concurrency

The implementation uses PostgreSQL JSONB **aggregates**, rather than one relational table per nested item in the document's conceptual model. `resources` holds UUID-keyed users, exercises, routines, workout snapshots, refresh sessions and idempotency records. Routine/workout children are embedded with independent UUIDs. Services validate child membership, decimal precision, ranges, ordering and state transitions before saving. API responses are projected maps; JPA entities and credential fields are not serialized by controllers.

A pessimistic account-row lock serializes mutations for one user across server instances. Database unique indexes also enforce normalized email, refresh-token hashes, idempotency scope and at most one active workout. Nested changes and replay records commit in the same transaction. This favors simple atomic snapshots at MVP scale; high-volume deployments should move list filtering/pagination to dedicated indexed SQL projections and introduce cleanup jobs for expired auth/idempotency records.

Passwords are SHA-256 prehashed before BCrypt cost 12 so the 128-character API limit does not encounter BCrypt's 72-byte truncation limit. Refresh tokens are random 384-bit opaque values, stored only as SHA-256 hashes. Access JWTs use fixed HS256, issuer, audience, expiry, issuance time and token ID. Access lifetime is 15 minutes; refresh lifetime is 30 days. Logout revokes refresh credentials; an issued access token remains valid until expiry.

Auth rate limits are stored in PostgreSQL: 10 register/login requests per account and 100 per direct peer IP per 15 minutes. This conservative limit counts successful attempts as well. Deploy behind a trusted ingress with its own IP rate limits; untrusted forwarded headers are not used. CORS permits configured web/Capacitor origins.

## Validation

```sh
bash mvnw verify
# Start the application on port 8081 against an isolated test database, then:
python3 tests/api_smoke.py
```

The HTTP suite exercises real authentication and PostgreSQL persistence, ownership, snapshot preservation, stale versions, replay and concurrent starts, set completion, terminal repeats, dashboard aggregation and refresh reuse. It creates uniquely named test accounts; use an isolated database. CI runs migrations, the Maven test and HTTP contracts against PostgreSQL 16.

Production operations (TLS ingress, backups/restore drills, monitoring, secret rotation and retention policy) belong to deployment infrastructure. The global exercise catalog can be populated by a separately authorized administrative import; no public endpoint grants global catalog writes.
