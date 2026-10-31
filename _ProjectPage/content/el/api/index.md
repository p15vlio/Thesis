Το backend του συστήματος έχει αναπτυχθεί με το framework Ktor και εκθέτει RESTful υπηρεσίες σε μορφή JSON. Η αυθεντικοποίηση υλοποιείται μέσω JSON Web Tokens (JWT) στην κεφαλίδα Authorization τύπου Bearer. Οι λειτουργίες πολλαπλής μίσθωσης εκτελούνται δυναμικά υπό το αντίστοιχο tenant schema (TenantContext) μέσω του JetBrains Exposed ORM.

## Α.1 Αυθεντικοποίηση Χρηστών (/auth)

- `POST /auth/login`: Αυθεντικοποίηση χρήστη πλατφόρμας (Platform Admin ή Owner). Σώμα αιτήματος: `{"email": "string", "password": "string"}`. Απάντηση (200 OK): `{"accessToken": "string", "refreshToken": "string", "role": "string", "expiresIn": 900}`.
- `POST /auth/refresh`: Ανανέωση συνεδρίας μέσω περιστρεφόμενου refresh token (Refresh Token Rotation). Σώμα αιτήματος: `{"refreshToken": "string"}`. Απάντηση (200 OK): `{"accessToken": "string", "refreshToken": "string", "expiresIn": 900}`.

## Α.2 Διαχείριση Πλατφόρμας και Ιδιοκτητών (/owners, /admin)

- `GET /owners`: Ανάκτηση όλων των καταχωρημένων ιδιοκτητών πλατφόρμας. Ρόλος: PLATFORM_ADMIN. Απάντηση (200 OK): Array of OwnerResponse objects.
- `POST /owners`: Εγγραφή νέου ιδιοκτήτη και αυτόματη δημιουργία αποκλειστικού σχήματος βάσης δεδομένων (Tenant Schema Provisioning). Ρόλος: PLATFORM_ADMIN. Σώμα αιτήματος: `{"companyName": "string", "vatId": "string", "contactEmail": "string", "contactPhone": "string"}`. Απάντηση (201 Created): `{"owner": OwnerResponse, "temporaryPassword": "string"}`.
- `GET /owners/{id}`: Ανάκτηση στοιχείων συγκεκριμένου ιδιοκτήτη βάσει αναγνωριστικού. Ρόλος: PLATFORM_ADMIN. Απάντηση (200 OK): OwnerResponse.
- `PUT /owners/{id}`: Ενημέρωση στοιχείων και ρυθμίσεων ιδιοκτήτη. Ρόλος: PLATFORM_ADMIN. Σώμα αιτήματος: UpdateOwnerRequest. Απάντηση (200 OK): OwnerResponse.
- `PATCH /owners/{id}/deactivate`: Προσωρινή αναστολή λειτουργίας λογαριασμού ιδιοκτήτη (Soft Delete). Ρόλος: PLATFORM_ADMIN. Απάντηση (204 No Content).
- `PATCH /owners/{id}/activate`: Επανενεργοποίηση ανασταλμένου λογαριασμού ιδιοκτήτη. Ρόλος: PLATFORM_ADMIN. Απάντηση (204 No Content).
- `GET /admin/owners/stats`: Συγκεντρωτικά στατιστικά πλατφόρμας ανά ιδιοκτήτη (πλήθος ακινήτων, συσκευών, προϊόντων). Ρόλος: PLATFORM_ADMIN. Απάντηση (200 OK): Array of OwnerWithStatsResponse objects.
- `GET /admin/orders/summary`: Συγκεντρωτική κατανομή ενεργών παραγγελιών σε ολόκληρη την πλατφόρμα. Ρόλος: PLATFORM_ADMIN. Απάντηση (200 OK): AdminOrderSummaryResponse.

## Α.3 Διαχείριση Ακινήτων και Καταλυμάτων (/properties)

