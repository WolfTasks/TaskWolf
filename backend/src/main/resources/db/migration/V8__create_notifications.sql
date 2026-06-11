CREATE TABLE notifications (
    id         UUID         NOT NULL PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type       VARCHAR(30)  NOT NULL,
    title      VARCHAR(255) NOT NULL,
    body       TEXT,
    link       VARCHAR(500),
    read       BOOLEAN      NOT NULL DEFAULT false,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL
);

CREATE INDEX idx_notifications_user ON notifications (user_id, read, created_at DESC);
