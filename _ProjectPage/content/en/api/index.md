The backend exposes a REST API in JSON under the `/api` prefix: 65 endpoints, 5 of them public. Click an endpoint to see its request body, response and errors; the fields of each type come from the shared DTOs of the code.

| Convention | Value |
|---|---|
| Base URL | `/api`, e.g. `http://localhost:8080/api` |
| Authentication | JWT in the `Authorization: Bearer <token>` header |
| Token lifetimes | access token 15 minutes, refresh token 30 days, kiosk device token 1 year |
| Tenant selection | from the token; the platform administrator, whose token carries no schema, passes the `schemaName` parameter on the tenant endpoints |
| Amounts and time | amounts in integer euro cents; timestamps in epoch milliseconds |
| Strict JSON | an unknown field or a missing required field: 400 Bad Request |
| Errors | `{"code": "string", "message": "string"}`; a missing or invalid JWT: a bare 401 with a `WWW-Authenticate` header |
| Device token | 401 when the device is unknown or disconnected; 403 when the device or its owner is deactivated |
| Rate limits | login, refresh, code resolution and device activation: 20 requests per minute per IP; address search: 30 per minute per user |

```api-toolbar
```

## A.1 User Authentication (/auth)

```api
[
 {
  "method": "POST",
  "path": "/auth/login",
  "roles": [
   "PUBLIC"
  ],
  "summary": "Authenticates a platform user.",
  "rateLimited": true,
  "body": "LoginRequest",
  "response": {
   "status": 200,
   "type": "TokenResponse"
  },
  "notes": [
   "The optional `schemaName` field is not used: the owner's schema comes from their account."
  ]
 },
 {
  "method": "POST",
  "path": "/auth/refresh",
  "roles": [
   "PUBLIC"
  ],
  "summary": "Renews the session through a rotating refresh token (Refresh Token Rotation).",
  "rateLimited": true,
  "body": "RefreshTokenRequest",
  "response": {
   "status": 200,
   "type": "TokenResponse"
  }
 },
 {
  "method": "POST",
  "path": "/auth/change-password",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN"
  ],
  "summary": "Changes the password of the signed-in administrator or owner.",
  "body": "ChangePasswordRequest",
  "response": {
   "status": 200,
   "type": "TokenResponse"
  },
  "notes": [
   "Returns a fresh token pair. The new password must have at least 8 characters and differ from the current one."
  ],
  "errors": [
   {
    "status": 400,
    "when": "Wrong current password (not 401)."
   },
   {
    "status": 403,
    "when": "Request with a device token."
   }
  ]
 }
]
```

## A.2 Platform and Owner Management (/owners, /admin, /company-lookup, /settings)

