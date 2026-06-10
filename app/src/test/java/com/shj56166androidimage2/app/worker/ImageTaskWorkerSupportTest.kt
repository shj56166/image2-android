package com.shj56166androidimage2.app.worker

import com.shj56166androidimage2.app.data.network.formatImageGenerationThrowable
import com.shj56166androidimage2.app.data.model.ImageTask
import com.shj56166androidimage2.app.data.model.TaskParams
import com.shj56166androidimage2.app.data.model.TaskStatus
import com.shj56166androidimage2.app.domain.engine.ImageResult
import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ImageTaskWorkerSupportTest {
    @Test
    fun `shouldRetryImageTask returns true for io failures`() {
        assertTrue(shouldRetryImageTask(IOException("socket closed")))
    }

    @Test
    fun `shouldRetryImageTask returns true for transient http style messages`() {
        assertTrue(shouldRetryImageTask(IllegalStateException("HTTP 503 upstream unavailable")))
        assertTrue(shouldRetryImageTask(IllegalStateException("request timeout from upstream")))
    }

    @Test
    fun `shouldRetryImageTask returns false for fatal validation failures`() {
        assertFalse(shouldRetryImageTask(IllegalArgumentException("model not found")))
    }

    @Test
    fun `withGeneratedOutputs appends new outputs and preserves existing order`() {
        val task = task(outputImageIds = listOf("image-a"))

        val updated = task.withGeneratedOutputs(
            outputs = listOf(
                GeneratedImageOutput("image-b", TaskParams(size = "1024x1024"), "revised b"),
                GeneratedImageOutput("image-c", null, null),
            ),
            rawImageUrls = listOf("https://example.test/b.png"),
            remoteTaskId = "remote-1",
        )

        assertEquals(listOf("image-a", "image-b", "image-c"), updated.outputImageIds)
        assertEquals("remote-1", updated.remoteTaskId)
        assertEquals("1024x1024", updated.actualParamsByImage["image-b"]?.size)
        assertEquals("revised b", updated.revisedPromptByImage["image-b"])
        assertEquals(listOf("https://example.test/b.png"), updated.rawImageUrls)
    }

    @Test
    fun `withGeneratedOutputs ignores duplicate outputs`() {
        val task = task(outputImageIds = listOf("image-a"), rawImageUrls = listOf("https://example.test/a.png"))

        val updated = task.withGeneratedOutputs(
            outputs = listOf(
                GeneratedImageOutput("image-a", TaskParams(size = "1024x1024"), "duplicate"),
                GeneratedImageOutput("image-b", null, null),
            ),
            rawImageUrls = listOf("https://example.test/a.png", "https://example.test/b.png"),
            remoteTaskId = null,
        )

        assertEquals(listOf("image-a", "image-b"), updated.outputImageIds)
        assertFalse(updated.actualParamsByImage.containsKey("image-a"))
        assertEquals(listOf("https://example.test/a.png", "https://example.test/b.png"), updated.rawImageUrls)
    }

    @Test
    fun `finalStatusForImageTask marks partial failures as error`() {
        val result = ImageResult(images = listOf("data:image/png;base64,abc"), partialErrors = listOf("HTTP 500"))

        assertEquals(TaskStatus.ERROR, finalStatusForImageTask(result))
        withLocale(Locale.SIMPLIFIED_CHINESE) {
            assertEquals("上游服务请求失败（HTTP 500）。", partialFailureMessage(result.partialErrors))
        }
    }

    @Test
    fun `partialFailureMessage formats multiple english errors for simplified chinese`() {
        val message = withLocale(Locale.SIMPLIFIED_CHINESE) {
            partialFailureMessage(
                listOf("HTTP 500", "Task polling timed out."),
            )
        }

        assertTrue(message.orEmpty().contains("上游服务请求失败（HTTP 500）。"))
        assertTrue(message.orEmpty().contains("任务轮询超时。"))
    }

    @Test
    fun `finalStatusForImageTask marks clean result as done`() {
        val result = ImageResult(images = listOf("data:image/png;base64,abc"))

        assertEquals(TaskStatus.DONE, finalStatusForImageTask(result))
        assertNull(partialFailureMessage(result.partialErrors))
    }

    @Test
    fun `reconcileActiveImageTask marks active task done when enough outputs exist`() {
        val task = task(
            outputImageIds = listOf("image-a", "image-b"),
            params = TaskParams(count = 2),
            createdAt = 1_000L,
        )

        val updated = reconcileActiveImageTask(task, now = 3_000L)

        assertEquals(TaskStatus.DONE, updated?.status)
        assertEquals(3_000L, updated?.finishedAt)
        assertEquals(2_000L, updated?.elapsedMillis)
        assertNull(updated?.error)
    }

    @Test
    fun `reconcileActiveImageTask marks stale partial task error and preserves outputs`() {
        val task = task(
            outputImageIds = listOf("image-a"),
            params = TaskParams(count = 3),
            createdAt = 1_000L,
        )

        val updated = reconcileActiveImageTask(task, now = 1_000L + STALE_ACTIVE_TASK_MILLIS + 1)

        assertEquals(TaskStatus.ERROR, updated?.status)
        assertEquals(listOf("image-a"), updated?.outputImageIds)
        assertTrue(updated?.error.orEmpty().contains("1/3"))
    }

    @Test
    fun `reconcileActiveImageTask preserves prior diagnostics when marking stale`() {
        val task = task(
            params = TaskParams(count = 2),
            createdAt = 1_000L,
            error = "06-09 14:40:00 retry:socket closed",
        )

        val updated = withLocale(Locale.SIMPLIFIED_CHINESE) {
            reconcileActiveImageTask(task, now = 1_000L + STALE_ACTIVE_TASK_MILLIS + 1)
        }

        assertTrue(updated?.error.orEmpty().contains("retry:socket closed"))
        assertTrue(updated?.error.orEmpty().contains("stale:任务长时间未完成，且未返回图片。"))
        assertFalse(updated?.error.orEmpty().contains("English:"))
    }

    @Test
    fun `reconcileActiveImageTask leaves fresh active task unchanged`() {
        val task = task(
            params = TaskParams(count = 3),
            createdAt = 1_000L,
        )

        assertNull(reconcileActiveImageTask(task, now = 1_000L + 5_000L))
    }

    @Test
    fun `taskWorkName prefixes unique work ids`() {
        assertEquals("image-task-task-123", taskWorkName("task-123"))
    }

    @Test
    fun `shouldDisableTaskNotificationsAfterForegroundFailure downgrades official foreground failures`() {
        assertTrue(shouldDisableTaskNotificationsAfterForegroundFailure(SecurityException("notifications denied")))
        assertTrue(shouldDisableTaskNotificationsAfterForegroundFailure(IllegalStateException("foreground service not allowed")))
    }

    @Test
    fun `shouldDisableTaskNotificationsAfterForegroundFailure keeps ordinary failures fatal`() {
        assertFalse(shouldDisableTaskNotificationsAfterForegroundFailure(IOException("network failed")))
        assertFalse(shouldDisableTaskNotificationsAfterForegroundFailure(IllegalArgumentException("bad input")))
    }

    @Test
    fun `activeTasksToReenqueue returns queued and resumable running tasks`() {
        val tasks = listOf(
            task(id = "queued", status = TaskStatus.QUEUED),
            task(id = "running", status = TaskStatus.RUNNING),
            task(id = "remote-running", status = TaskStatus.RUNNING, remoteTaskId = "remote-1"),
            task(id = "done", status = TaskStatus.DONE),
            task(id = "error", status = TaskStatus.ERROR),
        )

        assertEquals(listOf("queued", "remote-running"), activeTasksToReenqueue(tasks).map { it.id })
    }

    @Test
    fun `shouldEnqueueActiveTask skips existing unfinished work`() {
        assertFalse(shouldEnqueueActiveTask(task(status = TaskStatus.QUEUED), hasUnfinishedWork = true))
        assertFalse(shouldEnqueueActiveTask(task(status = TaskStatus.RUNNING, remoteTaskId = "remote-1"), hasUnfinishedWork = true))
    }

    @Test
    fun `orphanedRunningTaskUpdate marks sync running task error`() {
        val task = task(
            status = TaskStatus.RUNNING,
            params = TaskParams(count = 2),
            createdAt = 1_000L,
        )

        val updated = orphanedRunningTaskUpdate(task, hasUnfinishedWork = false, now = 4_000L)

        assertEquals(TaskStatus.ERROR, updated?.status)
        assertEquals(4_000L, updated?.finishedAt)
        assertEquals(3_000L, updated?.elapsedMillis)
        assertTrue(updated?.error.orEmpty().contains("orphaned:"))
    }

    @Test
    fun `orphanedRunningTaskUpdate keeps remote tasks resumable`() {
        val task = task(status = TaskStatus.RUNNING, remoteTaskId = "remote-1")

        assertNull(orphanedRunningTaskUpdate(task, hasUnfinishedWork = false, now = 4_000L))
    }

    @Test
    fun `cancelledActiveTaskUpdate marks active task error with cancellation diagnostics`() {
        val task = task(
            status = TaskStatus.RUNNING,
            outputImageIds = listOf("image-a"),
            params = TaskParams(count = 2),
            createdAt = 1_000L,
        )

        val updated = cancelledActiveTaskUpdate(task, now = 4_000L)

        assertEquals(TaskStatus.ERROR, updated?.status)
        assertEquals(4_000L, updated?.finishedAt)
        assertEquals(3_000L, updated?.elapsedMillis)
        assertTrue(updated?.error.orEmpty().contains("cancelled:user-exit:"))
        assertTrue(updated?.error.orEmpty().contains("1/2"))
    }

    @Test
    fun `cancelledActiveTaskUpdate ignores finished tasks`() {
        assertNull(cancelledActiveTaskUpdate(task(status = TaskStatus.DONE), now = 4_000L))
        assertNull(cancelledActiveTaskUpdate(task(status = TaskStatus.ERROR), now = 4_000L))
    }

    @Test
    fun `withDiagnosticLine keeps the newest diagnostics`() {
        val initial = (1..12).joinToString("\n") { "old-$it" }

        val updated = initial.withDiagnosticLine("retry:timeout", timestampMillis = 1_700_000_000_000L)
        val lines = updated.lines()

        assertFalse(lines.contains("old-1"))
        assertTrue(lines.contains("old-12"))
        assertTrue(lines.any { it.contains("retry:timeout") })
    }

    @Test
    fun `withDiagnosticLine preserves bilingual body before diagnostic line`() {
        val body = withLocale(Locale.SIMPLIFIED_CHINESE) {
            formatImageGenerationThrowable(IllegalStateException("HTTP 500"))
        }

        val updated = body.withDiagnosticLine("failed:HTTP 500", timestampMillis = 1_700_000_000_000L)
        val lines = updated.lines()

        assertEquals("上游服务请求失败（HTTP 500）。", lines[0])
        assertTrue(lines.last().contains("failed:HTTP 500"))
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

    private fun task(
        id: String = "task",
        status: TaskStatus = TaskStatus.RUNNING,
        outputImageIds: List<String> = emptyList(),
        rawImageUrls: List<String> = emptyList(),
        params: TaskParams = TaskParams(),
        createdAt: Long = 1L,
        error: String? = null,
        remoteTaskId: String? = null,
    ) =
        ImageTask(
            id = id,
            prompt = "prompt",
            params = params,
            status = status,
            error = error,
            remoteTaskId = remoteTaskId,
            outputImageIds = outputImageIds,
            rawImageUrls = rawImageUrls,
            createdAt = createdAt,
        )
}
