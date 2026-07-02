CREATE TABLE media_buying.companies (
    id BIGSERIAL PRIMARY KEY,
    company_name VARCHAR(255) NOT NULL,
    sector_id BIGINT NOT NULL REFERENCES media_buying.commerce_sector(id),
    primary_platform VARCHAR(50),
    source_name VARCHAR(50),
    confidence NUMERIC(5,4),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    UNIQUE(company_name, sector_id)
);

CREATE INDEX idx_companies_sector ON media_buying.companies(sector_id);
CREATE INDEX idx_companies_platform ON media_buying.companies(primary_platform);
