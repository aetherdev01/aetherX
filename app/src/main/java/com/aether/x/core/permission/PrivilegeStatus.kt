package com.aether.x.core.permission

import com.aether.x.core.adb.AdbConnectionState

enum class PrivilegeBackend { ADB, ROOT, NONE }

enum class RequestState { IDLE, REQUESTING }

enum class RequestFailureReason {
    ADB_WIRELESS_DEBUGGING_OFF,
    ADB_AUTO_DISCOVERY_TIMEOUT,
    ADB_PAIRING_CODE_INVALID_OR_EXPIRED,
    ADB_HOST_UNREACHABLE,

    ADB_CONNECT_AFTER_PAIRING_FAILED,
    ADB_SHELL_REJECTED_NEEDS_REPAIR,
    ADB_UNKNOWN,
    ADB_ALREADY_IN_PROGRESS,
    ROOT_DENIED_OR_UNAVAILABLE,
    ROOT_ALREADY_IN_PROGRESS,
}

sealed interface RequestFeedback {
    data class Failed(val backend: PrivilegeBackend, val reason: RequestFailureReason) : RequestFeedback
    data class Granted(val backend: PrivilegeBackend) : RequestFeedback
}

data class PrivilegeStatus(
    val adbState: AdbConnectionState = AdbConnectionState.NotPaired,
    val rootAvailable: Boolean? = null,
    val rootGranted: Boolean = false,
    val checkingRoot: Boolean = false,
    val writeSettingsGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val preferredBackend: PrivilegeBackend = PrivilegeBackend.NONE,

    val adbRequestState: RequestState = RequestState.IDLE,
    val rootRequestState: RequestState = RequestState.IDLE,
) {
    val adbGranted: Boolean get() = adbState == AdbConnectionState.Connected

    val activeBackend: PrivilegeBackend
        get() = when (preferredBackend) {
            PrivilegeBackend.ADB -> if (adbGranted) PrivilegeBackend.ADB else PrivilegeBackend.NONE
            PrivilegeBackend.ROOT -> if (rootGranted) PrivilegeBackend.ROOT else PrivilegeBackend.NONE
            PrivilegeBackend.NONE -> when {
                adbGranted -> PrivilegeBackend.ADB
                rootGranted -> PrivilegeBackend.ROOT
                else -> PrivilegeBackend.NONE
            }
        }

    val hasAccess: Boolean get() = activeBackend != PrivilegeBackend.NONE

    val hasAllSupportingPermissions: Boolean
        get() = writeSettingsGranted && overlayGranted && notificationsGranted
}
