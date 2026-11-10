package net.kustax.opendelivery.backend.adapter

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import net.kustax.opendelivery.backend.database.TenantContext
import net.kustax.opendelivery.domain.repository.OrderRepository
import net.kustax.opendelivery.domain.repository.OwnerRepository
import org.slf4j.LoggerFactory

// The emulated platforms keep their status timers in memory, so a backend restart would strand every
// order that was still being delivered. This re-schedules the remaining steps of each such order in every
// tenant. A real platform keeps its own state and would need no equivalent.
class EmulatedFoodOrderRecovery(
    private val adapters: List<EmulatedFoodPlatformAdapter>,
    private val ownerRepository: OwnerRepository,
    private val orderRepository: OrderRepository
) {

    private val logger = LoggerFactory.getLogger(EmulatedFoodOrderRecovery::class.java)

    suspend fun resumeAll() {
        ownerRepository.findAll().forEach { owner ->
            // One broken tenant (e.g. a schema that failed to provision) must not abort startup for the rest
            try {
                withContext(TenantContext(owner.schemaName)) {
                    orderRepository.findInFlightFoodOrders().forEach { order ->
                        adapters.firstOrNull { it.platform == order.externalPlatform }?.resume(owner.schemaName, order)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Could not resume in-flight food orders for tenant {}", owner.schemaName, e)
            }
        }
    }
}
