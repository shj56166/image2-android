package com.shj56166androidimage2.app.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaskWorkingSizeTest {
    @Test
    fun `calculateMaskWorkingSize keeps small images unchanged`() {
        val result = calculateMaskWorkingSize(1024, 768)

        assertEquals(1024, result.width)
        assertEquals(768, result.height)
        assertEquals(1f, result.scale)
        assertFalse(result.wasResized)
    }

    @Test
    fun `calculateMaskWorkingSize scales longest edge and floors to multiple of sixteen`() {
        val result = calculateMaskWorkingSize(4000, 3000)

        assertEquals(1920, result.width)
        assertEquals(1440, result.height)
        assertTrue(result.wasResized)
    }

    @Test
    fun `calculateMaskWorkingSize floors both dimensions to multiple of sixteen`() {
        val result = calculateMaskWorkingSize(3000, 2000)

        assertEquals(1920, result.width)
        assertEquals(1280, result.height)
        assertTrue(result.wasResized)
    }
}
