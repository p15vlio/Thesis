#!/usr/bin/env python3
"""
GuestDesk - Demo Data Seeder
============================
Creates 5 demo owners with 2-5 properties each (18 properties in total). Each owner gets one
catalogue of 20 products, shared by all of its properties; at every property 4 of those products
are switched off (per-property availability), so each property shows 20 products, 4 of them
unavailable. Also creates one separate test owner (one property, 20 products) with a KNOWN, fixed
password for the demo quick login and for manual/e2e testing.

Idempotent: an owner whose e-mail already exists is skipped, so the script is safe to re-run
(start-all runs it on every start). To recreate everything, reset the database volume
(manage reset) and seed again.

Requires the backend running on localhost:8080 and the Platform Admin created by seed_admin.py.

Requirements:
    pip install -r requirements.txt      (requests, bcrypt, psycopg2-binary)

Usage:
    python seed_data.py
    python seed_data.py --test-owner-only    # only the test owner

Environment variables (all optional):
    BASE_URL         (default: http://localhost:8080)
    ADMIN_EMAIL      (default: admin@guestdesk.io)
    ADMIN_PASS       (default: Admin@GuestDesk1)
    TEST_OWNER_EMAIL (default: owner@guestdesk.io)
    TEST_OWNER_PASS  (default: Owner@GuestDesk1)
    SEED_WAIT_SECONDS (default: 120) how long to wait for the backend
    DB_HOST / DB_PORT / DB_NAME / DB_USER / DB_PASSWORD - used to check which owners exist and to set
        the test owner's fixed password directly (same as seed_admin.py); defaults match the Docker setup
"""

import os
import sys
sys.stdout.reconfigure(errors="replace")  # server messages may contain characters a cp1253 console cannot print
import random
import time

try:
    import requests
except ImportError:
    sys.exit("Missing dependency: run  pip install -r requirements.txt  (package: requests)")

try:
    import bcrypt
except ImportError:
    sys.exit("Missing dependency: run  pip install -r requirements.txt  (package: bcrypt)")

try:
    import psycopg2
except ImportError:
    sys.exit("Missing dependency: run  pip install -r requirements.txt  (package: psycopg2-binary)")

BASE_URL    = os.getenv("BASE_URL",    "http://localhost:8080")
ADMIN_EMAIL = os.getenv("ADMIN_EMAIL", "admin@guestdesk.io")
ADMIN_PASS  = os.getenv("ADMIN_PASS",  "Admin@GuestDesk1")

# Direct DB access is used to check which owners already exist and to give the test owner a known,
# fixed password: the /api/owners endpoint always mints a random temporary one (by design, for the
# realistic demo owners), so a stable login for manual/e2e testing has to be set this way, as
# seed_admin.py does for the platform admin.
DB_HOST     = os.getenv("DB_HOST",     "localhost")
DB_PORT     = int(os.getenv("DB_PORT", "5432"))
DB_NAME     = os.getenv("DB_NAME",     "opendelivery")
DB_USER     = os.getenv("DB_USER",     "postgres")
DB_PASSWORD = os.getenv("DB_PASSWORD", "opendelivery")

TEST_OWNER_EMAIL = os.getenv("TEST_OWNER_EMAIL", "owner@guestdesk.io")
TEST_OWNER_PASS  = os.getenv("TEST_OWNER_PASS",  "Owner@GuestDesk1")
BCRYPT_COST = 12  # Must match PasswordHasher.kt

PRODUCTS_PER_OWNER = 20         # one catalogue per owner, shared by its properties
UNAVAILABLE_PER_PROPERTY = 4    # catalogue products switched off at each property
WAIT_SECONDS = int(os.getenv("SEED_WAIT_SECONDS", "120"))

# -- Product catalog -----------------------------------------------------------
# (name, description, category, basePrice_cents)

