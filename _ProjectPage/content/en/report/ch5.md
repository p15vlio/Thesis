The database design translates the entities of the domain layer (Chapters 3 and 4) into a concrete storage structure in PostgreSQL. Each table corresponds to a business entity, each column, as a rule, to a field of the :domain entities, and each constraint or data type reflects a specific decision with a measurable trade-off. This chapter documents that structure at the level of schema, table and column, complementing the architectural description of §4.3 and §4.5.3 with the actual outcome of the implementation.

The database is organised in two levels of schemas: a single public schema for the platform's data, and a tenant\_{id} schema per accommodation owner for their business data. This decision, justified as a strategic choice in §4.5.3, determines the overall structure that follows.

## 5.1 Creating and Preparing Schemas

The mechanism for routing an authenticated request from the JWT's schemaName claim to the execution of SET LOCAL search_path is described in §4.3. This section covers what is not examined there: how a tenant's schema is created, prepared and kept up to date.

Schema creation is coordinated by the SchemaManager object in the :backend module, which executes only DDL (Data Definition Language) operations. The createPlatformTables() method creates the five tables of the public schema when the application starts. The provisionTenant(schemaName) method executes, within one single transaction, all of the steps a tenant schema needs: it verifies the format of the name, issues CREATE SCHEMA IF NOT EXISTS, makes the schema active with SET LOCAL search_path and creates the nine tenant tables with their indexes, foreign keys and checks (CHECK constraints). Executing in a single transaction is essential: because the SET LOCAL statement applies only within the transaction, splitting the steps into several transactions could run part of the DDL statements on another connection of the pool and, consequently, in the wrong schema.

provisionTenant() is executed when each new owner is created, and again for every existing tenant on every start of the application (Figure 5.1). All of its statements are idempotent (IF NOT EXISTS, a check for existence before adding a constraint), so on an up-to-date schema they change nothing. On a schema created by an older version of the code, by contrast, they add the tables, columns, indexes and constraints that were added later and remove columns that no longer exist in the model. This mechanism works as a simple, built-in migration procedure without an external tool, with the limitation that every change must be expressed as an idempotent statement and that no schema version table is kept. The name of each index deliberately matches the automatically generated name that Exposed itself would give in a new schema, so that duplicate indexes are not created.

![Figure 5.1: Creation and update of a tenant schema — provisionTenant() when an owner is created and at every application start.](/media/fig-5-1-tenant-provisioning.png)

## 5.2 The Public Schema — Platform Data

The public schema stores the data that belongs to the platform as a whole and does not fall under any individual tenant. It contains five tables.

### 5.2.1 owners — Accommodation Owners

The owners table is the entry point into the system's hierarchy: each record corresponds to one tenant and links their administrative details to their isolated schema. Each owner is registered as a business, with fields that correspond to the published details of the company registry: company_name and distinctive_title (company name and trade name), vat_id (VAT ID), the registry number, legal_form (legal form), company_activity (main business activity) and the address of the registered seat (street_name, street_no, postal_code, city). The fields that come from the registry but are not essential for the platform to work are optional, so that registration is also possible manually when the registry is not available.

contact_first_name, contact_last_name, contact_email and contact_phone describe the contact person. contact_email serves as the owner's login identifier; it is stored in lower case and has a unique index on the expression lower(contact_email), so that the database rejects a duplicate that differs only in capitalisation even if some part of the code omits normalisation. secondary_contact_name, secondary_contact_phone and secondary_contact_email cover a second contact person, such as an accountant or property manager, while website and notes are optional descriptive fields.

schema_name is the field that links the record to the corresponding schema in the database (tenant\_{id}): it is set when the account is created, never changes afterwards, and is copied as a claim into every JWT issued for the particular owner or for a device belonging to one of their accommodations. password_hash stores the BCrypt hash of the password (justification in §4.5.7), never the password itself. The temporary password (§3.1.1) is not stored anywhere in readable form. is_active allows a tenant to be deactivated without deleting data (§4.5.7); on reactivation, the schema and the order history remain intact.

### 5.2.2 platform_admins — Platform Administrators

The platform_admins table stores the platform administrators' accounts, separately from the owners. Its structure is deliberately minimal: id, email, password_hash, created_at, with a unique index on lower(email). Separating it from the owners table, rather than a single users table with a role field, avoids mixing two roles with completely different scopes of access: the Platform Admin has no tenant schema, and the schemaName claim in their JWT remains null. When they look at an owner's data for support, they set that owner's schema explicitly on every request (§6.3).

### 5.2.3 refresh_tokens — Session Refresh Tokens

