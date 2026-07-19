package com.aether.x.core.booster

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object GameBoosterSessionHolder {

    private val _session = MutableStateFlow<GameBoosterSession?>(null)
    val session: StateFlow<GameBoosterSession?> = _session.asStateFlow()

    fun start(session: GameBoosterSession) {
        _session.value = session
    }

    fun update(transform: (GameBoosterSession) -> GameBoosterSession) {
        _session.update { current -> current?.let(transform) }
    }

    fun clear() {
        _session.value = null
    }
}