```api
[
 {
  "method": "GET",
  "path": "/owners",
  "roles": [
   "PLATFORM_ADMIN"
  ],
  "summary": "Retrieves all registered platform owners.",
  "response": {
   "status": 200,
   "type": "OwnerResponse",
   "list": true
  }
 },
 {
  "method": "POST",
  "path": "/owners",
  "roles": [
   "PLATFORM_ADMIN"
  ],
  "summary": "Registers a new owner and automatically creates a dedicated database schema (Tenant Schema Provisioning).",
  "body": "CreateOwnerRequest",
  "response": {
   "status": 201,
   "type": "OwnerResponse"
  },
  "notes": [
   "The response carries the `temporaryPassword` of the new account only in this response."
  ]
 },
 {
  "method": "GET",
  "path": "/owners/{id}",
  "roles": [
   "PLATFORM_ADMIN"
  ],
  "summary": "Retrieves the details of a specific owner by identifier.",
  "response": {
   "status": 200,
   "type": "OwnerResponse"
  }
 },
 {
  "method": "PUT",
  "path": "/owners/{id}",
  "roles": [
   "PLATFORM_ADMIN"
  ],
  "summary": "Updates an owner's details (the same fields as the creation request, without `vatId` and `contactEmail`).",
  "body": "UpdateOwnerRequest",
  "response": {
   "status": 200,
   "type": "OwnerResponse"
  }
 },
 {
  "method": "PATCH",
  "path": "/owners/{id}/deactivate",
  "roles": [
   "PLATFORM_ADMIN"
  ],
  "summary": "Temporarily suspends an owner's account (Soft Delete).",
  "response": {
   "status": 204
  }
 },
 {
  "method": "PATCH",
  "path": "/owners/{id}/activate",
  "roles": [
   "PLATFORM_ADMIN"
  ],
  "summary": "Reactivates a suspended owner account.",
  "response": {
   "status": 204
  }
 },
 {
  "method": "PATCH",
  "path": "/owners/{id}/reset-password",
  "roles": [
   "PLATFORM_ADMIN"
  ],
  "summary": "Generates a new temporary password for an owner.",
  "response": {
   "status": 200,
   "type": "OwnerResponse"
  },
  "notes": [
   "The response carries the `temporaryPassword` field."
  ]
 },
 {
  "method": "GET",
  "path": "/admin/owners/stats",
  "roles": [
   "PLATFORM_ADMIN"
  ],
  "summary": "Platform summary statistics per owner.",
  "response": {
   "status": 200,
   "type": "OwnerWithStatsResponse",
   "list": true
  }
 },
 {
  "method": "GET",
  "path": "/admin/orders/summary",
  "roles": [
   "PLATFORM_ADMIN"
  ],
  "summary": "The number of active orders and the distribution of all the platform's orders by status.",
  "response": {
   "status": 200,
   "type": "AdminOrderSummaryResponse"
  }
 },
 {
  "method": "GET",
  "path": "/company-lookup?vatId={vatId}",
  "roles": [
   "PLATFORM_ADMIN"
  ],
  "summary": "Looks up a company in the company registry by a nine-digit VAT number, to pre-fill the owner form.",
  "response": {
   "status": 200,
   "type": "CompanyLookupResponse"
  },
  "errors": [
   {
    "status": 404,
    "when": "The registry has no such company."
   }
  ]
 },
 {
  "method": "GET",
  "path": "/settings/smtp",
  "roles": [
   "PLATFORM_ADMIN"
  ],
  "summary": "Reads the outgoing-mail settings.",
  "response": {
   "status": 200,
   "type": "SmtpSettingsResponse"
  },
  "notes": [
   "The password is never returned, only `passwordSet`."
  ]
 },
 {
  "method": "PUT",
  "path": "/settings/smtp",
  "roles": [
   "PLATFORM_ADMIN"
  ],
  "summary": "Stores the outgoing-mail settings.",
  "body": "UpdateSmtpSettingsRequest",
  "response": {
   "status": 200,
   "type": "SmtpSettingsResponse"
  },
  "notes": [
   "If `password` is omitted, the stored password is kept."
  ]
 },
 {
  "method": "POST",
  "path": "/settings/smtp/test",
  "roles": [
   "PLATFORM_ADMIN"
  ],
  "summary": "Sends a test email with the stored settings.",
  "body": "SendTestEmailRequest",
  "response": {
   "status": 204
  }
 },
 {
  "method": "GET",
  "path": "/settings/gemh",
  "roles": [
   "PLATFORM_ADMIN"
  ],
  "summary": "Tells whether a company registry API key is stored (the key itself is never returned).",
  "response": {
   "status": 200,
   "type": "GemhSettingsResponse"
  }
 },
 {
  "method": "PUT",
  "path": "/settings/gemh",
  "roles": [
   "PLATFORM_ADMIN"
  ],
  "summary": "Stores the company registry API key (encrypted at rest).",
  "body": "UpdateGemhSettingsRequest",
  "response": {
   "status": 200,
   "type": "GemhSettingsResponse"
  }
 }
]
```

## A.3 Property Management (/properties, /geocode)

