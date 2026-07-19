package com.aether.x.core.adb

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.adbDataStore by preferencesDataStore(name = "aetherx_adb_prefs")

class AdbPreferences(private val context: Context) {

    private object Keys {
        val HOST = stringPreferencesKey("adb_connect_host")
        val PORT = intPreferencesKey("adb_connect_port")
    }

    suspend fun saveHostPort(host: String, port: Int) {
        context.adbDataStore.edit { prefs ->
            prefs[Keys.HOST] = host
            prefs[Keys.PORT] = port
        }
    }

    suspend fun getSavedHostPort(): Pair<String, Int>? {
        val prefs = context.adbDataStore.data.first()
        val host = prefs[Keys.HOST] ?: return null
        val port = prefs[Keys.PORT] ?: return null
        return host to port
    }

    suspend fun clearHostPort() {
        context.adbDataStore.edit { prefs ->
            prefs.remove(Keys.HOST)
            prefs.remove(Keys.PORT)
        }
    }
}
