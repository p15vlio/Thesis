#!/usr/bin/env python3
"""
GuestDesk - Platform Admin Seed Script
======================================
Inserts a Platform Admin account into the `public.platform_admins` table.

Requirements:
    pip install -r requirements.txt      (bcrypt, psycopg2-binary)

Usage:
    python seed_admin.py

    Override defaults via environment variables:
        DB_HOST      (default: localhost)
        DB_PORT      (default: 5432)
        DB_NAME      (default: opendelivery)
        DB_USER      (default: postgres)
        DB_PASSWORD  (default: opendelivery)
        ADMIN_EMAIL  (default: admin@guestdesk.io)
        ADMIN_PASS   (default: Admin@GuestDesk1)
        SEED_WAIT_SECONDS (default: 120) how long to wait for the database and the backend's tables

Notes:
    - BCrypt cost factor is 12, matching PasswordHasher.kt in the backend.
    - The script is idempotent: re-running it with the same email is a no-op.
    - The backend creates the `platform_admins` table on startup, so start the stack first
      (start-all script, or `manage up` in _Deployment). The script waits for the
      database and the table for up to SEED_WAIT_SECONDS.
"""

import os
import sys
sys.stdout.reconfigure(errors="replace")  # server messages may contain characters a cp1253 console cannot print
import uuid
import time

try:
    import bcrypt
except ImportError:
    sys.exit("Missing dependency: run  pip install -r requirements.txt  (package: bcrypt)")

try:
    import psycopg2
    import psycopg2.errors
except ImportError:
    sys.exit("Missing dependency: run  pip install -r requirements.txt  (package: psycopg2-binary)")


# -- Configuration -------------------------------------------------------------

DB_HOST     = os.getenv("DB_HOST",     "localhost")
DB_PORT     = int(os.getenv("DB_PORT", "5432"))
DB_NAME     = os.getenv("DB_NAME",     "opendelivery")
DB_USER     = os.getenv("DB_USER",     "postgres")
DB_PASSWORD = os.getenv("DB_PASSWORD", "opendelivery")

ADMIN_EMAIL = os.getenv("ADMIN_EMAIL", "admin@guestdesk.io")
ADMIN_PASS  = os.getenv("ADMIN_PASS",  "Admin@GuestDesk1")

WAIT_SECONDS = int(os.getenv("SEED_WAIT_SECONDS", "120"))
BCRYPT_COST = 12  # Must match PasswordHasher.kt (BCrypt.withDefaults().hashToString(12, ...))


# -- Hashing -------------------------------------------------------------------

def hash_password(password: str) -> str:
    """BCrypt hash with cost 12. $2b$ prefix is accepted by the Java at.favre library."""
    salt = bcrypt.gensalt(rounds=BCRYPT_COST)
    return bcrypt.hashpw(password.encode("utf-8"), salt).decode("utf-8")


# -- Seed ----------------------------------------------------------------------

def connect_when_ready():
    """Waits until PostgreSQL accepts connections and the backend has created platform_admins."""
    print(f"Connecting to {DB_HOST}:{DB_PORT}/{DB_NAME} as {DB_USER} (waiting up to {WAIT_SECONDS} s) ...")
    deadline = time.time() + WAIT_SECONDS
    last_problem = ""
    while True:
        try:
            conn = psycopg2.connect(host=DB_HOST, port=DB_PORT, dbname=DB_NAME, user=DB_USER,
                                    password=DB_PASSWORD, connect_timeout=10)
            cur = conn.cursor()
            cur.execute("SELECT to_regclass('public.platform_admins')")
            if cur.fetchone()[0] is not None:
                cur.close()
                return conn
            conn.close()
            problem = ("the table platform_admins does not exist yet: the backend creates it on startup, "
                       "so start the backend first")
        except psycopg2.OperationalError as e:
            problem = f"cannot connect: {str(e).strip()}"
        if problem != last_problem:
            print(f"  Not ready ({problem}), retrying ...")
            last_problem = problem
        if time.time() >= deadline:
            sys.exit(f"ERROR: database not ready after {WAIT_SECONDS} s: {problem}.\n"
                     f"Start the stack first (start-all script, or  manage up  in _Deployment), "
                     f"and check that no other PostgreSQL is using port {DB_PORT}.")
        time.sleep(3)


def seed():
    conn = connect_when_ready()
    try:
        cur = conn.cursor()
        cur.execute("SELECT id FROM platform_admins WHERE lower(email) = lower(%s)", (ADMIN_EMAIL,))
        if cur.fetchone():
            print(f"Admin '{ADMIN_EMAIL}' already exists - nothing to do.")
            return

        print(f"Hashing password (BCrypt cost {BCRYPT_COST}) ...")
        password_hash = hash_password(ADMIN_PASS)

        admin_id   = str(uuid.uuid4())
        created_at = int(time.time() * 1000)  # epoch ms, matching all backend timestamps

        cur.execute(
            """
            INSERT INTO platform_admins (id, email, password_hash, created_at)
            VALUES (%s, %s, %s, %s)
            """,
            (admin_id, ADMIN_EMAIL, password_hash, created_at),
        )
        conn.commit()
    except psycopg2.Error as e:
        sys.exit(f"ERROR: database error: {str(e).strip()}")
    finally:
        conn.close()

    print()
    print("Platform Admin seeded successfully.")
    print(f"  Email    : {ADMIN_EMAIL}")
    print(f"  Password : {ADMIN_PASS}")
    print(f"  ID       : {admin_id}")
    print()
    print("Change the password after first login.")


if __name__ == "__main__":
    seed()
