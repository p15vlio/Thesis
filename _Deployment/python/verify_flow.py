"""End-to-end check of the integrated flow (report section 9.3) against a running stack, using only the public REST API.
Usage: python verify_flow.py [base_url]   (admin must exist: _Deployment/python/seed_admin.py)

The script creates its own test owner, property, devices, products and orders (and deactivates the owner at the end),
so run it against a separate copy of the stack rather than the demo database. Takes about 3 minutes, including a
65-second wait for the login rate-limit window. Exit code 0 when all 53 checks pass."""
import sys, time, uuid, json
import requests

BASE = (sys.argv[1] if len(sys.argv) > 1 else 'http://localhost:8080').rstrip('/')
ADMIN = ('admin@guestdesk.io', 'Admin@GuestDesk1')
results = []  # (group, name, ok, detail)
G = ['']


def group(name):
    G[0] = name


def call(method, path, token=None, body=None, params=None):
    h = {}
    if token:
        h['Authorization'] = 'Bearer ' + token
    r = requests.request(method, BASE + path, headers=h, json=body, params=params, timeout=30)
    try:
        data = r.json() if r.content else None
    except ValueError:
        data = None
    return r.status_code, data


def check(name, ok, detail=''):
    results.append((G[0], name, bool(ok), detail))
    print(('PASS ' if ok else 'FAIL ') + f'[{G[0]}] {name}' + ('' if ok else f'  -> {detail}'))


def expect(name, got, want, extra=None):
    status, data = got
    ok = status == want and (extra is None or extra(data))
    check(name, ok, f'HTTP {status}, body={json.dumps(data)[:160] if data is not None else None}, wanted {want}')
    return data


uniq = uuid.uuid4().hex[:8]
OWNER_EMAIL = f'e2e.{uniq}@example.com'

# ---------------------------------------------------------------- 1 Authentication
group('Authentication')
admin = expect('admin login', call('POST', '/api/auth/login', body={'email': ADMIN[0], 'password': ADMIN[1], 'role': 'PLATFORM_ADMIN'}), 200, lambda d: d['role'] == 'PLATFORM_ADMIN')
AT = admin['accessToken']
owner_body = {'companyName': f'E2E Rentals {uniq}', 'vatId': '123456789', 'streetName': 'Ermou', 'streetNo': '1', 'postalCode': '10563', 'city': 'Athens',
              'contactFirstName': 'Eve', 'contactLastName': 'Tester', 'contactEmail': OWNER_EMAIL, 'contactPhone': '+302100000000'}
created = call('POST', '/api/owners', AT, owner_body)
owner = created[1]
OWNER_ID, TEMP = owner['id'], owner['temporaryPassword']
ol = expect('owner login with the temporary password', call('POST', '/api/auth/login', body={'email': OWNER_EMAIL, 'password': TEMP, 'role': 'OWNER'}), 200)
OT, OR = ol['accessToken'], ol['refreshToken']
prop = call('POST', '/api/properties', OT, {'name': 'E2E Flat', 'streetName': 'Ermou', 'streetNo': '1', 'postalCode': '10563', 'city': 'Athens', 'nameOnDoorbell': 'E2E', 'contactPhone': '+302100000000', 'level': 2})[1]
PID = prop['id']
expect("reading the owner's properties", call('GET', '/api/properties', OT), 200, lambda d: any(p['id'] == PID for p in d))
p1 = call('POST', '/api/products', OT, {'name': 'Water', 'description': 'Bottle', 'category': 'BEVERAGE', 'basePrice': 150, 'imageUrl': None})[1]
p2 = call('POST', '/api/products', OT, {'name': 'Chips', 'description': 'Bag', 'category': 'SNACK', 'basePrice': 220, 'imageUrl': None})[1]
p3 = call('POST', '/api/products', OT, {'name': 'Cola', 'description': 'Can', 'category': 'BEVERAGE', 'basePrice': 180, 'imageUrl': None})[1]


