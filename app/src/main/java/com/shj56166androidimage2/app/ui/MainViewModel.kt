package com.shj56166androidimage2.app.ui

import android.app.Application
import android.content.ClipData
import android.content.Intent
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
import com.shj56166androidimage2.app.data.network.ProfileConnectionTestResult
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
import com.shj56166androidimage2.app.ui.screens.CreateProfileDraft
import com.shj56166androidimage2.app.ui.screens.CreateProfileTestState
import com.shj56166androidimage2.app.ui.screens.createDefaultProfile
import com.shj56166androidimage2.app.ui.screens.deleteProfileFromSettings
import com.shj56166androidimage2.app.ui.screens.copyApiProfile
import com.shj56166androidimage2.app.ui.screens.nextDefaultProfileName
import com.shj56166androidimage2.app.ui.screens.normalizeImportedProfile
import com.shj56166androidimage2.app.ui.screens.removeCustomProviderFromSettings
import com.shj56166androidimage2.app.util.applyAppLanguagePreference
import com.shj56166androidimage2.app.worker.ImageTaskWorker
import com.shj56166androidimage2.app.worker.cancelledActiveTaskUpdate
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
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

sealed interface ProfileConnectionTestState {
    object Testing : ProfileConnectionTestState

    data class Success(
        val message: String,
    ) : ProfileConnectionTestState

