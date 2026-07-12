# i18n Foundation + Pilot Slice — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish frontend i18n infrastructure (react-i18next) with a DE/EN language switcher persisted per-user, and fully translate one pilot slice (nav chrome + auth + settings) as a copyable pattern.

**Architecture:** `react-i18next` + `i18next` + `i18next-browser-languagedetector` initialised once in `src/i18n/`, consumed via `useTranslation(ns)`. Language is detected (backend pref → localStorage → browser), persisted to `localStorage` immediately and to a new nullable `users.language` column via `PATCH /api/v1/me/language`. Backend server-rendered texts are NOT localised in this cycle.

**Tech Stack:** Backend Kotlin + Spring Boot + Flyway + JUnit/MockK (TDD). Frontend React 19 + TypeScript + Vite; **no test framework** → verification is `npm run build` (tsc typecheck + Vite build) plus scripted manual browser checks.

## Global Constraints

- **Supported languages:** `en`, `de` only. **Fallback / default language: `en`** (verbatim).
- **Backend texts stay as-is** — only the client is translated this cycle (no Spring `MessageSource`).
- **Invalid language value → HTTP 400 with a generic message.** Never leak enum names / `valueOf` exception text (H2 backlog lesson).
- **Translation keys are semantic/hierarchical** (`profile.displayName`), never the English text as key. Interpolation via variables only (`t('k', { name })`) — never concatenate translated fragments.
- **Both `en` and `de` locale files must stay key-identical** for every namespace the pilot touches.
- **Backend uses MockK** for unit tests (not Mockito). Latest Flyway migration is **V29** → new migration is **V30**.
- **Frontend has no test runner:** each frontend task's "test" step is `npm run build` from `frontend/` plus the listed manual browser checks. Commit only after the build is green.

---

## File Structure

**Backend (create/modify):**
- Modify `backend/src/main/kotlin/com/taskowolf/auth/domain/User.kt` — add `language` field.
- Create `backend/src/main/resources/db/migration/V30__add_user_language.sql`.
- Modify `backend/src/main/kotlin/com/taskowolf/auth/api/dto/UserResponse.kt` — expose `language`.
- Create `backend/src/main/kotlin/com/taskowolf/auth/api/dto/UpdateLanguageRequest.kt`.
- Modify `backend/src/main/kotlin/com/taskowolf/auth/api/MeController.kt` — `PATCH /me/language`.
- Modify `backend/src/main/kotlin/com/taskowolf/auth/application/UserAccountService.kt` — `updateLanguage`.
- Create `backend/src/test/kotlin/com/taskowolf/auth/UserLanguageServiceTest.kt` (MockK unit).
- Create `backend/src/test/kotlin/com/taskowolf/auth/UserLanguageIntegrationTest.kt`.

**Frontend (create/modify):**
- Create `frontend/src/i18n/index.ts` — i18next init.
- Create `frontend/src/i18n/format.ts` — Intl helpers.
- Create `frontend/src/i18n/locales/{en,de}/{common,nav,auth,settings}.json`.
- Modify `frontend/src/main.tsx` — `import './i18n'`.
- Modify `frontend/src/types/index.ts` — `User.language`.
- Modify `frontend/src/api/me.ts` — `updateLanguage`.
- Modify `frontend/src/hooks/useMe.ts` — `useUpdateLanguage`.
- Create `frontend/src/hooks/useLanguageSync.ts` — apply backend pref after `/me` load.
- Create `frontend/src/components/LanguageSwitcher.tsx`.
- Modify `frontend/src/pages/settings/ProfilePage.tsx` — mount switcher.
- Modify `frontend/src/layouts/AppLayout.tsx` — nav namespace + language sync.
- Modify `frontend/src/pages/auth/{LoginPage,RegisterPage}.tsx` — auth namespace.
- Modify `frontend/src/pages/settings/{ProfilePage,SecurityPage,AccountSettingsPage,NotificationSettingsPage}.tsx` — settings namespace.
- Docs: `docs/` wiki i18n conventions page.

---

## Task 1: Backend — `language` column, entity field, and `/auth/me` exposure

**Files:**
- Modify: `backend/src/main/kotlin/com/taskowolf/auth/domain/User.kt`
- Create: `backend/src/main/resources/db/migration/V30__add_user_language.sql`
- Modify: `backend/src/main/kotlin/com/taskowolf/auth/api/dto/UserResponse.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/auth/UserLanguageIntegrationTest.kt`

**Interfaces:**
- Produces: `User.language: String?`; `UserResponse(..., language: String?)`; `GET /api/v1/auth/me` JSON now contains `"language"` (null by default).

- [ ] **Step 1: Write the failing integration test**

Create `backend/src/test/kotlin/com/taskowolf/auth/UserLanguageIntegrationTest.kt`. Mirror an existing auth integration test's bootstrapping (see `UserActiveIntegrationTest.kt` for the `@SpringBootTest` + MockMvc + auth-token setup pattern used in this module — copy its class-level annotations and helper for obtaining a logged-in user's JWT).

