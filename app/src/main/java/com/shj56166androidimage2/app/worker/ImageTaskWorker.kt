package com.shj56166androidimage2.app.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shj56166androidimage2.app.ImagePlaygroundApp
import com.shj56166androidimage2.app.R
import com.shj56166androidimage2.app.data.network.formatImageGenerationErrors
import com.shj56166androidimage2.app.data.network.formatImageGenerationThrowable
import com.shj56166androidimage2.app.data.model.ImageSource
import com.shj56166androidimage2.app.data.model.ImageTask
import com.shj56166androidimage2.app.data.model.SessionTurn
import com.shj56166androidimage2.app.data.model.TaskParams
import com.shj56166androidimage2.app.data.model.TaskStatus
import com.shj56166androidimage2.app.data.repo.firstOrNull
import com.shj56166androidimage2.app.data.repo.TaskRepository
import com.shj56166androidimage2.app.domain.engine.ImageRequest
import com.shj56166androidimage2.app.domain.engine.ImageResult
import com.shj56166androidimage2.app.util.AppDisplayLanguage
import com.shj56166androidimage2.app.util.currentAppDisplayLanguage
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ImageTaskWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as ImagePlaygroundApp
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val task = app.container.taskRepository.getById(taskId) ?: return Result.failure()
        if (task.status == TaskStatus.DONE || task.status == TaskStatus.ERROR) {
            return Result.success()
        }
        Log.i(TAG, "start task=${taskId.shortLogId()} count=${task.params.count.coerceAtLeast(1)}")
        var useForegroundExecution = trySetForegroundInfo(task)
        val wakeLock = acquireTaskWakeLock(taskId)
        try {
            suspend fun markWorkerHeartbeat(stage: String) {
                val current = app.container.taskRepository.getById(taskId) ?: return
                app.container.taskRepository.upsert(
                    current.copy(
                        status = TaskStatus.RUNNING,
                        error = current.error.withDiagnosticLine("worker:$stage", System.currentTimeMillis()),
                        finishedAt = null,
                        elapsedMillis = null,
                    ),
                )
            }
            markWorkerHeartbeat("started")
            val settings = app.container.settingsRepository.settings.first()
            val profile =
                settings.profiles.firstOrNull { it.id == task.apiProfileId }
                    ?: settings.profiles.firstOrNull()
                    ?: error("No API profile configured.")
            val customProvider = settings.customProviders.firstOrNull { it.id == task.apiProvider }
            val inputImages = task.inputImageIds.mapNotNull { app.container.imageStorageRepository.getDataUrl(it) }
            val maskImage = task.maskImageId?.let { app.container.imageStorageRepository.getDataUrl(it) }
            val sessionTurns = task.sessionId?.let { app.container.sessionRepository.getTurns(it) }.orEmpty()

            app.container.taskRepository.upsert(
                task.copy(
                    status = TaskStatus.RUNNING,
                    error = null,
                    finishedAt = null,
                ),
            )

            runCatching {
                val appendMutex = Mutex()
                val appendedDataUrls = mutableSetOf<String>()
                suspend fun appendGeneratedImages(result: ImageResult, skipPreviouslySeen: Boolean): ImageTask {
                    markWorkerHeartbeat("partial-callback")
                    if (result.images.isEmpty()) {
                        return app.container.taskRepository.getById(taskId) ?: task
                    }
                    return appendMutex.withLock {
                        val outputs = result.images.mapIndexedNotNull { index, image ->
                            if (skipPreviouslySeen && image in appendedDataUrls) {
                                null
                            } else {
                                appendedDataUrls += image
                                val asset = app.container.imageStorageRepository.storeBase64DataUrl(image, ImageSource.GENERATED)
                                GeneratedImageOutput(
                                    imageId = asset.id,
                                    params = result.actualParamsList.getOrNull(index),
                                    revisedPrompt = result.revisedPrompts.getOrNull(index)?.takeIf { !it.isNullOrBlank() },
                                )
                            }
                        }
                        if (outputs.isEmpty()) {
                            return@withLock app.container.taskRepository.getById(taskId) ?: task
                        }
                        val currentTask = app.container.taskRepository.getById(taskId) ?: task
                        val updatedTask = currentTask.withGeneratedOutputs(
                            outputs = outputs,
                            rawImageUrls = result.rawImageUrls,
                            remoteTaskId = result.remoteTaskId,
                        )
                        app.container.taskRepository.upsert(updatedTask)
                        if (useForegroundExecution) {
                            useForegroundExecution = trySetForegroundInfo(updatedTask)
                        }
                        val added = updatedTask.outputImageIds.size - currentTask.outputImageIds.size
                        if (added > 0) {
                            Log.i(
                                TAG,
                                "partial task=${taskId.shortLogId()} added=$added total=${updatedTask.outputImageIds.size}",
                            )
                        }
                        updatedTask
                    }
                }
                val progressCallback: suspend (String) -> Unit = { remoteTaskId ->
                    val currentTask = app.container.taskRepository.getById(taskId)
                    if (currentTask != null && currentTask.remoteTaskId != remoteTaskId) {
                        app.container.taskRepository.upsert(
                            currentTask.copy(
                                remoteTaskId = remoteTaskId,
                                status = TaskStatus.RUNNING,
                                error = currentTask.error.withDiagnosticLine("remote:${remoteTaskId.shortLogId()}", System.currentTimeMillis()),
                            ),
                        )
                        Log.i(TAG, "remote task=${taskId.shortLogId()} remote=${remoteTaskId.shortLogId()}")
                    }
                }
                markWorkerHeartbeat("request-start")
                val result = coroutineScope {
                    val heartbeat = launch {
                        while (true) {
                            delay(15_000L)
                            markWorkerHeartbeat("request-waiting")
                            if (useForegroundExecution) {
                                val latest = app.container.taskRepository.getById(taskId) ?: task
                                useForegroundExecution = trySetForegroundInfo(latest)
                            }
                        }
                    }
                    try {
                        if (task.remoteTaskId != null && customProvider?.poll != null) {
                            app.container.imageExecutionEngine().resumeTask(
                                profile = profile,
                                customProvider = customProvider,
                                remoteTaskId = task.remoteTaskId,
                                params = task.params,
                            )
                        } else {
                            val request = ImageRequest(
                                profile = profile,
                                customProvider = customProvider,
                                prompt = task.prompt,
                                params = task.params,
                                inputImages = inputImages,
                                maskImage = maskImage,
                                sessionTurns = sessionTurns,
                                onRemoteTaskAccepted = progressCallback,
                                onPartialResult = { partialResult -> appendGeneratedImages(partialResult, skipPreviouslySeen = false) },
                            )
                            if (inputImages.isNotEmpty() || maskImage != null) {
                                app.container.imageExecutionEngine().edit(request)
                            } else {
                                app.container.imageExecutionEngine().generate(request)
                            }
                        }
                    } finally {
                        heartbeat.cancel()
                    }
                }
                markWorkerHeartbeat("request-finished")
                appendGeneratedImages(result, skipPreviouslySeen = true)
                val latestTask = app.container.taskRepository.getById(taskId) ?: task
                val finalStatus = finalStatusForImageTask(result)
                val finalError = partialFailureMessage(result.partialErrors)
                val updatedTask = latestTask.copy(
                    remoteTaskId = result.remoteTaskId ?: latestTask.remoteTaskId,
                    rawResponsePayload = result.rawResponsePayload ?: latestTask.rawResponsePayload,
                    status = finalStatus,
                    error = finalError,
                    finishedAt = System.currentTimeMillis(),
                    elapsedMillis = System.currentTimeMillis() - latestTask.createdAt,
                )
                app.container.taskRepository.upsert(updatedTask)
                if (finalStatus == TaskStatus.ERROR) {
                    Log.w(
                        TAG,
                        "partial failure task=${taskId.shortLogId()} outputs=${updatedTask.outputImageIds.size} error=${finalError.orEmpty().take(160)}",
                    )
                } else {
                    Log.i(TAG, "done task=${taskId.shortLogId()} outputs=${updatedTask.outputImageIds.size}")
                }

                if (updatedTask.outputImageIds.isNotEmpty()) task.sessionId?.let { sessionId ->
                    val session = app.container.sessionRepository.observeSessions().first().firstOrNull { it.id == sessionId }
                        ?: app.container.sessionRepository.newSession(name = "Session")
                    val turn = SessionTurn(
                        timestamp = System.currentTimeMillis(),
                        command = if (inputImages.isNotEmpty() || maskImage != null) "edit" else "generate",
                        prompt = task.prompt,
                        revisedPrompts = updatedTask.outputImageIds.mapNotNull { updatedTask.revisedPromptByImage[it] },
                        responseIds = result.responseIds,
                        outputImageIds = updatedTask.outputImageIds,
                        inputImageIds = task.inputImageIds,
                        maskImageId = task.maskImageId,
                        model = profile.model,
                        imageModel = "gpt-image-2",
                    )
                    app.container.sessionRepository.appendTurns(
                        session = session.copy(updatedAt = System.currentTimeMillis()),
                        turns = sessionTurns + turn,
                    )
                }
                if (finalStatus == TaskStatus.ERROR) {
                    return Result.failure()
                }
            }.onFailure { throwable ->
                val currentTask = app.container.taskRepository.getById(taskId) ?: task
                if (throwable is CancellationException) {
                    withContext(NonCancellable) {
                        val latestTask = app.container.taskRepository.getById(taskId) ?: currentTask
                        val userCancelled = latestTask.error.orEmpty().contains("cancelled:user-exit:")
                        if (!userCancelled) {
                            val expected = latestTask.params.count.coerceAtLeast(1)
                            val message = localizedTaskInterruptedMessage(latestTask.outputImageIds.size, expected)
                            app.container.taskRepository.upsert(
                                latestTask.copy(
                                    status = TaskStatus.ERROR,
                                    error = latestTask.error.withDiagnosticLine("cancelled:${message.take(180)}", System.currentTimeMillis()),
                                    finishedAt = System.currentTimeMillis(),
                                    elapsedMillis = System.currentTimeMillis() - latestTask.createdAt,
                                ),
                            )
                        }
                        Log.w(TAG, "cancelled task=${taskId.shortLogId()} outputs=${latestTask.outputImageIds.size}")
                    }
                    return Result.failure()
                }
                if (shouldRetryImageTask(throwable)) {
                    val formattedError = formatImageGenerationThrowable(throwable)
                    app.container.taskRepository.upsert(
                        currentTask.copy(
                            status = TaskStatus.QUEUED,
                            error = formattedError.withDiagnosticLine("retry:${throwable.logSummary()}", System.currentTimeMillis()),
                            finishedAt = null,
                            elapsedMillis = null,
                        ),
                    )
                    Log.w(TAG, "retry task=${taskId.shortLogId()} error=${throwable.logSummary()}")
                    return Result.retry()
                }
                val formattedError = formatImageGenerationThrowable(throwable)
                val failedTask = currentTask.copy(
                    status = TaskStatus.ERROR,
                    error = formattedError.withDiagnosticLine("failed:${throwable.logSummary()}", System.currentTimeMillis()),
                    finishedAt = System.currentTimeMillis(),
                    elapsedMillis = System.currentTimeMillis() - currentTask.createdAt,
                )
                app.container.taskRepository.upsert(
                    failedTask,
                )
                Log.e(TAG, "failed task=${taskId.shortLogId()} error=${throwable.logSummary()}", throwable)
                return Result.failure()
            }

            return Result.success()
        } finally {
            releaseTaskWakeLock(wakeLock, taskId)
        }
    }

    companion object {
        private const val TAG = "ImageTaskWorker"
        private const val KEY_TASK_ID = "task_id"
        private const val NOTIFICATION_CHANNEL_ID = "image_generation"

        fun enqueue(context: Context, taskId: String) {
            val work = OneTimeWorkRequestBuilder<ImageTaskWorker>()
                .setInputData(
                    Data.Builder()
                        .putString(KEY_TASK_ID, taskId)
                        .build(),
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10,
                    TimeUnit.SECONDS,
                )
                .addTag(taskWorkName(taskId))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                taskWorkName(taskId),
                ExistingWorkPolicy.KEEP,
                work,
            )
        }

        fun cancel(context: Context, taskId: String) {
            WorkManager.getInstance(context).cancelUniqueWork(taskWorkName(taskId))
        }

        suspend fun reconcileAndEnqueueActiveTasks(
            context: Context,
            repository: TaskRepository,
            now: Long = System.currentTimeMillis(),
        ): Int {
            reconcileActiveTasks(repository, now)
            val activeTasks = repository.getAll().filter { it.status == TaskStatus.QUEUED || it.status == TaskStatus.RUNNING }
            val workStateByTask = activeTasks.associateWithUnfinishedWork(context)
            val orphanUpdates = activeTasks.mapNotNull { task ->
                orphanedRunningTaskUpdate(task, hasUnfinishedWork = workStateByTask[task.id] == true, now = now)
            }
            if (orphanUpdates.isNotEmpty()) {
                repository.upsertAll(orphanUpdates)
                Log.w(TAG, "marked orphaned running tasks count=${orphanUpdates.size}")
            }
            val tasksToEnqueue = activeTasks.filter { task ->
                shouldEnqueueActiveTask(task, hasUnfinishedWork = workStateByTask[task.id] == true)
            }
            tasksToEnqueue.forEach { enqueue(context, it.id) }
            if (tasksToEnqueue.isNotEmpty()) {
                Log.i(TAG, "reenqueued active tasks count=${tasksToEnqueue.size}")
            }
            return tasksToEnqueue.size
        }

        suspend fun reconcileActiveTasks(
            repository: TaskRepository,
            now: Long = System.currentTimeMillis(),
        ): Int {
            val updates = repository.getAll().mapNotNull { reconcileActiveImageTask(it, now) }
            if (updates.isNotEmpty()) {
                repository.upsertAll(updates)
                Log.w(TAG, "reconciled stale active tasks count=${updates.size}")
            }
            return updates.size
        }

        private suspend fun List<ImageTask>.associateWithUnfinishedWork(context: Context): Map<String, Boolean> {
            val states = LinkedHashMap<String, Boolean>()
            forEach { task ->
                states[task.id] = hasUnfinishedWork(context, task.id)
            }
            return states
        }

        private suspend fun hasUnfinishedWork(context: Context, taskId: String): Boolean =
            withContext(Dispatchers.IO) {
                WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWork(taskWorkName(taskId))
                    .get()
                    .any { !it.state.isFinished }
            }
    }

    private fun createForegroundInfo(task: ImageTask): ForegroundInfo {
        val done = task.outputImageIds.size
        val total = task.params.count.coerceAtLeast(1)
        val notification = createNotification(done, total)
        val rawNotificationId = task.id.hashCode().takeIf { it != Int.MIN_VALUE }?.let { kotlin.math.abs(it) } ?: 1
        val notificationId = rawNotificationId.takeIf { it != 0 } ?: 1
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun createNotification(done: Int, total: Int): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                applicationContext.getString(R.string.notification_running_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle(applicationContext.getString(R.string.notification_running_title))
            .setContentText(applicationContext.getString(R.string.notification_running_message, done, total))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(total, done.coerceAtMost(total), done == 0)
            .build()
    }

    private suspend fun trySetForegroundInfo(task: ImageTask): Boolean {
        return runCatching {
            setForeground(createForegroundInfo(task))
            true
        }.getOrElse { throwable ->
            if (shouldDisableTaskNotificationsAfterForegroundFailure(throwable)) {
                Log.w(TAG, "foreground notification disabled task=${task.id.shortLogId()} error=${throwable.logSummary()}")
                false
            } else {
                throw throwable
            }
        }
    }

    private fun acquireTaskWakeLock(taskId: String): PowerManager.WakeLock? {
        val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return null
        return runCatching {
            powerManager
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG:${taskId.shortLogId()}")
                .apply {
                    setReferenceCounted(false)
                    acquire(WAKE_LOCK_TIMEOUT_MILLIS)
                }
        }.onSuccess {
            Log.i(TAG, "wake lock acquired task=${taskId.shortLogId()}")
        }.onFailure {
            Log.w(TAG, "wake lock skipped task=${taskId.shortLogId()} error=${it.logSummary()}")
        }.getOrNull()
    }

    private fun releaseTaskWakeLock(wakeLock: PowerManager.WakeLock?, taskId: String) {
        if (wakeLock == null) return
        runCatching {
            if (wakeLock.isHeld) {
                wakeLock.release()
                Log.i(TAG, "wake lock released task=${taskId.shortLogId()}")
            }
        }.onFailure {
            Log.w(TAG, "wake lock release failed task=${taskId.shortLogId()} error=${it.logSummary()}")
        }
    }
}

