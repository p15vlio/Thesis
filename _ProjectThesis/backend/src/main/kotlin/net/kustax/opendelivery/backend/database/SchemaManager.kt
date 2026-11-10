package net.kustax.opendelivery.backend.database

import net.kustax.opendelivery.backend.database.table.platform.AuditLogTable
import net.kustax.opendelivery.backend.database.table.platform.OwnersTable
import net.kustax.opendelivery.backend.database.table.platform.PlatformAdminsTable
import net.kustax.opendelivery.backend.database.table.platform.PlatformSettingsTable
import net.kustax.opendelivery.backend.database.table.platform.RefreshTokensTable
import net.kustax.opendelivery.backend.database.table.tenant.DevicesTable
import net.kustax.opendelivery.backend.database.table.tenant.FoodPlatformConnectionsTable
import net.kustax.opendelivery.backend.database.table.tenant.OrderItemsTable
import net.kustax.opendelivery.backend.database.table.tenant.OrdersTable
import net.kustax.opendelivery.backend.database.table.tenant.PaymentsTable
import net.kustax.opendelivery.backend.database.table.tenant.ProductPropertyOverridesTable
import net.kustax.opendelivery.backend.database.table.tenant.ProductsTable
import net.kustax.opendelivery.backend.database.table.tenant.PropertiesTable
import net.kustax.opendelivery.backend.database.table.tenant.PropertyFoodPlatformsTable
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.vendors.currentDialectMetadata

object SchemaManager {

    fun createPlatformTables() {
        transaction {
            SchemaUtils.create(OwnersTable, PlatformAdminsTable, RefreshTokensTable, AuditLogTable, PlatformSettingsTable)
            // Explicit, idempotent, named migration for the email uniqueness constraint — OwnersTable's
            // .uniqueIndex() on contactEmail is only guaranteed applied by SchemaUtils.create() on a
            // brand-new table; a database whose owners table already existed before that declaration was
            // added could be missing it. Safe to run on every startup (IF NOT EXISTS).
            exec("CREATE UNIQUE INDEX IF NOT EXISTS owners_contact_email_unique ON owners (contact_email)")
            // Emails are stored lower-cased (normalized on write) and looked up case-insensitively; these
            // case-insensitive unique indexes make the database refuse a differently-cased duplicate even if
            // a code path forgets to normalize. platform_admins had no email uniqueness at all.
            exec("CREATE UNIQUE INDEX IF NOT EXISTS owners_contact_email_lower_unique ON owners (lower(contact_email))")
            exec("CREATE UNIQUE INDEX IF NOT EXISTS platform_admins_email_lower_unique ON platform_admins (lower(email))")
            // Same self-heal pattern for refresh_tokens.token_hash — the lookup column on every token
            // refresh, and the one shared unpartitioned table most exposed as tenant/token volume grows.
            // Name must match Exposed's own auto-generated index name (table_column, no suffix for a
            // non-unique index) so this is a true no-op — not a second, redundant index — on a brand-new
            // schema where SchemaUtils.create() already added the .index() declared on the column.
            exec("CREATE INDEX IF NOT EXISTS refresh_tokens_token_hash ON refresh_tokens (token_hash)")
        }
    }

    fun dropAndRecreate() {
        transaction {
            // Drop all tenant schemas first
            exec("""
                DO ${'$'}${'$'}
                DECLARE s TEXT;
                BEGIN
                    FOR s IN SELECT nspname FROM pg_namespace WHERE nspname LIKE 'tenant_%'
                    LOOP
                        EXECUTE 'DROP SCHEMA "' || s || '" CASCADE';
                    END LOOP;
                END
                ${'$'}${'$'};
            """)
            SchemaUtils.drop(PlatformSettingsTable, AuditLogTable, RefreshTokensTable, PlatformAdminsTable, OwnersTable)
        }
        createPlatformTables()
    }