The refresh_tokens table lives in the public schema because it serves users of all tenant schemas at once: an owner of tenant_a and an owner of tenant_b both have their own refresh tokens here. user_id refers to the user's identifier regardless of role, while role is copied into the renewed access token and, for owners, triggers the check that the account is still active. token_hash, with an index for fast lookup, stores the SHA-256 hash of the token instead of the plaintext (justification in §4.5.7). schema_name (nullable) is copied from the owner so that it can be included in the new access token on renewal, and stays null for the Platform Admin. expires_at enforces the token's expiry (30 days). Kiosk devices have no records in this table, since they do not use refresh tokens.

### 5.2.4 audit_log — Action Log

The audit_log table records the important administrative actions and refunds (§6.9). actor_id and actor_role identify who performed the action, action describes its nature (e.g. CREATE_PROPERTY, PAYMENT_REFUNDED_ORDER_CANCELLED), and target_type and target_id (nullable) give the type and the object that was affected. schema_name (nullable) lets the Platform Admin filter entries by tenant, while details (nullable) contains free text with additional context, for example the amount and the transaction identifier of a refund. The application only inserts into the table, with no updates or deletions; each record represents an event that happened and not a state that changes.

### 5.2.5 platform_settings — Platform Settings

The platform_settings table stores the settings of the external services that concern the whole platform, namely the SMTP server and the company registry access key. Its structure is a simple key-value pair (key, value, updated_at), with key as the primary key (e.g. smtp.host, smtp.port). The key-value form was chosen because the settings are few, are always read as a group and adding a new setting does not require a change to the table's structure. The secret values (the SMTP password and the registry key) are stored encrypted with AES-256-GCM (§4.5.8).

## 5.3 The Tenant Schema — Accommodation Data

Each owner has their own schema with nine tables, created automatically by the SchemaManager (§5.1): properties, devices, products, product_property_overrides, orders, order_items, payments, food_platform_connections and property_food_platforms. The relationships between the tables are enforced by foreign key constraints inside the schema, with deletion rules that reflect the meaning of each relationship (§5.5). Because all of a tenant's tables are in the same schema, no foreign key extends beyond a single schema.

### 5.3.1 properties — Properties

The properties table stores an owner's physical accommodations, the second level of the hierarchy Owner → Property → Device → Guest. Each property is defined with full address details (street_name, street_no, postal_code, area (optional), city, level, name_on_doorbell, contact_phone). These details are also used as the delivery address when a food order is submitted to a delivery platform, which is why the name on the doorbell is mandatory and the floor is stored (0 when left empty). latitude/longitude (optional) are filled in automatically when the address is entered through the address search (§3.1.2). country and timezone are stored per property (§3.4.3), while timestamps are kept in UTC epoch milliseconds.

The fields wifi_name, wifi_password, check_out_time (format HH:mm) and house_rules are optional and contain the information that the kiosk application shows the guest on the stay information screen. Storing them in the same table reflects the 1:1 relationship with the property. is_active allows deactivation without losing historical data; deactivating a property also deactivates its devices.

### 5.3.2 devices — Kiosk Devices

The devices table stores the Android devices that run the kiosk application at each property. Its design reflects a practical need: the device is registered by the owner in the web panel before it is installed at the property, so several of its fields remain unknown when it is created. property_id is a foreign key to the property, and name is the label the owner gives the device so as to recognise it in the web panel. android_device_id is nullable because it only gets a value when the application is activated on the device. activation_code (eight characters) is generated at creation and deleted at activation, so that it cannot be used a second time; activated_at (nullable) records the moment activation was completed. Disconnecting a device sets android_device_id and activated_at to null, deactivates the device and generates a new activation_code. The full activation flow is described in §3.3.4 and §8.2. last_seen_at (nullable) records the device's last contact: the kiosk application sends a heartbeat request every minute (PATCH /devices/{id}/ping) with its own token, allowing the owner to tell a device that has never been activated from one that is merely offline. is_active is checked on every request from the device (§4.5.7). The is_kiosk_enabled field was intended to state whether the device runs in kiosk mode (§8.3); in the current prototype it is set to false when the device is created and is not used.

### 5.3.3 products — Products

The products table stores an owner's product catalogue, which is shared across all of their properties. category stores the product type (e.g. BEVERAGE, SNACK, PHARMACY) and is used for grouping in the kiosk application. base_price is stored in cents and is subject to a check (CHECK) that keeps it non-negative, image_url is optional, and is_available deactivates a product at all properties. Products imported from a delivery platform's catalogue (§3.1.2) are stored as ordinary records of the table, with no link to the platform of origin, so that the owner can freely change their price and description.

### 5.3.4 product_property_overrides — Availability Overrides per Property

