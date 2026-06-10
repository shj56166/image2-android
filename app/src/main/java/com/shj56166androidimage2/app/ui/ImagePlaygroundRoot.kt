package com.shj56166androidimage2.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.shj56166androidimage2.app.R
import com.shj56166androidimage2.app.data.model.ImageSource
import com.shj56166androidimage2.app.data.model.ImageTask
import com.shj56166androidimage2.app.data.model.StoredImageAsset
import com.shj56166androidimage2.app.data.model.TaskParams
import com.shj56166androidimage2.app.data.model.TaskStatus
import com.shj56166androidimage2.app.ui.screens.MaskEditorScreen
import com.shj56166androidimage2.app.ui.screens.SettingsAboutScreen
import com.shj56166androidimage2.app.ui.screens.SettingsCustomProvidersScreen
import com.shj56166androidimage2.app.ui.screens.SettingsDataScreen
import com.shj56166androidimage2.app.ui.screens.SettingsGeneralScreen
import com.shj56166androidimage2.app.ui.screens.SettingsProfilesScreen
import com.shj56166androidimage2.app.ui.screens.SettingsScreen
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.delay

private data class TopLevelDestination(
    val tab: HomeTab,
    @StringRes val labelRes: Int,
    val iconRes: Int,
)

private data class SimpleChoiceOption(
    val value: String,
    @StringRes val labelRes: Int,
)

private data class TaskOutputImageUiModel(
    val imageId: String,
    val filePath: String?,
    val thumbnailPath: String?,
    val exists: Boolean,
    val mimeType: String?,
    val width: Int?,
    val height: Int?,
    val source: ImageSource?,
    val taskParams: TaskParams?,
    val remoteImageUrl: String?,
)

private val topLevelDestinations =
    listOf(
        TopLevelDestination(HomeTab.CREATE, R.string.nav_create, R.drawable.ic_edit),
        TopLevelDestination(HomeTab.HISTORY, R.string.nav_history, R.drawable.ic_history),
        TopLevelDestination(HomeTab.SETTINGS, R.string.nav_settings, R.drawable.ic_settings),
    )

private val qualityOptions =
    listOf(
        SimpleChoiceOption("auto", R.string.quality_auto),
        SimpleChoiceOption("low", R.string.quality_low),
        SimpleChoiceOption("medium", R.string.quality_medium),
        SimpleChoiceOption("high", R.string.quality_high),
    )

private val outputFormatOptions =
    listOf(
        SimpleChoiceOption("png", R.string.option_png),
        SimpleChoiceOption("jpeg", R.string.option_jpeg),
        SimpleChoiceOption("webp", R.string.option_webp),
    )

private val moderationOptions =
    listOf(
        SimpleChoiceOption("auto", R.string.moderation_auto),
        SimpleChoiceOption("low", R.string.moderation_low),
    )

