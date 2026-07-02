CREATE TABLE media_buying.audit_log (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT REFERENCES media_buying.users(id),
    action          VARCHAR(100) NOT NULL,
    entity_type     VARCHAR(50),
    entity_id       VARCHAR(100),
    details         JSONB,
    correlation_id  VARCHAR(36),
    ip_address      VARCHAR(45),
    timestamp       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_user ON media_buying.audit_log(user_id);
CREATE INDEX idx_audit_timestamp ON media_buying.audit_log(timestamp DESC);
CREATE INDEX idx_audit_action ON media_buying.audit_log(action);
