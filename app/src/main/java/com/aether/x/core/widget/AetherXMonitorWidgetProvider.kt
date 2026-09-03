package com.aether.x.core.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.view.View
import android.widget.RemoteViews
import com.aether.x.MainActivity
import com.aether.x.R
import com.aether.x.core.monitor.RootSystemMonitor
import com.aether.x.core.permission.PrivilegeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Widget layar utama (v3.5) — kartu kecil menampilkan CPU/GPU load
 * sekilas tanpa buka app, dipasang lewat launcher (long-press home
 * screen > Widget > AetherX).
 *
 * Root-gated LEWAT [PrivilegeManager.getExecutor] (sinkron, return null
 * kalau root belum granted) — SENGAJA tidak memakai `RootShellExecutor()`
 * langsung, supaya refresh widget di background TIDAK PERNAH memicu
 * popup permintaan izin `su` yang mengejutkan user saat sedang di home
 * screen. Kalau root belum di-grant lewat alur normal di dalam app,
 * widget cukup menampilkan status "Butuh akses Root" + tap untuk buka
 * app, bukan memaksa prompt.
 *
 * CPU load dibaca dari [RootSystemMonitor] (native /proc/stat,
 * world-readable, TIDAK butuh root) supaya tetap berguna walau root
 * belum aktif — hanya panel GPU yang butuh root (lihat KDoc
 * RootSystemMonitor.readGpuSnapshotViaRoot).
 */
class AetherXMonitorWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.aether.x.action.WIDGET_REFRESH"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> refreshWidget(context, appWidgetManager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_REFRESH) return
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, AetherXMonitorWidgetProvider::class.java))
        ids.forEach { id -> refreshWidget(context, manager, id) }
    }

    private fun refreshWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val views = buildRemoteViews(context.applicationContext, widgetId)
                withContext(Dispatchers.Main) {
                    manager.updateAppWidget(widgetId, views)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun buildRemoteViews(context: Context, widgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_aetherx_monitor)

        val openAppIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent)

        val refreshIntent = PendingIntent.getBroadcast(
            context,
            widgetId,
            Intent(context, AetherXMonitorWidgetProvider::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshIntent)

        // CPU: native /proc/stat, tidak butuh root. Baca pertama setelah
        // resetDelta selalu -1 (belum ada sampel pembanding), jadi kasih
        // jeda kecil lalu baca sampel kedua — lihat KDoc RootSystemMonitor.
        RootSystemMonitor.resetDelta()
        delay(300)
        val cpu = RootSystemMonitor.readCpuSnapshot()

        val executor = PrivilegeManager.getExecutor()
        val gpuPercent = if (executor != null) {
            RootSystemMonitor.readGpuSnapshotViaRoot()?.loadPercent
        } else {
            null
        }

        val rootAvailable = executor != null
        views.setViewVisibility(R.id.widget_status, if (rootAvailable) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_metrics, if (rootAvailable) View.VISIBLE else View.GONE)

        views.setTextViewText(
            R.id.widget_cpu_value,
            cpu?.aggregatePercent?.takeIf { it >= 0f }?.let { "${it.roundToInt()}%" } ?: "—",
        )
        views.setTextViewText(
            R.id.widget_gpu_value,
            gpuPercent?.let { "${it.roundToInt()}%" } ?: "—",
        )
        views.setTextViewText(
            R.id.widget_updated,
            context.getString(
                R.string.widget_last_updated,
                DateFormat.format("HH:mm", System.currentTimeMillis()),
            ),
        )
        return views
    }
}
