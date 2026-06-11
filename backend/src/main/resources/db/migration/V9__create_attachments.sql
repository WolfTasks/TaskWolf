CREATE TABLE attachments (
    id            UUID         NOT NULL PRIMARY KEY,
    issue_id      UUID         NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    uploader_id   UUID         NOT NULL REFERENCES users(id),
    filename      VARCHAR(255) NOT NULL,
    stored_name   VARCHAR(255) NOT NULL,
    content_type  VARCHAR(127) NOT NULL,
    size          BIGINT       NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL
);