The product_property_overrides table allows an owner with multiple properties to deactivate specific products at specific properties, without affecting the rest of their catalogue. The table consists only of the pair product_id and property_id, which is also its composite primary key: the presence of a row means that the product is deactivated at that property, and its absence that it is available. Both columns are foreign keys with cascading deletion (CASCADE), so that deleting a product or a property automatically removes its overrides. The table carries no price column: a product's price remains uniform across all the properties of the same owner.

### 5.3.5 orders — Orders

The orders table is the central table of the system's business operation. device_id links the order to the device that submitted it: instead of the guest being identified through an account, the identity of the property is inferred from the device that submitted the order. property_id is stored explicitly, even though it follows indirectly from the device, so as to avoid an additional join on every search for orders by property. Both are foreign keys with a RESTRICT rule, so that a property or a device with an order history cannot be permanently deleted, only deactivated. guest_email (nullable) is recorded only if the guest provides it voluntarily for a receipt; an account is never created under any circumstances.

status records the order's state in its lifecycle (PENDING, CONFIRMED, PREPARING, OUT_FOR_DELIVERY, DELIVERED, CANCELLED), with the permitted transition rules enforced in the service layer (§6.5). type distinguishes the orders for the property's own products (IN_ROOM_STORE) from food orders (FOOD_DELIVERY). For the latter, external_platform, restaurant_id and restaurant_name record the platform and the restaurant, external_order_id the identifier the platform returned at submission, delivery_fee the delivery cost and estimated_delivery_at the estimated delivery time as reported by the platform. total_amount is calculated by the backend at creation and stored, while updated_at is updated on every status change; the backend uses it to decide when a submission to a delivery platform is considered failed (§6.5). Indexes on device_id, property_id and status serve the most frequent searches.

### 5.3.6 order_items — Order Lines

The order_items table stores the individual products of each order, implementing a form of snapshot: instead of keeping only the product_id and looking up the product's details each time, product_name and unit_price are copied at the moment the order is created. In this way the order history stays accurate even if the owner later changes a product's name or price, or if a platform changes its menu. In addition, product_id is deliberately not a foreign key: in a food order it refers to a dish on a platform's menu, which does not exist in the system's database. order_id is a foreign key with cascading deletion. subtotal is stored directly, instead of being recalculated in every query, and CHECK constraints ensure a positive quantity and non-negative amounts.

### 5.3.7 payments — Payments

The payments table records the card payment attempts for an order. The relationship with the order is 1:N, since the guest can try again after a decline. entry_mode records the way the card was read (CARD_CONTACTLESS, CARD_CHIP, CARD_MAGSTRIPE), card_last4 (nullable) the last four digits of the card when available, and status the outcome (COMPLETED, FAILED or, after a refund, REFUNDED), with decline_reason explaining a decline. provider_transaction_id stores the identifier of the transaction returned by the payment acquirer and is used in the refund request to the acquirer. A partial unique index on order_id with the condition status = 'COMPLETED' allows many declined attempts but at most one approved payment per order, serving as the database's last safeguard against recording an approved payment twice.

### 5.3.8 food_platform_connections — Connections to Delivery Platforms

The food_platform_connections table records the food delivery platforms that the owner has connected. The primary key is the platform itself (platform), since each owner connects at most once to each platform. api_key stores the owner's access key encrypted with AES-256-GCM (§4.5.8), and connected_at the moment of connection. Placing the table in the tenant schema, instead of public, ensures that each owner's keys are isolated by the same mechanism as the rest of their data.

### 5.3.9 property_food_platforms — Platforms per Property

The property_food_platforms table defines which of the connected platforms are offered at each property. It follows the same minimal design as product_property_overrides: a composite primary key (property_id, platform), where the presence of a row means that the platform is available at the property. property_id is a foreign key with cascading deletion. Disconnecting a platform by the owner also removes the corresponding rows, and is not allowed while it has orders in progress.

## 5.4 Entity-Relationship Diagram

The public schema (Figure 5.2) contains the tables owners, platform_admins and platform_settings, with no direct relationship between them. The tables refresh_tokens and audit_log are linked logically to the users through the fields user_id/role and actor_id/actor_role respectively, without a foreign key constraint, since the associated user may be in either of the two user tables or, in the case of audit_log, may be a device belonging to a tenant schema.

![Figure 5.2: Entities of the public schema — platform data.](/media/fig-5-2-er-public.png)

