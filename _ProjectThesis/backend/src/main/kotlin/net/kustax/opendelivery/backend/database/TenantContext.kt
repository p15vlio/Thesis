package net.kustax.opendelivery.backend.database

import kotlin.coroutines.CoroutineContext

// Carries the current tenant's schema name through the coroutine context
data class TenantContext(val schemaName: String) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<TenantContext>
    override val key: CoroutineContext.Key<*> = Key
}
