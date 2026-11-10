Requirements analysis is the foundational design phase of every software system: before any architectural decision is made and before any code is written, the scope of the functions and the needs of the users must be clarified precisely. In the context of this thesis, the requirements analysis served as the primary basis for shaping the architectural pattern, defining the boundaries of data isolation and designing the individual tables in the database.

The system serves three categories of users with different business needs and levels of access: the infrastructure administrator who oversees multiple tenants, the accommodation owners who want full administrative control of their properties without unnecessary technical burden, and the guests who need immediate service from their place of stay without having to install software or create a personal account. The central goal of the design was to bring together a single, secure and efficient system that covers these heterogeneous requirements.

## 3.1 Functional Requirements

The functional requirements specify the particular services and functions that the system provides to each category of user. Their organisation follows the separation of roles, since access to functions and data is governed by strict Role-Based Access Control (RBAC).

### 3.1.1 Platform Administrator (Platform Admin)

The Platform Admin is the central administrator (operator) of the SaaS infrastructure. They are responsible for overseeing the platform, managing the owners' accounts and the settings of the external services, without being involved in the day-to-day business operation of the individual accommodations.

**Tenant management.** The administrator creates, edits, deactivates and reactivates owner accounts. An owner is never deleted permanently, so that the history of their orders and payments is preserved. When a new owner is registered, the system automatically creates a separate, isolated schema in the database (tenant\_{id}). The owner is registered as a business: the record includes the company name and trade name, the VAT ID, the registry number, the legal form, the business activity, the address of the registered seat and the details of the contact person. When the VAT ID is entered, the system looks the business up in the public company registry [9] and fills in its published details automatically, which the administrator checks before saving. When creation is complete, a temporary password is generated automatically, sent to the owner in a welcome email and shown once to the administrator. If an owner forgets their password, the administrator can reset it: a new temporary password is generated and sent in the same way, and the owner's open sessions can no longer be refreshed (§4.5.7).

**Central platform oversight.** The administrator has access to a summary dashboard that shows overall activity: the number of active and inactive owners, the total number of properties and kiosk devices, and the distribution of the orders of the whole platform by status. From the same screen they can go to the data of any owner (properties, devices, products, orders) for support purposes.

**Action log (Audit Log).** The important administrative actions — such as creating an owner, changing a property's details, deactivating a device, deleting resources and refunds — are recorded in a central table (audit log) in the platform schema (public), to which records are only ever added. Each entry includes the identifier of the acting user (actor), their role, the type of action, the entity that was affected, the relevant tenant schema and the timestamp. The scope of the log is justified in §6.9.

**External service settings.** From the web panel the administrator configures the services that concern the whole platform: the outgoing mail server (SMTP), with the ability to send a test message, and the access key to the company registry's open data API. For the secret values of these settings (the SMTP password and the registry key), the administrator only sees whether they have been set (§3.2.2).

### 3.1.2 Accommodation Owner (Owner)

The owner is the tenant of the platform and manages only their own properties, devices and product catalogue. Their data is isolated from that of the other owners (§3.2.1).

**Property management.** The owner registers and configures their accommodations. Each property includes full address details (street, number, postal code, area, city, floor, name on the doorbell), a contact phone number and optional notes. The address is entered through a search: the owner types part of the address or uses their current location, and the system fills in the individual fields and the geographic coordinates from a public geocoding service [10]. For each property the owner also sets the information shown to the guest (the name and password of the Wi-Fi network, the check-out time, the house rules). The owner can put an accommodation into temporary inactivity without losing its associated settings and history.

**Kiosk device management.** For each accommodation, the owner can register and activate one or more Android tablet devices. Creating a device in the web panel automatically generates a unique activation code and a corresponding QR code, so that the physical device can be activated immediately on site. For each device the owner sees its activation status, the last-contact indicator (lastSeenAt), which the device refreshes every minute, and its order history. This lets them immediately spot a device that has lost its connection. They can deactivate a device, in which case its credential is refused from the very next request, but the kiosk keeps its registration and reactivating it in the web panel is enough for it to resume; or they can disconnect it from the tablet, in which case a new activation code is generated and the kiosk returns to the registration screen for reinstallation.