def new_device(name):
    d = call('POST', f'/api/properties/{PID}/devices', OT, {'name': name})
    return d


def activate(dev):
    r = call('GET', '/api/devices/resolve', params={'code': dev['activationCode']})
    a = call('POST', f"/api/devices/{dev['id']}/activate", params={'schemaName': r[1]['schemaName']}, body={'activationCode': dev['activationCode'], 'androidDeviceId': 'e2e-' + uniq})
    return r, a


# ---------------------------------------------------------------- 2 Device activation and lifetime
group('Device activation and lifetime')
st, dev = new_device('Tablet A')
check('creating a device returns an activation code and a QR code', st == 201 and dev.get('activationCode') and dev.get('qrCodeBase64'), st)
r, a = activate(dev)
check('resolving the activation code gives the device id and the tenant schema', r[0] == 200 and r[1]['deviceId'] == dev['id'] and r[1]['schemaName'].startswith('tenant_'), r)
check('activation returns the one-year device token', a[0] == 200 and a[1].get('token'), a)
DT = a[1]['token']
expect('device reads the catalogue', call('GET', '/api/products', DT), 200, lambda d: len(d) == 3)
expect('device reads its property', call('GET', f'/api/properties/{PID}', DT), 200)
expect('heartbeat with the device token', call('PATCH', f"/api/devices/{dev['id']}/ping", DT), 204)
expect('last contact is recorded', call('GET', f"/api/devices/{dev['id']}", OT), 200, lambda d: d['lastSeenAt'] is not None)
expect('heartbeat without a token is rejected', call('PATCH', f"/api/devices/{dev['id']}/ping"), 401)
expect("heartbeat with an owner's token is rejected", call('PATCH', f"/api/devices/{dev['id']}/ping", OT), 403)
expect('owner deactivates the device', call('PATCH', f"/api/devices/{dev['id']}/deactivate", OT), 204)
expect('the deactivated device token is refused (403, registration kept)', call('GET', '/api/products', DT), 403)

# a fresh device for the order flows
st, dev2 = new_device('Tablet B')
r, a = activate(dev2)
DT = a[1]['token']
DEV2 = dev2['id']

# ---------------------------------------------------------------- 3 Order and payment
group('Order and payment')


def order(token, items, **kw):
    body = {'guestEmail': None, 'type': 'IN_ROOM_STORE', 'items': items, 'notes': None}
    body.update(kw)
    return call('POST', '/api/orders', token, body, params={'propertyId': PID})


def pay(token, order_id, mode='CARD_CONTACTLESS', pin=None):
    b = {'orderId': order_id, 'entryMode': mode}
    if pin is not None:
        b['pinVerified'] = pin
    return call('POST', '/api/payments', token, b)


o = expect('order amount is calculated by the server', order(DT, [{'productId': p1['id'], 'quantity': 2}, {'productId': p2['id'], 'quantity': 1}]), 201,
           lambda d: d['totalAmount'] == 2 * 150 + 220 and d['status'] == 'PENDING')
O1 = o['id']
pr = expect('payment is approved', pay(DT, O1), 201, lambda d: d['status'] == 'COMPLETED')
expect('the order becomes CONFIRMED', call('GET', f'/api/device/orders/{O1}', DT), 200, lambda d: d['status'] == 'CONFIRMED')
expect('a second payment for the same order is rejected', pay(DT, O1), 409)
expect('owner disables a product at the property', call('PATCH', f'/api/products/{p3["id"]}/properties/{PID}/availability', OT, {'isAvailable': False}), 200)
expect('an order for the disabled product is rejected', order(DT, [{'productId': p3['id'], 'quantity': 1}]), 409)
expect('an order from an owner account is rejected', order(OT, [{'productId': p1['id'], 'quantity': 1}]), 403)
o2 = order(DT, [{'productId': p1['id'], 'quantity': 1}])[1]
expect('a declined chip payment (wrong PIN) is recorded and the order stays PENDING', pay(DT, o2['id'], 'CARD_CHIP', False), 201, lambda d: d['status'] == 'FAILED')
expect('...the order is still PENDING', call('GET', f"/api/device/orders/{o2['id']}", DT), 200, lambda d: d['status'] == 'PENDING')

