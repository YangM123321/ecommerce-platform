# ecommerce-platform

E-commerce backend built in stages: a modular monolith, then microservices on Azure (AKS),
then Kafka, Redis and monitoring.

**Current stage: Session 4, microservices: user, product and order services behind an API gateway,
with Eureka service discovery and a Config Server.**

## Tech stack

Java 17 · Spring Boot 3.4 · Spring Cloud 2024 (Gateway, Eureka, Config Server, OpenFeign) ·
Spring Security (JWT) · PostgreSQL 16 · Flyway · Docker Compose · GitHub Actions ·
JUnit 5 · Mockito · Testcontainers

## Services

| Service | Port | What it does | Database |
|---|---|---|---|
| api-gateway | 8080 | The only public entry point: routes requests, checks JWTs | none |
| config-server | 8888 | Serves each service's settings from `config-repo/` | none |
| discovery-server | 8761 | Eureka registry, dashboard at http://localhost:8761 | none |
| user-service | 8081 (inside Docker) | Register, login, tokens, users, admin | users_db |
| product-service | 8082 (inside Docker) | Catalog, categories, stock | catalog_db |
| order-service | 8083 (inside Docker) | Orders; calls product-service for prices and stock | orders_db |

## Run locally

Requirements: Docker Desktop (or Docker Engine + Compose).

```bash
docker compose up --build -d
docker compose ps          # wait until every service says (healthy), 1-3 minutes
```

Then check:

```bash
curl http://localhost:8080/actuator/health
curl "http://localhost:8080/api/products?size=2"
```

Open http://localhost:8761: the Eureka dashboard lists API-GATEWAY, USER-SERVICE,
PRODUCT-SERVICE and ORDER-SERVICE.

Stop: `docker compose down` (add `-v` to also wipe the databases).
Rebuild and restart just one service: `docker compose up --build -d order-service`.

### Failure demo

```powershell
powershell -ExecutionPolicy Bypass -File scripts\failure-demo.ps1
```

Stops product-service and shows login, profile and order history still work, while browsing and
ordering return a clean 503. Then starts it again and shows ordering recover.

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

Try the whole flow on Windows (stack running in Docker, everything goes through the gateway on 8080):

```powershell
powershell -ExecutionPolicy Bypass -File scripts\smoke-test.ps1
powershell -ExecutionPolicy Bypass -File scripts\smoke-test-orders.ps1
```

## Run tests

Requirements: Java 17, Maven 3.9+, Docker running (Testcontainers starts Postgres).

```bash
mvn verify                       # every module
mvn -pl order-service -am verify # one service (plus common)
```

Tests run each service alone: Config Server and Eureka are switched off, other services are mocked.

## Project layout

```
pom.xml               parent: module list, Spring Boot + Spring Cloud versions
common/               shared library: JWT config, CurrentUser, error handling, paging
config-server/        Spring Cloud Config Server
discovery-server/     Eureka server
api-gateway/          Spring Cloud Gateway (reactive) + edge JWT check
user-service/         auth, users, roles           (Flyway -> users_db)
product-service/      catalog, stock, /internal/*  (Flyway -> catalog_db)
order-service/        orders, Feign client         (Flyway -> orders_db)
config-repo/          settings served by config-server (local and azure profiles)
infra/postgres/       creates the three databases on first start
Dockerfile            one Dockerfile for every module (MODULE build argument)
docs/architecture.md  component diagram, failure points, design decisions
docs/manual-deploy.md manual deploy steps + timing log
postman/              Postman collection (via the gateway)
scripts/              smoke tests and the failure demo
```

See [docs/architecture.md](docs/architecture.md) for diagrams and design decisions.
