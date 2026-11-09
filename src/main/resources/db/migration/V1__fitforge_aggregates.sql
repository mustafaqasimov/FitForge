-- Aggregate documents retain routine/workout snapshots atomically. UUID child IDs
-- are validated within their parent; account row locks serialize owner mutations.
CREATE TABLE resources (
 id uuid PRIMARY KEY,
 kind varchar(255) NOT NULL CHECK (kind IN ('user','refresh','exercise','routine','workout','idempotency')),
 owner uuid REFERENCES resources(id),
 data jsonb NOT NULL
);
CREATE UNIQUE INDEX users_email_normalized ON resources ((data->>'email')) WHERE kind='user';
CREATE UNIQUE INDEX refresh_token_hash ON resources ((data->>'hash')) WHERE kind='refresh';
CREATE UNIQUE INDEX one_active_workout ON resources(owner) WHERE kind='workout' AND data->>'status'='active';
CREATE UNIQUE INDEX idempotency_scope ON resources(owner,(data->>'route'),(data->>'key')) WHERE kind='idempotency';
CREATE INDEX resource_owner_kind ON resources(owner,kind);
CREATE INDEX routine_order ON resources(owner,(data->>'archivedAt'),((data->>'position')::integer)) WHERE kind='routine';
CREATE INDEX workout_history ON resources(owner,(data->>'startedAt')) WHERE kind='workout';
