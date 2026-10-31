The previous chapters described the system module by module: the database and the multi-tenancy schema (Chapter 5), the backend (Chapter 6), the management web panel (Chapter 7) and the kiosk application (Chapter 8), on the foundations laid by the architecture of Chapter 4. This chapter brings these individual pieces together into a single, end-to-end picture of operation, describes the environment used for developing and demonstrating it, the way the overall flow was verified, and records the limitations that still apply in the current implementation, so that the evaluation in Chapter 10 rests on concrete data.

## 9.1 The Full Operating Flow

The system's complete path, from setting up the platform to delivering an order to a guest, runs through all three application modules (backend, owner-web, app) around the shared domain/data core. The narrative that follows tracks one specific scenario step by step, with reference to the actual endpoints and the entities involved at each stage.

The scenario starts with the Platform Admin, who logs in to owner-web and, from the settings screen, sets the mail server and the GEMI access key (§7.8). They then create a new owner (Use Case 2, §3.3.3): they type in the business's VAT ID, the system fills in its details from GEMI, the administrator adds the contact person and submits POST /api/owners. The backend stores the record in the public schema, creates a dedicated schema (tenant\_{id}) with the tenant's nine tables (§5.1, §5.3) and sends the owner a welcome message with the temporary password, which is stored only as a BCrypt hash.

The owner logs in with the temporary password and, from the Food delivery screen, connects the delivery platforms they work with by entering their access keys (§7.7). They then create their first property through POST /api/properties, filling in the address through the geocoding search, the information for the guest (Wi-Fi, check-out time, rules) and the platforms offered at the accommodation (§7.6). The accommodation's product catalogue is created manually or by importing items from a platform's catalogue.

Next, the owner registers a kiosk device for the property (POST /api/properties/{propertyId}/devices). The backend generates an activation code and a QR code that contains the device identifier, the code, the name of the tenant schema and the address of the API. The kiosk application scans the QR code (or alternatively calls GET /api/devices/resolve with the code) and calls POST /api/devices/{id}/activate (Use Case 3, §3.3.4, §8.2). The backend issues a JWT with the DEVICE role and a validity of one year, which the application stores encrypted and uses in every subsequent request. The application moves to the idle screen, ready to receive guests.

The guest touches the screen and chooses shopping. The stores screen shows the accommodation's store (GET /api/products, filtered so that products deactivated for the accommodation are excluded) and the restaurants of the enabled platforms (GET /api/food/restaurants). The guest picks a restaurant, adds dishes to the cart and completes the order. The application submits POST /api/orders with the type FOOD_DELIVERY and the backend, after pricing the dishes from the platform's menu and checking the minimum order, stores it as PENDING. The guest taps their card on the reader; the application submits POST /api/payments, the payment acquirer approves the transaction and the order becomes CONFIRMED. Only then does the backend submit the order to the platform, together with the accommodation's address, and send a receipt to the guest's email, if one was given (Use Case 5, §3.3.6).

From there on the platform informs the system of the order's progress (PREPARING, OUT_FOR_DELIVERY, DELIVERED) through the FoodOrderEventListener (§6.7), and the device's tracking screen shows each new status together with the countdown to the estimated arrival time. An order for the accommodation's own products follows the same flow up to the payment, but is advanced by the owner from the web panel (Use Case 4, §3.3.5). If a paid order is cancelled, by the owner, by the guest or because the platform did not accept it, the amount is refunded automatically to the card. The administrative actions of this chain (creating a tenant, connecting a platform, creating a device) and every refund are recorded in the audit log, visible to the Platform Admin (§6.9).

This chain reflects in practice the hierarchy platform → owner → property → device → guest (§4.3): each level depends functionally on the previous one, but none needs to know the internal implementation details of the other. Authenticating the device through a JWT, combined with the tenant schema claim that the same token carries, allows every kiosk request to reach the correct schema automatically without the Android application needing to know which owner it belongs to.

## 9.2 Development and Demonstration Environment

The development and demonstration of the system are organised through Docker Compose, with three services. The postgres service (PostgreSQL 16) hosts the database, with the data on a persistent volume, and exposes a healthcheck based on pg_isready. The backend service (the distribution of the Ktor server on JRE 21) starts only once the database has reported as healthy, exposes port 8080 and has its own healthcheck based on the endpoint GET /api/health. The frontend service serves, through nginx, the compiled wasmJs bundle of owner-web on port 80 and forwards /api requests to the backend (§7.1), starting only once the backend has reported as healthy. The backend and frontend services are built from a shared multi-stage Dockerfile, each with a different target, using the whole monorepo as the context, a direct consequence of the module organisation of §4.1. The first build takes a few minutes, since Gradle, Node.js (for compiling to Wasm) and the dependencies are downloaded inside the build stage.

