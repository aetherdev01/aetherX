package com.aether.x.core.ads

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.Window
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.annotation.RequiresApi
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.WeakHashMap

/**
 * Memblokir tombol/gesture BACK selama activity iklan (AdMob / Unity Ads)
 * sedang tampil, supaya user tidak sengaja keluar dari iklan sebelum selesai.
 * Iklan hanya bisa ditutup lewat tombol close/skip milik SDK iklannya sendiri.
 *
 * Activity iklan dibuat oleh SDK (bukan MainActivity), jadi back-nya tidak bisa
 * di-override dari UI app. Solusinya: pantau semua activity lewat
 * [Application.ActivityLifecycleCallbacks] lalu pasang pemblokir di activity
 * iklan saja:
 *  - API 33+ : [OnBackInvokedCallback] prioritas OVERLAY. Wajib karena manifest
 *              memakai `enableOnBackInvokedCallback="true"`, jadi back gesture
 *              tidak lagi lewat KeyEvent.
 *  - API < 33: wrap [Window.Callback] dan telan KEYCODE_BACK.
 *
 * Pengaman: blokir otomatis lepas setelah [MAX_BLOCK_MILLIS] supaya user tidak
 * terjebak kalau iklan nge-freeze tanpa tombol close.
 */
object AdBackBlocker : Application.ActivityLifecycleCallbacks {

    private val AD_PACKAGES = listOf(
        "com.google.android.gms.ads.",
        "com.unity3d.services.ads.",
        "com.unity3d.ads.",
    )

    // Activity milik SDK iklan yang bukan tampilan iklan.
    private val IGNORED_SUFFIXES = listOf(
        "NotificationHandlerActivity",
        "OutOfContextTestingActivity",
    )

    /** Batas maksimal blokir back (ms). Iklan normal jauh di bawah ini. */
    private const val MAX_BLOCK_MILLIS = 90_000L

    private class State {
        val startedAt: Long = SystemClock.elapsedRealtime()
        var windowWrapped = false
        var backCallback: Any? = null

        val expired: Boolean
            get() = SystemClock.elapsedRealtime() - startedAt > MAX_BLOCK_MILLIS
    }

    private val states = WeakHashMap<Activity, State>()

    fun install(app: Application) {
        app.unregisterActivityLifecycleCallbacks(this)
        app.registerActivityLifecycleCallbacks(this)
    }

    private fun isAdActivity(activity: Activity): Boolean {
        val name = activity.javaClass.name
        if (IGNORED_SUFFIXES.any { name.endsWith(it) }) return false
        return AD_PACKAGES.any { name.startsWith(it) }
    }

    private fun attach(activity: Activity) {
        if (!isAdActivity(activity)) return

        val state = states.getOrPut(activity) { State() }

        if (Build.VERSION.SDK_INT >= 33 && state.backCallback == null) {
            registerBackCallback(activity, state)
        }
        if (!state.windowWrapped) {
            wrapWindowCallback(activity, state)
        }
    }

    @RequiresApi(33)
    private fun registerBackCallback(activity: Activity, state: State) {
        val callback = OnBackInvokedCallback {
            if (state.expired) {
                // Pengaman: lepas blokir lalu teruskan back ke SDK iklan.
                unregisterBackCallback(activity, state)
                @Suppress("DEPRECATION")
                activity.onBackPressed()
            }
            // Selain itu: dikonsumsi, back diabaikan.
        }
        state.backCallback = callback
        activity.onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_OVERLAY,
            callback,
        )
    }

    @RequiresApi(33)
    private fun unregisterBackCallback(activity: Activity, state: State) {
        (state.backCallback as? OnBackInvokedCallback)?.let {
            activity.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it)
        }
        state.backCallback = null
    }

    private fun wrapWindowCallback(activity: Activity, state: State) {
        val original: Window.Callback = activity.window.callback ?: return

        // Proxy dipakai (bukan delegasi manual) supaya semua method Window.Callback,
        // termasuk default method di API baru, tetap diteruskan apa adanya.
        val handler = InvocationHandler { _, method, args ->
            val event = args?.firstOrNull() as? KeyEvent
            if (method.name == "dispatchKeyEvent" &&
                event?.keyCode == KeyEvent.KEYCODE_BACK &&
                !state.expired
            ) {
                return@InvocationHandler true
            }
            try {
                method.invoke(original, *(args ?: emptyArray()))
            } catch (e: InvocationTargetException) {
                throw e.targetException ?: e
            }
        }

        val wrapped = Proxy.newProxyInstance(
            Window.Callback::class.java.classLoader,
            arrayOf(Window.Callback::class.java),
            handler,
        ) as Window.Callback

        activity.window.callback = wrapped
        state.windowWrapped = true
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = attach(activity)

    // Jaga-jaga kalau SDK mengganti Window.Callback / membuat ulang dispatcher belakangan.
    override fun onActivityResumed(activity: Activity) = attach(activity)

    override fun onActivityDestroyed(activity: Activity) {
        states.remove(activity)
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
