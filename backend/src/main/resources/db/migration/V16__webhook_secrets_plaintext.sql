-- Outgoing webhooks: rename secret_hash to secret (store plaintext)
ALTER TABLE webhooks RENAME COLUMN secret_hash TO secret;

-- Incoming integrations: rename webhook_secret_hash to webhook_secret (store plaintext)
ALTER TABLE project_integrations RENAME COLUMN webhook_secret_hash TO webhook_secret;