```kotlin
package com.taskowolf.auth

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath

@SpringBootTest
@AutoConfigureMockMvc
class UserLanguageIntegrationTest {
    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper

    // Reuse the module's existing helper to register+login a user and get a Bearer token.
    // Replace `authHeader()` below with that helper (see UserActiveIntegrationTest).

    @Test
    fun `auth me returns null language by default`() {
        val token = authHeader() // -> "Bearer <jwt>"
        mockMvc.get("/api/v1/auth/me") { header("Authorization", token) }
            .andExpect { jsonPath("$.language") { value(null) } }
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.auth.UserLanguageIntegrationTest"`
Expected: FAIL — `$.language` does not exist in the response (or compile error: `authHeader` unresolved until you wire the existing helper).

- [ ] **Step 3: Add the migration**

Create `backend/src/main/resources/db/migration/V30__add_user_language.sql`:

```sql
ALTER TABLE users ADD COLUMN language varchar(8);
```

- [ ] **Step 4: Add the entity field**

In `backend/src/main/kotlin/com/taskowolf/auth/domain/User.kt`, add after the `deletedAt` property (keep it a constructor property so JPA maps it):

```kotlin
    @Column(name = "language")
    var language: String? = null
```

- [ ] **Step 5: Expose it in `UserResponse`**

Replace the body of `backend/src/main/kotlin/com/taskowolf/auth/api/dto/UserResponse.kt`:

```kotlin
data class UserResponse(
    val id: UUID,
    val email: String,
    val displayName: String,
    val avatarUrl: String?,
    val role: String,
    val language: String?
) {
    companion object {
        fun from(user: User) =
            UserResponse(user.id, user.email, user.displayName, user.avatarUrl, user.systemRole.name, user.language)
    }
}
```

- [ ] **Step 6: Run the test — it passes**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.auth.UserLanguageIntegrationTest"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/auth/domain/User.kt \
        backend/src/main/resources/db/migration/V30__add_user_language.sql \
        backend/src/main/kotlin/com/taskowolf/auth/api/dto/UserResponse.kt \
        backend/src/test/kotlin/com/taskowolf/auth/UserLanguageIntegrationTest.kt
git commit -m "feat(auth): add nullable user language column exposed on /auth/me"
```

---

## Task 2: Backend — `PATCH /api/v1/me/language` with validation

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/auth/api/dto/UpdateLanguageRequest.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/auth/application/UserAccountService.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/auth/api/MeController.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/auth/UserLanguageServiceTest.kt` (MockK unit)
- Test: extend `backend/src/test/kotlin/com/taskowolf/auth/UserLanguageIntegrationTest.kt`

**Interfaces:**
- Consumes: `User.language` (Task 1).
- Produces: `UserAccountService.updateLanguage(userId: UUID, language: String): User`; `PATCH /api/v1/me/language` accepting `{ "language": "de" }`, returning `UserResponse`; unknown value → 400.

- [ ] **Step 1: Write the failing MockK unit test**

Create `backend/src/test/kotlin/com/taskowolf/auth/UserLanguageServiceTest.kt`. Construct `UserAccountService` with mockk dependencies (mirror `UserAccountServiceTest.kt` for how the ctor args are mocked in this module).

```kotlin
package com.taskowolf.auth

import com.taskowolf.auth.application.UserAccountService
import com.taskowolf.auth.domain.User
import com.taskowolf.core.infrastructure.ValidationException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals

class UserLanguageServiceTest {
    // NOTE: match the real ctor arg order/types from UserAccountService.
    private val userRepository = mockk<com.taskowolf.auth.infrastructure.UserRepository>()
    private val accessTokenService = mockk<com.taskowolf.auth.application.AccessTokenService>(relaxed = true)
    private val refreshTokenService = mockk<com.taskowolf.auth.application.RefreshTokenService>(relaxed = true)
    private val passwordEncoder = mockk<PasswordEncoder>(relaxed = true)
    private val securityAuditListener = mockk<com.taskowolf.audit.application.SecurityAuditListener>(relaxed = true)
    private val service = UserAccountService(
        userRepository, accessTokenService, refreshTokenService, passwordEncoder, securityAuditListener
    )

    @Test
    fun `updateLanguage persists a supported language`() {
        val id = UUID.randomUUID()
        val user = User(email = "a@b.c", displayName = "A")
        every { userRepository.findById(id) } returns Optional.of(user)
        val saved = slot<User>()
        every { userRepository.save(capture(saved)) } answers { saved.captured }

        service.updateLanguage(id, "de")

        assertEquals("de", saved.captured.language)
        verify { userRepository.save(any()) }
    }

    @Test
    fun `updateLanguage rejects an unsupported language`() {
        val id = UUID.randomUUID()
        every { userRepository.findById(id) } returns Optional.of(User(email = "a@b.c", displayName = "A"))
        assertThrows<ValidationException> { service.updateLanguage(id, "fr") }
    }
}
```