CATALOG = [
    ("Still Water 500ml",           "Cold still mineral water, 500 ml bottle",                    "BEVERAGE", 150),
    ("Sparkling Water 500ml",       "Chilled sparkling mineral water, 500 ml bottle",              "BEVERAGE", 150),
    ("Orange Juice 250ml",          "Fresh-pressed orange juice carton",                           "BEVERAGE", 220),
    ("Energy Drink 250ml",          "Chilled energy drink",                                        "BEVERAGE", 280),
    ("Iced Coffee 330ml",           "Ready-to-drink cold-brew coffee",                             "BEVERAGE", 320),
    ("Greek Coffee Pods (×2)",      "Nespresso-compatible Greek mountain coffee pods",             "HOT_DRINK", 250),
    ("Hot Chocolate Sachet",        "Premium hot chocolate powder sachet",                         "HOT_DRINK", 190),
    ("Green Tea Bags (×3)",         "Organic green tea bags",                                      "HOT_DRINK", 150),
    ("Potato Chips 40g",            "Classic salted chips",                                        "SNACK", 180),
    ("Mixed Nuts 80g",              "Roasted mixed nuts — cashews, almonds, peanuts",              "SNACK", 320),
    ("Dark Chocolate 100g",         "70% cacao dark chocolate bar",                                "SNACK", 290),
    ("Honey & Sesame Bar",          "Traditional Greek sesame and honey snack bar",                "SNACK", 199),
    ("Greek Crackers 80g",          "Whole-grain crackers with olive oil",                         "SNACK", 240),
    ("Pita Chips 50g",              "Baked pita chips with sea salt",                              "SNACK", 210),
    ("Toothbrush Set",              "Soft toothbrush and mini toothpaste",                         "PERSONAL_CARE", 350),
    ("Shampoo Sachet 30ml",         "Hotel-size shampoo sachet",                                   "PERSONAL_CARE", 220),
    ("Sunscreen SPF50 50ml",        "Water-resistant sun protection SPF 50",                       "PERSONAL_CARE", 890),
    ("Shower Cap",                  "Disposable shower cap",                                       "PERSONAL_CARE", 120),
    ("Insect Repellent Spray 100ml","DEET-free insect repellent spray",                            "PERSONAL_CARE", 590),
    ("USB-C Cable 1m",              "Braided USB-C to USB-C charging cable",                       "OTHER", 790),
    ("Universal Travel Adapter",    "Universal plug adapter — EU / UK / US / AU",                 "OTHER", 1290),
    ("Power Bank 5000mAh",          "Portable USB-A + USB-C power bank",                          "OTHER", 1990),
    ("Paracetamol 500mg (10 tabs)", "Pain and fever relief tablets",                               "PHARMACY", 450),
    ("Antacid Tablets (8 tabs)",    "Fast-acting antacid relief tablets",                          "PHARMACY", 380),
    ("Blister Plasters (6 pack)",   "Hydrocolloid blister plasters",                              "PHARMACY", 299),
    ("After-Sun Lotion 100ml",      "Soothing after-sun moisturising lotion",                      "PHARMACY", 690),
    ("Greek Red Wine 750ml",        "Local label dry red wine",                                    "ALCOHOL", 1490),
    ("Greek White Wine 750ml",      "Local label crisp white wine",                                "ALCOHOL", 1390),
    ("Rosé Wine 750ml",             "Local label dry rosé wine",                                   "ALCOHOL", 1490),
    ("Greek Beer 330ml",            "Chilled local lager beer",                                    "ALCOHOL", 280),
]

# -- Owner definitions ---------------------------------------------------------

