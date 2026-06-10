package com.shj56166androidimage2.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SizePickerSupportTest {
    @Test
    fun `calculatePresetSize matches web presets`() {
        assertEquals("1024x1024", calculatePresetSize(SizeTier.ONE_K, "1:1"))
        assertEquals("2048x1152", calculatePresetSize(SizeTier.TWO_K, "16:9"))
        assertEquals("2160x3840", calculatePresetSize(SizeTier.FOUR_K, "9:16"))
    }

    @Test
    fun `findPresetForSize returns preset match`() {
        val result = findPresetForSize("2048x1152")

        assertEquals(SizeTier.TWO_K, result?.tier)
        assertEquals("16:9", result?.ratio)
    }

    @Test
    fun `validateCustomSize accepts positive multiples of sixteen only`() {
        assertEquals(
            CustomSizeValidationResult(size = "1024x1024"),
            validateCustomSize("1024", "1024"),
        )
        assertEquals(
            CustomSizeValidationResult(error = CustomSizeError.MULTIPLE_OF_SIXTEEN_REQUIRED),
            validateCustomSize("1025", "1024"),
        )
        assertEquals(
            CustomSizeValidationResult(error = CustomSizeError.MULTIPLE_OF_SIXTEEN_REQUIRED),
            validateCustomSize("1024", "1000"),
        )
    }

    @Test
    fun `auto size is not treated as a numeric preset`() {
        assertNull(findPresetForSize("auto"))
        assertNull(normalizeSizeLiteral("auto"))
    }
}
