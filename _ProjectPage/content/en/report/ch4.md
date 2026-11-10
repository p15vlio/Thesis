This chapter describes the architecture of the system and the technology choices it rests on: the module structure (§4.1), how Clean Architecture is applied (§4.2), how every request reaches the right tenant (§4.3), how the external services are connected (§4.4) and, finally, the design decisions together with the alternatives that were considered (§4.5).

## 4.1 Architecture Overview and Module Structure

The system lives in a single code repository (monorepo) with five modules, which together hold the logic, the data models and the applications of the platform. Because everything is in one repository, the shared modules (:domain and :data) always have the same version for every module that uses them, and the compiler catches a type mismatch or a change to a DTO immediately as a compile-time error on every platform at once, instead of it surfacing at runtime.

The five modules fall into two levels: the **shared modules** (:domain, :data) and the **application modules** (:backend, :owner-web, :app).

The :domain module is the core of the system. It contains the main business entities (Order, Product, Device, Property), the repository interfaces and the port interfaces for the external services. It depends on no framework, infrastructure library or other module. It is written entirely in Kotlin and compiled for two targets: Java bytecode (a JVM jar), used by the backend and by the Android kiosk application (the Android build converts it to DEX bytecode), and WebAssembly (a wasmJs bundle) for the browser. The same code thus runs in three different environments.

The :data module depends only on :domain and the kotlinx.serialization library. It defines the Data Transfer Objects (DTOs), that is, the requests and responses of the REST API [25], as Kotlin classes marked with the @Serializable annotation. It is compiled for the same two targets (JVM, wasmJs); the JVM artifact is also used by the kiosk application, and the module is the shared communication contract between the server and the clients.

The application modules depend on :data and :domain, never the other way round [26]:

- :backend is a JVM application that implements the REST API with Ktor and talks to PostgreSQL through JetBrains Exposed.
- :owner-web is the management application for the browser, written with Compose Multiplatform and compiled to WebAssembly (wasmJs).
- :app is the property's kiosk application for Android, written with Jetpack Compose.

The direction of the dependencies follows from what each module declares in the Gradle multi-project build [26]: because :domain declares no dependency on :data or on any application module, any reference to them fails at compile time. Figure 4.1 shows the modules and the direction of the dependencies.

![Figure 4.1: Module structure of the monorepo and the direction of dependencies.](/media/fig-4-1-modules.png)

## 4.2 Clean Architecture as a Structural Constraint

The system's architecture follows the principles of Clean Architecture [5], as formulated by Robert C. Martin. Here the Dependency Rule (§2.3) means that the outer layers (infrastructure, networking libraries, user interface, database) depend on the core, while the core does not depend on them.

In the system, this rule is enforced by the build system itself. :domain has no external dependency: it uses neither Ktor nor Exposed nor any other library dependent on a specific platform. An Order entity of :domain has exactly the same form and meaning on the server, on the Android tablet and in the owner's browser.

The repository interfaces are a typical example. OrderRepository is defined in :domain and describes the signatures of the retrieval and storage methods (method signatures) the business logic needs, while its implementation (ExposedOrderRepository) lives in :backend and uses the Exposed DSL and PostgreSQL. If PostgreSQL were replaced by another storage technology, the entities and interfaces of :domain would not change; the changes would stay within :backend. There, mainly the repository implementations would change, but also some services: those that set the boundaries of a transaction themselves call the tenantQuery helper directly, which is built on Exposed (§5.6). In other words, the service layer lives in :backend rather than in the core, a choice that keeps the code simple but couples the services closely to the transaction mechanism. Likewise, the choice of HTTP framework (Ktor) concerns only the routes and does not affect the core. Figure 4.2 shows the layers of the system and the direction of their dependencies.

![Figure 4.2: Applying Clean Architecture — dependencies point towards the core.](/media/fig-4-2-clean-architecture.png)

## 4.3 Multi-Tenancy — Request Flow Architecture

Data and access rights are organised in a five-level hierarchy: **Platform → Owner → Property → Device → Guest**.

The Platform Admin belongs to the platform's schema (public) and oversees the whole system. Each owner has their own schema in the database (tenant\_{id}), which holds their properties, devices, products and orders. Kiosk devices have no schema of their own; each one belongs to a specific property of the owner. The guest has no account or credentials; the tenant their orders belong to follows from the authenticated kiosk device they use. Figure 4.3 shows the hierarchy and how it maps onto the PostgreSQL schemas.