OWNERS = [
    {
        "companyName": "Papadopoulos Hospitality I.K.E.",
        "distinctiveTitle": "Syntagma Stays",
        "vatId": "123456789",
        "legalForm": "ΙΚΕ",
        "contactFirstName": "Alexandros",
        "contactLastName": "Papadopoulos",
        "contactEmail": "a.papadopoulos@guestdesk-demo.io",
        "contactPhone": "+306971000001",
        "companyActivity": "Short-term rental — Athens",
        "streetName": "Ermou", "streetNo": "15", "postalCode": "10563", "city": "Athens",
        "properties": [
            {
                "name": "Syntagma Studio",
                "streetName": "Mitropoleos", "streetNo": "8", "postalCode": "10557",
                "area": "Syntagma", "level": 3, "nameOnDoorbell": "Papadopoulos",
                "contactPhone": "+306971000001", "city": "Athens",
            },
            {
                "name": "Koukaki Apartment",
                "streetName": "Veikou", "streetNo": "22", "postalCode": "11742",
                "area": "Koukaki", "level": 1, "nameOnDoorbell": "Papadopoulos",
                "contactPhone": "+306971000001", "city": "Athens",
            },
            {
                "name": "Psirri Loft",
                "streetName": "Aristofanous", "streetNo": "5", "postalCode": "10554",
                "area": "Psirri", "level": 4, "nameOnDoorbell": "Papadopoulos",
                "contactPhone": "+306971000001", "city": "Athens",
            },
        ],
    },
    {
        "companyName": "Georgiou Rentals I.K.E.",
        "distinctiveTitle": "Thessaloniki Suites",
        "vatId": "234567891",
        "legalForm": "ΙΚΕ",
        "contactFirstName": "Eleni",
        "contactLastName": "Georgiou",
        "contactEmail": "e.georgiou@guestdesk-demo.io",
        "contactPhone": "+306932000002",
        "companyActivity": "Short-term rental — Thessaloniki",
        "streetName": "Tsimiski", "streetNo": "44", "postalCode": "54623", "city": "Thessaloniki",
        "properties": [
            {
                "name": "City Center Suite",
                "streetName": "Tsimiski", "streetNo": "44", "postalCode": "54623",
                "area": "Center", "level": 2, "nameOnDoorbell": "Georgiou",
                "contactPhone": "+306932000002", "city": "Thessaloniki",
            },
            {
                "name": "Ladadika Boutique",
                "streetName": "Katouni", "streetNo": "7", "postalCode": "54625",
                "area": "Ladadika", "level": 0, "nameOnDoorbell": "Georgiou",
                "contactPhone": "+306932000002", "city": "Thessaloniki",
            },
        ],
    },
    {
        "companyName": "Hellenic Stays Ltd",
        "vatId": "800123456",
        "legalForm": "ΕΠΕ",
        "contactFirstName": "Nikos",
        "contactLastName": "Stavros",
        "contactEmail": "info@hellenicstays-demo.io",
        "contactPhone": "+302241000003",
        "companyActivity": "Property management — Rhodes",
        "streetName": "Ippotou", "streetNo": "18", "postalCode": "85100", "city": "Rhodes",
        "website": "https://hellenicstays-demo.io",
        "properties": [
            {
                "name": "Rhodes Old Town Villa",
                "streetName": "Ippotou", "streetNo": "18", "postalCode": "85100",
                "area": "Old Town", "level": 0, "nameOnDoorbell": "Hellenic Stays",
                "contactPhone": "+302241000003", "city": "Rhodes",
            },
            {
                "name": "Lindos Sea View",
                "streetName": "Akropolis", "streetNo": "3", "postalCode": "85107",
                "area": "Lindos", "level": 1, "nameOnDoorbell": "Hellenic Stays",
                "contactPhone": "+302241000003", "city": "Rhodes",
            },
            {
                "name": "Ixia Beach Studio",
                "streetName": "Ethnikis Antistaseos", "streetNo": "120", "postalCode": "85101",
                "area": "Ixia", "level": 0, "nameOnDoorbell": "Hellenic Stays",
                "contactPhone": "+302241000003", "city": "Rhodes",
            },
            {
                "name": "Diagoras Airport Suite",
                "streetName": "Paradisi", "streetNo": "1", "postalCode": "85102",
                "area": "Paradisi", "level": 2, "nameOnDoorbell": "Hellenic Stays",
                "contactPhone": "+302241000003", "city": "Rhodes",
            },
            {
                "name": "Faliraki Summer Flat",
                "streetName": "Elpidas", "streetNo": "9", "postalCode": "85105",
                "area": "Faliraki", "level": 1, "nameOnDoorbell": "Hellenic Stays",
                "contactPhone": "+302241000003", "city": "Rhodes",
            },
        ],
    },
    {
        "companyName": "Nikolaou Island Homes I.K.E.",
        "distinctiveTitle": "Mykonos Town Homes",
        "vatId": "345678912",
        "legalForm": "ΙΚΕ",
        "contactFirstName": "Konstantinos",
        "contactLastName": "Nikolaou",
        "contactEmail": "k.nikolaou@guestdesk-demo.io",
        "contactPhone": "+302289000004",
        "companyActivity": "Short-term rental — Mykonos",
        "streetName": "Fabrika", "postalCode": "84600", "city": "Mykonos",
        "properties": [
            {
                "name": "Mykonos Town Suite",
                "streetName": "Matoyianni", "streetNo": "14", "postalCode": "84600",
                "area": "Town", "level": 1, "nameOnDoorbell": "Nikolaou",
                "contactPhone": "+302289000004", "city": "Mykonos",
            },
            {
                "name": "Little Venice Balcony",
                "streetName": "Agiou Anargirou", "streetNo": "2", "postalCode": "84600",
                "area": "Little Venice", "level": 0, "nameOnDoorbell": "Nikolaou",
                "contactPhone": "+302289000004", "city": "Mykonos",
            },
            {
                "name": "Ornos Beach Apartment",
                "streetName": "Ornos", "streetNo": "5", "postalCode": "84600",
                "area": "Ornos", "level": 0, "nameOnDoorbell": "Nikolaou",
                "contactPhone": "+302289000004", "city": "Mykonos",
            },
            {
                "name": "Psarou Hideaway",
                "streetName": "Psarou", "streetNo": "1", "postalCode": "84600",
                "area": "Psarou", "level": 0, "nameOnDoorbell": "Nikolaou",
                "contactPhone": "+302289000004", "city": "Mykonos",
            },
        ],
    },
    {
        "companyName": "Santorini Dreams LLC",
        "vatId": "900456123",
        "legalForm": "ΙΚΕ",
        "contactFirstName": "Maria",
        "contactLastName": "Economou",
        "contactEmail": "reservations@santorinidreams-demo.io",
        "contactPhone": "+302286000005",
        "companyActivity": "Luxury rental management — Santorini",
        "streetName": "Oia", "postalCode": "84702", "city": "Santorini",
        "website": "https://santorinidreams-demo.io",
        "properties": [
            {
                "name": "Caldera View Suite",
                "streetName": "Oia", "streetNo": "12", "postalCode": "84702",
                "area": "Oia", "level": 0, "nameOnDoorbell": "Santorini Dreams",
                "contactPhone": "+302286000005", "city": "Santorini",
            },
            {
                "name": "Oia Sunset Villa",
                "streetName": "Oia", "streetNo": "25", "postalCode": "84702",
                "area": "Oia", "level": 1, "nameOnDoorbell": "Santorini Dreams",
                "contactPhone": "+302286000005", "city": "Santorini",
            },
            {
                "name": "Fira Boutique Apartment",
                "streetName": "Ypapantis", "streetNo": "8", "postalCode": "84700",
                "area": "Fira", "level": 2, "nameOnDoorbell": "Santorini Dreams",
                "contactPhone": "+302286000005", "city": "Santorini",
            },
            {
                "name": "Pyrgos Retreat",
                "streetName": "Pyrgos", "streetNo": "3", "postalCode": "84701",
                "area": "Pyrgos", "level": 0, "nameOnDoorbell": "Santorini Dreams",
                "contactPhone": "+302286000005", "city": "Santorini",
            },
        ],
    },
]

