# Guest Desk

**Σχεδίαση και Υλοποίηση Συστήματος Ψηφιακών Παραγγελιών και Υπηρεσιών για Καταλύματα Βραχυχρόνιας Μίσθωσης**

Guest Desk is a multi-tenant platform for short-term rental properties. A platform administrator onboards property
owners (each owner gets an isolated PostgreSQL schema). Owners use a web portal to manage properties, kiosk devices,
products, food-delivery platform connections and orders. Guests use an Android kiosk app (for a payment terminal with a built-in card reader) placed in the property to buy local products, order food from delivery platforms and pay by card.
The backend is a Ktor/Exposed service on PostgreSQL, the web portal is Compose Multiplatform (Kotlin/Wasm), and the
domain and DTO modules are shared Kotlin Multiplatform code.

---

## Περιγραφή Έργου
Το **Guest Desk** είναι μια multi-tenant πλατφόρμα που στοχεύει στον ψηφιακό μετασχηματισμό των παρεχόμενων υπηρεσιών σε καταλύματα βραχυχρόνιας μίσθωσης. Το σύστημα επιτρέπει στους επισκέπτες να αποκτούν πρόσβαση σε τοπικά προϊόντα και υπηρεσίες μέσω μιας συσκευής Android (Kiosk) εντός του καταλύματος, ενώ οι ιδιοκτήτες διαχειρίζονται το περιεχόμενο και τις παραγγελίες μέσω ενός κεντρικού Web Panel.

Το έργο αναπτύχθηκε στο πλαίσιο πτυχιακής εργασίας στο Τμήμα Πληροφορικής.

## Αρχιτεκτονική Προσέγγιση
Το σύστημα ακολουθεί τις αρχές του **Clean Architecture** και είναι οργανωμένο ως **Monorepo**, κάνοντας χρήση της τεχνολογίας **Kotlin Multiplatform (KMP)**. Η προσέγγιση αυτή επιτρέπει:
- **Διαμοιρασμό Κώδικα (Shared Code)**: Τα μοντέλα δεδομένων (οντότητες και DTOs) είναι κοινά για όλες τις πλατφόρμες (Backend, Android, Web).
- **Multi-tenancy**: Τα δεδομένα κάθε ιδιοκτήτη (Owner) βρίσκονται σε ξεχωριστό PostgreSQL schema.
- **Επεκτασιμότητα**: Η χρήση του Port & Adapter pattern επιτρέπει την εύκολη ενσωμάτωση εξωτερικών υπηρεσιών στο μέλλον.

---

## Prerequisites

| Tool | Needed for | Notes |
|---|---|---|
| Docker Desktop (Windows, macOS) or Docker Engine + Compose plugin (Linux) | database, backend, web portal | Compose v2 (`docker compose`). On Windows the one-click script offers to install it with winget |
| Python 3.8 or newer | seed scripts in `_Deployment/python/` | The scripts create `_Deployment/python/.venv` and install `requirements.txt` (`requests`, `bcrypt`, `psycopg2-binary`) into it. On Windows the script offers to install Python with winget |
| JDK 17 or newer | Gradle builds outside Docker | The modules declare `jvmToolchain(17)`; Gradle downloads a JDK 17 toolchain automatically (foojay resolver) if none is installed |
| Android SDK, platform 36 | only for rebuilding the kiosk app (`:app`); ready APKs are in `_Deployment/apk/` | Set `sdk.dir` in `_ProjectThesis/local.properties` |
| `adb` (Android platform-tools) | installing the app on a device from the PC (optional) | |

Building the stack with Docker needs nothing else: Gradle, Node.js (for Wasm) and the JDK are downloaded inside the build.
The Docker builder image runs Gradle on JDK 21 (`eclipse-temurin:21`), and Gradle provisions the JDK 17 toolchain there as
well; the backend runs on JRE 21. All downloads are kept in a BuildKit cache, so only the first build fetches them.

## Quick start (one click)

