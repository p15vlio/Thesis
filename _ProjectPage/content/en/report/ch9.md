The previous chapters described the system module by module: the database and the multi-tenancy strategy (Chapter 5), the backend (Chapter 6), the management web panel (Chapter 7) and the kiosk application (Chapter 8), on the foundations laid by the architecture of Chapter 4. This chapter brings these individual subsystems together into a single, end-to-end picture of operation, describes the environment used for developing and demonstrating it, the way the overall flow was verified, and records the limitations that still apply in the current implementation, so that the evaluation in Chapter 10 rests on concrete data.

## 9.1 The Full Operating Flow

The system's complete path, from setting up the platform to delivering an order to a guest, runs through all three application modules (backend, owner-web, app) around the shared domain/data core. The narrative that follows tracks one specific scenario step by step (Figure 9.1), with reference to the actual endpoints and the entities involved at each stage.

The scenario starts with the Platform Admin, who logs in to owner-web and, from the settings screen, sets the mail server and the company registry access key (§7.8). They then create a new owner (Use Case 2, §3.3.3): they type in the business's VAT ID, the system fills in its details from the registry, the administrator adds the contact person and submits POST /api/owners. The backend stores the record in the public schema, creates a dedicated schema (tenant\_{id}) with the tenant's nine tables (§5.1, §5.3) and sends the owner a welcome message with the temporary password, which is stored only as a BCrypt hash.

The owner logs in with the temporary password and, from the Food delivery screen, enables the connection with the delivery platforms they work with by entering their access keys (§7.7). They then create their first property through POST /api/properties, filling in the address through the address search, the information for the guest (Wi-Fi, check-out time, rules) and the platforms offered at the accommodation (§7.6). The accommodation's product catalogue is created manually or by importing items from a platform's catalogue.

Next, the owner registers a kiosk device for the property (POST /api/properties/{propertyId}/devices). The backend generates an activation code and a QR code that contains the device identifier, the code, the name of the tenant schema and the address of the API. The kiosk application scans the QR code (or alternatively calls GET /api/devices/resolve with the code) and calls POST /api/devices/{id}/activate (Use Case 3, §3.3.4, §8.2). The backend issues a JWT with the DEVICE role (§4.5.7), which the application stores encrypted and uses in every subsequent request. The application moves to the idle screen, ready to receive guests.

The guest touches the screen and chooses shopping. The stores screen shows the accommodation's store (GET /api/products, filtered so that products deactivated for the accommodation are excluded) and the restaurants of the enabled platforms (GET /api/food/restaurants). The guest picks a restaurant, adds dishes to the cart, completes the selection and taps their card on the reader. The application then submits POST /api/orders with the type FOOD_DELIVERY and the backend, after pricing the dishes from the platform's menu and checking the minimum order, stores it as PENDING. Straight afterwards the application submits POST /api/payments, the payment acquirer approves the transaction and the order becomes CONFIRMED. Only then does the backend submit the order to the platform, together with the accommodation's address, and send a receipt to the guest's email, if one was given (Use Case 5, §3.3.6).

The platform then informs the system of the order's progress (PREPARING, OUT_FOR_DELIVERY, DELIVERED) through the FoodOrderEventListener (§6.7), and the device's tracking screen shows each new status together with the countdown to the estimated arrival time. An order for the accommodation's own products follows the same flow up to the payment, but its progress is updated by the owner from the web panel (Use Case 4, §3.3.5). If a paid order is cancelled, by the owner, by the guest or because the platform did not accept it, the system automatically asks the payment provider to refund the amount. The administrative actions of this chain (creating a tenant, connecting a platform, creating a device) and every refund are recorded in the audit log, visible to the Platform Admin (§6.9).

This chain reflects in practice the hierarchy platform → owner → property → device → guest (§4.3): each level depends functionally on the previous one, and every kiosk request reaches the correct tenant schema automatically through the device token.

![Figure 9.1: Sequence diagram of the complete end-to-end flow, from the creation of an owner to the delivery of a food order.](/media/fig-9-1-end-to-end.png)

## 9.2 Development and Demonstration Environment

The development and demonstration of the system are organised through Docker Compose, with three services (Figure 9.2). The postgres service (PostgreSQL 16) hosts the database, with the data on a persistent volume, and exposes a healthcheck based on pg_isready; its port is published only on the loopback interface (127.0.0.1:5432), for the seed scripts on the same machine, and is not reachable from the local network. The backend service (the distribution of the Ktor server on JRE 21) starts only once the database has been marked healthy, exposes port 8080 and has its own healthcheck based on the endpoint GET /api/health. The frontend service serves, through nginx, the production (optimised) wasmJs bundle of owner-web on port 80, compressing the responses with gzip, and forwards /api requests to the backend (§7.1), starting only once the backend has been marked healthy. The backend and frontend services are built from a shared multi-stage Dockerfile, each with a different target, using the whole monorepo as the context, a direct consequence of the module organisation of §4.1. On the first build, Gradle, Node.js (for compiling to Wasm) and the dependencies are downloaded inside the build stage, which is why the first run is noticeably slower than later ones.

