package com.aether.x.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.aether.x.R
import com.aether.x.core.locale.AppLanguage
import com.aether.x.core.locale.LocaleManager
import com.aether.x.ui.components.SectionCard

/**
 * Pilihan bahasa aplikasi: Indonesia / English.
 *
 * Nama bahasa selalu ditampilkan dalam bahasa aslinya (bukan diterjemahkan)
 * agar pengguna tetap bisa menemukan pilihannya walau UI sedang memakai
 * bahasa yang tidak dipahami. Setelah dipilih, Activity di-recreate supaya
 * seluruh teks langsung berganti tanpa perlu membuka ulang aplikasi.
 *
 * App language picker. Language names are shown in their own language so the
 * option stays findable. The activity is recreated so every string refreshes.
 */
@Composable
fun LanguageSettingsSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(LocaleManager.current(context)) }

    SectionCard(
        title = stringResource(R.string.settings_language_title),
        watermarkIcon = Icons.Outlined.Translate,
        modifier = modifier,
    ) {
        SettingGroup(label = stringResource(R.string.settings_language_label)) {
            ChoiceRow {
                AppLanguage.entries.forEach { language ->
                    ChoicePill(language.nativeName, selected == language) {
                        if (selected != language) {
                            selected = language
                            LocaleManager.set(context, language)
                            context.findActivity()?.recreate()
                        }
                    }
                }
            }
        }
        Text(
            text = stringResource(R.string.settings_language_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
