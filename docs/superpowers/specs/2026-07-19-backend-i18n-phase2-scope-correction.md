# Backend-i18n Phase 2 — Scope Correction (authoritative inventory)

**Status:** input for revising `docs/superpowers/plans/2026-07-19-backend-i18n-phase2-api-error-sweep.md`. Wolfgang chose the **full sweep, all constructs** on 2026-07-19. Session 1 (Task 0 hardening + auth slice) already shipped in PR #84 and is NOT affected by this correction — auth is complete.

## Why this exists

The original Phase-2 inventory used a single grep pattern — `throw X("…")` — and counted 81 sites. Executing Session 1 surfaced that user-facing free-text errors are thrown via **five** constructs, four of which the original pattern missed. The corrected remaining surface is ~122 sites (vs. 81), plus two modules absent from the plan.

## Construct classes (counts are REMAINING, i.e. after Session-1 auth)

| # | Construct | Remaining | Exception → HTTP | In original plan? |
|---|-----------|-----------|------------------|-------------------|
| 1 | `throw X("…")` | 65 | keyed → correct status | ✅ Tasks 2–7 |
| 2 | `.orElseThrow { X("…") }` | 41 | `NotFoundException` → 404 | ❌ missed |
| 3 | `?: error("…")` / `check {…}` | 11 (user-facing) | `IllegalStateException` → **500** | ❌ missed |
| 4 | `throw ResponseStatusException(status, "…")` | 3 | as declared | ❌ missed |
| 5 | `throw AccessDeniedException("…")` | 1 | 403 (handler returns `ex.message`) | ❌ missed |

**Construct 3 is also a latent bug:** `IllegalStateException` has no handler in `GlobalExceptionHandler`, so `error("Project not found: …")` currently returns **500**, not 404. Converting these to keyed `NotFoundException`/`BadRequestException` both localizes them AND fixes the status.

## Two modules absent from the plan

- **`issues`** — Phase-1 pilot keyed only its 3 `throw`-keyword sites. It has **5 `orElseThrow` sites** with no Phase-2 slice.
- **`servicedesk`** — entirely absent. Has **9 `error()` sites** + **2 `ResponseStatusException`** (severity/priority) across `ServiceDeskController` + `IncidentController`. (No `throw`-keyword or `orElseThrow` sites in this module.)

## Enumerated inventory of the MISSED sites (constructs 2–5)

Construct-1 (`throw`-keyword) sites are already itemized in the existing plan (Tasks 2–7) and are not repeated here.

### Construct 2 — `orElseThrow` (41 remaining; auth's 7 done in PR #84)

Recommended key per site — **[reuse]** = key already defined in the current plan; **[new]** = add.

