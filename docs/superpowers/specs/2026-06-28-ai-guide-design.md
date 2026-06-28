# AI Implementation Guide — Design Spec

**Date:** 2026-06-28
**Status:** Approved
**Audience:** AI coding assistants + human contributors

---

## Problem

AI coding assistants (Claude and others) bypass the developer wiki and infer implementation patterns directly from existing source code. This causes pattern drift: the AI copies the style of the nearest or most recently written file instead of following the canonical pattern documented in the wiki. The result is inconsistent implementations that diverge from architectural decisions.

---

## Goal

Add a single `developer-guide/ai-guide.md` page that:

1. Forces AI to follow a mandatory pre-implementation checklist before writing any code.
2. Provides a canonical pattern catalogue (one real code snippet per layer) that AI copies *from the wiki* rather than from source files.
3. Records architecture decisions (what was chosen, what was rejected, why) so AI understands the reasoning and doesn't revert to rejected alternatives.

Primary reader: AI. Dense, structured, no prose padding.

---

## File Location

```
mkdocs/developer-guide/ai-guide.md
```

Added to `mkdocs.yml` nav immediately after `developer-guide/index.md`:

```yaml
- Developer Guide:
    - Overview: developer-guide/index.md
    - AI Implementation Guide: developer-guide/ai-guide.md
    - Conventions: developer-guide/conventions.md
    - Backend: ...
    - Frontend: ...
```

Also add a prominent link in `developer-guide/index.md` — the first item under "How To Use This Guide."

---

## Page Structure

### Section 1: Pre-Implementation Checklist

A numbered mandatory checklist. AI must complete every item before writing any code:

1. Read `conventions.md` — all cross-cutting rules apply to every module.
2. Read the relevant module page(s) for the area being modified.
3. Check current Flyway version (V25 as of 2026-06-28 — next migration must be V26).
4. Run `cd backend && ./gradlew test` to establish a baseline before touching anything.
5. Identify which pattern sections below apply to the layers being touched.
6. **Do not infer patterns from source code.** Source files reflect history, not intent. This page is the source of truth.

### Section 2: Pattern Catalogue

One subsection per layer. Each subsection contains:
- A canonical code snippet (extracted verbatim from the codebase — not invented).
- A `> DO NOT` callout naming the stale alternative the snippet replaces.

Layers covered, with their canonical source:

| Layer | Canonical source file |
|---|---|
| Backend Entity | `backend/src/main/kotlin/com/taskowolf/labels/domain/Label.kt` |
| Backend Repository | `LabelRepository` (interface, Spring Data method names) |
| Backend Service | `backend/src/main/kotlin/com/taskowolf/labels/application/LabelService.kt` |
| Backend Controller | `backend/src/main/kotlin/com/taskowolf/labels/api/LabelController.kt` |
| Domain Event | naming + placement convention from `issues/domain/events/` |
| Unit Test | `backend/src/test/kotlin/com/taskowolf/labels/LabelServiceTest.kt` |
| Flyway Migration | `backend/src/main/resources/db/migration/V23__labels.sql` |
| Frontend API module | `frontend/src/api/labels.ts` |
| Frontend Hook | `frontend/src/hooks/useLabels.ts` |
| Frontend Component | `@/components/ui/` import convention + Tailwind + `cn()` |

Key `DO NOT` callouts to include explicitly:
- DO NOT use Mockito — use MockK (`io.mockk`).
- DO NOT import shadcn components from the npm package — import from `@/components/ui/`.
- DO NOT call `apiClient` directly from a component — always via a hook.
- DO NOT use `@Query` JPQL for filterable list endpoints — use `JpaSpecificationExecutor`.
- DO NOT inject a `@Service` from another module — publish a `DomainEventPublisher` event.
- DO NOT use field injection (`@Autowired`) — use constructor injection.
- DO NOT copy entity fields `id`, `createdAt`, `updatedAt` — they are inherited from `AuditableEntity`.

### Section 3: Architecture Decision Records

Compact table format. Three columns: Decision | Rejected alternative | Reason.

| Decision | Rejected | Reason |
|---|---|---|
| MockK (`io.mockk`) | Mockito | Kotlin-idiomatic DSL; `every {}` matches Kotlin syntax; Mockito requires Java-style verbosity |
| `DomainEventPublisher` for cross-module side effects | Direct `@Service` injection across modules | Enforces decoupling; modules can evolve independently; prevents circular dependencies |
| React Query for all server state | `useState` + `useEffect` + manual fetch | Automatic caching, background refetch, loading/error state; no boilerplate |
| Zustand for UI-only state | Redux, or React Query for UI state | Minimal boilerplate; clear boundary: Zustand = what the UI shows, React Query = what the server says |
| Project key in all URLs (`/api/v1/projects/{key}/...`) | Project UUID in URLs | Human-readable; enables future nginx sharding by key prefix |
| JPA `Specification<T>` for composable filters | if-return-early JPQL query | Adding a new filter doesn't require rewriting the query; filters compose with AND |
| shadcn/ui as local copy in `frontend/src/components/ui/` | Importing directly from shadcn npm package | Components are owned code — customizable without upstream library changes |
| Hexagonal package structure (`domain/application/infrastructure/api`) | Flat by feature or flat by layer | Enforces dependency direction: domain has no Spring imports; infrastructure imports domain, never the reverse |
| `AuditableEntity` base class for all `@Entity` | Declaring `id`/`createdAt`/`updatedAt` on each entity | Single source of truth; consistent UUID generation; `@CreatedDate`/`@LastModifiedDate` wired once |

---

## Scope Constraints

- All code snippets must be extracted from actual source files — never invented.
- The checklist is mandatory prose, not optional guidance.
- ADR reasons must be one sentence — no multi-sentence explanations.
- The page must not duplicate prose rules already in `conventions.md` — cross-link instead. Snippets are not duplication: `conventions.md` states the rule, this page shows the canonical copy-paste form of that rule.
- Page length target: dense but readable in under 5 minutes.

---

## Out of Scope

- Per-module pitfalls (belong in each module's `Common Pitfalls` section).
- Deployment or CI guidance (belongs in `development.md`).
- Onboarding for human developers (belongs in `getting-started.md`).
