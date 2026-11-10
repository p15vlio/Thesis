# :domain

Shared domain layer. The innermost module — no external dependencies, no framework imports.
Every other module depends on this one (directly or transitively).

**Depends on**: nothing
**Used by**: `:data`, `:backend`, `:owner-web`, `:app`

---

## Enums (`domain/enum/`)

| File | Values |
|------|--------|
| `UserRole` | `PLATFORM_ADMIN, OWNER, DEVICE` |
| `OrderStatus` | `PENDING, CONFIRMED, PREPARING, OUT_FOR_DELIVERY, DELIVERED, CANCELLED` |
| `OrderType` | `IN_ROOM_STORE, FOOD_DELIVERY` |
| `ProductCategory` | `BEVERAGE, HOT_DRINK, FOOD, SNACK, ALCOHOL, PERSONAL_CARE, PHARMACY, OTHER` |
| `ExternalPlatform` | `EFOOD, WOLT, BOX` |
| `CardEntryMode` | `CARD_CONTACTLESS, CARD_CHIP, CARD_MAGSTRIPE` |
| `PaymentStatus` | `PENDING, COMPLETED, FAILED, REFUNDED` |

---

## Entities (`domain/entity/`)

Pure Kotlin `data class`. No annotations. IDs are `String` (UUID). Timestamps are `Long` (epoch ms). Money is `Long` (cents).

### Platform-level (`entity/platform/`) — public DB schema, or data from external services
| Entity | Key fields |
|--------|-----------|
| `Owner` | id, companyName, distinctiveTitle, vatId, gemhNumber, legalForm, companyActivity, streetName, streetNo, postalCode, city, contactFirstName, contactLastName, contactEmail, contactPhone, secondary contact (name, phone, email), website, notes, schemaName, isActive, createdAt |
| `AuthRecord` | id, email, passwordHash, role, schemaName, isActive (login lookup for admins and owners) |
| `RefreshTokenRecord` | id, userId, role, tokenHash, schemaName, expiresAt, createdAt |
| `AuditLog` | id, actorId, actorRole, action, targetType, targetId, schemaName, details, createdAt |
| `CompanyRegistryRecord` | vatId, gemhNumber, companyName, distinctiveTitle, legalForm, companyActivity, address fields, website, statusDescription, isActive (from GEMH) |
| `GeocodedAddress` | displayName, address fields, latitude, longitude (from Nominatim) |
| `Restaurant` | id, platform, name, cuisine, rating, deliveryFeeCents, etaMinutes, minOrderCents (as listed by a food delivery platform) |
| `MenuItem` | id, restaurantId, name, description, priceCents, category (as listed by a food delivery platform) |
| `CatalogProduct` | id, platform, name, description, category, priceCents, brand (a platform catalog item an owner can import as a product) |

### Tenant-level (`entity/tenant/`) — tenant_{id} DB schema
| Entity | Key fields |
|--------|-----------|
| `Property` | id, name, streetName, streetNo, postalCode, area, level, nameOnDoorbell, contactPhone, city, country, timezone, notes, latitude, longitude, wifiName, wifiPassword, checkOutTime, houseRules, isActive, createdAt |
| `Device` | id, propertyId, name, androidDeviceId, isActive, isKioskEnabled, lastSeenAt, activationCode, activatedAt |
| `Product` | id, name, description, category, basePrice, imageUrl, isAvailable, createdAt |
| `Order` | id, deviceId, propertyId, guestEmail, status, type, externalPlatform, externalOrderId, restaurantId, restaurantName, deliveryFee, estimatedDeliveryAt, totalAmount, notes, createdAt, updatedAt |
| `OrderItem` | id, orderId, productId, productName, unitPrice, quantity, subtotal |
| `Payment` | id, orderId, entryMode, cardLast4, status, declineReason, amount, providerTransactionId, createdAt |
| `FoodPlatformConnection` | platform, encryptedApiKey, connectedAt |

---

## Repository Interfaces (`domain/repository/`)

All methods are `suspend`. Returns are bare domain types.

- Platform: `OwnerRepository` · `AuthRepository` · `AuditLogRepository` · `PlatformSettingsRepository`
- Tenant: `PropertyRepository` · `DeviceRepository` · `ProductRepository` · `ProductPropertyOverrideRepository` · `OrderRepository` · `OrderItemRepository` · `PaymentRepository` · `FoodPlatformConnectionRepository` · `PropertyFoodPlatformRepository`

---

## Port Interfaces (`domain/port/`)

Contracts that external adapters in `:backend` must implement.

| Interface | Implemented by |
|-----------|---------------|
| `FoodDeliveryProvider` | efood, Wolt and Box adapters (one per platform, emulated) |
| `FoodOrderEventListener` | Callback port: a platform reports order status changes (its webhook) through it |
| `PaymentGateway` | Emulated card acquirer adapter (authorize and refund) |
| `NotificationService` | SMTP email adapter (order receipts, owner welcome and password-reset emails) |
| `CompanyRegistry` | GEMH (Greek business registry) adapter |
| `Geocoder` | Nominatim (OpenStreetMap) address search and reverse geocoding adapter |
