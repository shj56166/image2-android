package com.shj56166androidimage2.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shj56166androidimage2.app.R
import com.shj56166androidimage2.app.data.model.ApiProfile
import com.shj56166androidimage2.app.data.model.AppSettingsState
import com.shj56166androidimage2.app.data.model.CustomProviderDefinition
import com.shj56166androidimage2.app.data.model.ImageSession
import com.shj56166androidimage2.app.data.model.ImageSource
import com.shj56166androidimage2.app.data.model.ImageTask
import com.shj56166androidimage2.app.data.model.MaskDraft
import com.shj56166androidimage2.app.data.model.StoredImageAsset
import com.shj56166androidimage2.app.data.model.TaskParams
import com.shj56166androidimage2.app.data.model.TaskStatus
import com.shj56166androidimage2.app.data.repo.AppContainer
import com.shj56166androidimage2.app.data.repo.AppJson
import com.shj56166androidimage2.app.ui.screens.createDefaultProfile
import com.shj56166androidimage2.app.ui.screens.deleteProfileFromSettings
import com.shj56166androidimage2.app.ui.screens.copyApiProfile
import com.shj56166androidimage2.app.ui.screens.nextDefaultProfileName
import com.shj56166androidimage2.app.ui.screens.normalizeImportedProfile
import com.shj56166androidimage2.app.ui.screens.removeCustomProviderFromSettings
import com.shj56166androidimage2.app.util.applyAppLanguagePreference
import com.shj56166androidimage2.app.worker.ImageTaskWorker
import com.shj56166androidimage2.app.worker.cancelledActiveTaskUpdate
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

enum class HomeTab { CREATE, HISTORY, SETTINGS }

enum class HistoryStatusFilter { ALL, ACTIVE, DONE, ERROR }

enum class SettingsSubpage { GENERAL, PROFILES, CUSTOM_PROVIDERS, DATA, ABOUT }

@Serializable
private data class AndroidBackup(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val settings: AppSettingsState? = null,
    val tasks: List<ImageTask> = emptyList(),
    val images: List<BackupImage> = emptyList(),
)

@Serializable
private data class BackupImage(
    val id: String,
    val path: String,
    val mimeType: String,
    val source: ImageSource,
)

@Serializable
private data class CustomProviderImportPayload(
    val customProviders: List<CustomProviderDefinition> = emptyList(),
    val profiles: List<ApiProfile> = emptyList(),
)

data class ComposerState(
    val prompt: String = "",
    val params: TaskParams = TaskParams(),
    val selectedImageIds: List<String> = emptyList(),
    val maskDraft: MaskDraft? = null,
    val currentSessionId: String? = null,
    val currentSessionName: String = "",
)

data class UiMessage(
    val title: String,
    val message: String,
)

data class MainUiState(
    val loading: Boolean = false,
    val tab: HomeTab = HomeTab.CREATE,
    val settings: AppSettingsState? = null,
    val tasks: List<ImageTask> = emptyList(),
    val createPreviewTasks: List<ImageTask> = emptyList(),
    val hasActiveTasks: Boolean = false,
    val images: Map<String, StoredImageAsset> = emptyMap(),
    val sessions: List<ImageSession> = emptyList(),
    val composer: ComposerState = ComposerState(),
    val selectedTaskId: String? = null,
    val selectedTask: ImageTask? = null,
    val maskEditorTargetImageId: String? = null,
    val settingsSubpage: SettingsSubpage? = null,
    val query: String = "",
    val historyStatusFilter: HistoryStatusFilter = HistoryStatusFilter.ALL,
    val errorMessage: String? = null,
    val infoMessage: UiMessage? = null,
)

