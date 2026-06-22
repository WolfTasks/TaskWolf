CREATE TABLE webhooks (
    id          UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    project_id  UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    url         VARCHAR(2048) NOT NULL,
    secret_hash VARCHAR(64)  NOT NULL,
    events      TEXT         NOT NULL,
    enabled     BOOLEAN      NOT NULL DEFAULT true,
    created_by  UUID         NOT NULL REFERENCES users(id),
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_webhooks_project ON webhooks (project_id, enabled);

CREATE TABLE webhook_deliveries (
    id              UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    webhook_id      UUID         NOT NULL REFERENCES webhooks(id) ON DELETE CASCADE,
    event_type      VARCHAR(100) NOT NULL,
    payload         TEXT         NOT NULL,
    response_status INTEGER,
    response_body   TEXT,
    attempt_count   INTEGER      NOT NULL DEFAULT 0,
    next_retry_at   TIMESTAMPTZ,
    delivered_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_webhook_deliveries_webhook   ON webhook_deliveries (webhook_id);
CREATE INDEX idx_webhook_deliveries_retry     ON webhook_deliveries (next_retry_at) WHERE delivered_at IS NULL;
