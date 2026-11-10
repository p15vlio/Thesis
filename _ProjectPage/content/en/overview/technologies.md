Every library version is declared once, in a shared Gradle version catalogue, so all modules use the same version of each library.

## Tech stack

| Layer | Technology | Version |
|---|---|---|
| Language and shared code | Kotlin, Kotlin Multiplatform | 2.4.21 |
| Serialisation and concurrency | kotlinx.serialization, kotlinx.coroutines | 1.11.0 |
| Build | Gradle (Kotlin DSL), Android Gradle Plugin | 9.8.1, 9.1.0 |
| Backend | Ktor (server and client) | 3.6.0 |
| Database access | JetBrains Exposed, HikariCP, PostgreSQL JDBC driver | 1.5.1, 7.1.0, 42.7.14 |
| Database | PostgreSQL | 16 |
| Security | Ktor Auth JWT, BCrypt (at.favre), AES-GCM from the JDK | 3.6.0, 0.10.2, – |
| Email | Jakarta Mail | 2.0.2 |
| QR codes | ZXing, ZXing Android Embedded | 3.5.4, 4.3.0 |
| Web panel | Compose Multiplatform (Kotlin/Wasm) | 1.12.0 |
| Kiosk app | Jetpack Compose (BOM), Navigation Compose, DataStore, Security Crypto, Coil | 2026.06.01, 2.9.8, 1.2.1, 1.1.0, 2.7.0 |
| Android platform | minSdk / compileSdk | 28 / 36 |
| Logging | Logback | 1.6.5 |
| Runtime | JDK 17 for development, JRE 21 in Docker, nginx | – |

## Why these choices

- **One language.** Kotlin on the server, in the browser (through WebAssembly) and on Android means that entities and DTOs are written once. A change to a field causes a compilation error everywhere at the same time, instead of failing at runtime. See [Chapter 4](/report/ch4).
- **Ktor.** It is built on coroutines, declares routes in plain Kotlin and has no reflection, which keeps the request flow clear. The report compares it with Spring Boot and ASP.NET Core.
- **Exposed.** Tables are Kotlin objects, so the code is the single source of the schema definition, and `SET LOCAL search_path` is straightforward for schema-per-tenant routing.
- **PostgreSQL schemas.** Each tenant has its own tables in the same database, with one connection pool for all. The isolation depends on selecting the right schema in every transaction ([Architecture](/overview/architecture)).
- **JWT with refresh-token rotation.** Stateless access tokens that live for 15 minutes; refresh tokens that live for 30 days, are stored only as SHA-256 hashes and are replaced every time they are used. A kiosk uses a one-year device token that the backend checks against the device record on every request, so deactivating a device takes effect from the next request.
- **AES-256-GCM for stored secrets.** The SMTP password, the company registry key and the platform keys must be read back in clear text, so they are encrypted with a key that is derived from an environment variable and is not stored in the database.
- **Compose Multiplatform for the web panel.** The web panel's interface is written in the same declarative way as the Android app (Jetpack Compose), with no JavaScript framework. The trade-off, a younger ecosystem, is discussed in [Chapter 10](/report/ch10).

## Code size

| Module | Kotlin files | Lines |
|---|---|---|
| `:domain` | 42 | 523 |
| `:data` | 46 | 637 |
| `:backend` | 79 | 5,695 |
| `:owner-web` | 46 | 9,450 |
| `:app` | 55 | 6,186 |
| **Total** | **268** | **22,491** |