```api
[
 {
  "method": "GET",
  "path": "/properties",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN"
  ],
  "summary": "Retrieves all registered properties of the logged-in owner.",
  "response": {
   "status": 200,
   "type": "PropertyResponse",
   "list": true
  }
 },
 {
  "method": "POST",
  "path": "/properties",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN"
  ],
  "summary": "Registers a new property in the owner's schema.",
  "body": "CreatePropertyRequest",
  "response": {
   "status": 201,
   "type": "PropertyResponse"
  },
  "notes": [
   "`checkOutTime` has the form \"HH:mm\"."
  ]
 },
 {
  "method": "GET",
  "path": "/properties/{id}",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN",
   "DEVICE"
  ],
  "summary": "Retrieves the details of a specific property.",
  "response": {
   "status": 200,
   "type": "PropertyResponse"
  },
  "notes": [
   "A device may read only its own property."
  ]
 },
 {
  "method": "PUT",
  "path": "/properties/{id}",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN"
  ],
  "summary": "Updates a property's details; the body has the same fields as the creation request.",
  "body": "UpdatePropertyRequest",
  "response": {
   "status": 200,
   "type": "PropertyResponse"
  }
 },
 {
  "method": "PATCH",
  "path": "/properties/{id}/deactivate",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN"
  ],
  "summary": "Suspends a property's operation.",
  "response": {
   "status": 204
  }
 },
 {
  "method": "PATCH",
  "path": "/properties/{id}/activate",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN"
  ],
  "summary": "Reactivates a property.",
  "response": {
   "status": 204
  }
 },
 {
  "method": "DELETE",
  "path": "/properties/{id}",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN"
  ],
  "summary": "Permanently deletes a property.",
  "response": {
   "status": 204
  },
  "errors": [
   {
    "status": 409,
    "when": "The property has orders or devices."
   }
  ]
 },
 {
  "method": "GET",
  "path": "/geocode/search?q={text}",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN"
  ],
  "summary": "Address search through the public geocoding service.",
  "rateLimited": true,
  "response": {
   "status": 200,
   "type": "GeocodedAddressResponse",
   "list": true
  }
 },
 {
  "method": "GET",
  "path": "/geocode/reverse?lat={lat}&lon={lon}",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN"
  ],
  "summary": "Finds the address of a map position.",
  "rateLimited": true,
  "response": {
   "status": 200,
   "type": "GeocodedAddressResponse"
  }
 }
]
```

## A.4 Kiosk Device Management (/properties/{propertyId}/devices, /devices)

```api
[
 {
  "method": "GET",
  "path": "/properties/{propertyId}/devices",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN"
  ],
  "summary": "Lists the kiosk devices connected to the specific property.",
  "response": {
   "status": 200,
   "type": "DeviceResponse",
   "list": true
  }
 },
 {
  "method": "POST",
  "path": "/properties/{propertyId}/devices",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN"
  ],
  "summary": "Registers a new device and generates an activation code with its QR code.",
  "body": "CreateDeviceRequest",
  "response": {
   "status": 201,
   "type": "DeviceResponse"
  },
  "notes": [
   "The response carries the `activationCode` and `qrCodeBase64` fields."
  ]
 },
 {
  "method": "GET",
  "path": "/devices/{id}",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN"
  ],
  "summary": "Retrieves a device's details by identifier.",
  "response": {
   "status": 200,
   "type": "DeviceResponse"
  }
 },
 {
  "method": "DELETE",
  "path": "/devices/{id}",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN"
  ],
  "summary": "Deletes a kiosk device from the property.",
  "response": {
   "status": 204
  },
  "errors": [
   {
    "status": 409,
    "when": "The device has orders."
   }
  ]
 },
 {
  "method": "PATCH",
  "path": "/devices/{id}/deactivate",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN"
  ],
  "summary": "Deactivates a device.",
  "response": {
   "status": 204
  }
 },
 {
  "method": "PATCH",
  "path": "/devices/{id}/reactivate",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN"
  ],
  "summary": "Reactivates a deactivated device.",
  "response": {
   "status": 204
  }
 },
 {
  "method": "POST",
  "path": "/devices/{id}/unregister",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN"
  ],
  "summary": "Disconnects an activated tablet from the kiosk device, so that it can be activated again.",
  "response": {
   "status": 200,
   "type": "DeviceResponse"
  }
 },
 {
  "method": "GET",
  "path": "/devices/{id}/orders",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN"
  ],
  "summary": "Retrieves the history of orders submitted from the specific device.",
  "response": {
   "status": 200,
   "type": "OrderResponse",
   "list": true
  }
 },
 {
  "method": "GET",
  "path": "/devices/resolve?code={code}",
  "roles": [
   "PUBLIC"
  ],
  "summary": "Lookup of the device and tenant schema from an entered activation code (pre-activation lookup).",
  "rateLimited": true,
  "response": {
   "status": 200,
   "type": "DeviceResolveResponse"
  }
 },
 {
  "method": "POST",
  "path": "/devices/{id}/activate?schemaName={schemaName}",
  "roles": [
   "PUBLIC"
  ],
  "summary": "Activates a kiosk tablet and issues a long-lived JWT valid for one year.",
  "rateLimited": true,
  "body": "ActivateDeviceRequest",
  "response": {
   "status": 200,
   "type": "DeviceResponse"
  },
  "notes": [
   "The response carries the `token` field."
  ],
  "errors": [
   {
    "status": 400,
    "when": "`schemaName` is missing or invalid."
   },
   {
    "status": 403,
    "when": "Wrong activation code."
   },
   {
    "status": 404,
    "when": "Unknown device."
   },
   {
    "status": 409,
    "when": "The device is already activated (also for the second of two simultaneous activations) or its property or owner is deactivated."
   }
  ]
 },
 {
  "method": "PATCH",
  "path": "/devices/{id}/ping",
  "roles": [
   "DEVICE"
  ],
  "summary": "Periodic operation check (heartbeat) and recording of `lastSeenAt`.",
  "response": {
   "status": 204
  },
  "notes": [
   "A device can report only for itself."
  ],
  "errors": [
   {
    "status": 401,
    "when": "The device has been disconnected."
   },
   {
    "status": 403,
    "when": "The device is deactivated."
   }
  ]
 }
]
```

