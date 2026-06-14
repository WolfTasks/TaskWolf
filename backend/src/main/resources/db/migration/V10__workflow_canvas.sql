ALTER TABLE workflow_transitions ADD COLUMN guards TEXT;

CREATE TABLE workflow_status_positions (
    workflow_id UUID NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
    status_id   UUID NOT NULL REFERENCES workflow_statuses(id) ON DELETE CASCADE,
    x           INT  NOT NULL DEFAULT 0,
    y           INT  NOT NULL DEFAULT 0,
    PRIMARY KEY (workflow_id, status_id)
);
