CREATE SCHEMA IF NOT EXISTS media_buying;

-- Platform table
CREATE TABLE media_buying.platform (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(50)  NOT NULL UNIQUE,
    display_name    VARCHAR(100) NOT NULL,
    api_reference   VARCHAR(500),
    logo_url        VARCHAR(1000),
    is_active       BOOLEAN      DEFAULT TRUE,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP
);
CREATE INDEX idx_platform_active ON media_buying.platform(is_active);

-- Commerce Sector table
CREATE TABLE media_buying.commerce_sector (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(50)  NOT NULL UNIQUE,
    display_name    VARCHAR(100) NOT NULL,
    is_active       BOOLEAN      DEFAULT TRUE,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP
);
CREATE INDEX idx_sector_active ON media_buying.commerce_sector(is_active);
