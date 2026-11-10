package net.kustax.opendelivery.backend.service

import net.kustax.opendelivery.data.request.CreateProductRequest
import net.kustax.opendelivery.data.request.ImportProductsRequest
import net.kustax.opendelivery.data.request.UpdateProductRequest
import net.kustax.opendelivery.data.response.ImportProductsResponse
import net.kustax.opendelivery.data.response.ProductResponse
import net.kustax.opendelivery.domain.entity.tenant.Product
import net.kustax.opendelivery.domain.enum.ExternalPlatform
import net.kustax.opendelivery.domain.enum.ProductCategory
import net.kustax.opendelivery.domain.repository.ProductPropertyOverrideRepository
import net.kustax.opendelivery.domain.repository.ProductRepository
import net.kustax.opendelivery.domain.repository.PropertyRepository
import java.util.UUID

class ProductService(
    private val productRepository: ProductRepository,
    private val overrideRepository: ProductPropertyOverrideRepository,
    private val propertyRepository: PropertyRepository,
    private val auditLogService: AuditLogService,
    private val foodDeliveryService: FoodDeliveryService
) {

    suspend fun create(request: CreateProductRequest): ProductResponse {
        require(request.name.isNotBlank()) { "Product name is required" }
        require(request.description.isNotBlank()) { "Product description is required" }
        require(request.basePrice >= 0) { "Base price cannot be negative" }
        val product = Product(
            id = UUID.randomUUID().toString(),
            name = request.name,
            description = request.description,
            category = enumValueOf<ProductCategory>(request.category),
            basePrice = request.basePrice,
            imageUrl = request.imageUrl,
            isAvailable = true,
            createdAt = System.currentTimeMillis()
        )
        productRepository.create(product)
        return product.toResponse()
    }

    // Creates own-stock products from a connected platform's retail catalog. Names, descriptions, categories and
    // prices always come from the platform, never from the client. Items already in stock (same name, any case)
    // are skipped and reported by name.
    suspend fun importFromPlatform(
        request: ImportProductsRequest,
        actorId: String,
        actorRole: String,
        schemaName: String
    ): ImportProductsResponse {
        val platform = enumValueOf<ExternalPlatform>(request.platform.uppercase())
        val ids = request.catalogProductIds.distinct()
        require(ids.isNotEmpty()) { "Select at least one product to import" }
        val catalog = foodDeliveryService.catalogProducts(platform).associateBy { it.id }
        val selected = ids.map { catalog[it] ?: throw IllegalArgumentException("Unknown catalog product: $it") }

        val knownNames = productRepository.findAll().map { it.name.lowercase() }.toMutableSet()
        val imported = mutableListOf<ProductResponse>()
        val skipped = mutableListOf<String>()
        for (item in selected) {
            if (!knownNames.add(item.name.lowercase())) {
                skipped.add(item.name)
                continue
            }
            val product = Product(
                id = UUID.randomUUID().toString(),
                name = item.name,
                description = item.description,
                category = item.category,
                basePrice = item.priceCents,
                imageUrl = null,
                isAvailable = true,
                createdAt = System.currentTimeMillis()
            )
            productRepository.create(product)
            imported.add(product.toResponse())
        }
        auditLogService.log(
            actorId, actorRole, "IMPORT_PRODUCTS", "Product", null, schemaName,
            "platform=${platform.name}, imported=${imported.size}, skipped=${skipped.size}"
        )
        return ImportProductsResponse(imported, skipped)
    }

    // N+1 query per product to fetch its disabled-property overrides — acceptable at MVP scale, this is
    // an owner/admin-facing catalog-management listing, not the hot kiosk path.
    suspend fun findAll(): List<ProductResponse> =
        productRepository.findAll().map { it.toResponse(overrideRepository.findDisabledPropertyIds(it.id).toList()) }

    // Kiosk-facing: full catalog minus anything globally unavailable or disabled at this specific property.
    suspend fun findAvailableForProperty(propertyId: String): List<ProductResponse> =
        productRepository.findAll()
            .filter { it.isAvailable }
            .filter { !overrideRepository.isDisabled(it.id, propertyId) }
            .map { it.toResponse() }

    suspend fun findById(id: String): ProductResponse {
        val product = productRepository.findById(id) ?: throw NotFoundException("Product not found: $id")
        return product.toResponse(overrideRepository.findDisabledPropertyIds(id).toList())
    }

    suspend fun update(id: String, request: UpdateProductRequest): ProductResponse {
        require(request.name.isNotBlank()) { "Product name is required" }
        require(request.description.isNotBlank()) { "Product description is required" }
        require(request.basePrice >= 0) { "Base price cannot be negative" }
        val existing = productRepository.findById(id)
            ?: throw NotFoundException("Product not found: $id")
        val updated = existing.copy(
            name = request.name,
            description = request.description,
            basePrice = request.basePrice,
            imageUrl = request.imageUrl,
            isAvailable = request.isAvailable
        )
        productRepository.update(updated)
        return updated.toResponse()
    }

    suspend fun toggleAvailability(id: String, isAvailable: Boolean): ProductResponse {
        val existing = productRepository.findById(id)
            ?: throw NotFoundException("Product not found: $id")
        productRepository.toggleAvailability(id, isAvailable)
        return existing.copy(isAvailable = isAvailable).toResponse()
    }

    suspend fun setPropertyAvailability(
        productId: String,
        propertyId: String,
        isAvailable: Boolean,
        actorId: String,
        actorRole: String,
        schemaName: String
    ): ProductResponse {
        val product = productRepository.findById(productId)
            ?: throw NotFoundException("Product not found: $productId")
        propertyRepository.findById(propertyId) ?: throw NotFoundException("Property not found: $propertyId")
        if (isAvailable) overrideRepository.enable(productId, propertyId) else overrideRepository.disable(productId, propertyId)
        auditLogService.log(actorId, actorRole, "SET_PRODUCT_PROPERTY_AVAILABILITY", "Product", productId, schemaName)
        return product.toResponse(overrideRepository.findDisabledPropertyIds(productId).toList())
    }

    suspend fun delete(id: String, actorId: String, actorRole: String, schemaName: String) {
        productRepository.findById(id) ?: throw NotFoundException("Product not found: $id")
        // Its per-property overrides go with it (ON DELETE CASCADE)
        productRepository.delete(id)
        auditLogService.log(actorId, actorRole, "DELETE_PRODUCT", "Product", id, schemaName)
    }

    private fun Product.toResponse(disabledPropertyIds: List<String> = emptyList()) = ProductResponse(
        id = id,
        name = name,
        description = description,
        category = category.name,
        basePrice = basePrice,
        imageUrl = imageUrl,
        isAvailable = isAvailable,
        createdAt = createdAt,
        disabledPropertyIds = disabledPropertyIds
    )
}
