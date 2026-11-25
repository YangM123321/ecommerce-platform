# Team roles

A real team splits this project across several people. I'm building it alone, so I play every
role, but I switch hats on purpose. Each role has its own job, and its own checks before work
counts as done. This is how I'd organise a 5-person team on the same project.

## The roles

| Role | Owns | Done means |
|---|---|---|
| **Product Owner (PO)** | What gets built and in what order: the sprint board, issues, acceptance criteria | Every issue has a clear "Deliverable", and the demo shows it |
| **Backend developer** | Spring Boot services, APIs, database migrations, security, unit and integration tests | `mvn verify` green, API documented in the README, PR reviewed |
| **Frontend developer** | React + Redux app, JWT login flow, UI pages, component tests | UI works against the real API, React Testing Library tests green |
| **QA engineer** | Test plans, Postman collection, smoke scripts, E2E tests (Selenium), load tests (JMeter) | The happy path AND the failure cases are tested and written down |
| **DevOps engineer** | Docker, Docker Compose, Kubernetes, CI/CD pipelines, Terraform, cloud cost, monitoring | Deploys are repeatable from scripts or pipelines, and torn down after use |

How the roles work together in this repo:
- **PO** writes each session as a GitHub issue with a deliverable, and moves the card on the sprint board.
- **Developers** work on a `feature/...` branch and open a pull request that says `Closes #N`.
- **QA** checks the PR against the deliverable (smoke tests, failure demo) before merge.
- **DevOps** keeps `main` protected: no merge without green CI.

## Which role I play in each session

Lead = most of the session's work. Support = smaller part.

| # | Session | Lead role(s) | Support role(s) |
|---|---|---|---|
| 1 | Setup + monolith skeleton | DevOps (repo, Docker Compose, GitHub Actions, branch protection) | PO (scope, sprint board, issues) |
| 2 | User module: auth + RBAC | Backend (Spring Security, JWT, RBAC) | QA (JUnit, Mockito, web-layer tests, smoke test) |
| 3 | Product + order modules | Backend (catalog, orders, stock rules) | QA (Testcontainers, Postman, concurrency test) |
| 4 | Split into microservices | Backend (service split, Feign, compensation) + DevOps (Config Server, Eureka, Gateway, Compose) | QA (failure demo: stop one service) |
| 5 | Azure: AKS, PostgreSQL, Blob Storage | DevOps (ACR, AKS, Kubernetes manifests, teardown script) | Backend (image upload API) |
| 6 | Azure DevOps CI/CD + Terraform + SonarQube | DevOps (pipelines, Terraform, environments) | QA (quality gate, deploy-time measurement) |
| 7 | Frontend + end-to-end integration | Frontend (React, Redux, JWT login, shop UI) | QA (Selenium E2E, React Testing Library), Backend (OpenAPI docs) |
| 8 | Kafka events + real-time order status | Backend (outbox, producers, consumers) | Frontend (live status), QA (consumer stop/restart test) |
| 9 | Redis sessions + caching | Backend (token store, cache-aside, rate limiting) | QA (JMeter baseline) |
| 10 | Elasticsearch search + Spring Boot Admin | Backend (search index, sync from Kafka) | DevOps (Spring Boot Admin), Frontend (search box) |
| 11 | Logging, monitoring, alerting, resilience | DevOps (ELK, Prometheus, Grafana, alerts) | Backend (Resilience4j, refactor from the SonarQube report), QA (JMeter re-run) |
| 12 | AWS/GCP deploy with GitHub Actions + Terraform | DevOps (second cloud, Terraform, GitHub Actions deploy) | QA (smoke + E2E against the new URL) |
| 13 | Review, demo, resume | PO (demo story, README) | Every role: presents its part in the demo |

## For interviews

"I built it alone, but I worked like a team: every session started as an issue with a deliverable
(product owner), the code went through a branch and a pull request (developer), it wasn't merged
until the tests and a smoke or failure test passed (QA), and `main` only changes through green CI
(DevOps)."
