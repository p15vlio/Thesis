Το backend εκθέτει ένα REST API σε JSON κάτω από το πρόθεμα `/api`: 65 endpoints, από τα οποία τα 5 είναι δημόσια. Κάθε endpoint ανοίγει με κλικ και δείχνει το σώμα του αιτήματος, την απάντηση και τα σφάλματά του· τα πεδία κάθε τύπου προκύπτουν από τα κοινά DTOs του κώδικα.

| Σύμβαση | Τιμή |
|---|---|
| Βασική διεύθυνση | `/api`, π.χ. `http://localhost:8080/api` |
| Ταυτοποίηση | JWT στην κεφαλίδα `Authorization: Bearer <token>` |
| Διάρκεια tokens | access token 15 λεπτά, refresh token 30 ημέρες, token συσκευής kiosk 1 έτος |
| Επιλογή tenant | από το token· ο διαχειριστής της πλατφόρμας, του οποίου το token δεν φέρει schema, δίνει την παράμετρο `schemaName` στα endpoints των tenants |
| Ποσά και χρόνος | ποσά σε ακέραια λεπτά του ευρώ· χρονοσφραγίδες σε epoch milliseconds |
| Αυστηρό JSON | άγνωστο πεδίο ή υποχρεωτικό πεδίο που λείπει: 400 Bad Request |
| Σφάλματα | `{"code": "string", "message": "string"}`· JWT που λείπει ή δεν είναι έγκυρο: σκέτο 401 με κεφαλίδα `WWW-Authenticate` |
| Token συσκευής | 401 όταν η συσκευή είναι άγνωστη ή αποσυνδεδεμένη· 403 όταν η συσκευή ή ο ιδιοκτήτης της είναι απενεργοποιημένοι |
| Rate limit | login, refresh, επίλυση κωδικού και ενεργοποίηση συσκευής: 20 αιτήματα ανά λεπτό ανά IP· αναζήτηση διευθύνσεων: 30 ανά λεπτό ανά χρήστη |

```api-toolbar
```

## Α.1 Ταυτοποίηση Χρηστών (/auth)

```api
[
 {
  "method": "POST",
  "path": "/auth/login",
  "roles": [
   "PUBLIC"
  ],
  "summary": "Ταυτοποίηση χρήστη πλατφόρμας.",
  "rateLimited": true,
  "body": "LoginRequest",
  "response": {
   "status": 200,
   "type": "TokenResponse"
  },
  "notes": [
   "Το προαιρετικό πεδίο `schemaName` δεν χρησιμοποιείται: το schema του ιδιοκτήτη προκύπτει από τον λογαριασμό του."
  ]
 },
 {
  "method": "POST",
  "path": "/auth/refresh",
  "roles": [
   "PUBLIC"
  ],
  "summary": "Ανανέωση συνεδρίας με εναλλαγή του refresh token (Refresh Token Rotation).",
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
  "summary": "Αλλαγή του κωδικού του συνδεδεμένου διαχειριστή ή ιδιοκτήτη.",
  "body": "ChangePasswordRequest",
  "response": {
   "status": 200,
   "type": "TokenResponse"
  },
  "notes": [
   "Επιστρέφει νέο ζεύγος tokens. Ο νέος κωδικός πρέπει να έχει τουλάχιστον 8 χαρακτήρες και να διαφέρει από τον τρέχοντα."
  ],
  "errors": [
   {
    "status": 400,
    "when": "Λάθος τρέχων κωδικός (όχι 401)."
   },
   {
    "status": 403,
    "when": "Αίτημα με token συσκευής."
   }
  ]
 }
]
```

## Α.2 Διαχείριση Πλατφόρμας και Ιδιοκτητών (/owners, /admin, /company-lookup, /settings)