![Figure 4.3: Entity hierarchy and mapping to PostgreSQL schemas.](/media/fig-4-3-hierarchy.png)

Every HTTP request reaches the right schema in four steps:

1. **The tenant inside the token:** When a user or a device is authenticated, the JWT access token that is issued contains the schemaName claim (e.g. tenant_a1b2c3d4e5f6 for an owner or for an activated kiosk device, and null for the Platform Admin). The claim is signed, so the client cannot change it.
1. **Verification in the middleware:** Ktor's JWT authentication plugin verifies the token's signature on every request and reads the schemaName.
1. **Carried by the TenantContext:** The schema is stored in a TenantContext (an implementation of CoroutineContext.Element), which follows the request's coroutine automatically through the whole chain of calls, without being passed from method to method as a parameter.
1. **Schema selection in the database:** The tenantQuery {} helper reads the TenantContext, checks that the schema name has the expected form (tenant\_ followed by twelve hexadecimal characters) and runs SET LOCAL search_path TO {schemaName} as the first statement of the Exposed transaction. All the queries of the transaction then run in the tenant's schema. SET LOCAL applies only to the transaction, so a connection that returns to the shared connection pool does not "carry" one tenant's schema into the next request, while the format check prevents arbitrary text from being injected into the SQL statement.

The repositories therefore do not need to know that the system is multi-tenant: they run plain queries such as SELECT \* FROM products, and the products table is the one of the request's tenant. If a request tries to reach the database without a TenantContext (e.g. because of a bug in the request flow), execution stops with an error, so that no other tenant's data is read or altered. Figure 4.4 summarises how a request is routed to the right tenant schema.

![Figure 4.4: Routing a request to the right tenant schema through the TenantContext.](/media/fig-4-3b-request-routing.png)

## 4.4 The Port/Adapter Pattern — Integrating External Services

External services are connected without affecting the business logic through the Port/Adapter pattern (Hexagonal Architecture) [7]. The core communicates with the outside world only through ports (port interfaces, §2.3) defined in :domain. Each external service corresponds to one interface, while the implementations (adapters) live in :backend and are given to the services through their constructors at startup (manual wiring in the configureRouting() function, without a dependency injection framework).

The system defines six port interfaces:

- PaymentGateway: The connection to the card payment acquirer, which approves a transaction or carries out a refund.
- FoodDeliveryProvider: The connection to the food ordering platforms: verifying the access key, restaurants, menus and the product catalogue, submitting and cancelling an order.
- FoodOrderEventListener: An inbound port (driving port, §2.3), through which a platform informs the system of an order's progress. It corresponds to the notification address (webhook) that a real platform would call.
- NotificationService: Sending welcome messages, password reset messages and receipts by email.
- CompanyRegistry: Looking up a business's published details by VAT ID.
- Geocoder: Searching for addresses and converting coordinates into an address.

The business services (OrderService, PaymentService, OwnerService and others) call only these interfaces and know nothing about each provider's protocols, libraries or data formats.

In the prototype, three ports are implemented by adapters connected to real services and two by emulated adapters (§3.4.1); the sixth, FoodOrderEventListener, is implemented by a backend service:

- SmtpEmailAdapter sends real messages over SMTP [8] and reads the mail server settings from the database on every send.
- The CompanyRegistry adapter calls the company registry's open data API [9].
- The Geocoder adapter calls the public geocoding service [10] and keeps, for the whole process, to the limit of one request per second set by its usage policy [24].
- EmulatedPaymentAdapter answers in place of a real acquirer and approves or declines a transaction based on how the card was read.
- The three emulated delivery platform adapters, with the shared base class EmulatedFoodPlatformAdapter, read their restaurants, menus and catalogues from data files and report the progress of every order they accepted through the FoodOrderEventListener, which the FoodOrderEventHandler service implements.

The benefit of the pattern shows in the move to production: to replace EmulatedPaymentAdapter with the adapter of a real payment acquirer, provided that the acquirer's interface corresponds to the port, it is enough to write the new class in :backend and change one line in the configureRouting() function, where the dependencies are assembled. The domain entities, services and routes do not change. That real and emulated adapters already coexist behind interfaces of the same form shows this property in practice within the prototype itself.

