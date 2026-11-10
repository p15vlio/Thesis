This chapter describes the kiosk application the guest uses: its place in the architecture (§8.1), device activation (§8.2), kiosk mode (§8.3), the order and payment flow (§8.4, §8.5), order tracking (§8.6), the design choices for a shared device (§8.7), local storage (§8.8) and the application's independence from the payment provider (§8.9).

## 8.1 Role and Place in the Architecture

The :app module implements the application that runs on the Android device of each accommodation and is the guest's only interface with the system. Unlike :owner-web (Chapter 7), which is compiled to WebAssembly through Compose Multiplatform, :app is a conventional Android application, written with Jetpack Compose for the interface and Jetpack Navigation for navigating between screens. Despite this difference at the presentation level, the module consumes the same :domain and :data DTOs as the backend and owner-web (§4.5.4).

The application does not use a dependency injection framework such as Hilt or Koin. A singleton object, the AppContainer, serves as a simple manual container: it gathers access to the preferences (§8.8), to the AppApiClient that communicates with the backend, to the accommodation's product catalogue and the restaurant catalogue (FoodCatalog), to the guest's cart (GuestCart) and to the card reader (CardTerminal, §8.5). The same container also holds the state of the current visit: the guest's email and notes, as well as the orders submitted since the guest started using the device. A visit ends when the application returns to the idle screen (§8.6), at which point this data is deleted, so that it is not carried over from one guest to the next.

## 8.2 The Device Activation Flow

At start-up, MainActivity reads the device's stored registration state (§8.8): if the device is not yet registered, the navigation graph starts from the Registration screen instead of the idle screen. This screen (Figure 8.2) and the corresponding RegistrationViewModel support two alternative activation routes (Figure 8.1).

On the first route, the application scans, through the ZXing library [35], the QR code that the owner shows in the web panel (§7.3). The scan result is passed from MainActivity to a shared MutableStateFlow in the AppContainer, which the ViewModel observes. The content of the QR code is a JSON object with the fields deviceId, activationCode, schemaName and endpoint; the application deserialises it with kotlinx.serialization into a local class that mirrors the structure the backend produces and, provided the required fields are not empty, goes straight on to activation, using the endpoint as the server's address.

On the second route, the user enters the server's address and the activation code manually. Because in this case the deviceId is not known, the application first calls GET /api/devices/resolve, which translates the code into a deviceId and a schemaName before the activation itself is called (§6.2).

![Figure 8.1: Device activation flow — QR scan or manual entry, resolve, activate, token issue and storage.](/media/fig-8-1-device-activation.png)

Both routes end up at the same point: a call to the activation endpoint, with Settings.Secure.ANDROID_ID as the device's identifier. The backend verifies the code, permanently links the device to this identifier and issues the device token (§6.2). If the activation is rejected, the application shows the reason: an unknown code (404), a wrong code (403) or, for 409, the reason the backend returns (an already activated device, or a deactivated property or owner). On receiving the response, the application stores the registration details, including the token in encrypted form (§8.8), and tries to preload the product catalogue and the accommodation's details, so that the first screen after activation does not appear empty. These preloads are not critical: if they fail, the application repeats them on every visit to the home screen.

The token accompanies, as an Authorization: Bearer header, every subsequent request from the application. If the backend answers with 401, for example because the owner disconnected the device, the application deletes the local registration details and the navigation graph returns automatically to the Registration screen, instead of staying on a screen that fails silently on every call. A 403 (§4.5.7) leaves the registration details in place, so the kiosk resumes once the device or the owner is reactivated. The same token is used by the heartbeat: while the application is running, the AppContainer sends a PATCH /devices/{id}/ping request every minute, which updates the last-contact indicator the owner sees (§5.3.2). Because the heartbeat runs even when nobody is using the device, a disconnection by the owner is noticed within a minute and the device returns to the registration screen without waiting for the next guest. A heartbeat that fails because of the network is simply repeated the next minute.

