CREATE TABLE labels (
    id         UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    project_id UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name       VARCHAR(50)  NOT NULL,
    color      VARCHAR(7)   NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (project_id, name)
);

CREATE TABLE issue_labels (
    issue_id  UUID NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    label_id  UUID NOT NULL REFERENCES labels(id) ON DELETE CASCADE,
    PRIMARY KEY (issue_id, label_id)
);
