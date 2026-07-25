package com.aether.x.core.permission

import com.aether.x.core.shizuku.ShizukuConnectionState

enum class PrivilegeBackend { SHIZUKU, ROOT, NONE }

enum class RequestState { IDLE, REQUESTING }

enum class RequestFailureReason {
    SHIZUKU_NOT_INSTALLED,
    SHIZUKU_SERVICE_NOT_RUNNING,
    SHIZUKU_PERMISSION_DENIED,
    ROOT_DENIED_OR_UNAVAILABLE,
    ROOT_ALREADY_IN_PROGRESS,
}

sealed interface RequestFeedback {
    data class Failed(val backend: PrivilegeBackend, val reason: RequestFailureReason) : RequestFeedback
    data class Granted(val backend: PrivilegeBackend) : RequestFeedback
}

data class PrivilegeStatus(
    val shizukuState: ShizukuConnectionState = ShizukuConnectionState.ServiceNotRunning,
    val rootAvailable: Boolean? = null,
    val rootGranted: Boolean = false,
    val checkingRoot: Boolean = false,
    val writeSettingsGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val preferredBackend: PrivilegeBackend = PrivilegeBackend.NONE,

    val rootRequestState: RequestState = RequestState.IDLE,
) {
    val shizukuGranted: Boolean get() = shizukuState == ShizukuConnectionState.Connected

    val activeBackend: PrivilegeBackend
        get() = when (preferredBackend) {
            PrivilegeBackend.SHIZUKU -> if (shizukuGranted) PrivilegeBackend.SHIZUKU else PrivilegeBackend.NONE
            PrivilegeBackend.ROOT -> if (rootGranted) PrivilegeBackend.ROOT else PrivilegeBackend.NONE
            PrivilegeBackend.NONE -> when {
                shizukuGranted -> PrivilegeBackend.SHIZUKU
                rootGranted -> PrivilegeBackend.ROOT
                else -> PrivilegeBackend.NONE
            }
        }

    val hasAccess: Boolean get() = activeBackend != PrivilegeBackend.NONE

    val hasAllSupportingPermissions: Boolean
        get() = writeSettingsGranted && overlayGranted && notificationsGranted
}
