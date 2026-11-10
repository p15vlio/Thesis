Guest Desk is one Kotlin Multiplatform monorepo. Its basic principle is that dependencies point towards the core and that the core does not depend on frameworks.

## Modules

![Module structure of the monorepo and direction of dependencies](/media/fig-4-1-modules.png)

`:domain` holds the entities, the repository interfaces and the port interfaces, with no framework dependency. `:data` holds the request and response classes that the backend, the web panel and the kiosk app all use. The three applications depend on `:data` and `:domain`, never on each other. The shared modules are compiled for two targets, JVM and WebAssembly. The Android app uses the JVM artifact. See [Chapter 4](/report/ch4).

## One database, one schema per owner

Every owner gets a separate PostgreSQL schema (`tenant_` followed by twelve hexadecimal characters). The platform's own data, such as owners, administrators and the audit log, lives in the `public` schema. A request reaches the right schema in four steps: the JWT carries the `schemaName` claim, Ktor verifies the token, a `TenantContext` is carried with the coroutine, and `tenantQuery { }` runs `SET LOCAL search_path` as the first statement of each transaction. The repositories therefore contain plain queries and do not need to know that the system is multi-tenant.

![Routing of a request to the right tenant schema](/media/fig-4-3b-request-routing.png)

The database has 14 tables: 5 in `public` and 9 per tenant. See [Chapter 5](/report/ch5).

## Ports and adapters

Every external service is a port interface in `:domain`. Three are connected to real services (email, company registry, geocoding) and two are emulated (the payment acquirer and the delivery platforms). The emulated platforms report order progress through the same listener port that a real platform's webhook would use.

![Port and adapter pattern](/media/fig-4-4-ports-adapters.png)

## The order lifecycle

An order is created by the kiosk as `PENDING` and is confirmed once the payment provider approves its payment. After that the owner updates its status step by step, or, for a food order, the delivery platform does. When a paid order is cancelled, the system asks the payment provider to refund the amount and records the refund in the audit log.

![Order state machine](/media/fig-6-2-order-state-machine.png)

Status changes are compare-and-set updates, so that two concurrent changes do not conflict, and the database accepts at most one approved payment per order. See [Chapter 6](/report/ch6).

## Deployment

Docker Compose runs three containers: PostgreSQL, the backend and an nginx container that serves the web panel and proxies `/api`. The kiosk tablet talks to the backend directly.

![Docker Compose topology](/media/fig-9-2-docker-topology.png)
