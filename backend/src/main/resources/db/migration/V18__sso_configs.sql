CREATE TABLE sso_configs (
    id                UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name              VARCHAR(100) NOT NULL,
    issuer_url        VARCHAR(500) NOT NULL,
    client_id         VARCHAR(255) NOT NULL,
    client_secret_enc VARCHAR(500) NOT NULL,
    enabled           BOOLEAN      NOT NULL DEFAULT true,
    auto_provision    BOOLEAN      NOT NULL DEFAULT true,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL
);
