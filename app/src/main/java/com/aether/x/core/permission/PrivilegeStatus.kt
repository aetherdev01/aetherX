package com.aether.x.core.permission

/**
 * v3.1 PURE ROOT: AetherX sekarang HANYA mendukung akses Root (Magisk/
 * KernelSU/APatch). Seluruh backend non-root (Shizuku, ADB) sudah DIHAPUS
 * TOTAL dari aplikasi — tidak ada lagi konsep "preferred backend" karena
 * cuma ada satu backend yang mungkin: Root atau tidak sama sekali.
 */
enum class RequestState { IDLE, REQUESTING }

enum class RequestFailureReason {
    ROOT_DENIED_OR_UNAVAILABLE,
    ROOT_ALREADY_IN_PROGRESS,
}

sealed interface RequestFeedback {
    data class Failed(val reason: RequestFailureReason) : RequestFeedback
    data object Granted : RequestFeedback
}

data class PrivilegeStatus(
    val rootAvailable: Boolean? = null,
    val rootGranted: Boolean = false,
    val checkingRoot: Boolean = false,
    val writeSettingsGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val notificationsGranted: Boolean = false,

    val rootRequestState: RequestState = RequestState.IDLE,
) {
    val hasAccess: Boolean get() = rootGranted

    val hasAllSupportingPermissions: Boolean
        get() = writeSettingsGranted && overlayGranted && notificationsGranted
}