private val historyStatusFilters =
    listOf(
        HistoryStatusFilter.ALL,
        HistoryStatusFilter.ACTIVE,
        HistoryStatusFilter.DONE,
        HistoryStatusFilter.ERROR,
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagePlaygroundRoot(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context.findActivity()
    val picker = rememberLauncherForActivityResult(PickMultipleVisualMedia()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.addImages(uris)
    }
    var showExitConfirmation by rememberSaveable { mutableStateOf(false) }
    var pendingExportConfig by rememberSaveable { mutableStateOf(true) }
    var pendingExportTasks by rememberSaveable { mutableStateOf(true) }
    var pendingImportConfig by rememberSaveable { mutableStateOf(true) }
    var pendingImportTasks by rememberSaveable { mutableStateOf(true) }
    val backupExporter = rememberLauncherForActivityResult(CreateDocument("application/zip")) { uri: Uri? ->
        if (uri != null) viewModel.exportBackup(uri, pendingExportConfig, pendingExportTasks)
    }
    val backupImporter = rememberLauncherForActivityResult(OpenDocument()) { uri: Uri? ->
        if (uri != null) viewModel.importBackup(uri, pendingImportConfig, pendingImportTasks)
    }
    val isSecondaryPage = state.selectedTaskId != null || state.settingsSubpage != null
    val activeDestination = topLevelDestinations.first { it.tab == state.tab }
    val topLevelScrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val secondaryTitleRes =
        when {
            state.selectedTaskId != null -> R.string.task_details_title
            state.settingsSubpage == SettingsSubpage.GENERAL -> R.string.settings_general_title
            state.settingsSubpage == SettingsSubpage.PROFILES -> R.string.settings_api_profiles_title
            state.settingsSubpage == SettingsSubpage.CUSTOM_PROVIDERS -> R.string.settings_custom_providers_title
            state.settingsSubpage == SettingsSubpage.DATA -> R.string.settings_data_title
            state.settingsSubpage == SettingsSubpage.ABOUT -> R.string.settings_about_title
            else -> null
        }
    val closeSecondaryPage =
        when {
            state.selectedTaskId != null -> viewModel::closeTask
            state.settingsSubpage != null -> viewModel::closeSettingsSubpage
            else -> null
        }

    if (closeSecondaryPage != null) {
        BackHandler(onBack = closeSecondaryPage)
    }
    if (!isSecondaryPage && state.hasActiveTasks && activity != null) {
        BackHandler {
            showExitConfirmation = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier =
                if (isSecondaryPage) Modifier
                else Modifier.nestedScroll(topLevelScrollBehavior.nestedScrollConnection),
            topBar = {
                if (isSecondaryPage) {
                    SecondaryTopBar(
                        title = secondaryTitleRes?.let { stringResource(it) }.orEmpty(),
                        onBack = closeSecondaryPage ?: {},
                    )
                } else {
                    TopLevelTopBar(
                        title = stringResource(activeDestination.labelRes),
                        scrollBehavior = topLevelScrollBehavior,
                    )
                }
            },
            bottomBar = {
                if (!isSecondaryPage) {
                    CompactBottomBar(
                        selectedTab = state.tab,
                        onSelectTab = viewModel::selectTab,
                    )
                }
            },
        ) { padding ->
            when {
                state.selectedTaskId != null -> {
                    val task = state.selectedTask ?: state.tasks.firstOrNull { it.id == state.selectedTaskId }
                    if (task != null) {
                        val outputImages = task.outputImageIds.mapIndexed { index, imageId ->
                            val asset = state.images[imageId]
                            TaskOutputImageUiModel(
                                imageId = imageId,
                                filePath = asset?.filePath,
                                thumbnailPath = asset?.thumbnailPath,
                                exists = asset?.filePath?.let { File(it).exists() } == true,
                                mimeType = asset?.mimeType,
                                width = asset?.width,
                                height = asset?.height,
                                source = asset?.source,
                                taskParams = task.actualParamsByImage[imageId],
                                remoteImageUrl = task.rawImageUrls.getOrNull(index),
                            )
                        }
                        TaskDetailScreen(
                            padding = padding,
                            task = task,
                            outputImages = outputImages,
                            onReuse = { viewModel.reuseTask(task) },
                            onEditOutputs = { viewModel.editOutputs(task) },
                            onToggleFavorite = { viewModel.toggleFavorite(task) },
                            onDelete = { viewModel.deleteTask(task) },
                            onSaveImage = viewModel::saveOutputImage,
                            onSaveAllImages = { viewModel.saveTaskOutputImages(task) },
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.record_unavailable),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                state.settingsSubpage != null -> {
                    when (state.settingsSubpage) {
                        SettingsSubpage.GENERAL ->
                            SettingsGeneralScreen(
                                state = state,
                                padding = padding,
                                onSaveSettings = viewModel::saveSettings,
                            )

                        SettingsSubpage.PROFILES ->
                            SettingsProfilesScreen(
                                state = state,
                                padding = padding,
                                onUpdateProfile = viewModel::updateProfile,
                                onCreateProfile = viewModel::createProfile,
                                onSetActiveProfile = viewModel::setActiveProfile,
                                onDuplicateProfile = viewModel::duplicateProfile,
                                onDeleteProfile = viewModel::deleteProfile,
                                onMoveProfile = viewModel::moveProfile,
                            )

                        SettingsSubpage.CUSTOM_PROVIDERS ->
                            SettingsCustomProvidersScreen(
                                state = state,
                                padding = padding,
                                onImportProvider = viewModel::importCustomProviderJson,
                                onUpdateProvider = viewModel::updateCustomProviderJson,
                                onDeleteProvider = viewModel::deleteCustomProvider,
                            )

                        SettingsSubpage.DATA ->
                            SettingsDataScreen(
                                state = state,
                                padding = padding,
                                onExportData = { exportConfig, exportTasks ->
                                    pendingExportConfig = exportConfig
                                    pendingExportTasks = exportTasks
                                    backupExporter.launch("image-playground-backup.zip")
                                },
                                onImportData = { importConfig, importTasks ->
                                    pendingImportConfig = importConfig
                                    pendingImportTasks = importTasks
                                    backupImporter.launch(arrayOf("application/zip", "application/octet-stream"))
                                },
                                onClearData = viewModel::clearSelectedData,
                            )

                        SettingsSubpage.ABOUT -> SettingsAboutScreen(padding = padding)

                        null -> Unit
                    }
                }

                else -> {
                    when (state.tab) {
                        HomeTab.CREATE ->
                            CreateScreen(
                                state = state,
                                padding = padding,
                                onPromptChange = viewModel::updatePrompt,
                                onPickImages = {
                                    picker.launch(
                                        PickVisualMediaRequest(PickVisualMedia.ImageOnly),
                                    )
                                },
                                onRemoveImage = viewModel::removeInputImage,
                                onOpenMaskEditor = viewModel::openMaskEditor,
                                onOpenTask = viewModel::openTask,
                                onSubmit = viewModel::submitTask,
                                onParamsChange = { next -> viewModel.updateParams { next } },
                            )

                        HomeTab.HISTORY ->
                            HistoryScreen(
                                state = state,
                                padding = padding,
                                onQueryChange = viewModel::updateQuery,
                                onStatusFilterChange = viewModel::updateHistoryStatusFilter,
                                onOpenTask = viewModel::openTask,
                                onDeleteTask = viewModel::deleteTask,
                            )

                        HomeTab.SETTINGS ->
                            SettingsScreen(
                                state = state,
                                padding = padding,
                                onOpenSubpage = viewModel::openSettingsSubpage,
                            )
                    }
                }
            }
        }

        state.maskEditorTargetImageId?.let { imageId ->
            val target = state.images[imageId]
            if (target != null) {
                MaskEditorScreen(
                    imagePath = target.filePath,
                    width = target.width ?: 1024,
                    height = target.height ?: 1024,
                    modifier = Modifier.fillMaxSize().zIndex(1f).systemBarsPadding().navigationBarsPadding(),
                    onDismiss = viewModel::closeMaskEditor,
                    onSave = {
                        strokes ->
                        viewModel.saveMaskFromStrokes(
                            imageId,
                            strokes,
                            target.width ?: 1024,
                            target.height ?: 1024,
                        )
                    },
                )
            }
        }

        state.errorMessage?.let { message ->
            AlertDialog(
                onDismissRequest = viewModel::clearError,
                confirmButton = {
                    TextButton(onClick = viewModel::clearError) {
                        Text(stringResource(R.string.dialog_ok))
                    }
                },
                title = { Text(stringResource(R.string.error_title)) },
                text = { Text(message) },
            )
        }
        state.infoMessage?.let { info ->
            AlertDialog(
                onDismissRequest = viewModel::clearInfoMessage,
                confirmButton = {
                    TextButton(onClick = viewModel::clearInfoMessage) {
                        Text(stringResource(R.string.dialog_ok))
                    }
                },
                title = { Text(info.title) },
                text = { Text(info.message) },
            )
        }
        if (showExitConfirmation) {
            AlertDialog(
                onDismissRequest = { showExitConfirmation = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showExitConfirmation = false
                            viewModel.cancelAllActiveTasks {
                                activity?.finish()
                            }
                        },
                    ) {
                        Text(stringResource(R.string.active_tasks_exit_confirm))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showExitConfirmation = false },
                    ) {
                        Text(stringResource(R.string.cancel_action))
                    }
                },
                title = { Text(stringResource(R.string.active_tasks_exit_title)) },
                text = { Text(stringResource(R.string.active_tasks_exit_message)) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopLevelTopBar(
    title: String,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
) {
    val typography = MaterialTheme.typography
    val topBarTypography =
        remember(typography) { typography.copy(headlineMedium = typography.displaySmall) }
    MaterialTheme(typography = topBarTypography) {
        LargeTopAppBar(
            title = { Text(text = title) },
            expandedHeight = 124.dp,
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            scrollBehavior = scrollBehavior,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecondaryTopBar(
    title: String,
    onBack: () -> Unit,
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = title,
                )
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
            ),
    )
}

@Composable
private fun CompactBottomBar(
    selectedTab: HomeTab,
    onSelectTab: (HomeTab) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        topLevelDestinations.forEach { destination ->
            NavigationBarItem(
                selected = destination.tab == selectedTab,
                onClick = { onSelectTab(destination.tab) },
                icon = {
                    Icon(
                        painter = painterResource(destination.iconRes),
                        contentDescription = stringResource(destination.labelRes),
                    )
                },
                label = { Text(text = stringResource(destination.labelRes)) },
                colors =
                    NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
            )
        }
    }
}

@Composable
internal fun CreateScreen(
    state: MainUiState,
    padding: PaddingValues,
    onPromptChange: (String) -> Unit,
    onPickImages: () -> Unit,
    onRemoveImage: (String) -> Unit,
    onOpenMaskEditor: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onSubmit: () -> Unit,
    onParamsChange: (TaskParams) -> Unit,
) {
    val composer = state.composer
    val colorScheme = MaterialTheme.colorScheme
    val promptReferenceVisualTransformation =
        remember(composer.selectedImageIds.size, colorScheme.primaryContainer, colorScheme.onPrimaryContainer) {
            PromptReferenceVisualTransformation(
                maxReferenceCount = composer.selectedImageIds.size,
                backgroundColor = colorScheme.primaryContainer,
                contentColor = colorScheme.onPrimaryContainer,
            )
        }
    var promptFieldValue by rememberSaveable(state.selectedTaskId, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(
            TextFieldValue(
                text = composer.prompt,
                selection = TextRange(composer.prompt.length),
            ),
        )
    }
    var lastLocalPrompt by rememberSaveable(state.selectedTaskId) { mutableStateOf(composer.prompt) }

    LaunchedEffect(composer.prompt) {
        if (composer.prompt != promptFieldValue.text && composer.prompt != lastLocalPrompt) {
            promptFieldValue =
                TextFieldValue(
                    text = composer.prompt,
                    selection = TextRange(composer.prompt.length),
                )
            lastLocalPrompt = composer.prompt
        }
    }

    val handlePromptValueChange: (TextFieldValue) -> Unit = { nextValue ->
        promptFieldValue = nextValue
        lastLocalPrompt = nextValue.text
        onPromptChange(nextValue.text)
    }

    val handleReferenceInsert: (String) -> Unit = { imageId ->
        val label = referenceLabelForImageId(composer.selectedImageIds, imageId)
        if (label != null) {
            val inserted =
                insertReferenceLabel(
                    text = promptFieldValue.text,
                    selectionStart = promptFieldValue.selection.start,
                    selectionEnd = promptFieldValue.selection.end,
                    label = label,
                )
            val nextValue =
                TextFieldValue(
                    text = inserted.text,
                    selection = TextRange(inserted.cursor),
                )
            promptFieldValue = nextValue
            lastLocalPrompt = nextValue.text
            onPromptChange(nextValue.text)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).testTag("create_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.prompt_label),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("create_prompt_label"),
                )
                OutlinedTextField(
                    value = promptFieldValue,
                    onValueChange = handlePromptValueChange,
                    modifier = Modifier.fillMaxWidth().testTag("create_prompt_input"),
                    minLines = 4,
                    shape = RoundedCornerShape(24.dp),
                    placeholder = { Text(stringResource(R.string.prompt_placeholder)) },
                    visualTransformation = promptReferenceVisualTransformation,
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            errorContainerColor = Color.Transparent,
                        ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(onClick = onSubmit) {
                        Text(stringResource(R.string.generate_action))
                    }
                }
            }
        }
        item {
            ReferenceImagesSection(
                state = state,
                selectedImageIds = composer.selectedImageIds,
                onPickImages = onPickImages,
                onRemoveImage = onRemoveImage,
                onOpenMaskEditor = onOpenMaskEditor,
                onInsertReference = handleReferenceInsert,
            )
        }
        item {
            ParameterSection(params = composer.params, onParamsChange = onParamsChange)
        }
        if (state.createPreviewTasks.isNotEmpty()) {
            item {
                CreatePreviewSection(
                    tasks = state.createPreviewTasks,
                    images = state.images,
                    onOpenTask = onOpenTask,
                )
            }
        }
    }
}

@Composable
private fun ReferenceImagesSection(
    state: MainUiState,
    selectedImageIds: List<String>,
    onPickImages: () -> Unit,
    onRemoveImage: (String) -> Unit,
    onOpenMaskEditor: (String) -> Unit,
    onInsertReference: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("create_reference_section"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.references_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedButton(
                onClick = onPickImages,
                modifier = Modifier.testTag("create_add_reference_button"),
            ) {
                Icon(painter = painterResource(R.drawable.ic_add), contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.add_references))
            }
        }

        if (selectedImageIds.isEmpty()) {
            Text(
                text = stringResource(R.string.references_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            selectedImageIds.forEach { imageId ->
                val asset = state.images[imageId] ?: return@forEach
                Card {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            onClick = { onInsertReference(asset.id) },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.size(72.dp).testTag("reference_insert_${asset.id}"),
                        ) {
                            AsyncImage(
                                model = asset.thumbnailPath ?: asset.filePath,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = referenceLabelForImageId(selectedImageIds, asset.id) ?: asset.id.take(12),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(R.string.reference_insert_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistChip(
                                    onClick = { onOpenMaskEditor(asset.id) },
                                    modifier = Modifier.testTag("reference_mask_${asset.id}"),
                                    label = { Text(stringResource(R.string.mask_action)) },
                                )
                                AssistChip(
                                    onClick = { onRemoveImage(asset.id) },
                                    modifier = Modifier.testTag("reference_remove_${asset.id}"),
                                    label = { Text(stringResource(R.string.remove_action)) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParameterSection(params: TaskParams, onParamsChange: (TaskParams) -> Unit) {
    var showSheet by rememberSaveable { mutableStateOf(false) }
    var showSizePicker by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Surface(
        modifier = Modifier.fillMaxWidth().testTag("create_parameter_section"),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = { showSheet = true },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(R.string.parameters_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = buildParameterSummary(params),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("create_parameter_summary"),
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_expand_more),
                contentDescription = stringResource(R.string.expand_parameters),
                modifier = Modifier.testTag("create_parameter_toggle"),
            )
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            modifier = Modifier.testTag("create_parameter_sheet"),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.parameters_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = buildParameterSummary(params),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Column(
                    modifier = Modifier.testTag("create_parameter_form"),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ParameterSizeRow(
                        params = params,
                        onOpenSizePicker = { showSizePicker = true },
                    )
                    ParameterChoiceRow(
                        label = stringResource(R.string.quality_label),
                        options = qualityOptions,
                        currentValue = params.quality,
                        onValueChange = { onParamsChange(params.copy(quality = it)) },
                    )
                    ParameterChoiceRow(
                        label = stringResource(R.string.format_label),
                        options = outputFormatOptions,
                        currentValue = params.outputFormat,
                        onValueChange = { onParamsChange(params.copy(outputFormat = it)) },
                    )
                    ParameterCompressionField(
                        params = params,
                        onParamsChange = onParamsChange,
                    )
                    ParameterChoiceRow(
                        label = stringResource(R.string.moderation_label),
                        options = moderationOptions,
                        currentValue = params.moderation,
                        onValueChange = { onParamsChange(params.copy(moderation = it)) },
                    )
                    CountStepper(
                        count = params.count,
                        onCountChange = { nextCount ->
                            onParamsChange(params.copy(count = nextCount))
                        },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { showSheet = false }) {
                        Text(stringResource(R.string.dialog_ok))
                    }
                }
            }
        }
    }

    if (showSizePicker) {
        SizePickerDialog(
            currentSize = params.size,
            onDismiss = { showSizePicker = false },
            onConfirm = { nextSize ->
                onParamsChange(params.copy(size = nextSize))
                showSizePicker = false
            },
        )
    }
}

@Composable
private fun ParameterSizeRow(
    params: TaskParams,
    onOpenSizePicker: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.size_label),
            style = MaterialTheme.typography.labelLarge,
        )
        Surface(
            modifier = Modifier.fillMaxWidth().testTag("create_size_selector"),
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            onClick = onOpenSizePicker,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = displaySizeValue(params.size), style = MaterialTheme.typography.bodyLarge)
                Icon(painter = painterResource(R.drawable.ic_expand_more), contentDescription = null)
            }
        }
    }
}

@Composable
private fun ParameterChoiceRow(
    label: String,
    options: List<SimpleChoiceOption>,
    currentValue: String,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = currentValue == option.value,
                    onClick = { onValueChange(option.value) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) {
                    Text(stringResource(option.labelRes))
                }
            }
        }
    }
}

