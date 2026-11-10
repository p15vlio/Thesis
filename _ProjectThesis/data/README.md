# :data — Shared DTOs and Serialization Models

KMP module shared by `:backend`, `:owner-web` and `:app`.

**Depends on**: `:domain` and `kotlinx.serialization`

All types are `@Serializable data class` with no imports from `:domain`.
Enum-typed fields are `String` — mapping to domain enums happens in `:backend`.

---

## Request DTOs — `net.kustax.opendelivery.data.request`

| Class | Purpose |
|---|---|
| `LoginRequest` | Credentials for platform admin or owner login |
| `RefreshTokenRequest` | Refresh an expired access token using a valid refresh token |
| `ChangePasswordRequest` | Signed-in admin or owner replaces their own password (current + new) |
| `CreateOwnerRequest` | Platform admin creates a new owner account (a business: company name, VAT ID, address, contact person) |
| `UpdateOwnerRequest` | Platform admin updates an owner |
| `UpdateGemhSettingsRequest` | Platform admin sets the GEMH API key |
| `UpdateSmtpSettingsRequest` | Platform admin sets the SMTP server settings |
| `SendTestEmailRequest` | Platform admin sends a test email with the stored SMTP settings |
| `CreatePropertyRequest` | Owner creates a new property |
| `UpdatePropertyRequest` | Owner updates a property (full state PUT) |
| `CreateDeviceRequest` | Owner registers a new kiosk device |
| `ActivateDeviceRequest` | Kiosk activates itself with its activation code |
| `CreateProductRequest` | Owner adds a product to the catalog |
| `UpdateProductRequest` | Owner updates a product (full state PUT) |
| `SetProductAvailabilityRequest` | Owner switches a product on or off for one property |
| `ImportProductsRequest` | Owner imports items from a connected food platform's catalog as products |
| `OrderItemRequest` | Single line item inside `CreateOrderRequest` |
| `CreateOrderRequest` | Guest places an order from the kiosk (`platform` + `restaurantId` for food delivery orders) |
| `UpdateOrderStatusRequest` | Owner or platform admin advances an order's status |
| `InitiatePaymentRequest` | Kiosk submits a card payment for an order (entry mode, PIN result, last 4 digits) |
| `ConnectFoodPlatformRequest` | Owner connects a food delivery platform with its API key |
| `SetPropertyFoodPlatformsRequest` | Owner sets which connected platforms a property offers |

---

## Response DTOs — `net.kustax.opendelivery.data.response`

| Class | Purpose |
|---|---|
| `TokenResponse` | Access token + refresh token returned after successful login or token refresh |
| `OwnerResponse` | Owner account details (includes the temporary password once, on creation or password reset) |
| `OwnerWithStatsResponse` | Owner plus property, device and product counts (admin list) |
| `GemhSettingsResponse` | Whether a GEMH API key is stored |
| `SmtpSettingsResponse` | Stored SMTP settings (only whether a password is set, never the password) |
| `CompanyLookupResponse` | Company data found in GEMH for a VAT ID |
| `GeocodedAddressResponse` | Address found by address search or reverse geocoding |
| `PropertyResponse` | Property details |
| `DeviceResponse` | Kiosk device details (activation code and QR while unactivated, token on activation) |
| `DeviceResolveResponse` | Device id and tenant schema for an activation code |
| `ProductResponse` | Product catalog entry, with the properties where it is switched off |
| `CatalogProductResponse` | Item of a food platform's catalog, available for import |
| `ImportProductsResponse` | Result of a catalog import |
| `OrderItemResponse` | Single line item embedded in `OrderResponse` |
| `OrderResponse` | Full order including embedded items list |
| `PaymentResponse` | Payment record |
| `DashboardSummaryResponse` | Owner dashboard figures |
| `AdminOrderSummaryResponse` | Platform-wide order counts by status |
| `AuditLogResponse` | Audit log entry |
| `FoodPlatformResponse` | A food delivery platform and whether the owner has connected it (never the API key) |
| `PropertyFoodPlatformsResponse` | Platforms enabled at a property |
| `FoodRestaurantResponse` | Restaurant offered to a kiosk |
| `FoodMenuItemResponse` | Dish on a restaurant menu |
| `ErrorResponse` | Uniform error body returned by the status-pages plugin |
