package net.kustax.opendelivery.backend.database

// Legitimate tenant schema names are always server-generated in exactly one place
// (OwnerService.create(): "tenant_${id.replace("-", "").take(12)}" where id is a UUID.randomUUID()
// string) — i.e. always "tenant_" followed by 12 lowercase hex characters.
private val TENANT_SCHEMA_NAME_REGEX = Regex("^tenant_[0-9a-f]{12}$")

fun isValidTenantSchemaName(name: String): Boolean = TENANT_SCHEMA_NAME_REGEX.matches(name)
