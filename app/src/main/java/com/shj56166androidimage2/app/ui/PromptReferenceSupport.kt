package com.shj56166androidimage2.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

internal data class PromptInsertResult(
    val text: String,
    val cursor: Int,
)

internal data class PromptReferenceRange(
    val start: Int,
    val end: Int,
    val index: Int,
)

internal fun referenceLabelForIndex(index: Int): String = "\u56FE${index + 1}"

internal fun referenceLabelForImageId(
    selectedImageIds: List<String>,
    imageId: String,
): String? {
    val index = selectedImageIds.indexOf(imageId)
    if (index < 0) return null
    return referenceLabelForIndex(index)
}

internal fun insertReferenceLabel(
    text: String,
    selectionStart: Int,
    selectionEnd: Int,
    label: String,
): PromptInsertResult {
    val safeStart = selectionStart.coerceIn(0, text.length)
    val safeEnd = selectionEnd.coerceIn(safeStart, text.length)
    val nextText = buildString(text.length + label.length) {
        append(text.substring(0, safeStart))
        append(label)
        append(text.substring(safeEnd))
    }
    return PromptInsertResult(
        text = nextText,
        cursor = safeStart + label.length,
    )
}

internal fun findReferenceLabelRanges(text: String, maxReferenceCount: Int): List<PromptReferenceRange> {
    if (maxReferenceCount <= 0) return emptyList()
    val ranges = mutableListOf<PromptReferenceRange>()
    val regex = Regex("""图(\d+)""")
    regex.findAll(text).forEach { match ->
        val oneBased = match.groupValues[1].toIntOrNull() ?: return@forEach
        if (oneBased in 1..maxReferenceCount) {
            ranges += PromptReferenceRange(
                start = match.range.first,
                end = match.range.last + 1,
                index = oneBased - 1,
            )
        }
    }
    return ranges
}

internal fun remapReferenceLabelsForOrder(
    prompt: String,
    previousImageIds: List<String>,
    nextImageIds: List<String>,
): String {
    if (previousImageIds == nextImageIds || previousImageIds.isEmpty() || nextImageIds.isEmpty()) return prompt
    val regex = Regex("""图(\d+)""")
    return regex.replace(prompt) { match ->
        val previousIndex = match.groupValues[1].toIntOrNull()?.minus(1) ?: return@replace match.value
        val imageId = previousImageIds.getOrNull(previousIndex) ?: return@replace match.value
        val nextIndex = nextImageIds.indexOf(imageId)
        if (nextIndex >= 0) referenceLabelForIndex(nextIndex) else match.value
    }
}

internal class PromptReferenceVisualTransformation(
    private val maxReferenceCount: Int,
    private val backgroundColor: Color,
    private val contentColor: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (text.text.isEmpty() || maxReferenceCount <= 0) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val builder = AnnotatedString.Builder(text)
        findReferenceLabelRanges(text.text, maxReferenceCount).forEach { range ->
            builder.addStyle(
                style = SpanStyle(
                    color = contentColor,
                    background = backgroundColor,
                    fontWeight = FontWeight.SemiBold,
                ),
                start = range.start,
                end = range.end,
            )
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}
