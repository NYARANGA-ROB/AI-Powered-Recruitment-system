

# SmartATS - Intelligent Recruitment and Resume Analysis System

## 📌 Project Overview

**SmartATS** is an intelligent recruitment management system designed for HR professionals. The system supports core features such as bulk resume uploads, AI-powered automatic resume parsing and structured information extraction, and RAG-based semantic candidate search.

### Technology Stack

| Component | Technology | Purpose |
|------|----------|------|
| Core Framework | Spring Boot 3.x | Application framework |
| ORM | MyBatis-Plus | Database operations |
| Database | MySQL 8.0 | Business data storage |
| Caching / Rate Limiting / Locking | Redis + Redisson | High-performance caching, distributed locks, and rate limiting |
| Message Queue | RabbitMQ | Asynchronous task decoupling |
| AI Integration | Spring AI | LLM calls, Embeddings, and RAG |
| Vector Database | Milvus / PgVector | Resume vector storage and retrieval |

---

## 🏗️ System Architecture Diagram

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                              SmartATS Architecture                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   ┌─────────┐     ┌─────────────────────────────────────────────────────┐  │
│   │   HR    │────▶│                   API Gateway                       │  │
│   │  Client │     │  (Rate Limiting via Redis + Token Bucket/Lua)       │  │
│   └─────────┘     └─────────────────────────────────────────────────────┘  │
│                                      │                                      │
│                                      ▼                                      │
│   ┌─────────────────────────────────────────────────────────────────────┐  │
│   │                        Spring Boot Application                       │  │
│   │  ┌───────────────┐  ┌───────────────┐  ┌───────────────────────┐   │  │
│   │  │ Resume Module│  │  Job Module   │  │   Search Module       │   │  │
│   │  │  - Upload     │  │  - CRUD       │  │   - AI Semantic Search│   │  │
│   │  │  - Status     │  │  - Cache      │  │   - Hybrid Query      │   │  │
│   │  └───────┬───────┘  └───────────────┘  └───────────────────────┘   │  │
│   │          │                                         │                │  │
│   └──────────┼─────────────────────────────────────────┼────────────────┘  │
│              │                                         │                    │
│              ▼                                         ▼                    │
│   ┌──────────────────┐                    ┌──────────────────────────┐     │
│   │    RabbitMQ      │                    │      Spring AI           │     │
│   │  ┌────────────┐  │                    │  ┌────────────────────┐  │     │
│   │  │ resume.queue│ │                    │  │ Embedding Model    │  │     │
│   │  └─────┬