## A.5 Product Catalogue and Availability (/products, /food-platforms)

```api
[
 {
  "method": "GET",
  "path": "/products",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN",
   "DEVICE"
  ],
  "summary": "Retrieves the product catalogue.",
  "response": {
   "status": 200,
   "type": "ProductResponse",
   "list": true
  },
  "notes": [
   "Kiosk devices automatically receive only the products available at their property."
  ]
 },
 {
  "method": "POST",
  "path": "/products",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN"
  ],
  "summary": "Creates a new item in the owner's catalogue.",
  "body": "CreateProductRequest",
  "response": {
   "status": 201,
   "type": "ProductResponse"
  }
 },
 {
  "method": "GET",
  "path": "/products/{id}",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN",
   "DEVICE"
  ],
  "summary": "Shows the details of a specific product.",
  "response": {
   "status": 200,
   "type": "ProductResponse"
  }
 },
 {
  "method": "PUT",
  "path": "/products/{id}",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN"
  ],
  "summary": "Updates a product's details.",
  "body": "UpdateProductRequest",
  "response": {
   "status": 200,
   "type": "ProductResponse"
  }
 },
 {
  "method": "PATCH",
  "path": "/products/{id}/availability",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN"
  ],
  "summary": "Globally enables or disables a product for all of the owner's properties.",
  "body": "UpdateProductRequest",
  "response": {
   "status": 200,
   "type": "ProductResponse"
  },
  "notes": [
   "The body is the same as that of the update, and only `isAvailable` is applied."
  ]
 },
 {
  "method": "PATCH",
  "path": "/products/{id}/properties/{propertyId}/availability",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN"
  ],
  "summary": "A specific availability setting for a product at a particular property (Product Property Override).",
  "body": "SetProductAvailabilityRequest",
  "response": {
   "status": 200,
   "type": "ProductResponse"
  },
  "notes": [
   "The `disabledPropertyIds` field of the response lists the properties where the product is off."
  ]
 },
 {
  "method": "DELETE",
  "path": "/products/{id}",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN"
  ],
  "summary": "Deletes a product from the catalogue.",
  "response": {
   "status": 204
  }
 },
 {
  "method": "POST",
  "path": "/products/import",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN"
  ],
  "summary": "Copies products of a connected food platform's catalogue into the owner's catalogue.",
  "body": "ImportProductsRequest",
  "response": {
   "status": 200,
   "type": "ImportProductsResponse"
  }
 },
 {
  "method": "GET",
  "path": "/food-platforms",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN"
  ],
  "summary": "Lists the three supported delivery platforms and whether each one is connected.",
  "response": {
   "status": 200,
   "type": "FoodPlatformResponse",
   "list": true
  }
 },
 {
  "method": "PUT",
  "path": "/food-platforms/{platform}",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN"
  ],
  "summary": "Connects a platform with the owner's API key (the key is stored encrypted and never returned).",
  "body": "ConnectFoodPlatformRequest",
  "response": {
   "status": 200,
   "type": "FoodPlatformResponse"
  },
  "errors": [
   {
    "status": 400,
    "when": "The platform rejected the key."
   }
  ]
 },
 {
  "method": "DELETE",
  "path": "/food-platforms/{platform}",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN"
  ],
  "summary": "Disconnects a platform.",
  "response": {
   "status": 204
  },
  "errors": [
   {
    "status": 409,
    "when": "The platform has orders in progress."
   }
  ]
 },
 {
  "method": "GET",
  "path": "/food-platforms/{platform}/catalog",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN"
  ],
  "summary": "Reads the product catalogue of a connected platform.",
  "response": {
   "status": 200,
   "type": "CatalogProductResponse",
   "list": true
  }
 },
 {
  "method": "GET",
  "path": "/properties/{id}/food-platforms",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN"
  ],
  "summary": "Lists the platforms enabled for a property.",
  "response": {
   "status": 200,
   "type": "PropertyFoodPlatformsResponse"
  }
 },
 {
  "method": "PUT",
  "path": "/properties/{id}/food-platforms",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN"
  ],
  "summary": "Sets the platforms enabled for a property.",
  "body": "SetPropertyFoodPlatformsRequest",
  "response": {
   "status": 200,
   "type": "PropertyFoodPlatformsResponse"
  }
 }
]
```