```api
[
 {
  "method": "GET",
  "path": "/owners",
  "roles": [
   "PLATFORM_ADMIN"
  ],
  "summary": "Ανάκτηση όλων των καταχωρημένων ιδιοκτητών πλατφόρμας.",
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
  "summary": "Εγγραφή νέου ιδιοκτήτη και αυτόματη δημιουργία αποκλειστικού schema βάσης δεδομένων (Tenant Schema Provisioning).",
  "body": "CreateOwnerRequest",
  "response": {
   "status": 201,
   "type": "OwnerResponse"
  },
  "notes": [
   "Η απάντηση φέρει το `temporaryPassword` του νέου λογαριασμού μόνο σε αυτή την απάντηση."
  ]
 },
 {
  "method": "GET",
  "path": "/owners/{id}",
  "roles": [
   "PLATFORM_ADMIN"
  ],
  "summary": "Ανάκτηση στοιχείων συγκεκριμένου ιδιοκτήτη βάσει αναγνωριστικού.",
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
  "summary": "Ενημέρωση των στοιχείων ιδιοκτήτη (τα ίδια πεδία με το αίτημα δημιουργίας, χωρίς `vatId` και `contactEmail`).",
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
  "summary": "Προσωρινή αναστολή του λογαριασμού ιδιοκτήτη (Soft Delete).",
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
  "summary": "Επανενεργοποίηση ανασταλμένου λογαριασμού ιδιοκτήτη.",
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
  "summary": "Δημιουργία νέου προσωρινού κωδικού για έναν ιδιοκτήτη.",
  "response": {
   "status": 200,
   "type": "OwnerResponse"
  },
  "notes": [
   "Η απάντηση φέρει το πεδίο `temporaryPassword`."
  ]
 },
 {
  "method": "GET",
  "path": "/admin/owners/stats",
  "roles": [
   "PLATFORM_ADMIN"
  ],
  "summary": "Συγκεντρωτικά στατιστικά της πλατφόρμας ανά ιδιοκτήτη.",
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
  "summary": "Το πλήθος των ενεργών παραγγελιών και η κατανομή όλων των παραγγελιών της πλατφόρμας ανά κατάσταση.",
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
  "summary": "Αναζήτηση εταιρείας στο εταιρικό μητρώο με εννεαψήφιο ΑΦΜ, για την προσυμπλήρωση της φόρμας ιδιοκτήτη.",
  "response": {
   "status": 200,
   "type": "CompanyLookupResponse"
  },
  "errors": [
   {
    "status": 404,
    "when": "Το μητρώο δεν έχει τέτοια εταιρεία."
   }
  ]
 },
 {
  "method": "GET",
  "path": "/settings/smtp",
  "roles": [
   "PLATFORM_ADMIN"
  ],
  "summary": "Ανάγνωση των ρυθμίσεων εξερχόμενης αλληλογραφίας.",
  "response": {
   "status": 200,
   "type": "SmtpSettingsResponse"
  },
  "notes": [
   "Ο κωδικός δεν επιστρέφεται ποτέ, μόνο το `passwordSet`."
  ]
 },
 {
  "method": "PUT",
  "path": "/settings/smtp",
  "roles": [
   "PLATFORM_ADMIN"
  ],
  "summary": "Αποθήκευση των ρυθμίσεων εξερχόμενης αλληλογραφίας.",
  "body": "UpdateSmtpSettingsRequest",
  "response": {
   "status": 200,
   "type": "SmtpSettingsResponse"
  },
  "notes": [
   "Αν το `password` λείπει, διατηρείται ο αποθηκευμένος κωδικός."
  ]
 },
 {
  "method": "POST",
  "path": "/settings/smtp/test",
  "roles": [
   "PLATFORM_ADMIN"
  ],
  "summary": "Αποστολή δοκιμαστικού email με τις αποθηκευμένες ρυθμίσεις.",
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
  "summary": "Έλεγχος ύπαρξης αποθηκευμένου κλειδιού API του εταιρικού μητρώου (το ίδιο το κλειδί δεν επιστρέφεται ποτέ).",
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
  "summary": "Αποθήκευση του κλειδιού API του εταιρικού μητρώου (κρυπτογραφημένο).",
  "body": "UpdateGemhSettingsRequest",
  "response": {
   "status": 200,
   "type": "GemhSettingsResponse"
  }
 }
]
```

## Α.3 Διαχείριση Καταλυμάτων (/properties, /geocode)

