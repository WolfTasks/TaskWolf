CREATE TABLE comments (
    id           UUID         NOT NULL PRIMARY KEY,
    body         TEXT         NOT NULL,
    issue_id     UUID         NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    author_id    UUID         NOT NULL REFERENCES users(id),
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,
    edited_at    TIMESTAMP,
    deleted_at   TIMESTAMP
);

CREATE TABLE issue_activities (
    id          UUID         NOT NULL PRIMARY KEY,
    issue_id    UUID         NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    actor_id    UUID         NOT NULL REFERENCES users(id),
    type        VARCHAR(40)  NOT NULL,
    comment_id  UUID         REFERENCES comments(id) ON DELETE SET NULL,
    old_value   TEXT,
    new_value   TEXT,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL
);

CREATE INDEX idx_issue_activity_issue ON issue_activities (issue_id, created_at DESC);
