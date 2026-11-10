package net.kustax.opendelivery.backend.repository.platform

import net.kustax.opendelivery.backend.database.DatabaseFactory
import net.kustax.opendelivery.backend.database.table.platform.OwnersTable
import net.kustax.opendelivery.domain.entity.platform.Owner
import net.kustax.opendelivery.domain.repository.OwnerRepository
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*

class ExposedOwnerRepository : OwnerRepository {

    override suspend fun createWithHash(owner: Owner, hash: String): Owner = DatabaseFactory.dbQuery {
        OwnersTable.insert { row ->
            row[id] = owner.id
            row[companyName] = owner.companyName
            row[distinctiveTitle] = owner.distinctiveTitle
            row[vatId] = owner.vatId
            row[gemhNumber] = owner.gemhNumber
            row[legalForm] = owner.legalForm
            row[companyActivity] = owner.companyActivity
            row[streetName] = owner.streetName
            row[streetNo] = owner.streetNo
            row[postalCode] = owner.postalCode
            row[city] = owner.city
            row[contactFirstName] = owner.contactFirstName
            row[contactLastName] = owner.contactLastName
            row[contactEmail] = owner.contactEmail
            row[passwordHash] = hash
            row[contactPhone] = owner.contactPhone
            row[secondaryContactName] = owner.secondaryContactName
            row[secondaryContactPhone] = owner.secondaryContactPhone
            row[secondaryContactEmail] = owner.secondaryContactEmail
            row[website] = owner.website
            row[notes] = owner.notes
            row[tenantSchemaName] = owner.schemaName
            row[isActive] = owner.isActive
            row[createdAt] = owner.createdAt
        }
        owner
    }

    override suspend fun findById(id: String): Owner? = DatabaseFactory.dbQuery {
        OwnersTable.selectAll()
            .where { OwnersTable.id eq id }
            .singleOrNull()
            ?.toOwner()
    }

    override suspend fun findByEmail(email: String): Owner? = DatabaseFactory.dbQuery {
        OwnersTable.selectAll()
            .where { OwnersTable.contactEmail.lowerCase() eq email.lowercase() }
            .singleOrNull()
            ?.toOwner()
    }

    override suspend fun findAll(): List<Owner> = DatabaseFactory.dbQuery {
        OwnersTable.selectAll().map { it.toOwner() }
    }

    override suspend fun update(owner: Owner): Owner = DatabaseFactory.dbQuery {
        OwnersTable.update({ OwnersTable.id eq owner.id }) { row ->
            row[companyName] = owner.companyName
            row[distinctiveTitle] = owner.distinctiveTitle
            row[gemhNumber] = owner.gemhNumber
            row[legalForm] = owner.legalForm
            row[companyActivity] = owner.companyActivity
            row[streetName] = owner.streetName
            row[streetNo] = owner.streetNo
            row[postalCode] = owner.postalCode
            row[city] = owner.city
            row[contactFirstName] = owner.contactFirstName
            row[contactLastName] = owner.contactLastName
            row[contactPhone] = owner.contactPhone
            row[secondaryContactName] = owner.secondaryContactName
            row[secondaryContactPhone] = owner.secondaryContactPhone
            row[secondaryContactEmail] = owner.secondaryContactEmail
            row[website] = owner.website
            row[notes] = owner.notes
            row[isActive] = owner.isActive
        }
        owner
    }

    override suspend fun deactivate(id: String): Unit = DatabaseFactory.dbQuery {
        OwnersTable.update({ OwnersTable.id eq id }) { it[isActive] = false }
    }

    override suspend fun activate(id: String): Unit = DatabaseFactory.dbQuery {
        OwnersTable.update({ OwnersTable.id eq id }) { it[isActive] = true }
    }
}

private fun ResultRow.toOwner() = Owner(
    id = this[OwnersTable.id],
    companyName = this[OwnersTable.companyName],
    distinctiveTitle = this[OwnersTable.distinctiveTitle],
    vatId = this[OwnersTable.vatId],
    gemhNumber = this[OwnersTable.gemhNumber],
    legalForm = this[OwnersTable.legalForm],
    companyActivity = this[OwnersTable.companyActivity],
    streetName = this[OwnersTable.streetName],
    streetNo = this[OwnersTable.streetNo],
    postalCode = this[OwnersTable.postalCode],
    city = this[OwnersTable.city],
    contactFirstName = this[OwnersTable.contactFirstName],
    contactLastName = this[OwnersTable.contactLastName],
    contactEmail = this[OwnersTable.contactEmail],
    contactPhone = this[OwnersTable.contactPhone],
    secondaryContactName = this[OwnersTable.secondaryContactName],
    secondaryContactPhone = this[OwnersTable.secondaryContactPhone],
    secondaryContactEmail = this[OwnersTable.secondaryContactEmail],
    website = this[OwnersTable.website],
    notes = this[OwnersTable.notes],
    schemaName = this[OwnersTable.tenantSchemaName],
    isActive = this[OwnersTable.isActive],
    createdAt = this[OwnersTable.createdAt]
)
