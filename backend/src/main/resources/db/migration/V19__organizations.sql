CREATE TABLE organizations (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    slug       VARCHAR(50)  NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE organization_members (
    org_id  UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role    VARCHAR(20) NOT NULL,
    PRIMARY KEY (org_id, user_id)
);

-- Additive org_id columns (nullable, no FK until populated):
ALTER TABLE users             ADD COLUMN org_id UUID REFERENCES organizations(id);
ALTER TABLE projects          ADD COLUMN org_id UUID REFERENCES organizations(id);
ALTER TABLE api_keys          ADD COLUMN org_id UUID REFERENCES organizations(id);
ALTER TABLE webhooks          ADD COLUMN org_id UUID REFERENCES organizations(id);
ALTER TABLE project_integrations ADD COLUMN org_id UUID REFERENCES organizations(id);
ALTER TABLE audit_events      ADD COLUMN org_id UUID REFERENCES organizations(id);

-- Create default org and backfill:
INSERT INTO organizations (id, name, slug, created_at, updated_at)
  VALUES (gen_random_uuid(), 'Default', 'default', now(), now());

UPDATE users             SET org_id = (SELECT id FROM organizations WHERE slug = 'default');
UPDATE projects          SET org_id = (SELECT id FROM organizations WHERE slug = 'default');
UPDATE api_keys          SET org_id = (SELECT id FROM organizations WHERE slug = 'default');
UPDATE webhooks          SET org_id = (SELECT id FROM organizations WHERE slug = 'default');
UPDATE project_integrations SET org_id = (SELECT id FROM organizations WHERE slug = 'default');
UPDATE audit_events      SET org_id = (SELECT id FROM organizations WHERE slug = 'default');