Configuration is done through environment variables, which are set in a .env file next to the Docker Compose file. JWT_SECRET (the JWT signing secret) and SETTINGS_SECRET (the source of the secrets' encryption key, §4.5.8) are mandatory; the backend refuses to start without them, instead of using a default value that could be left by mistake in a real installation. PUBLIC_API_URL is the address of the backend as the kiosk device sees it on the local network and is embedded in the QR codes, PUBLIC_FRONTEND_URL is the login address contained in the welcome message, and FOOD_PLATFORM_STEP_SECONDS is the interval between updates from the emulated platforms (§6.7). The email and GEMI settings are not set here but from the web panel (§7.8). The settings screen is pre-filled for a Gmail account with an app password, so that with such an account the welcome messages and receipts reach real mailboxes without a separate mail server.

Two Python scripts are used to fill the database with realistic demonstration data. seed_admin.py inserts the first Platform Admin account directly into the database, through the psycopg2 library, with the password hashed with BCrypt at cost 12, the same parameter the backend uses (§4.5.7). Direct access to the database is necessary here, since there is not yet any user able to create the first administrator through the API. seed_data.py works the opposite way, through the public REST API: it logs in as the administrator and creates five owner-businesses in different Greek cities (among others Athens, Thessaloniki and Rhodes), two to five properties per owner (eighteen in total) and twenty products per property from a catalogue of realistic items (beverages, snacks, personal care items, pharmaceuticals, alcohol), of which four are deliberately marked as unavailable. Because every record is created through the API, the script verifies the backend's validation rules at the same time. The same script also creates a separate test owner with a fixed password, useful for repeated tests and for the demonstration's quick login. Connecting to delivery platforms is not done by the script, so that it can be presented from the web panel during the demonstration.

## 9.3 Verification of the Integrated Flow

The operation of the whole was verified on a complete, isolated copy of the Docker Compose environment, with a real PostgreSQL database and data from the scripts of §9.2. A script that calls only the public REST API ran the flow of §9.1 from the side of the device, the owner and the administrator, checking at every step the code and the content of the server's response. The 47 checks are grouped in Table 9.1 and all completed with the expected result. The negative checks are particularly important, that is, those that confirm that an action that must not be allowed is rejected with the correct code, since these verify the security rules and the business rules of Chapters 4 to 6.

| Group | What Was Verified | Checks |
|---|---|---|
| Authentication | Owner and administrator login, reading the owner's accommodations | 3 |
| Device activation and lifetime | Creating a device, resolving the code, activation with token issue, reading the catalogue and the accommodation, heartbeat with an update of the last contact, rejecting a heartbeat without a token (401) or with an owner's token (403), rejecting the token after deactivation | 11 |
| Order and payment | The amount the server calculates, payment approval and the transition to CONFIRMED, rejecting a second payment (409), deactivating a product at an accommodation and rejecting an order for it (409), rejecting an order from an owner account (403) | 8 |
| Cancellations and refunds | Advancing to preparation by the owner, rejecting a cancellation from the device during preparation (409), cancellation by the owner with a refund, cancellation from the device before preparation with a refund | 6 |
| Delivery platforms | Connecting a platform, enabling it at an accommodation, restaurants and menus from the device, a food order and payment, autonomous progress up to DELIVERED | 8 |
| Accounts and passwords | Changing the password with a wrong current password (400), a successful change with new tokens, terminating the other sessions, login with the new password, a reset by the administrator and login with the temporary password, deactivating an owner and rejecting their access token on the very next request | 8 |
| Input security | Rejecting a malicious schema name (400) and an invalid activation code, rate limiting on login attempts (429) | 3 |

This verification concerns the backend and the API contract. The interfaces of the web panel and of the kiosk application are not covered by automated checks, and the system has no automated unit tests; systematic coverage with automated tests is included in the proposals of Chapter 10.

## 9.4 Known Limitations

Beyond the scope limits set from the start in the requirements analysis (§3.4), the current implementation retains certain limitations, which are recorded here so that the evaluation in Chapter 10 rests on the real picture of the code.

The first concerns the simulated services. The payment acquirer and the food delivery platforms are emulated (§3.4.1): no transaction actually charges a card and no order reaches a real restaurant. The PIN check is simulated in the application. The card events come from a real card reader, but the implementation of the CardTerminal requires an Android device that has the corresponding manufacturer service; on an emulator or on an ordinary tablet, the reader is reported as unavailable and the order flow stops at payment (§8.9).

The second concerns kiosk operation. Full locking of the device depends on its enrolment as Device Owner through a Device Policy Controller, which was not carried out within the scope of the thesis; without it, the application works normally but without the real locking of the operating system (§8.3). The exit code from kiosk mode is fixed for the needs of the demonstration.

The third concerns the security of the demonstration installation. Communication between the device, the browser and the backend uses plain HTTP without TLS, which is acceptable in a closed local demonstration network but not in a real installation. The values in the .env file (the database password, JWT_SECRET, SETTINGS_SECRET) are demonstration values that must be replaced. The web panel's tokens are stored in the browser's localStorage (§7.5). The rate limit of the public endpoints is based on the IP address reported by the X-Forwarded-For header, which is reliable only when the backend is reachable exclusively through the proxy; in a real installation the backend's port would not be exposed directly.

The fourth concerns choices of implementation simplicity. Updating the device and the web panel on the status of orders is done with polling and not with push notifications, updating the database structure is done with idempotent statements at start-up, without a schema version table (§5.1), and the interfaces of the web panel and of the kiosk application are written only in English, with no translation mechanism.

None of these limitations negates the basic flow of §9.1: device authentication, routing to the correct tenant schema, pricing on the server, payment with the guarantees of uniqueness and refunds, the integration of the delivery platforms and the tracking of orders all work end to end against a real backend and a real database, as verified in §9.3.

## 9.5 Technology Stack and Code Size

Table 9.2 brings together the versions of the main technologies and libraries with which the system was implemented and verified. All the library versions are declared in a shared Gradle version catalog, so that every module uses the same version of each library.

| Layer | Technology | Version |
|---|---|---|
| Language and shared code | Kotlin, Kotlin Multiplatform | 2.4.21 |
| Serialisation and asynchronous execution | kotlinx.serialization, kotlinx.coroutines | 1.11.0 |
| Build system | Gradle (Kotlin DSL), Android Gradle Plugin | 9.8.1, 9.1.0 |
| Backend runtime environment | JDK 17 (development), JRE 21 (Docker) | — |
| Web framework | Ktor (server and client) | 3.6.0 |
| Database access | JetBrains Exposed, HikariCP, PostgreSQL JDBC driver | 1.5.1, 7.1.0, 42.7.14 |
| Database | PostgreSQL | 16 |
| Security | Ktor Auth JWT, BCrypt (at.favre), the JDK's AES-GCM | 3.6.0, 0.10.2, — |
| Email | Jakarta Mail | 2.0.2 |
| QR generation and scanning | ZXing, ZXing Android Embedded | 3.5.4, 4.3.0 |
| Web panel | Compose Multiplatform (wasmJs) | 1.12.0 |
| Android application | Jetpack Compose (BOM), Navigation Compose, DataStore, Security Crypto, Coil | 2026.06.01, 2.9.8, 1.2.1, 1.1.0, 2.7.0 |
| Android platform | minSdk / compileSdk | 28 / 36 |
| Logging | Logback | 1.6.5 |
| Development and demonstration | Docker Compose, nginx | — |

Table 9.3 shows the size of the source code per module, measured in Kotlin files and lines. In total the system consists of 267 files and about 22,200 lines of code, and the backend exposes 65 endpoints.

| Module | Kotlin Files | Lines of Code | Contents |
|---|---|---|---|
| :domain | 42 | 518 | Entities, enums, repository and port interfaces |
| :data | 46 | 637 | Request and response DTOs |
| :backend | 79 | 5,529 | Routes, services, repositories, adapters, security |
| :owner-web | 45 | 9,372 | Screens and components of the web panel |
| :app | 55 | 6,157 | Screens, ViewModels, payment, kiosk, storage |
| **Total** | **267** | **22,213** |  |

Two observations follow from these sizes. First, the shared code (:domain and :data) is small in volume, about 1,150 lines or 5% of the total, yet it consists exclusively of the definitions shared by all three execution environments; its value lies in the fact that these definitions are written once. Second, the interface code (web panel and Android application) exceeds two thirds of the total, while the backend, which holds all of the business logic, accounts for about a quarter. This proportion is to be expected in a system with many management forms and indicates where further code sharing would pay off most (§10.6).

## 9.6 Development Process

Development began in March 2026 and proceeded iteratively, in one-week cycles during the first months. Each cycle ended in a weekly progress report, and seven such reports document the order in which the system was built: research and technology selection, requirements analysis and modelling, the database layer and authentication, the service layer and REST API, and then the web panel's screens for the owner and the administrator. This order follows the direction of the dependencies in §4.1: each module was built after the one it depends on had stabilised. The kiosk application, the delivery platforms, card payments and the real external services were added in later stages, on top of the stabilised core.

The tool that supported backend development most, before the interfaces existed, was a collection of Postman requests with 79 requests in twelve groups (from authentication to the delivery platforms, the settings and the lookups in GEMI and Nominatim). Each request stores the values it returns (tokens, identifiers, schema name) in collection variables, so that the following requests run in order without manually copying values. The collection accompanies the code and at the same time serves as executable documentation of the API.

Alongside development, systematic quality reviews of the code were carried out, with checks for security, the correctness of transactions and the correct use of coroutines. These reviews identified findings such as the vulnerability in the choice of the tenant schema (§6.10), the lack of atomicity in order creation and in owner onboarding, the possibility of a double charge for an order, and the incorrect handling of coroutine cancellation in the web panel (§7.9), which were fixed and verified before the thesis was completed. The experience from these findings is discussed in Chapter 10.
