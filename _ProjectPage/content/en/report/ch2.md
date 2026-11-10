This chapter places the system in its conceptual and technological context. It starts from the short-term rental market and guests' expectations and continues with the four technical areas the design rests on: the data isolation models in multi-tenancy, the principles of Clean Architecture and the Port/Adapter pattern, Kotlin Multiplatform (KMP) for code sharing, and compilation to WebAssembly for the browser. It ends with related work and existing systems in digital hospitality. The discussion stays at the level of concepts and comparisons; the specific choices of the system and their trade-offs are examined in Chapter 4.

## 2.1 The Short-Term Rental Market and Digital Services

Within a decade, short-term home rental evolved from a marginal form of the sharing economy into a significant part of global tourism. Guttentag [2] examines the rise of platforms such as Airbnb through the theory of disruptive innovation: a service that first addressed parts of the market that were poorly served (travellers on a small budget, guests who wanted more space, private owners with unused property) and gradually improved its reliability and quality; it also examines to what extent Airbnb can displace traditional tourist accommodation.

Travellers' expectations changed along with the market. The empirical study by Guttentag et al. on why tourists choose Airbnb distinguishes five groups of guests by motivation (from those who want to save money to those looking for home comforts or authentic local experiences), with Airbnb's practical advantages as the strongest motive [12]. Guests used to the amenities of a hotel (room service, immediate help, a mini-bar) often look for similar conveniences in a self-contained property. Short-term rental, however, runs without permanent staff on site, so such services are hard to offer in the traditional way.

This creates an asymmetry in the guest's digital experience: the stages up to arrival (search, booking, payment, self check-in) are largely digital and seamless (frictionless), while inside the property there is usually no comparable digital infrastructure (§1.1).

The problem is aggravated by the fragmentation of technology solutions in hospitality. Aldebert et al. [3], examining innovative projects presented at the Tourism@ trade fair, record intense innovation centred on information and communication technologies (ICT), from very different actors: start-ups, high-tech SMEs and large multinationals. Because the tools come from such different actors, the tools available to owners usually work in isolation, without communicating with one another. In practice, an owner with several properties combines a property management system (PMS) for calendars and bookings, messaging apps for communication and external ordering platforms. These tools rarely cover offering products and services to the guest during the stay, that is, products and services offered by the property itself, with central control of prices and immediate tracking of orders.

Because the same need arises for many independent owners, a suitable approach is a multi-tenant SaaS platform: a shared core of functions, with each owner's data kept apart from everyone else's.

## 2.2 Multi-Tenant Architecture and Data Isolation Models

A multi-tenant system serves many independent organisations or customers (tenants) from one running instance of the application and a shared infrastructure, keeping each one's data and settings separate. In the single-tenant approach, by contrast, every customer has their own installation of software and infrastructure. Multi-tenancy benefits from sharing computing, network and storage resources and simplifies maintenance and upgrades. Its main technical difficulty is isolation: one tenant's data must not be accessible to another, whether through a bug in the code or a malicious action.

In the system of this thesis, the tenants are the owners of the properties. The platform handles financial records, product catalogues, personal stay data and order history, data of real commercial value; reliable isolation is therefore a basic requirement.

The literature (Krebs et al. [6], Chong et al. [13]) describes three basic models for organising data in multi-tenancy. Each model strikes a different balance between the degree of isolation, the operating cost and the administrative complexity:

1. **Shared Schema:** All tenants share the same database and the same tables. Every table has a column (tenant_id) that shows which tenant a record belongs to, and every query has to filter records by that column. It is the cheapest option, and onboarding a new tenant requires only a new record. Isolation, however, depends entirely on the application being correct: if a query omits the tenant_id filter, it can show another customer's data. Taking backups or restoring data for a single tenant is also difficult.
1. **Schema-per-Tenant:** All tenants share the same database and the same connection pool, but each tenant has its own schema, that is, a separate namespace with the same table structure. A query that does not name a schema runs against the tables of the schema set by the connection's search_path, so omitting a filter is not enough to expose another tenant's records. How strong the isolation is, however, depends on how tenants connect: if each tenant connects with its own database role, the database management system forbids access to the other schemas; if they all share one role, a query can still read another schema's tables if it names that schema explicitly, and selecting the right schema is the application's responsibility. A new tenant is added by automatically running DDL statements that create its schema. The model separates tenants more strongly than a shared schema, at a low infrastructure cost, since one shared connection pool serves the whole system.
1. **Database-per-Tenant:** Each tenant has its own physical or logical database, possibly on a separate server. This model gives the greatest isolation, allows performance tuning per customer and makes compliance with strict privacy regulations easier. The infrastructure cost and the operational load, however, grow in proportion to the number of tenants: there are many connection pools, many backups, and structural updates (migrations) to run on each database separately.