# A single, separate test owner with a KNOWN, fixed password (not the random temporary password the
# API normally mints), for the demo quick login and manual/e2e testing, kept distinct from the 5
# realistic demo owners above so it is obviously "the test account".
TEST_OWNER = {
    "companyName": "Test Owner Co",
    "vatId": "111111111",
    "contactFirstName": "Test",
    "contactLastName": "Owner",
    "contactEmail": TEST_OWNER_EMAIL,
    "contactPhone": "+306900000000",
    "companyActivity": "QA / end-to-end testing account",
    "streetName": "Test Address", "streetNo": "1", "postalCode": "00000", "city": "Test City",
    "properties": [
        {
            "name": "Test Property",
            "streetName": "Test Street", "streetNo": "1", "postalCode": "00000",
            "area": "Test Area", "level": 0, "nameOnDoorbell": "Test Owner",
            "contactPhone": "+306900000000", "city": "Test City",
        },
    ],
}


# -- HTTP helpers --------------------------------------------------------------

class SeedError(Exception):
    pass


def request(method: str, path: str, body: dict, token: str = "") -> dict:
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    for attempt in range(4):
        try:
            r = requests.request(method, f"{BASE_URL}{path}", json=body, headers=headers, timeout=30)
        except requests.RequestException as e:
            raise SeedError(f"{method} {path} failed: {e}. Is the backend running on {BASE_URL}?")
        if r.status_code == 429 and attempt < 3:
            # Login is rate limited (20 per minute per address): wait for the window to clear.
            print("  Rate limited by the backend (429), waiting 65 s ...")
            time.sleep(65)
            continue
        if not r.ok:
            raise SeedError(f"{method} {path} -> HTTP {r.status_code}: {r.text[:300]}")
        return r.json() if r.content else {}
    raise SeedError(f"{method} {path} kept returning 429 (rate limit)")


