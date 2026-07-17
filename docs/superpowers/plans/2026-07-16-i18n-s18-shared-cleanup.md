# i18n Rollout — Session 18 (`shared`/cleanup — final sweep) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Localize the final two allowlisted files — `VersionTag` (S2-deferred app-version chrome) and `AuthLayout` (brand heading) — empty `frontend/scripts/i18n-allowlist.json` to `[]`, run the scanner-to-zero sweep across the whole `.tsx` surface, and mark **the entire #15 i18n full-rollout complete** in the master-spec coverage matrix.

**Architecture:** Thin execution plan against the locked pattern in `docs/superpowers/specs/2026-07-13-i18n-full-rollout-design.md` (Anhang — Migrations-Checkliste). This slice needs **no new namespace and no locale keys** — both remaining flags are non-translatable chrome. `VersionTag`'s `v{__APP_VERSION__}` is folded into a single JSX expression (the `v` is version-format, not UI copy). `AuthLayout`'s `🐺 TaskWolf` is a brand proper-noun that "stays branded" (foundation decision), handled with the same `{/* i18n-ignore: brand name, not translated */}` mechanism the pilot already uses in `AppLayout.tsx:58`. The matrix's other S18 names (DataTable, NavItem, SidebarSection, StatusBadge, table components) are already string-free and were never allowlisted — the empty-allowlist scan proves it.

**Tech Stack:** the Session-0 scanner/parity scripts (`scan:i18n`/`check:i18n`/`test:i18n`/`lint:i18n`), Node 20, Vite/tsc build. No react-i18next changes.

## Global Constraints

- **No new namespace, no new keys.** Both files are non-translatable chrome; do not add locale entries.
- **`VersionTag`: fold the `v` into the expression.** Change `v{__APP_VERSION__}` to `{\`v${__APP_VERSION__}\`}` so no bare JSX text child with letters remains — the scanner only flags JSX text children + `placeholder`/`title`/`aria-label`/`alt`/`label` attributes, and a JSX expression container is neither. Rendering is unchanged (`v1.0.xx`).
- **`AuthLayout`: keep the brand literal, mark it ignored.** `🐺 TaskWolf` stays exactly as-is; add the line comment `{/* i18n-ignore: brand name, not translated */}` immediately before the `<h1>` — the identical mechanism used in `frontend/src/layouts/AppLayout.tsx:58`. Do NOT route the brand through `t()`.
- **Empty the allowlist.** After both edits, `frontend/scripts/i18n-allowlist.json` must be exactly `[]`. **Net for S18: 2 files removed (2 → 0). Allowlist is now empty — the whole-project finish line.**
- **Fertig-Kriterium of the entire #15 rollout:** `i18n-allowlist.json === []` **and** scanner green (0 hardcoded across all `.tsx`) **and** en/de parity green across all namespaces.
- **Done-per-slice (master spec Abschnitt 5):** `npm run test:i18n && npm run lint:i18n && npm run build` all green; manual DE/EN browser check; matrix row flipped to ✅.
- All paths relative to repo root `C:\Users\Admin\IdeaProjects\TaskWolf`. Work in an isolated worktree branched from `origin/main` (which must contain merged S0–S17; the allowlist starts at 2).

---

### Task 1: Localize VersionTag (fold the `v` prefix)

**Files:**
- Modify: `frontend/src/components/VersionTag.tsx`
- Modify: `frontend/scripts/i18n-allowlist.json`

- [ ] **Step 1: Fold the `v` into the version expression**

Replace:

```tsx
    <span className={`text-xs text-gray-500 ${className}`}>
      v{__APP_VERSION__}
    </span>
```

with:

```tsx
    <span className={`text-xs text-gray-500 ${className}`}>
      {`v${__APP_VERSION__}`}
    </span>
```

- [ ] **Step 2: Remove VersionTag from the allowlist**

In `frontend/scripts/i18n-allowlist.json`, delete this line:

```json
  "src/components/VersionTag.tsx",
```

- [ ] **Step 3: Verify scanner + build**

Run: `cd frontend && npm run scan:i18n && npm run build`
Expected: `i18n-scan: OK — 0 hardcoded strings outside the allowlist (1 file(s) still allowlisted).` and a successful build. If the scanner still flags `VersionTag.tsx`, confirm the `v` is now inside the `{\`v${…}\`}` expression (no bare `v` JSX text remains).

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/VersionTag.tsx frontend/scripts/i18n-allowlist.json
git commit -m "feat(i18n): make version tag scanner-clean (fold v prefix into expression)"
```

---

### Task 2: Mark the AuthLayout brand as an i18n-ignore + empty the allowlist

**Files:**
- Modify: `frontend/src/layouts/AuthLayout.tsx`
- Modify: `frontend/scripts/i18n-allowlist.json`

- [ ] **Step 1: Add the i18n-ignore comment above the brand heading**

Replace:

```tsx
        <h1 className="text-3xl font-bold text-white text-center mb-1">🐺 TaskWolf</h1>
```

with:

```tsx
        {/* i18n-ignore: brand name, not translated */}
        <h1 className="text-3xl font-bold text-white text-center mb-1">🐺 TaskWolf</h1>
