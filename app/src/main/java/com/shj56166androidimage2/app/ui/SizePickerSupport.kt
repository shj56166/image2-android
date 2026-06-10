package com.shj56166androidimage2.app.ui

import kotlin.math.roundToInt

private val RATIO_PATTERN = Regex("""^\s*(\d+(?:\.\d+)?)\s*[:xX×]\s*(\d+(?:\.\d+)?)\s*$""")
private val SIZE_PATTERN = Regex("""^\s*(\d+)\s*[xX×]\s*(\d+)\s*$""")
private const val SIZE_MULTIPLE = 16

internal enum class SizePickerMode {
    AUTO,
    RATIO,
    CUSTOM,
}

internal enum class SizeTier(
    val label: String,
) {
    ONE_K("1K"),
    TWO_K("2K"),
    FOUR_K("4K"),
}

internal data class SizeRatioOption(
    val label: String,
    val value: String,
)

internal data class ParsedRatio(
    val width: Double,
    val height: Double,
)

internal data class SizePresetMatch(
    val tier: SizeTier,
    val ratio: String,
)

internal enum class CustomSizeError {
    POSITIVE_INTEGER_REQUIRED,
    MULTIPLE_OF_SIXTEEN_REQUIRED,
}

internal data class CustomSizeValidationResult(
    val size: String? = null,
    val error: CustomSizeError? = null,
)

internal val sizeRatioOptions =
    listOf(
        SizeRatioOption(label = "1:1", value = "1:1"),
        SizeRatioOption(label = "3:2", value = "3:2"),
        SizeRatioOption(label = "2:3", value = "2:3"),
        SizeRatioOption(label = "16:9", value = "16:9"),
        SizeRatioOption(label = "9:16", value = "9:16"),
        SizeRatioOption(label = "4:3", value = "4:3"),
        SizeRatioOption(label = "3:4", value = "3:4"),
        SizeRatioOption(label = "21:9", value = "21:9"),
    )

internal fun parseRatio(value: String): ParsedRatio? {
    val match = RATIO_PATTERN.matchEntire(value) ?: return null
    val width = match.groupValues[1].toDoubleOrNull() ?: return null
    val height = match.groupValues[2].toDoubleOrNull() ?: return null
    if (width <= 0.0 || height <= 0.0) return null
    return ParsedRatio(width = width, height = height)
}

internal fun calculatePresetSize(
    tier: SizeTier,
    ratio: String,
): String? {
    val parsed = parseRatio(ratio) ?: return null
    val ratioWidth = parsed.width
    val ratioHeight = parsed.height

    if (ratioWidth == ratioHeight) {
        val side =
            when (tier) {
                SizeTier.ONE_K -> 1024
                SizeTier.TWO_K -> 2048
                SizeTier.FOUR_K -> 3840
            }
        return "${side}x$side"
    }

    return if (tier == SizeTier.ONE_K) {
        val shortSide = 1024
        val width =
            if (ratioWidth > ratioHeight) {
                roundToMultiple(shortSide * ratioWidth / ratioHeight)
            } else {
                shortSide
            }
        val height =
            if (ratioWidth > ratioHeight) {
                shortSide
            } else {
                roundToMultiple(shortSide * ratioHeight / ratioWidth)
            }
        "${width}x$height"
    } else {
        val longSide = if (tier == SizeTier.TWO_K) 2048 else 3840
        val width =
            if (ratioWidth > ratioHeight) {
                longSide
            } else {
                roundToMultiple(longSide * ratioWidth / ratioHeight)
            }
        val height =
            if (ratioWidth > ratioHeight) {
                roundToMultiple(longSide * ratioHeight / ratioWidth)
            } else {
                longSide
            }
        "${width}x$height"
    }
}

internal fun findPresetForSize(size: String): SizePresetMatch? {
    val normalized = normalizeSizeLiteral(size) ?: return null
    for (tier in SizeTier.entries) {
        for (ratio in sizeRatioOptions) {
            if (calculatePresetSize(tier, ratio.value) == normalized) {
                return SizePresetMatch(tier = tier, ratio = ratio.value)
            }
        }
    }
    return null
}

internal fun parseSizeLiteral(size: String): Pair<String, String>? {
    val match = SIZE_PATTERN.matchEntire(size.trim()) ?: return null
    return match.groupValues[1] to match.groupValues[2]
}

internal fun normalizeSizeLiteral(size: String): String? {
    val parsed = parseSizeLiteral(size) ?: return null
    val width = parsed.first.toIntOrNull() ?: return null
    val height = parsed.second.toIntOrNull() ?: return null
    if (width <= 0 || height <= 0) return null
    return "${width}x$height"
}

internal fun validateCustomSize(
    widthInput: String,
    heightInput: String,
): CustomSizeValidationResult {
    val width = widthInput.trim().toIntOrNull()
    val height = heightInput.trim().toIntOrNull()

    if (width == null || height == null || width <= 0 || height <= 0) {
        return CustomSizeValidationResult(error = CustomSizeError.POSITIVE_INTEGER_REQUIRED)
    }
    if (width % SIZE_MULTIPLE != 0 || height % SIZE_MULTIPLE != 0) {
        return CustomSizeValidationResult(error = CustomSizeError.MULTIPLE_OF_SIXTEEN_REQUIRED)
    }
    return CustomSizeValidationResult(size = "${width}x$height")
}

private fun roundToMultiple(value: Double): Int = (value / SIZE_MULTIPLE).roundToInt() * SIZE_MULTIPLE