> If the codebase's 400-mapping exception is named differently than `ValidationException` (check `com.taskowolf.core.infrastructure` and `GlobalExceptionHandler` for the type that maps to HTTP 400 — the same one used for bad input elsewhere), use that type in both the test and the service. Do not introduce a new exception type.

- [ ] **Step 2: Run it and watch it fail**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.auth.UserLanguageServiceTest"`
Expected: FAIL — `updateLanguage` unresolved.

- [ ] **Step 3: Implement `updateLanguage` in the service**

In `backend/src/main/kotlin/com/taskowolf/auth/application/UserAccountService.kt`, add a companion set of supported languages and the method (use the project's 400-mapped exception type confirmed in Step 1):

```kotlin
    @Transactional
    fun updateLanguage(userId: UUID, language: String): User {
        if (language !in SUPPORTED_LANGUAGES) {
            throw ValidationException("Unsupported language")
        }
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found") }
        user.language = language
        return userRepository.save(user)
    }

    companion object {
        private val SUPPORTED_LANGUAGES = setOf("en", "de")
    }
```

Add the matching import for the 400-exception type at the top of the file.

- [ ] **Step 4: Add the request DTO**

Create `backend/src/main/kotlin/com/taskowolf/auth/api/dto/UpdateLanguageRequest.kt`:

```kotlin
package com.taskowolf.auth.api.dto

import jakarta.validation.constraints.NotBlank

data class UpdateLanguageRequest(
    @field:NotBlank val language: String
)
```

- [ ] **Step 5: Add the controller endpoint**

In `backend/src/main/kotlin/com/taskowolf/auth/api/MeController.kt`, add (and import `UpdateLanguageRequest`):

```kotlin
    @PatchMapping("/language")
    fun updateLanguage(
        @Valid @RequestBody request: UpdateLanguageRequest,
        @AuthenticationPrincipal user: User
    ) = UserResponse.from(userAccountService.updateLanguage(user.id, request.language))
```

- [ ] **Step 6: Run the unit test — it passes**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.auth.UserLanguageServiceTest"`
Expected: PASS.

- [ ] **Step 7: Add integration coverage for the endpoint**

Append to `UserLanguageIntegrationTest.kt`:

```kotlin
    @Test
    fun `patch me language persists and 400s on unknown value`() {
        val token = authHeader()
        mockMvc.patch("/api/v1/me/language") {
            header("Authorization", token)
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """{"language":"de"}"""
        }.andExpect { status { isOk() } }.andExpect { jsonPath("$.language") { value("de") } }

        mockMvc.patch("/api/v1/me/language") {
            header("Authorization", token)
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """{"language":"fr"}"""
        }.andExpect { status { isBadRequest() } }
    }
```

Add imports `org.springframework.test.web.servlet.patch` and `status`.

- [ ] **Step 8: Run the whole auth language suite + a full build**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.auth.UserLanguage*"`
Expected: PASS. Then `./gradlew build` — Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/auth/api/dto/UpdateLanguageRequest.kt \
        backend/src/main/kotlin/com/taskowolf/auth/application/UserAccountService.kt \
        backend/src/main/kotlin/com/taskowolf/auth/api/MeController.kt \
        backend/src/test/kotlin/com/taskowolf/auth/UserLanguageServiceTest.kt \
        backend/src/test/kotlin/com/taskowolf/auth/UserLanguageIntegrationTest.kt
git commit -m "feat(auth): PATCH /me/language with en/de validation"
```

---

## Task 3: Frontend — i18n infrastructure, `common` namespace, and Intl formatters

**Files:**
- Create: `frontend/src/i18n/index.ts`
- Create: `frontend/src/i18n/format.ts`
- Create: `frontend/src/i18n/locales/en/common.json`, `frontend/src/i18n/locales/de/common.json`
- Modify: `frontend/src/main.tsx`

**Interfaces:**
- Produces: side-effect module `@/i18n` (import for side effects); default export `i18n` instance; `common` namespace keys `save`, `cancel`, `saving`, `saved`, `loading`, `error`; `format.ts` exports `formatDate`, `formatDateTime`, `formatNumber`, `formatRelativeTime`.

- [ ] **Step 1: Install dependencies**

Run: `cd frontend && npm install i18next react-i18next i18next-browser-languagedetector`
Expected: three packages added to `dependencies` in `frontend/package.json`.

- [ ] **Step 2: Create the `common` locale files**

`frontend/src/i18n/locales/en/common.json`:

```json
{
  "save": "Save",
  "cancel": "Cancel",
  "saving": "Saving…",
  "saved": "Saved",
  "loading": "Loading…",
  "error": "Something went wrong"
}
```

`frontend/src/i18n/locales/de/common.json`:

```json
{
  "save": "Speichern",
  "cancel": "Abbrechen",
  "saving": "Speichern…",
  "saved": "Gespeichert",
  "loading": "Lädt…",
  "error": "Etwas ist schiefgelaufen"
}
```

- [ ] **Step 3: Create the i18n init module**

`frontend/src/i18n/index.ts`:

```ts
import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import LanguageDetector from 'i18next-browser-languagedetector'

import enCommon from './locales/en/common.json'
import deCommon from './locales/de/common.json'

export const SUPPORTED_LANGUAGES = ['en', 'de'] as const
export type AppLanguage = (typeof SUPPORTED_LANGUAGES)[number]

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      en: { common: enCommon },
      de: { common: deCommon },
    },
    fallbackLng: 'en',
    supportedLngs: [...SUPPORTED_LANGUAGES],
    nonExplicitSupportedLngs: true,
    defaultNS: 'common',
    ns: ['common'],
    interpolation: { escapeValue: false },
    detection: {
      order: ['localStorage', 'navigator'],
      lookupLocalStorage: 'taskowolf.lang',
      caches: ['localStorage'],
    },
    debug: import.meta.env.DEV,
  })