## A.6 Order Management (/orders, /device/orders, /food)

```api
[
 {
  "method": "POST",
  "path": "/orders?propertyId={propertyId}",
  "roles": [
   "DEVICE"
  ],
  "summary": "Submits a new order from the kiosk; the property must be the device's own.",
  "body": "CreateOrderRequest",
  "response": {
   "status": 201,
   "type": "OrderResponse"
  },
  "notes": [
   "The order starts as PENDING and is confirmed once its payment is approved.",
   "For FOOD_DELIVERY orders the `platform` and `restaurantId` are required and the `productId` of each item is the platform's menu item.",
   "Each quantity must be between 1 and 99."
  ],
  "errors": [
   {
    "status": 400,
    "when": "A food order below the restaurant's minimum; a `guestEmail` that is not a valid address or is longer than 255 characters; `notes` longer than 1,024 characters."
   },
   {
    "status": 403,
    "when": "The platform is not enabled at the property."
   },
   {
    "status": 409,
    "when": "A product is unavailable overall or at the property."
   }
  ]
 },
 {
  "method": "GET",
  "path": "/orders?propertyId={propertyId}",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN"
  ],
  "summary": "Retrieves a property's orders.",
  "response": {
   "status": 200,
   "type": "OrderResponse",
   "list": true
  },
  "notes": [
   "Optional filtering with the `status` parameter (PENDING, CONFIRMED, PREPARING, OUT_FOR_DELIVERY, DELIVERED, CANCELLED)."
  ]
 },
 {
  "method": "GET",
  "path": "/orders/{id}",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN"
  ],
  "summary": "Shows the detailed information of an order.",
  "response": {
   "status": 200,
   "type": "OrderResponse"
  }
 },
 {
  "method": "PATCH",
  "path": "/orders/{id}/status",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN"
  ],
  "summary": "Updates an order's status by the owner, one step at a time (CONFIRMED → PREPARING → OUT_FOR_DELIVERY → DELIVERED).",
  "body": "UpdateOrderStatusRequest",
  "response": {
   "status": 200,
   "type": "OrderResponse"
  },
  "notes": [
   "CANCELLED is allowed from any non-final status; a PENDING order can only be cancelled, since its payment confirms it, and a food order can only be cancelled, since its platform advances it.",
   "Cancelling a food order requires the platform to accept the cancellation."
  ],
  "errors": [
   {
    "status": 409,
    "when": "Any other transition; the platform refuses the cancellation (the status stays unchanged); a paid food order that is still being placed with its platform (no platform identifier yet), for up to 5 minutes after its last update: \"The order is being placed with the delivery platform, try again in a moment\"."
   }
  ]
 },
 {
  "method": "GET",
  "path": "/device/orders/{id}",
  "roles": [
   "DEVICE"
  ],
  "summary": "Checks an order's status from the kiosk device that submitted it.",
  "response": {
   "status": 200,
   "type": "OrderResponse"
  }
 },
 {
  "method": "PATCH",
  "path": "/device/orders/{id}/cancel",
  "roles": [
   "DEVICE"
  ],
  "summary": "Cancels an order by the guest through the kiosk device, only while it is PENDING or CONFIRMED.",
  "response": {
   "status": 200,
   "type": "OrderResponse"
  },
  "errors": [
   {
    "status": 403,
    "when": "An order of another device."
   },
   {
    "status": 409,
    "when": "The order is not PENDING or CONFIRMED; a paid food order that is still being placed with its platform, as in `PATCH /orders/{id}/status`."
   }
  ]
 },
 {
  "method": "GET",
  "path": "/food/restaurants",
  "roles": [
   "DEVICE"
  ],
  "summary": "Lists the restaurants of the platforms enabled for the device's property.",
  "response": {
   "status": 200,
   "type": "FoodRestaurantResponse",
   "list": true
  }
 },
 {
  "method": "GET",
  "path": "/food/restaurants/{platform}/{restaurantId}/menu",
  "roles": [
   "DEVICE"
  ],
  "summary": "Reads a restaurant's menu.",
  "response": {
   "status": 200,
   "type": "FoodMenuItemResponse",
   "list": true
  }
 }
]
```

