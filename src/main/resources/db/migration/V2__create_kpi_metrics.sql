CREATE TABLE media_buying.kpi_metrics (
    id                      BIGSERIAL PRIMARY KEY,
    platform_id             BIGINT NOT NULL REFERENCES media_buying.platform(id),
    sector_id               BIGINT NOT NULL REFERENCES media_buying.commerce_sector(id),
    -- Composite Score Inputs (6 KPIs)
    roas                    NUMERIC(12,2),
    cac                     NUMERIC(12,2),
    cltv                    NUMERIC(12,2),
    conversion_rate         NUMERIC(7,4),
    scalability             NUMERIC(14,2),
    attribution_accuracy    NUMERIC(7,4),
    -- Extended KPI Columns
    contribution_margin     NUMERIC(12,2),
    payback_period          NUMERIC(8,2),
    incremental_return      NUMERIC(12,2),
    cost_per_qualified_lead NUMERIC(12,2),
    cash_conversion_cycle   NUMERIC(8,2),
    saturation_point        NUMERIC(7,4),
    -- Metadata
    ingestion_timestamp     TIMESTAMP,
    data_source             VARCHAR(50),
    created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP,
    CONSTRAINT uq_platform_sector UNIQUE(platform_id, sector_id)
);

CREATE INDEX idx_kpi_platform_sector ON media_buying.kpi_metrics(platform_id, sector_id);
CREATE INDEX idx_kpi_ingestion ON media_buying.kpi_metrics(ingestion_timestamp DESC);