![Figure 9.2: Docker Compose topology — three services and their start-up dependencies.](/media/fig-9-2-docker-topology.png)

Configuration is set through environment variables. The backend's two secrets, the JWT signing secret and the source of the encryption key for stored secrets (§4.5.8), are mandatory: the backend refuses to start without them, rather than fall back on a default value that could be left by mistake in a real installation. The installation procedure described below generates them on its first run as cryptographically random values, so that only the database password remains a demonstration value. Other variables set the backend's address as it is reachable from the kiosk device on the local network, which is embedded in the QR codes, the login address included in the welcome message, and the interval between updates from the simulated platforms (§6.7). The email and company registry settings are not set here but from the web panel (§7.8).

Two seeding scripts written in Python fill the database with realistic demonstration data. The first inserts the first Platform Admin account directly into the database, with the password as a BCrypt hash, as the backend stores it (§4.5.7). Direct access to the database is necessary here, since there is not yet any user who could create the first administrator through the API.

The second script works the opposite way, mainly through the public REST API: it logs in as the administrator and creates five owner-businesses in different Greek cities (among others Athens, Thessaloniki and Rhodes), two to five properties per owner (eighteen in total) and, for each owner, one catalogue of twenty products shared by its properties, drawn from a catalogue of realistic items (beverages and hot drinks, snacks, personal care items, pharmaceuticals, alcohol, travel accessories); at every property four of them are switched off through per-property availability (§5.3.4). Because every record goes through the API, the script also exercises the backend's validation rules. It also creates a separate test owner with a fixed password, useful for repeated tests and for quick login during the demonstration. The script is idempotent: an owner whose email already exists is skipped, so it can be run again at any time; it reads the database directly only for this check and to set the test owner's fixed password. It does not connect the delivery platforms, so that the connection can be made live from the web panel during the demonstration.

The whole installation can also be carried out in one step, with a script that accompanies the code in versions for Windows, Linux and macOS. The script checks the dependencies (Docker, Docker Compose and Python; on Windows it offers, after asking, to install whatever is missing), creates the settings file with the generated secrets, builds and starts the three containers, waits until the backend and the web panel answer their health checks, and finally runs the seeding scripts. The first run takes about three to six minutes, depending on the machine and the connection, mostly for the build, while later runs, which reuse the caches, take less than a minute. Because existing settings are kept and the seeding scripts skip whatever already exists, the installation can safely be repeated. Detailed installation instructions are in the code repository.

## 9.3 Verification of the Integrated Flow

The operation of the whole was verified on a complete, isolated copy of the Docker Compose environment, with a real PostgreSQL database and data from the scripts of §9.2. A script that calls only the public REST API ran the flow of §9.1 from the side of the device, the owner and the administrator, checking at every step the HTTP status code and the content of the server's response. The script ships with the code, so the verification can be repeated on a fresh installation. The 53 checks are grouped in Table 9.1 and all completed with the expected result. The negative checks are particularly important, that is, those that confirm that an action that must not be allowed is rejected with the correct code, since these verify the security rules and the business rules of Chapters 4 to 6.

**Table 9.1: Groups of checks in the verification of the integrated flow**

| Group | What Was Verified | Checks |
|---|---|---|
| Authentication | Owner and administrator login, reading the owner's accommodations | 3 |
| Device activation and lifecycle | Creating a device, resolving the code, activation with token issue, reading the catalogue and the accommodation, heartbeat with an update of the last contact, rejecting a heartbeat without a token (401) or with an owner's token (403), refusing the token after deactivation (403) | 11 |
| Order and payment | The amount the server calculates, payment approval and the transition to CONFIRMED, rejecting a second payment (409), deactivating a product at an accommodation and rejecting an order for it (409), rejecting an order from an owner account (403) | 9 |
| Cancellations and refunds | Advancing to preparation by the owner, rejecting a cancellation from the device during preparation (409), cancellation by the owner with a refund, cancellation from the device before preparation with a refund | 6 |
| Delivery platforms | Connecting a platform, enabling it at an accommodation, restaurants and menus from the device, a food order and payment, autonomous progress up to DELIVERED | 8 |
| Order list filters | The status filter combined with a property returns only that property's orders, an unknown JSON field is rejected (400) | 4 |
| Accounts and passwords | Changing the password with a wrong current password (400), a successful change with new tokens, the other sessions' refresh rejected, login with the new password, a reset by the administrator and login with the temporary password, deactivating an owner and rejecting their access token on the very next request | 9 |
| Input security | Rejecting a malicious schema name (400) and an invalid activation code, rate limiting on login attempts (429) | 3 |

This verification concerns the backend and the API contract. The interfaces of the web panel and of the kiosk application are not covered by automated checks, and the system has no automated unit tests; systematic coverage with automated tests is included in the proposals of Chapter 10.