i18n.on('languageChanged', (lng) => {
  document.documentElement.lang = lng
})
document.documentElement.lang = i18n.language

export default i18n
```

> `resources`, `ns`, and the per-namespace imports grow in later tasks (nav/auth/settings). Backend-preference detection is applied at runtime by `useLanguageSync` (Task 5), not by the detector chain.

- [ ] **Step 4: Create the Intl format helpers**

`frontend/src/i18n/format.ts`:

```ts
import i18n from './index'

const locale = () => i18n.language || 'en'

export const formatDate = (d: Date | string | number) =>
  new Intl.DateTimeFormat(locale(), { dateStyle: 'medium' }).format(new Date(d))

export const formatDateTime = (d: Date | string | number) =>
  new Intl.DateTimeFormat(locale(), { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(d))

export const formatNumber = (n: number) =>
  new Intl.NumberFormat(locale()).format(n)

const RELATIVE_UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ['year', 31536000], ['month', 2592000], ['day', 86400],
  ['hour', 3600], ['minute', 60], ['second', 1],
]

export const formatRelativeTime = (d: Date | string | number) => {
  const rtf = new Intl.RelativeTimeFormat(locale(), { numeric: 'auto' })
  const diffSeconds = (new Date(d).getTime() - Date.now()) / 1000
  for (const [unit, secs] of RELATIVE_UNITS) {
    if (Math.abs(diffSeconds) >= secs || unit === 'second') {
      return rtf.format(Math.round(diffSeconds / secs), unit)
    }
  }
  return ''
}
```

- [ ] **Step 5: Wire i18n into app bootstrap**

In `frontend/src/main.tsx`, add the side-effect import above the `createRoot` call, after the other imports:

```ts
import './i18n'
```

- [ ] **Step 6: Verify the build is green**

Run: `cd frontend && npm run build`
Expected: `tsc` passes and Vite build succeeds (JSON module imports resolve; no type errors).

- [ ] **Step 7: Manual smoke check**

Run: `cd frontend && npm run dev`, open the app. Expected: app renders exactly as before (English default), no console errors from i18next. In devtools console, `window` unaffected; set `localStorage['taskowolf.lang'] = 'de'` and reload — no crash (nothing translated yet, but detector picks `de`).

- [ ] **Step 8: Commit**

```bash
git add frontend/package.json frontend/package-lock.json frontend/src/i18n frontend/src/main.tsx
git commit -m "feat(i18n): react-i18next infra, common namespace, Intl formatters"
```

---

## Task 4: Frontend — `language` on User type, API, and update hook

**Files:**
- Modify: `frontend/src/types/index.ts`
- Modify: `frontend/src/api/me.ts`
- Modify: `frontend/src/hooks/useMe.ts`

**Interfaces:**
- Consumes: `AppLanguage` (Task 3); `PATCH /me/language` (Task 2).
- Produces: `User.language: string | null`; `meApi.updateLanguage(language: string)`; `useUpdateLanguage()` mutation.

- [ ] **Step 1: Add `language` to the `User` type**

In `frontend/src/types/index.ts`, extend the `User` interface:

```ts
export interface User {
  id: string
  email: string
  displayName: string
  avatarUrl: string | null
  role: 'ADMIN' | 'MEMBER'
  language: string | null
}
```

- [ ] **Step 2: Add the API call**

In `frontend/src/api/me.ts`, add to the `meApi` object (import `User` type is already present):

```ts
  updateLanguage: (language: string) =>
    apiClient.patch<User>('/me/language', { language }),
```

- [ ] **Step 3: Add the mutation hook**

In `frontend/src/hooks/useMe.ts`, add:

```ts
export function useUpdateLanguage() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (language: string) => meApi.updateLanguage(language).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['me'] }),
  })
}
```

- [ ] **Step 4: Verify the build**

Run: `cd frontend && npm run build`
Expected: PASS (no type errors; every existing `User` construction still type-checks — `language` comes from the API, not constructed client-side).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/types/index.ts frontend/src/api/me.ts frontend/src/hooks/useMe.ts
git commit -m "feat(i18n): user language type, api, and update hook"
```