Table 2.1 compares the three models:

**Table 2.1: Comparison of the three data isolation models**

| Isolation Model | Degree of Isolation | Infrastructure Operating Cost | Migration Complexity | Data Leakage Risk |
|---|---|---|---|---|
| Shared Schema | Low (logical, at code level) | Minimal (shared resources) | Low (a single DDL run) | High (an omitted WHERE filter) |
| Schema-per-Tenant | High (separate namespace, stronger with a database role per tenant) | Moderate (shared connection pool) | Moderate (a run per tenant schema) | Low (a wrong schema choice, not a forgotten filter) |
| Database-per-Tenant | Very high (physical separation) | High (separate resources per tenant) | High (multiple databases) | Very low (separate database) |

Whatever the storage model, a multi-tenant system needs a way to carry the tenant's identity along the whole path of a request (tenant context propagation), to prepare each new tenant's data automatically at onboarding, and to manage the platform's shared data.

Figure 2.1 shows the three models. The choice of schema-per-tenant for the system, and the reasons for it, are presented in §4.5.3.

![Figure 2.1: The three isolation models of a multi-tenant system — shared schema, schema-per-tenant, database-per-tenant.](/media/fig-2-1-tenancy-models.png)

## 2.3 Clean Architecture and the Port/Adapter (Hexagonal) Pattern

In the classic layered architecture (n-tier architecture), code dependencies follow the control flow downwards: presentation depends on the business logic, and the business logic on the data layer, which in turn is tied to the specific database and frameworks. The drawback is that the core of the business logic, the most stable and valuable part of a system, ends up depending on implementation details that change often.

Clean Architecture, as formulated by Martin [5], reverses this direction with the Dependency Rule: code dependencies always point inwards, that is, towards the core, where the most general rules of the system reside. The system is organised in concentric layers:

- **Entities:** Hold the general business rules (enterprise-wide business rules).
- **Use Cases:** Coordinate the flow of data to and from the entities and implement the rules of the specific application.
- **Interface Adapters:** Convert data from the form of the business logic to the form required by external systems (controllers, presenters, gateways).
- **Frameworks & Drivers:** The outer layer, with tools such as the database, the web framework and device interfaces.

The inner layers know nothing about the outer ones. When an inner layer needs data or services from an outer one, it defines an interface itself, and the outer layer implements it. The control flow can thus move outwards, while the code dependency stays pointed towards the core.

Hexagonal Architecture, also known as the Ports and Adapters pattern [7], expresses the same principle with an emphasis on the boundaries of the application. The core sits inside a hexagon and communicates with the outside world only through ports, which fall into two categories:

- **Driving / Inbound Ports:** Define the operations the core offers. The corresponding adapters (REST controllers, CLI handlers or interface components) call the methods of the use cases.
- **Driven / Outbound Ports:** Define the services the core needs from external systems (storage, payment gateways, notifications). Here the core calls the interfaces it has defined itself, and the corresponding adapters implement them.

Combining Clean Architecture with Port/Adapter has three main advantages:

1. **Testability:** The core can be tested with unit tests without a database, network calls or external services.
1. **Infrastructure Independence:** The database, the networking framework or the user interface can be upgraded or replaced with minimal changes to the business logic.
1. **Adapter Interchangeability:** The adapter of a real external service and an emulated adapter implement the same port, so the system can be developed and tested before any commercial agreement with external providers.

In the system of this thesis, Port/Adapter is used for the external services (§4.4): the card payment acquirer, the food delivery platforms, email sending, the company registry and the address geocoding service. The broader rule of Clean Architecture governs the dependencies between the modules (§4.2).

## 2.4 Kotlin Multiplatform — Technical Overview and Evaluation

Sharing code across different platforms (code sharing) is a long-standing aim of software engineering. Four main strategies have been used:

1. **Fully Independent Development:** Each platform is implemented separately in its own language (e.g. Swift for iOS, Kotlin for Android, TypeScript for the Web). Performance is usually the best, but the maintenance cost multiplies and the business rules risk diverging.
1. **Runtime-based / Hybrid:** A common runtime hides the differences between platforms, as in hybrid apps that run inside a webview, with known performance problems and an interface that departs from the native one.
1. **Cross-Platform UI Frameworks:** A framework with a single interface codebase for all platforms. Flutter draws the interface with its own graphics engine, bypassing the native widgets, whereas React Native describes the interface in JavaScript and renders it with the platform's native widgets, through an intermediate layer that communicates with the native code [14].
1. **Shared Business Logic with Native Compilation:** The logic and the data models are shared without a common runtime; the shared code is compiled directly into the native form of each platform.

