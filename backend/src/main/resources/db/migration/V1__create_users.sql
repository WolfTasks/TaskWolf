CREATE TABLE users (
    id          UUID        NOT NULL PRIMARY KEY,
    email       VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    avatar_url  VARCHAR(512),
    password_hash VARCHAR(255),
    oauth_provider VARCHAR(50),
    oauth_subject VARCHAR(255),
    system_role VARCHAR(50) NOT NULL DEFAULT 'MEMBER',
    created_at  TIMESTAMP   NOT NULL,
    updated_at  TIMESTAMP   NOT NULL
);

CREATE INDEX idx_users_email ON users(email);