## 9.4 Known Limitations

Beyond the scope limits set from the start in the requirements analysis (§3.4), the current implementation has some further limitations, which are recorded below.

The payment acquirer and the food delivery platforms are simulated (§3.4.1): no transaction actually charges a card and no order reaches a real restaurant. The PIN check is simulated in the application. The card events come from a real card reader, but the implementation of the CardTerminal requires an Android device that has the corresponding manufacturer service; on an emulator or on an ordinary tablet, the reader is reported as unavailable and the order stops at payment (§8.9).

Full locking of the device requires it to be managed by a Device Policy Controller set as Device Owner, a configuration that was not carried out within the scope of the thesis; without it, the application works normally, but with at most screen pinning instead of the real locking of the operating system (§8.3). The exit code from kiosk mode is fixed for the needs of the demonstration.

The demonstration installation is not configured for real use. Communication between the device, the browser and the backend uses plain HTTP without TLS, which is acceptable in a closed local demonstration network but not in a real installation. The backend's secrets are generated randomly at installation, but the database password, like the demonstration accounts created by the seeding scripts, is a demonstration value that must be replaced. The web panel's tokens are stored in the browser's localStorage (§7.5). The backend connects to the database with one account for all tenants, in the demonstration installation the database administrator; tenant isolation therefore rests on the application selecting the schema (§4.3) rather than on database permissions, which a separate role per tenant would add. The rate limit of the public endpoints is based on the IP address reported by the X-Forwarded-For header, which nginx overwrites with the address of the connection, so that a client cannot spoof it, but which is reliable only when the backend is reachable exclusively through the proxy; in a real installation the backend's port would not be exposed directly.

Finally, some choices were made to keep the implementation simple: the device and the web panel learn about order status through polling rather than push notifications, the database structure is updated with idempotent statements at start-up, without a schema version table (§5.1), and the interfaces of the web panel and of the kiosk application exist only in English, with no translation mechanism.

These limitations do not affect the basic flow of §9.1: device authentication, routing to the correct tenant schema, pricing on the server, the connection to the payment provider with its uniqueness and refund mechanisms, the integration of the delivery platforms and the tracking of orders worked end to end against a real backend and a real database, as verified in §9.3.

## 9.5 Technologies, Versions and Code Size

Table 9.2 brings together the versions of the main technologies and libraries with which the system was implemented and verified. All the library versions are declared in a shared Gradle version catalog, so that every module uses the same version of each library.

**Table 9.2: Main technologies, libraries and versions**

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

Table 9.3 shows the size of the source code per module, measured in Kotlin files and lines. In total the system consists of 268 files and about 22,500 lines of code, and the backend exposes 65 endpoints.

**Table 9.3: Source code size per module**

| Module | Kotlin Files | Lines of Code | Contents |
|---|---|---|---|
| :domain | 42 | 523 | Entities, enums, repository and port interfaces |
| :data | 46 | 637 | Request and response DTOs |
| :backend | 79 | 5,695 | Routes, services, repositories, adapters, security |
| :owner-web | 46 | 9,450 | Screens and components of the web panel |
| :app | 55 | 6,186 | Screens, ViewModels, payment, kiosk, storage |
| **Total** | **268** | **22,491** |  |

Two observations follow from these sizes. First, the shared code (:domain and :data) is small in volume, about 1,160 lines or 5% of the total, yet it consists exclusively of the definitions shared by all three execution environments; its value lies in the fact that these definitions are written once. Second, the user interface code (web panel and Android application) exceeds two thirds of the total, while the backend, which holds most of the business logic, accounts for about a quarter. This proportion is to be expected in a system with many management forms and indicates where further code sharing would pay off most (§10.6).

## 9.6 Development Process

Development began in March 2026 and proceeded iteratively, in short cycles during the first months. The system was implemented in the following order: research and technology selection, requirements analysis and modelling, the database layer and authentication, the service layer and REST API, and then the web panel's screens for the owner and the administrator. This order follows the direction of the dependencies in §4.1: each module was implemented after the one it depends on had stabilised. The kiosk application, the delivery platforms, payment on the device and the real external services were added in later stages, on top of the stabilised core.

The tool that supported backend development most, before the user interfaces were implemented, was a collection of Postman requests with 79 requests in twelve groups (from authentication to the delivery platforms, the settings and the company and address lookups). Each request stores the values of its response (tokens, identifiers, schema name) in collection variables, so that the following requests run in order without manually copying values. The collection accompanies the code and at the same time serves as executable documentation of the API.

Alongside development, systematic quality reviews of the code were carried out, with checks for security, the correctness of transactions and the correct use of coroutines. These reviews identified problems such as the vulnerability in the choice of the tenant schema (§6.10), the lack of atomicity in order creation and in owner onboarding, the possibility of two concurrent payments for the same order, and the incorrect handling of coroutine cancellation in the web panel (§7.9), which were fixed and verified before the thesis was completed. The experience from these findings is discussed in Chapter 10.
