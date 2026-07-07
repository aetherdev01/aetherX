package com.aether.x.ui.components

import android.content.Context
import android.widget.Toast

/**
 * FITUR BARU (lihat perintah rework — "tambahkan Toast di semua Fitur
 * supaya lebih gampang"): extension function tipis di atas
 * [Toast.makeText] SUPAYA setiap ViewModel di app ini (semuanya sudah
 * `AndroidViewModel`, punya `getApplication()`) bisa memicu toast native
 * Android dari mana saja — termasuk dari dalam coroutine `viewModelScope`
 * SETELAH hasil sebuah aksi shell diketahui — tanpa perlu Activity context
 * atau state Compose tambahan (`SnackbarHostState`) di tiap layar.
 *
 * SENGAJA memakai Toast Android native (bukan menyatukan semua feedback ke
 * satu SnackbarHostState global) supaya perubahan ini MINIMAL INVASIF ke
 * state management yang sudah ada di tiap ViewModel — tiap ViewModel tetap
 * boleh mempertahankan `state.message` miliknya sendiri (dibaca inline oleh
 * Composable-nya, mis. untuk teks error yang butuh tetap terlihat lebih
 * lama), toast ini murni LAPISAN TAMBAHAN yang membuat hasil aksi terasa
 * lebih instan/"gampang" terlihat tanpa harus melihat ke bagian tertentu
 * layar.
 *
 * Durasi SELALU [Toast.LENGTH_SHORT] (~2 detik) supaya konsisten di semua
 * fitur — tidak ada toast yang terasa lebih "penting" dari yang lain hanya
 * karena durasinya beda.
 */
fun Context.showAetherToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
