CREATE TABLE workflows (
    id          UUID        NOT NULL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    project_id  UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    is_default  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP   NOT NULL,
    updated_at  TIMESTAMP   NOT NULL
);

CREATE TABLE workflow_statuses (
    id          UUID        NOT NULL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    category    VARCHAR(20)  NOT NULL,
    color       VARCHAR(7)   NOT NULL DEFAULT '#6c8fef',
    position    INT          NOT NULL DEFAULT 0,
    workflow_id UUID        NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
    created_at  TIMESTAMP   NOT NULL,
    updated_at  TIMESTAMP   NOT NULL
);

CREATE TABLE workflow_transitions (
    id              UUID NOT NULL PRIMARY KEY,
    workflow_id     UUID NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
    from_status_id  UUID REFERENCES workflow_statuses(id) ON DELETE CASCADE,
    to_status_id    UUID NOT NULL REFERENCES workflow_statuses(id) ON DELETE CASCADE,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL
);

ALTER TABLE projects ADD COLUMN workflow_id UUID REFERENCES workflows(id);

CREATE INDEX idx_workflows_project ON workflows(project_id);
CREATE INDEX idx_workflow_statuses_workflow ON workflow_statuses(workflow_id);