```api
[
 {
  "method": "GET",
  "path": "/properties",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN"
  ],
  "summary": "Ανάκτηση όλων των καταχωρημένων καταλυμάτων του συνδεδεμένου ιδιοκτήτη.",
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
  "summary": "Εγγραφή νέου καταλύματος στο schema του ιδιοκτήτη.",
  "body": "CreatePropertyRequest",
  "response": {
   "status": 201,
   "type": "PropertyResponse"
  },
  "notes": [
   "Το `checkOutTime` έχει τη μορφή \"HH:mm\"."
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
  "summary": "Ανάκτηση στοιχείων συγκεκριμένου καταλύματος.",
  "response": {
   "status": 200,
   "type": "PropertyResponse"
  },
  "notes": [
   "Μια συσκευή διαβάζει μόνο το δικό της κατάλυμα."
  ]
 },
 {
  "method": "PUT",
  "path": "/properties/{id}",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN"
  ],
  "summary": "Ενημέρωση των στοιχείων καταλύματος· το σώμα έχει τα ίδια πεδία με το αίτημα δημιουργίας.",
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
  "summary": "Αναστολή λειτουργίας καταλύματος.",
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
  "summary": "Επανενεργοποίηση καταλύματος.",
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
  "summary": "Οριστική διαγραφή καταλύματος.",
  "response": {
   "status": 204
  },
  "errors": [
   {
    "status": 409,
    "when": "Το κατάλυμα έχει παραγγελίες ή συσκευές."
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
  "summary": "Αναζήτηση διεύθυνσης μέσω της δημόσιας υπηρεσίας γεωκωδικοποίησης.",
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
  "summary": "Εύρεση της διεύθυνσης μιας θέσης στον χάρτη.",
  "rateLimited": true,
  "response": {
   "status": 200,
   "type": "GeocodedAddressResponse"
  }
 }
]
```

## Α.4 Διαχείριση Συσκευών Kiosk (/properties/{propertyId}/devices, /devices)

```api
[
 {
  "method": "GET",
  "path": "/properties/{propertyId}/devices",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN"
  ],
  "summary": "Λίστα των συσκευών kiosk που είναι συνδεδεμένες στο συγκεκριμένο κατάλυμα.",
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
  "summary": "Εγγραφή νέας συσκευής και δημιουργία κωδικού ενεργοποίησης (activation code) με τον αντίστοιχο κωδικό QR.",
  "body": "CreateDeviceRequest",
  "response": {
   "status": 201,
   "type": "DeviceResponse"
  },
  "notes": [
   "Η απάντηση φέρει τα πεδία `activationCode` και `qrCodeBase64`."
  ]
 },
 {
  "method": "GET",
  "path": "/devices/{id}",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN"
  ],
  "summary": "Ανάκτηση στοιχείων συσκευής βάσει αναγνωριστικού.",
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
  "summary": "Διαγραφή συσκευής kiosk από το κατάλυμα.",
  "response": {
   "status": 204
  },
  "errors": [
   {
    "status": 409,
    "when": "Η συσκευή έχει παραγγελίες."
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
  "summary": "Απενεργοποίηση συσκευής.",
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
  "summary": "Επανενεργοποίηση απενεργοποιημένης συσκευής.",
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
  "summary": "Αποσύνδεση ενεργοποιημένου tablet από τη συσκευή kiosk, ώστε να μπορεί να ενεργοποιηθεί ξανά.",
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
  "summary": "Ανάκτηση του ιστορικού παραγγελιών που υποβλήθηκαν από τη συγκεκριμένη συσκευή.",
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
  "summary": "Αναζήτηση της συσκευής και του tenant schema από κωδικό ενεργοποίησης που πληκτρολογήθηκε (pre-activation lookup).",
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
  "summary": "Ενεργοποίηση tablet kiosk και έκδοση JWT μακράς διάρκειας, με ισχύ ενός έτους.",
  "rateLimited": true,
  "body": "ActivateDeviceRequest",
  "response": {
   "status": 200,
   "type": "DeviceResponse"
  },
  "notes": [
   "Η απάντηση φέρει το πεδίο `token`."
  ],
  "errors": [
   {
    "status": 400,
    "when": "Το `schemaName` λείπει ή δεν είναι έγκυρο."
   },
   {
    "status": 403,
    "when": "Λάθος κωδικός ενεργοποίησης."
   },
   {
    "status": 404,
    "when": "Άγνωστη συσκευή."
   },
   {
    "status": 409,
    "when": "Η συσκευή είναι ήδη ενεργοποιημένη (και για τη δεύτερη από δύο ταυτόχρονες ενεργοποιήσεις) ή το κατάλυμα ή ο ιδιοκτήτης της είναι απενεργοποιημένοι."
   }
  ]
 },
 {
  "method": "PATCH",
  "path": "/devices/{id}/ping",
  "roles": [
   "DEVICE"
  ],
  "summary": "Περιοδικός έλεγχος λειτουργίας (heartbeat) και καταγραφή του `lastSeenAt`.",
  "response": {
   "status": 204
  },
  "notes": [
   "Μια συσκευή μπορεί να αναφερθεί μόνο για τον εαυτό της."
  ],
  "errors": [
   {
    "status": 401,
    "when": "Η συσκευή έχει αποσυνδεθεί."
   },
   {
    "status": 403,
    "when": "Η συσκευή είναι απενεργοποιημένη."
   }
  ]
 }
]
```

