**Guest Desk** is a multi-tenant platform for digital ordering and services in short-term rental accommodations. A tablet inside the property serves as a service point: there the guest finds the information about the stay, orders and pays, with no app to install and no account. The system consists of a central backend, a management web panel and the kiosk app, all written in Kotlin with a shared data model. Each owner's data is kept in a separate schema of the database.

The project is the practical part of a diploma thesis at the Department of Informatics of the Ionian University. The thesis report is under [Thesis Report](/report), and this page is the quick tour.

![The owner's dashboard in the web panel](/media/web-owner-dashboard.png)

## Who uses it

| Role | Works with | Does |
|---|---|---|
| **Platform administrator** | Web panel | Creates owners (company details are filled in from the public company registry), resets passwords, configures email (SMTP) and the registry key, reads the audit log |
| **Owner** | Web panel | Adds properties and kiosk devices, builds the product catalogue (or imports it from a delivery platform), connects delivery platforms, follows and updates orders |
| **Guest** | Kiosk tablet | Browses the property's store and the restaurants of the connected platforms, completes the order by card, follows its status, reads the stay information |

## What is inside

| Module | Technology | Purpose |
|---|---|---|
| `:backend` | Ktor, Exposed, PostgreSQL | REST API (65 endpoints), multi-tenancy, orders, integrations |
| `:owner-web` | Compose Multiplatform, Kotlin/Wasm | Web panel for administrators and owners |
| `:app` | Jetpack Compose, Android | Kiosk application for the tablet |
| `:domain`, `:data` | Kotlin Multiplatform | Entities, ports and DTOs shared by the three application modules |

All the code is Kotlin, in a single repository: about 22,500 lines in 268 files. Because the entities and DTOs are shared code, a change to a field is checked by the compiler in the backend, the web panel and the kiosk app at the same time.

## Real and emulated integrations

| Real services | Emulated services |
|---|---|
| Email over SMTP (welcome messages, receipts) | The payment provider |
| Company details lookup by VAT number in a public company registry | The food delivery platforms |
| Address search in a public geocoding service | |

Charging, approving and refunding a payment are the payment provider's job; the system only calls it. Each external service sits behind a port interface in the `:domain` module, so replacing an emulated adapter with a real one requires no change to the business logic ([Architecture](/overview/architecture)).

## Further reading

- [Architecture](/overview/architecture): how the modules, the tenants and the ports fit together.
- [Technologies](/overview/technologies): the technologies of the project and their versions.
- [Installation](/gettingStarted/installation): starting the whole system with Docker.
- [Flows](/gettingStarted/flows): the main scenarios, screen by screen.
- [Presentation and demo](/showcase/presentation): the presentation, the videos and the screenshots.
- [Thesis Report](/report): the full text of the thesis.
- [API Reference](/api): the endpoints with their roles, request bodies and responses.
- [Appendix](/appendix): the foreign terms and the acronyms, with their Greek rendering.
