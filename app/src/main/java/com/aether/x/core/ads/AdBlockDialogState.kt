package com.aether.x.core.ads

import android.content.Context
import com.aether.x.data.AetherXPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object AdBlockDialogState {
    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    private val _openMembershipRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openMembershipRequests: SharedFlow<Unit> = _openMembershipRequests.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    @Volatile
    private var shownThisSession = false

    @Volatile
    private var currentlyShownSignalKey: String? = null

    fun requestShow(context: Context, signals: AdBlockDetector.AdBlockSignals) {
        if (shownThisSession) return
        val appContext = context.applicationContext
        scope.launch {
            mutex.withLock {
                if (shownThisSession) return@withLock
                val preferences = AetherXPreferences(appContext)
                val acknowledged = preferences.getAdBlockAcknowledgedSignal()
                if (acknowledged == signals.signalKey) return@withLock

                shownThisSession = true
                currentlyShownSignalKey = signals.signalKey
                _visible.value = true
            }
        }
    }

    fun dismiss(context: Context) {
        _visible.value = false
        val signalKey = currentlyShownSignalKey ?: return
        currentlyShownSignalKey = null
        val appContext = context.applicationContext
        scope.launch {
            runCatching {
                AetherXPreferences(appContext).setAdBlockAcknowledgedSignal(signalKey)
            }
        }
    }

    fun requestOpenMembership() {
        _openMembershipRequests.tryEmit(Unit)
    }
}
