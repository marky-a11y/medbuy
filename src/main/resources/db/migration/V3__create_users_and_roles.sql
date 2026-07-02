-- Users table
CREATE TABLE media_buying.users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(100) NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    first_name      VARCHAR(100),
    last_name       VARCHAR(100),
    is_active       BOOLEAN      DEFAULT TRUE,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP
);

-- Roles table
CREATE TABLE media_buying.roles (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(50) NOT NULL UNIQUE
);

-- User-Roles join table
CREATE TABLE media_buying.user_roles (
    user_id         BIGINT NOT NULL REFERENCES media_buying.users(id),
    role_id         BIGINT NOT NULL REFERENCES media_buying.roles(id),
    PRIMARY KEY (user_id, role_id)
);