# ---------------------------------------------------------------- 4 Cancellations and refunds
group('Cancellations and refunds')
expect('owner advances to PREPARING', call('PATCH', f'/api/orders/{O1}/status', OT, {'status': 'PREPARING'}), 200, lambda d: d['status'] == 'PREPARING')
expect('cancelling from the device during preparation is rejected', call('PATCH', f'/api/device/orders/{O1}/cancel', DT), 409)
expect('owner cancels the prepared order', call('PATCH', f'/api/orders/{O1}/status', OT, {'status': 'CANCELLED'}), 200, lambda d: d['status'] == 'CANCELLED')
expect('...and its payment is refunded', call('GET', f'/api/orders/{O1}/payments', OT), 200, lambda d: d[0]['status'] == 'REFUNDED')
o3 = order(DT, [{'productId': p2['id'], 'quantity': 1}])[1]
pay(DT, o3['id'])
expect('guest cancels from the device before preparation', call('PATCH', f"/api/device/orders/{o3['id']}/cancel", DT), 200, lambda d: d['status'] == 'CANCELLED')
expect('...and its payment is refunded', call('GET', f"/api/orders/{o3['id']}/payments", OT), 200, lambda d: d[0]['status'] == 'REFUNDED')

# ---------------------------------------------------------------- 5 Delivery platforms
group('Delivery platforms')
expect('owner connects a platform', call('PUT', '/api/food-platforms/EFOOD', OT, {'apiKey': 'e2e-demo-key-1234'}), 200, lambda d: d['connected'] is True)
expect('owner enables it at the property', call('PUT', f'/api/properties/{PID}/food-platforms', OT, {'platforms': ['EFOOD']}), 200)
rest = expect('device lists restaurants', call('GET', '/api/food/restaurants', DT), 200, lambda d: len(d) > 0)
R = rest[0]
menu = expect('device reads a menu', call('GET', f"/api/food/restaurants/{R['platform']}/{R['id']}/menu", DT), 200, lambda d: len(d) > 0)
items = []
total = 0
for m in menu:
    q = 1
    items.append({'productId': m['id'], 'quantity': q})
    total += m['priceCents'] * q
    if R.get('minOrderCents') is None or total >= R['minOrderCents']:
        break
fo = expect('a food order is priced from the menu plus delivery', order(DT, items, type='FOOD_DELIVERY', platform=R['platform'], restaurantId=R['id']), 201,
            lambda d: d['totalAmount'] == total + R['deliveryFeeCents'] and d['status'] == 'PENDING')
FO = fo['id']
expect('the food order payment is approved', pay(DT, FO), 201, lambda d: d['status'] == 'COMPLETED')
delivered = False
for _ in range(60):
    s = call('GET', f'/api/device/orders/{FO}', DT)[1]['status']
    if s == 'DELIVERED':
        delivered = True
        break
    time.sleep(3)
check('the platform advances the order autonomously up to DELIVERED', delivered, s)
expect('the owner cannot advance a food order manually', call('PATCH', f'/api/orders/{FO}/status', OT, {'status': 'PREPARING'}), 409)

