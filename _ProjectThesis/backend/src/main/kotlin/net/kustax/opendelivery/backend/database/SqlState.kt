package net.kustax.opendelivery.backend.database

// PostgreSQL SQLSTATE codes the backend reacts to (https://www.postgresql.org/docs/current/errcodes-appendix.html)
object SqlState {
    const val FOREIGN_KEY_VIOLATION = "23503"
    const val UNIQUE_VIOLATION = "23505"
}