**Product catalogue management.** The owner decides which products are made available to guests. Each product is described by a name, a detailed description, a category (e.g. snacks, beverages, personal hygiene items), an optional image and a sale price. The catalogue is shared across all of the owner's accommodations; the owner can immediately change the availability of a product overall (isAvailable) or deactivate it only at specific accommodations, which updates what is shown on the corresponding tablets. To build the catalogue faster, the owner can import products from the catalogue of a connected delivery platform.

**Connecting food delivery platforms.** The owner connects the food delivery platforms they work with by entering the access key that each platform has given them. The key is verified by the platform at connection time and stored encrypted. The owner then sets, for each accommodation, which of the connected platforms are offered to its guests. A platform cannot be disconnected while it has orders in progress.

**Order oversight and management.** The owner has access to the incoming orders for each accommodation, with the ability to filter by status. They can see in detail the selected products, the quantities, the price per unit at the time of ordering, the payment attempts and the guest's comments. For orders of the accommodation's own products, they update the progress status through a state machine (CONFIRMED → PREPARING → OUT_FOR_DELIVERY → DELIVERED); confirmation comes from the approved payment. Food orders are advanced by the delivery platform itself, so the owner can only cancel them. Cancellation is allowed from any non-final stage and, if the order has already been paid, the system automatically asks the payment provider to refund the amount.

**Business overview panel.** The owner follows summary statistics: the number of active accommodations, the connected devices and the current distribution of orders by processing stage.

### 3.1.3 Guest

The guest is the end user of the kiosk application installed inside the accommodation. A basic design principle is frictionless use (frictionless experience), with no app to download and no account (§3.2.3).

**Choosing a store.** The initial shopping screen presents the "stores" from which the guest can order: the accommodation's own store, with the owner's products, and the restaurants of the delivery platforms enabled at that particular accommodation, grouped by platform. For each restaurant the screen shows the type of cuisine, the rating, the delivery cost, the estimated delivery time and, where there is one, the minimum order.

**Browsing the catalogue.** The guest browses the available products of the accommodation or the menu of a restaurant, sorted into categories, seeing detailed descriptions and prices. From the accommodation's catalogue only the items available at that particular accommodation are shown.

**Managing the cart and placing the order.** The guest selects quantities, reviews the contents of the cart with an automatic calculation of the final cost (including the delivery cost for food orders) and goes on to place the order without any login procedure. Each cart belongs to one store; if the guest adds an item from another store, the application asks for confirmation and, if the guest agrees, empties the cart. Optionally, the guest can enter an email address to receive a digital receipt.

**Payment on the device.** The guest pays on the device, with their card at the built-in card reader, without being redirected to another application. Charging, approving or declining the transaction and refunding money are the responsibility of the external payment provider; the system passes the request on and records the outcome. A declined transaction does not cancel the order: the guest is told the reason and can try again.

**Live status tracking.** After a successful payment, the device automatically moves to a confirmation and tracking screen, showing in near real time the successive stages of execution of the orders of the current visit through periodic polling. From the same screen the guest can cancel an order themselves, as long as its preparation has not yet started, in which case a refund of the amount is requested automatically. Once preparation has started, only the owner can cancel (§3.1.2), whose contact details are shown on the same screen.

**Stay information.** The guest has access to an information screen with the details of the accommodation, the Wi-Fi network, the check-out time, the house rules, the owner's phone number and the emergency numbers.

## 3.2 Non-Functional Requirements

The non-functional requirements define the quality attributes, the architectural properties and the operational constraints that govern the system.

### 3.2.1 Structural Data Isolation

Data isolation is the most critical non-functional requirement of the system. The data of each owner (catalogues, orders, financial transactions, device details) must remain separate from that of all other tenants.

This requirement is implemented with the schema-per-tenant architecture [6], [13]. Separation through a filter in every application query (e.g. WHERE owner_id = ? clauses) is rejected, for the reasons given in §2.2. Each owner has their own PostgreSQL schema, and the search_path is set automatically for every transaction from a single point in the code (§4.3).

### 3.2.2 Security and Authentication

The system must meet the following security requirements:

1. **User credentials:** Passwords are not stored in plaintext, only as hashes using the BCrypt algorithm [20]. Protecting them in transit requires an encrypted connection (TLS), which the demonstration installation does not have (§9.4).
1. **Session management (Sessions):** Authentication is based on short-lived JSON Web Tokens (JWT) [21] (15 minutes for users), accompanied by random refresh tokens valid for 30 days with rotation (token rotation): each refresh token is accepted only once and is replaced by a new one [22].
1. **Device identity:** Kiosk devices carry a specialised device-identity JWT (role = DEVICE) with the device identifier and the name of the tenant schema embedded in it. The token is issued only upon successful activation of the device and is stored in a secure, encrypted area on the Android tablet. Because its lifetime is long (one year), the backend checks on every request that the device is still active, so that its deactivation by the owner takes effect immediately; the same check also stops the devices of a deactivated owner (§4.5.7).
1. **Secrets of external services:** The access keys of the delivery platforms, the password of the SMTP server and the company registry key are stored in the database encrypted with a symmetric algorithm, using a key that is set in the backend's runtime environment and is not stored in the database. None of these values is ever returned through the API.
1. **Password management and immediate revocation:** Every administrator and owner can change their password, confirming the current one; after the change, the user's other sessions can no longer be refreshed. Deactivating an owner takes effect from their very next request, without waiting for the access token to expire.
1. **Rate limiting:** The public endpoints that accept secrets (login, session refresh, activation code) accept a limited number of requests per IP address, so that mass attempts to find passwords by repeated trial (brute force) are prevented. A corresponding per-user limit applies to address search.
1. **Card data:** Sensitive card data is not sent to the backend; handling it is the job of the card reader and the payment provider. The device sends only the way the card was read, the result of the cardholder verification and, where available, the last four digits of the card.

### 3.2.3 Zero-Friction Guest Interaction

The design of the system mandates the complete absence of any identification requirement for the guest. The guest does not create a user account or enter passwords. The validity of the order request comes from the physical kiosk device itself: the tablet, holding an active device credential (DEVICE token), certifies to the backend the physical presence of the order inside the particular property, relieving the user of any registration procedure.

### 3.2.4 External Service Extensibility (Port/Adapter Extensibility)

The system is required to integrate external services: food ordering platforms, a card payment acquirer, email services, a public company registry and an address geocoding service.

The architectural requirement mandates decoupling the core of the application from the providers' implementations through the Port/Adapter pattern [7]. Every external service is defined as a port interface in the :domain module. The adapters implement these interfaces externally, allowing a switch between emulated and real providers without changes to the system's business logic.

### 3.2.5 Reliability and Kiosk Operation (Kiosk Mode & Availability)

The tablet application must operate as a dedicated service terminal (dedicated kiosk device):

1. **Access restriction (LockTask Mode):** The guest must not be able to leave the application, open the Android settings, use other applications or browse the internet. The device is locked through the Android LockTask API [23], which requires the device to be configured accordingly (§8.3).
1. **Autonomy and recovery:** If the device restarts (e.g. after an unexpected shutdown or a power drop), the application should start automatically (auto-restart) once the operating system has finished booting; the system aims for this through a BroadcastReceiver for the Android system event BOOT_COMPLETED, subject to the restrictions of newer Android versions (§8.3).
1. **Network resilience:** Temporary loss of network connectivity does not lead to a crash of the application, but to a clear message to the user with the ability to reconnect automatically.

### 3.2.6 Data Consistency and Historical Integrity (Data Consistency)

The system must keep the financial data and the order history consistent:

1. **Immutable price snapshot (Price Snapshotting):** Any future change to a product's price or name by the owner must not retroactively affect earlier orders. When each order is recorded, the application stores a denormalised snapshot of the unit price in force and of the product name in the order lines table (order_items).
1. **Transition integrity:** The order's execution flow is controlled by a strict state machine in the backend service layer. Invalid transitions (e.g. moving directly from a cancelled state to a completed one) are rejected immediately, returning a structured HTTP 409 Conflict error. An order is confirmed exclusively by an approved payment; the owner cannot manually confirm an unpaid order.
1. **Consistency of payments:** An order can have at most one approved payment, even when two payment requests for the same order arrive at the same time. Declined attempts are recorded without affecting the order. Every cancellation of a paid order, whether by the owner, by the guest, or because the delivery platform did not accept it, is accompanied by a refund request to the payment provider, which is recorded in the audit log; a failed refund is flagged there as requiring manual action.

