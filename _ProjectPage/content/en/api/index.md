The system's backend has been developed with the Ktor framework and exposes RESTful services in JSON format. Authentication is implemented through JSON Web Tokens (JWT) in the Authorization header of Bearer type. Multi-tenancy operations are executed dynamically under the corresponding tenant schema (TenantContext) through the JetBrains Exposed ORM.

## A.1 User Authentication (/auth)

- `POST /auth/login`: Authenticates a platform user (Platform Admin or Owner). Request body: `{"email": "string", "password": "string"}`. Response (200 OK): `{"accessToken": "string", "refreshToken": "string", "role": "string", "expiresIn": 900}`.
- `POST /auth/refresh`: Renews the session through a rotating refresh token (Refresh Token Rotation). Request body: `{"refreshToken": "string"}`. Response (200 OK): `{"accessToken": "string", "refreshToken": "string", "expiresIn": 900}`.

## A.2 Platform and Owner Management (/owners, /admin)

- `GET /owners`: Retrieves all registered platform owners. Role: PLATFORM_ADMIN. Response (200 OK): Array of OwnerResponse objects.
- `POST /owners`: Registers a new owner and automatically creates a dedicated database schema (Tenant Schema Provisioning). Role: PLATFORM_ADMIN. Request body: `{"companyName": "string", "vatId": "string", "contactEmail": "string", "contactPhone": "string"}`. Response (201 Created): `{"owner": OwnerResponse, "temporaryPassword": "string"}`.
- `GET /owners/{id}`: Retrieves the details of a specific owner by identifier. Role: PLATFORM_ADMIN. Response (200 OK): OwnerResponse.
- `PUT /owners/{id}`: Updates an owner's details and settings. Role: PLATFORM_ADMIN. Request body: UpdateOwnerRequest. Response (200 OK): OwnerResponse.
- `PATCH /owners/{id}/deactivate`: Temporarily suspends an owner's account (Soft Delete). Role: PLATFORM_ADMIN. Response (204 No Content).
- `PATCH /owners/{id}/activate`: Reactivates a suspended owner account. Role: PLATFORM_ADMIN. Response (204 No Content).
- `GET /admin/owners/stats`: Platform summary statistics per owner (number of properties, devices, products). Role: PLATFORM_ADMIN. Response (200 OK): Array of OwnerWithStatsResponse objects.
- `GET /admin/orders/summary`: Summary distribution of active orders across the whole platform. Role: PLATFORM_ADMIN. Response (200 OK): AdminOrderSummaryResponse.

## A.3 Property and Accommodation Management (/properties)

- `GET /properties`: Retrieves all registered properties of the logged-in owner. Role: OWNER, PLATFORM_ADMIN. Response (200 OK): Array of PropertyResponse objects.
- `POST /properties`: Registers a new property in the owner's schema. Role: OWNER, PLATFORM_ADMIN. Request body: `{"name": "string", "streetName": "string", "streetNo": "string", "postalCode": "string", "contactPhone": "string", "fulfillmentModel": "string"}`. Response (201 Created): PropertyResponse.
- `GET /properties/{id}`: Retrieves the details of a specific property. Role: OWNER, PLATFORM_ADMIN, DEVICE. Response (200 OK): PropertyResponse.
- `PUT /properties/{id}`: Updates a property's details and operating parameters. Role: OWNER, PLATFORM_ADMIN. Request body: UpdatePropertyRequest. Response (200 OK): PropertyResponse.
- `PATCH /properties/{id}/deactivate`: Suspends a property's operation. Role: OWNER, PLATFORM_ADMIN. Response (204 No Content).
- `PATCH /properties/{id}/activate`: Reactivates a property. Role: OWNER, PLATFORM_ADMIN. Response (204 No Content).
- `DELETE /properties/{id}`: Permanently deletes a property. Role: OWNER, PLATFORM_ADMIN. Response (204 No Content).

## A.4 Kiosk Device Management (/properties/{propertyId}/devices, /devices)