---

## Task 5: Frontend — LanguageSwitcher, backend-pref sync, and Profile mounting

**Files:**
- Create: `frontend/src/components/LanguageSwitcher.tsx`
- Create: `frontend/src/hooks/useLanguageSync.ts`
- Modify: `frontend/src/pages/settings/ProfilePage.tsx`

**Interfaces:**
- Consumes: `useUpdateLanguage` (Task 4); `i18n` + `SUPPORTED_LANGUAGES` (Task 3); `authApi.me` (existing).
- Produces: `<LanguageSwitcher />`; `useLanguageSync()` hook (called once in `AppLayout`, Task 6).

- [ ] **Step 1: Create the LanguageSwitcher**

`frontend/src/components/LanguageSwitcher.tsx`:

```tsx
import { useTranslation } from 'react-i18next'
import { SUPPORTED_LANGUAGES } from '@/i18n'
import { useUpdateLanguage } from '@/hooks/useMe'

const LABELS: Record<string, string> = { en: 'English', de: 'Deutsch' }

export function LanguageSwitcher() {
  const { i18n, t } = useTranslation('settings')
  const updateLanguage = useUpdateLanguage()

  const onChange = (lng: string) => {
    void i18n.changeLanguage(lng)          // writes localStorage + updates <html lang> immediately
    updateLanguage.mutate(lng)             // best-effort backend persistence; UI never blocks
  }

  return (
    <label className="text-sm text-gray-300">
      {t('language.label')}
      <select
        value={i18n.language.split('-')[0]}
        onChange={e => onChange(e.target.value)}
        className="w-full mt-1 px-3 py-2 bg-gray-900 rounded border border-gray-600 text-sm"
      >
        {SUPPORTED_LANGUAGES.map(lng => (
          <option key={lng} value={lng}>{LABELS[lng]}</option>
        ))}
      </select>
    </label>
  )
}
```

- [ ] **Step 2: Create the backend-pref sync hook**

`frontend/src/hooks/useLanguageSync.ts`:

```ts
import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { authApi } from '@/api/auth'
import { SUPPORTED_LANGUAGES } from '@/i18n'

// Applies the server-stored language once after /me loads, so the choice
// follows the user to a new device even when localStorage is empty there.
export function useLanguageSync() {
  const { i18n } = useTranslation()
  const applied = useRef(false)
  const { data: me } = useQuery({
    queryKey: ['me'],
    queryFn: () => authApi.me().then(r => r.data),
  })

  useEffect(() => {
    if (applied.current || !me?.language) return
    applied.current = true
    const lng = me.language.split('-')[0]
    if ((SUPPORTED_LANGUAGES as readonly string[]).includes(lng) && lng !== i18n.language.split('-')[0]) {
      void i18n.changeLanguage(lng)
    }
  }, [me, i18n])
}
```

- [ ] **Step 3: Add the `settings` namespace files (switcher keys only for now)**

`frontend/src/i18n/locales/en/settings.json`:

```json
{
  "language": { "label": "Language" }
}
```

`frontend/src/i18n/locales/de/settings.json`:

```json
{
  "language": { "label": "Sprache" }
}
```

- [ ] **Step 4: Register the `settings` namespace in i18n init**

In `frontend/src/i18n/index.ts`: add imports and extend `resources` + `ns`:

```ts
import enSettings from './locales/en/settings.json'
import deSettings from './locales/de/settings.json'
```
then in `.init({...})`: `resources.en` → `{ common: enCommon, settings: enSettings }`, `resources.de` → `{ common: deCommon, settings: deSettings }`, and `ns: ['common', 'settings']`.

- [ ] **Step 5: Mount the switcher in ProfilePage**

In `frontend/src/pages/settings/ProfilePage.tsx`, import and render `<LanguageSwitcher />` inside the existing `flex flex-col gap-4` container (below the Display name field, above the save row):

```tsx
import { LanguageSwitcher } from '@/components/LanguageSwitcher'
// ... inside the column, after the Display name <label>:
        <LanguageSwitcher />
```

- [ ] **Step 6: Verify the build**

Run: `cd frontend && npm run build`
Expected: PASS.

- [ ] **Step 7: Manual verification**

`npm run dev`, log in, go to Settings → Profile. Expected: a Language select showing English/Deutsch. Switch to Deutsch → the select label (`Sprache`) flips immediately; reload → choice persists (localStorage). In devtools Network, the switch fired `PATCH /api/v1/me/language` returning 200 with `"language":"de"`. Clear `localStorage['taskowolf.lang']`, reload → language comes back as `de` from `/me` (backend sync).

- [ ] **Step 8: Commit**

