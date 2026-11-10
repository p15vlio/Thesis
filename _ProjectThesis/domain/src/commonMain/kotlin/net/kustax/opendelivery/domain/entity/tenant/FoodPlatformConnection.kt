package net.kustax.opendelivery.domain.entity.tenant

import net.kustax.opendelivery.domain.enum.ExternalPlatform

// encryptedApiKey is SecretCipher ciphertext; decrypting it is the caller's job.
data class FoodPlatformConnection(
    val platform: ExternalPlatform,
    val encryptedApiKey: String,
    val connectedAt: Long
)
