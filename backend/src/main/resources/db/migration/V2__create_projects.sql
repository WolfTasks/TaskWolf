CREATE TABLE projects (
    id          UUID        NOT NULL PRIMARY KEY,
    "key"       VARCHAR(10)  NOT NULL UNIQUE,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    owner_id    UUID        NOT NULL REFERENCES users(id),
    archived    BOOLEAN     NOT NULL DEFAULT FALSE,
    node_id     VARCHAR(100),
    created_at  TIMESTAMP   NOT NULL,
    updated_at  TIMESTAMP   NOT NULL
);

CREATE TABLE project_members (
    id          UUID        NOT NULL PRIMARY KEY,
    project_id  UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role        VARCHAR(50) NOT NULL DEFAULT 'MEMBER',
    created_at  TIMESTAMP   NOT NULL,
    updated_at  TIMESTAMP   NOT NULL,
    UNIQUE (project_id, user_id)
);

CREATE INDEX idx_projects_key ON projects("key");
CREATE INDEX idx_project_members_project ON project_members(project_id);
CREATE INDEX idx_project_members_user ON project_members(user_id);