- `GET /properties/{propertyId}/devices`: Lists the kiosk devices connected to the specific property. Role: OWNER, PLATFORM_ADMIN. Response (200 OK): Array of DeviceResponse objects.
- `POST /properties/{propertyId}/devices`: Registers a new device and generates an activation code (activation code). Role: OWNER, PLATFORM_ADMIN. Request body: `{"name": "string"}`. Response (201 Created): DeviceResponse (with the activationCode field).
- `GET /devices/{id}`: Retrieves a device's details by identifier. Role: OWNER, PLATFORM_ADMIN. Response (200 OK): DeviceResponse.
- `DELETE /devices/{id}`: Deletes a kiosk device from the property. Role: OWNER, PLATFORM_ADMIN. Response (204 No Content).
- `PATCH /devices/{id}/deactivate`: Deactivates a device. Role: OWNER, PLATFORM_ADMIN. Response (204 No Content).
- `PATCH /devices/{id}/reactivate`: Reactivates a deactivated device. Role: OWNER, PLATFORM_ADMIN. Response (204 No Content).
- `POST /devices/{id}/unregister`: Disconnects an activated tablet from the kiosk device. Role: OWNER, PLATFORM_ADMIN. Response (200 OK): DeviceResponse.
- `GET /devices/{id}/orders`: Retrieves the history of orders submitted from the specific device. Role: OWNER, PLATFORM_ADMIN. Response (200 OK): Array of OrderResponse objects.
- `GET /devices/resolve?code={code}`: Public lookup of the tenant schema from an entered activation code (pre-activation lookup). No JWT. Response (200 OK): ResolveDeviceResponse.
- `POST /devices/{id}/activate?schemaName={schemaName}`: Activates a kiosk tablet and issues a long-lived JWT token valid for 1 year. No JWT. Request body: `{"activationCode": "string", "androidDeviceId": "string"}`. Response (200 OK): DeviceTokenResponse.
- `PATCH /devices/{id}/ping?schemaName={schemaName}`: Periodic operation check (heartbeat) and recording of lastSeenAt. No JWT. Response (204 No Content).

## A.5 Product Catalogue and Availability (/products)

- `GET /products`: Retrieves the product catalogue (kiosk devices automatically receive only the products available at their property). Role: OWNER, PLATFORM_ADMIN, DEVICE. Response (200 OK): Array of ProductResponse objects.
- `POST /products`: Creates a new item in the owner's catalogue. Role: OWNER, PLATFORM_ADMIN. Request body: CreateProductRequest. Response (201 Created): ProductResponse.
- `GET /products/{id}`: Shows the details of a specific product. Role: OWNER, PLATFORM_ADMIN, DEVICE. Response (200 OK): ProductResponse.
- `PUT /products/{id}`: Updates a product's details. Role: OWNER, PLATFORM_ADMIN. Request body: UpdateProductRequest. Response (200 OK): ProductResponse.
- `PATCH /products/{id}/availability`: Globally enables or disables a product for the whole accommodation. Role: OWNER, PLATFORM_ADMIN. Request body: `{"isAvailable": boolean}`. Response (200 OK): ProductResponse.
- `PATCH /products/{id}/properties/{propertyId}/availability`: A specific availability setting for a product at a particular property (Product Property Override). Role: OWNER, PLATFORM_ADMIN. Request body: `{"isAvailable": boolean}`. Response (200 OK): ProductPropertyOverrideResponse.
- `DELETE /products/{id}`: Deletes a product from the catalogue. Role: OWNER, PLATFORM_ADMIN. Response (204 No Content).

## A.6 Order Management (/orders, /device/orders)

- `POST /orders?propertyId={propertyId}`: Submits a new order from the kiosk. Role: DEVICE. Request body: `{"type": "string", "fulfillmentModel": "string", "items": [{"productId": "UUID", "quantity": 1}], "notes": "string"}`. Response (201 Created): OrderResponse (status: CONFIRMED).
- `GET /orders?propertyId={propertyId}`: Retrieves a property's orders, with the option of filtering by the status parameter. Role: OWNER, PLATFORM_ADMIN. Response (200 OK): Array of OrderResponse objects.
- `GET /orders/{id}`: Shows the detailed information of an order. Role: OWNER, PLATFORM_ADMIN. Response (200 OK): OrderResponse.
- `PATCH /orders/{id}/status`: Updates an order's status by the owner (PREPARING, OUT_FOR_DELIVERY, DELIVERED, CANCELLED). Role: OWNER, PLATFORM_ADMIN. Request body: `{"status": "string"}`. Response (200 OK): OrderResponse.
- `GET /device/orders/{id}`: Checks an order's status from the kiosk device that submitted it. Role: DEVICE. Response (200 OK): OrderResponse.
- `PATCH /device/orders/{id}/cancel`: Cancels an order by the guest through the kiosk device. Role: DEVICE. Response (200 OK): OrderResponse.

## A.7 Payments and Auxiliary Services (/payments, /dashboard, /audit-logs, /health)

- `POST /payments`: Records a payment transaction for an existing order. Role: DEVICE. Request body: `{"orderId": "UUID", "method": "VIVA_APP2APP | VIVA_CLOUD | REVOLUT_QR", "amount": 0.0}`. Response (201 Created): PaymentResponse.
- `GET /orders/{orderId}/payment`: Retrieves the payment details of a specific order. Role: OWNER. Response (200 OK): PaymentResponse.
- `GET /dashboard/summary`: Summary of the owner's activity (active properties, online devices, today's orders and revenue). Role: OWNER. Response (200 OK): DashboardSummaryResponse.
- `GET /audit-logs`: Retrieves the history of system actions (Audit Trail). Role: OWNER, PLATFORM_ADMIN. Response (200 OK): Array of AuditLogResponse objects.
- `GET /health`: Service health check (Health Check). No authentication. Response (200 OK): `{"status": "UP"}`.
