package net.kustax.opendelivery.backend.repository.platform

import net.kustax.opendelivery.backend.database.DatabaseFactory
import net.kustax.opendelivery.backend.database.table.platform.OwnersTable
import net.kustax.opendelivery.backend.database.table.platform.PlatformAdminsTable
import net.kustax.opendelivery.backend.database.table.platform.RefreshTokensTable
import net.kustax.opendelivery.domain.entity.platform.AuthRecord
import net.kustax.opendelivery.domain.entity.platform.RefreshTokenRecord
import net.kustax.opendelivery.domain.enum.UserRole
import net.kustax.opendelivery.domain.repository.AuthRepository
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*

class ExposedAuthRepository : AuthRepository {

    override suspend fun findPlatformAdminByEmail(email: String): AuthRecord? =
        DatabaseFactory.dbQuery {
            PlatformAdminsTable.selectAll()
                .where { PlatformAdminsTable.email.lowerCase() eq email.lowercase() }
                .singleOrNull()
                ?.let { row ->
                    AuthRecord(
                        id = row[PlatformAdminsTable.id],
                        email = row[PlatformAdminsTable.email],
                        passwordHash = row[PlatformAdminsTable.passwordHash],
                        role = UserRole.PLATFORM_ADMIN,
                        schemaName = null,
                        isActive = true
                    )
                }
        }

    override suspend fun findOwnerByEmail(email: String): AuthRecord? =
        DatabaseFactory.dbQuery {
            OwnersTable.selectAll()
                .where { OwnersTable.contactEmail.lowerCase() eq email.lowercase() }
                .singleOrNull()
                ?.let { row ->
                    AuthRecord(
                        id = row[OwnersTable.id],
                        email = row[OwnersTable.contactEmail],
                        passwordHash = row[OwnersTable.passwordHash],
                        role = UserRole.OWNER,
                        schemaName = row[OwnersTable.tenantSchemaName],
                        isActive = row[OwnersTable.isActive]
                    )
                }
        }

    override suspend fun isOwnerActive(ownerId: String): Boolean =
        DatabaseFactory.dbQuery {
            OwnersTable.select(OwnersTable.isActive)
                .where { OwnersTable.id eq ownerId }
                .singleOrNull()
                ?.get(OwnersTable.isActive) ?: false
        }

    override suspend fun isOwnerActiveBySchema(schemaName: String): Boolean =
        DatabaseFactory.dbQuery {
            OwnersTable.select(OwnersTable.isActive)
                .where { OwnersTable.tenantSchemaName eq schemaName }
                .singleOrNull()
                ?.get(OwnersTable.isActive) ?: false
        }

    override suspend fun findPasswordHash(userId: String, role: UserRole): String? =
        DatabaseFactory.dbQuery {
            when (role) {
                UserRole.PLATFORM_ADMIN -> PlatformAdminsTable.select(PlatformAdminsTable.passwordHash)
                    .where { PlatformAdminsTable.id eq userId }
                    .singleOrNull()?.get(PlatformAdminsTable.passwordHash)
                UserRole.OWNER -> OwnersTable.select(OwnersTable.passwordHash)
                    .where { OwnersTable.id eq userId }
                    .singleOrNull()?.get(OwnersTable.passwordHash)
                UserRole.DEVICE -> null
            }
        }

    override suspend fun updatePasswordHash(userId: String, role: UserRole, passwordHash: String): Unit =
        DatabaseFactory.dbQuery {
            when (role) {
                UserRole.PLATFORM_ADMIN -> PlatformAdminsTable.update({ PlatformAdminsTable.id eq userId }) {
                    it[PlatformAdminsTable.passwordHash] = passwordHash
                }
                UserRole.OWNER -> OwnersTable.update({ OwnersTable.id eq userId }) {
                    it[OwnersTable.passwordHash] = passwordHash
                }
                UserRole.DEVICE -> error("Devices have no password")
            }
        }

    override suspend fun saveRefreshToken(record: RefreshTokenRecord): Unit =
        DatabaseFactory.dbQuery {
            RefreshTokensTable.insert {
                it[id] = record.id
                it[userId] = record.userId
                it[role] = record.role
                it[tokenHash] = record.tokenHash
                it[tenantSchemaName] = record.schemaName
                it[expiresAt] = record.expiresAt
                it[createdAt] = record.createdAt
            }
        }

    override suspend fun findRefreshToken(tokenHash: String): RefreshTokenRecord? =
        DatabaseFactory.dbQuery {
            RefreshTokensTable.selectAll()
                .where { RefreshTokensTable.tokenHash eq tokenHash }
                .singleOrNull()
                ?.toRefreshTokenRecord()
        }

    override suspend fun deleteRefreshToken(tokenHash: String): Unit =
        DatabaseFactory.dbQuery {
            RefreshTokensTable.deleteWhere { RefreshTokensTable.tokenHash eq tokenHash }
        }

    override suspend fun deleteRefreshTokensForUser(userId: String): Unit =
        DatabaseFactory.dbQuery {
            RefreshTokensTable.deleteWhere { RefreshTokensTable.userId eq userId }
        }
}

private fun ResultRow.toRefreshTokenRecord() = RefreshTokenRecord(
    id = this[RefreshTokensTable.id],
    userId = this[RefreshTokensTable.userId],
    role = this[RefreshTokensTable.role],
    tokenHash = this[RefreshTokensTable.tokenHash],
    schemaName = this[RefreshTokensTable.tenantSchemaName],
    expiresAt = this[RefreshTokensTable.expiresAt],
    createdAt = this[RefreshTokensTable.createdAt]
)