```bash
git add frontend/src/components/LanguageSwitcher.tsx frontend/src/hooks/useLanguageSync.ts \
        frontend/src/i18n/index.ts frontend/src/i18n/locales frontend/src/pages/settings/ProfilePage.tsx
git commit -m "feat(i18n): language switcher, backend-pref sync, settings namespace"
```

---

## Task 6: Frontend — translate the nav/chrome (AppLayout) + wire language sync

**Files:**
- Create: `frontend/src/i18n/locales/en/nav.json`, `frontend/src/i18n/locales/de/nav.json`
- Modify: `frontend/src/i18n/index.ts` (register `nav` namespace)
- Modify: `frontend/src/layouts/AppLayout.tsx`

**Interfaces:**
- Consumes: `useLanguageSync` (Task 5); `nav` namespace.
- Produces: fully translated sidebar/header/user-menu chrome.

- [ ] **Step 1: Enumerate the nav strings**

In `frontend/src/layouts/AppLayout.tsx`, list every user-visible literal: section labels (`sectionLabel` values for Admin/Project/Settings/Account and any top-level), every `NavItem` label (Dashboard, Projects, Organizations, Audit Log, Automation, Users, Board, Backlog, Sprints, Issues, Reports, Service Desk, Labels, Versions, Custom Fields, API Keys, Webhooks, Integrations, etc. — use the actual strings present), the `🐺 TaskWolf` wordmark (keep as-is, it is a brand — do **not** translate), Logout, and the collapse/expand `title`/`aria-label` ("Expand sidebar"/"Collapse sidebar").

- [ ] **Step 2: Create `nav` locale files**

`frontend/src/i18n/locales/en/nav.json` — one key per label discovered in Step 1, grouped:

```json
{
  "section": { "admin": "Admin", "project": "Project", "settings": "Settings", "account": "Account" },
  "item": {
    "dashboard": "Dashboard",
    "projects": "Projects",
    "organizations": "Organizations",
    "audit": "Audit Log",
    "automation": "Automation",
    "users": "Users",
    "board": "Board",
    "backlog": "Backlog",
    "sprints": "Sprints",
    "issues": "Issues",
    "reports": "Reports",
    "serviceDesk": "Service Desk",
    "labels": "Labels",
    "versions": "Versions",
    "customFields": "Custom Fields",
    "apiKeys": "API Keys",
    "webhooks": "Webhooks",
    "integrations": "Integrations"
  },
  "logout": "Logout",
  "sidebar": { "expand": "Expand sidebar", "collapse": "Collapse sidebar" }
}
```

`frontend/src/i18n/locales/de/nav.json` — same keys, German values:

```json
{
  "section": { "admin": "Admin", "project": "Projekt", "settings": "Einstellungen", "account": "Konto" },
  "item": {
    "dashboard": "Dashboard",
    "projects": "Projekte",
    "organizations": "Organisationen",
    "audit": "Audit-Log",
    "automation": "Automatisierung",
    "users": "Benutzer",
    "board": "Board",
    "backlog": "Backlog",
    "sprints": "Sprints",
    "issues": "Vorgänge",
    "reports": "Berichte",
    "serviceDesk": "Service Desk",
    "labels": "Labels",
    "versions": "Versionen",
    "customFields": "Benutzerdefinierte Felder",
    "apiKeys": "API-Schlüssel",
    "webhooks": "Webhooks",
    "integrations": "Integrationen"
  },
  "logout": "Abmelden",
  "sidebar": { "expand": "Seitenleiste ausklappen", "collapse": "Seitenleiste einklappen" }
}
```

> The exact key set MUST match the labels actually rendered in `AppLayout.tsx` after Step 1. Add/remove keys so both files and the component agree; keep `en`/`de` key-identical.

- [ ] **Step 3: Register the `nav` namespace**

In `frontend/src/i18n/index.ts`, import `enNav`/`deNav`, add `nav` to both `resources.en`/`resources.de` and to `ns`.

- [ ] **Step 4: Translate AppLayout**

In `frontend/src/layouts/AppLayout.tsx`: add `const { t } = useTranslation('nav')` in the component, call `useLanguageSync()` once at the top of the component body, and replace each literal from Step 1 with `t('...')` (e.g. `sectionLabel="Admin"` → `sectionLabel={t('section.admin')}`, `Logout` → `t('logout')`, the collapse button `title`/`aria-label` ternary → `t(collapsed ? 'sidebar.expand' : 'sidebar.collapse')`). Leave `🐺 TaskWolf` untranslated.

- [ ] **Step 5: Verify the build**

Run: `cd frontend && npm run build`
Expected: PASS.

- [ ] **Step 6: Manual verification**

`npm run dev`. Expected: with `en`, sidebar reads as today. Switch to `de` in Settings → Profile → sidebar labels and Logout flip to German live (no reload). Collapse toggle tooltip is translated. On a fresh session where the backend has `de` stored but localStorage is empty, the sidebar loads German (proves `useLanguageSync`).

