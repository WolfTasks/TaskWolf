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