## 3.3 User Roles and Use Cases

### 3.3.1 User Roles and Authentication System

The system defines three distinct authenticated software roles and one category of physical end user, as summarised in Table 3.1:

**Table 3.1: System roles, data scope and authentication mechanism**

| System Role | Application / Client | Data Scope | Authentication Mechanism |
|---|---|---|---|
| PLATFORM_ADMIN | owner-web | Platform & Metadata (`public` schema), and any tenant's data for support | JWT Access Token, BCrypt Password Hash |
| OWNER | owner-web | Only the tenant schema (`tenant\_{id}`) | JWT Access Token, BCrypt Password Hash |
| DEVICE | app (Android kiosk) | The accommodation's catalogue, orders & payments | Device-scoped JWT (issued at activation) |
| GUEST (Physical User) | app (Android kiosk) | Browsing and order-placing interface | No account (operates through the `DEVICE` device) |

The guest (GUEST) uses the device that is already connected and authenticated as a DEVICE. Every order request carries the cryptographic signature of the device's token, allowing the backend to identify the accommodation and the corresponding tenant schema unambiguously, without requiring the guest to identify themselves.

### 3.3.2 Use Case 1: Placing an Order as a Guest

**Primary Actor:** Guest (GUEST) through the authenticated device (DEVICE).

**Preconditions:**

- The kiosk device is fully activated and connected to a specific accommodation.
- The device holds an active DEVICE JWT.
- There are available products in the accommodation's catalogue.

**Main Flow (Main Success Scenario):**

1. The guest chooses the accommodation's store and browses the product catalogue on the kiosk screen.
1. They select the desired products and quantities, adding them to the shopping cart.
1. The kiosk dynamically calculates the subtotal of each item and the total cost in euro cents (cents).
1. The guest presses the Checkout button and moves to the payment screen, where they can optionally enter an email address to receive a receipt; the card reader waits for the card.
1. The guest taps their card on the reader (contactless transaction) or inserts it into the chip slot and types the PIN.
1. On the first card event, the kiosk sends an order creation request (POST /orders?propertyId={id}) accompanied by the DEVICE JWT. The request contains the product identifiers and the quantities, not prices.
1. The backend validates the identity of the device and that the accommodation in the request is the device's own, calculates the total amount from the current catalogue prices, inserts a record into the orders table with the initial status PENDING, stores the price snapshots in the order_items table.
1. The kiosk immediately sends a payment request (POST /payments) for this order, with the way the card was read. The backend requests approval from the payment acquirer, records the approved transaction (status = COMPLETED) and changes the order's status from PENDING to CONFIRMED. If an email was given, a receipt is sent.
1. The device shows a successful submission screen and starts periodic polling to track the status (§8.6).
1. The owner sees the order in the web panel and successively updates the status (PREPARING → OUT_FOR_DELIVERY → DELIVERED).
1. The guest's tablet shows each status change at its next periodic check.

**Alternative Flow 1a (Payment Declined):**

- In step 8, the payment acquirer declines the transaction.
- The attempt is recorded as FAILED together with the reason for the decline, while the order stays in the PENDING status.
- The kiosk shows the reason for the decline and the card reader waits for a card again, so that the guest can try again without a new order being created.

**Alternative Flow 1b (Cancellation by the Guest):**

- After step 9, and before the owner starts preparation, the guest chooses to cancel the order from the tracking screen and confirms it.
- The backend changes the status to CANCELLED, returns the amount of the approved payment through the payment acquirer and marks the payment as REFUNDED.

**Alternative Flow 1c (Temporary Loss of Connection During Polling):**

- During step 9, the device temporarily loses its Wi-Fi connection.
- The application keeps the orders of the current visit in memory and continues the periodic polling, retrieving the current status as soon as the network is restored.

**Postconditions:**

- The order and the payment have been recorded safely in the tenant schema.
- The price snapshots have been fixed.
- The order appears in the owner's order list in the web panel.

