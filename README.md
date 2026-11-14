# SmartATS

SmartATS is an AI-powered recruiting platform for HR teams. It combines resume ingestion, structured candidate extraction, automated job-application workflows, interview management, analytics, and semantic search into a single Spring Boot + React system.

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
- `src/main/resources` — app config and SQL resources
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
docker-compose up -d
```

This starts:

- MySQL: `localhost:3307`
- Redis: `localhost:6379`
- RabbitMQ: `localhost:5672` and UI at `http://localhost:15672`
- MinIO: `localhost:9000` and Console at `http://localhost:9001`

### 3) Configure environment variables

Copy the example file and update the values for your environment:

```bash
cp .env.example .env
```

The project also includes a frontend example file:

```bash
cp frontend/.env.example frontend/.env
```

### 4) Run the backend

```bash
mvn clean install
mvn spring-boot:run
```

The backend exposes the REST API under `/api/v1` and Swagger UI at:

```text
http://localhost:8080/api/v1/swagger-ui.html
```

### 5) Run the frontend

```bash
cd frontend
npm install
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

- `docs/SmartATS-Design-Document.md` — system design and architecture
- `docs/SmartATS-development-handbook-from-zero.md` — implementation guidance
- `docs/project-progress-summary.md` — project status and module progress
- `docs/deployment-guide.md` — production deployment instructions
- `docs/frontend-api-reference.md` — frontend to backend API overview

## Suggested workflow

1. Start Docker infrastructure
2. Apply database schema and seed scripts if needed
3. Run backend with environment variables
4. Run frontend with local proxy configuration
5. Use Swagger and the dashboard to validate flows end-to-end

## Troubleshooting

- If Docker services do not start cleanly, check that port 3307, 6379, 5672, 9000, and 15672 are free.
- If the backend fails to connect to MySQL or Redis, verify your `.env` values and ensure the containers are running with `docker-compose ps`.
- If the frontend cannot reach the API, confirm that the backend is running on `http://localhost:8080` and that `VITE_API_BASE_URL` points to the correct host.
- If login or token refresh fails, verify that `JWT_SECRET` is set and consistent between app restarts.

## Notes

- The backend uses environment variables for secrets and service credentials.
- The frontend reads `VITE_API_BASE_URL` from `.env` and proxies `/api/v1` to the backend during local development.
- The project is designed for development and learning-use deployment; production hardening should include secrets management, stricter network policies, and CI/CD checks.
