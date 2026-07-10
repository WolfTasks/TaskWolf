# H1 — nginx `index.html` no-cache Härtung — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `index.html` wird vom Frontend-nginx mit `Cache-Control: no-cache` ausgeliefert (Assets bleiben `immutable`), damit Browser nach jedem Deploy garantiert die frische HTML mit neuen Asset-Hashes laden.

**Architecture:** Eine einzige Config-Änderung an `frontend/nginx-spa.conf`: ein dedizierter `location = /index.html`-Block, der `Cache-Control: no-cache` setzt und — wegen nginx' Header-Vererbungsregel — alle fünf server-level Security-Header erneut deklariert.

**Tech Stack:** nginx (Frontend-Docker-Image, `nginx:alpine`), Docker.

## Global Constraints

- Nur `frontend/nginx-spa.conf` ändern. `docker/nginx.conf` (Compose-Proxy) bleibt unberührt.
- nginx-Regel: `add_header` in einem `location`-Block **ersetzt** alle server-level `add_header` — im neuen Block müssen daher alle fünf bestehenden Security-Header erneut stehen.
- Asset-Cache-Regel (`public, immutable`, `expires 1y`) darf sich nicht ändern.
- Kein Frontend-Test-Framework vorhanden → Verifikation via `nginx -t` + `curl -I` am gebauten Image (manuell/dokumentiert, kein Automated-Test).
- Referenz-Design: `docs/superpowers/specs/2026-07-10-h1-nginx-index-nocache-design.md`.

---

### Task 1: `location = /index.html` mit no-cache + Security-Headern ergänzen

**Files:**
- Modify: `frontend/nginx-spa.conf`

**Interfaces:**
- Consumes: nichts (isolierte Config-Änderung).
- Produces: nichts (kein Code-Interface; nur HTTP-Response-Header-Verhalten).

- [ ] **Step 1: Aktuellen Config-Stand bestätigen**

Öffne `frontend/nginx-spa.conf` und vergewissere dich, dass:
- die fünf Security-Header auf server-Ebene stehen (`X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy`, `Content-Security-Policy`),
- `location /` ein `try_files $uri $uri/ /index.html;` enthält,
- die Asset-`location ~* \.(js|css|...)$` mit `expires 1y` + `Cache-Control "public, immutable"` existiert,
- **kein** `location = /index.html` bereits vorhanden ist.

- [ ] **Step 2: Neuen `location = /index.html`-Block einfügen**

Füge in `frontend/nginx-spa.conf` **vor** dem `location /`-Block (Zeile ~13) diesen Block ein. Die CSP-Zeichenkette muss **exakt** der server-level CSP entsprechen:

```nginx
    # index.html darf nie gecached werden, damit neue Asset-Hashes nach jedem
    # Deploy sofort geladen werden. add_header hier ersetzt die server-level
    # Header -> alle fünf Security-Header erneut deklarieren.
    location = /index.html {
        add_header X-Content-Type-Options "nosniff" always;
        add_header X-Frame-Options "DENY" always;
        add_header Referrer-Policy "strict-origin-when-cross-origin" always;
        add_header Permissions-Policy "camera=(), microphone=(), geolocation=()" always;
        add_header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self' ws: wss:; font-src 'self';" always;
        add_header Cache-Control "no-cache" always;
    }
```

- [ ] **Step 3: Config-Syntax prüfen (`nginx -t`)**

Baue das Image und prüfe die Syntax im Container:

```bash
docker build -t taskwolf-fe-h1 ./frontend
docker run --rm taskwolf-fe-h1 nginx -t
```

Expected: `nginx: configuration file /etc/nginx/nginx.conf test is successful`

- [ ] **Step 4: Header-Verhalten mit curl verifizieren**

Container starten und Header prüfen:

```bash
docker run --rm -d -p 8099:80 --name taskwolf-fe-h1 taskwolf-fe-h1
curl -sI http://localhost:8099/ | tr -d '\r'
curl -sI http://localhost:8099/index.html | tr -d '\r'
# Ein gehashtes Asset (Name aus dem dist-Verzeichnis, z.B. /assets/index-<hash>.js):
curl -sI http://localhost:8099/assets/$(docker exec taskwolf-fe-h1 sh -c 'ls /usr/share/nginx/html/assets | grep -m1 "\.js$"') | tr -d '\r'
docker stop taskwolf-fe-h1
```

Expected:
- `/` **und** `/index.html`: enthalten `Cache-Control: no-cache` **und** alle fünf Security-Header (inkl. `Content-Security-Policy`).
- Das JS-Asset: enthält weiterhin `Cache-Control: public, immutable` und `Expires` ~1 Jahr; **kein** `no-cache`.

- [ ] **Step 5: Commit**

```bash
git add frontend/nginx-spa.conf
git commit -m "fix(nginx): serve index.html with Cache-Control no-cache (H1)

Add a dedicated location = /index.html block that sets Cache-Control:
no-cache and re-declares all five security headers (nginx add_header in a
location replaces server-level headers). Hashed assets stay immutable.
Browsers now always fetch fresh index.html with new asset hashes after a
deploy, removing the manual hard-reload workaround.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Self-Review

**Spec coverage:**
- no-cache auf index.html → Task 1, Step 2 ✅
- Security-Header wegen add_header-Vererbung erneut deklariert → Step 2 ✅
- Asset-immutable bleibt unverändert → Step 4 verifiziert ✅
- `docker/nginx.conf` unberührt → Global Constraints ✅
- Verifikation (`nginx -t` + curl) → Steps 3–4 ✅

**Placeholder scan:** keine TODO/TBD; CSP verbatim aus Spec/Config. ✅

**Consistency:** CSP-String identisch zur server-level Zeile in `nginx-spa.conf`. ✅
