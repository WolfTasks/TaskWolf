# Phase 7 Post-Review Fixes

Applied 2026-06-23.

## Fix 1 (Critical): SLA spam on resolved issues — APPLIED

**Files:** `SlaEventListener.kt`, `SlaMonitorJob.kt`

- `SlaEventListener.onStatusChanged` now loads the issue unconditionally and clears `slaStartTime` when `newStatus.category == StatusCategory.DONE`. Previously only handled IN_PROGRESS.
- `SlaMonitorJob.run` adds a null guard (`if (issue.slaStartTime == null) return@forEach`) to skip issues in the race window between the query and the loop body.
- Deviation: fix spec used `e.issueId` but the actual event field is `e.issue.id` — corrected to match the real domain event.

## Fix 2 (Critical): OIDC provisioned users orphaned from multi-tenancy — APPLIED

**Files:** `OidcUserProvisioningService.kt`

- Injected `OrganizationRepository` and `OrganizationMemberRepository`.
- After saving the new user, looks up `orgRepo.findBySlug("default")` and saves an `OrganizationMember` with `OrgRole.MEMBER` if found.
- `generateAccessToken(user.id)` updated to `generateAccessToken(user.id, defaultOrg?.id)` so the JWT carries the org claim.
- Existing users on re-login also get the org claim via the `defaultOrg` lookup before token generation.

## Fix 3 (Important): Public SSO list endpoint exposes full provider inventory — APPLIED

**Files:** `SsoController.kt`, `SecurityConfig.kt`, `frontend/src/api/sso.ts`, `frontend/src/pages/auth/LoginPage.tsx`

- Added `GET /api/v1/admin/sso/public` endpoint returning only `{id, name}` for enabled configs.
- Existing `GET /api/v1/admin/sso` now requires `ADMIN` role.
- `SecurityConfig` permit-all matcher updated from `/api/v1/admin/sso` to `/api/v1/admin/sso/public`.
- Frontend `ssoApi` gains `listPublic()` and `SsoConfigPublic` type.
- `LoginPage` uses `ssoApi.listPublic` / `SsoConfigPublic` instead of the full admin list.

## Fix 4 (Important): SubmitTicketRequest has no validation — APPLIED

**Files:** `SubmitTicketRequest.kt`, `ServiceDeskController.kt`

- Added `@field:NotBlank @field:Size(max=255)` on `title`, `@field:Size(max=5000)` on `description`, `@field:Size(max=255)` on `senderEmail`. Fields have default values per spec.
- `ServiceDeskController.submitTicket` parameter annotated with `@Valid`.

## Fix 5 (Important): SYSTEM_USER_ID may violate comments FK — APPLIED

**Files:** `Comment.kt`, `CommentResponse.kt`, `ActivityService.kt`, `IncidentService.kt`, `V21__nullable_comment_author.sql`

- FK exists: `author_id UUID NOT NULL REFERENCES users(id)` in V7. No system user row seeded.
- `Comment.authorId` made nullable (`UUID?`). Migration V21 drops the NOT NULL constraint.
- `CommentResponse.authorId` updated to `UUID?`.
- `ActivityService.onCommentCreated` skips activity recording when `authorId` is null (system comment).
- `IncidentService.SYSTEM_USER_ID` changed from `UUID(0,0)` to `null`, with explanatory comment.

## Fix 6 (Minor): Remove test_output.txt — APPLIED

- `git rm --cached backend/test_output.txt`
- `test_output.txt` added to root `.gitignore` and `backend/.gitignore`.

## Fix 7 (Minor): ServiceDeskPage SLA field names — APPLIED

**File:** `frontend/src/pages/projects/servicedesk/ServiceDeskPage.tsx`

- `computeSlaStatus` parameter type changed from `{ durationMinutes: number }` to `{ resolutionMinutes: number }`.
- All references to `slaPolicy.durationMinutes` replaced with `slaPolicy.resolutionMinutes`.
- Policy matching simplified from `p.issueType === t.type || p.priority === t.priority` to `p.priority === t.priority`.

## Fix 8 (Minor): IncidentDashboardPage postmortem reference — APPLIED

**File:** `frontend/src/pages/projects/servicedesk/IncidentDashboardPage.tsx`

- Replaced `inc.postmortemIssueId` with `inc.postmortemBody` in the conditional display block.

## Test Results

- Backend: `./gradlew test` — BUILD SUCCESSFUL (28s, 1 deprecation warning pre-existing in SsrfValidator)
- Frontend: `npm run build` — SUCCESS, zero TypeScript errors (pre-existing bundle size warning only)
