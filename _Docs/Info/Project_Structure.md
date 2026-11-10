# Project Structure

Guest Desk is a Kotlin Multiplatform monorepo (Gradle, Kotlin DSL). The Gradle project lives in `_ProjectThesis/`.

## Modules

| Module | Target | Contents |
|---|---|---|
| `:domain` | KMP common | Entities (`entity/platform`, `entity/tenant`), enums (`UserRole`, `OrderStatus`, `OrderType`, `PaymentStatus`, `CardEntryMode`, `ExternalPlatform`, `ProductCategory`), repository interfaces and external-service ports. No framework dependencies. |
| `:data` | KMP common | Request/response DTOs (`kotlinx.serialization`) shared by backend, web portal and kiosk app. |
| `:backend` | JVM, Ktor | Routing, services, Exposed repositories, adapters, JWT/BCrypt security, schema management. |
| `:owner-web` | Kotlin/Wasm, Compose Multiplatform | Web portal for Platform Admins and Owners. |
| `:app` | Android, Jetpack Compose | Guest kiosk app for a payment terminal with a card reader (minSdk 28, compileSdk 36). |

## Backend (`net.kustax.opendelivery.backend`)

- `database/`: `DatabaseFactory` (Hikari + Exposed), `SchemaManager` (creates platform tables, provisions and reconciles one schema per owner at startup), `TenantContext`, `table/platform` and `table/tenant`.
- `repository/`: Exposed implementations of the domain repository interfaces (`platform/` for the shared `public` schema, `tenant/` for per-owner schemas).
- `service/`: `AuthService`, `OwnerService`, `PropertyService`, `DeviceService`, `ProductService`, `OrderService` (status state machine), `PaymentService`, `RefundService` (refunds the payment of a cancelled order), `FoodDeliveryService`, `FoodOrderEventHandler`, `DashboardService`, `PlatformSettingsService`, `AuditLogService`.
- `routing/`: Ktor routes under `/api`: health, auth, owners, properties, devices, products (including import), orders, device orders, payments, food platforms and the kiosk food catalog, dashboard, admin stats, audit logs, settings (SMTP, GEMH), company lookup and geocoding. Roles are `PLATFORM_ADMIN`, `OWNER` and `DEVICE`; a platform admin selects a tenant with the `schemaName` query parameter. The Postman collection lists every endpoint.
- `security/`: `JwtConfig`, `PasswordHasher` (BCrypt), `SecretCipher` (encrypts stored secrets with `SETTINGS_SECRET`).
- `adapter/`: implementations of the domain ports (below).

Multi-tenancy: the `public` schema holds `owners`, `platform_admins`, `refresh_tokens`, `audit_log` and `platform_settings`. Each owner gets a schema with `properties`, `devices`, `products`, `product_property_overrides`, `orders`, `order_items`, `payments`, `food_platform_connections` and `property_food_platforms`.

## Ports and adapters

| Port (`domain/port`) | Adapter (`backend/adapter`) | Real or emulated |
|---|---|---|
| `NotificationService` | `SmtpEmailAdapter` | Real SMTP (configured in Settings) |
| `CompanyRegistry` | `GemhCompanyRegistryAdapter` | Real GEMH open data API |
| `Geocoder` | `NominatimGeocoderAdapter` | Real OpenStreetMap Nominatim |
| `PaymentGateway` | `EmulatedPaymentAdapter` | Emulated acquirer |
| `FoodDeliveryProvider` | `EmulatedEfoodAdapter`, `EmulatedWoltAdapter`, `EmulatedBoxAdapter` | Emulated (base `EmulatedFoodPlatformAdapter`; `EmulatedFoodOrderRecovery` resumes orders after a restart) |
| `FoodOrderEventListener` | `FoodOrderEventHandler` (service) | Updates order status from platform events |

## Web portal (`:owner-web`, package `net.kustax.opendelivery.web`)

- `api/ApiClient.kt`: Ktor JS client with bearer auth and single-flight token refresh; `auth/`: login screen, session storage, session controller.
- Admin screens: `AdminDashboardScreen`, `OwnerListScreen`, `AdminOwnerViewScreen`, `AdminPropertiesScreen`, `AdminDevicesScreen`, `AdminProductsScreen`, `AuditLogScreen`, `SettingsScreen` (SMTP, GEMH).
- Owner screens: `DashboardScreen`, `PropertyListScreen`, `DeviceListScreen`, `ProductListScreen`, `FoodDeliveryScreen`, `OrderListScreen`, `OwnerOrdersScreen`.
- Shared: `AboutScreen`, `HelpScreen`; reusable components in `ui/`.

## Kiosk app (`:app`, package `net.kustax.opendelivery`)

`ui/screen/` (idle, home, stores, store, payment, confirmation, tracking, info, registration), `payment/` (`CardTerminal`, `DeviceCardTerminal`, the client of the device's built-in card reader service, using `app/libs/reader-sdk.jar`), `kiosk/` (`KioskManager`, boot receiver), `data/remote` (API client and repositories), `data/local` (preferences), `navigation/`.

## Other directories

- `_Deployment/`: everything needed to run the system with Docker.
  - `start-all.bat` (Windows) and `start-all.sh` (Linux/macOS): the one-click deployment, running the three steps in `steps/`.
  - `manage.bat` / `manage.sh`: `up`, `down`, `status`, `logs`, `reset` of the running stack.
  - `apk/`: the kiosk app, `guestdesk-release.apk` (signed) and `guestdesk-debug.apk`.
  - `docker/`: `docker-compose.yml` (project `guestdesk`), `app.Dockerfile` (multi-stage: Gradle on JDK 21 with a JDK 17 toolchain, JRE 21 backend, nginx frontend with the production Wasm build), `.env.example` and the generated `.env`. The nginx configuration of the frontend image is `_ProjectThesis/docker/nginx.conf`.
  - `steps/`: the steps of `start-all` (each as `.bat`/`.ps1` and `.sh`), `start-all.ps1` and the shared helpers `common.ps1`, `common.sh`.
    - `01-check-dependencies`: checks Docker, Docker Compose, the engine and Python, creates `python/.venv` with `requirements.txt`, and creates `docker/.env` with random secrets if it is missing.
    - `02-build-and-start`: checks the ports, runs `docker compose up --build -d` and waits for the health checks.
    - `03-seed`: runs `seed_admin.py` and `seed_data.py` (both idempotent).
  - `python/`: `seed_admin.py`, `seed_data.py`, `verify_flow.py` (the 53 end-to-end API checks of the report) and `requirements.txt`.
- `_Docs/`: `Info/` (this documentation, including `Start_Guide.md`), `Postman/postman_collection.json` (79 requests covering all 65 endpoints) and `Images/` (screenshots of the web portal and kiosk app, and the diagrams of the report).

---
[Back to the Start Guide](./Start_Guide.md)
