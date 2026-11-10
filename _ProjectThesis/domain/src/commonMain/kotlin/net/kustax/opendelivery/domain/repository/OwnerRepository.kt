package net.kustax.opendelivery.domain.repository

import net.kustax.opendelivery.domain.entity.platform.Owner

interface OwnerRepository {
    // Inserts the owner together with its password hash (the hash is never part of the Owner entity)
    suspend fun createWithHash(owner: Owner, hash: String): Owner
    suspend fun findById(id: String): Owner?
    // Case-insensitive
    suspend fun findByEmail(email: String): Owner?
    suspend fun findAll(): List<Owner>
    suspend fun update(owner: Owner): Owner
    suspend fun deactivate(id: String)
    suspend fun activate(id: String)
}