@Composable
private fun ParameterCompressionField(
    params: TaskParams,
    onParamsChange: (TaskParams) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.compression_label), style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = params.outputCompression?.toString().orEmpty(),
            onValueChange = { input ->
                val next = input.trim()
                if (next.isBlank()) {
                    onParamsChange(params.copy(outputCompression = null))
                } else {
                    next.toIntOrNull()?.let { onParamsChange(params.copy(outputCompression = it.coerceIn(0, 100))) }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = params.outputFormat != "png",
            label = { Text(stringResource(R.string.compression_label)) },
            singleLine = true,
        )
    }
}

@Composable
private fun CountStepper(
    count: Int,
    onCountChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.count_label), style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { onCountChange((count - 1).coerceAtLeast(1)) },
                enabled = count > 1,
                modifier = Modifier.testTag("count_decrement"),
            ) {
                Text("-")
            }
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .testTag("count_value"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            OutlinedButton(
                onClick = { onCountChange((count + 1).coerceAtMost(10)) },
                enabled = count < 10,
                modifier = Modifier.testTag("count_increment"),
            ) {
                Text("+")
            }
        }
    }
}

@Composable
private fun buildParameterSummary(params: TaskParams): String {
    val compressionText = params.outputCompression?.toString() ?: stringResource(R.string.parameter_default_value)
    return listOf(
        "${stringResource(R.string.size_label)} ${displaySizeValue(params.size)}",
        "${stringResource(R.string.quality_label)} ${taskParamValueLabel(params.quality)}",
        "${stringResource(R.string.format_label)} ${formatLabel(params.outputFormat)}",
        "${stringResource(R.string.compression_label)} $compressionText",
        "${stringResource(R.string.moderation_label)} ${taskParamValueLabel(params.moderation)}",
        "${stringResource(R.string.count_label)} ${params.count}",
    ).joinToString(" · ")
}

