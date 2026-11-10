# libs

`reader-sdk.jar` is the third-party AIDL client library of the device's built-in card reader service. It is not authored by this project, and the app does not compile without it. Its Java package names are those of the reader service and cannot be changed, because the service checks them on every call.

The app only uses it when the service is installed: on a tablet without it, the card reader is reported as unavailable and the payment screen says so.
