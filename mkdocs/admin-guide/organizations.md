# Organizations

Organizations provide multi-tenancy — each org is an isolated namespace for projects and users.

## Creating an Organization

System Admin only. Go to **Admin → Organizations → New Organization**.

Each org has a unique **slug** used in API paths.

## Switching Organizations

Users with membership in multiple orgs see an **Org Switcher** in the top navigation. Switching sets the active org context for all subsequent actions.

## Managing Members

Organization **Owners** and **Admins** manage their own organization — you no
longer need to be a System Admin. Open **Organizations**, pick your org, and use
its **Settings** page. (System Admins can manage every org and additionally see a
global "All Organizations" list.)

- **Add Member** — search by name or email, pick the user, choose a role, and
  add. (No more raw user IDs.)
- **Change Role** — use the role dropdown next to a member.
- **Remove Member** — click **Remove** next to a member.

### Member Roles

| Role | Capabilities |
|---|---|
| Owner | Full control of the organization |
| Admin | Manage members and projects |
| Member | Access org projects (read-only inheritance by default) |

### Guard rails

- You can't change or remove **your own** membership row.
- Only a System Admin can change or remove an **Owner**, and every org must keep
  at least one Owner.
- Only Owners (and System Admins) can grant the **Owner** role.
- Plain **Members** see the settings page read-only.

## Assigning Projects to an Organization

A project can optionally belong to one organization. On the project's
**Settings → Organization** page (project Admins only):

- **Assign** the project to an organization you own or administer.
- **Remove** it from its organization at any time.

Projects without an organization behave exactly as before — no inheritance.

## Permission Inheritance

When a project belongs to an organization, org membership grants an inherited
project role — so you can manage access centrally at the org level:

| Org role | Inherited project role |
|---|---|
| Owner / Admin | Project **Admin** |
| Member | Project **Viewer** (read-only) |

Inheritance is additive: an explicit project membership can only **raise** a
person's effective role, never lower it. Inherited members don't appear in the
project's member list, so the members page shows a banner explaining who has
inherited access and why.

## Data Isolation

Multi-tenant data isolation and the **Org Switcher** are separate from the
permission model above: inheritance follows actual membership, not the active
org selected in the switcher. All projects, issues, and data belong to an org,
and System Admins can access any org.
