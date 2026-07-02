-- ============================================================
-- V11: Create client table for Client Portfolio Grid (BACK-19)
-- Description: Stores client information for the media buying
-- dashboard's client portfolio grid section.
-- ============================================================

CREATE TABLE media_buying.client (
    id                  BIGSERIAL PRIMARY KEY,
    client_name         VARCHAR(255) NOT NULL,
    sector_id           BIGINT NOT NULL REFERENCES media_buying.commerce_sector(id),
    contract_type       VARCHAR(50) NOT NULL CHECK (contract_type IN ('RETAINER','PERFORMANCE','HYBRID')),
    contract_value      NUMERIC(12,2),
    contract_start_date DATE,
    contract_end_date   DATE,
    satisfaction_rating INTEGER CHECK (satisfaction_rating BETWEEN 1 AND 5),
    growth_yoy_pct      NUMERIC(5,2),
    engagement_score    INTEGER CHECK (engagement_score BETWEEN 0 AND 100),
    outlook_score       INTEGER CHECK (outlook_score BETWEEN 0 AND 100),
    is_active           BOOLEAN DEFAULT TRUE,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP
);

CREATE INDEX idx_client_sector ON media_buying.client(sector_id);
CREATE INDEX idx_client_outlook ON media_buying.client(outlook_score DESC);