| Module → plan slice | Site(s) | Message | Key |
|---|---|---|---|
| attachments → Task 7 | AttachmentService:47 | `Attachment not found: {0}` | attachment.notFound **[new]** |
| automation → Task 6 | AutomationService:64 | `Rule not found: {0}` | automation.ruleNotFound **[reuse]** |
| comments → Task 7 | CommentService:39,57 | `Comment not found: {0}` | comment.notFound **[new]** |
| customfields → Task 7 | CustomFieldService:56,81,90,102,116 | `Custom field not found: {0}` | customField.notFound **[new]** |
| customfields → Task 7 | CustomFieldService:105,119 | `Option not found: {0}` | customField.optionNotFound **[new]** |
| labels → Task 7 | LabelService:39,53 | `Label not found: {0}` | label.notFound **[new]** |
| versions → Task 7 | VersionService:40,53 | `Version not found: {0}` | version.notFound **[new]** |
| workflows → Task 3 | WorkflowService:51,117,143 | `Workflow not found: {0}` | workflow.notFound **[new]** (distinct from `workflow.noneForProject`) |
| workflows → Task 3 | WorkflowService:46,125,145,147 | `Status not found: {0}` | workflow.statusNotFound **[reuse]** |
| workflows → Task 3 | WorkflowService:161 | `Transition not found: {0}` | workflow.transitionNotFound **[reuse]** |
| organizations → Task 6 | OrganizationService:51,99 | `User not found` | user.notFound **[reuse, shipped in PR #84]** |
| organizations → Task 6 | OrganizationService:59,89 | `Member not found` | org.memberNotFound **[new]** |
| projects → Task 2 | ProjectService:106 | `User not found` | user.notFound **[reuse]** |
| projects → Task 2 | ProjectService:59 | `Project not found: {0}` | project.notFound **[reuse]** |
| reports → Task 4 | DashboardService:36,71 | `Widget not found: {0}` | report.widgetNotFound **[new]** |
| reports → Task 4 | ReportsService:32,72 | `Sprint not found` (sprintId in scope; keep no-arg) | sprint.notFound **[new]** |
| sprints → Task 4 | SprintService:115 | `Sprint not found` | sprint.notFound **[new]** |
| sprints → Task 4 | SprintService:97,108 | `Issue not found` | issue.notFoundGeneric **[new]** (no-arg; distinct from P1 `issue.notFound`="Issue {0} not found") |
| **issues → NEW slice** | IssueService:66 | `Parent issue not found: {0}` | issue.parentNotFound **[new]** |
| **issues → NEW slice** | IssueService:81 | `Issue not found: {0}` | issue.notFound **[reuse P1]** (renders "Issue {0} not found") |
| **issues → NEW slice** | IssueService:170 | `Sprint not found: {0}` | sprint.notFound **[reuse]** (drop id, or add `sprint.notFoundWithId` if id must show — DECISION) |
| **issues → NEW slice** | IssueService:301 | `Issue not found` | issue.notFoundGeneric **[reuse new]** |
| **issues → NEW slice** | IssueService:393 | `Assignee not found: {0}` | issue.assigneeNotFound **[reuse P1]** |

### Construct 3 — `error()`/`check()` (behavior change: 500 → correct status)

| Module → slice | Site | Message | Disposition |
|---|---|---|---|
| servicedesk → NEW slice | ServiceDeskController:32,38,51,72,87; IncidentController:50 | `Project not found: {0}` | → `NotFoundException.keyed("project.notFound", key)` **[reuse]** (500→404) |
| servicedesk → NEW slice | ServiceDeskController:40,73,88 | `Service desk not enabled for project: {0}` | → `NotFoundException.keyed("serviceDesk.notEnabled", key)` **[new]** (500→404) |
| organizations → Task 6 | OrganizationService:30 `findBySlug` | `Org not found: {0}` | → `NotFoundException.keyed("org.notFound", slug)` **[new]** (500→404). Verify callers don't depend on ISE. |
| auth SSO | SsoController:35 `clientSecret required` | **SKIP** — internal/config invariant (not user-input error) |
| auth SSO | OidcUserProvisioningService:28 `OIDC user has no email` | **SKIP** — internal invariant (Wolfgang-flagged) |
| auth SSO | OidcUserProvisioningService:31 `check(autoProvision)` "Auto-provisioning disabled" | → `ForbiddenException.keyed("auth.autoProvisionDisabled")` **[new]** (user-facing 403) — DECISION: include or skip |

### Construct 4 — `ResponseStatusException`

| Module → slice | Site | Message | Disposition |
|---|---|---|---|
| auth → follow-up | AuthController:55 | `Not a member of this organization` (403) | → `ForbiddenException.keyed("org.notMemberCurrent")` **[new]** (auth slice already merged → handle in the correction slice, not a re-open of PR #84) |
| servicedesk → NEW slice | IncidentController:32 | `Invalid severity '{0}'. Must be one of: {1}` (400) | → `BadRequestException.keyed("incident.invalidSeverity", value, allowed)` **[new]** |
| servicedesk → NEW slice | ServiceDeskController:77 | `Invalid priority: {0}. Valid values: {1}` (400) | → `BadRequestException.keyed("serviceDesk.invalidPriority", value, allowed)` **[new]** |

### Construct 5 — manual `AccessDeniedException`

| Module → slice | Site | Message | Disposition |
|---|---|---|---|
| organizations → Task 6 | OrganizationService:82 | `Not a member of organization {0}` (403) | → `ForbiddenException.keyed("org.notMember", orgId)` **[new]**. NOTE: `AccessDeniedException` is Spring-Security-handled (returns `ex.message`); switching to `ForbiddenException` keeps 403 but routes through the keyed path. Verify no security filter depends on the AccessDeniedException type here (it's thrown in service code, not a filter). |

## Explicitly OUT of scope (leave as-is)

- `IncomingWebhookService` `SecurityException` ×2 (webhook signature/token) — security internals, not user-facing localization.
- SSO `clientSecret required`, `OIDC user has no email` — internal invariants.
- `log.error(...)` in `EmailIngestionService:47` — logging, not an exception.

## Revised slice structure — APPROVED (Wolfgang, 2026-07-19)

Deliver by **extending the existing plan doc in place** (`2026-07-19-backend-i18n-phase2-api-error-sweep.md`): Tasks 2–7 keep their construct-1 `throw`-keyword swaps + en/de catalog blocks; add to each an "orElseThrow / error / AccessDenied sites" subsection (extra keys + swaps); update header counts (81 → ~122); append two new tasks. This doc is the authoritative site index the plan references.

- **Task 8 — `issues` slice** (5 orElseThrow sites; mostly reuse P1 keys + `issue.notFoundGeneric`, `issue.parentNotFound`).
- **Task 9 — `servicedesk` slice** (9 error() + 2 ResponseStatusException; new `serviceDesk.*` + `incident.*` keys; **fixes 6× 500→404**). Needs its own localization integration test — check existing servicedesk tests for setup (`ServiceDeskServiceTest`, `IncidentServiceTest`, `SlaMonitorJobTest`).

**Approved session grouping (4 sessions remain):**
- **S2** = Task 2 (projects+boards) + Task 3 (workflow) — incl. orElseThrow.
- **S3** = Task 4 (agile-reports) + Task 8 (issues).
- **S4** = Task 5 (integrations) + Task 6 (orgs — incl. `error()` `org.notFound`, `AccessDeniedException`, `org.memberNotFound`, and the AuthController:55 `ResponseStatusException` "Not a member of this organization" auth follow-up) + Task 7 (content — incl. attachment/comment/customField/label/version `.notFound`).
- **S5** = Task 9 (servicedesk) — the 500→404 fixes + status-change tests.

## Resolved decisions — APPROVED (Wolfgang, 2026-07-19)

1. **Sprint-not-found:** unify to ONE key `sprint.notFound = "Sprint not found: {0}"` and pass the in-scope `sprintId` at all 4 sites (ReportsService:32,72; SprintService:115; IssueService:170). Matches the codebase's `*.notFound` id-in-message convention. No `sprint.notFoundWithId`.
2. **`auth.autoProvisionDisabled`:** INCLUDE as keyed `ForbiddenException` (403) for `OidcUserProvisioningService:31` `check`. Still SKIP the two internal invariants (`SsoController:35` `clientSecret required`, `OidcUserProvisioningService:28` `OIDC user has no email`) and the webhook `SecurityException`s.
3. **Status-change tests:** every construct-3/4 conversion that changes status (6× 500→404 in servicedesk; the RSE→400s) gets a test asserting the NEW status + localized body; grep existing tests for any asserting the old 500/RSE behavior and update them.
4. **No new handler needed** provided every user-facing `error()`/`check()` site is converted to a keyed exception; the sweep must leave no user-facing `IllegalStateException` (final-verification check).

## Gates already in place (from Session 1 / Task 0)

- `KeyedReferenceIntegrityTest` auto-guards every new `.keyed("k")` (fails build if a key is missing) — covers all construct conversions.
- `MessagesParityTest` guards en/de key-set + `{n}` placeholder parity.
- Add per-slice resolution tests (en+de) for the new keys, as in the existing plan.
