package com.aether.x.core.adb

sealed interface AdbConnectionState {

    data object NotPaired : AdbConnectionState

    data object PairedNotConnected : AdbConnectionState

    data object SearchingForPairing : AdbConnectionState

    data class PairingFound(val host: String, val port: Int) : AdbConnectionState

    data object Pairing : AdbConnectionState

    data object Connecting : AdbConnectionState

    data object Connected : AdbConnectionState

    data class Failed(val reason: AdbFailureReason, val detail: String? = null) : AdbConnectionState
}

enum class AdbFailureReason {

    WIRELESS_DEBUGGING_OFF,

    AUTO_DISCOVERY_TIMEOUT,

    PAIRING_CODE_INVALID_OR_EXPIRED,

    HOST_UNREACHABLE,

    CONNECT_AFTER_PAIRING_FAILED,

    SHELL_REJECTED_NEEDS_REPAIR,

    UNKNOWN,
}
