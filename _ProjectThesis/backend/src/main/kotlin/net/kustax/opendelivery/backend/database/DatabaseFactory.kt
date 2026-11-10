package net.kustax.opendelivery.backend.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

object DatabaseFactory {

    fun init() {
        val config = HikariConfig().apply {
            jdbcUrl = System.getenv("JDBC_URL") ?: "jdbc:postgresql://localhost:5432/opendelivery"
            username = System.getenv("DB_USER") ?: "postgres"
            password = System.getenv("DB_PASSWORD") ?: error("DB_PASSWORD environment variable must be set")
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 10
            isAutoCommit = false
            // READ_COMMITTED (the PostgreSQL default). Under REPEATABLE_READ a concurrent write to a row this
            // transaction also updates fails with an unretried 40001 serialization error. Read-modify-write
            // paths do not rely on a snapshot: order status changes are compare-and-set UPDATEs (they affect 0
            // rows when the order already moved on) and payments take a row lock on the order first.
            transactionIsolation = "TRANSACTION_READ_COMMITTED"
        }
        Database.connect(HikariDataSource(config))
    }

    suspend fun <T> dbQuery(block: suspend Transaction.() -> T): T =
        suspendTransaction {
            // search_path is only ever changed with SET LOCAL (tenantQuery, SchemaManager), which ends with
            // its transaction, so a pooled connection always starts on the default path. Pinning it to
            // public here also keeps a platform query correct if one is ever nested inside a tenantQuery.
            exec("SET LOCAL search_path TO public")
            block()
        }
}
