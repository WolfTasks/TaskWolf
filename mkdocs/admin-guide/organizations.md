# Organizations

Organizations provide multi-tenancy — each org is an isolated namespace for projects and users.

## Creating an Organization

System Admin only. Go to **Admin → Organizations → New Organization**.

Each org has a unique **slug** used in API paths.

## Switching Organizations

Users with membership in multiple orgs see an **Org Switcher** in the top navigation. Switching sets the active org context for all subsequent actions.

## Managing Members

From **Admin → Organizations → [Org Name] → Members**:

- **Add Member** — enter user ID and select role (Owner, Admin, Member)
- **Remove Member** — click the remove button next to a member

### Member Roles

| Role | Capabilities |
|---|---|
| Owner | Full control, can delete the org |
| Admin | Manage members and projects |
| Member | Access org projects |

## Data Isolation

All projects, issues, and data belong to an org. Users can only access resources within their active org context. System Admins can access any org.
