CREATE TABLE dashboard (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    project_id UUID        NOT NULL UNIQUE REFERENCES projects(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE dashboard_widget (
    id           UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    dashboard_id UUID        NOT NULL REFERENCES dashboard(id) ON DELETE CASCADE,
    type         VARCHAR(40) NOT NULL,
    config       TEXT,
    grid_x       INT         NOT NULL DEFAULT 0,
    grid_y       INT         NOT NULL DEFAULT 0,
    grid_w       INT         NOT NULL DEFAULT 4,
    grid_h       INT         NOT NULL DEFAULT 4,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_dashboard_widget_dashboard ON dashboard_widget (dashboard_id);
