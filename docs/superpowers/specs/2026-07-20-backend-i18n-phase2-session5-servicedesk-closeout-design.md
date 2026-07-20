# Backend i18n Phase 2 — Session 5: servicedesk slice + sweep close-out

**Date:** 2026-07-20
**Backlog:** #16 (backend text localization via Spring `MessageSource`)
**Status:** design approved; ready for implementation plan.

## Context

Phase 2 (the API-error sweep) localizes backend-generated error text to `MessageSource`
keys (English base bundle `messages.properties` + German `messages_de.properties`,
`\uXXXX`-escaped). Tasks 0–8 have shipped across Sessions 1–4 (PRs #84, #85, #86, #87);
as of this design all of #85/#86/#87 are **merged to `main`** (`main` HEAD `7f48fb0`).
Session 5 is the final Phase-2 session: it converts the one remaining module
(`servicedesk`, Task 9 of the plan) and then closes out the sweep with a durable
regression guard and a verification pass.

Reference plan (authoritative for Task 9's exact catalog entries, throw-site line
numbers, and test bodies): `docs/superpowers/plans/2026-07-19-backend-i18n-phase2-api-error-sweep.md`,
Task 9 (lines 1465–1679) and Final verification (lines 1683–1692).

### Why a close-out is needed

The final whole-branch review of Session 4 found that the plan's own construct-1/2
verification greps (`throw NotFoundException("…`) match only **un-qualified** throws
and silently miss **fully-qualified** ones (`throw com.taskowolf...NotFoundException("…`).
That blind spot is exactly why an un-keyed `AttachmentController` throw slipped through
the entire sweep until review. A widened audit on current `main` (2026-07-20) confirms
**no other stragglers remain** outside `servicedesk`:

- FQ-prefixed free-text throws of keyed exceptions (bare + fqcn), excl. `.keyed(`: **none**
- Free-text `orElseThrow { …Exception("…") }` lambdas: **none**
- `?: error(` / `throw ResponseStatusException` / `throw AccessDeniedException` / user-facing `check(`:
  only the 11 `servicedesk` sites Task 9 targets, plus the 2 intentionally-kept internal invariants.

So Session 5 is low-risk: after Task 9, the widened audit should return clean, proving the
Phase-2 sweep complete. The remaining design decision is how to prevent the blind spot from
silently recurring in future code — resolved below with a durable test guard.

## Goals

1. Localize all user-facing `servicedesk` error text (Task 9), fixing the 6× latent
   HTTP 500→404 bugs and giving the 2 invalid-enum 400s the standard localized body.
2. Add a **durable regression guard** so no future un-keyed user-facing throw (bare or
   fully-qualified) can reach `main` without failing CI.
3. Run the Phase-2 Final verification checklist (widened for the FQ blind spot) and
   confirm the sweep is complete.

**Out of scope (this session):** the manual DE/EN browser smoke check and the v1.0.x
release — both are done by Wolfgang after this session lands.

## Design

### 1. Session shape & logistics

- **Worktree:** a fresh isolated worktree branched off current `main` (`7f48fb0`).
  Sessions 2–4 are merged, so there is **no more stacking** — Session 5 branches from
  clean `main` and its PR targets `main` directly.
- **Method:** subagent-driven development — one implementer subagent for Task 9 + the
  guard (both in one commit), a task review, then a controller-run verification pass.
- **German catalog procedure (established):** write German values with raw umlauts,
  then run the PowerShell non-ASCII→`\uXXXX` pass and confirm 0 non-ASCII bytes remain;
  UTF-8 resolution tests assert the decoded strings.

### 2. Task 9 — `servicedesk` slice (execute per plan lines 1465–1679)

The plan's throw-site line numbers were re-confirmed against the live controllers on
`7f48fb0` and match exactly.

**`servicedesk/api/ServiceDeskController.kt`:**
- `:32, :38, :51, :72, :87` — `?: error("Project not found: $key")`
  → `?: throw NotFoundException.keyed("project.notFound", key)` (reuse existing key). **500→404.**
- `:40, :73, :88` — `?: error("Service desk not enabled for project: $key")`
  → `?: throw NotFoundException.keyed("serviceDesk.notEnabled", key)`. **500→404.**
- `:77` — `throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid priority: …")`
  → `throw BadRequestException.keyed("serviceDesk.invalidPriority", req.priority, IssuePriority.entries.joinToString())`. **Stays 400, now localized body.**
- Add imports `NotFoundException`, `BadRequestException`; remove now-unused
  `ResponseStatusException` import; **keep** `HttpStatus` (used by `@ResponseStatus`).

**`servicedesk/api/IncidentController.kt`:**
- `:50` — project `error()` → `NotFoundException.keyed("project.notFound", key)`. **500→404.**
- `:32` — `throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid severity '…'. …")`
  → `throw BadRequestException.keyed("incident.invalidSeverity", req.severity, IncidentSeverity.entries.joinToString())`. **Stays 400.**
- Add imports; remove unused `ResponseStatusException` import; keep `HttpStatus`.

**New catalog keys** (en base + de, `\uXXXX`-escaped; `project.notFound` reused from Task 2):
```
serviceDesk.notEnabled=Service desk not enabled for project: {0}
serviceDesk.invalidPriority=Invalid priority: {0}. Valid values: {1}
incident.invalidSeverity=Invalid severity ''{0}''. Must be one of: {1}
```
`incident.invalidSeverity` is resolved **with args** and contains literal quotes around
`{0}`, so the quotes MUST be doubled (`''{0}''`) in both languages (MessageFormat rule).
`serviceDesk.invalidPriority` has no literal quotes.

**Tests:**
- `i18n/ServiceDeskMessagesTest.kt` — en/de resolution for the 3 new keys, asserting the
  single-quote rendered output for `incident.invalidSeverity` (proves the doubling).
- `servicedesk/ServiceDeskErrorLocalizationTest.kt` (MockMvc, extends `IntegrationTestBase`) —
  Decision 3 status-change coverage: unknown project → **404** (localized de), service-desk-
  not-enabled → **404** (localized de), invalid priority → **400** localized, invalid severity
  → **400** localized. (`GET /service-desk` and `GET /incidents` carry no method-level
  `@PreAuthorize`, so an unknown key reaches the controller's `?: throw` and yields 404,
  not a 403 short-circuit.)

### 3. Durable guard — `NoUnkeyedUserFacingThrowTest` (same commit as Task 9)

A new test in `backend/src/test/kotlin/com/taskowolf/core/`, modelled on the existing
`KeyedReferenceIntegrityTest` (walk `src/main/kotlin`, resolve source root the same way,
assert it scanned > 0 files so it can't pass vacuously).

**It fails if any `.kt` under `src/main/kotlin` contains a user-facing free-text throw:**
- `throw (<fqcn>.)?(NotFound|Forbidden|Conflict|BadRequest)Exception("…")` — **bare OR
  fully-qualified** (the AttachmentController blind spot). A regex that tolerates an optional
  dotted qualifier before the exception name.
- `orElseThrow\s*\{\s*(<fqcn>.)?(…)Exception\("` — free-text `orElseThrow` lambdas.
- `\?:\s*error\(` , `throw\s+ResponseStatusException\(` , `throw\s+AccessDeniedException\(`.

**Deliberately NOT scanned:** bare `check(...)` / `require(...)` invariants. These are
pervasive internal-invariant idioms and scanning them would produce large, noisy allowlists.
The `?: error(` pattern (nullable-fallback, almost always a not-found lookup) is the one
`error/check` shape that is reliably user-facing, so it is the only IllegalStateException
producer the guard targets. Decision 4 ("no user-facing `IllegalStateException` remains") is
still satisfied: the audit confirms the only remaining `error()`/`check()` user paths were the
servicedesk sites (converted in this session) and the 2 allowlisted internal invariants.

**Allowlist** — a small explicit, self-documenting set of the 2 Decision-2 internal
invariants that are intentionally NOT keyed (they are not user-facing):
- `OidcUserProvisioningService.kt` — `?: error("OIDC user has no email")`
- `SsoController.kt` — clientSecret-required invariant

The allowlist is matched by `file name + a substring of the throwing line` (not a bare line
number, which drifts), so it stays valid as the files evolve. Any match **not** on the
allowlist fails the test with a message naming file + line, telling the author to use
`.keyed(...)`.

Rationale: this encodes Decision 2 (keep those two as internal invariants) directly in the
guard, and makes the whole sweep self-enforcing — a future `throw com.foo.NotFoundException("…")`
fails CI instead of silently shipping un-localized English.

> **Note / possible fix during implementation:** `.keyed(...)` calls are themselves
> `throw X.keyed(...)`, not `throw X("…")`, so the free-text regex (which requires a `"`
> immediately after `(`) should not match them — but the implementer must verify the
> guard is green on the post-Task-9 tree (only the 2 allowlisted sites remain) and that it
> would have caught the pre-fix `AttachmentController` line. If any legitimate keyed call
> trips the regex, tighten the pattern (do not broaden the allowlist).

### 4. Final verification (controller-run, after task review — no code expected)

- Widened greps A/B/C (from the audit above) → clean except the 2 allowlisted invariants.
- Plan's Final-verification checklist (lines 1685–1691): full suite green; `MessagesParityTest`,
  `KeyedReferenceIntegrityTest`, **and the new `NoUnkeyedUserFacingThrowTest`** all green;
  the servicedesk web guard shows 404 (not 500) and localized 400s; en/de parity holds.
- Confirm no user-facing `IllegalStateException` remains anywhere in `src/main`.

## Testing

Full backend suite via `cd backend && ./gradlew test` (currently 367 tests → grows by the
servicedesk resolution + web-guard tests + the guard). All three static gates must be green.
Per-task review + controller verification pass. Then a PR to `main`.

## Risks & mitigations

- **Guard false-positives on legitimate `.keyed(...)` / internal `check()`:** the regex
  targets `("` immediately after the exception constructor, which `.keyed(` does not match;
  the implementer verifies green-on-clean-tree and would-have-caught-AttachmentController.
- **MockMvc 403-vs-404 short-circuit** on unknown project key: mitigated by the plan's note
  (the two GET endpoints have no method-level `@PreAuthorize`); adjust only if a run proves
  otherwise.
- **German mojibake:** the raw-umlaut→`\uXXXX` PowerShell pass + UTF-8 resolution assertions
  are self-correcting.

## Deliverable

One PR to `main`: a single feature commit (Task 9 conversions + catalog keys + servicedesk
tests + the `NoUnkeyedUserFacingThrowTest` guard), completing the #16 Phase-2 API-error
sweep. Manual DE/EN smoke + release follow separately (Wolfgang).
