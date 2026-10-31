## 4.1 Architecture Overview and Module Structure

The proposed system is implemented as a single code repository (monorepo), which contains five distinct modules that together define the logic, the data models and the individual applications of the platform. Choosing a monorepo architecture ensures that the shared modules (:domain and :data) are kept continuously in sync at the same version for every consumer, allowing the compiler to detect any type mismatch or change to a DTO contract immediately as a compile-time error on all platforms at once, preventing failures at runtime.

The five modules of the architecture are organised hierarchically in two levels: the **shared modules** (:domain, :data) and the **application modules** (:backend, :owner-web, :app).

The :domain module is the independent core of the system. It contains the central business entities (Order, Product, Device, Property), the repository interfaces and the port interfaces for connecting to external services. This core depends on no external framework, infrastructure library or other module of the project. It consists of pure Kotlin code and is compiled simultaneously into three distinct artifacts: Java bytecode (JVM jar) for the backend, an Android library (AAR/jar) for the kiosk application, and a WebAssembly binary file (wasmJs bundle) for the browser.

The :data module depends only on :domain and on the kotlinx.serialization library. It defines the data transfer objects (Data Transfer Objects - DTOs), as well as the requests and responses of the REST API, implemented as @Serializable Kotlin classes. This module is also compiled for all three targets (JVM, Android, wasmJs), forming the shared communication contract between server and clients.

The application modules depend on :data (and through it on :domain), strictly observing the direction of dependencies [24]:

- :backend is a JVM application that implements the REST API through Ktor, takes care of database access through JetBrains Exposed and manages the connection to PostgreSQL.
- :owner-web is a management application for the browser, compiled to WebAssembly (wasmJs) and executed through Compose Multiplatform.
- :app is the accommodation's kiosk application for the Android environment, implemented with Jetpack Compose.

The direction of dependencies is strictly enforced by the Gradle build system [24]: any circular dependency, or any attempt at a reference from :domain to :data or to an application module, is rejected immediately as a compilation error.

## 4.2 Clean Architecture as a Structural Constraint

The system's architecture faithfully follows the principles of Clean Architecture [5], as formulated by Robert C. Martin. The fundamental rule of this approach is the Dependency Rule: source code dependencies always point inwards, towards the layers of higher policy and abstraction. The outer layers (infrastructure, networking libraries, user interface, databases) have full knowledge of the core, but the core remains completely unaware of any outer element.

In the proposed system, this rule is enforced as a structural constraint of the build system. The :domain module has zero external dependencies: it does not import Ktor, it does not import Exposed, nor any other library bound to a specific platform. As a result, its code is compiled independently and identically in three radically different execution environments (JVM, Android, WebAssembly). An Order entity defined in :domain is represented in exactly the same form and with the same meaning on the backend server, on the Android tablet and in the owner's browser.

The repository interfaces are a characteristic application of the principle. The OrderRepository interface is defined inside :domain and specifies the retrieval and storage method signatures that the business logic requires. The specific implementation (ExposedOrderRepository) lives in the :backend module, using the Exposed DSL and the PostgreSQL database. If PostgreSQL were ever replaced by another storage technology, the change would be limited strictly to the :backend level. No domain entity, no service and no route would be affected. Likewise, the choice of HTTP framework (Ktor) remains a detail of the outer infrastructure layer, leaving the core of the system untouched.

## 4.3 Multi-Tenancy — Request Flow Architecture

The system organises data and access rights in a strict hierarchy of five levels: **Platform → Owner → Property → Device → Guest**.

The Platform Admin belongs to the platform schema (public) and has oversight of the whole system. Each owner (Owner) holds an exclusive schema in the database (tenant\_{id}), inside which their properties, devices, products and orders are recorded. The physical kiosk devices do not have a separate schema, but are linked unambiguously to a specific property of the owner. The guest has no account or credentials; their presence and their placement in the correct tenant schema are determined automatically through the authenticated kiosk device they use.

The dynamic routing of every incoming HTTP request to the appropriate schema is achieved through four cooperating mechanisms:

1. **Cryptographic embedding of the Tenant Claim:** When the user or device is authenticated, the JWT access token that is issued includes the claim schemaName (e.g. tenant_a1b2c3 for an owner or for an activated kiosk device, and null for the Platform Admin). This claim is cryptographically signed, ruling out any tampering on the client side.
1. **Verification and extraction through Middleware:** Ktor's JWT authentication plugin verifies the token's signature on every incoming request and extracts the value of schemaName.
1. **Propagation of the TenantContext through Coroutines:** The schema value is stored in a TenantContext instance (an implementation of CoroutineContext.Element), which is propagated automatically through the coroutine scope along the whole asynchronous call chain of the request, without needing to be passed explicitly as a parameter to the methods.
1. **Dynamic schema selection in the database:** The convenience function tenantQuery {} retrieves the TenantContext from the current coroutine scope, verifies that the schema name has the expected form (tenant\_ followed by twelve hexadecimal characters) and executes, as the first statement of the Exposed transaction, the statement SET LOCAL search_path TO {schemaName}. As a consequence, PostgreSQL automatically redirects all the transaction's SQL queries to the correct schema. The SET LOCAL form limits the setting to the duration of the transaction, so that a connection returning to the shared connection pool does not "carry" one tenant's schema into the next request, while the format check rules out inserting arbitrary text into the SQL statement.

Through this architecture, the repositories are not aware of the multi-tenant nature of the system: they execute plain queries of the form SELECT \* FROM products, while the infrastructure ensures that the table name always corresponds to the schema of the specific tenant. If a request tries to access the database without an active TenantContext (e.g. because of an error in the pipeline), execution is stopped immediately with a security exception, ruling out reading or altering another tenant's data.

## 4.4 Port/Adapter Pattern — Integrating External Services

The integration of external services without burdening or binding the business logic is implemented through the Port/Adapter pattern (Hexagonal Architecture) [9]. According to this pattern, the core of the application interacts with the outside world exclusively through ports (port interfaces) defined in the :domain module. Each external service is represented by an interface, while the specific implementations (adapters) live in the :backend module and are injected when the application starts through a dependency injection mechanism.

The system defines six port interfaces:

- PaymentGateway: Port connecting to the card payment acquirer, for approving a transaction and refunding money.
- FoodDeliveryProvider: Port connecting to food ordering platforms (efood, Wolt, Box): verifying the access key, retrieving restaurants, menus and the product catalogue, and submitting and cancelling an order.
- FoodOrderEventListener: A reverse-direction port, through which a platform informs the system of the progress of an order. It corresponds to the notification address (webhook) that a real platform would call.
- NotificationService: Port for sending welcome messages and receipts by email.
- CompanyRegistry: Port for looking up the published details of a business based on the VAT ID.
- Geocoder: Port for address search and for converting coordinates into an address.

The business services (OrderService, PaymentService, OwnerService, etc.) communicate exclusively with the abstractions above, remaining fully isolated from the communication protocols, libraries or data formats of the specific providers.

In this prototype, three ports are implemented by adapters connected to real services and two by emulated adapters (§3.4.1):

- The SmtpEmailAdapter sends real messages over SMTP [33], with the server settings read from the database on every send.
- The GemhCompanyRegistryAdapter calls the GEMI open data API [30].
- The NominatimGeocoderAdapter calls the Nominatim service [31], observing at process level the limit of one request per second set by its usage policy.
- The EmulatedPaymentAdapter decides whether to approve or decline a transaction based on the way the card was read and the result of the PIN check.
- The EmulatedEfoodAdapter, EmulatedWoltAdapter and EmulatedBoxAdapter, which share the EmulatedFoodPlatformAdapter class as a common base, read their restaurants, menus and catalogues from data files and report the progress of every accepted order through the FoodOrderEventListener, whose implementation is provided by the FoodOrderEventHandler service.

The architectural strength of the pattern shows in how easy it is to move to a production environment: to replace the EmulatedPaymentAdapter with the adapter of a real payment acquirer, all that is needed is to write the new class in :backend and change one line in the configureRouting() function, where all the dependencies are assembled. No domain entity, no service and no route needs to be changed. The coexistence of real and emulated adapters behind interfaces of the same form is, moreover, the practical verification of this property within the prototype itself.

The type of each order (type) determines which ports are involved in fulfilling it. An order for the accommodation's own products (IN_ROOM_STORE) is priced from the owner's catalogue and fulfilled by the owner. A food order (FOOD_DELIVERY) is priced from the platform's menu through the FoodDeliveryProvider, is submitted to the platform after the approved payment and is then advanced by the events of the FoodOrderEventListener. This decision is made entirely in the service layer, with no direct dependency on specific third-party implementations.

## 4.5 Design Decisions and Technology Selection

During the architecture design phase, the functional and non-functional characteristics of the available technologies were analysed systematically, evaluating the corresponding trade-offs. The main design decisions are documented in the sections that follow.