internal data class GeneratedImageOutput(
    val imageId: String,
    val params: TaskParams?,
    val revisedPrompt: String?,
)

internal fun ImageTask.withGeneratedOutputs(
    outputs: List<GeneratedImageOutput>,
    rawImageUrls: List<String>,
    remoteTaskId: String?,
): ImageTask {
    val existingIds = outputImageIds.toSet()
    val newOutputs = outputs.filterNot { it.imageId in existingIds }
    val newRawImageUrls = rawImageUrls.filterNot { it in this.rawImageUrls }
    if (newOutputs.isEmpty() && remoteTaskId == null && newRawImageUrls.isEmpty()) return this
    return copy(
        remoteTaskId = remoteTaskId ?: this.remoteTaskId,
        outputImageIds = outputImageIds + newOutputs.map { it.imageId },
        rawImageUrls = this.rawImageUrls + newRawImageUrls,
        actualParamsByImage = actualParamsByImage + newOutputs.mapNotNull { output ->
            output.params?.let { output.imageId to it }
        },
        revisedPromptByImage = revisedPromptByImage + newOutputs.mapNotNull { output ->
            output.revisedPrompt?.let { output.imageId to it }
        },
        status = TaskStatus.RUNNING,
        error = null,
        finishedAt = null,
        elapsedMillis = null,
    )
}

