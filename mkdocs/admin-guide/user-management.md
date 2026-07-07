# User Management

System Admins can view all users and manage their account status from **Admin → Users**.

## Listing Users

The Users page shows every user's email, display name, system role, and status (**Active** / **Inactive**).

## Deactivating & Activating

Click **Deactivate** next to an active user, or **Activate** next to an inactive one.

Deactivation takes effect immediately:

- The user's session is invalidated — they cannot log in while inactive.
- All of their personal access tokens are revoked.
- All of their refresh tokens are revoked, ending any active sessions.

Reactivating a user restores login access. It does **not** restore previously revoked tokens — the user must issue new ones.

## Deleting a User

Click **Delete** next to a user, then confirm. Deletion is a **soft delete**:

- The account is deactivated (same effects as above: login blocked, tokens revoked).
- The user's personal data is anonymized — email, display name, avatar, password hash, and OAuth identity are cleared.
- The underlying record is kept (not removed) so existing issues, comments, and audit history remain intact and attributable to the anonymized account.

This action cannot be undone.

## Last Active Admin Protection

The last active System Admin cannot be deactivated or deleted, by yourself or by anyone else. At least one active admin must always remain able to log in and manage the instance. To remove the last admin, first promote another user to Admin.

## Users Deleting Their Own Account

Any user can delete their own account from **Account settings** (Account → Account). This uses the same soft-delete and anonymization behavior described above, and is subject to the same last-active-admin protection if the user is the sole admin.

See [Personal Access Tokens](../user-guide/access-tokens.md) for how token revocation interacts with account status.
