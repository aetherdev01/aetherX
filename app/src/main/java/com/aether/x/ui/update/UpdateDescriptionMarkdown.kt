package com.aether.x.ui.update

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

private val COLOR_TAG_REGEX = Regex("""\[(\w+)](.*?)\[/\1]""", RegexOption.DOT_MATCHES_ALL)
private val BOLD_REGEX = Regex("""\*\*(.+?)\*\*""")
private val ITALIC_REGEX = Regex("""(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)|_(.+?)_""")

private fun parseInlineMarkdown(line: String, dark: Boolean): AnnotatedString = buildAnnotatedString {
    appendWithColorTags(line, dark)
}

private fun AnnotatedString.Builder.appendWithColorTags(text: String, dark: Boolean) {
    var lastIndex = 0
    for (match in COLOR_TAG_REGEX.findAll(text)) {
        if (match.range.first > lastIndex) {
            appendWithBold(text.substring(lastIndex, match.range.first))
        }
        val colorName = match.groupValues[1]
        val inner = match.groupValues[2]
        val color = colorFor(colorName, dark)
        if (color != null) {
            withStyle(SpanStyle(color = color)) { appendWithBold(inner) }
        } else {

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

// v3.8 — warna tag changelog dari server: dua set nada. Satu nada tengah TIDAK
// bisa terbaca baik di putih DAN di gelap sekaligus (amber/hijau tengah hanya
// ~2.8-3.4:1 di atas putih), jadi dipilih per mode. Semua >= 4.5:1 di
// permukaan masing-masing. Fungsi parser ini bukan @Composable, jadi mode
// gelap dikirim sebagai parameter.
private fun colorFor(name: String, dark: Boolean): Color? = when (name.lowercase()) {
    "blue" -> if (dark) Color(0xFF6CA8FF) else Color(0xFF1F63C2)
    "green" -> if (dark) Color(0xFF5BE38A) else Color(0xFF1E7A38)
    "amber", "yellow" -> if (dark) Color(0xFFF2B84E) else Color(0xFF9A5B00)
    "red" -> if (dark) Color(0xFFFF7A70) else Color(0xFFC62828)
    else -> null
}

data class UpdateDescriptionLine(
    val text: AnnotatedString,
    val isBullet: Boolean,
)

fun parseUpdateDescription(description: String, dark: Boolean = false): List<UpdateDescriptionLine> {
    if (description.isBlank()) return emptyList()
    return description.trimEnd().split("\n").map { rawLine ->
        val trimmed = rawLine.trimStart()
        val isBullet = trimmed.startsWith("- ")
        val content = if (isBullet) trimmed.removePrefix("- ") else rawLine
        UpdateDescriptionLine(
            text = parseInlineMarkdown(content, dark),
            isBullet = isBullet,
        )
    }
}
