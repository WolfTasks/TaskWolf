# H1 — nginx `index.html` no-cache Härtung (Design)

> Kleiner, isolierter Ops-Fix am Frontend-Image. Aus dem Debugging vom
> 2026-07-07 (wkozian sah nach dem v1.0.07-Redeploy keine Projekte → Ursache
> war client-seitiger Stale-State, behoben per Hard-Reload).

## Problem
`frontend/nginx-spa.conf` liefert `index.html` **ohne** explizites `Cache-Control`.
`index.html` wird über `location /` (`try_files $uri $uri/ /index.html;`)
ausgeliefert; die Asset-Cache-Regex `\.(js|css|png|…)$` matcht `.html`
**nicht**, `index.html` bekommt also weder `immutable` noch `no-cache`.
Ohne `no-cache` kann der Browser eine veraltete `index.html` (mit alten
Asset-Hashes) aus dem HTTP-Cache halten → nach einem Deploy erscheinen keine
neuen Assets, bis der Nutzer manuell hart neu lädt.

## Ziel
`index.html` wird mit `Cache-Control: no-cache` ausgeliefert (Browser
revalidiert bei jedem Load), während gehashte Assets `immutable`/1 Jahr bleiben.
Nach jedem Deploy holt der Browser garantiert die frische `index.html` mit den
neuen Asset-Hashes — kein manueller Hard-Reload mehr nötig.

## Ansatz
Dedizierten Block in `frontend/nginx-spa.conf` ergänzen:

```nginx
location = /index.html {
    # add_header in einem location-Block ERSETZT die server-level add_header,
    # daher müssen die Security-Header hier erneut deklariert werden.
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-Frame-Options "DENY" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;
    add_header Permissions-Policy "camera=(), microphone=(), geolocation=()" always;
    add_header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self' ws: wss:; font-src 'self';" always;
    add_header Cache-Control "no-cache" always;
}
```

**Kritischer Punkt (nginx-Header-Vererbung):** `add_header` in einem
`location`-Block **überschreibt** alle server-level `add_header`-Direktiven
(nginx vererbt `add_header` nur, wenn im Kind-Block **keine** `add_header`
steht). Würde man nur `Cache-Control` ergänzen, verlöre `index.html` seine
fünf Security-Header (inkl. CSP). Deshalb werden im `location = /index.html`
alle fünf Security-Header **erneut** aufgeführt.

## Abgrenzung
- **`docker/nginx.conf`** (Compose-Proxy) braucht **keine** Änderung — er
  proxied `location /` nur an `frontend:80` durch; die Header setzt das
  Frontend-Image.
- Keine Änderung an der Asset-Cache-Regel (bleibt `public, immutable`, 1 Jahr).
- Kein Einfluss auf das SPA-Routing (`try_files … /index.html` bleibt in
  `location /`; der neue `location = /index.html` greift für den direkten
  Datei-Request bzw. den internen Rewrite auf `/index.html`).

## Betroffene Dateien
- `frontend/nginx-spa.conf` (einzige Code-Änderung)

## Verifikation
Da das Frontend kein Test-Framework hat (nur `tsc` + manuell) und es sich um
nginx-Config handelt, erfolgt die Prüfung am gebauten Image:
1. `docker build -t taskwolf-fe-test ./frontend` (bzw. gezogenes Image nach
   Merge/Publish).
2. `curl -I http://localhost:PORT/` bzw. `/index.html` →
   `Cache-Control: no-cache` **und** alle fünf Security-Header vorhanden.
3. `curl -I` auf ein gehashtes Asset (`/assets/index-*.js`) →
   weiterhin `Cache-Control: public, immutable` + `expires 1y`.
4. `nginx -t` (Config-Syntax) im Container ist grün.

## Risiko / Größe
Sehr klein, isoliert. Hauptrisiko = Header-Vererbungs-Falle (oben adressiert)
und versehentliches Deaktivieren des immutable-Caches (nicht betroffen).
