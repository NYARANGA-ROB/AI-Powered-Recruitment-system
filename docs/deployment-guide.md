# SmartATS Production Deployment Guide

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
| SmartATS App | 8080 | API service |
| MySQL | 3306 | Database |
| Redis | 6379 | Caching & distributed locks |
| RabbitMQ AMQP | 5672 | Message queue |
| RabbitMQ Management | 15672 | MQ management UI |
| MinIO API | 9000 | File storage |
| MinIO Console | 9001 | File management UI |
| Milvus | 19530 | Vector database |
| Milvus Metrics | 9091 | Milvus health check |
| etcd | 2379 | Milvus metadata |

---

## Quick Deployment (Docker Compose)

### 1. Clone the Project

```bash
git clone https://github.com/NissonCX/SmartATS.git
cd SmartATS
```

### 2. Configure Environment Variables

```bash
cp .env.example .env
vim .env
```

Edit the `.env` file and set every required environment variable (see [Environment Variable Configuration](#environment-variable-configuration)).

### 3. Build the JAR

```bash
# Requires JDK 21 + Maven 3.9+
mvn clean package -DskipTests
```

### 4. Start All Infrastructure Services

```bash
docker-compose up -d
```

### 5. Wait for Services to Be Ready

```bash
# Check the status of all services
docker-compose ps

# Wait for MySQL to be ready
docker-compose exec mysql mysqladmin ping -h localhost -uroot -proot123

# Wait for Milvus to be ready (first startup takes 90 seconds)
curl -sf http://localhost:9091/healthz
```

### 6. Initialize the Database

```bash
# Run the table creation scripts
mysql -h 127.0.0.1 -P 3307 -u smartats -psmartats123 smartats < docker/mysql/init/01-init-database.sql
mysql -h 127.0.0.1 -P 3307 -u smartats -psmartats123 smartats < src/main/resources/db/webhook_tables.sql
```

### 7. Start the Application

```bash
# Start with the production profile
java -jar target/smartats-1.0.0.jar \
  --spring.profiles.active=prod \
  --server.port=8080
```

### 8. Verify the Deployment

```bash
# Health check
curl http://localhost:8080/api/v1/auth/test

# Expected response: unauthenticated 403 (normal, it means the service is up)
```

---

## Step-by-Step Deployment

If you need to deploy the components on different servers, follow this order:

### Step 1: MySQL

```bash
docker run -d \
  --name smartats-mysql \
  -e MYSQL_ROOT_PASSWORD=<strong_root_password> \
  -e MYSQL_DATABASE=smartats \
  -e MYSQL_USER=smartats \
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
  --name smartats-redis \
  -p 6379:6379 \
  -v /data/redis:/data \
  redis:7.0-alpine \
  redis-server --appendonly yes --requirepass <strong_redis_password>
```

### Step 3: RabbitMQ

```bash
docker run -d \
  --name smartats-rabbitmq \
  -e RABBITMQ_DEFAULT_USER=admin \
  -e RABBITMQ_DEFAULT_PASS=<strong_mq_password> \
  -e RABBITMQ_DEFAULT_VHOST=smartats \
  -p 5672:5672 \
  -p 15672:15672 \
  -v /data/rabbitmq:/var/lib/rabbitmq \
  rabbitmq:3.12-management
```

### Step 4: MinIO

```bash
docker run -d \
  --name smartats-minio \
  -e MINIO_ROOT_USER=admin \
  -e MINIO_ROOT_PASSWORD=<strong_minio_password> \
  -p 9000:9000 \
  -p 9001:9001 \
  -v /data/minio:/data \
  minio/minio:latest \
  server /data --console-address ":9001"
```

### Step 5: Milvus (with etcd)

```bash
# etcd
docker run -d \
  --name smartats-etcd \
  -e ETCD_AUTO_COMPACTION_MODE=revision \
  -e ETCD_AUTO_COMPACTION_RETENTION=1000 \
  -e ETCD_QUOTA_BACKEND_BYTES=4294967296 \
  -v /data/etcd:/etcd \
  quay.io/coreos/etcd:v3.5.16 \
  etcd -advertise-client-urls=http://127.0.0.1:2379 -listen-client-urls http://0.0.0.0:2379 --data-dir /etcd

# Milvus
docker run -d \
  --name smartats-milvus \
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

### Step 6: SmartATS Application

```bash
java -jar smartats-1.0.0.jar \
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
DB_NAME=smartats
DB_USERNAME=smartats
DB_PASSWORD=<strong_db_password>

# ==================== Redis ====================
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=<strong_redis_password>

# ==================== RabbitMQ ====================
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=admin
RABBITMQ_PASSWORD=<strong_mq_password>
RABBITMQ_VHOST=smartats

# ==================== MinIO ====================
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=admin
MINIO_SECRET_KEY=<strong_minio_password>

# ==================== Milvus ====================
MILVUS_HOST=localhost
MILVUS_PORT=19530
MILVUS_DATABASE=default

# ==================== AI (Zhipu) ====================
ZHIPU_API_KEY=<your_zhipu_api_key>
ZHIPU_MODEL=glm-4-flash-250414
AI_EMBEDDING_MODEL=embedding-3
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

# ==================== Server ====================
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=prod
```

### Key Configuration Notes

| Variable | Required | Description |
|------|:----:|------|
| `DB_PASSWORD` | ✅ | MySQL password, never use the default value |
| `REDIS_PASSWORD` | ✅ | Redis password |
| `ZHIPU_API_KEY` | ✅ | Zhipu AI API key ([get it from the console](https://open.bigmodel.cn)) |
| `JWT_SECRET` | ✅ | JWT signing secret, a random string of ≥32 characters |
| `MAIL_PASSWORD` | ✅ | SMTP authorization code (not the mailbox login password) |
| `CORS_ALLOWED_ORIGINS` | ✅ | Frontend domains, separated by commas if there are several |
| `AI_DAILY_QUOTA` | ❌ | Daily AI call limit, defaults to 500 |

> ⚠️ **Security notice**: the `.env` file is excluded via `.gitignore`; never commit it to version control.

---

## Database Initialization

### First Deployment

The table creation script lives at `docker/mysql/init/01-init-database.sql`, and Docker Compose runs it automatically on first startup.

To initialize manually:

```bash
mysql -h <DB_HOST> -P <DB_PORT> -u smartats -p<DB_PASSWORD> smartats < docker/mysql/init/01-init-database.sql
mysql -h <DB_HOST> -P <DB_PORT> -u smartats -p<DB_PASSWORD> smartats < src/main/resources/db/webhook_tables.sql
```

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

---

## Health Checks

### Application Layer

```bash
# Basic connectivity (an authenticated endpoint returning 403 means the service is healthy)
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/v1/jobs
# Expected: 200

# Swagger docs (available in the dev environment only, disabled in prod)
curl http://localhost:8080/api/v1/swagger-ui.html
```

### Infrastructure

```bash
# MySQL
docker exec smartats-mysql mysqladmin ping -h localhost -uroot -p<password>

# Redis
docker exec smartats-redis redis-cli -a <password> ping

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

If you see the `NOT_ALLOWED - vhost smartats not found` error:

```bash
# Create the VHost manually
docker exec smartats-rabbitmq rabbitmqctl add_vhost smartats
docker exec smartats-rabbitmq rabbitmqctl set_permissions -p smartats admin ".*" ".*" ".*"
```

### 3. MinIO Bucket Does Not Exist

The application creates the `smartats-resumes` bucket automatically at startup. To create it manually:

```bash
docker exec smartats-minio mc alias set local http://localhost:9000 admin <password>
docker exec smartats-minio mc mb local/smartats-resumes
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
- Output path: `logs/smartats.log`
- Rolling policy: maximum 100MB per file, retained for 30 days
- Log levels: `root=WARN`, `com.smartats=INFO`

```bash
# Follow the log in real time
tail -f logs/smartats.log

# Search for errors
grep ERROR logs/smartats.log
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
mysqldump -h <DB_HOST> -P <DB_PORT> -u smartats -p<DB_PASSWORD> \
  --single-transaction --routines --triggers smartats \
  | gzip > /backup/smartats_$(date +%Y%m%d_%H%M%S).sql.gz

# Keep the last 7 days
find /backup -name "smartats_*.sql.gz" -mtime +7 -delete
```

### MinIO Backup

```bash
# Use mc mirror to sync to the backup storage
mc alias set local http://localhost:9000 admin <password>
mc alias set backup <backup_endpoint> <access_key> <secret_key>
mc mirror local/smartats-resumes backup/smartats-resumes
```

### Redis Backup

Redis has AOF persistence enabled (`appendonly yes`) and stores its data in the `./data/redis/` directory. Backing up that directory periodically is enough.

---

## Nginx Reverse Proxy Configuration (Optional)

```nginx
upstream smartats {
    server 127.0.0.1:8080;
}

server {
    listen 443 ssl;
    server_name api.your-domain.com;

    ssl_certificate     /etc/nginx/ssl/cert.pem;
    ssl_certificate_key /etc/nginx/ssl/key.pem;

    client_max_body_size 50M;

    location /api/v1/ {
        proxy_pass http://smartats;
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
  -jar smartats-1.0.0.jar
```