### 3.3.3 Use Case 2: Creating a New Tenant (Tenant Onboarding)

**Primary Actor:** Platform Administrator (PLATFORM_ADMIN).

**Preconditions:**

- The administrator is logged in to the system with valid administrator credentials.

**Main Flow:**

1. The administrator goes to the owner management screen, opens the registration form and types in the VAT ID of the business.
1. The web panel calls the backend's company lookup service, which queries the company registry's open data API [9] and returns the company name, trade name, registry number, legal form, business activity and the address of the registered seat. These details are filled into the form automatically.
1. The administrator checks the details and fills in the details of the contact person (full name, email, phone).
1. The system validates the required fields (the VAT ID must consist of nine digits) and checks that the contact email, which is used for the owner's login, does not already belong to another owner.
1. The system generates a unique UUID identifier and a safe schema name (tenant\_ followed by twelve hexadecimal characters of the identifier).
1. The backend:
   - Creates the owner's record in the owners table of the public schema, together with the BCrypt hash of a random 12-character password.
   - Dynamically creates the isolated PostgreSQL schema (CREATE SCHEMA tenant\_{id}).
   - Runs the DDL scripts to create all of the tenant tables (properties, devices, products, orders, etc.).
1. The action is recorded in detail in the audit_log table.
1. The backend sends the owner a welcome email with the login address and the temporary password, while the web panel also shows the password once to the administrator. The password is not stored in plaintext and is not shown again.

**Alternative Flow 2a (Business Not Found in the Registry):**

- In step 2, the registry returns no business for the given VAT ID, the service is unavailable, or no access key has been set.
- The web panel informs the administrator, who fills in the company details manually; the flow continues from step 3.

**Alternative Flow 2b (Email Already Exists):**

- In step 4, it is found that the contact email is already used by another owner.
- The request is rejected immediately with an HTTP 409 Conflict error.
- No new schema is created in the database and the administrator is informed.

**Alternative Flow 2c (Email Sending Failure):**

- In step 8, the mail server is not configured or is not available.
- The creation of the owner is not affected; the administrator delivers the temporary password shown in the web panel through another channel.

**Postconditions:**

- The new tenant has a configured and isolated data space, ready for use.

### 3.3.4 Use Case 3: Activating and Connecting a Kiosk Device

**Primary Actor:** Accommodation Owner (OWNER).

**Preconditions:**

- The owner has created at least one accommodation in the system.
- The Android application has been installed on the physical tablet and is running for the first time.

**Main Flow (QR Code Scan):**

1. The owner selects an accommodation in the web panel and requests the creation of a new device, setting a descriptive name (e.g. "Living Room Tablet").
1. The backend generates a unique, cryptographically random eight-character activation code (uppercase Latin letters and digits) and builds a JSON payload that also includes the public address of the API: {"deviceId": "...", "activationCode": "...", "schemaName": "...", "endpoint": "..."}.
1. The backend generates the corresponding QR code as a PNG image (encoded in Base64), which the web panel shows on screen.
1. On the tablet, the kiosk application starts on the registration screen (Registration Screen) and asks for a QR code scan.
1. The tablet scans the code with its camera and decodes the fields deviceId, activationCode, schemaName and endpoint; in this way the device also learns the server's address without manual configuration.
1. The tablet sends an activation request POST /devices/{id}/activate?schemaName={schemaName} without a JWT, passing the activation code and the device's hardware identifier (androidDeviceId).
1. The backend verifies that the device has not been activated before, that the code matches, and that both the accommodation and the owner are active.
1. The backend activates the device in one atomic update, records the androidDeviceId and deletes the activation code, so that it cannot be reused (§6.2).
1. The backend issues a signed DEVICE JWT and returns it in the response.
1. The application securely stores the token, the device identifier and the schema in the device's encrypted DevicePreferences.
1. The tablet preloads the catalogue and the details of the accommodation, enters LockTask mode (kiosk mode) and moves to the idle screen, ready for use.

**Alternative Flow 3a (Manual Entry of the Activation Code):**

