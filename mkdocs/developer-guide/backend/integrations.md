# Module: integrations

## Purpose

Manages outgoing webhooks (fanout, signed HTTP delivery with retries), incoming GitHub and GitLab event ingestion (commit/PR linking), and API key–based authentication. Owns `ApiKeyAuthFilter`, which intercepts `Bearer tw_*` tokens before `JwtAuthFilter` in the Spring Security filter chain.

---

## Entities Owned

| Entity | Table | Key Fields |
|---|---|---|
| `Webhook` | `webhooks` | `projectId` UUID NOT NULL, `url` VARCHAR(2048), `secret` TEXT (plaintext — see Security below), `events` TEXT (JSON array of event type strings), `enabled` BOOLEAN, `createdBy` UUID |
| `WebhookDelivery` | `webhook_deliveries` | `webhookId` UUID FK→webhooks ON DELETE CASCADE, `eventType` VARCHAR(100), `payload` TEXT (JSON body sent), `responseStatus` INT?, `responseBody` TEXT?, `attemptCount` INT, `nextRetryAt` Instant? (null = no more retries), `deliveredAt` Instant? |
| `ProjectIntegration` | `project_integrations` | `projectId` UUID NOT NULL, `provider: IntegrationProvider` (`GITHUB`/`GITLAB`), `webhookSecret` TEXT (plaintext), `repoUrl` VARCHAR(2048)?; UNIQUE `(project_id, provider)` |
| `IssueRef` | `issue_refs` | `issueId` UUID FK→issues ON DELETE CASCADE, `provider: IntegrationProvider`, `refType: RefType` (`COMMIT`/`PR`), `externalId` VARCHAR(255), `url` VARCHAR(2048), `title` VARCHAR(1024)?; UNIQUE `(issue_id, provider, ref_type, external_id)` |

`api_keys` table (V13) is used by `ApiKeyAuthFilter` but its CRUD controller is in `backend/src/main/kotlin/com/taskowolf/auth/api/ApiKeyController.kt`.

`WebhookEventType` constants (all strings): `issue.created`, `issue.updated`, `issue.status_changed`, `issue.assigned`, `issue.deleted`, `sprint.started`, `sprint.completed`, `comment.created`, `attachment.added`. `issue.deleted` has no dispatcher listener and is never emitted.

---

## DB Schema

### `api_keys` (V13)

```sql
CREATE TABLE api_keys (
    id           UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    key_hash     VARCHAR(64)  NOT NULL UNIQUE,
    key_prefix   VARCHAR(12)  NOT NULL,
    project_id   UUID         REFERENCES projects(id) ON DELETE CASCADE,
    created_by   UUID         NOT NULL REFERENCES users(id),
    last_used_at TIMESTAMPTZ,
    expires_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL
);
```

`key_hash` stores the SHA-256 hash of the raw `tw_*` token. The raw token is returned once at creation and never stored.

### `webhooks`, `webhook_deliveries` (V14 + V16)

V14 created `webhooks.secret_hash`; V16 renamed it to `secret` (now stores plaintext). The column name in the running schema is `secret`.

```sql
CREATE TABLE webhooks (
    id         UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    project_id UUID          NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    url        VARCHAR(2048) NOT NULL,
    secret     VARCHAR(64)   NOT NULL,
    events     TEXT          NOT NULL,
    enabled    BOOLEAN       NOT NULL DEFAULT true,
    created_by UUID          NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ   NOT NULL,
    updated_at TIMESTAMPTZ   NOT NULL
);
```

`webhook_deliveries.next_retry_at` has a partial index `WHERE delivered_at IS NULL` — retry polling uses this index.

### `project_integrations`, `issue_refs` (V15 + V16)

V15 created `project_integrations.webhook_secret_hash`; V16 renamed it to `webhook_secret` (now stores plaintext).

---

## API Endpoints