Every command in this guide that mentions `_Deployment` runs **in the `_Deployment` folder**. Its layout: `start-all` and `manage`
at the top, `apk/` (kiosk app), `docker/` (compose file, Dockerfile, `.env`), `steps/` (the three steps of `start-all` and
their helpers) and `python/` (seed and verification scripts). Its layout: `start-all` and `manage`
at the top, `apk/` (kiosk app), `docker/` (compose file, Dockerfile, `.env`), `steps/` (the three steps of `start-all` and
their helpers) and `python/` (seed and verification scripts). Its layout: `start-all` and `manage`
at the top, `apk/` (kiosk app), `docker/` (compose file, Dockerfile, `.env`), `steps/` (the three steps of `start-all` and
their helpers) and `python/` (seed and verification scripts). Its layout: `start-all` and `manage`
at the top, `apk/` (kiosk app), `docker/` (compose file, Dockerfile, `.env`), `steps/` (the three steps of `start-all` and
their helpers) and `python/` (seed and verification scripts).

| OS | Run |
|---|---|
| Windows | double-click `_Deployment\start-all.bat`, or in a terminal: `cd _Deployment` then `start-all.bat` (add `-Yes` to answer yes to the install questions) |
| Linux / macOS | `cd _Deployment` then `bash start-all.sh` (add `--yes` for unattended use) |

What it does (it stops at the first error with a clear message, and it is safe to run again):

1. **`01-check-dependencies`**: checks the Docker CLI, `docker compose` and that the Docker engine is running (on Windows
   and macOS it starts Docker Desktop and waits up to 3 minutes), checks Python 3, creates `_Deployment/python/.venv` and installs
   the packages of `python/requirements.txt` into it. Missing Docker Desktop or Python is installed with winget on Windows only
   after the script has printed what it will run and you have confirmed (or passed `-Yes`); on Linux/macOS the script prints
   the install command instead. If `_Deployment/docker/.env` does not exist it is created from `docker/.env.example` with **freshly
   generated random** `JWT_SECRET` and `SETTINGS_SECRET`; an existing `.env` is never modified.
2. **`02-build-and-start`**: checks that ports 80, 8080 and 5432 are free, runs `docker compose up --build -d` and waits
   until `http://localhost:8080/api/health` and `http://localhost/api/health` answer 200 (on failure it prints the backend
   log). The first build takes about 3-6 minutes; later runs only rebuild what changed.
3. **`03-seed`**: runs `seed_admin.py` (the platform admin) and `seed_data.py` (demo data) with the `.venv` Python and the
   database settings of `.env`. Both wait for the backend's tables and skip what already exists.

Each step also exists on its own in `_Deployment/steps/` (`01-check-dependencies.bat|.ps1|.sh`, `02-build-and-start.*`, `03-seed.*`).

The stack (compose project `guestdesk`) has three services: `postgres` (PostgreSQL 16, port 5432, published on
`127.0.0.1` only), `backend` (Ktor, port 8080) and `frontend` (nginx serving the production Wasm build of the web portal on
port 80, gzip-compressed, and proxying `/api` to the backend). Containers are named `guestdesk-postgres-1`,
`guestdesk-backend-1` and `guestdesk-frontend-1`; the data lives in the volume `guestdesk_postgres_data`. Database tables
are created automatically when the backend starts. The backend refuses to start without non-blank `JWT_SECRET` and
`SETTINGS_SECRET`.

Demo data (`seed_data.py`): 5 demo owners in Greek cities with 18 properties in total. Each owner has one catalogue of
20 products shared by its properties, and at each property 4 of these products are switched off, so every property shows
20 products, 4 of them unavailable. Plus the test owner with one property and 20 products.

Open http://localhost. The login screen has a **Demo quick login** with the two demo accounts:

| Account | Email | Password |
|---|---|---|
| Platform admin (`seed_admin.py`) | `admin@guestdesk.io` | `Admin@GuestDesk1` |
| Test owner (`seed_data.py`) | `owner@guestdesk.io` | `Owner@GuestDesk1` |