![Figure 8.2: The setup screen of the kiosk application.](/media/kiosk-registration.png)

## 8.3 Kiosk Mode through the Android LockTask API

The requirement of §3.2.5, that the guest must not be able to leave the application for other functions of the device, is implemented through the KioskManager, a thin wrapper around the Android LockTask API [23]. When the device is registered, MainActivity calls kioskManager.startKiosk(), which in turn calls the operating system's Activity.startLockTask(), locking the device to the current application.

Full locking presupposes that the device is managed by a device management application (Device Policy Controller, DPC) that has been set as Device Owner and has allowed the kiosk application to run in LockTask mode. This configuration is done once, when the device is first set up, and is independent of the application's code. On a device without such a policy, such as a development device, Android either falls back to screen pinning, which the user confirms and can undo, or refuses the call; the KioskManager deliberately ignores the failure and the application continues normally. The application thus stays functional and testable on devices without full management as well, while the full behaviour is activated automatically when a real installation provides for the corresponding provisioning.

To leave kiosk mode, for maintenance or reinstallation, the header bar of the application's screens, as well as the idle screen, contain an invisible area: five taps within three seconds open an admin PIN entry dialog, and entering the correct code calls kioskManager.stopKiosk() and closes the application. In the prototype this code is fixed, for the needs of the demonstration. A system event receiver (BroadcastReceiver) for the BOOT_COMPLETED event additionally attempts to start the application automatically after a restart of the device, satisfying the autonomy requirement of §3.2.5. From Android 10 onwards, the operating system restricts starting applications from the background, so this behaviour depends on the version and configuration of the device.

## 8.4 The Order Flow and Navigation

Navigation is implemented with Jetpack Navigation Compose, through a navigation graph (Figure 8.3) and a sealed class Screen that defines the available routes (Registration, Idle, Home, Stores, Store, Payment, OrderConfirmation, OrderTracking, Info). Each screen has its own ViewModel, and all share common state through the AppContainer.

![Figure 8.3: Screen flow of the guest — from the home screen to payment and order tracking.](/media/fig-8-2-kiosk-screens.png)

The home screen (Home) offers the guest three options: shopping, stay information and tracking their orders. The shopping option leads to the Stores screen (Figure 8.4), which first presents the accommodation's own store, with the number of available products, and then the restaurants of the delivery platforms offered at the accommodation, grouped by platform, with each restaurant's type of cuisine, rating, delivery cost, estimated delivery time and minimum order. A platform with no restaurants for the accommodation is not shown. The restaurants and menus are retrieved from the backend (§6.7) and kept in a cache, so that going back to a restaurant does not require a new call.

The Store screen is shared by the accommodation's store and by a restaurant (Figures 8.5 and 8.6): it shows the items grouped into categories and, next to them, the cart (CartPanel) with the subtotal, the delivery cost and the final amount. For a food order, the cart tells the guest the amount still short of the minimum order and does not allow completion until it is met. The GuestCart holds the items of one store at a time; if the guest adds an item from another store, the application asks for confirmation and empties the cart only if the guest agrees. This simplification reflects the fact that each order is paid for and fulfilled by a single party (the owner or one platform). The quantity of each item is limited to 99, as on the backend.

Once the items are chosen, the guest moves to the payment screen (§8.5), and after the approved payment to the confirmation screen (Figure 8.9), which summarises the order and leads on to tracking.

![Figure 8.4: Choosing a store.](/media/kiosk-stores.png)

![Figure 8.5: The in-room store with a cart.](/media/kiosk-cart.png)

![Figure 8.6: A restaurant menu with the delivery cost in the cart.](/media/kiosk-restaurant.png)

## 8.5 Card Reading and Payment

The payment screen (Figure 8.7) does not offer a choice of method: the guest's action with the card starts the payment. The PaymentViewModel first waits for a card from the device's reader. On the first card event it creates the order (POST /orders, §6.5), which comes back in the PENDING status with the amount the server calculated, and immediately afterwards sends the payment request. The order identifier is kept, so that a new attempt after a decline concerns the same order and no duplicate is created.