### Outgoing webhooks (`/api/v1/projects/{key}/webhooks`)

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/v1/projects/{key}/webhooks` | USER | List webhooks for project |
| `POST` | `/api/v1/projects/{key}/webhooks` | ADMIN | Create webhook; response includes `plaintextSecret` (one-time); SSRF validation runs before save |
| `PUT` | `/api/v1/projects/{key}/webhooks/{webhookId}` | ADMIN | Update URL/events/enabled |
| `DELETE` | `/api/v1/projects/{key}/webhooks/{webhookId}` | ADMIN | Delete webhook; returns 204 |
| `GET` | `/api/v1/projects/{key}/webhooks/{webhookId}/deliveries` | USER | Paged delivery history (`page`, `size`) |
| `POST` | `/api/v1/projects/{key}/webhooks/{webhookId}/test` | ADMIN | Send a test ping delivery; returns 201 |

### Project integrations (`/api/v1/projects/{key}/integrations`)

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/v1/projects/{key}/integrations` | USER | List GitHub/GitLab integrations |
| `POST` | `/api/v1/projects/{key}/integrations` | ADMIN | Create integration; response includes generated `plaintextSecret` |
| `DELETE` | `/api/v1/projects/{key}/integrations/{integrationId}` | ADMIN | Remove integration; returns 204 |

### Incoming webhooks (permit-all in `SecurityConfig`)

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/v1/integrations/github/{projectKey}/webhook` | PUBLIC | Receive GitHub push/PR event; validated by `X-Hub-Signature-256` HMAC header |
| `POST` | `/api/v1/integrations/gitlab/{projectKey}/webhook` | PUBLIC | Receive GitLab push/MR event; validated by `X-Gitlab-Token` header equality |

Both incoming paths are `permitAll()` in `SecurityConfig` — no JWT or API key required. HMAC (GitHub) or token equality (GitLab) is the only authentication.

---

## Events Emitted

None. The integrations module creates `IssueRef` records but publishes no domain events.

---

## Events Consumed

`WebhookDispatcher` listens to domain events and fans out to all enabled webhooks for the project:

| Event | Handler | Webhook event type dispatched |
|---|---|---|
| `IssueCreatedEvent` | `onIssueCreated()` | `issue.created` |
| `IssueStatusChangedEvent` | `onStatusChanged()` | `issue.status_changed` |
| `IssueFieldChangedEvent` (field=`assignee`) | `onFieldChanged()` | `issue.assigned` |
| `IssueFieldChangedEvent` (other fields) | `onFieldChanged()` | `issue.updated` |
| `SprintStartedEvent` | `onSprintStarted()` | `sprint.started` |
| `SprintCompletedEvent` | `onSprintCompleted()` | `sprint.completed` |
| `CommentCreatedEvent` | `onCommentCreated()` | `comment.created` |
| `AttachmentAddedEvent` | `onAttachmentAdded()` | `attachment.added` |

Dispatch saves a `WebhookDelivery` record synchronously, then calls `sendAsync(deliveryId)` which is `@Async` — the HTTP POST to the target URL happens on a background thread. Retry schedule: attempt 1 → 60 s delay, attempt 2 → 300 s, attempt 3+ → no further retries.

`DeliveryRetryJob` and `DeliveryCleanupJob` are scheduled jobs that process pending retries and remove old delivery records.

---

## Key Files

- `backend/src/main/kotlin/com/taskowolf/integrations/application/WebhookDispatcher.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/application/IncomingWebhookService.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/application/HmacSigner.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/application/SsrfValidator.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/application/WebhookService.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/application/IssueKeyParser.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/api/GitHubWebhookController.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/api/GitLabWebhookController.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/api/WebhookController.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/api/ProjectIntegrationController.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/domain/WebhookEventType.kt`
- `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/ApiKeyAuthFilter.kt`
- `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/SecurityConfig.kt`
- `backend/src/main/resources/db/migration/V13__api_keys.sql`
- `backend/src/main/resources/db/migration/V14__webhooks.sql`
- `backend/src/main/resources/db/migration/V15__integrations.sql`
- `backend/src/main/resources/db/migration/V16__webhook_secrets_plaintext.sql`

---

## Extension Points

- **Add a new outgoing webhook event type:** (1) Add a constant to `WebhookEventType`. (2) Add an `@EventListener` method in `WebhookDispatcher` for the new domain event. (3) Build the data payload map and call `dispatch(eventType, projectId, data)`.
- **Add a new incoming provider (e.g. Bitbucket):** (1) Add the provider to `IntegrationProvider`. (2) Add a `@RestController` at `/api/v1/integrations/bitbucket/{projectKey}/webhook`. (3) Add the path to the `permitAll()` list in `SecurityConfig`. (4) Verify the provider's signature scheme (Bitbucket uses `X-Hub-Signature` with SHA-256; reuse `HmacSigner.verify()`). (5) Add a `handleBitbucket()` method in `IncomingWebhookService` following the `handleGitHub` pattern.
- **`IssueKeyParser`** parses issue keys from commit/PR text using a regex. Extend it if your project key format differs from the default `[A-Z]+-\d+` pattern.

---

## Common Pitfalls

- **Webhook secrets are stored plaintext** — `webhooks.secret` and `project_integrations.webhook_secret` both hold raw secret values. V16 intentionally renamed `secret_hash` to `secret` to reflect this. HMAC verification requires the raw value; do NOT hash it before storing.
- **API key tokens (`tw_*`) ARE hashed** — `api_keys.key_hash` stores the SHA-256 hash. The raw token is returned once at creation and never persisted. Authentication in `ApiKeyAuthFilter` hashes the incoming token and compares to the stored hash.
- **`ApiKeyAuthFilter` position** — registered `.addFilterBefore(apiKeyAuthFilter, JwtAuthFilter::class.java)` in `SecurityConfig`. It runs before JWT processing. Both filters short-circuit if `SecurityContextHolder` already has an authentication.
- **`SsrfValidator` accepts unresolvable DNS names** — if `InetAddress.getAllByName(host)` throws (DNS failure), `SsrfValidator.validate()` returns without error. Only resolved private IPs are blocked (`isLoopbackAddress`, `isSiteLocalAddress`, `isLinkLocalAddress`, `isAnyLocalAddress`, `isMulticastAddress`). A webhook URL with an unresolvable hostname passes validation and fails only at delivery time.
- **GitLab uses token equality, not HMAC** — `IncomingWebhookService.handleGitLab()` compares `integration.webhookSecret == tokenHeader` directly (no `HmacSigner`). If the `X-Gitlab-Token` header is absent, the comparison is against an empty string.
- **`IssueRef` duplicate upsert is silent** — `linkKeys()` catches exceptions from duplicate UNIQUE constraint violations and logs at DEBUG. A duplicate commit/PR reference silently does nothing.

---

## Example

GitHub webhook HMAC verification — `IncomingWebhookService` calls `HmacSigner.verify()` with the plaintext secret:

```kotlin
// IncomingWebhookService
private fun verifyGitHubSignature(
    payload: String,
    secret: String,
    signatureHeader: String?
): Boolean {
    if (signatureHeader == null) return false
    return hmacSigner.verify(payload, secret, signatureHeader)
}

