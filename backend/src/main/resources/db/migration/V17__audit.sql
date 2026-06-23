CREATE TABLE audit_events (
    id            UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    timestamp     TIMESTAMPTZ NOT NULL DEFAULT now(),
    user_id       UUID        REFERENCES users(id) ON DELETE SET NULL,
    user_email    VARCHAR(255) NOT NULL,
    project_id    UUID        REFERENCES projects(id) ON DELETE SET NULL,
    action        VARCHAR(100) NOT NULL,
    level         VARCHAR(20)  NOT NULL,
    resource_type VARCHAR(50),
    resource_id   VARCHAR(100),
    details       JSONB,
    ip_address    VARCHAR(45),
    user_agent    VARCHAR(500)
);
CREATE INDEX idx_audit_timestamp   ON audit_events (timestamp DESC);
CREATE INDEX idx_audit_project     ON audit_events (project_id, timestamp DESC);
CREATE INDEX idx_audit_user        ON audit_events (user_id, timestamp DESC);

CREATE TABLE audit_config (
    level   VARCHAR(20) NOT NULL PRIMARY KEY,
    enabled BOOLEAN     NOT NULL DEFAULT false
);
INSERT INTO audit_config (level, enabled) VALUES ('SECURITY', true), ('WRITE', false), ('ALL', false);
