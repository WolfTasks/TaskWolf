# SSO / OIDC

TaskWolf supports any OIDC-compliant identity provider (Keycloak, Okta, Azure AD, Auth0, Google Workspace).

## Register a Provider

1. In your IdP, create an **OIDC application** (Authorization Code flow)
2. Set the redirect URI to: `https://<your-domain>/login/oauth2/code/<provider-id>`
3. Note the **Issuer URL**, **Client ID**, and **Client Secret**

Then in TaskWolf:

1. Log in as System Admin
2. Go to **Admin → SSO**
3. Click **Add Provider**
4. Fill in: Name, Issuer URL, Client ID, Client Secret
5. **Save** — the provider appears on the login page immediately

## Auto-Provisioning

When **Auto-Provision** is enabled (default), users logging in via SSO for the first time are automatically created with the `MEMBER` system role.

Disable auto-provisioning if you want to manually create accounts before allowing SSO login.

## Discovery

TaskWolf fetches provider metadata from `{issuerUrl}/.well-known/openid-configuration` at login time. No manual endpoint configuration is required.

## Removing a Provider

Delete the provider from **Admin → SSO**. Existing user accounts are not affected.
