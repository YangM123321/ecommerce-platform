# ecommerce-platform

E-commerce backend built in stages: a modular monolith, then microservices on Azure (AKS),
then Kafka, Redis and monitoring.

**Current stage: Session 1, monolith skeleton.**

## Tech stack

Java 17 · Spring Boot 3 · PostgreSQL 16 · Flyway · Docker · GitHub Actions ·
Testcontainers · ArchUnit

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
