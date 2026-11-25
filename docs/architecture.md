# Architecture (modular monolith)

One Spring Boot app, three modules, one Postgres database with one schema per module.
GitHub renders the Mermaid diagrams below automatically.

## Module diagram

```mermaid
flowchart LR
    client[Client / Postman] --> app

    subgraph app[ecommerce-platform - one Spring Boot app]
        direction TB
        user[user module<br/>api + internal]
        product[product module<br/>api + internal]
        order[order module<br/>api + internal]
        order -- "uses user.api" --> user
        order -- "uses product.api" --> product
    end

    subgraph db[PostgreSQL]
        s1[(schema: users)]
        s2[(schema: catalog)]
        s3[(schema: ordering)]
    end

    user --> s1
    product --> s2
    order --> s3
```

Rules:
- A module only reads and writes its own schema.
- Other modules call it only through its `api` package. `ModuleBoundariesTest` enforces this.
- In Session 4 each module becomes its own service, and each schema its own database.

## User module classes (Session 2)

```mermaid
classDiagram
    direction LR

    class User {
        Long id
        String email
        String passwordHash
        String fullName
        boolean enabled
        Instant createdAt
        addRole(Role)
        roleNames() List~String~
    }
    class Role {
        Long id
        RoleName name
    }
    class RoleName {
        <<enumeration>>
        USER
        ADMIN
    }
    class RefreshToken {
        Long id
        String tokenHash
        Instant expiresAt
        boolean revoked
        isExpired(Instant) boolean
        revoke()
    }

    class AuthController {
        register(RegisterRequest) UserResponse
        login(LoginRequest) TokenResponse
        refresh(RefreshRequest) TokenResponse
        logout(RefreshRequest)
    }
    class UserController {
        me(Jwt) UserResponse
    }
    class AdminController {
        listUsers() List~UserResponse~
    }
    class AuthService {
        register()
        login()
        refresh()
        logout()
    }
    class TokenService {
        createAccessToken(User) String
        newRefreshToken() String
        sha256(String) String
    }

    User "*" -- "*" Role : user_roles
    Role --> RoleName
    RefreshToken "*" --> "1" User

    AuthController --> AuthService
    AuthService --> TokenService
    AuthService --> UserRepository
    AuthService --> RefreshTokenRepository
    UserController --> UserRepository
    AdminController --> UserRepository
```

Tables (schema `users`): `users`, `roles`, `user_roles`, `refresh_tokens` - see `V2__create_user_tables.sql`.

## Product and order classes (draft, built in Session 3)

```mermaid
classDiagram
    class Product {
        Long id
        String name
        BigDecimal price
        Long categoryId
    }
    class Category {
        Long id
        String name
    }
    class Inventory {
        Long productId
        int available
        int reserved
    }
    class Order {
        Long id
        Long userId
        OrderStatus status
        Instant createdAt
    }
    class OrderItem {
        Long productId
        int quantity
        BigDecimal unitPrice
    }

    Category "1" -- "*" Product
    Product "1" -- "1" Inventory
    Order "1" *-- "*" OrderItem
```

Note: `Order.userId` and `OrderItem.productId` are plain IDs, not JPA relationships across modules.
That keeps the modules separable.

## Testing strategy

| Level | Example | What it checks | Speed |
|---|---|---|---|
| Unit (JUnit + Mockito) | `AuthServiceTest` | Business rules, e.g. refresh-token reuse revokes all sessions | milliseconds |
| Web slice (`@WebMvcTest`) | `UserWebLayerTest` | URLs, JSON, validation errors, status codes, security rules; service mocked | ~2 s |
| Integration (`@SpringBootTest` + Testcontainers) | `AuthFlowIntegrationTest` | Whole flow against a real Postgres | ~20 s |
| Architecture (ArchUnit) | `ModuleBoundariesTest` | No module reaches into another module's `internal` package | ~1 s |

## Login and token refresh (Session 2)

```mermaid
sequenceDiagram
    actor C as Client
    participant A as AuthController / AuthService
    participant DB as Postgres (users schema)
    participant API as Protected endpoint

    C->>A: POST /api/auth/login {email, password}
    A->>DB: find user by email, check BCrypt hash
    A->>DB: save SHA-256(refresh token)
    A-->>C: accessToken (JWT, 15 min) + refreshToken (7 days)

    C->>API: GET /api/users/me  Authorization: Bearer accessToken
    API->>API: verify signature, expiry and issuer, map roles claim to ROLE_*
    API-->>C: 200 profile

    C->>A: POST /api/auth/refresh {refreshToken}
    A->>DB: find by hash, revoke it, save a new one
    A-->>C: new accessToken + new refreshToken
    Note over A,DB: Reusing an old refresh token revokes ALL of the user's tokens
```

Security decisions:
- **Stateless JWT access tokens** (HS256): any instance can verify them without a database lookup,
  which matters once the app is split into services (Session 4).
- **Short access-token life + revocable refresh tokens**: a stolen access token works for at most
  15 minutes; refresh tokens can be revoked (logout) and are rotated on every use.
- **Only hashes are stored**: BCrypt for passwords, SHA-256 for refresh tokens.
- **Roles** live in the `roles` claim and become Spring authorities `ROLE_USER` / `ROLE_ADMIN`.
  `/api/admin/**` is protected by a URL rule and by `@PreAuthorize`.
