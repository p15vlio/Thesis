package net.kustax.opendelivery.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.deviceDataStore: DataStore<Preferences> by preferencesDataStore(name = "device_prefs")

data class DeviceRegistrationState(
    val isRegistered: Boolean = false,
    val serverUrl: String = "",
    val deviceId: String = "",
    val schemaName: String = "",
    val activationCode: String = "",
    val propertyId: String = "",
    val propertyName: String = "",
    val deviceName: String = "",
    val token: String = ""
)

class DevicePreferences(private val context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs = EncryptedSharedPreferences.create(
        context,
        "secure_token_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private object Keys {
        val IS_REGISTERED = booleanPreferencesKey("is_registered")
        val SERVER_URL = stringPreferencesKey("server_url")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val SCHEMA_NAME = stringPreferencesKey("schema_name")
        val ACTIVATION_CODE = stringPreferencesKey("activation_code")
        val PROPERTY_ID = stringPreferencesKey("property_id")
        val PROPERTY_NAME = stringPreferencesKey("property_name")
        val DEVICE_NAME = stringPreferencesKey("device_name")
    }

    val registrationState: Flow<DeviceRegistrationState> = context.deviceDataStore.data.map { prefs ->
        val secureToken = securePrefs.getString("token", "") ?: ""
        DeviceRegistrationState(
            isRegistered = prefs[Keys.IS_REGISTERED] ?: false,
            serverUrl = prefs[Keys.SERVER_URL] ?: "",
            deviceId = prefs[Keys.DEVICE_ID] ?: "",
            schemaName = prefs[Keys.SCHEMA_NAME] ?: "",
            activationCode = prefs[Keys.ACTIVATION_CODE] ?: "",
            propertyId = prefs[Keys.PROPERTY_ID] ?: "",
            propertyName = prefs[Keys.PROPERTY_NAME] ?: "",
            deviceName = prefs[Keys.DEVICE_NAME] ?: "",
            token = secureToken
        )
    }

    suspend fun saveRegistration(
        serverUrl: String,
        deviceId: String,
        schemaName: String,
        activationCode: String,
        propertyId: String,
        propertyName: String,
        deviceName: String,
        token: String
    ) {
        securePrefs.edit().putString("token", token).apply()
        context.deviceDataStore.edit { prefs ->
            prefs[Keys.SERVER_URL] = serverUrl
            prefs[Keys.DEVICE_ID] = deviceId
            prefs[Keys.SCHEMA_NAME] = schemaName
            prefs[Keys.ACTIVATION_CODE] = activationCode
            prefs[Keys.PROPERTY_ID] = propertyId
            prefs[Keys.PROPERTY_NAME] = propertyName
            prefs[Keys.DEVICE_NAME] = deviceName
        }
    }

    suspend fun markRegistered() {
        context.deviceDataStore.edit { it[Keys.IS_REGISTERED] = true }
    }

    suspend fun clearRegistration() {
        securePrefs.edit().clear().apply()
        context.deviceDataStore.edit { it.clear() }
    }
}
