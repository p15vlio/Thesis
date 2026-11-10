package net.kustax.opendelivery.backend.routing

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import net.kustax.opendelivery.backend.adapter.EmulatedBoxAdapter
import net.kustax.opendelivery.backend.adapter.EmulatedEfoodAdapter
import net.kustax.opendelivery.backend.adapter.EmulatedFoodOrderRecovery
import net.kustax.opendelivery.backend.adapter.EmulatedPaymentAdapter
import net.kustax.opendelivery.backend.adapter.EmulatedWoltAdapter
import net.kustax.opendelivery.backend.adapter.GemhCompanyRegistryAdapter
import net.kustax.opendelivery.backend.adapter.NominatimGeocoderAdapter
import net.kustax.opendelivery.backend.adapter.SmtpEmailAdapter
import net.kustax.opendelivery.backend.repository.platform.ExposedAuditLogRepository
import net.kustax.opendelivery.backend.repository.platform.ExposedAuthRepository
import net.kustax.opendelivery.backend.repository.platform.ExposedOwnerRepository
import net.kustax.opendelivery.backend.repository.platform.ExposedPlatformSettingsRepository
import net.kustax.opendelivery.backend.repository.tenant.ExposedDeviceRepository
import net.kustax.opendelivery.backend.repository.tenant.ExposedFoodPlatformConnectionRepository
import net.kustax.opendelivery.backend.repository.tenant.ExposedOrderItemRepository
import net.kustax.opendelivery.backend.repository.tenant.ExposedOrderRepository
import net.kustax.opendelivery.backend.repository.tenant.ExposedPaymentRepository
import net.kustax.opendelivery.backend.repository.tenant.ExposedProductPropertyOverrideRepository
import net.kustax.opendelivery.backend.repository.tenant.ExposedProductRepository
import net.kustax.opendelivery.backend.repository.tenant.ExposedPropertyFoodPlatformRepository
import net.kustax.opendelivery.backend.repository.tenant.ExposedPropertyRepository
import net.kustax.opendelivery.backend.service.AuditLogService
import net.kustax.opendelivery.backend.service.AuthService
import net.kustax.opendelivery.backend.service.DashboardService
import net.kustax.opendelivery.backend.service.DeviceService
import net.kustax.opendelivery.backend.service.FoodDeliveryService
import net.kustax.opendelivery.backend.service.FoodOrderEventHandler
import net.kustax.opendelivery.backend.service.OrderService
import net.kustax.opendelivery.backend.service.OwnerService
import net.kustax.opendelivery.backend.service.PaymentService
import net.kustax.opendelivery.backend.service.PlatformSettingsService
import net.kustax.opendelivery.backend.service.ProductService
import net.kustax.opendelivery.backend.service.PropertyService
import net.kustax.opendelivery.backend.service.RefundService

fun Application.configureRouting() {
    val auditLogRepository = ExposedAuditLogRepository()
    val auditLogService = AuditLogService(auditLogRepository)

    val authRepository = ExposedAuthRepository()
    val authService = AuthService(authRepository, auditLogService)

    val settingsService = PlatformSettingsService(ExposedPlatformSettingsRepository(), auditLogService)
    val emailAdapter = SmtpEmailAdapter(settingsService::getSmtpSettings)
    val companyRegistry = GemhCompanyRegistryAdapter(settingsService::getGemhApiKey)
    val geocoder = NominatimGeocoderAdapter()

    val ownerRepository = ExposedOwnerRepository()
    val ownerService = OwnerService(ownerRepository, authRepository, auditLogService, emailAdapter)

    val propertyRepository = ExposedPropertyRepository()
    val orderRepository = ExposedOrderRepository()

    val deviceRepository = ExposedDeviceRepository()
    attributes.put(DeviceRepositoryKey, deviceRepository)
    attributes.put(AuthRepositoryKey, authRepository)
    val deviceService = DeviceService(deviceRepository, auditLogService, ownerRepository, authRepository, orderRepository, propertyRepository)

    val propertyService = PropertyService(propertyRepository, auditLogService, deviceService, orderRepository)

    val productRepository = ExposedProductRepository()
    val productPropertyOverrideRepository = ExposedProductPropertyOverrideRepository()

    val orderItemRepository = ExposedOrderItemRepository()
    val paymentRepository = ExposedPaymentRepository()

    val paymentAdapter = EmulatedPaymentAdapter()

    // Seconds between the status updates an emulated platform sends for an accepted order
    val foodStepMillis = (System.getenv("FOOD_PLATFORM_STEP_SECONDS")?.toLongOrNull()?.takeIf { it > 0 } ?: 45L) * 1000
    val foodOrderEventHandler = FoodOrderEventHandler(orderRepository)
    val foodAdapters = listOf(
        EmulatedEfoodAdapter(foodOrderEventHandler, foodStepMillis),
        EmulatedWoltAdapter(foodOrderEventHandler, foodStepMillis),
        EmulatedBoxAdapter(foodOrderEventHandler, foodStepMillis)
    )
    val foodDeliveryService = FoodDeliveryService(
        foodAdapters,
        ExposedFoodPlatformConnectionRepository(),
        ExposedPropertyFoodPlatformRepository(),
        propertyRepository,
        orderRepository,
        auditLogService
    )
    runBlocking { EmulatedFoodOrderRecovery(foodAdapters, ownerRepository, orderRepository).resumeAll() }

    val productService = ProductService(productRepository, productPropertyOverrideRepository, propertyRepository, auditLogService, foodDeliveryService)

    val refundService = RefundService(paymentRepository, paymentAdapter, auditLogService)
    val orderService = OrderService(orderRepository, orderItemRepository, productRepository, productPropertyOverrideRepository, paymentRepository, foodDeliveryService, refundService)
    val paymentService = PaymentService(paymentRepository, orderRepository, orderItemRepository, paymentAdapter, emailAdapter, foodDeliveryService, refundService)

    val dashboardService = DashboardService(propertyRepository, deviceRepository, orderRepository)

    monitor.subscribe(ApplicationStopped) {
        paymentService.close()
        foodAdapters.forEach { it.close() }
        geocoder.close()
        companyRegistry.close()
    }

    routing {
        route("/api") {
            healthRoute()
            rateLimit(PublicRateLimit) { authRoutes(authService) }
            deviceRoutes(deviceService, orderService)

            authenticate("jwt") {
                changePasswordRoute(authService)
                orderRoutes(orderService)
                deviceOrderRoutes(orderService)
                paymentRoutes(paymentService)
                productRoutes(productService)
                foodPlatformRoutes(foodDeliveryService)
                foodCatalogRoutes(foodDeliveryService)

                ownerRoutes(ownerService)
                propertyRoutes(propertyService)
                dashboardRoutes(dashboardService)
                auditLogRoutes(auditLogRepository)
                settingsRoutes(settingsService, emailAdapter)
                companyLookupRoutes(companyRegistry)
                rateLimit(GeocodeRateLimit) { geocodeRoutes(geocoder) }
                adminRoutes(ownerRepository, propertyRepository, deviceRepository, productRepository, orderRepository)
            }
        }
    }
}