Each order's type determines which ports are used. An order for the property's own products (IN_ROOM_STORE) is priced from the owner's catalogue and fulfilled by the owner. A food order (FOOD_DELIVERY) is priced from the platform's menu through the FoodDeliveryProvider, submitted to the platform after the approved payment and its progress is then updated by the FoodOrderEventListener's events. The decision is made in the service layer, without a direct dependency on specific implementations of external providers. Figure 4.5 shows the six port interfaces together with their real and emulated adapters.

![Figure 4.5: The Port/Adapter pattern — real (green) and emulated (orange) adapters behind the six port interfaces.](/media/fig-4-4-ports-adapters.png)

## 4.5 Design Decisions and Technology Choices

Before implementation, the available technologies were compared for their functional and non-functional characteristics and their trade-offs. The following sections present the main decisions.

### 4.5.1 Choosing Kotlin as the Single Language

The main decision of the thesis was to use **one programming language across all the software**: on the server, in the Android kiosk application and in the web panel. The reason was type consistency in a system with many subsystems that communicate with each other.

In a multi-language approach (polyglot architecture), the backend is usually written in Java or Go, the Android application in Kotlin and the web frontend in TypeScript. Each subsystem uses the most suitable tools of its platform, but the code fragments: the models, DTOs and validation rules are written several times in different languages, with a constant risk of divergence and with deserialisation errors that only show up at runtime.

**Kotlin Multiplatform (KMP)** [4] largely removes this problem. The shared code is compiled for every target of the system (§2.4, §4.1). Entities, DTOs and port interfaces are written once and used as they are. Every change to a field is checked immediately by the compiler across the whole system (end-to-end type safety).

Kotlin is also a modern language with static types, null checks (null safety) and built-in support for asynchronous programming with coroutines. It runs on the JVM alongside Java and is the language Google prefers for Android (Kotlin-first) [27], [28]. In development practice, one language means one build system (Gradle), one development environment (IDE) and a single approach for the whole project.

### 4.5.2 Backend Framework: Ktor versus Spring Boot and ASP.NET Core

Three widely used options were considered for the backend: **ASP.NET Core**, **Spring Boot** and **Ktor**.

**ASP.NET Core** was ruled out from the start because it is based on C#. It would have cancelled the KMP strategy, since :domain and :data could not have been used on the server.

**Spring Boot** is the most widely used JVM framework and supports Kotlin. It was originally designed for Java, however, and makes extensive use of reflection and annotations (@Autowired, @Component, @RestController). It also relies on auto-configuration, which sets up the application according to the libraries it finds on the classpath [29]; in the author's assessment, this implicit behaviour makes the execution flow less visible.

**Ktor** [30], which JetBrains develops specifically for Kotlin, follows a different philosophy. It is built on Kotlin Coroutines, so that, while a request waits on an external service, no server thread is held (non-blocking I/O). It does not use reflection for routing: the routes and their parameters are declared explicitly in a Kotlin DSL. It is also modular: every feature (authentication, CORS, JSON serialisation, status pages) is installed explicitly as a plugin. This explicitness fits the principles of Clean Architecture, since the dependencies and the control flow are visible in the code.

Table 4.1 compares the three frameworks:

**Table 4.1: Comparison of the candidate backend frameworks**

| Comparison Criterion | Ktor | Spring Boot | ASP.NET Core |
|---|---|---|---|
| Language & KMP Compatibility | Kotlin (Full sharing through KMP) | Java / Kotlin (Partial KMP use) | C# (.NET — Incompatible with KMP) |
| I/O Execution Model | Non-blocking (Kotlin Coroutines) | Thread pool / Project Reactor (WebFlux) | Non-blocking (async / await) |
| Reflection Mechanism | Minimal (Explicit code declaration) | Extensive (Runtime annotations) | Moderate (Dependency injection reflection) |
| Configuration Philosophy | Explicit modular plugins via DSL | Convention over configuration (auto-config) | Registration in the middleware pipeline |

### 4.5.3 Multi-Tenancy Strategy: Schema-Per-Tenant

How data is isolated in the database is one of the most important decisions of the architecture [6], [13]. Of the three models in Chapter 2:

1. **Shared Schema:** It has the lowest operating cost, but data security depends on the filter of every query (§2.2); for a system that handles commercial transactions, this risk was judged unacceptable.
1. **Database-per-Tenant:** It gives the greatest physical isolation, but at a disproportionate administrative cost (separate connection pools, backups and migrations per database), which the scale of the application does not justify.
1. **Schema-per-Tenant:** It was judged the best balance of isolation and operating efficiency for the system. Each tenant has its own namespace in PostgreSQL, so its data sits in its own tables and queries need no per-tenant filter. All tenants connect with the same database role, so isolation rests on selecting the right schema, which happens at a single point in the code (§4.3). The whole platform is served by one shared connection pool (HikariCP), and a new tenant is created by running DDL statements in the same database.

