package com.aether.x.core.ads

import java.net.InetAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * v3.6 — mendeteksi apakah domain iklan yang dipakai app ini diblokir di
 * level jaringan (DNS pribadi pemblokir iklan, file hosts hasil AdAway, atau
 * VPN pemblokir iklan).
 *
 * Cara kerja: resolve beberapa host iklan + satu host kontrol. Host iklan
 * dianggap DIBLOKIR kalau resolve-nya gagal (NXDOMAIN) atau mengarah ke
 * alamat sink (0.0.0.0 / 127.x / ::), SEMENTARA host kontrol berhasil
 * resolve (artinya jaringan hidup — offline TIDAK dihitung sebagai blokir).
 * Perlu minimal [BLOCKED_THRESHOLD] host iklan terblokir supaya satu domain
 * yang kebetulan down tidak memicu positif palsu.
 *
 * Catatan: ini hanya DETEKSI. App tidak mengubah DNS/hosts/VPN milik user.
 * Tindak lanjutnya (dialog di `AdBlockGate`) hanya berlaku untuk pengguna
 * gratis; member tidak pernah melihat iklan jadi tidak pernah dicek.
 */
object AdBlockGuard {

    data class Result(
        val blocked: Boolean,
        /** Host iklan yang terdeteksi diblokir (untuk log/diagnosa). */
        val blockedHosts: List<String> = emptyList(),
    )

    private val AD_HOSTS = listOf(
        "googleads.g.doubleclick.net",
        "pagead2.googlesyndication.com",
        "config.unityads.unity3d.com",
    )

    private val CONTROL_HOSTS = listOf(
        "connectivitycheck.gstatic.com",
        "www.google.com",
    )

    private const val LOOKUP_TIMEOUT_MS = 3_500L
    private const val BLOCKED_THRESHOLD = 2

    private enum class Lookup { OK, SINKHOLE, UNRESOLVED, TIMEOUT }

    suspend fun check(): Result = withContext(Dispatchers.IO) {
        coroutineScope {
            val control = CONTROL_HOSTS.map { host -> async { lookup(host) } }.awaitAll()
            // Jaringan tidak bisa dipastikan hidup -> jangan menuduh pemblokir iklan.
            if (control.none { it == Lookup.OK }) return@coroutineScope Result(blocked = false)

            val results = AD_HOSTS.map { host -> host to async { lookup(host) } }
                .map { (host, deferred) -> host to deferred.await() }
            val blockedHosts = results
                .filter { (_, r) -> r == Lookup.SINKHOLE || r == Lookup.UNRESOLVED }
                .map { it.first }
            Result(blocked = blockedHosts.size >= BLOCKED_THRESHOLD, blockedHosts = blockedHosts)
        }
    }

    // Scope terpisah: InetAddress.getAllByName bersifat blocking & tidak bisa
    // dibatalkan, jadi lookup yang kelamaan dibiarkan selesai sendiri di sini
    // tanpa menahan coroutineScope pemanggil.
    private val lookupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private suspend fun lookup(host: String): Lookup {
        val deferred = lookupScope.async { runCatching { InetAddress.getAllByName(host).toList() } }
        val outcome = withTimeoutOrNull(LOOKUP_TIMEOUT_MS) { deferred.await() }
            ?: return Lookup.TIMEOUT

        val addresses = outcome.getOrNull() ?: return Lookup.UNRESOLVED
        if (addresses.isEmpty()) return Lookup.UNRESOLVED
        val allSink = addresses.all { it.isAnyLocalAddress || it.isLoopbackAddress }
        return if (allSink) Lookup.SINKHOLE else Lookup.OK
    }
}
