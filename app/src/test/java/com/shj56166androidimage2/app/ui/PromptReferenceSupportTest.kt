package com.shj56166androidimage2.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PromptReferenceSupportTest {
    @Test
    fun `reference labels are sequential for current order`() {
        assertEquals("\u56FE1", referenceLabelForIndex(0))
        assertEquals("\u56FE3", referenceLabelForIndex(2))
    }

    @Test
    fun `reference label lookup reorders after removals`() {
        val selectedImageIds = listOf("image-a", "image-c")

        assertEquals("\u56FE1", referenceLabelForImageId(selectedImageIds, "image-a"))
        assertEquals("\u56FE2", referenceLabelForImageId(selectedImageIds, "image-c"))
        assertNull(referenceLabelForImageId(selectedImageIds, "missing"))
    }

    @Test
    fun `insert reference label at cursor position`() {
        val result = insertReferenceLabel("prefix suffix", 7, 7, "\u56FE1")

        assertEquals("prefix \u56FE1suffix", result.text)
        assertEquals(9, result.cursor)
    }

    @Test
    fun `insert reference label replaces selected range`() {
        val result = insertReferenceLabel("abcde", 1, 4, "\u56FE2")

        assertEquals("a\u56FE2e", result.text)
        assertEquals(3, result.cursor)
    }

    @Test
    fun `findReferenceLabelRanges returns only labels within current references`() {
        val ranges = findReferenceLabelRanges("参考 图1 和 图3，忽略 图4", maxReferenceCount = 3)

        assertEquals(
            listOf(
                PromptReferenceRange(start = 3, end = 5, index = 0),
                PromptReferenceRange(start = 8, end = 10, index = 2),
            ),
            ranges,
        )
    }

    @Test
    fun `findReferenceLabelRanges ignores text when no references are selected`() {
        assertEquals(emptyList<PromptReferenceRange>(), findReferenceLabelRanges("图1", maxReferenceCount = 0))
    }

    @Test
    fun `remapReferenceLabelsForOrder updates labels for reordered images`() {
        val result = remapReferenceLabelsForOrder(
            prompt = "参考 图1 和 图3",
            previousImageIds = listOf("a", "b", "c"),
            nextImageIds = listOf("c", "a", "b"),
        )

        assertEquals("参考 图2 和 图1", result)
    }
}
