# SmartAPS Production Deployment Guide

## Table of Contents

- [Environment Requirements](#environment-requirements)
- [Quick Deployment (Docker Compose)](#quick-deployment-docker-compose)
- [Step-by-Step Deployment](#step-by-step-deployment)
- [Environment Variable Configuration](#environment-variable-configuration)
- [Database Initialization](#database-initialization)
- [Health Checks](#health-checks)
- [FAQ](#faq)
- [Monitoring and Logging](#monitoring-and-logging)
- [Backup Strategy](#backup-strategy)

---

## Environment Requirements

| Component | Minimum Version | Recommended Version |
|------|----------|----------|
| Docker | 24.0+ | Latest stable |
| Docker Compose | 2.20+ | Latest stable |
| JDK (building from source) | 21 | 21 LTS |
| Memory | 4 GB | 8 GB+ |
| Disk | 20 GB | 50 GB+ (includes Milvus vector data) |

**Port list (make sure these are not in use):**

| Service | Port | Purpose |
|------|------|------|
| SmartAPS App | 8080 | API service |
| MySQL | 3306 | Database |
| Redis | 6379 | Caching & distributed locks |
| RabbitMQ AMQP | 5672 | Message queue |
| RabbitMQ Management | 15672 | MQ management UI |
| MinIO API | 9000 | File storage |
| MinIO Console | 9001 | File management UI |
| Milvus | 19530 | Vector database |
| Milvus Metrics | 9091 | Milvus health check |
| etcd | 2379 | Milvus metadata |

The local Compose file binds published service ports to loopback only. In production, keep databases, Redis, RabbitMQ, MinIO, Milvus, and etcd on private networks; expose only the application through a TLS-terminating reverse proxy.

---

## Quick Deployment (Docker Compose)

### 1. Clone the Project

```bash
git clone https://github.com/NYARANGA-ROB/AI-Powered-Recruitment-system.git
cd AI-Powered-Recruitment-system
```

### 2. Configure Environment Variables

```bash
cp .env.example .env
```

Set unique secrets in `.env`. On Windows PowerShell, copy the file with `Copy-Item .env.example .env`. Do not reuse the sample values in production.

### 3. Build the JAR

```bash
# Requires JDK 21 + Maven 3.9+
mvn -s settings.xml clean verify
```

### 4. Start All Infrastructure Services

```bash
docker compose up -d
```

### 5. Wait for Services to Be Ready

```bash
# Check the status of all services
docker compose ps

# Wait for MySQL to be ready
docker compose exec mysql mysqladmin ping -h localhost -uroot -p

# Wait for Milvus to be ready (first startup takes 90 seconds)
curl -sf http://localhost:9091/healthz
```

### 6. Start the Application

```bash
# Start with the production profile
java -jar target/smartaps-1.0.0.jar \
  --spring.profiles.active=prod \
  --server.port=8080
```

Flyway applies versioned schema migrations at startup. Existing SmartAPS installations are baselined at version `0`; the migrations preserve existing tables and align the resume schema. Back up the database before upgrading.

### 7. Verify the Deployment

```bash
# Liveness/readiness health check; details are not public
curl http://localhost:8080/api/v1/actuator/health
```

---

## Step-by-Step Deployment

If you need to deploy the components on different servers, follow this order:

### Step 1: MySQL

```bash
docker run -d \
  --name smartaps-mysql \
  -e MYSQL_ROOT_PASSWORD=<strong_root_password> \
  -e MYSQL_DATABASE=smartaps \
  -e MYSQL_USER=smartaps \
  -e MYSQL_PASSWORD=<strong_db_password> \
  -e TZ=Asia/Shanghai \
  -p 3306:3306 \
  -v /data/mysql:/var/lib/mysql \
  mysql:8.0 \
  --character-set-server=utf8mb4 \
  --collation-server=utf8mb4_unicode_ci
```

### Step 2: Redis

```bash
docker run -d \
  --name smartaps-redis \
  -p 6379:6379 \
  -v /data/redis:/data \
  redis:7.0-alpine \
  redis-server --appendonly yes --requirepass <strong_redis_password>
```

### Step 3: RabbitMQ

```bash
docker run -d \
  --name smartaps-rabbitmq \
  -e RABBITMQ_DEFAULT_USER=admin \
  -e RABBITMQ_DEFAULT_PASS=<strong_mq_password> \
  -e RABBITMQ_DEFAULT_VHOST=smartaps \
  -p 5672:5672 \
  -p 15672:15672 \
  -v /data/rabbitmq:/var/lib/rabbitmq \
  rabbitmq:3.12-management
```

### Step 4: Object Storage

Use a maintained S3-compatible object-storage service or a supported MinIO release. Keep the resume bucket private; SmartAPS uses server-side reads and short-lived presigned download URLs. The archived Bitnami MinIO image in the local Compose file is for development only.

### Step 5: Milvus (with etcd)

```bash
# etcd
docker run -d \
  --name smartaps-etcd \
  -e ETCD_AUTO_COMPACTION_MODE=revision \
  -e ETCD_AUTO_COMPACTION_RETENTION=1000 \
  -e ETCD_QUOTA_BACKEND_BYTES=4294967296 \
  -v /data/etcd:/etcd \
  quay.io/coreos/etcd:v3.5.16 \
  etcd -advertise-client-urls=http://127.0.0.1:2379 -listen-client-urls http://0.0.0.0:2379 --data-dir /etcd

# Milvus
docker run -d \
  --name smartaps-milvus \
  -e ETCD_ENDPOINTS=<etcd_host>:2379 \
  -e MINIO_ADDRESS=<minio_host>:9000 \
  -e MINIO_ACCESS_KEY_ID=admin \
  -e MINIO_SECRET_ACCESS_KEY=<strong_minio_password> \
  -p 19530:19530 \
  -p 9091:9091 \
  -v /data/milvus:/var/lib/milvus \
  milvusdb/milvus:v2.4.17 \
  milvus run standalone
```

### Step 6: SmartAPS Application

```bash
java -jar smartaps-1.0.0.jar \
  --spring.profiles.active=prod \
  --server.port=8080
```

---

## Environment Variable Configuration

### `.env` File Template

```env
# ==================== Database ====================
DB_HOST=localhost
DB_PORT=3306
DB_NAME=smartaps
DB_USERNAME=smartaps
DB_PASSWORD=<strong_db_password>
MYSQL_ROOT_PASSWORD=<strong_mysql_root_password>

# ==================== Redis ====================
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=<strong_redis_password>

# ==================== RabbitMQ ====================
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=admin
RABBITMQ_PASSWORD=<strong_mq_password>
RABBITMQ_VHOST=smartaps

# ==================== MinIO ====================
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=<storage_access_key>
MINIO_SECRET_KEY=<strong_minio_password>
MINIO_BUCKET_NAME=smartaps-resumes

# ==================== Milvus ====================
MILVUS_HOST=localhost
MILVUS_PORT=19530
MILVUS_DATABASE=default

# ==================== AI (Zhipu) ====================
ZHIPU_API_KEY=<your_zhipu_api_key>
ZHIPU_MODEL=glm-4-flash-250414
ZHIPU_EMBEDDING_MODEL=embedding-3
AI_DAILY_QUOTA=500

# ==================== JWT ====================
JWT_SECRET=<random_string_at_least_32_chars>

# ==================== Mail ====================
MAIL_HOST=smtp.qq.com
MAIL_PORT=587
MAIL_USERNAME=<your_email@qq.com>
MAIL_PASSWORD=<your_smtp_auth_code>

# ==================== CORS ====================
CORS_ALLOWED_ORIGINS=https://your-frontend-domain.com

SPRING_PROFILES_ACTIVE=prod
```

### Key Configuration Notes

| Variable | Required | Description |
|------|:----:|------|
| `DB_PASSWORD` | ✅ | MySQL password, never use the default value |
| `REDIS_PASSWORD` | ✅ | Redis password |
| `ZHIPU_API_KEY` | ✅ | Zhipu AI API key ([get it from the console](https://open.bigmodel.cn)) |
| `JWT_SECRET` | ✅ | Random JWT signing secret of at least 32 UTF-8 bytes |
| `MAIL_PASSWORD` | ✅ | SMTP authorization code (not the mailbox login password) |
| `CORS_ALLOWED_ORIGINS` | ✅ | Frontend domains, separated by commas if there are several |
> ⚠️ **Security notice**: the `.env` file is excluded via `.gitignore`; never commit it to version control.

---

## Database Initialization and Upgrades

Flyway creates or upgrades the schema when the application starts. Migrations live in `src/main/resources/db/migration`; do not run the old standalone SQL files or manually drop tables.

For an existing installation, use the [SmartAPS migration guide](./smartaps-migration-guide.md) to back up and move the database, RabbitMQ vhost, and object bucket before switching the application. Retain the old data until the new deployment passes verification.

### Database Table Overview

| Table | Description |
|------|------|
| `users` | User accounts (ADMIN/HR/INTERVIEWER) |
| `jobs` | Job postings |
| `resumes` | Resume files (MD5 deduplication) |
| `candidates` | Candidate structured data extracted by AI |
| `job_applications` | Job application records |
| `interview_records` | Interview records |
| `webhook_configs` | Webhook configuration |
| `webhook_logs` | Webhook delivery logs |
| `audit_logs` | Administrative audit events |

---

## Health Checks

### Application Layer

```bash
# Health details are hidden; actuator metrics require an administrator token.
curl http://localhost:8080/api/v1/actuator/health
# Expected: 200

# Swagger docs (available in the dev environment only, disabled in prod)
curl http://localhost:8080/api/v1/swagger-ui.html
```

### Infrastructure

```bash
# MySQL
docker exec smartaps-mysql mysqladmin ping -h localhost -uroot -p<password>

# Redis
docker exec smartaps-redis redis-cli -a <password> ping

# RabbitMQ
curl -u admin:<password> http://localhost:15672/api/healthchecks/node

# MinIO
curl -sf http://localhost:9000/minio/health/live

# Milvus
curl -sf http://localhost:9091/healthz
```

---

## FAQ

### 1. Milvus Starts Slowly

Milvus needs about 90 seconds on first startup, and `docker-compose.yml` already sets `start_period: 90s`. If its dependencies are not ready, Milvus may restart repeatedly. Make sure etcd and MinIO start first.

### 2. RabbitMQ VHost Does Not Exist

If you see the `NOT_ALLOWED - vhost smartaps not found` error:

```bash
# Create the VHost manually
docker exec smartaps-rabbitmq rabbitmqctl add_vhost smartaps
docker exec smartaps-rabbitmq rabbitmqctl set_permissions -p smartaps admin ".*" ".*" ".*"
```

### 3. MinIO Bucket Does Not Exist

The application creates the `smartaps-resumes` bucket automatically at startup. To create it manually:

```bash
docker exec smartaps-minio mc alias set local http://localhost:9000 admin <password>
docker exec smartaps-minio mc mb local/smartaps-resumes
```

### 4. AI Parsing Fails

Check the following:
- Whether `ZHIPU_API_KEY` is valid
- Whether the network can reach `https://open.bigmodel.cn`
- Whether the AI quota is exhausted (inspect the Redis key `rate:ai:{userId}:{date}`)

### 5. Email Sending Fails

- QQ Mail requires SMTP to be enabled and an authorization code obtained
- `MAIL_PASSWORD` is the authorization code, not the mailbox login password
- Make sure the server's outbound port 587 is not blocked by the firewall

---

## Monitoring and Logging

### Logging Configuration

Production logging:
- Output path: `logs/smartaps.log`
- Rolling policy: maximum 100MB per file, retained for 30 days
- Log levels: `root=WARN`, `com.smartaps=INFO`

```bash
# Follow the log in real time
tail -f logs/smartaps.log

# Search for errors
grep ERROR logs/smartaps.log
```

### RabbitMQ Monitoring

- Management UI: `http://<host>:15672`
- Username/password: as defined by your environment variables
- Watch: message backlog on `resume.parse.queue`, and the `resume.parse.dlq` dead-letter queue

### Redis Monitoring

```bash
# Check memory usage
redis-cli -a <password> INFO memory

# Check the number of keys
redis-cli -a <password> DBSIZE

# Check slow queries
redis-cli -a <password> SLOWLOG GET 10
```

---

## Backup Strategy

### MySQL Backup

```bash
# Daily full backup (recommended as a cron job)
mysqldump -h <DB_HOST> -P <DB_PORT> -u smartaps -p<DB_PASSWORD> \
  --single-transaction --routines --triggers smartaps \
  | gzip > /backup/smartaps_$(date +%Y%m%d_%H%M%S).sql.gz

# Keep the last 7 days
find /backup -name "smartaps_*.sql.gz" -mtime +7 -delete
```

### MinIO Backup

```bash
# Use mc mirror to sync to the backup storage
mc alias set local http://localhost:9000 admin <password>
mc alias set backup <backup_endpoint> <access_key> <secret_key>
mc mirror local/smartaps-resumes backup/smartaps-resumes
```

### Redis Backup

Redis has AOF persistence enabled (`appendonly yes`) and stores its data in the `./data/redis/` directory. Backing up that directory periodically is enough.

---

## Nginx Reverse Proxy Configuration (Optional)

```nginx
upstream smartaps {
    server 127.0.0.1:8080;
}

server {
    listen 443 ssl;
    server_name api.your-domain.com;

    ssl_certificate     /etc/nginx/ssl/cert.pem;
    ssl_certificate_key /etc/nginx/ssl/key.pem;

    client_max_body_size 50M;

    location /api/v1/ {
        proxy_pass http://smartaps;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # WebSocket (if needed)
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}

server {
    listen 80;
    server_name api.your-domain.com;
    return 301 https://$server_name$request_uri;
}
```

---

## Performance Tuning Recommendations

| Setting | Development | Production Recommendation |
|--------|----------|-------------|
| JVM heap | Default | `-Xms512m -Xmx2g` |
| HikariCP pool | 20 | 20-50 (adjust according to concurrency) |
| Redis pool | 20 | 20-50 |
| RabbitMQ prefetch | 1 | 3-10 |
| Daily AI quota | 100 | 500+ |
| File upload size | 10MB | 10MB |

```bash
# Example production JVM startup parameters
java -Xms512m -Xmx2g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -Dspring.profiles.active=prod \
  -jar smartaps-1.0.0.jar
```