private fun formatLabel(value: String): String =
    when (value) {
        "jpeg" -> "JPEG"
        "webp" -> "WebP"
        else -> "PNG"
    }

@Composable
private fun CreatePreviewSection(
    tasks: List<ImageTask>,
    images: Map<String, StoredImageAsset>,
    onOpenTask: (String) -> Unit,
) {
    val nowMillis = rememberElapsedTicker()

    Column(
        modifier = Modifier.fillMaxWidth().testTag("create_preview_section"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.create_tasks_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        tasks.forEach { task ->
            TaskPreviewCard(
                task = task,
                previewPath = taskPreviewPath(task, images),
                isGenerating = isTaskGenerating(task),
                elapsedText = formatElapsedForTask(task, nowMillis),
                onClick = { onOpenTask(task.id) },
                modifier = Modifier.testTag("create_preview_${task.id}"),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryScreen(
    state: MainUiState,
    padding: PaddingValues,
    onQueryChange: (String) -> Unit,
    onStatusFilterChange: (HistoryStatusFilter) -> Unit,
    onOpenTask: (String) -> Unit,
    onDeleteTask: (ImageTask) -> Unit,
) {
    var pendingDeleteTask by remember { mutableStateOf<ImageTask?>(null) }
    val nowMillis = rememberElapsedTicker()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.search_history)) },
                    )
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        historyStatusFilters.forEach { filter ->
                            FilterChip(
                                selected = state.historyStatusFilter == filter,
                                onClick = { onStatusFilterChange(filter) },
                                label = { Text(historyStatusFilterLabel(filter)) },
                            )
                        }
                    }
                }
            }
            items(state.tasks, key = { it.id }) { task ->
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    TaskPreviewCard(
                        task = task,
                        previewPath = taskPreviewPath(task, state.images),
                        isGenerating = isTaskGenerating(task),
                        elapsedText = formatElapsedForTask(task, nowMillis),
                        onClick = { onOpenTask(task.id) },
                        onLongClick = { showMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete_action)) },
                            onClick = {
                                showMenu = false
                                pendingDeleteTask = task
                            },
                        )
                    }
                }
            }
        }

        pendingDeleteTask?.let { task ->
            AlertDialog(
                onDismissRequest = { pendingDeleteTask = null },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingDeleteTask = null
                            onDeleteTask(task)
                        },
                    ) {
                        Text(stringResource(R.string.delete_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteTask = null }) {
                        Text(stringResource(R.string.cancel_action))
                    }
                },
                title = { Text(stringResource(R.string.delete_task_title)) },
                text = { Text(stringResource(R.string.delete_task_message)) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskPreviewCard(
    task: ImageTask,
    previewPath: String?,
    isGenerating: Boolean,
    elapsedText: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val interactionModifier =
        when {
            onClick != null && onLongClick != null ->
                Modifier.combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
            onClick != null -> Modifier.clickable(onClick = onClick)
            onLongClick != null ->
                Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = onLongClick,
                )
            else -> Modifier
        }

    Card(
        modifier = modifier.then(interactionModifier),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(72.dp)
                        .aspectRatio(1f),
                contentAlignment = Alignment.Center,
            ) {
                if (previewPath != null && File(previewPath).exists()) {
                    AsyncImage(
                        model = previewPath,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {}
                }
                if (isGenerating) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.28f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(26.dp),
                            strokeWidth = 2.5.dp,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(task.prompt, maxLines = 3, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text =
                        taskProgressFraction(task)?.let { progress ->
                            stringResource(
                                R.string.task_summary_progress,
                                taskStatusLabel(task.status),
                                task.params.size,
                                progress,
                            )
                        } ?: stringResource(
                            R.string.task_summary,
                            taskStatusLabel(task.status),
                            task.params.size,
                            task.outputImageIds.size,
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                elapsedText?.let {
                    Text(
                        text = stringResource(R.string.elapsed_time_line, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskDetailScreen(
    padding: PaddingValues,
    task: ImageTask,
    outputImages: List<TaskOutputImageUiModel>,
    onReuse: () -> Unit,
    onEditOutputs: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onSaveImage: (String) -> Unit,
    onSaveAllImages: () -> Unit,
) {
    var previewIndex by rememberSaveable(task.id) { mutableStateOf<Int?>(null) }
    var detailImageId by rememberSaveable(task.id) { mutableStateOf<String?>(null) }
    val selectedDetailImage = outputImages.firstOrNull { it.imageId == detailImageId }
    val pendingPlaceholderCount = pendingOutputPlaceholderCount(task, outputImages.size)

    BackHandler(enabled = previewIndex != null) {
        previewIndex = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(task.prompt, style = MaterialTheme.typography.titleLarge)
            }
            if (outputImages.size > 1) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OutlinedButton(
                            onClick = onSaveAllImages,
                            enabled = outputImages.any { it.exists },
                        ) {
                            Text(stringResource(R.string.save_all_images_action))
                        }
                    }
                }
            }
            if (outputImages.isNotEmpty() || pendingPlaceholderCount > 0) {
                items(outputImages, key = { it.imageId }) { image ->
                    val imageIndex = outputImages.indexOfFirst { it.imageId == image.imageId }.coerceAtLeast(0)
                    Card {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (image.exists && image.filePath != null) {
                                AsyncImage(
                                    model = image.filePath,
                                    contentDescription = null,
                                    contentScale = ContentScale.FillWidth,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onClick = { previewIndex = imageIndex },
                                            ),
                                )
                            } else {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.image_missing_message),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            ) {
                                OutlinedButton(
                                    onClick = { detailImageId = image.imageId },
                                ) {
                                    Text(stringResource(R.string.view_image_parameters_action))
                                }
                                OutlinedButton(
                                    onClick = { onSaveImage(image.imageId) },
                                    enabled = image.exists,
                                ) {
                                    Text(stringResource(R.string.save_image_action))
                                }
                            }
                        }
                    }
                }
                items(pendingPlaceholderCount, key = { "pending-output-$it" }) {
                    LoadingOutputPlaceholderCard()
                }
            }
            item {
                val notSetValue = stringResource(R.string.not_set_value)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(
                            R.string.detail_line,
                            stringResource(R.string.provider_label),
                            task.apiProfileName ?: task.apiProvider,
                        )
                    )
                    Text(
                        stringResource(
                            R.string.detail_line,
                            stringResource(R.string.model_label),
                            task.apiModel ?: notSetValue,
                        )
                    )
                    Text(
                        stringResource(
                            R.string.detail_line,
                            stringResource(R.string.status_label),
                            taskStatusLabel(task.status),
                        )
                    )
                    task.error?.let {
                        Text(
                            stringResource(
                                R.string.detail_line,
                                stringResource(R.string.error_title),
                                it,
                            )
                        )
                    }
                }
            }
            if (task.revisedPromptByImage.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.revised_prompt_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        task.outputImageIds.forEachIndexed { index, imageId ->
                            task.revisedPromptByImage[imageId]?.takeIf { it.isNotBlank() }?.let { revisedPrompt ->
                                Text(
                                    text = stringResource(R.string.revised_prompt_item, index + 1, revisedPrompt),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onReuse) { Text(stringResource(R.string.reuse_action)) }
                    Button(onClick = onEditOutputs) { Text(stringResource(R.string.edit_action)) }
                    OutlinedButton(onClick = onToggleFavorite) {
                        Text(
                            stringResource(
                                if (task.isFavorite) R.string.unfavorite_action else R.string.favorite_action
                            )
                        )
                    }
                    OutlinedButton(onClick = onDelete) { Text(stringResource(R.string.delete_action)) }
                }
            }
        }

        previewIndex?.let { currentIndex ->
            ImagePreviewOverlay(
                outputImages = outputImages,
                initialIndex = currentIndex.coerceIn(0, max(outputImages.lastIndex, 0)),
                onDismiss = { previewIndex = null },
            )
        }
    }

    selectedDetailImage?.let { image ->
        ImageParameterSheet(
            task = task,
            image = image,
            imageIndex = outputImages.indexOfFirst { it.imageId == image.imageId }.coerceAtLeast(0),
            onDismiss = { detailImageId = null },
        )
    }
}

