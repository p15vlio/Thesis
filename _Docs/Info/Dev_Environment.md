# Local Development Environment

See the [Start Guide](./Start_Guide.md) for prerequisites and the quick start. This page covers the details.

## Containers

`_Deployment/docker/docker-compose.yml` (compose project name `guestdesk`) runs three services. Run `docker compose` from
`_Deployment/docker`, or use the one-click `start-all.bat` / `start-all.sh` described in the Start Guide.

| Service (container) | Image / target | Port | Purpose |
|---|---|---|---|
| `postgres` (`guestdesk-postgres-1`) | `postgres:16` | `127.0.0.1:5432` | PostgreSQL, data in the `guestdesk_postgres_data` volume |
| `backend` (`guestdesk-backend-1`) | `app.Dockerfile` target `backend` (JRE 21 on Alpine) | 8080 | Ktor API, started after the database is healthy |
| `frontend` (`guestdesk-frontend-1`) | `app.Dockerfile` target `frontend` (nginx, production Wasm build, gzip) | 80 | Owner/admin web portal; proxies `/api` to the backend |

PostgreSQL is published on the loopback interface only (the seed scripts on this PC use it; the LAN does not need it).
`manage.bat` / `manage.sh` `up|down|status|logs|reset` (in `_Deployment`) wrap the common compose commands (`up` = `docker compose up --build -d`,
`reset` = `docker compose down -v` after a confirmation). `docker compose down` keeps the data volume; `-v` wipes the database.

## Environment variables

Values come from `_Deployment/docker/.env` (git-ignored). `01-check-dependencies` (part of `start-all`) creates it from
`_Deployment/docker/.env.example` with freshly generated random `JWT_SECRET` and `SETTINGS_SECRET` and never touches an existing
`.env`. When you copy the example by hand, fill in both secrets: they are empty in the example, and `docker compose`
refuses to start (`${JWT_SECRET:?...}`) until they are set.

| Variable | Default | Purpose |
|---|---|---|
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | `opendelivery`, `postgres`, `opendelivery` | Database name and credentials; passed to the backend as `JDBC_URL`, `DB_USER`, `DB_PASSWORD` |
| `JWT_SECRET` | none, required | JWT signing secret; the backend refuses to start without a non-blank value |
| `SETTINGS_SECRET` | none, required | Key for encrypting stored secrets (SMTP password, GEMH API key, food platform API keys); the backend refuses to start without a non-blank value |
| `PUBLIC_API_URL` | `http://localhost:8080` (`.env`: LAN address of this PC) | Embedded in device QR codes so the kiosk finds the server; must be reachable from the tablet |
| `PUBLIC_FRONTEND_URL` | `http://localhost` | Login URL used in the owner welcome email (optional, commented out in `.env.example`) |
| `FOOD_PLATFORM_STEP_SECONDS` | `45` | Seconds between the status changes simulated by the emulated food platforms (optional, commented out in `.env.example`) |
| `RESET_DB` | unset | Backend only, not set in compose: `true` drops and recreates all tables at startup (development only, destroys data) |

Outside Docker the backend also reads `JDBC_URL` (default `jdbc:postgresql://localhost:5432/opendelivery`) and `DB_USER`
(default `postgres`), and requires `DB_PASSWORD`.

## Seeding

`03-seed` (part of `start-all`) runs both scripts with the Python of `_Deployment/python/.venv` and the database settings of
`.env`. By hand, in `_Deployment/python`:

```bash
pip install -r requirements.txt     # requests, bcrypt, psycopg2-binary
python seed_admin.py                # platform admin: admin@guestdesk.io / Admin@GuestDesk1 (idempotent)
python seed_data.py                 # 5 demo owners, 18 properties, products, and the test owner (idempotent)
python seed_data.py --test-owner-only   # only owner@guestdesk.io / Owner@GuestDesk1
```

Tables are created by the backend when it starts; both scripts wait for them (and for the backend) up to
`SEED_WAIT_SECONDS` (default 120) and skip what already exists. Each demo owner gets one catalogue of 20 products shared by
its properties, with 4 of them switched off per property (`PATCH /products/{id}/properties/{propertyId}/availability`).
Both scripts connect to PostgreSQL on `localhost:5432` (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`);
`seed_data.py` also calls the API at `BASE_URL` (default `http://localhost:8080`). See the script headers for all variables.

## Running parts outside Docker

```bash
cd _ProjectThesis
./gradlew :backend:run                              # needs the env vars above and a reachable PostgreSQL
./gradlew :owner-web:wasmJsBrowserDevelopmentRun    # dev server for the web portal
```

The web portal calls the API with relative URLs (`/api`), so it needs the nginx proxy of the compose stack (or an equivalent proxy).

---
[Back to the Start Guide](./Start_Guide.md)
