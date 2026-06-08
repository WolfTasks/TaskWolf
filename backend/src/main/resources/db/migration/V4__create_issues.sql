CREATE TABLE issues (
    id              UUID        NOT NULL PRIMARY KEY,
    "key"           VARCHAR(20)  NOT NULL UNIQUE,
    key_number      INT          NOT NULL,
    title           VARCHAR(500) NOT NULL,
    description     TEXT,
    type            VARCHAR(20)  NOT NULL DEFAULT 'TASK',
    priority        VARCHAR(20)  NOT NULL DEFAULT 'MEDIUM',
    story_points    INT,
    status_id       UUID        NOT NULL REFERENCES workflow_statuses(id),
    project_id      UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    assignee_id     UUID        REFERENCES users(id),
    reporter_id     UUID        NOT NULL REFERENCES users(id),
    sprint_id       UUID,
    parent_id       UUID        REFERENCES issues(id),
    due_date        DATE,
    created_at      TIMESTAMP   NOT NULL,
    updated_at      TIMESTAMP   NOT NULL
);

CREATE TABLE issue_links (
    id              UUID NOT NULL PRIMARY KEY,
    from_issue_id   UUID NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    to_issue_id     UUID NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    link_type       VARCHAR(30) NOT NULL,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL,
    UNIQUE(from_issue_id, to_issue_id, link_type)
);

CREATE INDEX idx_issues_project ON issues(project_id);
CREATE INDEX idx_issues_assignee ON issues(assignee_id);
CREATE INDEX idx_issues_status ON issues(status_id);
CREATE INDEX idx_issues_parent ON issues(parent_id);
CREATE INDEX idx_issue_links_from ON issue_links(from_issue_id);
CREATE INDEX idx_issue_links_to ON issue_links(to_issue_id);
