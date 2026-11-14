# SmartAPS migration guide

This guide covers a fresh install and an upgrade from the previous SmartATS identifiers. The upgrade keeps source data available for rollback; do not remove old databases, buckets, or queues until the new deployment is verified.

## Fresh install

1. Copy `.env.example` to `.env` and set unique values.
2. Start the local infrastructure with `docker compose up -d`.
3. Start the backend. Flyway creates the schema and applies migrations from `src/main/resources/db/migration`.
4. Start the frontend and verify `GET /api/v1/actuator/health`.

## Existing installation

### 1. Back up and stop writers

Back up MySQL, Redis, RabbitMQ definitions/messages, MinIO objects, and the application configuration. Stop the backend before copying data. Never use `docker compose down -v`; the Compose stack uses persistent bind-mounted data directories.

### 2. Copy the MySQL schema and data

On the existing MySQL instance, create the new schema and application user using the deployment's chosen password:

```sql
CREATE DATABASE smartaps CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'smartaps'@'%' IDENTIFIED BY '<strong_db_password>';
GRANT ALL PRIVILEGES ON smartaps.* TO 'smartaps'@'%';
```

Copy all tables and rows from the legacy `smartats` schema into `smartaps`:

```bash
mysqldump --single-transaction --set-gtid-purged=OFF smartats | mysql smartaps
```

Configure `DB_NAME=smartaps`, `DB_USERNAME=smartaps`, and `DB_PASSWORD` for the new application. On first startup, Flyway baselines an existing schema at version `0`, applies the versioned migrations, and aligns the resume table. Keep the original schema until the new app passes verification.

### 3. Migrate RabbitMQ

Create the `smartaps` virtual host and grant the application account access:

```bash
rabbitmqctl add_vhost smartaps
rabbitmqctl set_permissions -p smartaps <app_user> ".*" ".*" ".*"
```

The application declares the `smartaps.exchange` and `smartaps.dlx` exchanges and its queues on startup. Queue messages are not copied between virtual hosts. Drain the legacy resume queues before switching, or use RabbitMQ Shovel with an explicit, tested plan.

### 4. Migrate private object storage

Create the private `smartaps-resumes` bucket and copy objects from the old bucket while preserving object keys. For MinIO Client (`mc`), configure aliases for both stores, then run:

```bash
mc mb smartaps/smartaps-resumes
mc mirror --preserve legacy/smartats-resumes smartaps/smartaps-resumes
```

Verify object counts and sample downloads before switching. SmartAPS now reads objects server-side and returns short-lived presigned download URLs; do not make the bucket public.

### 5. Switch, verify, and retain rollback data

Update the application environment, including `RABBITMQ_VHOST=smartaps`, `MINIO_BUCKET_NAME=smartaps-resumes`, a strong `JWT_SECRET`, and explicit `CORS_ALLOWED_ORIGINS`. The API path remains `/api/v1`. Rotating the JWT secret invalidates existing sessions, so users must sign in again.

Verify login, resume downloads, queue processing, candidate search, and recruitment workflows. Monitor `/api/v1/actuator/health` and the authenticated `smartaps.resume.parse.outcomes` metric. Only retire the legacy database, broker vhost, and storage bucket after the agreed rollback period.

## Local migration performed for this workspace

The local MySQL data was copied into `smartaps` without removing the old `smartats` schema. The two seeded accounts were preserved. The new RabbitMQ vhost was created; its legacy queues were empty at migration time. The old schema and vhost remain available for rollback.
