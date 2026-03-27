# CollabDocs — Collaborative Document Editing API

![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen?style=flat-square&logo=springboot)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?style=flat-square&logo=postgresql)
![Redis](https://img.shields.io/badge/Redis-7-red?style=flat-square&logo=redis)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker)
![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)

A production-ready backend API that replicates the core functionality of Google Docs. Built with Spring Boot 3, it supports real-time collaborative editing via WebSocket, role-based access control, full version history, presence tracking, comment threads, and full-text search.

> **Live Demo:** [https://collabdocs.onrender.com](https://collabdocs.onrender.com) — Note: Render free tier has a ~30s cold start after 15 minutes of inactivity.
>
> **Frontend:** [https://collabdocs.vercel.app](https://collabdocs.vercel.app)

---

## Table of Contents

- [Features](#features)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [API Reference](#api-reference)
- [WebSocket Protocol](#websocket-protocol)
- [Environment Variables](#environment-variables)
- [Running Locally](#running-locally)
- [Deployment](#deployment)
- [Project Structure](#project-structure)

---

## Features

- **JWT Authentication** — Stateless register/login with HS256-signed tokens
- **Document CRUD** — Create, read, update, and delete documents with pagination
- **Role-Based Access Control** — Three roles: `OWNER`, `EDITOR`, `VIEWER` with enforced permission hierarchy
- **Real-Time Collaborative Editing** — WebSocket + STOMP with version vector conflict detection (Last-Write-Wins)
- **Horizontal Scalability** — Redis Pub/Sub broadcasts edits across multiple server instances
- **Presence Tracking** — Redis TTL keys with 5s expiry and 3s client heartbeat to show active users per document
- **Version History** — Append-only operation log with point-in-time preview and restore
- **Comment Threads** — Threads anchored to character ranges with replies and resolve functionality
- **Full-Text Search** — PostgreSQL `tsvector`/`tsquery` with weighted ranking (title > content) and GIN index
- **Email Notifications** — Async email on document share via Spring Mail
- **Docker Compose** — Full local stack with one command

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.2.5 |
| Build | Maven |
| Database | PostgreSQL 16 |
| Cache / Pub-Sub / Presence | Redis 7 |
| Real-Time | WebSocket + STOMP + SockJS |
| Auth | Spring Security + JWT (JJWT 0.11.5) |
| Email | Spring Mail (SMTP) |
| HTTP Client | WebClient (Spring WebFlux) |
| Containerization | Docker + Docker Compose |
| Hosting | Render (App + PostgreSQL), Upstash (Redis) |

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                        Clients                          │
│         REST (Axios)          WebSocket (STOMP)         │
└──────────────┬────────────────────────┬─────────────────┘
               │                        │
               ▼                        ▼
┌──────────────────────────────────────────────────────────┐
│                   Spring Boot App                        │
│                                                          │
│  JwtAuthFilter ──► SecurityFilterChain                   │
│                                                          │
│  REST Controllers        WebSocket Controllers           │
│  ├── AuthController      ├── DocumentEditController      │
│  ├── DocumentController  └── WebSocketAuthInterceptor    │
│  ├── PresenceController                                  │
│  ├── HistoryController   Service Layer                   │
│  ├── CommentController   ├── DocumentEditService         │
│  └── SearchController    ├── PresenceService             │
│                          ├── HistoryService              │
│                          └── RedisSubscriptionService    │
└───────────┬──────────────────────────┬───────────────────┘
            │                          │
            ▼                          ▼
   ┌─────────────────┐      ┌─────────────────────┐
   │   PostgreSQL     │      │        Redis         │
   │                 │      │                     │
   │  documents      │      │  Pub/Sub channels   │
   │  users          │      │  doc:edits:{id}     │
   │  permissions    │      │                     │
   │  operations     │      │  Presence TTL keys  │
   │  comment_threads│      │  presence:{id}:{uid}│
   │  comments       │      │                     │
   └─────────────────┘      └─────────────────────┘
```

### Key Design Decisions

**Conflict Resolution — Last-Write-Wins with Version Vectors**
Each client tracks a `clientVersion`. On every edit, the server compares `clientVersion` against `serverVersion`. A mismatch rejects the edit — the client re-syncs and retries. Simpler than Operational Transformation and sufficient for a portfolio-scale system.

**Event Sourcing (Lightweight)**
Every accepted edit appends a `DocumentOperation` row. The current document content is a cache of replaying all operations. This gives version history and restore for free without any extra schema complexity.

**Redis Pub/Sub for Horizontal Scaling**
Spring's default in-memory STOMP broker only broadcasts to clients on the same server instance. Redis Pub/Sub solves this — every instance subscribes to the same `doc:edits:{id}` channels and forwards messages to its locally connected WebSocket clients.

---

## API Reference

### Auth

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `POST` | `/api/auth/register` | None | Register new user |
| `POST` | `/api/auth/login` | None | Login, receive JWT |

**Register / Login Request:**
```json
{
  "email": "alice@example.com",
  "password": "password123",
  "fullName": "Alice Smith"
}
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "email": "alice@example.com",
  "fullName": "Alice Smith"
}
```

---

### Documents

| Method | Endpoint | Role | Description |
|---|---|---|---|
| `POST` | `/api/documents` | Any | Create document |
| `GET` | `/api/documents` | Any | List accessible documents (paginated) |
| `GET` | `/api/documents/{id}` | VIEWER+ | Get document detail |
| `PUT` | `/api/documents/{id}` | EDITOR+ | Update title or content |
| `DELETE` | `/api/documents/{id}` | OWNER | Delete document |
| `POST` | `/api/documents/{id}/share` | OWNER | Share with user, sends email |
| `DELETE` | `/api/documents/{id}/permissions/{userId}` | OWNER | Revoke access |

**Share Request:**
```json
{
  "email": "bob@example.com",
  "role": "EDITOR"
}
```

---

### Presence

| Method | Endpoint | Role | Description |
|---|---|---|---|
| `POST` | `/api/documents/{id}/presence/heartbeat` | VIEWER+ | Refresh presence (call every 3s) |
| `DELETE` | `/api/documents/{id}/presence` | VIEWER+ | Leave document |
| `GET` | `/api/documents/{id}/presence` | VIEWER+ | Get currently active users |

---

### Version History

| Method | Endpoint | Role | Description |
|---|---|---|---|
| `GET` | `/api/documents/{id}/history` | VIEWER+ | Full operation log |
| `GET` | `/api/documents/{id}/history/preview?version=N` | VIEWER+ | Preview content at version N |
| `POST` | `/api/documents/{id}/history/restore?version=N` | OWNER | Restore document to version N |

---

### Comments

| Method | Endpoint | Role | Description |
|---|---|---|---|
| `POST` | `/api/documents/{id}/threads` | EDITOR+ | Create thread anchored to char range |
| `GET` | `/api/documents/{id}/threads` | VIEWER+ | List threads (filter: `?resolved=false`) |
| `POST` | `/api/documents/{id}/threads/{threadId}/resolve` | OWNER / Creator | Resolve thread |
| `DELETE` | `/api/documents/{id}/threads/{threadId}` | OWNER / Creator | Delete thread |
| `POST` | `/api/documents/{id}/threads/{threadId}/comments` | EDITOR+ | Reply to thread |
| `PUT` | `/api/documents/{id}/threads/{threadId}/comments/{commentId}` | Author | Edit comment |
| `DELETE` | `/api/documents/{id}/threads/{threadId}/comments/{commentId}` | Author / OWNER | Delete comment |

**Create Thread Request:**
```json
{
  "startIndex": 0,
  "endIndex": 11,
  "body": "What does this mean?"
}
```

---

### Search

| Method | Endpoint | Role | Description |
|---|---|---|---|
| `GET` | `/api/search?q={query}` | Any | Full-text search, permission-filtered |

---

## WebSocket Protocol

**Endpoint:** `http://localhost:8080/ws` (SockJS)

**Authentication:** Pass JWT in the STOMP `CONNECT` headers:
```javascript
client.connectHeaders = {
  Authorization: `Bearer ${token}`
}
```

### Destinations

| Direction | Destination | Description |
|---|---|---|
| Client → Server | `/app/document/{id}/edit` | Send an edit operation |
| Client → Server | `/app/document/{id}/sync` | Request full document state (after rejection) |
| Server → All | `/topic/document/{id}/edits` | Broadcast accepted edit |
| Server → All | `/topic/document/{id}/presence` | Broadcast presence update |
| Server → User | `/user/queue/errors` | Rejected edit or error |
| Server → User | `/user/queue/sync` | Sync response with current content + version |

### Edit Payload (Client → Server)

```json
{
  "type": "INSERT",
  "position": 5,
  "content": "hello ",
  "length": 0,
  "clientVersion": 3
}
```

`type` is one of `INSERT`, `DELETE`, or `REPLACE`.

### Accepted Edit Broadcast (Server → All Subscribers)

```json
{
  "documentId": 1,
  "operationId": 42,
  "type": "INSERT",
  "position": 5,
  "content": "hello ",
  "length": 0,
  "serverVersion": 4,
  "editorId": 2,
  "editorEmail": "bob@example.com",
  "timestamp": "2024-01-01T12:00:00"
}
```

### Rejected Edit (Server → Sender Only)

```json
{
  "documentId": 1,
  "clientVersion": 3,
  "serverVersion": 5,
  "reason": "Stale version — re-sync and retry"
}
```

**On rejection:** Send to `/app/document/{id}/sync`, receive current `content` and `version` on `/user/queue/sync`, then retry the edit with the updated `clientVersion`.

---

## Environment Variables

| Variable | Description | Example |
|---|---|---|
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://host:5432/collabdocs` |
| `DB_USERNAME` | PostgreSQL username | `postgres` |
| `DB_PASSWORD` | PostgreSQL password | `yourpassword` |
| `REDIS_HOST` | Redis host | `localhost` |
| `REDIS_PORT` | Redis port | `6379` |
| `REDIS_PASSWORD` | Redis password (if any) | _(empty for local)_ |
| `JWT_SECRET` | HS256 signing key — min 32 chars | `your-256-bit-secret-key-here` |
| `JWT_EXPIRATION` | Token expiry in milliseconds | `86400000` (24h) |
| `MAIL_HOST` | SMTP host | `smtp.gmail.com` |
| `MAIL_PORT` | SMTP port | `587` |
| `MAIL_USERNAME` | SMTP email address | `you@gmail.com` |
| `MAIL_PASSWORD` | Gmail App Password | `abcd efgh ijkl mnop` |
| `CORS_ORIGINS` | Allowed frontend origins (comma-separated) | `https://yourapp.vercel.app` |

> **Note:** Do not embed `DB_USERNAME` and `DB_PASSWORD` directly in `SPRING_DATASOURCE_URL`. Keep them as separate variables.

---

## Running Locally

### Prerequisites

- Java 21
- Maven
- Docker Desktop (must be running)

### Steps

**1. Clone the repository**
```bash
git clone https://github.com/your-username/collabdocs.git
cd collabdocs
```

**2. Start PostgreSQL and Redis**
```bash
docker compose up postgres redis -d
```

**3. Configure `application.yml`**

For local development, hardcode values directly — no env variables needed:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/collabdocs
    username: postgres
    password: postgres
  data:
    redis:
      host: localhost
      port: 6379
app:
  jwt:
    secret: your-256-bit-secret-key-here-make-it-long-enough
    expiration: 86400000
```

**4. Run the app**
```bash
mvn spring-boot:run
```

App starts at `http://localhost:8080`

**5. Apply full-text search trigger (first time only)**

```bash
docker exec -it collabdocs-postgres psql -U postgres -d collabdocs
```

Then run:
```sql
ALTER TABLE documents ADD COLUMN IF NOT EXISTS search_vector tsvector;

CREATE OR REPLACE FUNCTION documents_search_vector_update()
RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('english', coalesce(NEW.title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(NEW.content, '')), 'B');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER documents_search_vector_trigger
BEFORE INSERT OR UPDATE ON documents
FOR EACH ROW EXECUTE FUNCTION documents_search_vector_update();

CREATE INDEX IF NOT EXISTS idx_documents_search_vector
ON documents USING GIN(search_vector);

UPDATE documents SET title = title;
```

Exit with `\q`

### Full Docker (App + Infra)

```bash
mvn clean package -DskipTests
docker compose up --build
```

---

## Deployment

### Stack

| Service | Platform |
|---|---|
| Spring Boot App | Render (Docker Web Service) |
| PostgreSQL | Render (Managed PostgreSQL) |
| Redis | Upstash (Serverless Redis) |
| Frontend | Vercel |

### Notes

- **WebSocket support** — Render supports HTTP upgrade for WebSocket connections. No extra configuration needed.
- **Cold starts** — Render free tier spins down after 15 minutes of inactivity. Expect ~30s on the first request.
- **CORS** — Set `CORS_ORIGINS` to your exact deployed frontend URL (e.g. `https://yourapp.vercel.app`). No trailing slash.
- **Search trigger** — After first deployment, connect to the Render PostgreSQL instance and run the SQL from Step 5 above.
- **Redis password** — Upstash requires a password. Set it via `REDIS_PASSWORD` environment variable.

---

## Project Structure

```
src/main/java/com/collabdocs/
├── config/
│   ├── SecurityConfig.java
│   ├── AppConfig.java
│   ├── RedisConfig.java
│   └── WebSocketConfig.java
├── controller/
│   ├── AuthController.java
│   ├── DocumentController.java
│   ├── PresenceController.java
│   ├── HistoryController.java
│   ├── CommentController.java
│   └── SearchController.java
├── dto/
│   ├── request/        AuthRequest, DocumentRequest, EditRequest, CommentRequest
│   └── response/       AuthResponse, DocumentResponse, EditResponse,
│                       HistoryResponse, CommentResponse, PresenceResponse
├── entity/
│   ├── User.java
│   ├── Document.java
│   ├── DocumentPermission.java
│   ├── DocumentOperation.java
│   ├── CommentThread.java
│   └── Comment.java
├── enums/
│   ├── Role.java           (VIEWER, EDITOR, OWNER)
│   └── OperationType.java  (INSERT, DELETE, REPLACE)
├── exception/
│   ├── GlobalExceptionHandler.java
│   └── ...custom exceptions
├── filter/
│   └── JwtAuthFilter.java
├── repository/
│   └── ...6 JPA repositories
├── service/
│   ├── AuthService.java
│   ├── DocumentService.java
│   ├── DocumentEditService.java
│   ├── EmailService.java
│   ├── HistoryService.java
│   ├── CommentService.java
│   ├── PresenceService.java
│   ├── RedisSubscriptionService.java
│   └── SearchService.java
├── util/
│   ├── JwtUtil.java
│   └── SecurityUtils.java
└── websocket/
    ├── WebSocketAuthInterceptor.java
    ├── DocumentEditController.java
    ├── RedisMessageSubscriber.java
    └── PresenceWebSocketHandler.java
```

---

## Role Hierarchy

| Action | VIEWER | EDITOR | OWNER |
|---|---|---|---|
| Read documents | ✅ | ✅ | ✅ |
| Edit content (WebSocket) | ❌ | ✅ | ✅ |
| Create comment threads | ❌ | ✅ | ✅ |
| Reply to comments | ❌ | ✅ | ✅ |
| View version history | ✅ | ✅ | ✅ |
| Restore versions | ❌ | ❌ | ✅ |
| Share document | ❌ | ❌ | ✅ |
| Revoke access | ❌ | ❌ | ✅ |
| Delete document | ❌ | ❌ | ✅ |

---

## License

MIT