internal fun finalStatusForImageTask(result: ImageResult): TaskStatus =
    if (result.partialErrors.isEmpty()) TaskStatus.DONE else TaskStatus.ERROR

internal fun partialFailureMessage(errors: List<String>): String? =
    errors.takeIf { it.isNotEmpty() }?.let(::formatImageGenerationErrors)?.joinToString(separator = "\n")

internal const val STALE_ACTIVE_TASK_MILLIS: Long = 30 * 60 * 1_000L

internal fun reconcileActiveImageTask(
    task: ImageTask,
    now: Long,
    staleMillis: Long = STALE_ACTIVE_TASK_MILLIS,
): ImageTask? {
    if (task.status != TaskStatus.QUEUED && task.status != TaskStatus.RUNNING) return null
    val expected = task.params.count.coerceAtLeast(1)
    val outputCount = task.outputImageIds.size
    if (outputCount >= expected) {
        return task.copy(
            status = TaskStatus.DONE,
            error = null,
            finishedAt = task.finishedAt ?: now,
            elapsedMillis = task.elapsedMillis ?: (now - task.createdAt).coerceAtLeast(0),
        )
    }
    if (now - task.createdAt < staleMillis) return null
    val message = localizedTaskStaleMessage(outputCount, expected)
    return task.copy(
        status = TaskStatus.ERROR,
        error = task.error.withDiagnosticLine("stale:$message", now),
        finishedAt = task.finishedAt ?: now,
        elapsedMillis = task.elapsedMillis ?: (now - task.createdAt).coerceAtLeast(0),
    )
}

