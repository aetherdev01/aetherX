package com.aether.x.core.adb

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object WirelessDebuggingMonitor {

    private const val KEY_ADB_WIFI_ENABLED = "adb_wifi_enabled"

    private val _state = MutableStateFlow(false)
    val state: StateFlow<Boolean> = _state.asStateFlow()

    private var registered = false
    private var observer: ContentObserver? = null

    fun isEnabled(context: Context): Boolean = runCatching {
        Settings.Global.getInt(context.contentResolver, KEY_ADB_WIFI_ENABLED, 0) == 1
    }.getOrDefault(false)

    fun refresh(context: Context) {
        _state.value = isEnabled(context.applicationContext)
    }

    fun startObserving(context: Context) {
        if (registered) return
        val appContext = context.applicationContext
        refresh(appContext)
        val handler = Handler(Looper.getMainLooper())
        val contentObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                val wasEnabled = _state.value
                val nowEnabled = isEnabled(appContext)
                _state.value = nowEnabled
                if (!wasEnabled && nowEnabled) {

                    AdbConnectionManager.autoReconnect()
                }
            }
        }
        runCatching {
            appContext.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(KEY_ADB_WIFI_ENABLED),
                false,
                contentObserver,
            )
            observer = contentObserver
            registered = true
        }
    }

    fun stopObserving(context: Context) {
        val current = observer ?: return
        runCatching { context.applicationContext.contentResolver.unregisterContentObserver(current) }
        observer = null
        registered = false
    }
}
