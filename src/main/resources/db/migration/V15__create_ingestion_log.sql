CREATE TABLE media_buying.ingestion_log (
    id BIGSERIAL PRIMARY KEY,
    cycle_id UUID NOT NULL,
    source_name VARCHAR(50) NOT NULL,
    fetch_status VARCHAR(20) NOT NULL,
    source_type VARCHAR(10),
    record_count INT DEFAULT 0,
    normalized_summary VARCHAR(500),
    error_message VARCHAR(500),
    ingestion_timestamp TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_ingestion_log_cycle ON media_buying.ingestion_log(cycle_id);
CREATE INDEX idx_ingestion_log_timestamp ON media_buying.ingestion_log(ingestion_timestamp DESC);
