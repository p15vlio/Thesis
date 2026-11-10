package net.kustax.opendelivery.backend.repository

import kotlinx.coroutines.currentCoroutineContext
import net.kustax.opendelivery.backend.database.TenantContext
import net.kustax.opendelivery.backend.database.isValidTenantSchemaName
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

suspend fun <T> tenantQuery(block: suspend Transaction.() -> T): T {
    val schema = currentCoroutineContext()[TenantContext]?.schemaName
        ?: error("TenantContext not set in coroutine context")
    require(isValidTenantSchemaName(schema)) { "Invalid tenant schema name" }
    return suspendTransaction {
        // SET LOCAL: the path lasts only for this transaction, so it can't leak onto the pooled connection.
        // Nested tenantQuery calls join the outer transaction and simply set the same path again.
        exec("SET LOCAL search_path TO \"$schema\"")
        block()
    }
}