- `GET /properties`: Ανάκτηση όλων των καταχωρημένων ακινήτων του συνδεδεμένου ιδιοκτήτη. Ρόλος: OWNER, PLATFORM_ADMIN. Απάντηση (200 OK): Array of PropertyResponse objects.
- `POST /properties`: Καταχώριση νέου ακινήτου στο σχήμα του ιδιοκτήτη. Ρόλος: OWNER, PLATFORM_ADMIN. Σώμα αιτήματος: `{"name": "string", "streetName": "string", "streetNo": "string", "postalCode": "string", "contactPhone": "string", "fulfillmentModel": "string"}`. Απάντηση (201 Created): PropertyResponse.
- `GET /properties/{id}`: Ανάκτηση στοιχείων συγκεκριμένου ακινήτου. Ρόλος: OWNER, PLATFORM_ADMIN, DEVICE. Απάντηση (200 OK): PropertyResponse.
- `PUT /properties/{id}`: Ενημέρωση στοιχείων και παραμέτρων λειτουργίας ακινήτου. Ρόλος: OWNER, PLATFORM_ADMIN. Σώμα αιτήματος: UpdatePropertyRequest. Απάντηση (200 OK): PropertyResponse.
- `PATCH /properties/{id}/deactivate`: Αναστολή λειτουργίας ακινήτου. Ρόλος: OWNER, PLATFORM_ADMIN. Απάντηση (204 No Content).
- `PATCH /properties/{id}/activate`: Επανενεργοποίηση ακινήτου. Ρόλος: OWNER, PLATFORM_ADMIN. Απάντηση (204 No Content).
- `DELETE /properties/{id}`: Οριστική διαγραφή ακινήτου. Ρόλος: OWNER, PLATFORM_ADMIN. Απάντηση (204 No Content).

## Α.4 Διαχείριση Συσκευών Kiosk (/properties/{propertyId}/devices, /devices)

- `GET /properties/{propertyId}/devices`: Λίστα συσκευών kiosk που είναι συνδεδεμένες στο συγκεκριμένο ακίνητο. Ρόλος: OWNER, PLATFORM_ADMIN. Απάντηση (200 OK): Array of DeviceResponse objects.
- `POST /properties/{propertyId}/devices`: Καταχώριση νέας συσκευής και δημιουργία κωδικού ενεργοποίησης (activation code). Ρόλος: OWNER, PLATFORM_ADMIN. Σώμα αιτήματος: `{"name": "string"}`. Απάντηση (201 Created): DeviceResponse (με πεδίο activationCode).
- `GET /devices/{id}`: Ανάκτηση στοιχείων συσκευής βάσει αναγνωριστικού. Ρόλος: OWNER, PLATFORM_ADMIN. Απάντηση (200 OK): DeviceResponse.
- `DELETE /devices/{id}`: Διαγραφή συσκευής kiosk από το ακίνητο. Ρόλος: OWNER, PLATFORM_ADMIN. Απάντηση (204 No Content).
- `PATCH /devices/{id}/deactivate`: Απενεργοποίηση συσκευής. Ρόλος: OWNER, PLATFORM_ADMIN. Απάντηση (204 No Content).
- `PATCH /devices/{id}/reactivate`: Επανενεργοποίηση απενεργοποιημένης συσκευής. Ρόλος: OWNER, PLATFORM_ADMIN. Απάντηση (204 No Content).
- `POST /devices/{id}/unregister`: Αποσύνδεση ενεργοποιημένου tablet από τη συσκευή kiosk. Ρόλος: OWNER, PLATFORM_ADMIN. Απάντηση (200 OK): DeviceResponse.
- `GET /devices/{id}/orders`: Ανάκτηση ιστορικού παραγγελιών που υποβλήθηκαν από τη συγκεκριμένη συσκευή. Ρόλος: OWNER, PLATFORM_ADMIN. Απάντηση (200 OK): Array of OrderResponse objects.
- `GET /devices/resolve?code={code}`: Δημόσιος εντοπισμός σχήματος μισθωτή βάσει εισαχθέντος κωδικού ενεργοποίησης (pre-activation lookup). Χωρίς JWT. Απάντηση (200 OK): ResolveDeviceResponse.
- `POST /devices/{id}/activate?schemaName={schemaName}`: Ενεργοποίηση tablet kiosk και έκδοση μακροχρόνιου διακριτικού JWT διάρκειας 1 έτους. Χωρίς JWT. Σώμα αιτήματος: `{"activationCode": "string", "androidDeviceId": "string"}`. Απάντηση (200 OK): DeviceTokenResponse.
- `PATCH /devices/{id}/ping?schemaName={schemaName}`: Περιοδικός έλεγχος λειτουργίας (heartbeat) και καταγραφή lastSeenAt. Χωρίς JWT. Απάντηση (204 No Content).

## Α.5 Κατάλογος Προϊόντων και Διαθεσιμότητα (/products)

