-- ============================================================
-- V7: Create client_prospects table
-- Description: Stores prospect client companies for each sector
-- ============================================================

CREATE TABLE media_buying.client_prospects (
    id                  BIGSERIAL PRIMARY KEY,
    sector_id           BIGINT NOT NULL REFERENCES media_buying.commerce_sector(id),
    company_name        VARCHAR(255) NOT NULL,
    est_annual_revenue  NUMERIC(15,2) NOT NULL,
    yoy_growth_rate     NUMERIC(5,2) NOT NULL,
    est_ad_budget       NUMERIC(15,2) NOT NULL,
    industry_vertical   VARCHAR(100),
    notes               TEXT,
    is_active           BOOLEAN DEFAULT TRUE,
    last_updated        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_client_prospects_sector ON media_buying.client_prospects(sector_id);
CREATE INDEX idx_client_prospects_sector_budget ON media_buying.client_prospects(sector_id, est_ad_budget DESC);
CREATE INDEX idx_client_prospects_sector_revenue ON media_buying.client_prospects(sector_id, est_annual_revenue DESC);
CREATE INDEX idx_client_prospects_sector_growth ON media_buying.client_prospects(sector_id, yoy_growth_rate DESC);