- [ ] **Step 7: Commit**

```bash
git add frontend/src/i18n/index.ts frontend/src/i18n/locales frontend/src/layouts/AppLayout.tsx
git commit -m "feat(i18n): translate sidebar/nav chrome, apply backend language sync"
```

---

## Task 7: Frontend — translate the auth pages (Login + Register)

**Files:**
- Create: `frontend/src/i18n/locales/en/auth.json`, `frontend/src/i18n/locales/de/auth.json`
- Modify: `frontend/src/i18n/index.ts` (register `auth` namespace)
- Modify: `frontend/src/pages/auth/LoginPage.tsx`, `frontend/src/pages/auth/RegisterPage.tsx`

**Interfaces:**
- Consumes: `auth` namespace.
- Produces: fully translated login/register screens.

- [ ] **Step 1: Create `auth` locale files**

`frontend/src/i18n/locales/en/auth.json` (keys cover LoginPage literals: "Sign in" heading + button, "Invalid email or password", "Email", "Password", "or sign in with SSO", "Sign in with {{name}}", "No account?", "Register" link — plus the RegisterPage equivalents after inspecting it):

```json
{
  "login": {
    "title": "Sign in",
    "submit": "Sign in",
    "email": "Email",
    "password": "Password",
    "invalid": "Invalid email or password",
    "ssoDivider": "or sign in with SSO",
    "ssoButton": "Sign in with {{name}}",
    "noAccount": "No account?",
    "registerLink": "Register"
  },
  "register": {
    "title": "Create account",
    "submit": "Register",
    "email": "Email",
    "displayName": "Display name",
    "password": "Password",
    "haveAccount": "Already have an account?",
    "loginLink": "Sign in"
  }
}
```

`frontend/src/i18n/locales/de/auth.json`:

```json
{
  "login": {
    "title": "Anmelden",
    "submit": "Anmelden",
    "email": "E-Mail",
    "password": "Passwort",
    "invalid": "Ungültige E-Mail oder Passwort",
    "ssoDivider": "oder per SSO anmelden",
    "ssoButton": "Anmelden mit {{name}}",
    "noAccount": "Kein Konto?",
    "registerLink": "Registrieren"
  },
  "register": {
    "title": "Konto erstellen",
    "submit": "Registrieren",
    "email": "E-Mail",
    "displayName": "Anzeigename",
    "password": "Passwort",
    "haveAccount": "Bereits ein Konto?",
    "loginLink": "Anmelden"
  }
}
```

> First read `RegisterPage.tsx` and reconcile the `register.*` keys with its actual literals (fields, buttons, links, any inline error). Keep `en`/`de` key-identical.

- [ ] **Step 2: Register the `auth` namespace**

In `frontend/src/i18n/index.ts`, import `enAuth`/`deAuth`, add to both `resources` and `ns`.

- [ ] **Step 3: Translate LoginPage**

In `frontend/src/pages/auth/LoginPage.tsx`: add `const { t } = useTranslation('auth')`; replace the heading, both `placeholder`s, the submit button, `setError('Invalid email or password')` → `setError(t('login.invalid'))`, the SSO divider, the SSO anchor text → `t('login.ssoButton', { name: config.name })`, and the "No account? Register" line with `t('login.*')` calls.

- [ ] **Step 4: Translate RegisterPage**

Apply the same treatment to `frontend/src/pages/auth/RegisterPage.tsx` using `t('register.*')`.

- [ ] **Step 5: Verify the build**

Run: `cd frontend && npm run build`
Expected: PASS.

- [ ] **Step 6: Manual verification**

Log out. On the login screen with `localStorage['taskowolf.lang']='de'` set (or a German browser), the login/register forms render in German including the SSO button interpolation and the error message on a bad login. With `en`, they match today's copy.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/i18n/index.ts frontend/src/i18n/locales \
        frontend/src/pages/auth/LoginPage.tsx frontend/src/pages/auth/RegisterPage.tsx