```

(Matches `frontend/src/layouts/AppLayout.tsx:58`. The brand text is unchanged.)

- [ ] **Step 2: Remove AuthLayout from the allowlist — it is now empty**

In `frontend/scripts/i18n-allowlist.json`, delete this line:

```json
  "src/layouts/AuthLayout.tsx"
```

The file must now contain exactly an empty array:

```json
[]
```

- [ ] **Step 3: Verify scanner + build (allowlist empty)**

Run: `cd frontend && npm run scan:i18n && npm run build`
Expected: `i18n-scan: OK — 0 hardcoded strings outside the allowlist (0 file(s) still allowlisted).` and a successful build. If the scanner flags `AuthLayout.tsx`, confirm the `{/* i18n-ignore: brand name, not translated */}` comment is on the line **immediately before** the `<h1>` (the pilot pattern). If it flags any *other* file (a page added during the rollout), localize it under the most fitting existing namespace, or add its `// i18n-ignore` if it is genuine chrome — the allowlist must stay `[]`.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/layouts/AuthLayout.tsx frontend/scripts/i18n-allowlist.json
git commit -m "feat(i18n): mark auth-layout brand i18n-ignore; empty the allowlist"
```

---

### Task 3: Final scanner-to-zero sweep + mark the whole rollout complete

**Files:**
- Modify: `docs/superpowers/specs/2026-07-13-i18n-full-rollout-design.md`

- [ ] **Step 1: Run the full gate with an empty allowlist**

Run: `cd frontend && npm run test:i18n && npm run lint:i18n && npm run build`
Expected: scanner self-tests pass, `i18n-scan: OK — 0 hardcoded strings outside the allowlist (0 file(s) still allowlisted).`, `i18n-parity: OK — en/de namespaces and keys match.`, and a successful build. Confirm `frontend/scripts/i18n-allowlist.json` is exactly `[]`.

- [ ] **Step 2: Confirm the whole-project finish criterion**

Verify all three hold (master spec Fertig-Kriterium):
1. `cat frontend/scripts/i18n-allowlist.json` → `[]`
2. `npm run scan:i18n` → `0 hardcoded strings … (0 file(s) still allowlisted)`
3. `npm run check:i18n` → `en/de namespaces and keys match`

- [ ] **Step 3: Manual DE/EN browser check**

Start the dev server (`cd frontend && npm run dev`), switch language via Settings → Profile, and confirm: the version tag still reads `v1.0.xx` in both languages, the auth (login/register) screen brand `🐺 TaskWolf` stays branded (unchanged) in both languages, and — spot-checking a few pages from earlier slices — no raw keys appear anywhere.

- [ ] **Step 4: Flip S18 to ✅ and record project completion in the coverage matrix**

In `docs/superpowers/specs/2026-07-13-i18n-full-rollout-design.md`, replace the S18 matrix row:

```
| S18 | `shared`/Cleanup | ⬜ | DataTable, Table-Komponenten, NavItem, SidebarSection, VersionTag/StatusBadge falls geteilt, Rest-`common`; **finaler Scanner-auf-Null-Sweep** (Allowlist muss danach leer sein) |
```

with:

```
| S18 | `shared`/Cleanup | ✅ | VersionTag folded (`{\`v${__APP_VERSION__}\`}`); AuthLayout brand `i18n-ignore` (pilot pattern); DataTable/NavItem/SidebarSection/StatusBadge/table components already string-free (never allowlisted). **Allowlist = `[]`; scanner 0; parity green — #15 i18n full-rollout COMPLETE.** |
```

Then, just under the Abschnitt-4 legend note, add a one-line completion banner (optional but recommended):

```markdown
> ✅ **ROLLOUT COMPLETE (S0–S18):** every user-facing frontend string runs through `t()`; `i18n-allowlist.json = []`; scanner + en/de parity green in CI. Backend `MessageSource` remains backlog **#16**.
```

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/specs/2026-07-13-i18n-full-rollout-design.md
git commit -m "docs(i18n): mark S18 + entire #15 i18n full-rollout complete (allowlist empty)"
```

---

## Self-Review

**Spec coverage:**
- `VersionTag` made scanner-clean without a bogus key (folded `v` into the expression) → Task 1. ✅
- `AuthLayout` brand kept literal + `i18n-ignore` (pilot `AppLayout` pattern) → Task 2. ✅
- Allowlist emptied to `[]` (2 → 0) → Tasks 1–2. ✅
- Whole-project Fertig-Kriterium (allowlist `[]`, scanner 0, parity green) verified → Task 3. ✅
- Matrix S18 flipped + rollout-complete banner → Task 3. ✅
- No new namespace/keys (both files are non-translatable chrome) → Global Constraints. ✅

**Placeholder scan:** No TBD/TODO; every step has exact old/new code and exact commands with expected output.

**Type/key consistency:** No translation keys are introduced in this slice (chrome only), so there is nothing to cross-check against locale JSON. Allowlist arithmetic: 2 → 1 (Task 1) → 0 (Task 2). The empty-allowlist scan (Task 2 Step 3, Task 3 Step 1) is the authoritative proof that the remaining matrix-named shared components (DataTable/NavItem/SidebarSection/StatusBadge/table) are already string-free.
