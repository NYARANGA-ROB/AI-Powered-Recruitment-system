# SmartATS — Intelligent Applicant Tracking System

<p align="center">
  <b>An AI-Powered Applicant Tracking System Built with Spring Boot 3, Zhipu AI, and Milvus</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-blue" alt="Java 21">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen" alt="Spring Boot 3.2.5">
  <img src="https://img.shields.io/badge/Spring%20AI-1.0.0--M4-orange" alt="Spring AI">
  <img src="https://img.shields.io/badge/Tests-190%20passed-success" alt="Tests">
  <img src="https://img.shields.io/badge/API%20Endpoints-40-blue" alt="API">
  <img src="https://img.shields.io/badge/License-MIT-yellow" alt="License">
</p>

---

## Project Overview

**SmartATS (Smart Applicant Tracking System)** is an intelligent recruitment management system designed for HR professionals. It supports resume uploads, automatic AI-powered information extraction, comprehensive recruitment workflow management (job applications and interview scheduling), RAG-based semantic candidate search, and webhook event notifications.

> 📌 This is a **learning project** designed to demonstrate practical experience with Spring Boot 3 full-stack development, AI integration, and distributed systems architecture.

### Core Features

- **AI-Powered Resume Parsing** — Upload PDF, DOC, and DOCX resumes. Zhipu AI automatically extracts structured information, including names, skills, and work experience.
- **Asynchronous Processing Pipeline** — RabbitMQ message queues, Redisson distributed locks, retry mechanisms, and dead-letter queues.
- **RAG Semantic Search** — Milvus vector database combined with the embedding-3 model to enable natural language candidate searches.
- **Complete Recruitment Workflow** — Job publishing → Resume parsing → Candidate management → Job applications → Interview scheduling → Feedback.
- **Webhook Notifications** — 12 event types, HMAC-SHA256 signatures, asynchronous delivery, and retry mechanisms.
- **Redis Caching System** — Cache-aside pattern, delayed double deletion, atomic counters, and trending rankings.

### Business Workflow

```text
Resume Upload → MD5 Deduplication → MinIO Storage → RabbitMQ Async Processing
     → AI Structured Data Extraction → Vector Embedding and Storage
     → Webhook Notifications

Semantic Search → Query Embedding → Milvus ANN Retrieval
     → Score Filtering → MySQL Data Enrichment → RAG Response
```

---

## Technology Stack

| Layer | Technology | Version |
|------|------|------|
| Core Framework | Spring Boot | 3.2.5 |
| Runtime | JDK | 21 |
| ORM | MyBatis-Plus | 3.5.10.1 |
| Database | MySQL | 8.0 |
| Caching & Distributed Locks | Redis + Redisson | 7.0 / 3.25.0 |
| Message Queue | RabbitMQ | 3.12 |
| File Storage | MinIO | 8.5.10 |
| Vector Database | Milvus | 2.4.17 |
| AI Integration | Spring AI + Zhipu AI | 1.0.0-M4 |
| Document Parsing | Apache POI + PDFBox | 5.2.5 / 2.0.29 |
| Authentication & Security | Spring Security + JWT | jjwt 0.11.5 |
| API Documentation | SpringDoc OpenAPI | 2.5.0 |
| Testing | JUnit 5 + Mockito + MockMvc | 190 test cases |

---

## System Architecture

```text
                       Spring Security + JWT Authentication Filter
                                      |
   +--------+--------+--------+-------+-------+--------+