@Composable
private fun LoadingOutputPlaceholderCard() {
    Card {
        Surface(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(34.dp),
                    strokeWidth = 3.dp,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageParameterSheet(
    task: ImageTask,
    image: TaskOutputImageUiModel,
    imageIndex: Int,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val effectiveParams = image.taskParams ?: task.params
    var showRawDetails by rememberSaveable(image.imageId) { mutableStateOf(false) }
    val rawResponseSummary = remember(task.rawResponsePayload) { truncateRawResponse(task.rawResponsePayload) }
    val nowMillis = rememberElapsedTicker()
    val elapsedText = formatElapsedForTask(task, nowMillis)
    val notSetValue = stringResource(R.string.not_set_value)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.image_parameters_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.image_index_value, imageIndex + 1),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailLine(stringResource(R.string.provider_label), task.apiProfileName ?: task.apiProvider)
                DetailLine(stringResource(R.string.model_label), task.apiModel ?: notSetValue)
                DetailLine(stringResource(R.string.status_label), taskStatusLabel(task.status))
                elapsedText?.let {
                    DetailLine(stringResource(R.string.elapsed_time_label), it)
                }
                DetailLine(stringResource(R.string.size_label), displaySizeValue(effectiveParams.size))
                DetailLine(stringResource(R.string.quality_label), taskParamValueLabel(effectiveParams.quality))
                DetailLine(stringResource(R.string.format_label), effectiveParams.outputFormat.uppercase())
                DetailLine(
                    stringResource(R.string.compression_label),
                    effectiveParams.outputCompression?.toString() ?: stringResource(R.string.parameter_default_value),
                )
                DetailLine(stringResource(R.string.moderation_label), taskParamValueLabel(effectiveParams.moderation))
                DetailLine(
                    stringResource(R.string.image_resolution_label),
                    buildResolutionValue(image.width, image.height, notSetValue),
                )
                DetailLine(
                    stringResource(R.string.mime_type_label),
                    image.mimeType ?: notSetValue,
                )
                DetailLine(
                    stringResource(R.string.image_source_label),
                    image.source?.let { imageSourceLabel(it) } ?: notSetValue,
                )
            }

            if (image.remoteImageUrl != null || rawResponseSummary != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showRawDetails = !showRawDetails }) {
                        Text(
                            stringResource(
                                if (showRawDetails) R.string.hide_raw_details_action else R.string.show_raw_details_action,
                            ),
                        )
                    }
                    if (showRawDetails) {
                        image.remoteImageUrl?.let { remoteImageUrl ->
                            DetailLine(stringResource(R.string.remote_image_url_label), remoteImageUrl)
                        }
                        rawResponseSummary?.let { payload ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = stringResource(R.string.raw_response_label),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = payload,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImagePreviewOverlay(
    outputImages: List<TaskOutputImageUiModel>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = initialIndex) { outputImages.size }
    val pageScales =
        remember(outputImages.map { it.imageId }) {
            mutableStateListOf<Float>().apply {
                repeat(outputImages.size) { add(1f) }
            }
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.96f))
                .zIndex(2f),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = pageScales.getOrElse(pagerState.currentPage) { 1f } <= 1.05f,
        ) { page ->
            PreviewImagePage(
                image = outputImages[page],
                onScaleChanged = { scale ->
                    if (page in pageScales.indices) pageScales[page] = scale
                },
            )
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .systemBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.close_preview_action),
                    tint = Color.White,
                )
            }
            Text(
                text = stringResource(R.string.image_preview_counter, pagerState.currentPage + 1, outputImages.size),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PreviewImagePage(
    image: TaskOutputImageUiModel,
    onScaleChanged: (Float) -> Unit,
) {
    if (!image.exists || image.filePath == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.image_missing_message),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        return
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        var scale by remember(image.imageId) { mutableStateOf(1f) }
        var offset by remember(image.imageId) { mutableStateOf(Offset.Zero) }
        var containerSize by remember(image.imageId) { mutableStateOf(IntSize.Zero) }
        val state =
            rememberTransformableState { zoomChange, panChange, _ ->
                val nextScale = (scale * zoomChange).coerceIn(1f, 4f)
                scale = nextScale
                offset =
                    if (nextScale <= 1f) {
                        Offset.Zero
                    } else {
                        clampPreviewOffset(offset + panChange, nextScale, containerSize)
                    }
                onScaleChanged(scale)
            }

        LaunchedEffect(scale) {
            onScaleChanged(scale)
        }

        AsyncImage(
            model = image.filePath,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier =
                Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .onSizeChanged { containerSize = it }
                    .pointerInput(image.imageId) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                                onScaleChanged(scale)
                            },
                        )
                    }
                    .transformable(state)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Text(
        text = stringResource(R.string.detail_line, label, value),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun imageSourceLabel(source: ImageSource): String =
    when (source) {
        ImageSource.UPLOAD -> stringResource(R.string.image_source_upload)
        ImageSource.GENERATED -> stringResource(R.string.image_source_generated)
        ImageSource.MASK -> stringResource(R.string.image_source_mask)
    }

@Composable
private fun taskParamValueLabel(value: String): String =
    when (value.lowercase()) {
        "auto" -> stringResource(R.string.parameter_auto_value)
        "low" -> stringResource(R.string.quality_low)
        "medium" -> stringResource(R.string.quality_medium)
        "high" -> stringResource(R.string.quality_high)
        else -> value
    }

@Composable
private fun displaySizeValue(value: String): String =
    if (value.isBlank() || value.equals("auto", ignoreCase = true)) {
        stringResource(R.string.parameter_auto_value)
    } else {
        value
    }

private fun buildResolutionValue(
    width: Int?,
    height: Int?,
    fallback: String,
): String = if (width != null && height != null) "$width x $height" else fallback

private fun truncateRawResponse(rawResponsePayload: String?): String? {
    val normalized = rawResponsePayload?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val limit = 1200
    return if (normalized.length <= limit) normalized else normalized.take(limit) + "..."
}

private fun clampPreviewOffset(
    offset: Offset,
    scale: Float,
    containerSize: IntSize,
): Offset {
    if (containerSize == IntSize.Zero || scale <= 1f) return Offset.Zero
    val maxX = (containerSize.width * (scale - 1f)) / 2f
    val maxY = (containerSize.height * (scale - 1f)) / 2f
    return Offset(
        x = offset.x.coerceIn(-maxX, maxX),
        y = offset.y.coerceIn(-maxY, maxY),
    )
}

@Composable
private fun rememberElapsedTicker(): Long {
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            nowMillis = System.currentTimeMillis()
        }
    }
    return nowMillis
}

