package com.aether.x.ui.update

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.aether.x.ui.theme.AccentAmber
import com.aether.x.ui.theme.AccentBlue
import com.aether.x.ui.theme.AccentGreen
import com.aether.x.ui.theme.AccentRed

/**
 * Parser markdown MINIMAL untuk deskripsi/changelog update (lihat
 * [UpdateInfo.description]) — dibuat khusus untuk kebutuhan ini alih-alih
 * memakai library pihak ketiga (mis. Markwon) supaya tidak menambah
 * dependency Gradle baru. Sengaja dibatasi ke sintaks paling umum dipakai
 * di changelog, gampang diketik admin langsung dari keyboard Telegram tanpa
 * perlu escape karakter apa pun:
 *
 * - `**tebal**`            -> bold
 * - `*miring*` / `_miring_` -> italic
 * - `- item` (awal baris)  -> bullet point ("• item")
 * - `[warna]teks[/warna]`  -> teks berwarna, `warna` salah satu dari
 *   `blue`, `green`, `amber`, `red` (dipetakan ke token warna tema AetherX
 *   yang sudah ada di [com.aether.x.ui.theme.Color]).
 *
 * Sintaks yang tidak dikenali (mis. tag warna dengan nama salah, atau
 * `**` yang tidak berpasangan) dibiarkan tampil apa adanya sebagai teks
 * literal — parser ini sengaja tidak pernah throw/crash pada input bebas
 * dari Firestore, karena input itu ditulis manual oleh admin dan rawan typo.
 */
// Urutan penting: [warna]...[/warna] diproses lebih dulu (span terluas),
// baru bold, baru italic, supaya tag bersarang seperti [blue]**tebal**[/blue]
// tetap terbaca benar dari luar ke dalam.
private val COLOR_TAG_REGEX = Regex("""\[(\w+)](.*?)\[/\1]""", RegexOption.DOT_MATCHES_ALL)
private val BOLD_REGEX = Regex("""\*\*(.+?)\*\*""")
private val ITALIC_REGEX = Regex("""(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)|_(.+?)_""")

/**
 * Ubah satu baris teks (sudah tanpa prefix bullet "- ") menjadi
 * [AnnotatedString] dengan span warna/bold/italic diterapkan.
 */
private fun parseInlineMarkdown(line: String): AnnotatedString = buildAnnotatedString {
    appendWithColorTags(line)
}

private fun AnnotatedString.Builder.appendWithColorTags(text: String) {
    var lastIndex = 0
    for (match in COLOR_TAG_REGEX.findAll(text)) {
        if (match.range.first > lastIndex) {
            appendWithBold(text.substring(lastIndex, match.range.first))
        }
        val colorName = match.groupValues[1]
        val inner = match.groupValues[2]
        val color = colorFor(colorName)
        if (color != null) {
            withStyle(SpanStyle(color = color)) { appendWithBold(inner) }
        } else {
            // Nama warna tidak dikenal -> tampilkan tag apa adanya, jangan
            // ditelan diam-diam supaya admin sadar ada typo di nama warna.
            appendWithBold(match.value)
        }
        lastIndex = match.range.last + 1
    }
    if (lastIndex < text.length) {
        appendWithBold(text.substring(lastIndex))
    }
}

private fun AnnotatedString.Builder.appendWithBold(text: String) {
    var lastIndex = 0
    for (match in BOLD_REGEX.findAll(text)) {
        if (match.range.first > lastIndex) {
            appendWithItalic(text.substring(lastIndex, match.range.first))
        }
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            appendWithItalic(match.groupValues[1])
        }
        lastIndex = match.range.last + 1
    }
    if (lastIndex < text.length) {
        appendWithItalic(text.substring(lastIndex))
    }
}

private fun AnnotatedString.Builder.appendWithItalic(text: String) {
    var lastIndex = 0
    for (match in ITALIC_REGEX.findAll(text)) {
        if (match.range.first > lastIndex) {
            append(text.substring(lastIndex, match.range.first))
        }
        val inner = match.groupValues[1].ifEmpty { match.groupValues[2] }
        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
            append(inner)
        }
        lastIndex = match.range.last + 1
    }
    if (lastIndex < text.length) {
        append(text.substring(lastIndex))
    }
}

/** Petakan nama warna dari tag `[warna]...[/warna]` ke token warna tema AetherX. */
private fun colorFor(name: String): Color? = when (name.lowercase()) {
    "blue" -> AccentBlue
    "green" -> AccentGreen
    "amber", "yellow" -> AccentAmber
    "red" -> AccentRed
    else -> null
}

/**
 * Representasi satu baris deskripsi yang sudah di-parse: teks (dengan span
 * markdown) + apakah baris ini bullet point (diawali "- ").
 */
data class UpdateDescriptionLine(
    val text: AnnotatedString,
    val isBullet: Boolean,
)

/**
 * Pecah deskripsi mentah jadi list baris siap-render, menerapkan markdown
 * inline (bold/italic/warna) di tiap baris dan mendeteksi prefix bullet
 * "- " di awal baris (prefix-nya dibuang dari teks, ditandai lewat
 * [UpdateDescriptionLine.isBullet] supaya UI yang menggambar bullet char-nya
 * sendiri dengan style konsisten, bukan literal "-" dari teks).
 */
fun parseUpdateDescription(description: String): List<UpdateDescriptionLine> {
    if (description.isBlank()) return emptyList()
    return description.trimEnd().split("\n").map { rawLine ->
        val trimmed = rawLine.trimStart()
        val isBullet = trimmed.startsWith("- ")
        val content = if (isBullet) trimmed.removePrefix("- ") else rawLine
        UpdateDescriptionLine(
            text = parseInlineMarkdown(content),
            isBullet = isBullet,
        )
    }
}
