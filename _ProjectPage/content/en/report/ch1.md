This thesis concerns the design and implementation of Guest Desk, a complete multi-tenant system for short-term rental accommodations. The system combines the central management of the platform and the properties, through a web application (web panel), with digital self-service for the guest, through a tablet (kiosk) inside the property. Its architectural choices, from the way each owner's data is organised in the database to the use of the same programming language in every subsystem, follow from specific functional and non-functional requirements. This chapter presents the problem, the academic motivation and the practical objectives, the contribution of the thesis and the structure of the chapters that follow.

## 1.1 Problem Description

Short-term home rental has transformed the hospitality sector. Booking platforms such as Airbnb, with more than 7.7 million active listings worldwide at the end of 2023 [1], base their operation on internet technologies [2]: the guest checks availability in real time, completes the booking online and often enters the property unaided (self check-in). Up to arrival, the experience is largely digital. Once the guest is inside the property, however, this digital continuity breaks.

When guests need an everyday essential, a meal or a local service during their stay, they usually find no single point of service inside the property. Food delivery apps work independently of the property: they show neither the products the owner offers nor the information about the stay. Contacting the owner directly, by phone or by message, depends on the owner being available and scales poorly when the owner manages many properties, while searching for shops in the area puts the burden on the guest. This creates the need for a self-service point (kiosk) inside the property itself.

The owner faces a similar fragmentation. Digital tourism tools come from many providers that are not connected to one another [3], so whoever manages many properties has no single view of the orders at each one. An owner who wants to offer their own products to guests has to keep track of the orders and payments themselves.

At platform level, serving many independent owners from the same infrastructure (multi-tenancy) makes the separation of data a basic requirement: the orders, payments and guest details of each owner must not be accessible to others. This separation is better reflected in the organisation of the database itself, rather than only in filters that every query has to remember (§2.2).

Guest self-service, centralised management for the owner and the separation of data on the platform are the three sides of the problem that the system sets out to address.

## 1.2 Motivation and Objectives

### 1.2.1 Academic Motivation

The main academic motivation for developing the system was its architectural complexity. The system brings together, in one project, three demanding areas of software engineering: organising data in a multi-tenant environment, decoupling the business logic from external services, and sharing code across heterogeneous runtimes (server, Android, browser) without implementing the same code several times.

On the technology side, the thesis uses Kotlin as the single language of every subsystem, through Kotlin Multiplatform (KMP) [4]. Kotlin is well established on the server and on Android; the newer part is the browser, where the web panel is compiled to WebAssembly. The implementation shows in practice both the benefits of shared code and the current limits of the ecosystem (§10.3).

The design follows the principles of Clean Architecture [5] and the Port/Adapter pattern, so that the core of the business logic stays independent of the details of external providers (§4.4).

### 1.2.2 Practical Objectives

In practical terms, the aim of the thesis was a complete system that works in practice. The objectives are organised by user role:

For the **guest**: a point for ordering and information inside the property that works with no app to install, no account and no need to know the local market. From the same screen the guest orders the property's products, or food from the delivery platforms the owner has enabled, pays by card on the device through an external payment provider, and sees information about the stay, such as the Wi-Fi network and the check-out time.

For the **property owner**: a single web panel for managing properties, devices, products and orders (Chapter 7). A new tablet is activated by scanning a QR code, with no special technical knowledge (§8.2). The owner chooses which delivery platforms are offered at each property and enters each property's address through a search.

For the **platform administrator**: central oversight of the owners, a record of significant actions (audit log), and onboarding of new owners with no manual changes to the database. A new owner's company details are filled in from a public company registry by VAT number, and the settings of the external services are managed from the same web panel.

## 1.3 Contribution of the Thesis

The contribution of the thesis can be summarised in four points:

**One language across the whole system (mono-language full-stack).** The backend (Ktor), the Android kiosk app and the web panel (Compose Multiplatform) are written in Kotlin, in a single repository (monorepo). The shared :domain and :data modules are compiled for two targets: the JVM, used by the backend and the Android app, and wasmJs for the browser (§4.5.4). A mismatch in data structure or serialisation is therefore caught at compile time (compile-time safety) rather than at runtime.

**Data separation with schema-per-tenant.** Each owner has their own PostgreSQL schema (tenant\_{id}) [6], so their data lives in their own tables and queries need no per-owner filter (§4.5.3). The backend uses a single database account and selects the schema centrally for each request (§4.3); the repositories therefore run plain queries with no multi-tenancy logic, which reduces the risk of accidentally mixing data of different owners.

**Kiosk device activation by QR code.** A new tablet is linked to the property by scanning a QR code from the web panel; the one-time activation code it contains is enough for the app to obtain the device credential, with no manual configuration (§8.2).

**External services through the Port/Adapter pattern.** Each external service is defined as an interface (port) in the :domain module and implemented by an adapter in the infrastructure layer [7] (§4.4). Three integrations talk to real services: sending email over SMTP [8], looking up company details in the open-data interface of a public company registry [9], and searching for addresses in a public geocoding service [10], [11]. The food delivery platforms and the card payment provider are emulated, because connecting to them requires a commercial agreement (§3.4.1). The particularities of each provider thus stay in its adapter and do not burden the business logic.

## 1.4 Structure of the Text

- [**Chapter 2 — Theoretical Background and Related Work.**](/report/ch2) Presents the context of short-term rental, the multi-tenancy models, the principles of Clean Architecture and the Port/Adapter pattern, Kotlin Multiplatform and related digital hospitality solutions.
- [**Chapter 3 — Requirements Analysis.**](/report/ch3) Records the functional requirements per role, the non-functional requirements (data separation, security, frictionless guest experience) and the boundary between real and emulated services.
- [**Chapter 4 — System Architecture.**](/report/ch4) Analyses the architectural and technology decisions and their trade-offs: the module structure, the application of Clean Architecture, the routing of requests per tenant, the choice of Ktor, the use of KMP and authentication with JWT.
- [**Chapter 5 — Database Design.**](/report/ch5) Describes the public and tenant\_{id} schemas, the design choices for the tables, the integrity constraints and the relationships between entities.
- [**Chapter 6 — Backend Implementation.**](/report/ch6) Examines the implementation of the REST API in Ktor, the middleware and service layers, the order state machine and the adapters of the external services.
- [**Chapter 7 — Owner Web Panel.**](/report/ch7) Presents the management application in Compose Multiplatform (wasmJs), state management, navigation and the separation of responsibilities between the platform administrator and the owner.
- [**Chapter 8 — Guest Kiosk Application.**](/report/ch8) Describes the tablet's Android app: activation by QR code, locking into kiosk mode through the Android LockTask API, ordering without a user account and paying by card.
- [**Chapter 9 — System Integration and Demonstration.**](/report/ch9) Presents the subsystems working together in a Docker Compose environment, the verification of the end-to-end flow and the known limitations of the prototype.
- [**Chapter 10 — Conclusions and Future Work.**](/report/ch10) Assesses whether the objectives were met, summarises the conclusions from using Kotlin Multiplatform and proposes directions for future extension.
