CREATE TABLE media_buying.opportunity_metrics (
    id BIGSERIAL PRIMARY KEY,
    sector_name VARCHAR(50) NOT NULL,
    platform_name VARCHAR(50),
    market_demand_score NUMERIC(5,2),
    advertising_competition_score NUMERIC(5,2),
    local_market_saturation NUMERIC(5,2),
    average_estimated_cpc NUMERIC(10,2),
    search_growth NUMERIC(5,2),
    opportunity_index NUMERIC(5,2),
    computation_timestamp TIMESTAMP NOT NULL,
    data_sources_used VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_opportunity_sector ON media_buying.opportunity_metrics(sector_name);
CREATE INDEX idx_opportunity_timestamp ON media_buying.opportunity_metrics(computation_timestamp DESC);