Table 4.2 compares the three strategies:

**Table 4.2: Evaluation of the multi-tenancy strategies**

| Evaluation Parameter | Shared Schema | Schema-per-Tenant (Chosen) | Database-per-Tenant |
|---|---|---|---|
| Level at Which Isolation Is Enforced | Application Level (WHERE filters) | Database structure (PostgreSQL Schema), with the schema selected by the application | Physical Level (Independent Database) |
| Connection Pool Use | Optimal (shared pool) | Optimal (shared pool through HikariCP) | Fragmented (Pool per database) |
| Data Leakage Risk | Real (in case of a bug in the code) | Limited (only the schema selection at one point has to be right) | Minimal (physical isolation) |
| Per-Tenant Backup Management | Very complex | Simple (pg_dump -n tenant_x) | Simple (Backup of the whole database) |
| Infrastructure Cost | Minimal | Low / Predictable | High (resources grow with the number of tenants) |

### 4.5.4 Shared Code: Kotlin Multiplatform

Sharing code through KMP addresses the problem of keeping server and clients in sync. The code of :data (DTOs, requests and responses) is written once and compiled for each target.

The server serialises an OrderResponse object to JSON, and the client (Android kiosk or web panel) deserialises it with exactly the same OrderResponse class. Because the class is the same everywhere, errors from mismatched fields (type mismatches) or names are limited by the structure of the code itself, and no code generators or Swagger/OpenAPI schemas are needed to keep the project in sync internally.

### 4.5.5 Web Panel: Compose Multiplatform (wasmJs)

For the owners' web panel, the usual choice would have been a JavaScript/TypeScript framework (such as React or Vue). **Compose Multiplatform with the WebAssembly compilation target (wasmJs)** [31] was chosen so that the interface layer follows the same model across the whole system.

The technical characteristics of WebAssembly [17], the Kotlin/Wasm target and canvas rendering were described in §2.4.

The main advantages of the choice are the following:

1. **A Single UI Programming Model:** The same declarative Composable functions, the same way of managing state (State) and the same architectural patterns are used on the web and on Android.
1. **Direct Code Reuse:** The web panel uses the Kotlin DTOs of :data directly, without intermediate conversions.
1. **Independence from the DOM:** Rendering onto a canvas reduces differences in appearance between browsers and needs no CSS frameworks.

The main drawback is that the Kotlin/Wasm ecosystem is younger than the established JavaScript tools; within this thesis, the consistency and code sharing it offers make up for that.

### 4.5.6 ORM: JetBrains Exposed

Three JVM options were considered for talking to the relational database: **Hibernate/JPA**, **JOOQ** and **JetBrains Exposed**.

**Hibernate/JPA** is the established ORM. It relies, however, on reflection, annotations and a complex entity lifecycle (managed, detached, transient states). In addition, it offers no native integration with Kotlin coroutines.

**JOOQ** generates type-safe code from an existing database schema (database-first). It needs an extra code generation step in the build, however, usually with a running database.

**Exposed** [32] takes the opposite, code-first approach, with a Kotlin DSL:

- Tables are defined in the code as Kotlin objects (Table objects), so the code is the single source for the database structure.
- Queries are written with a type-safe DSL, and the compiler checks column names and their types already at build time.
- It offers the suspendTransaction function, which fits transactions into the Kotlin Coroutines model. Database calls over JDBC remain blocking, as with any tool built on JDBC.
- It does not rely on reflection or runtime annotations to define tables and queries, which keeps the flow of the code clear.

Table 4.3 summarises the comparison:

**Table 4.3: Comparison of the ORM tools**

| Evaluation Criterion | JetBrains Exposed (Chosen) | Hibernate / JPA | JOOQ |
|---|---|---|---|
| Schema Definition (Source of Truth) | Code-first through the Kotlin Table DSL | Code-first through Java Annotations | Database-first through Code Generation |
| Coroutine Integration | Built-in (suspendTransaction) | Needs external thread bridging | Needs Reactive Extensions / R2DBC |
| Reflection Mechanism | Minimal | Extensive at runtime | None (generated code) |
| Live DB Dependency at Build | None | None | Usually required for generation |
| Ease of Multi-Schema Routing | Direct support for SET search_path | Complex multi-tenancy configuration | Supported through configuration |