internal fun shouldRetryImageTask(throwable: Throwable): Boolean {
    if (throwable is IOException) return true
    val message = throwable.message.orEmpty().lowercase()
    return listOf("timeout", "temporarily", "503", "502", "504", "429").any(message::contains)
}

internal fun shouldDisableTaskNotificationsAfterForegroundFailure(throwable: Throwable): Boolean =
    throwable is SecurityException ||
        throwable is IllegalStateException ||
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && throwable is ForegroundServiceStartNotAllowedException)

internal fun taskWorkName(taskId: String): String = "$WORK_NAME_PREFIX$taskId"

internal fun activeTasksToReenqueue(tasks: List<ImageTask>): List<ImageTask> =
    tasks.filter { shouldEnqueueActiveTask(it, hasUnfinishedWork = false) }

internal fun shouldEnqueueActiveTask(task: ImageTask, hasUnfinishedWork: Boolean): Boolean {
    if (hasUnfinishedWork) return false
    return task.status == TaskStatus.QUEUED ||
        (task.status == TaskStatus.RUNNING && task.remoteTaskId != null)
}

internal fun orphanedRunningTaskUpdate(
    task: ImageTask,
    hasUnfinishedWork: Boolean,
    now: Long,
): ImageTask? {
    if (hasUnfinishedWork || task.status != TaskStatus.RUNNING || task.remoteTaskId != null) return null
    val expected = task.params.count.coerceAtLeast(1)
    val outputCount = task.outputImageIds.size
    val message = localizedTaskInterruptedMessage(outputCount, expected)
    return task.copy(
        status = TaskStatus.ERROR,
        error = task.error.withDiagnosticLine("orphaned:$message", now),
        finishedAt = task.finishedAt ?: now,
        elapsedMillis = task.elapsedMillis ?: (now - task.createdAt).coerceAtLeast(0),
    )
}