class MainViewModel(
    private val container: AppContainer,
    application: Application,
) : AndroidViewModel(application) {
    private val settingsState = MutableStateFlow(container.settingsRepository.defaultState)
    private val createPreviewTaskIds = MutableStateFlow<List<String>>(emptyList())
    private val tab = MutableStateFlow(HomeTab.CREATE)
    private val composer =
        MutableStateFlow(
            ComposerState(
                currentSessionName = application.getString(R.string.default_session_name),
            ),
        )
    private val selectedTaskId = MutableStateFlow<String?>(null)
    private val maskEditorTargetImageId = MutableStateFlow<String?>(null)
    private val settingsSubpage = MutableStateFlow<SettingsSubpage?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val infoMessage = MutableStateFlow<UiMessage?>(null)
    private val query = MutableStateFlow("")
    private val historyStatusFilter = MutableStateFlow(HistoryStatusFilter.ALL)
    private val selectedTask =
        selectedTaskId.flatMapLatest { taskId ->
            if (taskId == null) flowOf(null) else container.taskRepository.observeById(taskId)
        }

    val uiState: StateFlow<MainUiState> = combine(
        settingsState,
        container.taskRepository.observeAll(),
        container.imageStorageRepository.observeAll(),
        container.sessionRepository.observeSessions(),
        tab,
        composer,
        selectedTaskId,
        selectedTask,
        createPreviewTaskIds,
        maskEditorTargetImageId,
        settingsSubpage,
        query,
        historyStatusFilter,
        errorMessage,
        infoMessage,
    ) { values ->
        val settings = values[0] as AppSettingsState
        val tasks = values[1] as List<ImageTask>
        val images = values[2] as List<StoredImageAsset>
        val sessions = values[3] as List<ImageSession>
        val currentTab = values[4] as HomeTab
        val composerState = values[5] as ComposerState
        val taskId = values[6] as String?
        val selectedTaskDetail = values[7] as ImageTask?
        val previewTaskIds = values[8] as List<String>
        val maskTarget = values[9] as String?
        val currentSettingsSubpage = values[10] as SettingsSubpage?
        val currentQuery = values[11] as String
        val currentHistoryStatusFilter = values[12] as HistoryStatusFilter
        val error = values[13] as String?
        val info = values[14] as UiMessage?
        val tasksById = tasks.associateBy { it.id }
        val queryTrimmed = currentQuery.trim()
        val queryFilteredTasks =
            if (queryTrimmed.isBlank()) {
                tasks
            } else {
                tasks.filter { task -> task.matchesHistoryQuery(queryTrimmed) }
            }
        val filteredTasks = queryFilteredTasks.filter { task ->
            currentHistoryStatusFilter.matches(task.status)
        }
        val createPreviewTasks = previewTaskIds.mapNotNull(tasksById::get)
        MainUiState(
            settings = settings,
            tasks = filteredTasks,
            createPreviewTasks = createPreviewTasks,
            hasActiveTasks = hasActiveTasks(tasks),
            images = images.associateBy { it.id },
            sessions = sessions,
            tab = currentTab,
            composer = composerState,
            selectedTaskId = taskId,
            selectedTask = selectedTaskDetail,
            maskEditorTargetImageId = maskTarget,
            settingsSubpage = currentSettingsSubpage,
            query = currentQuery,
            historyStatusFilter = currentHistoryStatusFilter,
            errorMessage = error,
            infoMessage = info,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(loading = true),
    )

    init {
        observeSettings()
        reconcileActiveTasks()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            container.settingsRepository.settings.collect { settings ->
                settingsState.value = settings
            }
        }
    }

    fun selectTab(next: HomeTab) {
        tab.value = next
        selectedTaskId.value = null
        settingsSubpage.value = null
    }

    fun openSettingsSubpage(subpage: SettingsSubpage) {
        tab.value = HomeTab.SETTINGS
        selectedTaskId.value = null
        settingsSubpage.value = subpage
    }

    fun closeSettingsSubpage() {
        settingsSubpage.value = null
    }

    fun updatePrompt(value: String) {
        composer.update { it.copy(prompt = value) }
    }

    fun updateQuery(value: String) {
        query.value = value
    }

    fun updateHistoryStatusFilter(value: HistoryStatusFilter) {
        historyStatusFilter.value = value
    }

    fun updateParams(transform: (TaskParams) -> TaskParams) {
        composer.update { it.copy(params = transform(it.params)) }
    }

    fun clearError() {
        errorMessage.value = null
    }

    fun clearInfoMessage() {
        infoMessage.value = null
    }

    fun addImages(uris: List<android.net.Uri>) {
        viewModelScope.launch {
            runCatching {
                val assets = uris.map { container.imageStorageRepository.importUri(it, ImageSource.UPLOAD) }
                composer.update { state ->
                    state.copy(selectedImageIds = (state.selectedImageIds + assets.map { it.id }).distinct())
                }
            }.onFailure { errorMessage.value = it.message ?: it.toString() }
        }
    }

    fun removeInputImage(imageId: String) {
        composer.update {
            it.copy(
                selectedImageIds = it.selectedImageIds.filterNot { selected -> selected == imageId },
                maskDraft = it.maskDraft?.takeUnless { draft -> draft.targetImageId == imageId },
            )
        }
    }

    fun openMaskEditor(imageId: String) {
        viewModelScope.launch {
            runCatching {
                val prepared = container.imageStorageRepository.prepareMaskWorkingImage(imageId)
                val workingImageId = prepared.asset.id
                composer.update { state ->
                    if (workingImageId == imageId) {
                        state
                    } else {
                        state.copy(
                            selectedImageIds = state.selectedImageIds.replaceImageId(imageId, workingImageId),
                            maskDraft = state.maskDraft?.takeUnless { draft -> draft.targetImageId == imageId },
                        )
                    }
                }
                if (prepared.wasResized || prepared.wasConvertedToPng) {
                    infoMessage.value = UiMessage(
                        title = getApplication<Application>().getString(R.string.dialog_notice),
                        message = getApplication<Application>().getString(
                            R.string.annotation_image_prepared,
                            prepared.originalWidth,
                            prepared.originalHeight,
                            prepared.asset.width ?: 0,
                            prepared.asset.height ?: 0,
                        ),
                    )
                }
                maskEditorTargetImageId.value = workingImageId
            }.onFailure { errorMessage.value = it.message ?: it.toString() }
        }
    }

    fun closeMaskEditor() {
        maskEditorTargetImageId.value = null
    }

    fun saveMaskFromStrokes(targetImageId: String, strokes: List<List<Pair<Float, Float>>>, width: Int, height: Int) {
        viewModelScope.launch {
            runCatching {
                if (strokes.all { it.size < 2 }) {
                    error(getApplication<Application>().getString(R.string.annotation_empty_error))
                }
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                val paint = Paint().apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 48f
                    isAntiAlias = true
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                }
                canvas.drawARGB(255, 255, 255, 255)
                strokes.forEach { stroke ->
                    stroke.windowed(size = 2, step = 1, partialWindows = false).forEach { (a, b) ->
                        canvas.drawLine(a.first, a.second, b.first, b.second, paint)
                    }
                }
                val pixels = IntArray(width * height)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                val transparentCount = pixels.count { pixel -> (pixel ushr 24) == 0 }
                if (transparentCount == 0) {
                    bitmap.recycle()
                    error(getApplication<Application>().getString(R.string.annotation_empty_error))
                }
                if (transparentCount == pixels.size) {
                    bitmap.recycle()
                    error(getApplication<Application>().getString(R.string.annotation_full_error))
                }
                val output = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                bitmap.recycle()
                val bytes = output.toByteArray()
                val asset = container.imageStorageRepository.storeBytes(bytes, "image/png", ImageSource.MASK)
                val draft = MaskDraft(
                    targetImageId = targetImageId,
                    maskImageId = asset.id,
                    updatedAt = System.currentTimeMillis(),
                )
                container.maskDraftRepository.upsert(draft)
                composer.update { state ->
                    val orderedImageIds = state.selectedImageIds.orderMaskTargetFirst(targetImageId)
                    state.copy(
                        selectedImageIds = orderedImageIds,
                        prompt = remapReferenceLabelsForOrder(state.prompt, state.selectedImageIds, orderedImageIds),
                        maskDraft = draft,
                    )
                }
                maskEditorTargetImageId.value = null
            }.onFailure { errorMessage.value = it.message ?: it.toString() }
        }
    }

    fun submitTask() {
        viewModelScope.launch {
            ImageTaskWorker.reconcileActiveTasks(container.taskRepository)
            val settings = uiState.value.settings ?: return@launch
            val profile = settings.profiles.firstOrNull { it.id == settings.activeProfileId } ?: return@launch
            val prompt = composer.value.prompt.trim()
            if (prompt.isBlank()) {
                errorMessage.value = getApplication<Application>().getString(R.string.prompt_required_error)
                return@launch
            }
            val currentComposer = composer.value
            val maskDraft = currentComposer.maskDraft
            if (maskDraft != null && !currentComposer.selectedImageIds.contains(maskDraft.targetImageId)) {
                composer.update { it.copy(maskDraft = null) }
                errorMessage.value = getApplication<Application>().getString(R.string.annotation_target_missing_error)
                return@launch
            }
            val orderedInputImageIds = maskDraft?.let {
                currentComposer.selectedImageIds.orderMaskTargetFirst(it.targetImageId)
            } ?: currentComposer.selectedImageIds
            val sessionId = composer.value.currentSessionId ?: UUID.randomUUID().toString()
            val task = ImageTask(
                id = UUID.randomUUID().toString(),
                prompt = prompt,
                params = currentComposer.params,
                sessionId = sessionId,
                apiProvider = profile.provider,
                apiProfileId = profile.id,
                apiProfileName = profile.name,
                apiModel = profile.model,
                inputImageIds = orderedInputImageIds,
                maskTargetImageId = maskDraft?.targetImageId,
                maskImageId = maskDraft?.maskImageId,
                status = TaskStatus.RUNNING,
                createdAt = System.currentTimeMillis(),
            )
            container.taskRepository.upsert(task)
            rememberCreatePreviewTask(task.id)
            container.sessionRepository.upsertSession(
                ImageSession(
                    id = sessionId,
                    name = currentComposer.currentSessionName.ifBlank {
                        getApplication<Application>().getString(R.string.default_session_name)
                    },
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            ImageTaskWorker.enqueue(getApplication(), task.id)
            composer.update {
                it.copy(
                    prompt = "",
                    selectedImageIds = emptyList(),
                    maskDraft = null,
                    currentSessionId = sessionId,
                )
            }
            tab.value = HomeTab.CREATE
        }
    }

    private fun reconcileActiveTasks() {
        viewModelScope.launch {
            ImageTaskWorker.reconcileAndEnqueueActiveTasks(
                context = getApplication<Application>(),
                repository = container.taskRepository,
            )
        }
    }

    fun openTask(taskId: String) {
        selectedTaskId.value = taskId
    }

    fun closeTask() {
        selectedTaskId.value = null
    }

    fun reuseTask(task: ImageTask) {
        composer.update {
            it.copy(
                prompt = task.prompt,
                params = task.params,
                selectedImageIds = task.inputImageIds,
                maskDraft = task.maskImageId?.let { maskId ->
                    task.maskTargetImageId?.let { targetId ->
                        MaskDraft(targetImageId = targetId, maskImageId = maskId, updatedAt = System.currentTimeMillis())
                    }
                },
                currentSessionId = task.sessionId,
            )
        }
        tab.value = HomeTab.CREATE
        selectedTaskId.value = null
    }

    fun editOutputs(task: ImageTask) {
        composer.update {
            it.copy(
                selectedImageIds = (it.selectedImageIds + task.outputImageIds).distinct(),
                prompt = task.prompt,
            )
        }
        tab.value = HomeTab.CREATE
        selectedTaskId.value = null
    }

    fun toggleFavorite(task: ImageTask) {
        viewModelScope.launch {
            container.taskRepository.upsert(task.copy(isFavorite = !task.isFavorite))
        }
    }

    fun deleteTask(task: ImageTask) {
        viewModelScope.launch {
            container.taskRepository.deleteById(task.id)
            forgetCreatePreviewTask(task.id)
            if (selectedTaskId.value == task.id) selectedTaskId.value = null
        }
    }

    fun saveOutputImage(imageId: String) {
        viewModelScope.launch {
            runCatching {
                container.imageStorageRepository.saveToPictures(imageId)
                infoMessage.value = UiMessage(
                    title = getApplication<Application>().getString(R.string.dialog_notice),
                    message = getApplication<Application>().getString(R.string.image_saved_message),
                )
            }.onFailure { errorMessage.value = it.message ?: it.toString() }
        }
    }

    fun saveTaskOutputImages(task: ImageTask) {
        viewModelScope.launch {
            runCatching {
                var saved = 0
                for (imageId in task.outputImageIds) {
                    runCatching {
                        container.imageStorageRepository.saveToPictures(imageId)
                    }.onSuccess {
                        saved++
                    }
                }
                if (saved == 0) {
                    error(getApplication<Application>().getString(R.string.no_images_saved_error))
                }
                infoMessage.value = UiMessage(
                    title = getApplication<Application>().getString(R.string.dialog_notice),
                    message = getApplication<Application>().getString(R.string.images_saved_message, saved),
                )
            }.onFailure { errorMessage.value = it.message ?: it.toString() }
        }
    }

    fun saveSettings(settings: AppSettingsState) {
        settingsState.value = settings
        applyAppLanguagePreference(settings.appLanguage)
        viewModelScope.launch {
            container.settingsRepository.save(settings)
        }
    }

    fun cancelAllActiveTasks(onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val activeTasks = container.taskRepository.getAll().filter { task ->
                task.status == TaskStatus.QUEUED || task.status == TaskStatus.RUNNING
            }
            activeTasks.forEach { task ->
                ImageTaskWorker.cancel(getApplication(), task.id)
            }
            val updates = activeTasks.mapNotNull { task ->
                cancelledActiveTaskUpdate(task, now = now)
            }
            if (updates.isNotEmpty()) {
                container.taskRepository.upsertAll(updates)
            }
            onComplete?.invoke()
        }
    }

    fun createProfile(name: String, baseUrl: String, apiKey: String) {
        val current = uiState.value.settings ?: return
        val app = getApplication<Application>()
        val newProfile = createDefaultProfile(
            name = name.trim().ifBlank {
                nextDefaultProfileName(current, app.getString(R.string.default_profile_name_prefix))
            },
            baseUrl = baseUrl.trim(),
            apiKey = apiKey.trim(),
        )
        saveSettings(
            current.copy(
                activeProfileId = newProfile.id,
                profiles = current.profiles + newProfile,
            ),
        )
    }

    fun updateProfile(profile: ApiProfile) {
        val current = uiState.value.settings ?: return
        saveSettings(
            current.copy(
                profiles = current.profiles.map { existing -> if (existing.id == profile.id) profile else existing },
            ),
        )
    }

    fun setActiveProfile(profileId: String) {
        val current = uiState.value.settings ?: return
        if (current.profiles.none { it.id == profileId }) return
        saveSettings(current.copy(activeProfileId = profileId))
    }

    fun duplicateProfile(profileId: String) {
        val current = uiState.value.settings ?: return
        val profile = current.profiles.firstOrNull { it.id == profileId } ?: return
        val app = getApplication<Application>()
        val copy = copyApiProfile(
            profile = profile,
            defaultName = app.getString(R.string.new_profile_name),
            copySuffix = app.getString(R.string.profile_copy_suffix),
        )
        saveSettings(current.copy(profiles = current.profiles + copy, activeProfileId = copy.id))
    }

    fun deleteProfile(profileId: String) {
        val current = uiState.value.settings ?: return
        saveSettings(deleteProfileFromSettings(current, profileId))
    }

    fun moveProfile(profileId: String, offset: Int) {
        val current = uiState.value.settings ?: return
        val index = current.profiles.indexOfFirst { it.id == profileId }
        val targetIndex = (index + offset).coerceIn(0, current.profiles.lastIndex)
        if (index < 0 || index == targetIndex) return
        val next = current.profiles.toMutableList()
        val moved = next.removeAt(index)
        next.add(targetIndex, moved)
        saveSettings(current.copy(profiles = next))
    }

    fun importCustomProviderJson(text: String) {
        viewModelScope.launch {
            runCatching {
                val current = uiState.value.settings ?: return@launch
                val imported = decodeCustomProviderImport(text)
                val nextProviders = (current.customProviders + imported.customProviders).distinctBy { it.id }
                val settingsWithProviders = current.copy(customProviders = nextProviders)
                val usedIds = current.profiles.map { it.id }.toMutableSet()
                val importedProfiles = imported.profiles.map { profile ->
                    val normalized = normalizeImportedProfile(profile, settingsWithProviders)
                    if (usedIds.add(normalized.id)) normalized else normalized.copy(id = UUID.randomUUID().toString())
                }
                saveSettings(
                    settingsWithProviders.copy(
                        profiles = settingsWithProviders.profiles + importedProfiles,
                        activeProfileId = importedProfiles.firstOrNull()?.id ?: settingsWithProviders.activeProfileId,
                    ),
                )
            }.onFailure {
                errorMessage.value = it.message ?: it.toString()
            }
        }
    }

    fun updateCustomProviderJson(providerId: String, text: String) {
        viewModelScope.launch {
            runCatching {
                val current = uiState.value.settings ?: return@launch
                val provider = decodeSingleCustomProvider(text).copy(id = providerId)
                saveSettings(
                    current.copy(
                        customProviders = current.customProviders.map {
                            if (it.id == providerId) provider else it
                        },
                    ),
                )
            }.onFailure {
                errorMessage.value = it.message ?: it.toString()
            }
        }
    }

    fun deleteCustomProvider(providerId: String) {
        val current = uiState.value.settings ?: return
        saveSettings(removeCustomProviderFromSettings(current, providerId))
    }

    fun exportBackup(uri: Uri, exportConfig: Boolean, exportTasks: Boolean) {
        viewModelScope.launch {
            runCatching {
                val settings = uiState.value.settings
                val tasks = if (exportTasks) container.taskRepository.getAll() else emptyList()
                val assets = if (exportTasks) container.imageStorageRepository.getAll() else emptyList()
                val backup = AndroidBackup(
                    settings = settings.takeIf { exportConfig },
                    tasks = tasks,
                    images = assets.map { asset ->
                        BackupImage(
                            id = asset.id,
                            path = imageZipPath(asset),
                            mimeType = asset.mimeType,
                            source = asset.source,
                        )
                    },
                )
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { output ->
                    ZipOutputStream(output).use { zip ->
                        zip.putNextEntry(ZipEntry("manifest.json"))
                        zip.write(AppJson.encodeToString(AndroidBackup.serializer(), backup).toByteArray())
                        zip.closeEntry()
                        if (exportTasks) {
                            assets.forEach { asset ->
                                val file = File(asset.filePath)
                                if (!file.exists()) return@forEach
                                zip.putNextEntry(ZipEntry(imageZipPath(asset)))
                                file.inputStream().use { it.copyTo(zip) }
                                zip.closeEntry()
                            }
                        }
                    }
                } ?: error("Unable to open export target.")
                infoMessage.value = UiMessage(
                    title = getApplication<Application>().getString(R.string.dialog_notice),
                    message = getApplication<Application>().getString(R.string.data_exported_message),
                )
            }.onFailure {
                errorMessage.value = it.message ?: it.toString()
            }
        }
    }

    fun importBackup(uri: Uri, importConfig: Boolean, importTasks: Boolean) {
        viewModelScope.launch {
            runCatching {
                val entries = mutableMapOf<String, ByteArray>()
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                    ZipInputStream(input).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory) {
                                entries[entry.name] = zip.readBytes()
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                } ?: error("Unable to open import file.")
                val manifest = entries["manifest.json"]?.decodeToString()
                    ?: error("Backup manifest.json is missing.")
                val backup = AppJson.decodeFromString(AndroidBackup.serializer(), manifest)
                if (importConfig) {
                    backup.settings?.let { container.settingsRepository.save(it) }
                }
                if (importTasks) {
                    backup.images.forEach { image ->
                        val bytes = entries[image.path] ?: return@forEach
                        container.imageStorageRepository.storeBytes(bytes, image.mimeType, image.source)
                    }
                    container.taskRepository.upsertAll(backup.tasks)
                }
                infoMessage.value = UiMessage(
                    title = getApplication<Application>().getString(R.string.dialog_notice),
                    message = getApplication<Application>().getString(R.string.data_imported_message),
                )
            }.onFailure {
                errorMessage.value = it.message ?: it.toString()
            }
        }
    }

    fun clearSelectedData(clearConfig: Boolean, clearTasks: Boolean) {
        viewModelScope.launch {
            runCatching {
                if (clearTasks) {
                    container.taskRepository.deleteAll()
                    container.sessionRepository.deleteAll()
                    container.maskDraftRepository.deleteAll()
                    container.imageStorageRepository.deleteAll()
                    createPreviewTaskIds.value = emptyList()
                    selectedTaskId.value = null
                    composer.update { it.copy(selectedImageIds = emptyList(), maskDraft = null) }
                }
                if (clearConfig) {
                    container.settingsRepository.reset()
                }
                infoMessage.value = UiMessage(
                    title = getApplication<Application>().getString(R.string.dialog_notice),
                    message = getApplication<Application>().getString(R.string.data_cleared_message),
                )
            }.onFailure {
                errorMessage.value = it.message ?: it.toString()
            }
        }
    }

    private fun rememberCreatePreviewTask(taskId: String) {
        createPreviewTaskIds.update { previewTaskIds ->
            (listOf(taskId) + previewTaskIds.filterNot { it == taskId }).take(CREATE_PREVIEW_LIMIT)
        }
    }

    private fun forgetCreatePreviewTask(taskId: String) {
        createPreviewTaskIds.update { previewTaskIds -> previewTaskIds.filterNot { it == taskId } }
    }
}

class MainViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(
                container = container,
                application = container.appContext as android.app.Application,
            ) as T
        }
        error("Unknown ViewModel class: ${modelClass.name}")
    }
}

private fun List<String>.replaceImageId(oldId: String, newId: String): List<String> {
    var replaced = false
    val next = map { id ->
        if (id == oldId) {
            replaced = true
            newId
        } else {
            id
        }
    }
    return if (replaced) next.distinct() else this
}

private fun List<String>.orderMaskTargetFirst(targetImageId: String): List<String> {
    val targetIndex = indexOf(targetImageId)
    if (targetIndex <= 0) return this
    val next = toMutableList()
    val target = next.removeAt(targetIndex)
    next.add(0, target)
    return next
}

private fun HistoryStatusFilter.matches(status: TaskStatus): Boolean =
    when (this) {
        HistoryStatusFilter.ALL -> true
        HistoryStatusFilter.ACTIVE -> status == TaskStatus.QUEUED || status == TaskStatus.RUNNING
        HistoryStatusFilter.DONE -> status == TaskStatus.DONE
        HistoryStatusFilter.ERROR -> status == TaskStatus.ERROR
    }

private fun ImageTask.matchesHistoryQuery(query: String): Boolean =
    prompt.contains(query, ignoreCase = true) ||
        params.size.contains(query, ignoreCase = true) ||
        (apiProfileName?.contains(query, ignoreCase = true) == true) ||
        (apiModel?.contains(query, ignoreCase = true) == true)

