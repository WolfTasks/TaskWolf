ALTER TABLE issues ADD COLUMN sla_start_time TIMESTAMPTZ;

CREATE TABLE service_desks (
    id            UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    project_id    UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    email_address VARCHAR(255),
    enabled       BOOLEAN      NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL
);

CREATE TABLE sla_policies (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    service_desk_id     UUID         NOT NULL REFERENCES service_desks(id) ON DELETE CASCADE,
    name                VARCHAR(100) NOT NULL,
    priority            VARCHAR(20)  NOT NULL,
    response_minutes    INT          NOT NULL,
    resolution_minutes  INT          NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL
);

CREATE TABLE escalation_rules (
    id                     UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    sla_policy_id          UUID        NOT NULL REFERENCES sla_policies(id) ON DELETE CASCADE,
    escalate_after_minutes INT         NOT NULL,
    assignee_id            UUID        REFERENCES users(id),
    notify_user_ids        UUID[]      NOT NULL DEFAULT '{}',
    created_at             TIMESTAMPTZ NOT NULL,
    updated_at             TIMESTAMPTZ NOT NULL
);

CREATE TABLE incidents (
    id                   UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    issue_id             UUID        NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    severity             VARCHAR(5)  NOT NULL,
    on_call_assignee_id  UUID        REFERENCES users(id),
    postmortem_body      TEXT,
    resolved_at          TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL
);
