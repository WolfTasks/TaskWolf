CREATE TABLE custom_field_definitions (
    id         UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    project_id UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name       VARCHAR(100) NOT NULL,
    type       VARCHAR(10)  NOT NULL CHECK (type IN ('TEXT','NUMBER','DATE','DROPDOWN','CHECKBOX')),
    required   BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order INT          NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (project_id, name)
);

CREATE TABLE custom_field_options (
    id         UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    field_id   UUID         NOT NULL REFERENCES custom_field_definitions(id) ON DELETE CASCADE,
    label      VARCHAR(100) NOT NULL,
    sort_order INT          NOT NULL DEFAULT 0,
    UNIQUE (field_id, label)
);

CREATE TABLE custom_field_values (
    id            UUID    NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    issue_id      UUID    NOT NULL REFERENCES issues(id)                   ON DELETE CASCADE,
    field_id      UUID    NOT NULL REFERENCES custom_field_definitions(id) ON DELETE CASCADE,
    text_value    TEXT,
    number_value  NUMERIC,
    date_value    DATE,
    boolean_value BOOLEAN,
    option_id     UUID    REFERENCES custom_field_options(id) ON DELETE SET NULL,
    UNIQUE (issue_id, field_id)
);