- `GET /products`: Ανάκτηση καταλόγου προϊόντων (οι συσκευές kiosk λαμβάνουν αυτόματα μόνο τα διαθέσιμα προϊόντα του ακινήτου τους). Ρόλος: OWNER, PLATFORM_ADMIN, DEVICE. Απάντηση (200 OK): Array of ProductResponse objects.
- `POST /products`: Δημιουργία νέου είδους στον κατάλογο του ιδιοκτήτη. Ρόλος: OWNER, PLATFORM_ADMIN. Σώμα αιτήματος: CreateProductRequest. Απάντηση (201 Created): ProductResponse.
- `GET /products/{id}`: Προβολή στοιχείων συγκεκριμένου προϊόντος. Ρόλος: OWNER, PLATFORM_ADMIN, DEVICE. Απάντηση (200 OK): ProductResponse.
- `PUT /products/{id}`: Ενημέρωση στοιχείων προϊόντος. Ρόλος: OWNER, PLATFORM_ADMIN. Σώμα αιτήματος: UpdateProductRequest. Απάντηση (200 OK): ProductResponse.
- `PATCH /products/{id}/availability`: Καθολική ενεργοποίηση ή απενεργοποίηση προϊόντος για ολόκληρο το κατάλυμα. Ρόλος: OWNER, PLATFORM_ADMIN. Σώμα αιτήματος: `{"isAvailable": boolean}`. Απάντηση (200 OK): ProductResponse.
- `PATCH /products/{id}/properties/{propertyId}/availability`: Εξειδικευμένη ρύθμιση διαθεσιμότητας προϊόντος για συγκεκριμένο ακίνητο (Product Property Override). Ρόλος: OWNER, PLATFORM_ADMIN. Σώμα αιτήματος: `{"isAvailable": boolean}`. Απάντηση (200 OK): ProductPropertyOverrideResponse.
- `DELETE /products/{id}`: Διαγραφή προϊόντος από τον κατάλογο. Ρόλος: OWNER, PLATFORM_ADMIN. Απάντηση (204 No Content).

## Α.6 Διαχείριση Παραγγελιών (/orders, /device/orders)

- `POST /orders?propertyId={propertyId}`: Υποβολή νέας παραγγελίας από το kiosk. Ρόλος: DEVICE. Σώμα αιτήματος: `{"type": "string", "fulfillmentModel": "string", "items": [{"productId": "UUID", "quantity": 1}], "notes": "string"}`. Απάντηση (201 Created): OrderResponse (κατάσταση: CONFIRMED).
- `GET /orders?propertyId={propertyId}`: Ανάκτηση παραγγελιών ακινήτου με δυνατότητα φιλτραρίσματος βάσει παραμέτρου status. Ρόλος: OWNER, PLATFORM_ADMIN. Απάντηση (200 OK): Array of OrderResponse objects.
- `GET /orders/{id}`: Προβολή αναλυτικών στοιχείων παραγγελίας. Ρόλος: OWNER, PLATFORM_ADMIN. Απάντηση (200 OK): OrderResponse.
- `PATCH /orders/{id}/status`: Ενημέρωση κατάστασης παραγγελίας από τον ιδιοκτήτη (PREPARING, OUT_FOR_DELIVERY, DELIVERED, CANCELLED). Ρόλος: OWNER, PLATFORM_ADMIN. Σώμα αιτήματος: `{"status": "string"}`. Απάντηση (200 OK): OrderResponse.
- `GET /device/orders/{id}`: Έλεγχος κατάστασης παραγγελίας από τη συσκευή kiosk που την υπέβαλε. Ρόλος: DEVICE. Απάντηση (200 OK): OrderResponse.
- `PATCH /device/orders/{id}/cancel`: Ακύρωση παραγγελίας από τον φιλοξενούμενο μέσω της συσκευής kiosk. Ρόλος: DEVICE. Απάντηση (200 OK): OrderResponse.

## Α.7 Πληρωμές και Βοηθητικές Υπηρεσίες (/payments, /dashboard, /audit-logs, /health)

- `POST /payments`: Καταγραφή συναλλαγής πληρωμής για υφιστάμενη παραγγελία. Ρόλος: DEVICE. Σώμα αιτήματος: `{"orderId": "UUID", "method": "VIVA_APP2APP | VIVA_CLOUD | REVOLUT_QR", "amount": 0.0}`. Απάντηση (201 Created): PaymentResponse.
- `GET /orders/{orderId}/payment`: Ανάκτηση στοιχείων πληρωμής συγκεκριμένης παραγγελίας. Ρόλος: OWNER. Απάντηση (200 OK): PaymentResponse.
- `GET /dashboard/summary`: Σύνοψη δραστηριότητας ιδιοκτήτη (ενεργά ακίνητα, online συσκευές, σημερινές παραγγελίες και έσοδα). Ρόλος: OWNER. Απάντηση (200 OK): DashboardSummaryResponse.
- `GET /audit-logs`: Ανάκτηση ιστορικού καταγραφής ενεργειών συστήματος (Audit Trail). Ρόλος: OWNER, PLATFORM_ADMIN. Απάντηση (200 OK): Array of AuditLogResponse objects.
- `GET /health`: Έλεγχος υγείας υπηρεσίας (Health Check). Χωρίς αυθεντικοποίηση. Απάντηση (200 OK): `{"status": "UP"}`.
