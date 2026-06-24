# Audit Logs

Audit logs record security events, data changes, and (optionally) read access.

## Log Levels

| Level | Always active | Example events |
|---|---|---|
| `SECURITY` | Yes | Login, logout, password change, role change, API key created/deleted |
| `WRITE` | Configurable | Issue created/updated/deleted, sprint started, member added |
| `ALL` | Configurable | Issue viewed, board opened, report viewed |

Configure levels at **Admin → Audit → Configuration**.

## Viewing Logs

**Admin → Audit** shows the full audit log with filters:

- Date range
- User
- Action type
- Level

**Project Admins** can view project-scoped audit logs from **Project Settings → Audit**.

## Exporting

Click **Export** on the audit log view and choose **JSON** or **CSV**.

The CSV export uses OWASP-recommended formula-injection escaping — safe to open directly in Excel or Google Sheets.

## Retention

Audit events are stored in the database with no automatic expiry. Archive old events via the JSON export and delete directly in PostgreSQL if needed.