    // Creates the tenant schema if needed and brings its tables up to the current model. Idempotent: it runs
    // for a brand-new owner and again for every existing tenant on each startup, which also self-heals tenant
    // schemas provisioned before a table, column, index or constraint was added.
    //
    // Invariant: the search_path switch and ALL tenant DDL share ONE transaction. SET LOCAL scopes the path
    // to that transaction, so it can neither leak onto a pooled connection nor be missing for part of the
    // DDL — a plain SET in a separate transaction could put the tables into another schema (public) if the
    // next statement ran on a different pooled connection.
    fun provisionTenant(schemaName: String) {
        require(isValidTenantSchemaName(schemaName)) { "Invalid tenant schema name" }
        transaction {
            exec("CREATE SCHEMA IF NOT EXISTS \"$schemaName\"")
            exec("SET LOCAL search_path TO \"$schemaName\"")
            // SchemaUtils.create() would commit mid-way (ending SET LOCAL and the single transaction), so the
            // CREATE statements it would run are generated and executed here instead. The caches are reset
            // first because Exposed remembers which tables exist per schema.
            currentDialectMetadata.resetCaches()
            SchemaUtils.createStatements(
                PropertiesTable,
                DevicesTable,
                ProductsTable,
                ProductPropertyOverridesTable,
                OrdersTable,
                OrderItemsTable,
                PaymentsTable,
                FoodPlatformConnectionsTable,
                PropertyFoodPlatformsTable
            ).forEach { exec(it) }
            // Payments are card attempts now: an order may have several FAILED attempts but at most one
            // COMPLETED. The old one-row-per-order unique index is replaced by a plain lookup index plus a
            // partial unique index on COMPLETED rows.
            exec("ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_order_id_unique")
            exec("DROP INDEX IF EXISTS payments_order_id_unique")
            exec("CREATE INDEX IF NOT EXISTS payments_order_id ON payments (order_id)")
            exec("CREATE UNIQUE INDEX IF NOT EXISTS payments_one_completed_per_order ON payments (order_id) WHERE status = 'COMPLETED'")
            // The payment-method column is replaced by the card entry mode. Rows that predate it get
            // CARD_CONTACTLESS as the column default, then the old column goes.
            exec("ALTER TABLE payments ADD COLUMN IF NOT EXISTS entry_mode VARCHAR(32) NOT NULL DEFAULT 'CARD_CONTACTLESS'")
            exec("ALTER TABLE payments ADD COLUMN IF NOT EXISTS card_last4 VARCHAR(4)")
            exec("ALTER TABLE payments ADD COLUMN IF NOT EXISTS decline_reason VARCHAR(255)")
            exec("ALTER TABLE payments DROP COLUMN IF EXISTS method")
            // Same self-heal pattern for the filtered non-PK columns queried on every order/device list —
            // retroactively applies to tenant schemas provisioned before these indexes were added. Names
            // match Exposed's own auto-generated index names (see refresh_tokens_token_hash note above).
            exec("CREATE INDEX IF NOT EXISTS devices_property_id ON devices (property_id)")
            exec("CREATE INDEX IF NOT EXISTS orders_device_id ON orders (device_id)")
            exec("CREATE INDEX IF NOT EXISTS orders_property_id ON orders (property_id)")
            exec("CREATE INDEX IF NOT EXISTS orders_status ON orders (status)")
            exec("CREATE INDEX IF NOT EXISTS order_items_order_id ON order_items (order_id)")
            // Columns that no longer exist in the model. Some were NOT NULL, so tenant schemas provisioned
            // before their removal would reject inserts that no longer supply them.
            exec("ALTER TABLE properties DROP COLUMN IF EXISTS fulfillment_model")
            exec("ALTER TABLE orders DROP COLUMN IF EXISTS fulfillment_model")
            exec("ALTER TABLE orders DROP COLUMN IF EXISTS delivery_provider")
            exec("ALTER TABLE products DROP COLUMN IF EXISTS source")
            // SchemaUtils.create never adds columns to an existing table, so columns added after a tenant
            // was provisioned are added explicitly.
            exec("ALTER TABLE orders ADD COLUMN IF NOT EXISTS external_order_id VARCHAR(64)")
            exec("ALTER TABLE orders ADD COLUMN IF NOT EXISTS restaurant_id VARCHAR(64)")
            exec("ALTER TABLE orders ADD COLUMN IF NOT EXISTS restaurant_name VARCHAR(255)")
            exec("ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivery_fee BIGINT NOT NULL DEFAULT 0")
            exec("ALTER TABLE orders ADD COLUMN IF NOT EXISTS estimated_delivery_at BIGINT")
            exec("ALTER TABLE properties ADD COLUMN IF NOT EXISTS wifi_name VARCHAR(128)")
            exec("ALTER TABLE properties ADD COLUMN IF NOT EXISTS wifi_password VARCHAR(128)")
            exec("ALTER TABLE properties ADD COLUMN IF NOT EXISTS check_out_time VARCHAR(5)")
            exec("ALTER TABLE properties ADD COLUMN IF NOT EXISTS house_rules TEXT")

            // Foreign keys: SchemaUtils.create declares them for brand-new tables only, so existing tenants
            // get them here. Each is skipped when the column already carries a foreign key (the
            // Exposed-generated one on a new tenant, or ours from an earlier startup). order_items.product_id
            // is deliberately not a foreign key — see OrderItemsTable.
            addForeignKey("devices", "property_id", "properties", "RESTRICT")
            addForeignKey("orders", "device_id", "devices", "RESTRICT")
            addForeignKey("orders", "property_id", "properties", "RESTRICT")
            addForeignKey("order_items", "order_id", "orders", "CASCADE")
            addForeignKey("payments", "order_id", "orders", "RESTRICT")
            addForeignKey("product_property_overrides", "product_id", "products", "CASCADE")
            addForeignKey("product_property_overrides", "property_id", "properties", "CASCADE")
            addForeignKey("property_food_platforms", "property_id", "properties", "CASCADE")

            // Money and quantity sanity checks (all amounts are cents).
            addCheck("order_items", "order_items_quantity_positive", "quantity > 0")
            addCheck("order_items", "order_items_unit_price_non_negative", "unit_price >= 0")
            addCheck("order_items", "order_items_subtotal_non_negative", "subtotal >= 0")
            addCheck("orders", "orders_total_amount_non_negative", "total_amount >= 0")
            addCheck("orders", "orders_delivery_fee_non_negative", "delivery_fee >= 0")
            addCheck("payments", "payments_amount_non_negative", "amount >= 0")
            addCheck("products", "products_base_price_non_negative", "base_price >= 0")
        }
    }

