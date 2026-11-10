package net.kustax.opendelivery.backend.repository.tenant

import net.kustax.opendelivery.backend.database.table.tenant.ProductPropertyOverridesTable
import net.kustax.opendelivery.backend.repository.tenantQuery
import net.kustax.opendelivery.domain.repository.ProductPropertyOverrideRepository
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*

// Pure association table, a ProductService implementation detail. Row presence means "disabled at this
// property"; rows disappear with their product or property through the foreign keys' ON DELETE CASCADE.
class ExposedProductPropertyOverrideRepository : ProductPropertyOverrideRepository {

    // insertIgnore (rather than an exists-check + conditional insert) keeps this atomic — two concurrent
    // calls disabling the same (productId, propertyId) pair would otherwise race past the exists-check and
    // the second insert would throw on the composite primary key.
    override suspend fun disable(productId: String, propertyId: String): Unit = tenantQuery {
        ProductPropertyOverridesTable.insertIgnore { row ->
            row[ProductPropertyOverridesTable.productId] = productId
            row[ProductPropertyOverridesTable.propertyId] = propertyId
        }
    }

    override suspend fun enable(productId: String, propertyId: String): Unit = tenantQuery {
        ProductPropertyOverridesTable.deleteWhere {
            (ProductPropertyOverridesTable.productId eq productId) and (ProductPropertyOverridesTable.propertyId eq propertyId)
        }
    }

    override suspend fun findDisabledPropertyIds(productId: String): Set<String> = tenantQuery {
        ProductPropertyOverridesTable.selectAll()
            .where { ProductPropertyOverridesTable.productId eq productId }
            .map { it[ProductPropertyOverridesTable.propertyId] }
            .toSet()
    }

    override suspend fun isDisabled(productId: String, propertyId: String): Boolean = tenantQuery {
        ProductPropertyOverridesTable.selectAll()
            .where { (ProductPropertyOverridesTable.productId eq productId) and (ProductPropertyOverridesTable.propertyId eq propertyId) }
            .any()
    }
}