@Composable
private fun formatElapsedForTask(task: ImageTask, nowMillis: Long): String? {
    val elapsedMillis =
        when {
            task.elapsedMillis != null -> task.elapsedMillis
            task.status == TaskStatus.RUNNING || task.status == TaskStatus.QUEUED -> (nowMillis - task.createdAt).coerceAtLeast(0L)
            else -> null
        } ?: return null
    return formatElapsedMillis(elapsedMillis)
}

@Composable
private fun formatElapsedMillis(elapsedMillis: Long): String {
    val totalSeconds = (elapsedMillis / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        hours > 0L -> stringResource(R.string.elapsed_hours_minutes_seconds_value, hours, minutes, seconds)
        minutes > 0L -> stringResource(R.string.elapsed_minutes_seconds_value, minutes, seconds)
        else -> stringResource(R.string.elapsed_seconds_value, seconds)
    }
}

private fun taskPreviewPath(
    task: ImageTask,
    images: Map<String, StoredImageAsset>,
): String? = task.outputImageIds.firstOrNull()?.let { imageId ->
    images[imageId]?.thumbnailPath ?: images[imageId]?.filePath
}

private fun isTaskGenerating(task: ImageTask): Boolean =
    task.status == TaskStatus.RUNNING || task.status == TaskStatus.QUEUED

