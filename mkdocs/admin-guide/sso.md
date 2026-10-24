# SSO / OIDC

TaskWolf supports any OIDC-compliant identity provider (Keycloak, Okta, Azure AD, Auth0, Google Workspace).

## Prerequisite: public base URL

Set `TW_BASE_URL` to the URL users open in the browser, including the scheme (e.g. `https://tasks.example.com`).
TaskWolf builds the OIDC redirect URI and the post-login redirect from it, so it must match what the IdP has
registered even when TLS terminates in front of TaskWolf.

## Register a Provider

1. In your IdP, create an **OIDC application** (Authorization Code flow) and note the **Issuer URL**,
   **Client ID**, and **Client Secret**

Then in TaskWolf:

1. Log in as System Admin
2. Go to **Admin → SSO**
3. Click **Add Provider**
4. Fill in: Name, Issuer URL, Client ID, Client Secret
5. **Save** — the provider appears on the login page immediately
6. Copy the **Redirect URI** shown for the provider (`https://<your-domain>/login/oauth2/code/<provider-id>`)
   into the IdP application's allowed redirect URIs

## Login Flow

The login page's SSO button starts the flow at `/oauth2/authorization/<provider-id>`; the bundled nginx forwards
`/oauth2/` and `/login/oauth2/` to the backend. After a successful login the browser lands on
`/auth/sso/callback`, which stores the session (access + refresh token, passed in the URL fragment so they never
reach server logs). Failed or rejected logins return to the login page with an error message.

## Auto-Provisioning

When **Auto-Provision** is enabled (default), users logging in via SSO for the first time are automatically created with the `MEMBER` system role.

Disable auto-provisioning if you want to manually create accounts before allowing SSO login.

## Discovery

TaskWolf fetches provider metadata from `{issuerUrl}/.well-known/openid-configuration` at login time. No manual endpoint configuration is required.

## Removing a Provider

Delete the provider from **Admin → SSO**. Existing user accounts are not affected.