    // Adds table(column) -> refTable(id) unless that column already carries a foreign key. Table names resolve
    // through the transaction's search_path, so this must run after provisionTenant's SET LOCAL.
    private fun JdbcTransaction.addForeignKey(table: String, column: String, refTable: String, onDelete: String) {
        exec("""
            DO ${'$'}${'$'}
            BEGIN
                IF NOT EXISTS (
                    SELECT 1 FROM pg_constraint c
                    WHERE c.contype = 'f'
                      AND c.conrelid = '$table'::regclass
                      AND c.conkey @> ARRAY[(SELECT a.attnum FROM pg_attribute a WHERE a.attrelid = '$table'::regclass AND a.attname = '$column')]
                ) THEN
                    ALTER TABLE $table ADD CONSTRAINT ${table}_${column}_fkey
                        FOREIGN KEY ($column) REFERENCES $refTable (id) ON DELETE $onDelete;
                END IF;
            END
            ${'$'}${'$'};
        """)
    }

    private fun JdbcTransaction.addCheck(table: String, name: String, condition: String) {
        exec("""
            DO ${'$'}${'$'}
            BEGIN
                IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = '$name' AND conrelid = '$table'::regclass) THEN
                    ALTER TABLE $table ADD CONSTRAINT $name CHECK ($condition);
                END IF;
            END
            ${'$'}${'$'};
        """)
    }
}