Kotlin Multiplatform (KMP) belongs to the fourth strategy and optionally lets the interface be shared too, through Compose Multiplatform. Comparative studies of cross-platform tools (Nawrocki et al. [14], Punia et al. [15]) examine the trade-offs of each strategy between performance and development cost. Unlike approaches that go through an intermediate bridge (such as the JavaScript bridge of classic React Native), KMP code runs directly on the target platform.

KMP rests on three mechanisms [4]:

1. **Source Sets:** The code is organised hierarchically. commonMain holds platform-independent Kotlin code, while the platform source sets (jvmMain, androidMain, wasmJsMain) hold code for the particularities of each environment.
1. **The expect / actual mechanism:** When the shared code needs functionality that differs per platform, it declares it with the expect keyword, and each platform provides its own actual implementation. The matching happens at compile time, with no cost at runtime.
1. **Multiple Compilation Targets:** The same shared code is compiled by different backends of the Kotlin compiler: by Kotlin/JVM into Java bytecode, which runs on the server and, after the Android toolchain (D8) converts it to DEX bytecode, on the kiosk tablet; and by Kotlin/Wasm into WebAssembly for the browser. The compiler checks types strictly: a change to a shared DTO or domain interface that no longer matches the code using it causes a compilation error.

The official kotlinx.serialization library helps with sharing data. It relies on a compiler plugin rather than on reflection at runtime: shared classes are marked with the @Serializable annotation, and the JSON serialisation and deserialisation code is generated automatically for all targets.

As for maturity, JetBrains declared the core of KMP Stable in November 2023 [16]. Maturity differs by target, however: the JVM and Android targets are mature and proven at large scale, while the WebAssembly target (Kotlin/Wasm) is newer.

WebAssembly (Wasm) is defined as a compact, portable and safe binary instruction format for a stack machine, running at a speed close to that of native code inside the isolated environment (sandbox) of the browser [17]. It was designed at first for languages without automatic memory management (such as C++ and Rust); the WasmGC extension added garbage collection support directly to the browser's engine [4]. Kotlin/Wasm requires a browser that supports WasmGC and is the target Compose Multiplatform uses for the web; at the time of writing, JetBrains classifies it as Beta [4]. Compose Multiplatform draws the interface onto a canvas surface through the Skia graphics library.

The practical evaluation of the approach and the choice of KMP over other solutions are presented in §4.5.1, §4.5.4 and §4.5.5.

## 2.5 Related Systems and Academic Work

The system lies at the intersection of three areas: multi-tenant SaaS platforms, fixed self-service systems (kiosks) inside the property, and the automatic routing of orders in short-term rental. The literature reviewed did not reveal a system that covers all three together. The related work falls into three separate areas:

1. **Interactive In-Room Device Systems in Hospitality:** In hotels, in-room tablets (such as the commercial solutions SuitePad and Crave Interactive) are established practice. At the academic level, Zhu and Morosan [18] studied empirically the factors (such as cognitive absorption, playfulness and security) that lead hotel guests to use interactive mobile devices. Kim and Qu [19] showed that travellers' attitude toward hotel self-service kiosks depends on perceived usefulness, ease of use, compatibility and perceived risk, which agrees with the choice of a kiosk that is simple to use and needs no account. These systems, however, are usually designed for a single hotel (single-tenant logic), connect mainly to hotel PMS and assume floor staff and a reception desk, which short-term rental usually lacks.
1. **Order Aggregation and Logistics Platforms:** The spread of food ordering and courier platforms has drawn attention to order management and route assignment. These systems, however, are aimed at the end consumer. They do not take the property into account and do not let the owner set up their own catalogue of products and services. That is why this thesis treats them as external providers integrated into the system: the owner chooses which are offered at each property, and the guest orders from their restaurants on the same device on which they buy the property's products.
1. **Engineering of Multi-Tenant SaaS Systems:** As seen in §2.2, software engineering research (Krebs et al. [6], Chong et al. [13]) has formulated models for data isolation and resource management in cloud applications. These studies, however, concern general business applications (enterprise SaaS) and do not examine the connection of physical kiosk devices to separate data schemas.

Beyond these three areas, a system aimed at businesses can draw on public data sources instead of asking the user to type information that already exists elsewhere. Some public company registries, the Greek one among them, publish the registered details of businesses (name, legal form, registered seat, status) as open data through an API [9]. There are also global, freely available geographic datasets, created by their own users [11], on top of which geocoding services operate, that is, converting an address into coordinates and back [10]. Using such sources reduces data-entry errors and speeds up the onboarding of owners and properties, at the cost of depending on the availability and terms of use of third-party services.

Overall, the literature on short-term rental [2], [3], [12] focuses mainly on tourism management and economics and describes the guest's experience, without examining questions of software architecture.

The contribution of this thesis therefore lies in the combination: it applies the principles of Clean Architecture, a multi-tenant data model and Kotlin Multiplatform together in a complete system for the needs of short-term rental, where the properties belong to many independent owners.
