# App-Version anzeigen — Design

> Status: Entwurf zur Review · Datum: 2026-07-07 · Vorhaben #1

## Ziel

Die aktuelle App-Version soll an zwei Stellen sichtbar sein:

1. **Startbildschirm** (Login/Register) — dezent unter dem `🐺 TaskWolf`-Titel.
2. **Sidebar unten links** — im Fußbereich der App-Navigation (unter Logout).

Es gibt aktuell **keine gemeinsame Versionsquelle**: `frontend/package.json` steht auf
`0.0.1`, die tatsächlichen Releases laufen aber auf der Konvention `v1.0.0x`
(zuletzt `v1.0.06`). Das wird mit diesem Vorhaben vereinheitlicht.

## Entscheidungen

| Frage | Entscheidung | Begründung |
|---|---|---|
| Wahrheitsquelle der Version | `frontend/package.json` (`version`) | Eine Stelle, kein Backend-Call, funktioniert auch vor dem Login |
| Bereitstellung im Client | Vite `define` → globale Konstante `__APP_VERSION__` | Zur Build-Zeit injiziert, kein Runtime-Overhead |
| `package.json`-Version | Auf aktuellen Release-Stand korrigieren (`1.0.6`) | Beseitigt die `0.0.1`-Diskrepanz; bleibt künftig Single Source of Truth |
| Anzeigeformat | `v{version}` (z.B. `v1.0.6`), klein/grau | Konsistent mit Release-Konvention |
| Backend-Version anzeigen | Nein (Out of Scope) | Für die Anforderung genügt die App-Version; kein Endpoint nötig |

Hinweis zur Versionskonvention: Die Release-Tags nutzen `v1.0.0x`
(führende Null im Patch). In `package.json` (SemVer, keine führende Null) wird
`1.0.6` gepflegt; angezeigt wird `v1.0.6`. Die Anzeige folgt dem SemVer-Wert aus
`package.json`, nicht dem Tag-Format. Das ist bewusst so, um eine einzige
maschinenlesbare Quelle zu haben.

## Architektur

**Build-Konfiguration (`frontend/vite.config.ts`):**
Die Version wird aus `package.json` gelesen und als Compile-Time-Konstante
bereitgestellt:

```ts
import pkg from './package.json'
// ...
define: {
  __APP_VERSION__: JSON.stringify(pkg.version),
}
```

Typdeklaration in `frontend/src/vite-env.d.ts` (oder neuer `globals.d.ts`):
```ts
declare const __APP_VERSION__: string
```

**Komponente `frontend/src/components/VersionTag.tsx`:**
Eine kleine, wiederverwendbare Komponente, damit beide Anzeigeorte dieselbe
Quelle und dasselbe Format nutzen.

```tsx
export function VersionTag({ className = '' }: { className?: string }) {
  return (
    <span className={`text-xs text-gray-500 ${className}`}>
      v{__APP_VERSION__}
    </span>
  )
}
```

**Einbindung:**
- `AuthLayout.tsx`: unter dem `<h1>🐺 TaskWolf</h1>` eine zentrierte
  `<VersionTag className="block text-center mb-8" />` (den bestehenden
  `mb-8`-Abstand des Titels dabei auf den Wrapper verlagern).
- `AppLayout.tsx`: im bestehenden `mt-auto`-Fußblock (nach dem Logout-Button)
  eine `<VersionTag className="px-3 pt-2" />`.

## Komponentengrenzen

- **`VersionTag`** — reine Präsentationskomponente. Input: optionale
  CSS-Klassen. Abhängigkeit: globale Konstante `__APP_VERSION__`. Keine Logik,
  kein State, kein Netzwerk. Änderbar ohne Auswirkung auf Konsumenten.

## Fehlerfälle

- Fehlt `__APP_VERSION__` (theoretisch, bei falscher Build-Konfig), rendert die
  Komponente `v` ohne Nummer — kein Crash. Die Typdeklaration + `define`
  stellen sicher, dass der Wert zur Build-Zeit existiert.

## Tests

Das Frontend hat **kein** Test-Framework (nur `tsc`-Typecheck + manuelle
Prüfung). Verifikation daher:

- `npm run build` (bzw. `tsc && vite build`) läuft fehlerfrei durch
  (Typecheck der neuen globalen Konstante).
- Manuelle Sichtprüfung: Version erscheint auf `/login` und in der Sidebar
  unten links; Wert entspricht `package.json`.

## Umfang / Nicht enthalten

- Kein Backend-Versions-Endpoint.
- Kein Link auf Changelog/Release Notes (kann später ergänzt werden).
- Keine automatische Ableitung aus Git-Tags/CI (bewusst: `package.json` bleibt
  die manuell gepflegte Quelle, passend zur bestehenden Release-Konvention).

## Betroffene Dateien

**Ändern:**
- `frontend/package.json` (`version` → `1.0.6`)
- `frontend/vite.config.ts` (`define`)
- `frontend/src/vite-env.d.ts` (Typdeklaration)
- `frontend/src/layouts/AuthLayout.tsx`
- `frontend/src/layouts/AppLayout.tsx`

**Neu:**
- `frontend/src/components/VersionTag.tsx`
