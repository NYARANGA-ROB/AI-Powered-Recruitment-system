# SmartAPS

SmartAPS is an AI-powered recruiting platform for HR teams. It combines resume ingestion, structured candidate extraction, automated job-application workflows, interview management, analytics, and semantic search into a single Spring Boot + React system.

## Highlights

- AI resume parsing via Zhipu AI with PDF/DOC/DOCX support
- Distributed async pipeline with RabbitMQ, Redis, Redisson, and DLQ retry handling
- Candidate search with Milvus vector embeddings and RAG-like enrichment
- Job, application, interview, audit, and analytics modules
- JWT-based authentication, webhook delivery, and SSE dashboard updates
- Full-stack dashboard in React + TypeScript

## Stack

- Backend: Java 21, Spring Boot 3.2.5, Spring Security, MyBatis-Plus
- Database: MySQL 8, Redis 7, Milvus, MinIO
- Messaging: RabbitMQ
- AI: Spring AI + Zhipu AI
- Frontend: React 19, Vite, TypeScript, Ant Design

## Repository layout

- `src/main/java` — backend services, controllers, security, MQ, AI integrations
- `src/main/resources` — app configuration and Flyway database migrations
- `src/test/java` — backend tests
- `frontend` — React admin dashboard and API client
- `docs` — architecture and deployment references
- `docker-compose.yml` — local infrastructure for MySQL, Redis, RabbitMQ, and MinIO

## Quick start

### 1) Prerequisites

- JDK 21+
- Maven 3.9+
- Node.js 20+
- Docker Desktop

### 2) Start infrastructure

```bash
docker compose up -d
```

This starts:

- MySQL: `127.0.0.1:3307`
- Redis: `127.0.0.1:6379`
- RabbitMQ: `127.0.0.1:5672` and UI at `http://localhost:15672`
- MinIO: `127.0.0.1:9000` and Console at `http://localhost:9001`
- Milvus: `127.0.0.1:19530`

The Compose defaults are for local development only. For a fresh install, configure `.env` first; when reusing an existing data directory, keep the MySQL root password consistent with that database.

### 3) Configure environment variables

```bash
cp .env.example .env
```

On Windows PowerShell, use `Copy-Item .env.example .env`. Set unique local passwords and a JWT secret of at least 32 bytes. Configure the frontend environment file if you need to change the API URL:

```bash
cp frontend/.env.example frontend/.env
```

### 4) Run the backend

```bash
mvn -s settings.xml clean verify
mvn -s settings.xml spring-boot:run
```

Flyway applies versioned schema migrations automatically at startup. The REST API is under `/api/v1`; local Swagger UI is at:

```text
http://localhost:8080/api/v1/swagger-ui/index.html
```

The local seed accounts are `admin` / `password123` and `hr01` / `password123`. Change or remove these accounts before any shared or production deployment.

### 5) Run the frontend

```bash
cd frontend
npm ci
npm run dev
```

The frontend is available at:

```text
http://localhost:3000
```

## Core modules

- Auth and authorization
- Job management
- Resume upload and AI parsing
- Candidate data and semantic search
- Recruitment applications and matching
- Interview scheduling and review
- Webhooks and auditing
- Analytics and SSE dashboard updates

## Project documentation

See the docs in `docs/` for deeper technical details:

- `docs/SmartAPS-Design-Document.md` — system design and architecture
- `docs/SmartAPS-development-handbook-from-zero.md` — implementation guidance
- `docs/project-progress-summary.md` — project status and module progress
- `docs/deployment-guide.md` — production deployment instructions
- `docs/smartaps-migration-guide.md` — safe database and infrastructure rename steps
- `docs/frontend-api-reference.md` — frontend to backend API overview

## Suggested workflow

1. Start Docker infrastructure
2. Run the backend; Flyway initializes or upgrades the schema
3. Run the frontend
4. Validate login, resume processing, and recruitment workflows end-to-end

## Troubleshooting

- If Docker services do not start cleanly, check that ports 3307, 6379, 5672, 9000, 9001, 15672, and 19530 are free.
- If the backend fails to connect to infrastructure, verify `.env` values and check `docker compose ps`.
- If the frontend cannot reach the API, confirm that the backend is running on `http://localhost:8080` and that `VITE_API_BASE_URL` points to the correct host.
- If login or token refresh fails after a restart, use fresh credentials; changing `JWT_SECRET` intentionally invalidates existing tokens.
- The production profile requires real credentials, an explicit CORS origin list, and a 32-byte JWT secret. Swagger is disabled in production.

## Notes

- The backend uses environment variables for secrets and service credentials.
- The frontend reads `VITE_API_BASE_URL` from `.env` and proxies `/api/v1` to the backend during local development.
- Docker Compose is a local-development stack. Replace its archived MinIO fallback with a maintained object-storage service before production use.
- Keep candidate documents private; SmartAPS uses private MinIO objects and short-lived presigned download URLs.