def post(path: str, body: dict, token: str = "") -> dict:
    return request("POST", path, body, token)


def patch(path: str, body: dict, token: str) -> dict:
    return request("PATCH", path, body, token)


# -- Seed logic ----------------------------------------------------------------

def login(email: str, password: str, role: str) -> str:
    resp = post("/api/auth/login", {"email": email, "password": password, "role": role, "schemaName": None})
    return resp["accessToken"]


def admin_login() -> str:
    print("Logging in as Platform Admin ...")
    try:
        token = login(ADMIN_EMAIL, ADMIN_PASS, "PLATFORM_ADMIN")
    except SeedError as e:
        raise SeedError(f"{e}\nThe Platform Admin login failed: run  python seed_admin.py  first "
                        f"(or set ADMIN_EMAIL / ADMIN_PASS).")
    print("  Admin token acquired.\n")
    return token


def create_owner(admin_token: str, owner: dict) -> dict:
    payload = {k: v for k, v in owner.items() if k != "properties"}
    return post("/api/owners", payload, admin_token)


def create_property(owner_token: str, prop: dict) -> dict:
    return post("/api/properties", prop, owner_token)


def create_product(owner_token: str, entry: tuple) -> dict:
    name, description, category, base_price = entry
    return post("/api/products", {
        "name": name,
        "description": description,
        "category": category,
        "basePrice": base_price,
        "imageUrl": None,
    }, owner_token)


def disable_at_property(owner_token: str, product: dict, property_id: str) -> None:
    patch(f"/api/products/{product['id']}/properties/{property_id}/availability", {"isAvailable": False}, owner_token)


def db_connect():
    try:
        return psycopg2.connect(host=DB_HOST, port=DB_PORT, dbname=DB_NAME, user=DB_USER, password=DB_PASSWORD,
                                connect_timeout=10)
    except psycopg2.OperationalError as e:
        raise SeedError(f"Cannot connect to PostgreSQL at {DB_HOST}:{DB_PORT}/{DB_NAME}: {str(e).strip()}")


def owner_exists(email: str) -> bool:
    conn = db_connect()
    try:
        cur = conn.cursor()
        cur.execute("SELECT 1 FROM owners WHERE lower(contact_email) = lower(%s)", (email,))
        return cur.fetchone() is not None
    finally:
        conn.close()


def set_known_owner_password(email: str, password: str) -> None:
    conn = db_connect()
    try:
        cur = conn.cursor()
        hashed = bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt(rounds=BCRYPT_COST)).decode("utf-8")
        cur.execute("UPDATE owners SET password_hash = %s WHERE contact_email = %s", (hashed, email))
        conn.commit()
    finally:
        conn.close()


