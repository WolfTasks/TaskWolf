# Configuration Reference

All configuration is done via environment variables. Set them in your `.env` file when using `docker-compose.prod.yml`.

## Required

| Variable | Description |
|---|---|
| `TW_JWT_SECRET` | JWT signing secret — minimum 32 characters. Generate with `openssl rand -hex 32`. |

## Database

| Variable | Default | Description |
|---|---|---|
| `TW_DB_URL` | `jdbc:postgresql://db:5432/taskowolf` | JDBC connection URL |
| `TW_DB_USER` | `taskowolf` | Database username |
| `TW_DB_PASS` | — | Database password. **Required in production.** |

## Application

| Variable | Default | Description |
|---|---|---|
| `TW_BASE_URL` | `http://localhost` | Public base URL used in email links and OAuth2 redirect URIs |
| `TW_STORAGE_PATH` | `/data/attachments` | Path inside the container for file attachments |
| `SPRING_PROFILES_ACTIVE` | `prod` | Spring profile. Use `prod` for production. |

## OAuth2 (optional)

| Variable | Description |
|---|---|
| `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GITHUB_CLIENT_ID` | GitHub OAuth App client ID |
| `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GITHUB_CLIENT_SECRET` | GitHub OAuth App client secret |
| `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID` | Google OAuth client ID |
| `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET` | Google OAuth client secret |

## Email / IMAP (optional, Service Desk)

| Variable | Default | Description |
|---|---|---|
| `TW_MAIL_HOST` | — | SMTP host for outgoing emails |
| `TW_MAIL_PORT` | `587` | SMTP port |
| `TW_MAIL_USER` | — | SMTP username |
| `TW_MAIL_PASS` | — | SMTP password |
| `TW_IMAP_HOST` | — | IMAP host for email-to-ticket ingestion |
| `TW_IMAP_USER` | — | IMAP username |
| `TW_IMAP_PASS` | — | IMAP password |
| `TW_IMAP_FOLDER` | `INBOX` | IMAP folder to poll |

## Audit Logging

Audit levels are configured at runtime via the Admin UI (`/admin/audit/config`), not via environment variables.
