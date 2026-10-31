This thesis focuses on the design and implementation of an integrated software system for the short-term rental market, drawing on modern software engineering methodologies and distributed architectures. Its objective is to build a complete platform that bridges the operational needs of accommodation management with the provision of digital services directly to the guest. Every architectural decision, from the data isolation strategy in the database to the choice of a single programming language across all subsystems, was shaped by specific functional and non-functional requirements. This chapter delimits the problem under examination, presents the academic and practical motivation, summarises the contribution of the thesis, and describes the structure of the chapters that follow.

## 1.1 Problem Description

Short-term home rental has radically transformed the tourism industry. Platforms such as Airbnb, with more than 7.7 million active listings worldwide [1], have created a highly developed digital booking ecosystem: real-time availability checks, instant online payment and an automated check-in process [2]. As a result, the guest's experience before arriving at the accommodation is fully digitised and seamless. By contrast, the situation after arrival shows a significant digital gap.

When the guest is inside the accommodation and wants to order essential goods, a meal or a local service, they face a gap with no built-in digital solution at the level of the accommodation. General-purpose commercial delivery applications do not cover this gap adequately, since these platforms are unaware of the specific space, of the products the host may keep in stock, or of the accommodation's particular settings. Direct communication with the owner by phone or message depends entirely on human availability and cannot scale effectively, while searching for shops in person burdens the guest. In practice, there is no digital service point (kiosk) installed in the space itself [3].

From the owner's side, management is just as fragmented [4]. A manager of multiple accommodations typically uses a property management system (PMS), separate communication channels and possibly external delivery platforms, without a central mechanism for overseeing on-site consumption. Offering additional products or services with a personalised price list requires laborious manual tracking.

At the infrastructure level, the challenge becomes one of security and isolation. A central software provider hosting multiple independent owners through a multi-tenancy architecture must guarantee absolute data isolation. The sensitive commercial data, the orders and the guest details of each owner must remain inaccessible to third parties, even in the event of a failure in the application logic. This requirement makes simple logical separation through application-level queries insufficient and calls for strict structural separation at the database level.

These three levels — the guest's experience, the owner's operational management and the platform's infrastructure — make up the framework of needs that the system developed in this thesis sets out to address.

## 1.2 Motivation and Objectives

### 1.2.1 Academic Motivation

The main academic motivation for choosing this particular system was its combined architectural complexity. Developing such a platform requires several demanding patterns of modern software engineering to coexist: data isolation in a multi-tenancy environment, keeping the business logic independent of the infrastructure and execution environments, and sharing source code across heterogeneous platforms (server, Android, browser) without unnecessary reimplementation.

The choice of Kotlin as a single programming language for the whole system constitutes a central research hypothesis: whether Kotlin Multiplatform (KMP) [6] offers the maturity needed to build a working, distributed system that spans a JVM backend environment, an Android application and a browser environment via WebAssembly at the same time. The thesis approaches this question empirically, through the implementation and evaluation of a complete application.

At the same time, adopting the principles of Clean Architecture [5] and the Port/Adapter pattern is a fundamental design parameter: it allows the system to integrate external payment and ordering services without binding or altering the core of the business logic. This hypothesis is validated in practice through emulated adapters, which simulate communication with third-party services with no impact on the domain layer (§3.4.1, §4.4).

### 1.2.2 Practical Objectives

Alongside the academic motivation, the system was designed as a complete, working software prototype. The practical objectives are organised by user role:

For the **guest**: providing a self-service ordering point inside the accommodation that requires no application installation, no personal account and no familiarity with the local market. From the same screen, the guest orders both the accommodation's own products and meals from the restaurants of the delivery platforms that the owner has enabled, pays by card at the device's card reader, and sees useful stay information such as the Wi-Fi network and the check-out time.

For the **accommodation owner**: providing a single web panel for the centralised management of properties, devices, products and orders, with an automated device activation process via QR code so that installing a new kiosk tablet does not require specialised technical intervention. The owner connects the food delivery platforms they work with, chooses which ones are offered at each accommodation, and enters the address of each property through an address search instead of typing it manually.