The tenant schema (Figure 5.3) contains the nine tables described in §5.3, with the following relationships: properties ↔ devices (1:N), properties ↔ orders (1:N), devices ↔ orders (1:N), orders ↔ order_items (1:N), orders ↔ payments (1:N, with at most one approved payment), products ↔ product_property_overrides (1:N), properties ↔ product_property_overrides (1:N) and properties ↔ property_food_platforms (1:N). The food_platform_connections table is not linked by a foreign key to the others; the rows of property_food_platforms refer to the connected platforms through the value of the platform column, with the consistency checked in the service layer.

![Figure 5.3: Entities of the tenant schema — accommodation data.](/media/fig-5-3-er-tenant.png)

## 5.5 Design Decisions

Table 5.1 summarises the fundamental design decisions applied uniformly across all tables.

**Table 5.1: Fundamental design decisions of the database**

| Decision | Choice | Justification |
|---|---|---|
| Primary keys | UUID v4 (varchar(36)) | Identifiers are unique across all tenant schemas. No predictable (enumerable) IDs are exposed through the API. They are generated in the service layer without an extra round-trip to the database. |
| Monetary values | Long (cents) | Avoids the rounding errors of floating-point numbers. Conversion to euros happens only at the presentation level. |
| Timestamps | Long (epoch milliseconds, UTC) | Time-zone-independent storage; conversion to local time happens on the client, avoiding daylight saving time issues. |
| Enumeration values (enum) | varchar | Readable values in the database, with no dependence on the definition order of the Kotlin enum. |
| Password storage | BCrypt hash, cost 12 | Full justification in §4.5.7. |
| Refresh token storage | SHA-256 hash | Full justification in §4.5.7. |
| Storage of external service secrets | AES-256-GCM | The values must be retrieved in their original form; full justification in §4.5.8. |
| Foreign key constraints | Within each tenant schema | RESTRICT where deletion would destroy history (devices, orders, payments), CASCADE for the association tables and the order lines. No foreign key extends beyond a single schema. |
| Value checks | CHECK constraints | Positive quantity, non-negative prices and amounts: the database rejects invalid data even if a code error produces it. |
| Structure updates | Idempotent DDL on every start | Existing tenant schemas automatically incorporate the model's changes (§5.1), without an external migration tool. |
| Column naming | snake_case | A PostgreSQL convention; lower-case names need no quotes (quoted identifiers). |
| Soft deletion (soft delete) | The is_active field | Prevents permanent data loss. Owners are never deleted permanently; properties and devices are permanently deleted only when they have no orders. |

## 5.6 Transactions and Concurrent Access

The structure of the tables ensures the correctness of the data only if it is accompanied by proper transaction management, since many requests are processed at the same time: a kiosk submits a payment, the owner changes the status of the same order and a delivery platform sends a progress event. The system's decisions at this level can be summarised in five points.

First, all connections to PostgreSQL come from a shared HikariCP connection pool with a maximum size of ten connections, which serves all the tenants (§4.5.3). The schema is selected in every transaction as described in §4.3. In addition, every query on the public schema explicitly sets search_path to public, so that a platform query runs correctly even if it is called nested inside a tenant transaction.

Second, nested calls take part in the same transaction. Each repository method runs inside its own tenantQuery, but when a service calls several repositories inside an outer tenantQuery, Exposed reuses the transaction that is already open instead of opening a new one. In this way, creating an order with its lines (§6.5) or recording a payment together with the change of the order's status (§6.6) is atomic: either all the writes complete or none does.

Third, the transaction isolation level is READ COMMITTED, PostgreSQL's default. A stricter level, such as REPEATABLE READ, would reject with a serialisation error every transaction that updates a row that another transaction changed concurrently, and would require a retry mechanism at every point of the code. Instead, the few read-modify-write operations of the system are protected explicitly with the two mechanisms that follow.

Fourth, an order's status changes are made with compare-and-set: the UPDATE statement includes as a condition the status that was read (WHERE id = ? AND status = ?). If another action changed the order in the meantime, the statement affects no row and the service treats it as a conflict (§6.5). This mechanism holds no lock between the read and the write and is suitable for actions that can simply fail and be retried by the user.

Fifth, during payment, where a double execution would lead to a double charge, the order's row is locked with SELECT … FOR UPDATE at the start of the transaction (§6.6). Two concurrent payment attempts for the same order are thus executed one after the other, and the second finds the order already paid. The partial unique index of the payments table (§5.3.7) and the mapping of uniqueness violations to HTTP 409 (§6.3) form the second, independent safeguard. During the verification of §9.3, a second payment attempt for an already paid order was rejected with 409 without reaching the payment acquirer.

The structure described in this chapter corresponds precisely to the current stage of implementation of the system: nine tables per tenant schema and five in the platform schema, with no entity that presupposes functionality outside the current scope of the thesis. The implementation of the business logic that consumes this structure is analysed in Chapter 6.