    data class Error(
        val message: String,
    ) : ProfileConnectionTestState
}

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
    val favoritesOnly: Boolean = false,
    val profileConnectionStates: Map<String, ProfileConnectionTestState> = emptyMap(),
    val createProfileTestState: CreateProfileTestState = CreateProfileTestState(),
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
    private val favoritesOnly = MutableStateFlow(false)
    private val profileConnectionStates = MutableStateFlow<Map<String, ProfileConnectionTestState>>(emptyMap())
    private val createProfileTestState = MutableStateFlow(CreateProfileTestState())
    private var createProfileTestJob: Job? = null
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
        favoritesOnly,
        profileConnectionStates,
        createProfileTestState,
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
        val currentFavoritesOnly = values[13] as Boolean
        val currentProfileConnectionStates = values[14] as Map<String, ProfileConnectionTestState>
        val currentCreateProfileTestState = values[15] as CreateProfileTestState
        val error = values[16] as String?
        val info = values[17] as UiMessage?
        val tasksById = tasks.associateBy { it.id }
        val filteredTasks = filterHistoryTasks(tasks, currentQuery, currentHistoryStatusFilter, currentFavoritesOnly)
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
            favoritesOnly = currentFavoritesOnly,
            profileConnectionStates = currentProfileConnectionStates,
            createProfileTestState = currentCreateProfileTestState,
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

    fun updateFavoritesOnly(value: Boolean) {
        favoritesOnly.value = value
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
            val task = buildSubmittedTask(
                profile = profile,
                prompt = prompt,
                params = currentComposer.params,
                sessionId = sessionId,
                inputImageIds = orderedInputImageIds,
                maskDraft = maskDraft,
                taskId = UUID.randomUUID().toString(),
                createdAt = System.currentTimeMillis(),
            )
            enqueueTask(task, currentComposer.currentSessionName)
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

    fun retryTask(task: ImageTask) {
        viewModelScope.launch {
            val sessionId = task.sessionId ?: UUID.randomUUID().toString()
            val retryTask = buildRetryTask(
                task,
                newTaskId = UUID.randomUUID().toString(),
                createdAt = System.currentTimeMillis(),
                sessionId = sessionId,
            )
            val sessionName = resolveSessionName(retryTask.sessionId)
            enqueueTask(retryTask, sessionName)
            selectedTaskId.value = retryTask.id
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

    fun shareOutputImage(task: ImageTask, imageId: String) {
        viewModelScope.launch {
            runCatching {
                val image = container.imageStorageRepository.getShareableImage(imageId)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = image.mimeType
                    putExtra(Intent.EXTRA_STREAM, image.uri)
                    putExtra(Intent.EXTRA_TEXT, buildTaskShareText(task, imageCount = 1))
                    clipData = ClipData.newRawUri(task.prompt, image.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                launchShareChooser(intent, getApplication<Application>().getString(R.string.share_image_action))
            }.onFailure { errorMessage.value = it.message ?: it.toString() }
        }
    }

    fun shareTaskOutputImages(task: ImageTask) {
        viewModelScope.launch {
            runCatching {
                val shareableImages = task.outputImageIds.map { imageId ->
                    container.imageStorageRepository.getShareableImage(imageId)
                }
                if (shareableImages.isEmpty()) {
                    error(getApplication<Application>().getString(R.string.no_images_shared_error))
                }
                val imageUris = ArrayList(shareableImages.map { it.uri })
                val shareMimeType = shareableImages.map { it.mimeType }.distinct().singleOrNull() ?: "*/*"
                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = shareMimeType
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, imageUris)
                    putExtra(Intent.EXTRA_TEXT, buildTaskShareText(task, imageCount = shareableImages.size))
                    clipData = buildShareClipData(task.prompt, imageUris)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                launchShareChooser(intent, getApplication<Application>().getString(R.string.share_all_images_action))
            }.onFailure { errorMessage.value = it.message ?: it.toString() }
        }
    }

    fun testProfileConnection(profileId: String) {
        val profile = uiState.value.settings?.profiles?.firstOrNull { it.id == profileId } ?: return
        profileConnectionStates.update { it + (profileId to ProfileConnectionTestState.Testing) }
        viewModelScope.launch {
            val nextState =
                when (val result = container.profileConnectionTester().test(profile)) {
                    is ProfileConnectionTestResult.Success -> ProfileConnectionTestState.Success(result.message)
                    is ProfileConnectionTestResult.Error -> ProfileConnectionTestState.Error(result.message)
                }
            profileConnectionStates.update { states -> states + (profileId to nextState) }
        }
    }

    fun testCreateProfileDraft(draft: CreateProfileDraft) {
        createProfileTestJob?.cancel()
        createProfileTestJob =
            viewModelScope.launch {
                container.profileConnectionTester().testDraft(draft) { state ->
                    createProfileTestState.value = state
                }
            }
    }

    fun resetCreateProfileTestState() {
        createProfileTestJob?.cancel()
        createProfileTestJob = null
        createProfileTestState.value = CreateProfileTestState()
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

    fun createProfile(draft: CreateProfileDraft) {
        val current = uiState.value.settings ?: return
        val app = getApplication<Application>()
        val newProfile = createDefaultProfile(
            name = draft.name.trim().ifBlank {
                nextDefaultProfileName(current, app.getString(R.string.default_profile_name_prefix))
            },
            baseUrl = draft.baseUrl.trim(),
            apiKey = draft.apiKey.trim(),
            apiMode = draft.apiMode,
            codexCliLikeMode = draft.codexCliLikeMode,
            responseFormatB64Json = draft.responseFormatB64Json,
        )
        resetCreateProfileTestState()
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
        profileConnectionStates.update { states -> states - profileId }
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

    private suspend fun enqueueTask(task: ImageTask, sessionName: String) {
        val sessionId = task.sessionId ?: error("Task session ID is required.")
        container.taskRepository.upsert(task)
        rememberCreatePreviewTask(task.id)
        container.sessionRepository.upsertSession(
            ImageSession(
                id = sessionId,
                name = sessionName,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        ImageTaskWorker.enqueue(getApplication(), task.id)
    }

    private suspend fun resolveSessionName(sessionId: String?): String {
        val defaultSessionName = getApplication<Application>().getString(R.string.default_session_name)
        if (sessionId.isNullOrBlank()) return defaultSessionName
        return container.sessionRepository.getSession(sessionId)?.name ?: defaultSessionName
    }

    private fun launchShareChooser(intent: Intent, title: String) {
        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(chooser)
    }

    private fun buildTaskShareText(task: ImageTask, imageCount: Int): String {
        val app = getApplication<Application>()
        return listOf(
            task.prompt,
            "${app.getString(R.string.provider_label)}: ${task.apiProfileName ?: task.apiProvider}",
            "${app.getString(R.string.model_label)}: ${task.apiModel ?: app.getString(R.string.not_set_value)}",
            "${app.getString(R.string.size_label)}: ${task.params.size}",
            "${app.getString(R.string.quality_label)}: ${task.params.quality}",
            "${app.getString(R.string.format_label)}: ${task.params.outputFormat.uppercase()}",
            "${app.getString(R.string.count_label)}: $imageCount",
            formatElapsedShareLine(task, app),
        ).filter { it.isNotBlank() }.joinToString(separator = "\n")
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

internal fun filterHistoryTasks(
    tasks: List<ImageTask>,
    query: String,
    statusFilter: HistoryStatusFilter,
    favoritesOnly: Boolean,
): List<ImageTask> {
    val queryTrimmed = query.trim()
    return tasks.filter { task ->
        (!favoritesOnly || task.isFavorite) &&
            statusFilter.matches(task.status) &&
            (queryTrimmed.isBlank() || task.matchesHistoryQuery(queryTrimmed))
    }
}

private fun ImageTask.matchesHistoryQuery(query: String): Boolean =
    prompt.contains(query, ignoreCase = true) ||
        params.size.contains(query, ignoreCase = true) ||
        (apiProfileName?.contains(query, ignoreCase = true) == true) ||
        (apiModel?.contains(query, ignoreCase = true) == true)

internal fun canShowTaskRetryAction(task: ImageTask, alwaysShowRetryButton: Boolean): Boolean =
    when (task.status) {
        TaskStatus.ERROR -> true
        TaskStatus.DONE -> alwaysShowRetryButton
        TaskStatus.QUEUED,
        TaskStatus.RUNNING,
        -> false
    }

internal fun buildSubmittedTask(
    profile: ApiProfile,
    prompt: String,
    params: TaskParams,
    sessionId: String,
    inputImageIds: List<String>,
    maskDraft: MaskDraft?,
    taskId: String,
    createdAt: Long,
): ImageTask =
    ImageTask(
        id = taskId,
        prompt = prompt,
        params = params,
        sessionId = sessionId,
        apiProvider = profile.provider,
        apiProfileId = profile.id,
        apiProfileName = profile.name,
        apiModel = profile.model,
        inputImageIds = inputImageIds,
        maskTargetImageId = maskDraft?.targetImageId,
        maskImageId = maskDraft?.maskImageId,
        status = TaskStatus.RUNNING,
        createdAt = createdAt,
    )

internal fun buildRetryTask(
    task: ImageTask,
    newTaskId: String,
    createdAt: Long,
    sessionId: String,
): ImageTask =
    task.copy(
        id = newTaskId,
        sessionId = sessionId,
        remoteTaskId = null,
        outputImageIds = emptyList(),
        rawImageUrls = emptyList(),
        rawResponsePayload = null,
        actualParamsByImage = emptyMap(),
        revisedPromptByImage = emptyMap(),
        status = TaskStatus.RUNNING,
        error = null,
        createdAt = createdAt,
        finishedAt = null,
        elapsedMillis = null,
        isFavorite = false,
    )

internal fun hasActiveTasks(tasks: List<ImageTask>): Boolean =
    tasks.any { it.status == TaskStatus.QUEUED || it.status == TaskStatus.RUNNING }

private fun buildShareClipData(label: String, uris: List<Uri>): ClipData? {
    val firstUri = uris.firstOrNull() ?: return null
    return ClipData.newRawUri(label, firstUri).apply {
        uris.drop(1).forEach { uri -> addItem(ClipData.Item(uri)) }
    }
}

private fun formatElapsedShareLine(task: ImageTask, application: Application): String =
    task.elapsedMillis?.let { elapsedMillis ->
        val seconds = (elapsedMillis / 1_000L).coerceAtLeast(0L)
        "${application.getString(R.string.elapsed_time_label)}: ${seconds}s"
    }.orEmpty()

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