- If the tablet's camera is not available, the user does not scan the QR code but fills in the fields of the same screen.
- They type in the server address and the eight-character activation code shown in the web panel below the QR code, and presses Activate Device.
- The tablet calls the public endpoint GET /devices/resolve?code={code}.
- The backend identifies which tenant schema the code belongs to and returns the deviceId and the schemaName.
- The flow continues normally from step 6.

**Alternative Flow 3b (Invalid or Already Used Code):**

- If the code does not correspond to any device, the backend returns HTTP 404 Not Found; if it does not match the device, HTTP 403 Forbidden; if the device has already been activated, or its accommodation or owner has been deactivated, HTTP 409 Conflict. Because the activation is applied atomically, of two simultaneous activations with the same code only one succeeds and the other also receives 409.
- The application rejects the activation, shows the user a message with the reason and stays on the registration screen.

**Postconditions:**

- The Android tablet is connected to the specific accommodation and holds a cryptographically validated device identity (DEVICE role).

### 3.3.5 Use Case 4: Overseeing and Managing Orders as an Owner

**Primary Actor:** Accommodation Owner (OWNER).

**Preconditions:**

- The owner is logged in to the web panel.
- There are recorded orders at the accommodation.

**Main Flow:**

1. The owner selects an accommodation and opens its orders (Orders).
1. The web panel retrieves the list of orders by calling GET /orders?propertyId={id} with the owner's JWT.
1. The owner selects an order and reviews its full details: the products it contains, the quantities, the fixed unit prices, the payment status and the guest's contact details (if provided).
1. For an order of the accommodation's own products, the owner selects the status transition to the next stage (e.g. from CONFIRMED to PREPARING). Transitions happen one at a time, without skipping stages.
1. The web panel sends an update request PATCH /orders/{id}/status.
1. The backend validates that the transition is allowed according to the order state machine, updates the database and returns the updated status.
1. On the kiosk device's next periodic polling call, the guest's tablet shows the new status.

**Alternative Flow 4a (Order Cancellation):**

- The owner finds they cannot fulfil the order and selects the CANCELLED status in the status update dialog.
- The backend allows the transition to CANCELLED provided the order is not in a final state (DELIVERED or CANCELLED). For a food order, the cancellation is also requested from the delivery platform; if the platform refuses, the order is not cancelled and the owner is informed (§6.5).
- If the order has been paid, once the cancellation has been completed the system automatically asks the payment provider, exactly once, to refund the amount, and the refund is recorded in the audit log.
- The status is updated and the guest's tablet shows the cancellation at its next periodic check.

**Alternative Flow 4b (Food Order):**

- In step 4, the order is a food order from a delivery platform.
- The web panel shows the platform and the restaurant, but does not allow the status to be advanced manually, since it is updated by the platform (Use Case 5); the only available action is cancellation.

### 3.3.6 Use Case 5: Ordering Food from a Delivery Platform

**Primary Actor:** Guest (GUEST) through the authenticated device (DEVICE).

**Secondary Actor:** Food delivery platform.

**Preconditions:**

- The owner has connected the platform with their access key and has enabled it for the specific accommodation.
- The kiosk device is activated and holds an active DEVICE JWT.

**Main Flow:**

1. The guest opens the stores screen, which shows the restaurants of the platforms offered at the accommodation, and selects a restaurant.
1. The kiosk retrieves the restaurant's menu through the backend, which gets it from the platform using the owner's access key.
1. The guest adds dishes to the cart. The kiosk shows the restaurant's delivery cost and alerts the guest if the minimum order has not been reached.
1. The guest completes the order and uses their card on the payment screen. The kiosk then sends POST /orders with the type FOOD_DELIVERY, the platform, the restaurant and the identifiers of the dishes with their quantities.
1. The backend checks that the platform is available at the device's accommodation, prices each dish at the platform menu's current price, adds the delivery cost, verifies the minimum order and stores the order in the PENDING status.
1. The kiosk sends the payment request, as in step 8 of Use Case 1, and the order moves to CONFIRMED.
1. Only after the approved payment does the backend submit the order to the platform, together with the accommodation's delivery address (street, floor, name on the doorbell, phone), and records the identifier returned by the platform.
1. The platform informs the backend of the order's progress (PREPARING, OUT_FOR_DELIVERY, DELIVERED) together with the estimated delivery time. The backend applies each update only forwards in the lifecycle, ignoring late or duplicate messages.
1. The guest's tablet shows each new status and the estimated delivery time through the periodic polling.

