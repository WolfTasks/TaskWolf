-- backend/src/main/resources/db/migration/V24__versions.sql
CREATE TABLE versions (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    project_id UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name       VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (project_id, name)
);

CREATE TABLE issue_versions (
    issue_id   UUID       NOT NULL REFERENCES issues(id)   ON DELETE CASCADE,
    version_id UUID       NOT NULL REFERENCES versions(id) ON DELETE CASCADE,
    type       VARCHAR(8) NOT NULL CHECK (type IN ('FIX', 'AFFECTS')),
    PRIMARY KEY (issue_id, version_id, type)
);