internal fun hasActiveTasks(tasks: List<ImageTask>): Boolean =
    tasks.any { it.status == TaskStatus.QUEUED || it.status == TaskStatus.RUNNING }

private fun decodeCustomProviderImport(text: String): CustomProviderImportPayload {
    val element = AppJson.parseToJsonElement(text)
    return when (element) {
        is JsonObject -> {
            val providersElement = element["customProviders"]
            if (providersElement != null) {
                AppJson.decodeFromJsonElement(CustomProviderImportPayload.serializer(), element)
            } else {
                CustomProviderImportPayload(customProviders = listOf(AppJson.decodeFromJsonElement(CustomProviderDefinition.serializer(), element)))
            }
        }
        is JsonArray -> CustomProviderImportPayload(
            customProviders = AppJson.decodeFromJsonElement(ListSerializer(CustomProviderDefinition.serializer()), element),
        )
        else -> error("Unsupported provider JSON.")
    }
}

private fun decodeSingleCustomProvider(text: String): CustomProviderDefinition {
    val imported = decodeCustomProviderImport(text)
    return imported.customProviders.firstOrNull() ?: error("Provider JSON is empty.")
}

private fun imageZipPath(asset: StoredImageAsset): String {
    val extension = when {
        asset.mimeType.contains("jpeg", ignoreCase = true) -> "jpg"
        asset.mimeType.contains("webp", ignoreCase = true) -> "webp"
        else -> "png"
    }
    return "images/${asset.id}.$extension"
}

private const val CREATE_PREVIEW_LIMIT = 5
