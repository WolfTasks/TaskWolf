CREATE TABLE automation_rules (
    id              UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    project_id      UUID         REFERENCES projects(id) ON DELETE CASCADE,
    scope           VARCHAR(10)  NOT NULL,
    name            VARCHAR(255) NOT NULL,
    trigger_type    VARCHAR(50)  NOT NULL,
    trigger_payload TEXT,
    enabled         BOOLEAN      NOT NULL DEFAULT true,
    created_by      UUID         NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_automation_rules_trigger ON automation_rules (trigger_type, scope, enabled);
CREATE INDEX idx_automation_rules_project ON automation_rules (project_id);

CREATE TABLE rule_condition_groups (
    id              UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    rule_id         UUID        NOT NULL REFERENCES automation_rules(id) ON DELETE CASCADE,
    parent_group_id UUID        REFERENCES rule_condition_groups(id) ON DELETE CASCADE,
    logic           VARCHAR(3)  NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_rule_condition_groups_rule ON rule_condition_groups (rule_id);

CREATE TABLE rule_conditions (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    group_id   UUID        NOT NULL REFERENCES rule_condition_groups(id) ON DELETE CASCADE,
    type       VARCHAR(50) NOT NULL,
    operator   VARCHAR(20) NOT NULL,
    params     TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE rule_actions (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    rule_id    UUID        NOT NULL REFERENCES automation_rules(id) ON DELETE CASCADE,
    position   INT         NOT NULL,
    type       VARCHAR(50) NOT NULL,
    params     TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_rule_actions_rule ON rule_actions (rule_id);
