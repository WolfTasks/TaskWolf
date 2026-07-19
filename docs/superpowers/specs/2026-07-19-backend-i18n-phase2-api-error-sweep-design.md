# Backend-i18n Phase 2 — API-error sweep — Design

**Backlog:** #16 (backend text localization via Spring `MessageSource`). Follow-up to Phase 1 (foundation + `issues` pilot, PR #83).

**Goal:** Localize the remaining **81 free-text exception throw-sites** (across 27 files) and the one remaining custom Bean-Validation message onto `MessageSource` keys, so every API error message resolves in the request locale (en default, de). Reuse Phase 1's `.keyed()` factory, catalog, validator wiring, and parity gate. `ErrorResponse.code` values stay unchanged.

---

## Prerequisite (hard gate)

Phase 2 builds on Phase 1's foundation, which currently lives **only** on the unmerged branch `worktree-backend-i18n-phase1` (PR #83, OPEN). **PR #83 must be merged to `main`** (and ideally released) **before Phase 2 implementation starts.** All slices below are planned against a fresh `main` that already contains:

- Base catalog `messages.properties` (English master) + `messages_de.properties` (`\uXXXX`-escaped). **English lives in the base bundle — there is no `messages_en.properties`** (Spring Boot's `ResourceBundleCondition` only matches `messages.properties`).
- `LocalizedMessages` helper: `get(key, vararg args)` (request locale via `LocaleContextHolder`), `get(key, locale, vararg args)` (explicit locale, with English fallback), `localeOf(user)`.
- `LocalizedException` interface + `.keyed(key, vararg args)` companion factory on all four domain exceptions (`NotFoundException`, `ForbiddenException`, `ConflictException`, `BadRequestException`). **Always use `.keyed()` for keyed throws** — a bare `Exception("some.key")` binds to the free-text `(message: String)` constructor and leaks the raw key.
- `GlobalExceptionHandler` resolving keyed exceptions against the request locale; free-text constructors preserved (backward-compatible — unmigrated sites keep working).
- `LocaleConfig`: `AcceptHeaderLocaleResolver` (en/de, default en) + `LocalValidatorFactoryBean` bound to the `MessageSource` (per-request-locale validation).
- Frontend axios interceptor sending `Accept-Language: <i18n.language>`.
- CI gate `MessagesParityTest` (en/de key-set identical + no blank values).

---

## Global constraints (unchanged from Phase 1)

- Languages: **en** (default/fallback) and **de** only.
- `ErrorResponse.code` values (`NOT_FOUND`, `FORBIDDEN`, `CONFLICT`, `BAD_REQUEST`, `VALIDATION_ERROR`, `INTERNAL_ERROR`) are **stable and machine-readable — never change them**. Only `message`/`details` get localized.
- Keys are **namespaced by domain**: `auth.*`, `project.*`, `workflow.*`, `sprint.*`, `report.*`, `integration.*`, `org.*`, `customField.*`, `label.*`, `version.*`, `comment.*`, `attachment.*`, `notification.*`, `automation.*`. Placeholders are positional `{0}`, `{1}` (`java.text.MessageFormat`).
- `.properties` files are ISO-8859-1; non-ASCII in `messages_de.properties` MUST be `\uXXXX`-escaped (`ü`=`ü`, `ö`=`ö`, `ä`=`ä`, `ß`=`ß`, `Ö`=`Ö`, etc.).
- Backend TDD: failing test first → minimal implementation → green → commit. Backend tests run from `backend/` via `./gradlew`. Frontend is untouched by Phase 2.

---

## Architecture

No new infrastructure. Phase 2 is a **mechanical sweep** applying the Phase 1 pattern site-by-site, plus three small cross-cutting hardening changes done up front (Slice 0). Each module slice:

1. Adds a keyed section to the base `messages.properties` (English) and the same keys to `messages_de.properties` (German, escaped).
2. Swaps that module's free-text throws to `Exception.keyed("key", args…)`.
3. Adds a localization integration test asserting representative sites return de under `Accept-Language: de` and en by default.
4. Keeps the parity gate (key-set + placeholder) green.
5. Ships as its own PR.

Interpolated string content in a throw-site (`${request.email}`, a status name, an id) becomes a positional `{0}` arg passed to `.keyed()`.

Reuse existing keys where a message already exists — e.g. Board's `"Project has no workflow"` reuses `project.noWorkflow` (added in Phase 1 for the `issues` pilot); no new key.

---

## Slice 0 — Cross-cutting hardening (do first)

Small, foundation-level; every later slice benefits and the extended gate guards them.

### 0.1 Typo safety net
Add a `try/catch (NoSuchMessageException)` to the **request-locale** overload `LocalizedMessages.get(key: String, vararg args: Any?)` — currently unguarded (only the explicit-locale overload has a fallback). On miss, return the key itself as last resort.

**Rationale for this over `spring.messages.use-code-as-default-message=true`:** the global flag makes `getMessage` never throw `NoSuchMessageException`, which would *defeat* the explicit-locale overload's fallback-to-English that Phase 3 (emails/notifications in recipient locale) depends on. A code-level catch on only the request-locale overload keeps the two overloads' semantics distinct. Result: a mistyped `.keyed(...)` surfaces as a visible raw key (caught by that slice's localization test), never a 500.

**Test:** `LocalizedMessages.get("nonexistent.key")` returns `"nonexistent.key"` (no throw).

### 0.2 Placeholder-parity gate
Add a third test to `MessagesParityTest`: for each shared key, extract the `{n}` index set from the en value and the de value and assert they are equal. Catches a German line that dropped or renumbered a placeholder.

**Test:** passes on the current catalog; a temporary `de` line with a missing `{0}` makes it fail.

---

## Module slices (7)

Each is one PR. Counts are free-text throw-sites in that module today.

### Slice 1 — `auth` (15 sites)
- `AuthService` (5): registration disabled, email already registered `{0}`, invalid credentials, account disabled, invalid refresh token.
- `UserAccountService` (5): cannot reactivate deleted account, account has no password, current password incorrect, unsupported language, cannot deactivate/delete last active admin.
- `RefreshTokenService` (2), `AccessTokenService` (1, token id `{0}`), `ApiKeyService` (1, key id `{0}`), `UserSearchController` (1).
- Namespace `auth.*`.

### Slice 2 — `projects` (13 sites)
- `ProjectService` (12): key already exists `{0}`, project not found `{0}`, not a member `{0}`, admin role required, already a member, cannot change own role, cannot change owner's role, member not found (×2), cannot remove owner, must be admin of target org.
- `BoardService` (1): reuses existing `project.noWorkflow`.
- Namespace `project.*`.

### Slice 3 — `workflow` (7 sites)
- `WorkflowService` (7): no TODO status `{0}`, no workflow for project `{0}`, transition not allowed (`{0}`→`{1}`), transition blocked field required `{0}`, transition blocked role not permitted `{0}`, status not found `{0}`, transition not found `{0}`.
- Namespace `workflow.*`.

### Slice 4 — `agile-reports` (11 sites)
- `SprintService` (7): cannot change dates once started, not in PLANNED, already an active sprint, not ACTIVE, cannot assign to closed sprint, issue not in this sprint, sprint not in this project.
- `ReportsService` (2): sprint does not belong to this project (×2).
- `DashboardService` (2): dashboard not found (×2).
- Namespaces `sprint.*`, `report.*`.

### Slice 5 — `integrations` (11 sites) + IllegalArgumentException localization
- `WebhookService` (4): webhook not found `{0}` (×4 → one key).
- `ProjectIntegrationService` (3): unknown provider `{0}`, integration already exists `{0}`/`{1}`, integration not found `{0}`.
- `SsrfValidator` (2), `IncomingWebhookService` (2).
- **Convert the user-facing `IllegalArgumentException` throws to `BadRequestException.keyed(...)`** so they localize: `SsrfValidator` "Invalid URL `{0}`" + blocked-address message, `IncomingWebhookService` "Invalid JSON payload" (×2), `ProjectIntegrationService` "Unknown provider `{0}`". The generic `IllegalArgumentException` handler stays for internal/programmer IAEs.
- Namespace `integration.*`.

### Slice 6 — `organizations` (10 sites) + slug validation
- `OrganizationService` (8): only owner/system-admin can grant OWNER (×2), user already a member, cannot remove an owner, cannot remove the last owner, cannot change own role, cannot change an owner's role, cannot demote the last owner.
- `AutomationService` (1, rule not found `{0}`), `AdminAutomationController` (1, system admin role required).
- **Key the `CreateOrganizationRequest.slug` `@Pattern` message** → `org.slug.pattern` (add to both catalogs), following the Phase 1 `project.key.pattern` precedent.
- Namespaces `org.*`, `automation.*`.

### Slice 7 — `content` (14 sites)
- `CustomFieldService` (3), `LabelService` (2), `VersionService` (2), `CommentService` (2), `AttachmentService` (1), `StorageService` (2), `NotificationService` (1), `NotificationPreferenceController` (1).
- Namespaces `customField.*`, `label.*`, `version.*`, `comment.*`, `attachment.*`, `notification.*`.

**Total: 81 sites.**

---

## Testing

- **Per slice:** one localization integration test (extends `IntegrationTestBase`, real Postgres via Testcontainers) driving a representative endpoint per module, asserting the German message under `Accept-Language: de` and the English message by default. Follow the Phase 1 `IssueErrorLocalizationTest` / `ProjectValidationLocalizationTest` shape.
- **Gate:** `MessagesParityTest` (key-set + non-blank + new placeholder-parity check) runs in the existing `backend-test` CI job — no workflow change.
- **Full suite:** `cd backend && ./gradlew test` green after each slice (free-text constructors preserved, so no regression in existing tests; grep any test asserting an old English literal for a message being migrated and update it).

---

## Sessions & sequencing

- ~2–3 slices per session at the ~70% usage checkpoint → roughly **3 sessions**. Slice 0 + Slice 1 land together in session one.
- Each slice its own worktree branch + PR, per-task TDD, per-slice review before the next.

## Out of scope

- **Phase 3** — `EmailService` + `NotificationService` (incl. the 3 `createDirect` callers) rendering in the recipient's `user.language` via `LocalizedMessages.get(key, localeOf(user), args)`; notification `title` rendered at creation, `body` stays user-content. Separate spec.
- Any change to `ErrorResponse.code` values, HTTP statuses, or frontend code.
