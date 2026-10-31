# SmartATS Production Deployment Guide

## Table of Contents

- [System Requirements](#system-requirements)
- [Quick Deployment (Docker Compose)](#quick-deployment-docker-compose)
- [Step-by-Step Deployment](#step-by-step-deployment)
- [Environment Variable Configuration](#environment-variable-configuration)
- [Database Initialization](#database-initialization)
- [Health Checks](#health-checks)
- [Common Issues](#common-issues)
- [Monitoring and Logging](#monitoring-and-logging)
- [Backup Strategy](#backup-strategy)
- [Nginx Reverse Proxy Configuration (Optional)](#nginx-reverse-proxy-configuration-optional)
- [Performance Tuning Recommendations](#performance-tuning-recommendations)

---

## System Requirements

| Component | Minimum Version | Recommended Version |
|------|----------|----------|
| Docker | 24.0+ | Latest stable version |
| Docker Compose | 2.20+ | Latest stable version |
| JDK (for source builds) | 21 | 21 LTS |
| Memory | 4 GB | 8 GB+ |
| Disk Space | 20 GB | 50 GB+ (including Milvus vector data) |

**Port List (ensure these ports are available):**

| Service | Port | Purpose |
|------|------|------|
| SmartATS App | 8080 | API service |
| MySQL | 3306 | Database |
| Redis | 6379 | Cache and distributed locks |
| RabbitMQ AMQP | 5672 | Message queue |
| RabbitMQ Management | 15672 | MQ management interface |
| MinIO API | 9000 | File storage |
| MinIO Console | 9001 | File management interface |
| Milvus | 19530 | Vector database |
| Milvus Metrics | 9091 | Milvus health checks |
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

Edit the `.env` file and configure all required environment variables (see [Environment Variable Configuration](#environment-variable-configuration) for details).

### 3. Build the JAR Package

```bash
# Requires JDK 21 + Maven 3.9+
mvn clean package -DskipTests
```

### 4. Start All Infrastructure Services

```bash
docker-compose up -d
```

### 5. Wait for Services to Become Ready

```bash
# Check the status of all services
docker-compose ps

# Wait for MySQL to become ready
docker-compose exec mysql mysqladmin ping -h localhost -uroot -proot123

# Wait for Milvus to become ready (first startup takes approximately 90 seconds)
curl -sf http://localhost:9091/healthz
```

### 6. Initialize the Database

```bash
# Execute the database schema scripts
mysql -h 127.0.0.1 -P 3307 -u smartats -psmartats123 smartats < docker/mysql/init/01-init-database.sql
mysql -h 127.0.0.1 -P 3307 -u smartats -psmartats123 smartats < src/main/resources/db/webhook_tables.sql
```

### 7. Start the Application

```bash
# Start using the production configuration
java -jar target/smartats-1.0.0.jar \
  --spring.profiles.active=prod \
  --server.port=8080
```

### 8. Verify the Deployment

```bash
# Health check
curl http://localhost:8080/api/v1/auth/test

# Expected response: 403 Unauthorized (normal; indicates that the service has started)
```

---

## Step-by-Step Deployment

If you need to deploy the components on different servers, follow the sequence below.

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

### Step 5: Milvus (Including etcd)

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
  -e ETCD_ENDPOINTS=<et
