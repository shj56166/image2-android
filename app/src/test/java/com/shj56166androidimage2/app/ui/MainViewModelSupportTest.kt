package com.shj56166androidimage2.app.ui

import com.shj56166androidimage2.app.data.model.ImageTask
import com.shj56166androidimage2.app.data.model.TaskParams
import com.shj56166androidimage2.app.data.model.TaskStatus
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainViewModelSupportTest {
    @Test
    fun `TaskParams default count is two`() {
        assertEquals(2, TaskParams().count)
    }

    @Test
    fun `hasActiveTasks returns true when queued task exists`() {
        val tasks = listOf(task(status = TaskStatus.DONE), task(status = TaskStatus.QUEUED))

        assertTrue(hasActiveTasks(tasks))
    }

    @Test
    fun `hasActiveTasks returns true when running task exists`() {
        val tasks = listOf(task(status = TaskStatus.RUNNING))

        assertTrue(hasActiveTasks(tasks))
    }

    @Test
    fun `hasActiveTasks returns false when all tasks are terminal`() {
        val tasks = listOf(task(status = TaskStatus.DONE), task(status = TaskStatus.ERROR))

        assertFalse(hasActiveTasks(tasks))
    }

    @Test
    fun `pendingOutputPlaceholderCount fills missing running outputs`() {
        val task = task(status = TaskStatus.RUNNING, params = TaskParams(count = 4))

        assertEquals(2, pendingOutputPlaceholderCount(task, renderedOutputCount = 2))
    }

    @Test
    fun `pendingOutputPlaceholderCount does not fill terminal tasks`() {
        val doneTask = task(status = TaskStatus.DONE, params = TaskParams(count = 4))
        val errorTask = task(status = TaskStatus.ERROR, params = TaskParams(count = 4))

        assertEquals(0, pendingOutputPlaceholderCount(doneTask, renderedOutputCount = 2))
        assertEquals(0, pendingOutputPlaceholderCount(errorTask, renderedOutputCount = 2))
    }

    @Test
    fun `taskProgressFraction returns count fraction for active multi image tasks`() {
        val task = task(
            status = TaskStatus.RUNNING,
            params = TaskParams(count = 3),
            outputImageIds = listOf("image-a"),
        )

        assertEquals("1/3", taskProgressFraction(task))
    }

    @Test
    fun `taskProgressFraction skips active single image tasks`() {
        val task = task(status = TaskStatus.RUNNING, params = TaskParams(count = 1))

        assertEquals(null, taskProgressFraction(task))
    }

    @Test
    fun `taskProgressFraction skips terminal multi image tasks`() {
        val task = task(
            status = TaskStatus.DONE,
            params = TaskParams(count = 3),
            outputImageIds = listOf("image-a", "image-b", "image-c"),
        )

        assertEquals(null, taskProgressFraction(task))
    }

    @Test
    fun `filterHistoryTasks applies favorites status and query together`() {
        val tasks =
            listOf(
                task(status = TaskStatus.DONE, prompt = "sunrise", isFavorite = true),
                task(status = TaskStatus.DONE, prompt = "portrait", isFavorite = false),
                task(status = TaskStatus.ERROR, prompt = "sunset", isFavorite = true),
            )

        val result = filterHistoryTasks(tasks, query = "sun", statusFilter = HistoryStatusFilter.DONE, favoritesOnly = true)

        assertEquals(listOf("sunrise"), result.map { it.prompt })
    }

    @Test
    fun `canShowTaskRetryAction depends on task status and setting`() {
        assertTrue(canShowTaskRetryAction(task(status = TaskStatus.ERROR), alwaysShowRetryButton = false))
        assertTrue(canShowTaskRetryAction(task(status = TaskStatus.DONE), alwaysShowRetryButton = true))
        assertFalse(canShowTaskRetryAction(task(status = TaskStatus.DONE), alwaysShowRetryButton = false))
        assertFalse(canShowTaskRetryAction(task(status = TaskStatus.RUNNING), alwaysShowRetryButton = true))
    }

    @Test
    fun `buildRetryTask clears result fields and keeps request fields`() {
        val original =
            task(
                status = TaskStatus.ERROR,
                params = TaskParams(count = 4),
                outputImageIds = listOf("image-a", "image-b"),
                inputImageIds = listOf("input-a"),
                sessionId = "session-1",
                error = "failed",
                isFavorite = true,
            )

        val retried = buildRetryTask(original, newTaskId = "retry-1", createdAt = 200L, sessionId = "session-1")

        assertEquals("retry-1", retried.id)
        assertEquals(original.prompt, retried.prompt)
        assertEquals(original.params, retried.params)
        assertEquals(original.inputImageIds, retried.inputImageIds)
        assertEquals("session-1", retried.sessionId)
        assertEquals(TaskStatus.RUNNING, retried.status)
        assertTrue(retried.outputImageIds.isEmpty())
        assertTrue(retried.rawImageUrls.isEmpty())
        assertTrue(retried.actualParamsByImage.isEmpty())
        assertTrue(retried.revisedPromptByImage.isEmpty())
        assertNull(retried.error)
        assertEquals(200L, retried.createdAt)
        assertEquals(false, retried.isFavorite)
    }

    private fun task(
        status: TaskStatus,
        params: TaskParams = TaskParams(),
        outputImageIds: List<String> = emptyList(),
        inputImageIds: List<String> = emptyList(),
        prompt: String = "prompt",
        sessionId: String? = null,
        error: String? = null,
        isFavorite: Boolean = false,
    ) =
        ImageTask(
            id = status.name,
            prompt = prompt,
            params = params,
            status = status,
            sessionId = sessionId,
            inputImageIds = inputImageIds,
            outputImageIds = outputImageIds,
            error = error,
            createdAt = 1L,
            isFavorite = isFavorite,
        )
}
