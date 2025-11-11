package com.privacyguard.batteryanalyzer.firewall

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private const val DATA_STORE_NAME = "firewall_prefs"
private val Context.firewallDataStore by preferencesDataStore(name = DATA_STORE_NAME)

class FirewallPreferencesDataSource(context: Context) {

    private val dataStore: DataStore<Preferences> = context.applicationContext.firewallDataStore

    val preferencesFlow: Flow<FirewallPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            FirewallPreferences(
                isEnabled = preferences[Keys.FIREWALL_ENABLED] ?: false,
                isBlocking = preferences[Keys.IS_BLOCKING] ?: false,
                reactivateAt = preferences[Keys.REACTIVATE_AT],
                blockedPackages = preferences[Keys.BLOCKED_PACKAGES] ?: emptySet()
            )
        }

    suspend fun setState(
        isEnabled: Boolean,
        isBlocking: Boolean,
        reactivateAt: Long?,
        blockedPackages: Set<String>? = null
    ) {
        dataStore.edit { preferences ->
            preferences[Keys.FIREWALL_ENABLED] = isEnabled
            preferences[Keys.IS_BLOCKING] = isBlocking
            if (reactivateAt == null) {
                preferences.remove(Keys.REACTIVATE_AT)
            } else {
                preferences[Keys.REACTIVATE_AT] = reactivateAt
            }
            if (blockedPackages != null) {
                if (blockedPackages.isEmpty()) {
                    preferences.remove(Keys.BLOCKED_PACKAGES)
                } else {
                    preferences[Keys.BLOCKED_PACKAGES] = blockedPackages
                }
            }
        }
    }

    suspend fun setBlocking(blocking: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.IS_BLOCKING] = blocking
        }
    }

    suspend fun setBlockedPackages(blockedPackages: Set<String>) {
        dataStore.edit { preferences ->
            if (blockedPackages.isEmpty()) {
                preferences.remove(Keys.BLOCKED_PACKAGES)
            } else {
                preferences[Keys.BLOCKED_PACKAGES] = blockedPackages
            }
        }
    }

    private object Keys {
        val FIREWALL_ENABLED = booleanPreferencesKey("firewall_enabled")
        val IS_BLOCKING = booleanPreferencesKey("firewall_is_blocking")
        val REACTIVATE_AT = longPreferencesKey("firewall_reactivate_at")
        val BLOCKED_PACKAGES = stringSetPreferencesKey("firewall_blocked_packages")
    }
}

data class FirewallPreferences(
    val isEnabled: Boolean,
    val isBlocking: Boolean,
    val reactivateAt: Long?,
    val blockedPackages: Set<String>
)