## Α.5 Κατάλογος Προϊόντων και Διαθεσιμότητα (/products, /food-platforms)

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
  "summary": "Ανάκτηση του καταλόγου προϊόντων.",
  "response": {
   "status": 200,
   "type": "ProductResponse",
   "list": true
  },
  "notes": [
   "Οι συσκευές kiosk λαμβάνουν αυτόματα μόνο τα προϊόντα που είναι διαθέσιμα στο κατάλυμά τους."
  ]
 },
 {
  "method": "POST",
  "path": "/products",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN"
  ],
  "summary": "Δημιουργία νέου είδους στον κατάλογο του ιδιοκτήτη.",
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
  "summary": "Εμφάνιση των στοιχείων συγκεκριμένου προϊόντος.",
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
  "summary": "Ενημέρωση των στοιχείων προϊόντος.",
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
  "summary": "Καθολική ενεργοποίηση ή απενεργοποίηση προϊόντος για όλα τα καταλύματα του ιδιοκτήτη.",
  "body": "UpdateProductRequest",
  "response": {
   "status": 200,
   "type": "ProductResponse"
  },
  "notes": [
   "Το σώμα είναι ίδιο με εκείνο της ενημέρωσης και εφαρμόζεται μόνο το `isAvailable`."
  ]
 },
 {
  "method": "PATCH",
  "path": "/products/{id}/properties/{propertyId}/availability",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN"
  ],
  "summary": "Ειδική ρύθμιση διαθεσιμότητας προϊόντος σε συγκεκριμένο κατάλυμα (Product Property Override).",
  "body": "SetProductAvailabilityRequest",
  "response": {
   "status": 200,
   "type": "ProductResponse"
  },
  "notes": [
   "Το πεδίο `disabledPropertyIds` της απάντησης απαριθμεί τα καταλύματα όπου το προϊόν είναι απενεργοποιημένο."
  ]
 },
 {
  "method": "DELETE",
  "path": "/products/{id}",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN"
  ],
  "summary": "Διαγραφή προϊόντος από τον κατάλογο.",
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
  "summary": "Αντιγραφή προϊόντων από τον κατάλογο μιας συνδεδεμένης πλατφόρμας διανομής φαγητού στον κατάλογο του ιδιοκτήτη.",
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
  "summary": "Λίστα των τριών υποστηριζόμενων πλατφορμών διανομής και αν η καθεμία είναι συνδεδεμένη.",
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
  "summary": "Σύνδεση πλατφόρμας με το κλειδί API του ιδιοκτήτη (το κλειδί αποθηκεύεται κρυπτογραφημένο και δεν επιστρέφεται ποτέ).",
  "body": "ConnectFoodPlatformRequest",
  "response": {
   "status": 200,
   "type": "FoodPlatformResponse"
  },
  "errors": [
   {
    "status": 400,
    "when": "Η πλατφόρμα απέρριψε το κλειδί."
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
  "summary": "Αποσύνδεση πλατφόρμας.",
  "response": {
   "status": 204
  },
  "errors": [
   {
    "status": 409,
    "when": "Η πλατφόρμα έχει παραγγελίες σε εξέλιξη."
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
  "summary": "Ανάγνωση του καταλόγου προϊόντων μιας συνδεδεμένης πλατφόρμας.",
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
  "summary": "Λίστα των πλατφορμών που είναι ενεργοποιημένες για ένα κατάλυμα.",
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
  "summary": "Ορισμός των πλατφορμών που είναι ενεργοποιημένες για ένα κατάλυμα.",
  "body": "SetPropertyFoodPlatformsRequest",
  "response": {
   "status": 200,
   "type": "PropertyFoodPlatformsResponse"
  }
 }
]
```

## Α.6 Διαχείριση Παραγγελιών (/orders, /device/orders, /food)

```api
[
 {
  "method": "POST",
  "path": "/orders?propertyId={propertyId}",
  "roles": [
   "DEVICE"
  ],
  "summary": "Υποβολή νέας παραγγελίας από το kiosk· το κατάλυμα πρέπει να είναι της ίδιας της συσκευής.",
  "body": "CreateOrderRequest",
  "response": {
   "status": 201,
   "type": "OrderResponse"
  },
  "notes": [
   "Η παραγγελία ξεκινά ως PENDING και επιβεβαιώνεται όταν εγκριθεί η πληρωμή της.",
   "Για παραγγελίες FOOD_DELIVERY τα `platform` και `restaurantId` είναι υποχρεωτικά και το `productId` κάθε είδους είναι το είδος του μενού της πλατφόρμας.",
   "Κάθε ποσότητα πρέπει να είναι από 1 έως 99."
  ],
  "errors": [
   {
    "status": 400,
    "when": "Παραγγελία φαγητού κάτω από την ελάχιστη παραγγελία του εστιατορίου· `guestEmail` που δεν είναι έγκυρη διεύθυνση ή ξεπερνά τους 255 χαρακτήρες· `notes` άνω των 1.024 χαρακτήρων."
   },
   {
    "status": 403,
    "when": "Η πλατφόρμα δεν είναι ενεργοποιημένη στο κατάλυμα."
   },
   {
    "status": 409,
    "when": "Προϊόν μη διαθέσιμο συνολικά ή στο κατάλυμα."
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
  "summary": "Ανάκτηση των παραγγελιών ενός καταλύματος.",
  "response": {
   "status": 200,
   "type": "OrderResponse",
   "list": true
  },
  "notes": [
   "Προαιρετικό φιλτράρισμα με την παράμετρο `status` (PENDING, CONFIRMED, PREPARING, OUT_FOR_DELIVERY, DELIVERED, CANCELLED)."
  ]
 },
 {
  "method": "GET",
  "path": "/orders/{id}",
  "roles": [
   "OWNER",
   "PLATFORM_ADMIN"
  ],
  "summary": "Εμφάνιση των αναλυτικών στοιχείων μιας παραγγελίας.",
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
  "summary": "Ενημέρωση της κατάστασης παραγγελίας από τον ιδιοκτήτη, ένα βήμα τη φορά (CONFIRMED → PREPARING → OUT_FOR_DELIVERY → DELIVERED).",
  "body": "UpdateOrderStatusRequest",
  "response": {
   "status": 200,
   "type": "OrderResponse"
  },
  "notes": [
   "Η ακύρωση (CANCELLED) επιτρέπεται από κάθε μη τελική κατάσταση· μια παραγγελία PENDING μπορεί μόνο να ακυρωθεί, αφού την επιβεβαιώνει η πληρωμή της, και μια παραγγελία φαγητού μόνο να ακυρωθεί, αφού την πρόοδό της την ενημερώνει η πλατφόρμα.",
   "Η ακύρωση μιας παραγγελίας φαγητού απαιτεί να την αποδεχθεί η πλατφόρμα."
  ],
  "errors": [
   {
    "status": 409,
    "when": "Κάθε άλλη μετάβαση· άρνηση της ακύρωσης από την πλατφόρμα (η κατάσταση μένει αμετάβλητη)· πληρωμένη παραγγελία φαγητού που υποβάλλεται ακόμη στην πλατφόρμα της (χωρίς ακόμη αναγνωριστικό πλατφόρμας), για έως 5 λεπτά από την τελευταία ενημέρωσή της: «The order is being placed with the delivery platform, try again in a moment»."
   }
  ]
 },
 {
  "method": "GET",
  "path": "/device/orders/{id}",
  "roles": [
   "DEVICE"
  ],
  "summary": "Έλεγχος της κατάστασης μιας παραγγελίας από τη συσκευή kiosk που την υπέβαλε.",
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
  "summary": "Ακύρωση παραγγελίας από τον επισκέπτη μέσω της συσκευής kiosk, μόνο όσο είναι PENDING ή CONFIRMED.",
  "response": {
   "status": 200,
   "type": "OrderResponse"
  },
  "errors": [
   {
    "status": 403,
    "when": "Παραγγελία άλλης συσκευής."
   },
   {
    "status": 409,
    "when": "Η παραγγελία δεν είναι PENDING ή CONFIRMED· πληρωμένη παραγγελία φαγητού που υποβάλλεται ακόμη στην πλατφόρμα της, όπως στο `PATCH /orders/{id}/status`."
   }
  ]
 },
 {
  "method": "GET",
  "path": "/food/restaurants",
  "roles": [
   "DEVICE"
  ],
  "summary": "Λίστα των εστιατορίων των πλατφορμών που είναι ενεργοποιημένες για το κατάλυμα της συσκευής.",
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
  "summary": "Ανάγνωση του μενού ενός εστιατορίου.",
  "response": {
   "status": 200,
   "type": "FoodMenuItemResponse",
   "list": true
  }
 }
]
```

## Α.7 Πληρωμές και Βοηθητικές Υπηρεσίες (/payments, /dashboard, /audit-logs, /health)

```api
[
 {
  "method": "POST",
  "path": "/payments",
  "roles": [
   "DEVICE"
  ],
  "summary": "Πληρωμή μιας παραγγελίας που υποβλήθηκε από την καλούσα συσκευή: το backend ζητά έγκριση από τον φορέα πληρωμών και καταγράφει το αποτέλεσμα.",
  "body": "InitiatePaymentRequest",
  "response": {
   "status": 201,
   "type": "PaymentResponse"
  },
  "notes": [
   "Ο αριθμός της κάρτας και το PIN δεν αποστέλλονται· το `pinVerified` αποστέλλεται μόνο για κάρτες chip και το `cardLast4` μόνο όταν το εκθέτει ο αναγνώστης.",
   "Μια πληρωμή που απορρίφθηκε επιστρέφεται επίσης με 201 και φέρει `status` FAILED και `declineReason`.",
   "Για παραγγελία φαγητού που η πλατφόρμα της δεν μπόρεσε να δεχτεί μετά την πληρωμή, η απάντηση φέρει `orderStatus` CANCELLED και `message`, και ζητείται από τον φορέα πληρωμών η επιστροφή του ποσού."
  ],
  "errors": [
   {
    "status": 409,
    "when": "Η παραγγελία έχει ήδη πληρωθεί ή δεν είναι πλέον PENDING."
   }
  ]
 },
 {
  "method": "GET",
  "path": "/orders/{orderId}/payments",
  "roles": [
   "OWNER"
  ],
  "summary": "Ανάκτηση των αποπειρών πληρωμής μιας συγκεκριμένης παραγγελίας.",
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
  "summary": "Σύνοψη της δραστηριότητας του ιδιοκτήτη.",
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
  "summary": "Ανάκτηση του ιστορικού ενεργειών του συστήματος (Audit Trail).",
  "response": {
   "status": 200,
   "type": "AuditLogResponse",
   "list": true
  },
  "notes": [
   "Από προεπιλογή επιστρέφονται οι τελευταίες 50."
  ]
 },
 {
  "method": "GET",
  "path": "/health",
  "roles": [
   "PUBLIC"
  ],
  "summary": "Έλεγχος υγείας της υπηρεσίας (Health Check).",
  "response": {
   "status": 200,
   "example": {
    "status": "ok"
   }
  }
 }
]
```