Access to the card reader is isolated behind the CardTerminal interface, following in the Android application the same port/adapter logic as the backend. Its awaitCard() method suspends execution until the guest taps, inserts or swipes a card, and returns an event of the sealed interface CardEvent: Tapped (contactless read), Inserted (chip insertion), Swiped (magnetic stripe, with the last four digits of the card when they can be read), ReadFailed (the card was not read) or Unavailable (the reader is not available). The implementation of the interface communicates, through Android's AIDL inter-process communication mechanism, with the card reader service provided by the device's manufacturer. Cancelling the wait, for example when the guest moves to another screen, also cancels the request to the reader, so that it does not stay active in the background.

The ViewModel translates each event into a payment request (POST /payments). For a contactless read and a magnetic stripe, the request is sent immediately. A chip insertion also requires cardholder verification with a PIN, which is normally done in the secure reader with the payment acquirer's keys; in the prototype it is simulated in the application (§9.4). The PIN is not stored, recorded or sent; only the result of the verification reaches the backend (§3.2.2).

The backend's response determines what happens next. An approval completes the visit to the store: the cart is emptied, the order is added to the current visit's orders and the application moves to the confirmation. A decline shows its reason for a few seconds and the wait for a card continues, so that the guest can try again. A food order that was charged but not accepted by the platform comes back as cancelled with a refund message (§6.6), which the application shows to the guest. A technical error (network, server) is distinguished from a card decline. Because such an error can happen after the card has already been charged, for example when the response is lost on the network or when a repeat is answered with "already paid", the ViewModel first asks the server for the status of the order: if it is now confirmed, the payment is shown as approved and the flow continues normally, if it has been cancelled the corresponding message is shown, and only if it remains unpaid is the guest asked to try again. In this way a guest who has already been charged does not see a failure message.

![Figure 8.7: The payment screen waiting for a card.](/media/kiosk-payment-waiting.png)

![Figure 8.8: The payment screen on a device without the card reader service.](/media/kiosk-payment-reader-unavailable.png)

## 8.6 Order Status Tracking and Inactivity

The tracking screen (Figures 8.10 and 8.11) shows all the orders of the current visit as cards, each with its status. The corresponding ViewModel retrieves the orders through GET /device/orders/{id}, every 5 seconds for food-delivery orders and every 15 seconds for in-room store orders, until all orders are final. The choice of polling instead of a push mechanism (such as WebSocket or Server-Sent Events) is consistent with the simplicity sought at this stage of development: on every check the application fetches the whole order and shows its current status. For food orders, the card additionally shows a countdown to the estimated arrival time reported by the platform (§6.7). While the preparation of an order has not started (status PENDING or CONFIRMED), its card shows a cancel button. Because a confirmed order has already been paid, cancelling first asks for confirmation with a dialog that states the amount that will be refunded, and then calls PATCH /device/orders/{id}/cancel; the backend applies the same rule (§6.5) and automatically asks the payment provider to refund the amount (§6.6). If the cancellation fails, for example because preparation started in the meantime or because the platform refused it, the guest is informed with a message.

Independently of the order flow, an inactivity wrapper (InactivityWrapper) surrounds the guest's main screens (home, stores, catalogue, payment, order tracking and property information) and watches every touch event. If no interaction is recorded for three minutes, the application returns to the idle screen, which marks the end of the visit and deletes the guest's data from memory (§8.1). This is a usability and privacy requirement that follows from the guest's frictionless experience (§3.2.3): a kiosk device must not stay on an intermediate screen, nor show one guest's data to the next.

The information screen (Figure 8.12) shows the accommodation's details as set by the owner (§7.6): the address, the Wi-Fi network and password, the check-out time, the house rules, the owner's phone number and the emergency numbers. The accommodation's details are refreshed on every visit to the home screen, so that a change by the owner appears without restarting the application. If the catalogue cannot be loaded, the home screen shows that the device is offline and displays the owner's phone number.

