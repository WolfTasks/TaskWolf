CREATE TABLE sprints (
    id               UUID         NOT NULL PRIMARY KEY,
    name             VARCHAR(255) NOT NULL,
    goal             TEXT,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PLANNED',
    start_date       DATE,
    end_date         DATE,
    planned_points   INT,
    completed_points INT,
    project_id       UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP    NOT NULL
);

CREATE INDEX idx_sprints_project ON sprints(project_id);

ALTER TABLE issues ADD CONSTRAINT fk_issues_sprint
    FOREIGN KEY (sprint_id) REFERENCES sprints(id) ON DELETE SET NULL;

CREATE INDEX idx_issues_sprint ON issues(sprint_id);