internal fun cancelledActiveTaskUpdate(
    task: ImageTask,
    now: Long,
    reason: String = "user-exit",
): ImageTask? {
    if (task.status != TaskStatus.QUEUED && task.status != TaskStatus.RUNNING) return null
    val expected = task.params.count.coerceAtLeast(1)
    val outputCount = task.outputImageIds.size
    val message = localizedTaskCancelledMessage(outputCount, expected)
    return task.copy(
        status = TaskStatus.ERROR,
        error = task.error.withDiagnosticLine("cancelled:$reason:${message.take(180)}", now),
        finishedAt = task.finishedAt ?: now,
        elapsedMillis = task.elapsedMillis ?: (now - task.createdAt).coerceAtLeast(0),
    )
}

internal fun String?.withDiagnosticLine(label: String, timestampMillis: Long): String {
    val line = "${formatDiagnosticTime(timestampMillis)} $label"
    val existing = this.orEmpty()
    val lines = (existing.lines().filter { it.isNotBlank() } + line).takeLast(MAX_TASK_ERROR_DIAGNOSTIC_LINES)
    return lines.joinToString(separator = "\n").takeLast(MAX_TASK_ERROR_DIAGNOSTIC_CHARS)
}

internal fun formatDiagnosticTime(timestampMillis: Long): String =
    java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(timestampMillis))

