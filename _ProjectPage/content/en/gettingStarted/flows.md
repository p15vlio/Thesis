These are the main scenarios, in the order in which a real deployment goes through them. The screenshots come from the running system with the demo data.

## Owner onboarding

The platform administrator opens **Owners** and chooses **New Owner**. Once the VAT number is typed in and the search is chosen, the company name, trade name, legal form, activity and address are filled in from the public company registry, so only the contact person has to be added. If the registry does not answer, or no key has been set, the fields are filled in by hand.

![The list of owners](/media/web-admin-owners.png)

![The create-owner form, starting from the VAT number](/media/web-admin-owner-new.png)

When the owner is saved, the backend creates a dedicated database schema, generates a temporary password and sends it by email. A dialog shows the password once, in case the message does not arrive. The administrator can also view any owner's data, from the icon on the owner's card, and read the platform's administrative actions in the **Audit Logs**.

![The audit log of administrative actions](/media/web-admin-audit-logs.png)

## Property and device

The owner signs in, changes the temporary password and adds a property. The address is found with a search, which fills in the street, number, postal code, area, city and coordinates. The same form holds the information that the guest will see on the tablet, such as the Wi-Fi network, the check-out time and the house rules, as well as the delivery platforms that the property offers.

![Editing a property](/media/web-owner-property-edit.png)

![Guest information and platforms in the property form](/media/web-owner-property-guest-info.png)

Under **Devices** the owner adds a tablet. The dialog shows a QR code and an eight-character activation code. The activation code works once, and the dialog asks for it to stay open until the tablet has used it.

![Adding a device: the QR code and the activation code](/media/web-owner-device-qr.png)

Each activated device shows when it was last in contact, which the tablet refreshes every minute. A device can be deactivated at any time, and every following request from it is rejected.

![The devices of a property](/media/web-owner-devices.png)

## Tablet activation

On first start the tablet shows the setup screen, which is meant for the owner or the installer and not for the guest. There the QR code is scanned, or the server address and the activation code are typed in. The backend checks the code, deletes it and returns a one-year device token, which the app stores encrypted. After that the tablet shows the property's name and waits for a guest.

![Setup screen of the kiosk app](/media/kiosk-registration.png)

![The idle screen](/media/kiosk-idle.png)

## Ordering by the guest

The guest touches the screen and chooses **Order Now**. The first screen shows the property's own store and, grouped by platform, the restaurants that the owner has enabled, with cuisine, rating, delivery cost, estimated time and minimum order.

![The home screen](/media/kiosk-home.png)

![Choosing a store](/media/kiosk-stores.png)

The store shows the products that are available at this property, in categories. The cart is on the right and takes items from one store at a time.

![The in-room store with a cart](/media/kiosk-cart.png)

A restaurant works the same way. The cart adds the delivery cost and warns the guest until the minimum order is reached.

![A restaurant menu with its delivery cost in the cart](/media/kiosk-restaurant.png)

On the payment screen the guest can leave an email address for the receipt and a note, and the screen asks for a card. As soon as the guest uses the card, the backend creates the order, calculates its amount and returns it as `PENDING`, and the application immediately sends the payment request. Approving the payment is the payment provider's job; once it is approved, the order is confirmed and the receipt is sent by email. After a decline, the guest can try again with the same order.

![Order placed](/media/kiosk-confirmation.png)

The tracking screen shows the status of the order. It checks the backend every 5 seconds for a food order and every 15 seconds for an order from the in-room store, and stops once the order has reached a final status. The guest can cancel it until the preparation starts.

![Tracking: confirmed, with the cancel button](/media/kiosk-tracking.png)

![Tracking: the owner has started the preparation](/media/kiosk-tracking-preparing.png)

The guest can also read the stay information at any time: the address, Wi-Fi, check-out time, house rules and emergency numbers.

![Property information](/media/kiosk-info.png)

## Order tracking

The owner's dashboard counts the orders by status. The **Orders** list refreshes every 15 seconds and can be filtered by property and status. An order shows its lines, the delivery cost, the payment attempts and, for a food order, the platform and the restaurant.

![The owner's orders](/media/web-owner-orders.png)

![An order with its lines and its payment](/media/web-owner-order-detail.png)

For the property's own products the owner moves the order forward one step at a time. A food order is advanced by the platform, so the owner can only cancel it. Whenever a paid order is cancelled, by the owner or by the guest, the system asks the payment provider to refund the amount.

![Updating the status of an order](/media/web-owner-order-update.png)

## Catalogue and delivery platforms

The owner builds the catalogue by hand or imports products from the catalogue of a connected platform. A product can be switched off for all properties or for a single one.

![The owner's products](/media/web-owner-products.png)

![Importing products from a platform](/media/web-owner-product-import.png)

![Connecting the delivery platforms](/media/web-owner-food-delivery.png)

## Platform settings

The administrator configures the services that concern the whole platform. Secret values are stored encrypted and are not shown again; the screen only tells whether they have been set.

![Platform settings](/media/web-admin-settings.png)