internal fun taskProgressFraction(task: ImageTask): String? {
    if (!isTaskGenerating(task)) return null
    val expectedCount = task.params.count.coerceAtLeast(1)
    if (expectedCount <= 1) return null
    return "${task.outputImageIds.size.coerceAtMost(expectedCount)}/$expectedCount"
}

internal fun pendingOutputPlaceholderCount(task: ImageTask, renderedOutputCount: Int): Int {
    if (task.status != TaskStatus.RUNNING && task.status != TaskStatus.QUEUED) return 0
    return (task.params.count.coerceAtLeast(1) - renderedOutputCount).coerceAtLeast(0)
}

@Composable
private fun taskStatusLabel(status: TaskStatus): String {
    val labelRes =
        when (status) {
            TaskStatus.QUEUED -> R.string.task_status_queued
            TaskStatus.RUNNING -> R.string.task_status_running
            TaskStatus.DONE -> R.string.task_status_done
            TaskStatus.ERROR -> R.string.task_status_error
        }
    return stringResource(labelRes)
}

@Composable
private fun historyStatusFilterLabel(filter: HistoryStatusFilter): String {
    val labelRes =
        when (filter) {
            HistoryStatusFilter.ALL -> R.string.history_filter_all
            HistoryStatusFilter.ACTIVE -> R.string.history_filter_active
            HistoryStatusFilter.DONE -> R.string.history_filter_done
            HistoryStatusFilter.ERROR -> R.string.history_filter_error
        }
    return stringResource(labelRes)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