// HmacSigner
fun sign(payload: String, secret: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    val hex = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return "sha256=$hex"
}

fun verify(payload: String, secret: String, signature: String): Boolean =
    sign(payload, secret) == signature
```

`sign()` produces `sha256=<64 hex chars>`. GitHub sends this value in `X-Hub-Signature-256`. The secret passed to `sign()` must be the raw plaintext value from `project_integrations.webhook_secret`.

---

## Test Patterns

- **`GitHubWebhookControllerTest`** — full integration test (`IntegrationTestBase`); creates a real project and integration via the API, then POSTs to the webhook endpoint with a valid HMAC signature. Verifies: valid signature returns 200; invalid signature returns 401; commit message containing an issue key creates an `IssueRef` visible on the issue detail endpoint.
- **`HmacSignerTest`** — pure unit test. Verifies: signature starts with `sha256=` and is 71 chars; same input produces same output; different secret produces different signature; `verify()` returns true for matching signature; `verify()` returns false for tampered payload.
- **`SsrfValidatorTest`** — pure unit test. Verifies: public URL accepted; `localhost`, `127.0.0.1`, `10.x.x.x`, `192.168.x.x` ranges all rejected.
- **`WebhookServiceTest`** — pure unit test with MockK. Verifies: `create()` calls `ssrfValidator.validate()` before saving; the saved `Webhook.secret` equals the provided plaintext value (not a hash); SSRF rejection prevents `webhookRepository.save()` from being called.
- **`WebhookControllerTest`** — MockMvc slice test; verifies endpoint routing and status codes.
- **`IssueKeyParserTest`** — pure unit test; verifies key extraction regex against various commit message formats.