def seed_owner(admin_token: str, owner_def: dict, rng: random.Random, unavailable: int) -> tuple:
    """Creates one owner, its properties and its catalogue. Returns (properties, products)."""
    owner_resp = create_owner(admin_token, owner_def)
    temp_password = owner_resp["temporaryPassword"]
    owner_email = owner_resp["contactEmail"]
    print(f"  Created - email: {owner_email}  temporary password: {temp_password}")
    owner_token = login(owner_email, temp_password, "OWNER")

    # One catalogue per owner, shared by all of its properties.
    catalogue = [create_product(owner_token, entry) for entry in rng.sample(CATALOG, PRODUCTS_PER_OWNER)]
    print(f"  Catalogue: {len(catalogue)} products")

    for prop_def in owner_def["properties"]:
        prop_id = create_property(owner_token, prop_def)["id"]
        off = rng.sample(catalogue, unavailable) if unavailable else []
        for product in off:
            disable_at_property(owner_token, product, prop_id)
        print(f"  Property '{prop_def['name']}': {len(catalogue) - len(off)} available, {len(off)} unavailable")
    return owner_token, len(owner_def["properties"]), len(catalogue)


def seed_test_owner(admin_token: str) -> bool:
    print(f"Test owner ({TEST_OWNER_EMAIL}) ...")
    if owner_exists(TEST_OWNER_EMAIL):
        print("  Already exists - skipped.\n")
        return False
    seed_owner(admin_token, TEST_OWNER, random.Random(TEST_OWNER["vatId"]), 0)
    # Fix the password only after every owner-authenticated setup call above has completed.
    set_known_owner_password(TEST_OWNER_EMAIL, TEST_OWNER_PASS)
    print(f"  Password set to the fixed demo password.\n")
    return True


def wait_for_backend() -> None:
    print(f"Waiting for the backend at {BASE_URL}/api/health (up to {WAIT_SECONDS} s) ...")
    deadline = time.time() + WAIT_SECONDS
    while time.time() < deadline:
        try:
            if requests.get(f"{BASE_URL}/api/health", timeout=5).ok:
                print("  Backend is up.\n")
                return
        except requests.RequestException:
            pass
        time.sleep(3)
    raise SeedError(f"The backend did not answer on {BASE_URL}/api/health within {WAIT_SECONDS} s. "
                    f"Start it first (start-all script, or  manage up  in _Deployment).")


def print_credentials() -> None:
    print("Admin credentials:")
    print(f"  Email    : {ADMIN_EMAIL}")
    print(f"  Password : {ADMIN_PASS}")
    print()
    print("Test owner credentials (fixed, for the demo quick login and testing):")
    print(f"  Email    : {TEST_OWNER_EMAIL}")
    print(f"  Password : {TEST_OWNER_PASS}")


def seed() -> None:
    wait_for_backend()
    admin_token = admin_login()

    created = skipped = properties = products = 0
    for owner_def in OWNERS:
        display = owner_def.get("distinctiveTitle") or owner_def["companyName"]
        print(f"Owner: {display} ({owner_def['contactEmail']}) ...")
        if owner_exists(owner_def["contactEmail"]):
            print("  Already exists - skipped.\n")
            skipped += 1
            continue
        _, n_props, n_products = seed_owner(admin_token, owner_def, random.Random(owner_def["vatId"]),
                                            UNAVAILABLE_PER_PROPERTY)
        created += 1
        properties += n_props
        products += n_products
        print()

    test_created = seed_test_owner(admin_token)

    print("=" * 60)
    print(" Seeding complete")
    print(f"  Demo owners created : {created} (already present: {skipped})")
    print(f"  Properties created  : {properties}")
    print(f"  Products created    : {products}")
    print(f"  Test owner          : {'created' if test_created else 'already present'}")
    print("=" * 60)
    print()
    print_credentials()


def seed_test_owner_only() -> None:
    wait_for_backend()
    admin_token = admin_login()
    seed_test_owner(admin_token)
    print_credentials()


if __name__ == "__main__":
    try:
        if "--test-owner-only" in sys.argv[1:]:
            seed_test_owner_only()
        else:
            seed()
    except SeedError as e:
        sys.exit(f"\nERROR: {e}")
    except psycopg2.Error as e:
        sys.exit(f"\nERROR: database error: {str(e).strip()}\n"
                 f"If the tables do not exist yet, start the backend first (it creates them on startup).")
