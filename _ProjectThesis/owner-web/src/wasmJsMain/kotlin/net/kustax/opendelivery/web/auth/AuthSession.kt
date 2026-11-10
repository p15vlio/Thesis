package net.kustax.opendelivery.web.auth

import kotlinx.browser.localStorage

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val role: String,
    val schemaName: String?
) {
    fun save() {
        localStorage.setItem("od_token", accessToken)
        localStorage.setItem("od_refresh", refreshToken)
        localStorage.setItem("od_role", role)
        localStorage.setItem("od_schema", schemaName ?: "")
    }

    companion object {
        fun load(): AuthSession? {
            val token = localStorage.getItem("od_token") ?: return null
            val refresh = localStorage.getItem("od_refresh") ?: return null
            val role = localStorage.getItem("od_role") ?: return null
            val schema = localStorage.getItem("od_schema")?.takeIf { it.isNotEmpty() }
            return AuthSession(token, refresh, role, schema)
        }

        fun clear() {
            listOf("od_token", "od_refresh", "od_role", "od_schema").forEach {
                localStorage.removeItem(it)
            }
        }
    }
}
