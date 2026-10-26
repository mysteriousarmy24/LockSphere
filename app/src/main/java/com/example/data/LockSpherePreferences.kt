package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "locksphere_prefs")

class LockSpherePreferences(private val context: Context) {

    companion object {
        val KEY_PROTECTION_ENABLED = booleanPreferencesKey("is_protection_enabled")
        val KEY_PROTECTED_PACKAGES = stringSetPreferencesKey("protected_packages")
        val KEY_RELOCK_TIMEOUT_SECONDS = intPreferencesKey("relock_timeout_seconds")
        val KEY_LOCK_ON_SCREEN_OFF = booleanPreferencesKey("lock_on_screen_off")
        val KEY_BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val KEY_PIN_HASH = stringPreferencesKey("pin_hash")
        val KEY_PIN_SALT = stringPreferencesKey("pin_salt")
        val KEY_START_ON_BOOT = booleanPreferencesKey("start_on_boot")
        val KEY_VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val KEY_FIRST_RUN_COMPLETED = booleanPreferencesKey("first_run_completed")
    }

    val isProtectionEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_PROTECTION_ENABLED] ?: false
    }

    val protectedPackagesFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_PROTECTED_PACKAGES] ?: emptySet()
    }

    val relockTimeoutSecondsFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_RELOCK_TIMEOUT_SECONDS] ?: 0 // 0 = Immediate
    }

    val lockOnScreenOffFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_LOCK_ON_SCREEN_OFF] ?: true
    }

    val biometricEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_BIOMETRIC_ENABLED] ?: true
    }

    val pinHashFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_PIN_HASH]
    }

    val pinSaltFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_PIN_SALT]
    }

    val startOnBootFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_START_ON_BOOT] ?: true
    }

    val vibrationEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_VIBRATION_ENABLED] ?: true
    }

    suspend fun setProtectionEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PROTECTION_ENABLED] = enabled
        }
    }

    suspend fun toggleAppProtection(packageName: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_PROTECTED_PACKAGES]?.toMutableSet() ?: mutableSetOf()
            if (current.contains(packageName)) {
                current.remove(packageName)
            } else {
                current.add(packageName)
            }
            prefs[KEY_PROTECTED_PACKAGES] = current
        }
    }

    suspend fun setProtectedPackages(packages: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PROTECTED_PACKAGES] = packages
        }
    }

    suspend fun setRelockTimeoutSeconds(seconds: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_RELOCK_TIMEOUT_SECONDS] = seconds
        }
    }

    suspend fun setLockOnScreenOff(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LOCK_ON_SCREEN_OFF] = enabled
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BIOMETRIC_ENABLED] = enabled
        }
    }

    suspend fun setPin(hash: String, salt: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PIN_HASH] = hash
            prefs[KEY_PIN_SALT] = salt
        }
    }

    suspend fun removePin() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_PIN_HASH)
            prefs.remove(KEY_PIN_SALT)
        }
    }

    suspend fun setStartOnBoot(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_START_ON_BOOT] = enabled
        }
    }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_VIBRATION_ENABLED] = enabled
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
