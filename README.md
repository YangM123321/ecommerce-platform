# ecommerce-platform

E-commerce backend built in stages: a modular monolith, then microservices on Azure (AKS),
then Kafka, Redis and monitoring.

**Current stage: Session 2, user module with JWT authentication and roles.**

## Tech stack

Java 17 · Spring Boot 3 · Spring Security (JWT) · PostgreSQL 16 · Flyway · Docker ·
GitHub Actions · JUnit 5 · Mockito · Testcontainers · ArchUnit

## Run locally

Requirements: Docker Desktop (or Docker Engine + Compose).

```bash
docker compose up --build
```

Then check:

```bash
curl http://localhost:8080/api/info
curl http://localhost:8080/actuator/health
```

Stop: `docker compose down` (add `-v` to also wipe the database).

## Auth API (Session 2)

| Method | Path | Auth | What it does |
|---|---|---|---|
| POST | `/api/auth/register` | none | Create an account (role USER) |
| POST | `/api/auth/login` | none | Returns an access token (15 min) and a refresh token (7 days) |
| POST | `/api/auth/refresh` | none | Swaps a refresh token for new tokens (old one stops working) |
| POST | `/api/auth/logout` | none | Revokes a refresh token |
| GET | `/api/users/me` | Bearer token | Your own profile |
| GET | `/api/admin/users` | Bearer token, ADMIN | List all users |

Send the access token as `Authorization: Bearer <token>`.
Docker Compose creates an admin account: `admin@local.test` / `Admin123!` (local only).

Try the whole flow on Windows (app running in Docker):

```powershell
powershell -ExecutionPolicy Bypass -File scripts\smoke-test.ps1
```

## Run tests

Requirements: Java 17, Maven 3.9+, Docker running (Testcontainers starts Postgres).

```bash
mvn verify
```

## Project layout

```
src/main/java/com/miayang/ecommerce/
  user/       user module    (schema: users)
  product/    product module (schema: catalog)
  order/      order module   (schema: ordering)
  shared/     cross-cutting code
src/main/resources/db/migration/   Flyway SQL migrations
docs/architecture.md               diagrams
```

See [docs/architecture.md](docs/architecture.md) for diagrams and module rules.