For the **platform administrator**: ensuring full oversight of all tenants, with a central audit log that records critical actions in the system, and automating the onboarding of new owners without manual intervention in the database structure. The company details of a new owner are filled in automatically from the General Commercial Registry (GEMI) based on the VAT ID, while the settings of the external services (mail server, GEMI access key) are managed from the same web panel.

## 1.3 Contribution of the Thesis

The contribution of the thesis centres on four specific axes:

**A single language (mono-language full-stack) with Kotlin.** The system is a complete example of a monorepo in which the backend server, the Android kiosk application and the web panel are all developed in Kotlin. The shared modules :domain and :data (with more than 80 classes, enums and interfaces) are compiled simultaneously to three distinct targets: a JVM jar, an Android library and a wasmJs bundle. Any inconsistency in the data structure or in serialisation is detected immediately at compile time (compile-time safety) and not at runtime.

**Structural tenant isolation with schema-per-tenant.** Implementing multi-tenancy with a separate PostgreSQL schema per owner (tenant\_{id}) provides isolation that is enforced directly by the database and not solely by the application code [8]. Through an automated mechanism for propagating the tenant context, the repositories have no awareness of the existence of multi-tenancy and execute plain queries, while the system ensures dynamic routing to the appropriate PostgreSQL schema.

**Automated and secure activation of kiosk devices.** A new Android tablet is integrated into the system by scanning a QR code generated when the device is registered in the web panel. The kiosk application scans the code, authenticates to the backend through a one-time activation token and receives the operating credentials it needs, eliminating the need for manual configuration.

**Isolation of external services through Port/Adapter.** Every external integration is represented by a port interface in the :domain module [9]. Three of them are connected to real services: sending email over SMTP [33], looking up company details through the open data API of GEMI [30], and searching for addresses in the Nominatim service [31] of OpenStreetMap [32]. The food delivery platforms (efood, Wolt, Box) and the card payment acquirer are implemented with emulated adapters, while the card events (contactless transaction, chip insertion, magnetic stripe) come from the card reader of the device on which the kiosk application runs. In this way, the business logic remains fully protected from the quirks and changes of the external providers.

## 1.4 Structure of the Text

- [**Chapter 2 — Theoretical Background and Related Work.**](/report/ch2) Presents the theoretical foundations of the system: an overview of the short-term rental market, a comparison of the multi-tenancy models, the principles of Clean Architecture and Port/Adapter, a technical overview of Kotlin Multiplatform, and related work in the field of digital hospitality services.
- [**Chapter 3 — Requirements Analysis.**](/report/ch3) Documents the functional requirements for the system's main roles and the non-functional requirements (isolation, security, a seamless guest experience (frictionless)), and explicitly delimits the scope of the system.
- [**Chapter 4 — System Architecture.**](/report/ch4) Analyses, through a comparative evaluation of trade-offs, the architectural and technology choices: module structure, application of Clean Architecture, request routing in multi-tenancy, the choice of Ktor over alternative frameworks, the adoption of KMP and the JWT security strategy.
- [**Chapter 5 — Database Design.**](/report/ch5) Analyses the structure of the public and tenant\_{id} schemas, documenting the design decisions for each table, the integrity constraints and the relationships between the entities.
- [**Chapter 6 — Backend Implementation.**](/report/ch6) Covers the implementation of the REST API with Ktor: middleware structure, error handling, the service layer, the order state machine, payments and refunds, and the adapters for the external services (food delivery platforms, GEMI, geocoding, SMTP).
- [**Chapter 7 — Owner Web Panel.**](/report/ch7) Analyses the management application built with Compose Multiplatform (wasmJs): compilation to WebAssembly, state management, navigation and role-based access control between Platform Admin and Owner.
- [**Chapter 8 — Guest Kiosk Application.**](/report/ch8) Presents the Android kiosk application: the activation flow via QR code, the use of the Android LockTask API for kiosk operation, ordering from the accommodation and from food delivery platforms without the need to identify the user, and card payment through the terminal's card reader.
- [**Chapter 9 — System Integration and Demonstration.**](/report/ch9) Presents the combined operation of all subsystems in a single demonstration environment (Docker Compose) and records in detail the limits and constraints of the prototype.
- [**Chapter 10 — Conclusions and Future Work.**](/report/ch10) Assesses the achievement of the initial objectives, summarises the conclusions drawn from using Kotlin Multiplatform and proposes directions for the future extension of the system.