These are demo accounts only.

Stop and reset (in `_Deployment`):

| Action | Command |
|---|---|
| Stop, keep the data | `manage.bat down` (Linux/macOS: `bash manage.sh down`) |
| Status / logs | `manage.bat status`, `manage.bat logs` |
| Reset: delete the database and start over | `manage.bat reset` (asks for confirmation), then run `start-all` again |
| Start again after a stop | `manage.bat up` |

`manage` runs `docker compose` in `_Deployment/docker`; you can also run `docker compose` there directly.

### Manual steps (instead of the script)

In `_Deployment/docker`:

1. `cp .env.example .env` (Windows: `copy .env.example .env`) and fill in `JWT_SECRET` and `SETTINGS_SECRET` with long
   random values, e.g. `python -c "import secrets; print(secrets.token_hex(32))"`. They are empty in the example, so
   `docker compose` stops with "JWT_SECRET must be set" until you set them.
2. Optionally set `PUBLIC_API_URL` to the LAN address of this PC as seen from the kiosk tablet, for example
   `http://192.168.1.20:8080` (embedded in device QR codes; the default `http://localhost:8080` is enough for the web portal).
   The optional `PUBLIC_FRONTEND_URL` and `FOOD_PLATFORM_STEP_SECONDS` are listed, commented out, in `.env.example`.
3. `docker compose up --build -d`
4. In `_Deployment/python`: `pip install -r requirements.txt` (preferably in a virtual environment), then:
   ```bash
   python seed_admin.py                     # platform admin (idempotent)
   python seed_data.py                      # demo owners, properties, products and the test owner (idempotent)
   python seed_data.py --test-owner-only    # only the test owner
   ```
   The `.env` defaults match the script defaults. Override via `BASE_URL`, `ADMIN_EMAIL`, `ADMIN_PASS`,
   `TEST_OWNER_EMAIL`, `TEST_OWNER_PASS`, `SEED_WAIT_SECONDS` and `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`;
   `seed_admin.py` takes `ADMIN_EMAIL`, `ADMIN_PASS`, `SEED_WAIT_SECONDS` and the `DB_*` variables.

### End-to-end check (report, section 9.3)

`verify_flow.py` runs the 53 checks of the report through the public API (devices, orders, payments, refunds,
delivery platforms, accounts, input security) and exits with 0 when all pass. It creates its own test owner and data,
so run it on a fresh database rather than on the demo data (in `_Deployment`, with the `python/.venv` Python):

```bash
bash manage.sh reset && bash manage.sh up     # Windows: manage.bat reset, manage.bat up
cd python
python seed_admin.py
python verify_flow.py            # about 3 minutes; optional argument: base URL (default http://localhost:8080)
python seed_data.py              # afterwards, if you also want the demo data
```

### Troubleshooting the deployment

- **Docker is not running**: start Docker Desktop (Linux: `sudo systemctl start docker`) and run the script again. On
  Linux, "permission denied" means your user is not in the `docker` group.
- **Port in use**: step 2 names the program or container that holds port 80, 8080 or 5432. Stop it (`docker stop <name>`)
  or free the port. Container name conflicts with other stacks no longer happen (fixed project name `guestdesk`, no fixed
  container names).
- **Backend unhealthy**: the script prints `docker compose logs --tail 80 backend`. Typical causes: empty secrets in `.env`,
  or a database volume created earlier with a different `POSTGRES_PASSWORD` (reset with `manage reset`).
- **Seed script cannot connect / "start the backend first"**: the seed scripts wait up to `SEED_WAIT_SECONDS` (120) for the
  database and the tables. Another PostgreSQL on port 5432 of this PC may be shadowing the container.
- **`./gradlew: not found` (CRLF)**: `.gitattributes` keeps `gradlew` and `*.sh` at LF, and the Dockerfile strips CR from
  `gradlew` anyway. For the `.sh` scripts copied with Windows line endings run `sed -i 's/\r$//' *.sh steps/*.sh`.