### 4.5.7 Authentication: JWT with Refresh Token Rotation

Authentication relies on **stateless JSON Web Tokens (JWT)** [21] with **refresh token renewal and rotation (Refresh Token Rotation)** [22].

Independently of the tokens, users' passwords are stored in the database only as hashes with the **BCrypt** algorithm and a cost factor of 12 [20]. The factor was chosen so that the calculation takes about 250 ms (measured on the development computer): enough to make dictionary or brute-force attacks costly, without noticeably slowing down the user's login.

The choice was compared with classic authentication through sessions kept by the server (stateful server sessions), as Table 4.4 shows:

**Table 4.4: Stateless JWT versus stateful server sessions**

| Comparison Parameter | Stateless JWT with Token Rotation (Chosen) | Stateful Server Sessions (Cookies) |
|---|---|---|
| Horizontal Scaling (Scalability) | Simpler (No shared memory between servers) | Needs a distributed session cache (e.g. Redis) |
| Database Load per Request | Small (Signature verification and one or two short queries checking the account or the device) | A mandatory session lookup query on every call |
| Access Token Exposure Window | Short (Short lifetime of 15 minutes) | Depends on the session timeout |
| Revoking Access | Immediate for owners and devices, through the account status check and the deletion of refresh tokens | Immediate by deleting the session |
| Suitability for Different Clients | Good (Shared HTTP Authorization header) | Limitations with CORS and WebAssembly |

The access token deliberately has a short lifetime (15 minutes for users and administrators), so that an intercepted token can be used only for a short period. The refresh token is valid for 30 days and is used only to issue a new access token without entering the password again.

With rotation (**Refresh Token Rotation**), every refresh token used for renewal is deleted from the database before the new pair of tokens is issued. A refresh token that has already been used is therefore rejected if it is sent again (replay attack), since it no longer corresponds to any record. Reuse is not treated as a sign of theft, however: the attempt is simply rejected, without revoking the tokens issued in the meantime. Renewal is also rejected if the owner's account has been deactivated in the meantime, and on every owner request for the tenant's data the backend checks that the account is still active, so that deactivation takes effect from the next request and not when the access token expires. Users can change their own password; the change deletes all of their refresh tokens, so that their other sessions can no longer be refreshed and end at the latest when their access token expires, and returns a new pair of tokens for the current one.

Kiosk devices are a special case. A device has no password and uses no refresh token; when it is activated it receives an access token with the DEVICE role and a lifetime of one year. So that revocation does not depend on the token expiring, the backend checks on every device request that the device's record exists and is active; deactivating or disconnecting a device thus takes effect from its very next request. A device that has been disconnected or is unknown is rejected with 401, so its kiosk returns to the registration screen, while a deactivated device, or a device of a deactivated owner, is rejected with 403, so the kiosk keeps its activation and continues as soon as the device or the owner is reactivated. These checks cost two extra queries per device request, a price considered acceptable for immediate revocation.

Finally, refresh tokens are not stored in plaintext, only as hashes with the **SHA-256** function [33]. If the refresh_tokens table leaked, the hashes would not be enough for an attacker, since renewal needs the token itself and not its hash. Figure 4.6 summarises the authentication flow and the rotation of the refresh token.

![Figure 4.6: Authentication flow — JWT access token and refresh token rotation.](/media/fig-4-5-jwt-flow.png)

### 4.5.8 Encrypting the Secrets of External Services

Connecting to external services requires storing secrets: the SMTP server password, the company registry access key and each owner's delivery platform access keys. Unlike users' passwords, these cannot be stored as hashes, because the backend has to retrieve them as they are in order to use them. Two options were considered: environment variables on the server or encrypted storage in the database.

Environment variables suit settings that are set once at installation, but every change requires restarting the server, and they cannot store values that differ per tenant, such as each owner's platform keys. Storage in the database was therefore chosen, with symmetric encryption using **AES-256 in GCM mode** [34], which protects both the content and the integrity of the encrypted text. Each encryption uses a new random 12-byte initialisation vector (IV), stored together with the encrypted text. The key is derived from a secret value set as an environment variable (SETTINGS_SECRET) and is not stored in the database, so a copy of the database is not enough to recover the secrets. The settings are read from the database on every use, so a change made in the web panel applies immediately, without a restart. No secret is returned through the API; the interface only receives an indication that a value has been set.
