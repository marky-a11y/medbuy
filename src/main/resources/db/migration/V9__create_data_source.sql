CREATE TABLE media_buying.data_source (
    id              BIGSERIAL PRIMARY KEY,
    platform_id     BIGINT NOT NULL REFERENCES media_buying.platform(id),
    source_type     VARCHAR(50) NOT NULL CHECK (source_type IN ('API','DOCUMENTATION','REPORT','MANUAL')),
    source_url      VARCHAR(1000) NOT NULL,
    source_name     VARCHAR(255) NOT NULL,
    license_type    VARCHAR(50) NOT NULL CHECK (license_type IN ('PROPRIETARY','PUBLIC','OPEN')),
    last_verified_at TIMESTAMP,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_data_source_platform ON media_buying.data_source(platform_id);