### 4.5.1 The Choice of Kotlin as the Single Language

A central design decision of the thesis was to adopt **one and only one programming language for the entire software stack** — from the backend server to the Android kiosk application and the web management environment. This choice follows directly from the need to ensure absolute type consistency in a complex distributed system.

In the classic polyglot approach (polyglot architecture), the backend is usually developed in Java or Go, the Android application in Kotlin, and the web frontend in TypeScript. Although this approach allows the use of specialised tools in each area, it introduces a serious drawback: fragmentation of the code. The domain models, the DTOs and the validation rules are implemented several times in different languages, creating a constant risk of divergence and deserialisation errors that are only revealed at runtime.

With **Kotlin Multiplatform (KMP)** [6], the problem is solved at its root. The shared code developed in the commonMain source set is compiled automatically to JVM bytecode for the backend server, to Android DEX bytecode for the kiosk application, and to WebAssembly for the browser. The entities, the DTOs and the port interfaces are defined once and reused as they are. Any field change is checked immediately by the compiler, providing guaranteed end-to-end type safety.

In addition, Kotlin is a modern language with static type safety, strict null safety and full integration of asynchronous programming through coroutines. It offers performance equivalent to Java on the JVM, is the official language for Android [10][11], and through WebAssembly compilation delivers high execution speed in the browser. In terms of productivity, using a single language drastically simplifies project management: a single build system (Gradle), a shared development environment (IDE) and a unified mental model for the software engineer.

### 4.5.2 Backend Framework: Ktor versus Spring Boot and ASP.NET Core

Three prevailing solutions were considered for implementing the backend: **ASP.NET Core**, **Spring Boot** and **Ktor**.

**ASP.NET Core** was ruled out immediately, since it is based on the C# ecosystem. Choosing it would have undermined the KMP strategy, making it impossible to share the :domain and :data modules on the server.

**Spring Boot** is the most widespread framework in the JVM ecosystem, with support for Kotlin. However, its architecture was designed historically for Java and relies on extensive use of reflection mechanisms and annotations (@Autowired, @Component, @RestController). This approach introduces implicit behaviour and heavy automatic configuration (auto-configuration) [22], hiding the execution flow. In addition, Spring Boot burdens start-up with significant memory consumption and introduces complexity in making its classic execution model work with Kotlin Coroutines.

**Ktor** [16], developed by JetBrains exclusively for Kotlin, offers a radically different philosophy. It is built entirely on Kotlin Coroutines, ensuring non-blocking asynchronous execution for every request. It does not use reflection for routing, but a declarative Kotlin DSL in which every route and parameter is defined explicitly in the code. Its architecture is fully modular: each individual function (authentication, CORS, JSON serialisation, status pages) is installed explicitly as a distinct plugin. This explicitness is completely aligned with the principles of Clean Architecture, since all dependencies and control flows remain visible and verifiable.

The table below compares the three candidate frameworks:

| Comparison Criterion | Ktor | Spring Boot | ASP.NET Core |
|---|---|---|---|
| Language & KMP Compatibility | Native Kotlin (Full KMP sharing) | Java / Kotlin (Partial KMP use) | C# (.NET — Incompatible with KMP) |
| I/O Execution Model | Non-blocking (Kotlin Coroutines) | Thread pool / Project Reactor (WebFlux) | Non-blocking (async / await) |
| Reflection Mechanism | None (Explicit code declaration) | Extensive (Runtime annotations) | Moderate (Dependency injection reflection) |
| Configuration Philosophy | Explicit Modular Plugins via DSL | Convention over Configuration (Auto-config) | Middleware Pipeline Registration |
| Start-up Time & Memory | Extremely low (\< 1 sec, \~50MB RAM) | Higher (5–15 sec, 250MB+ RAM) | Low (1–3 sec, \~80MB RAM) |

### 4.5.3 Multi-Tenancy Strategy: Schema-Per-Tenant

The choice of isolation strategy in the database is a critical architectural decision [8][13]. As analysed in Chapter 2, three approaches were considered:

1. **Shared Schema:** It has minimal operating cost, but data security rests entirely on the correctness of the application's queries (WHERE tenant_id = ?). For a system that handles commercial transactions, the risk of data leakage due to a programming error was judged unacceptable.
1. **Database-per-Tenant:** It offers maximum physical isolation, but introduces a disproportionate administrative burden (hundreds of independent connection pools, backup routines and a separate run of migrations per database), making it uneconomical for the scale of the application.
1. **Schema-per-Tenant:** It is the best balance of isolation and operating efficiency. Each tenant enjoys an independent namespace in PostgreSQL, ensuring structural isolation at the database level. At the same time, the whole platform is served by a single, efficient connection pool (HikariCP), while creating a new tenant requires only running a DDL script inside the same database.

The table below evaluates the three strategies:

| Evaluation Parameter | Shared Schema | Schema-per-Tenant (Chosen) | Database-per-Tenant |
|---|---|---|---|
| Level at Which Isolation Is Enforced | Application Level (WHERE filters) | RDBMS Level (PostgreSQL Schema) | Physical Level (Independent Database) |
| Connection Pool Utilisation | Optimal (Shared pool) | Optimal (Shared pool via HikariCP) | Fragmented (Pool per database) |
| Data Leakage Risk | Real (in case of a bug in the code) | None (ruled out through search_path) | None (full isolation) |
| Backup Management per Tenant | Extremely complex | Simple (pg_dump -n tenant_x) | Simple (Backup of the whole database) |
| Infrastructure Cost | Minimal | Low / Predictable | High (linear growth in resources) |

### 4.5.4 Shared Code: Kotlin Multiplatform

Sharing source code through KMP solves the problem of keeping the interfaces in sync. The code of the :data module (DTOs, requests and responses) is written once and compiled directly to the corresponding targets.

The backend server serialises an OrderResponse object to JSON. The client (Android kiosk or web panel) deserialises the data using exactly the same OrderResponse class. The identity of the class at all levels rules out, by construction, field mismatch errors (type mismatches) or naming inconsistencies, eliminating the need to maintain external code generators or Swagger/OpenAPI schemas for the project's internal synchronisation.

### 4.5.5 Web Panel: Compose Multiplatform (wasmJs)

For developing the owners' web management environment, the traditional choice would have been a JavaScript/TypeScript framework (such as React or Vue). The adoption of **Compose Multiplatform with a WebAssembly (wasmJs) compilation target** [21] was dictated by the aim of absolute architectural uniformity in the UI layer.

WebAssembly (Wasm) [14] is a compact binary instruction format that runs inside the browser's sandbox at close to native speed. The support for WasmGC in modern browsers allowed JetBrains to develop the Kotlin/Wasm target, in which Kotlin code is compiled directly to Wasm without needing a heavy JavaScript runtime. Compose Multiplatform takes advantage of this by drawing the interface's graphical elements directly onto an HTML5 Canvas surface.

The main advantages of this choice can be summarised as follows:

1. **A unified UI programming model:** The developer uses the same declarative Composable functions, the same state management (State) and the same architectural patterns on both Web and Android.
1. **Direct code reuse:** The web panel consumes the Kotlin DTOs of the :data module directly, with no intermediate conversion interfaces.
1. **Independence from the DOM:** Rendering through Canvas eliminates differences in appearance between different browsers and frees the application from the complexity of CSS frameworks.

The main trade-off of this choice lies in the fact that the Kotlin/Wasm ecosystem is newer compared with the established JavaScript tooling, a drawback that is fully offset by the cohesion and code sharing it offers the system.

### 4.5.6 ORM: JetBrains Exposed

Three approaches from the JVM ecosystem were considered for communicating with the relational database: **Hibernate/JPA**, **JOOQ** and **JetBrains Exposed**.

**Hibernate/JPA** is the traditional ORM standard. However, it relies heavily on reflection, annotations and a complex entity lifecycle (managed, detached, transient states). In addition, it has no native support for non-blocking coroutines, requiring special settings so that it does not tie up Ktor's threads.

**JOOQ** generates powerful type-safe code from an existing database schema (a database-first approach). However, it requires an additional code generation step during the build process, and presupposes an active database in order to generate the classes.

**Exposed** [15] follows the opposite, code-first approach through a Kotlin DSL:

- The structure of the tables is defined directly in the code as Kotlin objects (Table objects), making the source code the single source of truth for the structure of the database.
- Queries are written with full type safety (type-safe DSL), where the compiler verifies the column names and data types already at build time.
- It natively supports asynchronous execution through the suspendTransaction function, ideally integrating transactions into the Kotlin Coroutines environment.
- It works without reflection or runtime annotations, keeping things clear and fast to execute.

The table below summarises the comparative evaluation of the ORM tools:

| Evaluation Criterion | JetBrains Exposed (Chosen) | Hibernate / JPA | JOOQ |
|---|---|---|---|
| Schema Definition (Source of Truth) | Code-first via the Kotlin Table DSL | Code-first via Java Annotations | Database-first via Code Generation |
| Integration with Coroutines | Native (suspendTransaction) | Requires external thread bridging | Requires Reactive Extensions / R2DBC |
| Reflection Mechanism | None | Extensive at runtime | None (generated code) |
| Dependence on a Live DB at Build | None | None | Required for generation |
| Ease of Multi-Schema Routing | Direct support for SET search_path | Complex Multi-tenancy configuration | Supported through configuration |

### 4.5.7 Authentication: JWT with Refresh Token Rotation

Session and access management is implemented through **stateless JSON Web Tokens (JWT)** [18] in combination with a **refresh mechanism with rotation (Refresh Token Rotation)** [19].

Regardless of the token type, all user passwords are stored in the database exclusively as cryptographic hashes using the **BCrypt** algorithm with a cost factor of 12 [20]. This factor was chosen so that the computation takes about 250ms on a modern processor, making dictionary or brute-force attacks uneconomical, without burdening the user's login experience.

The architectural choice was examined against classic authentication through stateful server sessions (stateful server sessions):

| Comparison Parameter | Stateless JWT with Token Rotation (Chosen) | Stateful Server Sessions (Cookies) |
|---|---|---|
| Horizontal Scalability | Immediate (No memory sharing between servers) | Requires a distributed session cache (e.g. Redis) |
| Database Load per Request | None (Verification of the cryptographic signature) | A mandatory query to retrieve the session on every call |
| Access Token Exposure Time | Minimal (Short lifetime of 15 minutes) | Depends on the session timeout |
| Access Revocation | Immediate by invalidating the refresh token | Immediate by deleting the session |
| Suitability for Heterogeneous Clients | Ideal (Common HTTP Authorization header) | Vulnerable to CORS and WebAssembly restrictions |

The access token deliberately has a short validity period (15 minutes for users and administrators), drastically limiting the window of threat in the event of interception. The refresh token is valid for 30 days and is used exclusively to issue a new access token without re-entering credentials.

Security is reinforced through the **Refresh Token Rotation** mechanism: every time a refresh token is used for renewal, it is deleted from the database before the new pair of tokens is issued. A refresh token that has already been used therefore cannot be used again (replay attack), since it no longer corresponds to any record. Renewal is also rejected if the owner's account has been deactivated in the meantime, while every owner request for the tenant's data checks that the account is still active, so that deactivation takes effect from the next request and does not wait for the access token to expire. The user themselves can change their password; the change deletes all of their refresh tokens, terminating their other sessions, and returns a new pair of tokens for the current one.

Kiosk devices are a different case. A device does not log in with a password and does not use a refresh token; at activation it receives an access token with the DEVICE role and a validity of one year. So that revoking access does not depend on the token's expiry, the backend checks on every device request that the corresponding record exists and is active; deactivating or disconnecting a device by the owner thus takes effect from its very next request. This check costs one additional query per device request, which was considered an acceptable price for immediate revocation.

In addition, refresh tokens are not stored in plaintext in the database: they are stored exclusively as cryptographic hashes using the **SHA-256** function [29]. If the refresh_tokens table were ever compromised, the recorded hashes would be useless to an attacker, since authentication requires possession of the original, unhashed token.

### 4.5.8 Encrypting the Secrets of the External Services

Integrating external services requires storing secret values: the SMTP server's password, the GEMI access key and the access keys of the delivery platforms of each owner. Unlike user passwords, these values cannot be stored as a hash, since the backend must retrieve them in their original form in order to use them. Two approaches were considered: keeping them in the server's environment variables, and storing them encrypted in the database.

Environment variables are suitable for settings defined once at installation, but they require a server restart for every change and cannot hold values that differ per tenant, such as the platform keys of each owner. Storing them in the database with symmetric encryption using **AES-256 in GCM mode** [34] was therefore chosen, which provides both confidentiality and an integrity check of the ciphertext. For every encryption a new random 12-byte initialisation vector (IV) is generated, which is stored together with the ciphertext. The key is derived from a secret value set as an environment variable (SETTINGS_SECRET) and is never stored in the database, so that a copy of the database is not enough to recover the secrets. The platform's settings are read from the database on every use, so a change from the web panel is applied immediately, without a restart. None of the secrets is returned through the API; the interface is told only whether one has been set.
