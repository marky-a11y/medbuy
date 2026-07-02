CREATE TABLE media_buying.scoring_weights (
    id              BIGSERIAL PRIMARY KEY,
    kpi_name        VARCHAR(50)  NOT NULL UNIQUE,
    weight          NUMERIC(5,4) NOT NULL CHECK (weight BETWEEN 0.0 AND 1.0),
    updated_by      BIGINT REFERENCES media_buying.users(id),
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
