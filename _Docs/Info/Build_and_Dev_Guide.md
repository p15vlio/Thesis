# Build and Dev Guide

Setup and first run: see the [Start Guide](./Start_Guide.md). Environment variables and containers: [Dev_Environment.md](./Dev_Environment.md).

## Build commands

Run from `_ProjectThesis/` (Gradle wrapper 9.8.1, JDK 17 or newer to run Gradle; the modules declare `jvmToolchain(17)`,
and Gradle downloads a JDK 17 toolchain automatically through the foojay resolver if none is installed):

```bash
./gradlew :backend:installDist            # backend distribution (build/install/backend)
./gradlew :owner-web:compileKotlinWasmJs  # compile check of the web portal
./gradlew :owner-web:wasmJsBrowserDistribution              # production static files, served by nginx in Docker
./gradlew :owner-web:wasmJsBrowserDevelopmentDistribution   # larger, unoptimised development build
./gradlew :app:assembleDebug              # kiosk APK (needs Android SDK platform 36 in local.properties); ready APKs: _Deployment/apk/; ready APKs: _Deployment/apk/; ready APKs: _Deployment/apk/; ready APKs: _Deployment/apk/
./gradlew :app:installDebug               # install on the connected device/emulator
```

The Docker images run the backend and web portal builds themselves (`start-all`, or `manage up` in
`_Deployment`), so Gradle is only needed locally for development or for the Android app. In Docker, Gradle runs on the
JDK 21 of the `eclipse-temurin:21` builder image and provisions the JDK 17 toolchain there too; Gradle's downloads
(dependencies, toolchain, Node.js, Yarn, Binaryen) are kept in a BuildKit cache mount, so they are fetched only once.
The Dockerfile strips CR from `gradlew` before running it, and `.gitattributes` keeps `gradlew` and `*.sh` at LF.

## Testing the API with Postman

Import `_Docs/Postman/postman_collection.json`. It uses collection variables (`baseUrl`, `admin_token`, `owner_token`,
`device_token`, `owner_schema`, `property_id`, ...) that the test script of each request fills in. Run order the first time:

1. `1a` Login Platform Admin (after `python/seed_admin.py`)
2. `2a` Create Owner: saves the owner id, schema, email and temporary password (also emailed if SMTP is configured)
3. `1b` Login Owner
4. `3a` Create Property, `4a` Create Device, `4e` Activate Device (saves the long-lived `device_token`)
5. `5a` Create Product, `6a` Create Order, `7a` Initiate Payment (the order becomes `CONFIRMED`), then `6e` PREPARING, `6f` OUT_FOR_DELIVERY and `6g` DELIVERED
6. Run `6a` again before `7b` (chip payment) and `6j` (cancel your order): a paid order cannot be paid twice, and a delivered order cannot be cancelled

Further folders cover food platforms, platform settings (SMTP, GEMH), company lookup, geocoding, dashboard, admin stats and audit logs.

## Lifecycle rules

- Owners are never hard-deleted: `PATCH /owners/{id}/deactivate` and `/activate`. A deactivated owner cannot log in or refresh tokens, and their access token is rejected on the next request.
- Passwords: a signed-in admin or owner changes their own password with `POST /auth/change-password` (signs out their other sessions). A platform admin resets a forgotten owner password with `PATCH /owners/{id}/reset-password`; the new temporary password is emailed and returned once.
- Properties and devices support soft (`deactivate`/`activate`/`reactivate`) and hard (`DELETE`) removal. Deactivating a property deactivates its devices.
- `POST /devices/{id}/unregister` resets a device to unactivated with a new activation code and QR.
- A device JWT is long-lived, but the device's `isActive` flag is checked on every request, so deactivation takes effect immediately. The kiosk sends a heartbeat (`PATCH /devices/{id}/ping`, device JWT) every minute, shown to the owner as the device's last-seen time.
- A guest can cancel an order from the kiosk only before preparation starts (`PENDING`/`CONFIRMED`); later cancellations are the owner's. Cancelling a paid order refunds it.
- Rate limits: 20 requests per minute per client IP on login, refresh, activation-code resolve and activate; 30 address searches per minute per user. Exceeding them returns 429.
- Products are defined per owner. A product can additionally be switched off for a single property (`PATCH /products/{id}/properties/{propertyId}/availability`); the kiosk catalog is filtered accordingly.
- Products can be imported from a connected food platform's catalog (`POST /products/import`).

## Troubleshooting

- **Backend container unhealthy / database connection refused**: PostgreSQL needs 5-10 seconds on first start; check `docker compose logs backend`. The backend also refuses to start with an empty `JWT_SECRET` or `SETTINGS_SECRET`.
- **Seed script cannot connect**: another PostgreSQL on port 5432 may be shadowing the container. The scripts wait up to `SEED_WAIT_SECONDS` for the backend's tables ("start the backend first").
- **Port 80, 8080 or 5432 already in use**: `02-build-and-start` names the program or container; stop it and run again.
- **Kiosk cannot reach the server**: `PUBLIC_API_URL` must be the PC's LAN address (not `localhost`); allow port 8080 in the Windows firewall; after changing it run `docker compose up -d` and create a new device (or unregister the old one) so the QR carries the new address.
- **First Docker build is slow (about 3-5 minutes)**: Gradle, the JDK 17 toolchain, Node and Binaryen are downloaded inside the build; later builds use the layer and Gradle caches.
- **Start over**: `manage reset` in `_Deployment` deletes the database volume; `start-all` then seeds it again.

---
[Back to the Start Guide](./Start_Guide.md)
