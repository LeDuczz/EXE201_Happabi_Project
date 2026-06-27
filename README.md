# Happabi Backend

Happabi Backend is the enterprise service layer for a maternity and postpartum care booking platform. The system connects mothers with verified nurses, supports doctor-led nurse onboarding review, handles booking/payment workflows, and gives administrators operational visibility over users, revenue, incidents, feedback, audit logs, and platform configuration.

## Technology Stack

| Area | Technology |
| --- | --- |
| Runtime | Java 21 |
| Framework | Spring Boot 3.3, Spring MVC, Spring Security |
| Persistence | Spring Data JPA, Hibernate, PostgreSQL, pgvector |
| Cache and rate limit | Redis, Spring Cache |
| Authentication | AWS Cognito, OAuth2 Resource Server, JWT |
| Object storage | AWS S3 presigned upload/download |
| Async integration | AWS SQS workers for file cleanup and review aggregation |
| Payment | PayOS payment link and webhook integration |
| Realtime | Netty Socket.IO notification gateway |
| AI | Spring AI, Google GenAI, pgvector-backed knowledge base |
| Observability | Actuator, Micrometer Prometheus, structured logging, audit outbox, Elasticsearch |
| API documentation | Springdoc OpenAPI |
| Testing | JUnit 5, Spring Security Test, Testcontainers, ArchUnit |

## Core Capabilities

### Identity and Access Control

- Phone-based registration and OTP verification through AWS Cognito.
- Role-based access for `ADMIN`, `DOCTOR`, `NURSE`, and `MOTHER`.
- Permission-driven authorization at service/API boundaries.
- Token blacklist and Redis-backed security controls.
- Global and endpoint-level rate limiting with audit and metrics.

### Nurse Onboarding

- Nurse profile, KYC, certification, contract, and bank account management.
- CCCD OCR support and S3-backed document storage.
- Doctor review flow for nurse approval or rejection.
- Nurse activation rules based on verification and required deposit.
- Availability windows so nurses can explicitly open bookable time ranges.

### Booking and Scheduling

- Single-service booking model with service duration and price configuration.
- Slot-safe booking flow using transaction boundaries, database locks, and uniqueness constraints.
- Nurse availability validation and overlapping schedule prevention.
- Payment modes for 30 percent deposit plus cash remainder, or full app payment.
- Mother and nurse cancellation policies, refund request flow, and incident handling.

### Payment and Settlement

- PayOS payment link creation for bookings, wallet top-ups, and nurse deposits.
- Idempotent webhook processing with unique transaction references.
- Configurable platform commission and payment gateway fee.
- Admin wallet ledger for platform funds.
- Nurse wallet ledger for earnings, top-ups, deposit, and withdrawals.
- Booking settlement after service completion, with platform revenue and nurse payout tracking.

### Work Sessions

- Nurse check-in and checkout with evidence uploads.
- Checklist completion tracking.
- Mother confirmation and auto-confirm background job.
- Incident reporting when mother is unreachable or nurse no-shows.
- Nurse penalty workflow for operational policy enforcement.

### Notifications and Realtime UX

- Persistent notifications for mother, nurse, doctor, and admin flows.
- Realtime delivery through Socket.IO.
- Booking, payment, withdrawal, onboarding, incident, and feedback notifications.

### Admin Operations

- Operations dashboard with GMV, payment volume, platform revenue, PayOS fees, nurse payout, and net cash contribution.
- User management and doctor account provisioning.
- Admin wallet and withdrawal payout management.
- Work-session incident review.
- User feedback review.
- AI knowledge base review and approval.
- System configuration for commission and payment fee policy.
- Audit log search backed by Elasticsearch projection.

### AI Assistant

- AI chat API for platform support use cases.
- Knowledge items stored and reviewed before being used by the chatbot.
- pgvector support for semantic retrieval.

## Architecture Overview

The backend follows a layered Spring architecture:

```text
controller -> service -> repository -> database/integration
```

Key design principles:

- Controllers expose HTTP contracts and validation.
- Services own business rules, transaction boundaries, audit annotations, metrics, and integration orchestration.
- Repositories encapsulate persistence access.
- Integrations isolate external providers such as Cognito, S3, SQS, PayOS, Redis, Elasticsearch, and Socket.IO.
- Domain errors are returned through centralized exception handling.

## Project Structure

```text
src/main/java/com/minduc/happabi
├── common          # Shared base response, utilities, constants
├── config          # Spring, security, startup, integration configuration
├── controller      # REST API controllers grouped by domain
├── dto             # Request/response contracts and events
├── entity          # JPA entities
├── enums           # Domain enums
├── exception       # Application error codes and handlers
├── filter          # HTTP logging, rate limit, security filters
├── integration     # External system adapters
├── listener        # Domain and integration event listeners
├── mapper          # MapStruct mappers
├── observability   # Audit, metrics, logging, outbox projection
├── repository      # Spring Data repositories
├── seed            # Startup seed support
└── service         # Business services
```

## Local Development

### Prerequisites

- JDK 21
- Maven wrapper included in the repository
- PostgreSQL 15 with pgvector
- Redis
- Elasticsearch
- AWS Cognito/S3/SQS compatible configuration
- PayOS sandbox or production credentials

### Run Tests

```bash
./mvnw test
```

On Windows PowerShell:

```powershell
.\mvnw.cmd test
```

### Run Application

```bash
./mvnw spring-boot:run
```

The default HTTP API port used by the project is typically `8088`, and the metrics port is configured separately when running through Docker Compose.

## Configuration

Configuration is environment-driven. Important groups include:

- Database datasource and Hibernate settings.
- Redis host/port and cache TTL values.
- AWS Cognito user pool, client, region, and domain.
- AWS S3 bucket and SQS queue URLs.
- PayOS client ID, API key, checksum key, return URL, and cancel URL.
- Socket.IO host/port.
- Observability, audit outbox, logging, and Prometheus settings.
- Business settings such as booking payment expiry, commission rate, PayOS fee rate, and nurse deposit policy.

Keep secrets outside source control and inject them through environment variables or deployment secret management.

## Production Notes

- Booking correctness depends on database transactions, pessimistic locking, and unique constraints rather than JVM memory locks.
- Payment processing is webhook-idempotent and should be protected by unique transaction/order references.
- Redis improves cache, rate limit, and session-related performance, but critical booking/payment correctness must remain database-backed.
- All external side effects should be designed for retry and idempotency.
- Audit and metric signals are first-class production concerns and should be reviewed before releasing new business flows.
- Use database migrations or approved SQL scripts for production schema evolution instead of relying only on Hibernate auto-update.

## Quality Gates

Recommended checks before merging:

```bash
./mvnw test
```

Additional production checks:

- Verify transactional booking/payment paths.
- Verify PayOS webhook idempotency.
- Verify audit records for business actions.
- Verify permission coverage for every protected endpoint.
- Verify Redis/DB/external-provider failure handling.
- Verify API compatibility with the frontend.
