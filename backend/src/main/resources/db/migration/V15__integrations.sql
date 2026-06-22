CREATE TABLE project_integrations (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    project_id          UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    provider            VARCHAR(20)  NOT NULL,
    webhook_secret_hash VARCHAR(64)  NOT NULL,
    repo_url            VARCHAR(2048),
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    UNIQUE (project_id, provider)
);

CREATE TABLE issue_refs (
    id          UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    issue_id    UUID         NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    provider    VARCHAR(20)  NOT NULL,
    ref_type    VARCHAR(20)  NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    url         VARCHAR(2048) NOT NULL,
    title       VARCHAR(1024),
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    UNIQUE (issue_id, provider, ref_type, external_id)
);

CREATE INDEX idx_issue_refs_issue ON issue_refs (issue_id);
