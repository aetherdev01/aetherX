package com.aether.x.ui.components

import android.content.Context
import android.widget.Toast

fun Context.showAetherToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
