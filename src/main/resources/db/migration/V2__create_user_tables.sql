-- Session 2: user module tables. All live in the "users" schema owned by the user module.

CREATE TABLE users.users (
    id            BIGSERIAL    PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,          -- BCrypt hash, never the raw password
    full_name     VARCHAR(100) NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE users.roles (
    id   BIGSERIAL   PRIMARY KEY,
    name VARCHAR(30) NOT NULL UNIQUE
);

CREATE TABLE users.user_roles (
    user_id BIGINT NOT NULL REFERENCES users.users (id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES users.roles (id),
    PRIMARY KEY (user_id, role_id)
);

-- Refresh tokens are stored as SHA-256 hashes, so a database leak does not leak usable tokens.
-- Session 8 moves this to Redis.
CREATE TABLE users.refresh_tokens (
    id         BIGSERIAL   PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    user_id    BIGINT      NOT NULL REFERENCES users.users (id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user_id ON users.refresh_tokens (user_id);

INSERT INTO users.roles (name) VALUES ('USER'), ('ADMIN');