git commit -m "feat(i18n): translate login and register pages"
```

---

## Task 8: Frontend — translate the core settings pages

**Files:**
- Modify: `frontend/src/i18n/locales/en/settings.json`, `frontend/src/i18n/locales/de/settings.json` (extend)
- Modify: `frontend/src/pages/settings/ProfilePage.tsx`
- Modify: `frontend/src/pages/settings/SecurityPage.tsx`
- Modify: `frontend/src/pages/settings/AccountSettingsPage.tsx`
- Modify: `frontend/src/pages/settings/NotificationSettingsPage.tsx`

**Interfaces:**
- Consumes: `settings` + `common` namespaces.
- Produces: four fully translated settings pages (the representative pilot depth; other settings subpages remain hardcoded for follow-up cycles).

- [ ] **Step 1: Read the four pages and enumerate literals**

Read each page; list headings, field labels, buttons, helper text, confirmation/warning copy, and any inline error strings the UI itself renders (e.g. ProfilePage's `'Failed to update profile'` alert, its `Profile`/`Email`/`Display name` labels, the Save/`Saving…`/`Saved` states — the latter three map to `common`).

- [ ] **Step 2: Extend the `settings` locale files**

Extend both `settings.json` files (they already contain `language.label` from Task 5) with a section per page. English example (fill German equivalents symmetrically, and reconcile keys against the actual literals from Step 1):

```json
{
  "language": { "label": "Language" },
  "profile": {
    "title": "Profile",
    "email": "Email",
    "displayName": "Display name",
    "updateFailed": "Failed to update profile"
  },
  "security": { "title": "Security" },
  "account": { "title": "Account" },
  "notifications": { "title": "Notifications" }
}
```

German file mirrors every key (`"title": "Profil"`, `"displayName": "Anzeigename"`, `"updateFailed": "Profil konnte nicht aktualisiert werden"`, `"security.title": "Sicherheit"`, `"account.title": "Konto"`, `"notifications.title": "Benachrichtigungen"`, …). Add whatever additional keys the pages actually need. Keep `en`/`de` key-identical.

- [ ] **Step 3: Translate the pages**

For each page add `const { t } = useTranslation('settings')` (add a second `const { t: tc } = useTranslation('common')` where Save/Cancel/loading reuse `common`). Replace literals: e.g. in `ProfilePage.tsx`, `Profile` → `t('profile.title')`, `Email` → `t('profile.email')`, `Display name` → `t('profile.displayName')`, the Save button `update.isPending ? 'Saving…' : 'Save'` → `update.isPending ? tc('saving') : tc('save')`, the `Saved` badge → `tc('saved')`, and the alert `'Failed to update profile'` → `t('profile.updateFailed')`. Apply the analogous replacements to Security, Account, and Notification pages.

- [ ] **Step 4: Verify the build**

Run: `cd frontend && npm run build`
Expected: PASS.

- [ ] **Step 5: Manual verification**

Settings → each of Profile/Security/Account/Notifications. In `de`, all headings, labels, buttons, and the profile update-failure alert render German; switching back to `en` restores current copy. No raw keys (e.g. `profile.title`) visible anywhere.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/i18n/locales frontend/src/pages/settings/ProfilePage.tsx \
        frontend/src/pages/settings/SecurityPage.tsx \
        frontend/src/pages/settings/AccountSettingsPage.tsx \
        frontend/src/pages/settings/NotificationSettingsPage.tsx
git commit -m "feat(i18n): translate core settings pages"
```

---

## Task 9: Documentation — i18n conventions for follow-up cycles

**Files:**
- Create/Modify: the project wiki i18n page (follow the repo's existing `docs/` wiki convention; also update `ai-guide.md` if present — it is the mandatory AI pre-implementation reference).

**Interfaces:**
- Consumes: everything above.
- Produces: a "How to add translations" guide that turns the pilot into a repeatable pattern.

- [ ] **Step 1: Locate the docs convention**

Find where wiki/docs pages live (look under `docs/` and for `ai-guide.md`). Match that location and format.

- [ ] **Step 2: Write the i18n conventions page**

Document, with a concrete example lifted from the pilot:
- One namespace per feature area; `common` for shared words. Create both `en` and `de` files, key-identical.
- Semantic hierarchical keys; never the English text as the key.
- Consume via `useTranslation('<ns>')`; register new namespaces in `src/i18n/index.ts` (`resources` + `ns`).
- Interpolation via variables (`t('k', { name })`); never concatenate translated fragments. Plurals via i18next `_plural` keys.
- Dates/numbers/relative times via `src/i18n/format.ts` (Intl), not hand-rolled formatting.
- Supported languages/fallback (`en`) live in `SUPPORTED_LANGUAGES`; language persists to localStorage + `PATCH /me/language`.
- Migration recipe: pick a page → add its namespace → move every literal to keys (both locales) → `npm run build` → manual DE/EN check.

- [ ] **Step 3: Update `ai-guide.md` if present**

Add a short "Internationalisation" note pointing at the conventions page and the `src/i18n/` module so future work uses `t()` from the start.

- [ ] **Step 4: Commit**

```bash
git add docs
git commit -m "docs(i18n): translation conventions and follow-up migration guide"
```

---

## Final Verification

- [ ] `cd backend && ./gradlew build` — BUILD SUCCESSFUL (V30 migration applies on a clean DB; all `UserLanguage*` tests green).
- [ ] `cd frontend && npm run build` — tsc + Vite green.
- [ ] Manual end-to-end: switch DE↔EN in Settings → Profile; verify live update across sidebar, auth (after logout), and settings; verify persistence across reload (localStorage) and across a fresh session with empty localStorage (backend `/me`); verify a bad-login error and the SSO button interpolation are translated; confirm no raw translation keys are visible and `🐺 TaskWolf` stays branded.
- [ ] Confirm nothing outside the pilot slice changed behaviour (other pages still render their existing hardcoded copy).