## A.7 Payments and Auxiliary Services (/payments, /dashboard, /audit-logs, /health)

```api
[
 {
  "method": "POST",
  "path": "/payments",
  "roles": [
   "DEVICE"
  ],
  "summary": "Pays for an order placed from the calling device: the backend requests approval from the payment provider and records the outcome.",
  "body": "InitiatePaymentRequest",
  "response": {
   "status": 201,
   "type": "PaymentResponse"
  },
  "notes": [
   "The card number and PIN are not sent; `pinVerified` is sent only for chip cards and `cardLast4` only when the reader exposes it.",
   "A declined payment is also returned with 201 and carries `status` FAILED and a `declineReason`.",
   "For a food order that its platform could not accept after the payment, the response carries `orderStatus` CANCELLED and a `message`, and a refund of the amount is requested from the payment provider."
  ],
  "errors": [
   {
    "status": 409,
    "when": "The order is already paid or is no longer PENDING."
   }
  ]
 },
 {
  "method": "GET",
  "path": "/orders/{orderId}/payments",
  "roles": [
   "OWNER"
  ],
  "summary": "Retrieves the payment attempts of a specific order.",
  "response": {
   "status": 200,
   "type": "PaymentResponse",
   "list": true
  }
 },
 {
  "method": "GET",
  "path": "/dashboard/summary",
  "roles": [
   "OWNER"
  ],
  "summary": "Summary of the owner's activity.",
  "response": {
   "status": 200,
   "type": "DashboardSummaryResponse"
  }
 },
 {
  "method": "GET",
  "path": "/audit-logs?limit={limit}&offset={offset}",
  "roles": [
   "PLATFORM_ADMIN"
  ],
  "summary": "Retrieves the history of system actions (Audit Trail).",
  "response": {
   "status": 200,
   "type": "AuditLogResponse",
   "list": true
  },
  "notes": [
   "By default the latest 50 are returned."
  ]
 },
 {
  "method": "GET",
  "path": "/health",
  "roles": [
   "PUBLIC"
  ],
  "summary": "Service health check (Health Check).",
  "response": {
   "status": 200,
   "example": {
    "status": "ok"
   }
  }
 }
]
```
