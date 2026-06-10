package com.shj56166androidimage2.app.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class DirectUpstreamExecutionEngineTest {
    @Test
    fun `effectiveImageRequestTimeoutSec raises short profile timeout`() {
        assertEquals(1_800, effectiveImageRequestTimeoutSec(600))
    }

    @Test
    fun `effectiveImageRequestTimeoutSec preserves longer profile timeout`() {
        assertEquals(3_600, effectiveImageRequestTimeoutSec(3_600))
    }

    @Test
    fun `formatImageGenerationError formats known english messages for simplified chinese`() {
        withLocale(Locale.SIMPLIFIED_CHINESE) {
            assertEquals(
                "任务轮询超时。",
                formatImageGenerationError("Task polling timed out."),
            )
            assertEquals(
                "并发 Responses API 请求全部失败。",
                formatImageGenerationError("All concurrent response requests failed."),
            )
        }
    }

    @Test
    fun `formatImageGenerationError maps http timeout model and remote image failures for simplified chinese`() {
        withLocale(Locale.SIMPLIFIED_CHINESE) {
            assertEquals(
                "上游服务请求失败（HTTP 503）。",
                formatImageGenerationError("HTTP 503 upstream unavailable"),
            )
            assertEquals(
                "请求超时。",
                formatImageGenerationError("request timeout from upstream"),
            )
            assertEquals(
                "模型或请求参数无效。",
                formatImageGenerationError("model not found"),
            )
            assertEquals(
                "远程图片下载失败（HTTP 404）。",
                formatImageGenerationError("Failed to download remote image: HTTP 404"),
            )
        }
    }

    @Test
    fun `formatImageGenerationError keeps chinese and existing bilingual messages unchanged`() {
        assertEquals("任务已取消，未返回图片。", formatImageGenerationError("任务已取消，未返回图片。"))

        val bilingual = "任务轮询超时\nEnglish: Task polling timed out."
        assertEquals(bilingual, formatImageGenerationError(bilingual))
    }

    @Test
    fun `formatImageGenerationError preserves diagnostic lines while formatting body`() {
        val formatted = withLocale(Locale.SIMPLIFIED_CHINESE) {
            formatImageGenerationError(
                "Task polling timed out.\n06-09 14:40:00 retry:socket closed",
            )
        }

        assertTrue(formatted.startsWith("任务轮询超时。"))
        assertTrue(formatted.contains("\n06-09 14:40:00 retry:socket closed"))
    }

    private fun <T> withLocale(locale: Locale, block: () -> T): T {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        return try {
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }
}
