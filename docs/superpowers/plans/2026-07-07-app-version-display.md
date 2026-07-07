# App-Version anzeigen — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Die App-Version aus `frontend/package.json` an zwei Stellen anzeigen — auf dem Startbildschirm (Login/Register) und unten links in der App-Sidebar.

**Architecture:** Version wird zur Build-Zeit von Vite aus `package.json` gelesen und als globale Konstante `__APP_VERSION__` injiziert. Eine kleine `VersionTag`-Komponente rendert sie an beiden Orten aus derselben Quelle.

**Tech Stack:** React 19, TypeScript 6, Vite 8, Tailwind 4

## Global Constraints

- Frontend hat **kein** Test-Framework → Verifikation via `npm run build` (führt `tsc && vite build` aus) + manuelle Sichtprüfung. Keine Unit-Tests.
- Wahrheitsquelle der Version: `frontend/package.json` (`version`), gepflegt als SemVer ohne führende Null (`1.0.6`), angezeigt als `v1.0.6`.
- Pfad-Alias: `@` → `frontend/src` (bereits konfiguriert).
- Alle Kommandos werden aus dem Verzeichnis `frontend/` ausgeführt.

---

### Task 1: Version zentral bereitstellen und an beiden Orten anzeigen

**Files:**
- Modify: `frontend/package.json:4` (`version`)
- Modify: `frontend/vite.config.ts`
- Modify: `frontend/src/vite-env.d.ts`
- Create: `frontend/src/components/VersionTag.tsx`
- Modify: `frontend/src/layouts/AuthLayout.tsx`
- Modify: `frontend/src/layouts/AppLayout.tsx`

**Interfaces:**
- Produces: globale Konstante `__APP_VERSION__: string` (Compile-Time), Komponente `VersionTag({ className?: string })`

- [ ] **Step 1: `package.json`-Version auf den aktuellen Release-Stand korrigieren**

In `frontend/package.json` die Zeile `"version": "0.0.1",` ersetzen durch:
```json
  "version": "1.0.6",
```

- [ ] **Step 2: Vite so konfigurieren, dass die Version injiziert wird**

`frontend/vite.config.ts` vollständig ersetzen durch:
```ts
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'
import pkg from './package.json'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  define: {
    __APP_VERSION__: JSON.stringify(pkg.version),
  },
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') }
  },
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': { target: 'ws://localhost:8080', ws: true },
      '/ws-stomp': { target: 'ws://localhost:8080', ws: true }
    }
  }
})
```

> Hinweis: `import pkg from './package.json'` benötigt `resolveJsonModule` — bei Vite/TS-Standardsetup aktiv. Falls `tsc` in Step 6 einen JSON-Import-Fehler meldet, in `frontend/tsconfig.json` unter `compilerOptions` `"resolveJsonModule": true` ergänzen.

- [ ] **Step 3: Globale Konstante typisieren**

`frontend/src/vite-env.d.ts` vollständig ersetzen durch:
```ts
/// <reference types="vite/client" />

declare const __APP_VERSION__: string
```

- [ ] **Step 4: `VersionTag`-Komponente anlegen**

Create `frontend/src/components/VersionTag.tsx`:
```tsx
export function VersionTag({ className = '' }: { className?: string }) {
  return (
    <span className={`text-xs text-gray-500 ${className}`}>
      v{__APP_VERSION__}
    </span>
  )
}
```

- [ ] **Step 5: In `AuthLayout` (Startbildschirm) einbinden**

In `frontend/src/layouts/AuthLayout.tsx` den Import ergänzen und den Titelblock anpassen. Datei vollständig ersetzen durch:
```tsx
import { Outlet } from 'react-router-dom'
import { VersionTag } from '@/components/VersionTag'

export function AuthLayout() {
  return (
    <div className="min-h-screen bg-gray-950 flex items-center justify-center">
      <div className="w-full max-w-md p-8">
        <h1 className="text-3xl font-bold text-white text-center mb-1">🐺 TaskWolf</h1>
        <VersionTag className="block text-center mb-8" />
        <Outlet />
      </div>
    </div>
  )
}
```

- [ ] **Step 6: In `AppLayout` (Sidebar unten links) einbinden**

In `frontend/src/layouts/AppLayout.tsx`:

(a) Import ergänzen (nach der bestehenden `IssueDialogHost`-Import-Zeile):
```tsx
import { VersionTag } from '@/components/VersionTag'
```

(b) Im Fußbereich der Sidebar den `mt-auto`-Block erweitern — ersetze diesen Block:
```tsx
        <div className="flex flex-col gap-1 mt-auto">
          <OrgSwitcher />
          <div className="flex items-center gap-2">
            <NotificationBell />
            <button onClick={logout} className="flex-1 px-3 py-2 text-sm text-gray-400 hover:text-white text-left">
              Logout
            </button>
          </div>
        </div>
```
durch:
```tsx
        <div className="flex flex-col gap-1 mt-auto">
          <OrgSwitcher />
          <div className="flex items-center gap-2">
            <NotificationBell />
            <button onClick={logout} className="flex-1 px-3 py-2 text-sm text-gray-400 hover:text-white text-left">
              Logout
            </button>
          </div>
          <VersionTag className="px-3 pt-2" />
        </div>
```

- [ ] **Step 7: Build/Typecheck ausführen**

Run (in `frontend/`): `npm run build`
Expected: Build läuft ohne TypeScript-Fehler durch; keine „`__APP_VERSION__` is not defined"-Meldung.

- [ ] **Step 8: Manuelle Sichtprüfung**

Run (in `frontend/`): `npm run dev`
Prüfen:
- `/login` zeigt unter „🐺 TaskWolf" zentriert `v1.0.6`.
- Nach Login: Sidebar unten links (unter Logout) zeigt `v1.0.6`.

- [ ] **Step 9: Commit**

```bash
git add frontend/package.json frontend/vite.config.ts frontend/src/vite-env.d.ts \
        frontend/src/components/VersionTag.tsx \
        frontend/src/layouts/AuthLayout.tsx frontend/src/layouts/AppLayout.tsx
git commit -m "feat(frontend): show app version on start screen and sidebar"
```

---

## Self-Review

- **Spec-Abdeckung:** Startbildschirm (Step 5) ✓, Sidebar unten links (Step 6) ✓, Single Source `package.json` (Steps 1–2) ✓, `VersionTag`-Komponente (Step 4) ✓, kein Backend ✓.
- **Platzhalter:** keine.
- **Typkonsistenz:** `__APP_VERSION__` in `vite.config.ts` (define) ↔ `vite-env.d.ts` (declare) ↔ `VersionTag.tsx` (Nutzung) konsistent.
