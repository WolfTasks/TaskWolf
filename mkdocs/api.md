# API Reference

## Interactive Docs

TaskWolf exposes a full OpenAPI 3 specification. Access the interactive Swagger UI at:

```
http://<your-host>/swagger-ui.html
```

The OpenAPI JSON is available at `/v3/api-docs`.

## Authentication

### JWT (browser sessions)

Obtained via `POST /api/v1/auth/login`. Include as a Bearer token:

```
Authorization: Bearer <token>
```

### API Keys (CI/CD)

Generate API keys from **Profile → API Keys**. Keys use the `tw_` prefix and are displayed only once at creation.

```
Authorization: Bearer tw_<key>
```

API keys carry the permissions of the user who created them.

## Base URL

All endpoints are prefixed with `/api/v1`. Project-scoped endpoints include the project key:

```
/api/v1/projects/{key}/issues
/api/v1/projects/{key}/sprints
```

## Rate Limiting

Requests are rate-limited per IP address. The default limit is 100 requests per minute. Exceeded limits return HTTP 429.
