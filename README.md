# ecommerce-platform

E-commerce backend built in stages: a modular monolith, then microservices on Azure (AKS),
then Kafka, Redis and monitoring.

**Current stage: Session 3, complete modular monolith: users, catalog and orders.**

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

## Catalog and orders API (Session 3)

| Method | Path | Auth | What it does |
|---|---|---|---|
| GET | `/api/products?categoryId=&q=&page=&size=&sort=` | none | Browse, filter, search, page and sort products |
| GET | `/api/products/{id}` | none | One product, with stock |
| POST | `/api/products` | ADMIN | Create a product with initial stock |
| PUT | `/api/products/{id}` | ADMIN | Update name, description, price, category |
| DELETE | `/api/products/{id}` | ADMIN | Deactivate (soft delete) |
| POST | `/api/products/{id}/stock` | ADMIN | Add stock |
| GET / POST | `/api/categories` | none / ADMIN | List / create categories |
| POST | `/api/orders` | logged in | Place an order (all items or nothing) |
| GET | `/api/orders` | logged in | My orders, newest first, paged |
| GET | `/api/orders/{id}` | owner or ADMIN | One order with its items |
| POST | `/api/orders/{id}/cancel` | owner or ADMIN | Cancel and return the stock |

Postman: import `postman/ecommerce-platform.postman_collection.json` and run the folders in order.
Docker Compose creates an admin account: `admin@local.test` / `Admin123!` (local only).

Try the whole flow on Windows (app running in Docker):

```powershell
powershell -ExecutionPolicy Bypass -File scripts\smoke-test.ps1
powershell -ExecutionPolicy Bypass -File scripts\smoke-test-orders.ps1
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
docs/architecture.md               diagrams and design decisions
docs/manual-deploy.md              manual deploy steps + timing log
postman/                           Postman collection
scripts/                           smoke tests
```

See [docs/architecture.md](docs/architecture.md) for diagrams and module rules.