![Figure 8.9: Order confirmation.](/media/kiosk-confirmation.png)

![Figure 8.10: Order tracking, with the cancel button before the preparation starts.](/media/kiosk-tracking.png)

![Figure 8.11: Order tracking after the owner has started the preparation.](/media/kiosk-tracking-preparing.png)

![Figure 8.12: The accommodation information screen.](/media/kiosk-info.png)

## 8.7 Designing for Kiosk Operation

An application that runs permanently on a shared device, used by people seeing it for the first time, has requirements different from a personal mobile application. These requirements determined a series of choices at the level of the Android platform and the interface.

At the platform level, the application is declared in the manifest not only as a launcher application but also as the device's home screen (category HOME). If it is set as the default home screen, pressing the home button returns to the application itself instead of the Android home screen, which works as a complement to LockTask (§8.3) on devices without full management. The screen is locked to landscape orientation, and the system bars (status and navigation) are hidden every time the application gains focus, so that the guest sees only the application; they appear temporarily only with a swipe from the edge of the screen. The application requests only the permissions it needs: network access, the camera for scanning the QR code at activation, and receiving the device's boot event.

At the interface level, the accommodation's products and the restaurants' dishes are presented with the same component, thanks to a single model (BrowseItem) that represents "an item that can be ordered", regardless of its origin. In this way the store screen, the item card and the cart were written once for two very different data sources (the owner's catalogue and a platform's menu). The items are shown in a grid with an adaptive number of columns, so that the layout fills the screen regardless of the tablet's size. Product images are loaded asynchronously from their address with the Coil library, and where there is no image or loading fails, a category icon is shown, so that the grid has no gaps.

Some details arose from the nature of the device itself. The phone numbers on the information screen (the owner's and the emergency services') open the calling application where one exists; on a tablet without calling capability, the number is shown in a dialog in large letters, so that the guest can call it from their own mobile. The interface is written in English, as a common language for the international guests that short-term rental addresses; support for multiple languages was not implemented (§9.4). Finally, the application's network client ignores the fields of a response that it does not know, so that a newer version of the backend that adds fields stays compatible with devices already installed, and encodes every value embedded in a URL, since identifiers and codes come from QR codes or from typing.

## 8.8 Local Storage: DataStore and Encrypted Preferences

The application uses two independent storage areas, each dedicated to a different kind of state, with a level of security proportionate to the sensitivity of the data.

DevicePreferences stores the device's registration state (whether it is registered, the server's address, the device identifier, the tenant schema and the property) in a regular preferencesDataStore, with one exception: the device's authentication token is stored separately, in the EncryptedSharedPreferences of the AndroidX Security Crypto library, encrypted with an AES-256 key protected by a MasterKey. The other fields are not sensitive and benefit from DataStore's reactive API, while the token, as the device's only access credential, is stored encrypted on disk, so that physical access to a lost or stolen tablet is not enough on its own to extract it.

CartPreferences is an independent DataStore that stores, serialised as JSON, the items of the accommodation's store that are in the cart, so that they survive an unexpected shutdown of the application. The restaurants' dishes are kept only in memory, since their prices and availability depend on the platform and it makes no sense to keep them after a restart. In any case, the end of the visit (§8.6) empties the cart.

## 8.9 Independence from the Payment Provider

The kiosk application does not communicate directly with any payment acquirer: its card reader provides only the card events, and the approval decision is made in the backend through the PaymentGateway port (§6.6). Connecting to a real acquirer would therefore change only the backend's adapter (§4.4), and a device with a different card reader would need only a new implementation of CardTerminal (§8.5), with no change to the screens or the payment flow. On a device without the card reader service, the payment screen reports that the reader is not available (Figure 8.8), so payment, and therefore completing an order, is not possible (§9.4).
