package com.aether.x.core.overlay

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * [LifecycleOwner] + [ViewModelStoreOwner] + [SavedStateRegistryOwner] manual
 * untuk `ComposeView` yang dipasang LANGSUNG ke [android.view.WindowManager]
 * dari sebuah [android.app.Service] (overlay floating) — BUKAN dari
 * [android.app.Activity] biasa.
 *
 * KENAPA INI DIPERLUKAN (dipakai oleh
 * [com.aether.x.core.overlay.GameBoosterOverlayService] untuk floating
 * sidebar Game Booster — lihat perintah rework: "saat buka game game
 * booster jadi side bar/floating"): `ComposeView` MEWAJIBKAN tiga owner ini
 * tersedia di view tree-nya sebelum konten Compose apa pun bisa
 * di-render — normalnya ini otomatis disediakan oleh
 * `ComponentActivity`/`Fragment` lewat `setContent {}`. Tapi floating
 * overlay TIDAK PUNYA Activity/Fragment di belakangnya sama sekali (ia
 * hanya `View` biasa yang di-attach manual lewat
 * `WindowManager.addView(view, layoutParams)` dari [android.app.Service]),
 * jadi ketiga owner ini harus disediakan MANUAL lewat kelas ini — tanpanya,
 * `ComposeView.setContent {}` akan crash dengan
 * `IllegalStateException: ViewTreeLifecycleOwner not found`.
 *
 * Cara pakai (lihat [GameBoosterOverlayService] untuk contoh lengkap):
 * ```
 * val owner = ComposeOverlayLifecycleOwner()
 * owner.attachToDecorView(composeView)
 * owner.onCreate()
 * owner.onStart()
 * owner.onResume()
 * windowManager.addView(composeView, layoutParams)
 * // ...
 * // saat overlay dihentikan:
 * windowManager.removeView(composeView)
 * owner.onDestroy()
 * ```
 */
internal class ComposeOverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore = ViewModelStore()

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    /**
     * Pasang ketiga owner ini ke [decorView] (root `ComposeView` overlay) —
     * WAJIB dipanggil SEBELUM [decorView] di-attach ke [android.view.WindowManager]
     * dan sebelum [ComposeView.setContent] dipanggil.
     */
    fun attachToDecorView(decorView: View) {
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        decorView.setViewTreeLifecycleOwner(this)
        decorView.setViewTreeViewModelStoreOwner(this)
        decorView.setViewTreeSavedStateRegistryOwner(this)
    }

    fun onCreate() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun onStart() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    fun onResume() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onPause() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    fun onStop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    /** Dipanggil saat overlay dilepas dari [android.view.WindowManager] — melepas ViewModelStore juga. */
    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
    }
}
