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

`POST /api/v1/auth/login` and `POST /api/v1/auth/register` are rate-limited per client IP: by default 10 requests
per 60 seconds (`taskowolf.auth.rate-limit.max-requests` / `window-seconds`). Exceeded limits return HTTP 429 with
code `RATE_LIMITED`. Other endpoints are not rate-limited.

The client IP is the `X-Real-IP` header set by the bundled nginx (trusted only when the request comes from a
private/loopback address, i.e. the proxy); `X-Forwarded-For` is ignored because clients can set it themselves. If you
put another reverse proxy or load balancer in front of the bundled nginx, configure nginx's
[`real_ip`](https://nginx.org/en/docs/http/ngx_http_realip_module.html) module for it, otherwise all users share
that proxy's address and one bucket.
