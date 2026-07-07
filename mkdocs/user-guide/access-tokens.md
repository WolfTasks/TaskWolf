# Personal Access Tokens

Personal access tokens let you authenticate API requests as yourself, without using your password or browser session — useful for scripts, CI jobs, and CLI tools.

## Creating a Token

1. Open **Account → Access Tokens** in the sidebar
2. Click **Create Token**
3. Give it a descriptive name (e.g. "My CLI")
4. Choose a **scope**:
   - **Read & Write** — full access, same permissions as your user account
   - **Read-only** — only `GET` requests are allowed; any write request is rejected with `403`
5. Choose an **expiry**: 30 days, 60 days, 90 days, or Never
6. Click **Create** — the token's plaintext value is shown **once**

## Copy It Now

The plaintext value (prefixed `twk_…`) is only ever displayed at creation time. Copy it immediately — it cannot be retrieved again. If you lose it, revoke the token and create a new one.

## Using a Token

Send it as a bearer token on any API request:

```
Authorization: Bearer twk_XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
```

The token acts with your own permissions — it can access anything your user account can access, subject to its scope. There is no separate authorization model to configure.

## Revoking a Token

On the **Access Tokens** page, click **Revoke** next to any token. Revocation is immediate — the token stops authenticating on the very next request.

Deactivating or deleting your account (see [User Management](../admin-guide/user-management.md)) also immediately revokes all of your personal access tokens.

## Personal Access Tokens vs. Project API Keys

| | Personal Access Token | Project API Key |
|---|---|---|
| Prefix | `twk_` | `tw_` |
| Scope | Your user account (Read-only or Read & Write) | A single project |
| Created at | Account → Access Tokens | Project Settings → API Keys |
| Permissions | Same as your own user permissions | Fixed to the project it was created for |

Use a personal access token for scripts that act as you across the whole application. Use a project API key when you want to scope access to a single project, independent of any one user's account.
