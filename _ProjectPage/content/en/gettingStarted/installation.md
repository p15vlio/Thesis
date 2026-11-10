The whole system runs from one Docker Compose file, and is set up with one script. Apart from Docker, your machine only needs Python 3 for the seed scripts: Gradle, Node.js and the JDK are downloaded inside the build.

## Prerequisites

| Tool | For | Notes |
|---|---|---|
| Docker Desktop (Windows, macOS) or Docker Engine with the Compose plugin (Linux) | database, backend, web panel | Compose v2 (`docker compose`). On Windows the script offers to install it with winget |
| Python 3.8 or newer | the seed scripts in `_Deployment/python/` | the script installs `requests`, `bcrypt` and `psycopg2-binary` into `_Deployment/python/.venv`. On Windows it offers to install Python with winget |
| Android Studio or the Android SDK (platform 36) | building the kiosk app | optional: ready APKs are in `_Deployment/apk/` |

## Quick start: one click

All commands below run in the `_Deployment` folder of the repository. Its root holds `start-all` and `manage`, and the subfolders hold the rest: `apk/` (the kiosk app), `docker/` (Docker Compose, Dockerfile, `.env`), `steps/` (the steps of `start-all`) and `python/` (the seed and verification scripts).

- **Windows:** double-click `start-all.bat`, or run `start-all.bat` in a terminal (`start-all.bat -Yes` answers yes to the installation questions).
- **Linux and macOS:** run `bash start-all.sh` (`bash start-all.sh --yes` for unattended use).

The script stops at the first error with a clear message, and it is safe to run again. It runs three steps, which also exist as separate scripts in the `steps/` folder (`01-check-dependencies`, `02-build-and-start`, `03-seed`, each as `.bat`/`.ps1` and `.sh`):

1. It checks Docker, Docker Compose, the Docker Engine (on Windows and macOS it starts Docker Desktop if needed) and Python, and creates `.venv` with the Python packages. Confirmation is asked before anything is installed. If `docker/.env` does not exist, it is created from `docker/.env.example` with two newly generated random secrets, `JWT_SECRET` and `SETTINGS_SECRET`. An existing `.env` is left as it is.
2. It runs `docker compose up --build -d` and waits until `http://localhost:8080/api/health` and `http://localhost/api/health` answer. The first build takes about 3 to 6 minutes; later builds are much faster.
3. It creates the first platform administrator (`seed_admin.py`) and the demonstration data (`seed_data.py`). Both skip what already exists.

Docker Compose starts three services: `postgres` (PostgreSQL 16, port 5432, reachable only from this computer), `backend` (Ktor, port 8080) and `frontend` (nginx, port 80). The backend creates its tables when it starts, and it refuses to start without the two secrets.

`seed_data.py` creates five demonstration owners in Greek cities with 18 properties. Each owner has one catalogue of 20 products, shared by its properties, and at each property four of them are unavailable. It also creates a test owner for the quick login.

Open [http://localhost](http://localhost). The login screen has a **Demo quick login** for the two demonstration accounts:

| Account | Email | Password |
|---|---|---|
| Platform administrator | `admin@guestdesk.io` | `Admin@GuestDesk1` |
| Test owner | `owner@guestdesk.io` | `Owner@GuestDesk1` |

![The login screen with the demo quick login](/media/web-login.png)

To manage the system, use `manage.bat` (Windows) or `bash manage.sh` (Linux, macOS) with one of `up`, `down`, `status`, `logs` and `reset`. `manage down` stops everything and keeps the data. `manage reset` also deletes the database volume, after a confirmation, so the next run of `start-all` starts from an empty database.

## Manual steps

If you prefer to run each step yourself:

1. In the `_Deployment/docker` folder, copy `.env.example` to `.env` and fill in `JWT_SECRET` and `SETTINGS_SECRET` with long random values, for example the output of `python -c "import secrets; print(secrets.token_hex(32))"`. They are empty in the example, so Docker Compose refuses to start until they are set.
2. For a real tablet, set `PUBLIC_API_URL` in `.env` to the LAN address of your PC, for example `http://192.168.1.20:8080`. It is embedded in the QR codes of the devices. For the web panel alone, the default `http://localhost:8080` is enough.
3. In the same folder, build and start: `docker compose up --build -d`.
4. In the `_Deployment/python` folder, install the packages and seed: `pip install -r requirements.txt`, then `python seed_admin.py` and `python seed_data.py`.

## Configure the integrations

Log in as the administrator and open **Settings**. All of this is optional.

- **Email (SMTP).** For a public email service, fill in its SMTP server, the port (usually 587 with STARTTLS), your address and an app password. Without this setting, welcome messages and receipts are skipped, and the temporary password of a new owner is still shown on screen.
- **Company registry.** An API key from the registry's open data portal turns on the company lookup by VAT number when you create an owner.
- **Delivery platforms.** The owner connects the platforms under **Food delivery**. The platforms are emulated, so any key of at least eight characters is accepted.

## The kiosk app

The `_Deployment/apk/` folder holds the app, ready to install:

| File | Description |
|---|---|
| `guestdesk-release.apk` | The signed production (release) build |
| `guestdesk-debug.apk` | The debug build, with its own package name, so it installs next to the release build |

Copy the file to the tablet and open it or, from the PC, run `adb install guestdesk-release.apk`. The server address is given at activation, so the same APK works with any installation.

To build the app yourself, run from the `_ProjectThesis` folder (Android Studio creates `local.properties` with your SDK path):

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

Then add a device in the owner's web panel and, on the tablet, either scan the QR code or type the server address and the eight-character activation code.

**Using an emulator.** Create a tablet virtual device (a landscape tablet system image, API 35, works well), set `PUBLIC_API_URL` to `http://10.0.2.2:8080`, and type `http://10.0.2.2:8080` as the server address on the tablet. The application works normally except for the card reader: the reader is a service of the payment terminal, so on an emulator the payment screen says that it is unavailable ([Chapter 8](/report/ch8)).

To leave kiosk mode, tap the invisible area at the right end of the top bar five times within 3 seconds and enter the demonstration PIN `123456`.

## The emulated payment provider

Approving payments is the payment provider's job, and in the demonstration the provider is emulated. The device's card reader produces real card events, and the emulated provider answers as follows:

- **Tap (contactless):** approved.
- **Insert (chip):** the app asks for a PIN. `0000` is declined as a wrong PIN, and any other four digits are approved.
- **Swipe (magnetic stripe):** declined.

## Troubleshooting

- **Docker is not running.** Start Docker Desktop and wait until it reports that the engine is running, then run the script again. On Linux run `sudo systemctl start docker`.
- **A port is in use.** The script stops if another program or container already uses port 80, 8080 or 5432, and names it. Stop it (`docker stop <name>` for a container) and run again.
- **The backend is unhealthy.** The script prints the last lines of the backend log. A missing secret in `docker/.env` is the usual cause; see also `manage logs`.
- **`./gradlew: not found` during the build.** The file has Windows line endings. The repository's `.gitattributes` and the Dockerfile both prevent this; if you copied the files by hand, convert `gradlew` to LF.
- **The tablet cannot connect to the server.** `PUBLIC_API_URL` must be the address of the PC as the tablet sees it, not `localhost`. Allow port 8080 in the firewall, and create a new device after changing the address, so that its QR code carries the new one.
- **Too many requests.** Login, token refresh and activation are limited to 20 requests per minute per address. Wait a minute.
