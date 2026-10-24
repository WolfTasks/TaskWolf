# Getting Started

## Prerequisites

- Docker 24+ and Docker Compose v2
- A terminal
- 512 MB RAM available for the backend container

## 1. Clone the repository

```bash
git clone https://github.com/WolfTasks/TaskWolf.git
cd TaskWolf
```

## 2. Configure environment variables

Copy the example file and set required values:

```bash
cp .env.example .env
```

Edit `.env` — the only **required** variable is `TW_JWT_SECRET`. Generate a secure value:

```bash
openssl rand -hex 32
```

Also set `TW_DB_PASS` to a strong password, and `TW_BASE_URL` to the URL users open in the browser
(e.g. `https://tasks.example.com`) — email links and SSO redirects are built from it.

The PostgreSQL port is published on `127.0.0.1:5432` only, so local tools (`psql`, `pg_dump`) on the host work,
but the database is not reachable from the network. To reach it remotely, use an SSH tunnel
(`ssh -L 5432:127.0.0.1:5432 <host>`) rather than publishing the port.

## 3. Start the stack

```bash
docker compose -f docker-compose.prod.yml up -d
```

TaskWolf starts at `http://localhost`. The first user to register becomes System Admin.

## 4. Log in

Open `http://localhost` in your browser. Click **Register** and create your account. You will automatically receive the System Admin role.

## Upgrading

```bash
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

Database migrations run automatically on startup via Flyway.