private const val MAX_TASK_ERROR_DIAGNOSTIC_LINES = 12
private const val MAX_TASK_ERROR_DIAGNOSTIC_CHARS = 1_500
private const val WAKE_LOCK_TIMEOUT_MILLIS = 2 * 60 * 60 * 1_000L
private const val WORK_NAME_PREFIX = "image-task-"

private fun String.shortLogId(): String = take(8)

private fun Throwable.logSummary(): String =
    (message?.takeIf { it.isNotBlank() } ?: this::class.java.simpleName).take(240)

private fun localizedTaskInterruptedMessage(outputCount: Int, expected: Int): String =
    when (currentAppDisplayLanguage()) {
        AppDisplayLanguage.EN ->
            if (outputCount > 0) {
                "The task was interrupted in the background after returning $outputCount/$expected images."
            } else {
                "The task was interrupted in the background before any images were returned. Please retry."
            }
        AppDisplayLanguage.ZH_HANS ->
            if (outputCount > 0) {
                "任务在后台被系统中断，已返回 $outputCount/$expected 张图片。"
            } else {
                "任务在后台被系统中断，未返回图片。请重新生成。"
            }
        AppDisplayLanguage.ZH_HANT ->
            if (outputCount > 0) {
                "任務在背景被系統中斷，已返回 $outputCount/$expected 張圖片。"
            } else {
                "任務在背景被系統中斷，未返回圖片。請重新生成。"
            }
    }

private fun localizedTaskStaleMessage(outputCount: Int, expected: Int): String =
    when (currentAppDisplayLanguage()) {
        AppDisplayLanguage.EN ->
            if (outputCount > 0) {
                "The task did not finish in time and returned $outputCount/$expected images."
            } else {
                "The task did not finish in time and returned no images."
            }
        AppDisplayLanguage.ZH_HANS ->
            if (outputCount > 0) {
                "任务长时间未完成，已返回 $outputCount/$expected 张图片。"
            } else {
                "任务长时间未完成，且未返回图片。"
            }
        AppDisplayLanguage.ZH_HANT ->
            if (outputCount > 0) {
                "任務長時間未完成，已返回 $outputCount/$expected 張圖片。"
            } else {
                "任務長時間未完成，且未返回圖片。"
            }
    }

private fun localizedTaskCancelledMessage(outputCount: Int, expected: Int): String =
    when (currentAppDisplayLanguage()) {
        AppDisplayLanguage.EN ->
            if (outputCount > 0) {
                "The task was cancelled after returning $outputCount/$expected images."
            } else {
                "The task was cancelled before any images were returned."
            }
        AppDisplayLanguage.ZH_HANS ->
            if (outputCount > 0) {
                "任务已取消，已返回 $outputCount/$expected 张图片。"
            } else {
                "任务已取消，未返回图片。"
            }
        AppDisplayLanguage.ZH_HANT ->
            if (outputCount > 0) {
                "任務已取消，已返回 $outputCount/$expected 張圖片。"
            } else {
                "任務已取消，未返回圖片。"
            }
    }
