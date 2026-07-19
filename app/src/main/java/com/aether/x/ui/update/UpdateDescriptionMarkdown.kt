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

private val COLOR_TAG_REGEX = Regex("""\[(\w+)](.*?)\[/\1]""", RegexOption.DOT_MATCHES_ALL)
private val BOLD_REGEX = Regex("""\*\*(.+?)\*\*""")
private val ITALIC_REGEX = Regex("""(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)|_(.+?)_""")

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

private fun colorFor(name: String): Color? = when (name.lowercase()) {
    "blue" -> AccentBlue
    "green" -> AccentGreen
    "amber", "yellow" -> AccentAmber
    "red" -> AccentRed
    else -> null
}

data class UpdateDescriptionLine(
    val text: AnnotatedString,
    val isBullet: Boolean,
)

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