**Alternative Flow 5a (Minimum Order Not Met):**

- In step 5, the value of the dishes is below the restaurant's minimum order.
- The backend rejects the request with HTTP 400 Bad Request and no order is created.

**Alternative Flow 5b (The Platform Does Not Accept the Order):**

- In step 7, the platform refuses or is unable to register the already paid order.
- The backend cancels the order, asks the payment provider to refund the amount and records the refund in the audit log.
- The kiosk informs the guest that the restaurant could not accept the order and that the amount will be refunded.

**Postconditions:**

- The order is registered both in the owner's tenant schema and on the delivery platform, with the platform's identifier as the common point of reference.
- No order is submitted to the platform before its approved payment.

## 3.4 Assumptions and System Boundaries

A clear record of the boundaries and technical assumptions is necessary to delimit the prototype precisely and to prevent mistaken conclusions during evaluation.

### 3.4.1 External Services — Simulation through Emulated Adapters

The prototype's external integrations fall into two categories. Where free or open access was possible, the system connects to the real service; where access requires a commercial agreement, the service is implemented with an emulated adapter that simulates the behaviour of the real provider.

Three integrations are real:

- **Email:** The owners' welcome messages and the guests' receipts are sent over the SMTP protocol [8] to any server the administrator sets. If no server has been set, sending is skipped without affecting the rest of the operation.
- **Company registry:** Company details are looked up in the registry's public open data API [9], with an access key that is issued after registration and approval.
- **Address geocoding:** Address search and the conversion of coordinates to an address are done through a public geocoding service [10], [11], following its rate-limit and identification requirements [24] (one request per second and an application identifier on every request).

Two integrations are simulated:

- **Food delivery platforms:** Commercial delivery platforms usually have no public API for third parties. Each platform is simulated by a separate adapter with its own set of restaurants, menus and product catalogue. The adapter accepts any access key of sufficient length, accepts orders and, at regular intervals, reports their progress through the same notification mechanism that a real platform would use.
- **Card payment acquirer:** Connecting to a real payment provider requires a commercial contract and certification of the device. The emulated adapter answers in its place: it approves or declines a transaction depending on the way the card was read and completes refunds. The card events come from the real card reader of the kiosk device.

How a simulated adapter can be replaced by a real one is described in §4.4.

### 3.4.2 Inventory Management

Tracking product availability in this prototype is implemented through the boolean field Product.isAvailable, which the owner switches on or off manually, overall or per accommodation. The system does not keep stock quantities; automated stock reduction per order is a future extension and is not applied in the current prototype's flow.

### 3.4.3 Localisation and Geographic Adaptability

The structure of the system's data is not tied to a specific geographic location: each accommodation stores its own address, country and time zone (timezone), while timestamps are stored in UTC. The prototype is nevertheless aimed at the Greek market: owner registration is based on the Greek VAT ID and on the Greek company registry, new accommodations receive the values "Greece" and Europe/Athens, the address search is limited to Greek addresses, and the demonstration data (seed data) concerns Greek cities. Extending to another country would require an adapter for the corresponding national company registry, the ability to choose the country and time zone when an accommodation is registered and a matching setting of the address search, with no change to the database structure.

### 3.4.4 Out of Scope — Future Extensions

The following functions have been assessed and are explicitly defined as outside the scope of this thesis, forming the subject of future development:

- Rider & Store Partner application: Implementing a specialised mobile application for physical couriers and partner local shops, which would require new entities and tables in the tenant schema.
- Smart Minibar / IoT Automation (Smart Cabinet): Connecting the kiosk tablet with physical electronic locks or shelf sensors for the automatic recording of product consumption.
- Dynamic Pricing: Automatic adjustment of catalogue prices based on occupancy, seasonality or time of day.
- AI Recommendation Engine: Personalised recommendations of products and local services based on the guest's preferences.
- Digital Concierge Services: Automated booking of tickets, tours and transport services.
- Mobile Device Security: Mechanisms for detecting operating system tampering (rooting detection) and remote data wiping (remote wipe).
