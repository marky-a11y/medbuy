CREATE TABLE media_buying.kpi_source_attribution (
    id                  BIGSERIAL PRIMARY KEY,
    kpi_metrics_id      BIGINT NOT NULL REFERENCES media_buying.kpi_metrics(id),
    data_source_id      BIGINT NOT NULL REFERENCES media_buying.data_source(id),
    attribution_context VARCHAR(50) NOT NULL CHECK (attribution_context IN ('RAW','INTERPOLATED','DERIVED')),
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_kpi_source UNIQUE (kpi_metrics_id, data_source_id)
);

CREATE INDEX idx_kpi_source_kpi ON media_buying.kpi_source_attribution(kpi_metrics_id);
CREATE INDEX idx_kpi_source_source ON media_buying.kpi_source_attribution(data_source_id);

-- Seed 5 data sources matching the platforms
INSERT INTO media_buying.data_source (platform_id, source_name, source_type, source_url, license_type) VALUES
(1, 'Google Ads API v17', 'API', 'https://developers.google.com/google-ads/api', 'PROPRIETARY'),
(2, 'Meta Marketing API', 'API', 'https://developers.facebook.com/docs/marketing-apis/', 'PROPRIETARY'),
(3, 'TikTok Ads Manager API', 'API', 'https://ads.tiktok.com/marketing_api/docs', 'PROPRIETARY'),
(4, 'LinkedIn Marketing API', 'API', 'https://learn.microsoft.com/en-us/linkedin/marketing/', 'PROPRIETARY'),
(5, 'iHeart Media (estimated)', 'REPORT', 'https://www.iheartmedia.com/advertise', 'PROPRIETARY');