- **First build slow**: about 3-6 minutes (Gradle, the JDK 17 toolchain, Kotlin/Wasm, Node and Binaryen are downloaded once
  into the build cache).
- **Starting over**: `manage reset`, then `start-all` again. Delete `_Deployment/docker/.env` only if you also want new
  secrets (stored SMTP/GEMH/platform keys then have to be entered again).

## Configure integrations (in the web portal)

Log in as the platform admin or owner and open:

- **Settings -> SMTP** (admin): host `smtp.gmail.com`, port 587, security STARTTLS, your Gmail address and a Gmail
  *app password* (requires 2-step verification). Use *Send test email* to verify. Without SMTP, owner welcome emails and
  guest receipts are skipped (a temporary owner password is still shown in the portal).
- **Settings -> GEMH** (admin): API key from https://opendata.businessportal.gr. It enables the company lookup by VAT id
  when creating an owner.
- **Owner -> Food delivery**: connect efood, Wolt and/or Box with any API key of at least 8 characters (emulated platforms).
- **Owner -> Properties -> a property**: enable the platforms the property offers, and fill in guest information
  (Wi-Fi, check-out time, house rules) shown on the kiosk.
- **Owner -> Products -> Import from platform**: copy items of a connected platform's catalog into your own products.

## Android kiosk app

1. Install a ready APK from `_Deployment/apk/`: `guestdesk-release.apk` (signed release build) or `guestdesk-debug.apk`
   (debug build, package `net.kustax.opendelivery.debug`, so both can be installed side by side). Copy the file to the
   tablet and open it, or run `adb install guestdesk-release.apk`. The server address is entered at activation, so the
   same APK works with any installation.
2. Optional, to rebuild the app yourself: set `sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk` in
   `_ProjectThesis/local.properties` (Android Studio creates this file automatically), then:
   ```bash
   cd _ProjectThesis
   ./gradlew :app:assembleDebug     # APK: app/build/outputs/apk/debug/
   ./gradlew :app:installDebug
   ```
3. adb over Wi-Fi (handy when the device is not at your desk): on Android 9 connect USB once, run `adb tcpip 5555`, unplug, then
   `adb connect <device-ip>:5555`. The PC and the device must be on the same network.
4. Register the device: in the web portal go to *Properties -> a property -> Devices -> create device*. On the kiosk, enter
   the server URL (the `PUBLIC_API_URL` value) and scan the QR code, or enter the activation code manually.
   The server address is also contained in the QR code.
5. Emulator: a landscape tablet image (API 35) works. Set `PUBLIC_API_URL=http://10.0.2.2:8080` and type `http://10.0.2.2:8080` as the server address. Everything works except the card reader; the payment screen reports that the reader is unavailable.
6. The app uses cleartext HTTP to the backend on the LAN. This is intentional for the demo (`usesCleartextTraffic`).
7. To leave kiosk mode, tap the invisible zone at the right end of the top bar five times within three seconds and
   enter the exit PIN `123456` (constant `DEMO_KIOSK_EXIT_PIN` in `AdminPinDialog.kt`).

Card payment demo rules (the card reader of the device produces the real card events; the acquirer is emulated):

- tap (contactless): approved
- chip: the app asks for a PIN; `0000` is declined (wrong PIN), any other 4 digits are approved
- swipe (magnetic stripe): declined

`_ProjectThesis/app/libs/reader-sdk.jar` is the SDK jar of the device's built-in card reader service (see `_ProjectThesis/app/libs/README.md`); it is committed because the app does not compile without it. The app can be built and run without the reader, but card events then only come from the real hardware.

## Real vs emulated