group('Order list filters')
prop2 = call('POST', '/api/properties', OT, {'name': 'E2E Flat 2', 'streetName': 'Ermou', 'streetNo': '2', 'postalCode': '10563', 'city': 'Athens', 'nameOnDoorbell': 'E2E2', 'contactPhone': '+302100000000', 'level': 1})[1]
allp = call('GET', '/api/orders', OT, params={'propertyId': PID})
expect('orders of property 1 exist', allp, 200, lambda d: len(d) > 0)
st = allp[1][0]['status']
expect('status + property 1 returns only property 1 orders', call('GET', '/api/orders', OT, params={'propertyId': PID, 'status': st}), 200, lambda d: len(d) > 0 and all(o['propertyId'] == PID for o in d))
expect('status + property 2 returns nothing', call('GET', '/api/orders', OT, params={'propertyId': prop2['id'], 'status': st}), 200, lambda d: d == [])
expect('an unknown JSON field is rejected with 400', call('POST', '/api/products', OT, {'name': 'X', 'description': 'x', 'category': 'SNACK', 'basePrice': 1, 'imageUrl': None, 'bogus': 1}), 400)

# ---------------------------------------------------------------- 6 Accounts and passwords
group('Accounts and passwords')
expect('changing the password with a wrong current password', call('POST', '/api/auth/change-password', OT, {'currentPassword': 'nope-nope', 'newPassword': 'NewPassw0rd!'}), 400)
other = call('POST', '/api/auth/login', body={'email': OWNER_EMAIL, 'password': TEMP, 'role': 'OWNER'})[1]  # a second session
chg = expect('a successful password change returns new tokens', call('POST', '/api/auth/change-password', OT, {'currentPassword': TEMP, 'newPassword': 'NewPassw0rd!'}), 200, lambda d: d.get('accessToken'))
OT2 = chg['accessToken']
expect('the other session can no longer refresh', call('POST', '/api/auth/refresh', body={'refreshToken': other['refreshToken']}), 401)
expect('login with the new password', call('POST', '/api/auth/login', body={'email': OWNER_EMAIL, 'password': 'NewPassw0rd!', 'role': 'OWNER'}), 200)
rs = expect('administrator resets the password', call('PATCH', f'/api/owners/{OWNER_ID}/reset-password', AT), 200, lambda d: d.get('temporaryPassword'))
ln = expect('login with the temporary password', call('POST', '/api/auth/login', body={'email': OWNER_EMAIL, 'password': rs['temporaryPassword'], 'role': 'OWNER'}), 200)
OT3 = ln['accessToken']
expect('administrator deactivates the owner', call('PATCH', f'/api/owners/{OWNER_ID}/deactivate', AT), 204)
expect("the owner's access token is rejected on the very next request", call('GET', '/api/properties', OT3), 401)
expect('a deactivated owner cannot log in', call('POST', '/api/auth/login', body={'email': OWNER_EMAIL, 'password': rs['temporaryPassword'], 'role': 'OWNER'}), 401)

# ---------------------------------------------------------------- 7 Input security
group('Input security')
print('waiting 65 s so the public rate-limit window is clean ...')
time.sleep(65)
expect('a malicious schema name is rejected', call('POST', f"/api/devices/{dev['id']}/activate", params={'schemaName': 'public"; DROP SCHEMA public CASCADE; --'}, body={'activationCode': 'AAAAAAAA', 'androidDeviceId': 'x'}), 400)
expect('an invalid activation code is rejected', call('GET', '/api/devices/resolve', params={'code': 'ZZZZZZZZ'}), 404)
codes = [call('POST', '/api/auth/login', body={'email': 'nobody@example.com', 'password': 'x', 'role': 'OWNER'})[0] for _ in range(30)]
check('repeated login attempts are rate limited (429)', 429 in codes, sorted(set(codes)))

# ---------------------------------------------------------------- summary
print('\n==== SUMMARY ====')
groups = {}
for g, n, ok, d in results:
    groups.setdefault(g, [0, 0])
    groups[g][0 if ok else 1] += 1
for g, (p, f) in groups.items():
    print(f'{g:34} pass={p:2} fail={f}')
tp = sum(v[0] for v in groups.values())
tf = sum(v[1] for v in groups.values())
print(f'TOTAL checks={tp + tf} pass={tp} fail={tf}')
sys.exit(1 if tf else 0)
