# 🛒 QuickBite — Omnichannel E-Commerce Platform
> Microservices · Java Spring Boot · Kafka · gRPC · PostgreSQL · Docker · CI/CD

A production-ready, omnichannel e-commerce backend system built on a microservices architecture. Deployed on a VPS via GitHub Actions CI/CD with automated rollback, full observability stack (Prometheus, Grafana, Jaeger), and real payment integration (VNPay).

🌐 **Live:** `https://khoatla-ecommerce.duckdns.org`

---

## 📋 Table of Contents
- [Architecture](#-architecture)
- [Services](#-services)
- [Tech Stack](#-tech-stack)
- [Key Design Patterns](#-key-design-patterns)
- [CI/CD Pipeline](#-cicd-pipeline)
- [Getting Started](#-getting-started)
- [API Documentation](#-api-documentation)
- [Monitoring](#-monitoring)

---

## 🏗️ Architecture

```
                        Internet
                            │ HTTPS (443)
                     ┌──────▼──────┐
                     │    Nginx    │  TLS termination (Let's Encrypt)
                     │  (host)     │  Reverse proxy to gateway
                     └──────┬──────┘
                            │ HTTP (127.0.0.1:8080)
                     ┌──────▼──────────────────────┐
                     │       API Gateway :8080      │
                     │  Spring Cloud Gateway WebFlux│
                     │  • JWT verification          │
                     │  • Rate limiting (Redis)     │
                     │  • CORS                      │
                     └──┬────┬────┬────┬────┬───────┘
                        │    │    │    │    │
           ┌────────────┘    │    │    │    └──────────────┐
           ▼                 ▼    ▼    ▼                   ▼
  ┌─────────────┐  ┌──────────┐ ┌──────────┐  ┌──────────────────┐
  │  Identity   │  │ Product  │ │  Order & │  │   Notification   │
  │  & User     │  │ Catalog  │ │Inventory │  │    Service       │
  │  :8082      │  │ :8083    │ │ :8084    │  │    :8086         │
  │  gRPC:9090  │  │gRPC:9091 │ │          │  │                  │
  └─────────────┘  └──────────┘ └────┬─────┘  └──────────────────┘
                                      │              ▲
                                      │         ┌────┴────┐
                                      │         │ Payment │
                                      │         │Billing  │
                                      │         │ :8085   │
                                      │         └─────────┘
                              [Apache Kafka] ← Saga / Outbox events
                           ┌──────────────────┐
                           │   Infrastructure │
                           │  PostgreSQL 16   │  5 isolated databases
                           │  Redis 7         │  rate-limit + dist. lock
                           │  Kafka 3.8.1     │  KRaft (no ZooKeeper)
                           └──────────────────┘
```

---

## 🧩 Services

| Service | Port | gRPC | Database | Responsibility |
|---|---|---|---|---|
| **api-gateway** | 8080 | — | Redis | Single entry point. JWT auth, rate limiting, routing. |
| **identity-user-service** | 8082 | 9090 | `identity_user_db` | User registration, login, JWT issuance, roles, address management. |
| **product-catalog-service** | 8083 | 9091 | `product_catalog_db` | Brands, categories, products, SKU variants, stock, slugs, search. |
| **order-inventory-service** | 8084 | — | `order_inventory_db` + Redis | Order state machine, idempotency, inventory management, Outbox→Kafka. |
| **payment-billing-service** | 8085 | — | `payment_billing_db` | VNPay integration, payment lifecycle, Saga compensation via Kafka. |
| **notification-service** | 8086 | — | `notification_db` | Email (Gmail) + SMS (Twilio) + Push. Idempotent event consumption. |

---

## 🛠️ Tech Stack

| Category | Technology |
|---|---|
| **Language** | Java 17 |
| **Framework** | Spring Boot 4.1.1 |
| **Build Tool** | Gradle (Wrapper) |
| **API Gateway** | Spring Cloud Gateway Server WebFlux (Spring Cloud 2025.1.3) |
| **Auth** | JWT — JJWT 0.11.5, HS256, shared secret |
| **Inter-service (sync)** | gRPC (Protobuf 3.25.1 · grpc-java 1.68.1) |
| **Message Broker** | Apache Kafka 3.8.1 (KRaft mode — no ZooKeeper) |
| **Messaging Pattern** | Transactional Outbox + Saga Compensation |
| **Primary Database** | PostgreSQL 16 (database-per-service) |
| **Cache / Distributed Lock** | Redis 7 (Redisson) |
| **Schema Migration** | Flyway |
| **Containerization** | Docker Compose |
| **Image Registry** | GitHub Container Registry (GHCR) |
| **CI/CD** | GitHub Actions (3-job pipeline with auto-rollback) |
| **Reverse Proxy / TLS** | Nginx + Let's Encrypt (Certbot) |
| **Monitoring** | Prometheus + Grafana + Loki + Promtail + Jaeger (OpenTelemetry) |
| **Load Testing** | k6 |
| **API Docs** | SpringDoc OpenAPI / Swagger UI 3.1.0 |
| **Payment Gateway** | VNPay (HMAC-SHA512) |
| **Notifications** | Spring Mail (Gmail SMTP) + Twilio SDK 10.1.5 (SMS/WhatsApp) |

---

## 🔑 Key Design Patterns

### 1. Transactional Outbox Pattern
Prevents dual-write problem: Kafka events are written atomically to `outbox_events` table in the same DB transaction as domain changes. A scheduler then publishes PENDING events to Kafka.

```
[Service] ──(same transaction)──→ [Domain Table] + [outbox_events table]
[Outbox Scheduler] ──polls──→ publishes to Kafka ──→ marks as PUBLISHED
```

### 2. Saga Pattern (Choreography-based)
```
Order Service: order.created ──→ Payment Service: charge customer
  ✅ payment.completed ──→ Order Service: CONFIRM order
  ❌ payment.failed    ──→ Order Service: CANCEL order + return inventory
```

### 3. gRPC for Internal Sync Calls
```
Order Service ──gRPC──→ Product Catalog :9091  (validate SKU + stock)
Order Service ──gRPC──→ Identity/User   :9090  (get user info)
Notification  ──gRPC──→ Identity/User   :9090  (get contact info)
```

### 4. Gateway-level Rate Limiting (Redis Token Bucket)
| Route | Rate | Burst |
|---|---|---|
| Login/Register | 5 req/s | 10 |
| Product Catalog | 100 req/s | 200 |
| Orders | 100 req/s | 300 |
| Payments | 20 req/s | 40 |

### 5. Database-per-Service
Each service owns its DB and PostgreSQL role. No cross-service DB access.

---

## ⚙️ CI/CD Pipeline

GitHub Actions — 3 sequential jobs on push to `main`:

```
┌─────────────────────────────────────────────────────────────┐
│  Job 1: build-test  (6 services in parallel)                │
│  • ./gradlew clean bootJar  ← MUST PASS                     │
│  • ./gradlew test           ← logs only (non-blocking)       │
└──────────────────────┬──────────────────────────────────────┘
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  Job 2: docker-push  (6 services in parallel)               │
│  • Multi-stage Dockerfile build                             │
│  • Push to GHCR: ghcr.io/<owner>/<service>:<sha> + :latest │
│  • Layer caching via GitHub Actions cache                   │
└──────────────────────┬──────────────────────────────────────┘
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  Job 3: deploy (single job)                                 │
│  • SCP compose + infra files to VPS                        │
│  • SSH: docker compose pull → up -d                        │
│  • Health check loop (up to 25 min, 10s intervals)         │
│  • Check /actuator/health + https://<DOMAIN>/health        │
│  • ❌ ROLLBACK if unhealthy → revert to previous IMAGE_TAG  │
│  • Prune Docker images older than 48h                      │
└─────────────────────────────────────────────────────────────┘
```

---

## 🚀 Getting Started

### Prerequisites
- Docker & Docker Compose
- Java 17+ (for local development)

### Local Development

```bash
git clone https://github.com/tranleanhkhoa041221-cpu/KhoaTLA_JB-IOC-PTHB251125_DEVOPS_PROJECT_Omnichannel_ECommerce.git
cd KhoaTLA_JB-IOC-PTHB251125_DEVOPS_PROJECT_Omnichannel_ECommerce

# Copy env template
cp .env.example .env
# Edit .env with your credentials (DB passwords, JWT secret, VNPay keys, Twilio, etc.)

# Start all services (lite profile — 1GB RAM)
docker compose --env-file profiles/lite.env -f docker-compose.production.yml up -d

# Check health
docker compose ps
curl http://localhost:8080/actuator/health
```

### Profiles

| Profile | RAM Target | Use Case |
|---|---|---|
| `lite.env` | 1 GB | Minimal — development/demo |
| `standard.env` | 2 GB | Standard VPS |
| `4gb.env` | 4 GB | Full production |

---

## 📖 API Documentation

Swagger UI available per service (dev mode):
- API Gateway routes: `http://localhost:8080`
- Identity Service: `http://localhost:8082/swagger-ui.html`
- Product Catalog: `http://localhost:8083/swagger-ui.html`
- Order & Inventory: `http://localhost:8084/swagger-ui.html`
- Payment: `http://localhost:8085/swagger-ui.html`

Postman collection: `ecommerce_omnichannel_postman_collection.json` (included in repo)

---

## 📊 Monitoring

```bash
# Start monitoring stack (optional)
docker compose -f docker-compose.production.yml -f monitoring/docker-compose.monitoring.yml up -d

# Access via SSH tunnel (ports are loopback-only)
ssh -L 3000:localhost:3000 user@your-vps
# Open: http://localhost:3000  (Grafana — admin/admin)
```

| Tool | Purpose |
|---|---|
| **Prometheus** | Metrics scraping (`/actuator/prometheus`) |
| **Grafana** | Dashboards |
| **Loki + Promtail** | Log aggregation |
| **Jaeger** | Distributed tracing (OpenTelemetry) |

---

## 👤 Author

**Trần Lê Anh Khoa** — Backend Developer (Java Spring Boot) & DevOps  
📧 tranleanhkhoa041221@gmail.com  
🔗 [GitHub](https://github.com/tranleanhkhoa041221-cpu)