| Real | Emulated |
|---|---|
| SMTP email (welcome and receipt emails) | Payment acquirer (`EmulatedPaymentAdapter`) |
| GEMH company registry lookup | PIN verification (done in the app; `0000` fails) |
| Nominatim geocoding / address search | efood, Wolt and Box platforms (catalog, orders, timer-driven status changes every `FOOD_PLATFORM_STEP_SECONDS`) |
| Card reader hardware events | |

## Project structure

```
_ProjectThesis/  Gradle multi-module project (the complete Kotlin source)
  domain/     entities, enums, repository and external-service ports (KMP, no framework dependencies)
  data/       request/response DTOs shared by backend, web and app (KMP, kotlinx.serialization)
  backend/    Ktor server, Exposed/PostgreSQL repositories, services, adapters
  owner-web/  admin/owner web portal (Compose Multiplatform, Kotlin/Wasm)
  app/        Android kiosk app (Jetpack Compose)
  docker/     nginx.conf for the web portal container
_Deployment/  start-all and manage (.bat for Windows, .sh for Linux/macOS)
  apk/        guestdesk-release.apk, guestdesk-debug.apk (the kiosk app)
  docker/     docker-compose.yml, app.Dockerfile, .env.example (and the generated .env)
  steps/      01-check-dependencies, 02-build-and-start, 03-seed (.bat/.ps1/.sh), common.ps1/common.sh
  python/     seed_admin.py, seed_data.py, verify_flow.py, requirements.txt
_Docs/        Info/ (this guide and the technical guides), Postman/ (API collection), Images/ (screenshots and diagrams),
              Videos/ (demo videos), Presentation.pptx, Final_Report.docx
```

Hexagonal ports (`domain/port`) and their adapters (`backend/adapter`):

| Port | Adapter(s) |
|---|---|
| `NotificationService` | `SmtpEmailAdapter` |
| `CompanyRegistry` | `GemhCompanyRegistryAdapter` |
| `Geocoder` | `NominatimGeocoderAdapter` |
| `PaymentGateway` | `EmulatedPaymentAdapter` |
| `FoodDeliveryProvider` | `EmulatedEfoodAdapter`, `EmulatedWoltAdapter`, `EmulatedBoxAdapter` (shared base `EmulatedFoodPlatformAdapter`); `EmulatedFoodOrderRecovery` resumes in-flight orders after a restart |
| `FoodOrderEventListener` | `FoodOrderEventHandler` (service) |

Multi-tenancy: the `public` schema holds owners, platform admins, refresh tokens, audit log and platform settings; each
owner has its own schema with properties, devices, products, orders, payments and food-platform tables.

More detail: [Project structure](Project_Structure.md), [Dev environment](Dev_Environment.md),
[Build and dev guide](Build_and_Dev_Guide.md), Postman collection `_Docs/Postman/postman_collection.json`.

## Known limitations

- The database password in `_Deployment/docker/.env.example` is a demo value; the one-click script generates random `JWT_SECRET` and `SETTINGS_SECRET`. Use your own values for any real deployment.
- Plain HTTP between the kiosk, browser and backend (no TLS).
- The backend uses one database account for all tenants (the PostgreSQL superuser in the demo), so tenant isolation relies on the backend selecting the schema, not on database permissions.
- CORS allows any host. Login, token refresh and activation-code endpoints are rate limited per client IP (taken from `X-Forwarded-For`), which is only trustworthy when the backend is reached through the nginx proxy alone.
- Web portal tokens are kept in browser `localStorage`.
- Schema evolution happens at backend startup (idempotent create/alter) without a schema version table.
- The web portal and kiosk app are English-only.
- The payment acquirer and food platforms are emulated; no real money or orders move.

---

## Τεκμηρίωση & Εργαλεία
- [Δομή έργου](Project_Structure.md)
- [Περιβάλλον ανάπτυξης](Dev_Environment.md)
- [Οδηγός build και ανάπτυξης](Build_and_Dev_Guide.md)
- [Συλλογή Postman για το API](../Postman/postman_collection.json)

---
© 2026 Κωνσταντίνος Βλιώρας — Τμήμα Πληροφορικής
