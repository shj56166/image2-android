package com.shj56166androidimage2.app.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.shj56166androidimage2.app.R
import com.shj56166androidimage2.app.data.model.ApiMode
import com.shj56166androidimage2.app.data.model.AppLanguage
import com.shj56166androidimage2.app.data.model.ApiProfile
import com.shj56166androidimage2.app.data.model.AppSettingsState
import com.shj56166androidimage2.app.data.model.CustomProviderDefinition
import com.shj56166androidimage2.app.data.repo.AppJson
import com.shj56166androidimage2.app.ui.MainUiState
import com.shj56166androidimage2.app.ui.SettingsSubpage
import kotlinx.serialization.encodeToString

private data class ApiModeOption(
    val mode: ApiMode,
    @StringRes val labelRes: Int,
)

private val apiModeOptions =
    listOf(
        ApiModeOption(ApiMode.RESPONSES, R.string.mode_responses),
        ApiModeOption(ApiMode.IMAGES, R.string.mode_images),
    )

private data class AppLanguageOption(
    val value: AppLanguage,
    @StringRes val labelRes: Int,
)

private val appLanguageOptions =
    listOf(
        AppLanguageOption(AppLanguage.SYSTEM, R.string.language_option_system),
        AppLanguageOption(AppLanguage.ENGLISH, R.string.language_option_english),
        AppLanguageOption(AppLanguage.SIMPLIFIED_CHINESE, R.string.language_option_simplified_chinese),
        AppLanguageOption(AppLanguage.TRADITIONAL_CHINESE, R.string.language_option_traditional_chinese),
    )

private data class NewProfileDraft(
    val name: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",
)

@Composable
fun SettingsScreen(
    state: MainUiState,
    padding: PaddingValues,
    onOpenSubpage: (SettingsSubpage) -> Unit,
) {
    val settings = state.settings ?: return
    val activeProfile = settings.profiles.firstOrNull { it.id == settings.activeProfileId }
    val notSetValue = stringResource(R.string.not_set_value)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).testTag("settings_home"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingsNavigationItem(
                title = stringResource(R.string.settings_general_title),
                summary = stringResource(R.string.settings_general_summary),
                iconRes = R.drawable.ic_settings,
                testTag = "settings_general_entry",
                onClick = { onOpenSubpage(SettingsSubpage.GENERAL) },
            )
        }
        item {
            SettingsNavigationItem(
                title = stringResource(R.string.settings_api_profiles_title),
                summary = stringResource(
                    R.string.settings_current_profile_summary,
                    activeProfile?.name ?: notSetValue,
                    settings.profiles.size,
                ),
                iconRes = R.drawable.ic_settings,
                testTag = "settings_api_profiles_entry",
                onClick = { onOpenSubpage(SettingsSubpage.PROFILES) },
            )
        }
        item {
            SettingsNavigationItem(
                title = stringResource(R.string.settings_custom_providers_title),
                summary = stringResource(R.string.settings_provider_count, settings.customProviders.size),
                iconRes = R.drawable.ic_add,
                testTag = "settings_custom_providers_entry",
                onClick = { onOpenSubpage(SettingsSubpage.CUSTOM_PROVIDERS) },
            )
        }
        item {
            SettingsNavigationItem(
                title = stringResource(R.string.settings_data_title),
                summary = stringResource(R.string.settings_data_summary),
                iconRes = R.drawable.ic_history,
                testTag = "settings_data_entry",
                onClick = { onOpenSubpage(SettingsSubpage.DATA) },
            )
        }
        item {
            SettingsNavigationItem(
                title = stringResource(R.string.settings_about_title),
                summary = stringResource(R.string.settings_about_summary),
                iconRes = R.drawable.ic_arrow_forward,
                testTag = "settings_about_entry",
                onClick = { onOpenSubpage(SettingsSubpage.ABOUT) },
            )
        }
    }
}

@Composable
fun SettingsGeneralScreen(
    state: MainUiState,
    padding: PaddingValues,
    onSaveSettings: (AppSettingsState) -> Unit,
) {
    val settings = state.settings ?: return
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).testTag("settings_general_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingsLanguageItem(
                title = stringResource(R.string.settings_language_title),
                summary = stringResource(R.string.settings_language_summary),
                value = settings.appLanguage,
                onValueChange = { onSaveSettings(settings.copy(appLanguage = it)) },
            )
        }
        item {
            SettingsSwitchItem(
                title = stringResource(R.string.settings_enter_submit_title),
                summary = stringResource(R.string.settings_enter_submit_summary),
                checked = settings.enterSubmit,
                testTag = "settings_enter_submit",
                onCheckedChange = { onSaveSettings(settings.copy(enterSubmit = it)) },
            )
        }
        item {
            SettingsSwitchItem(
                title = stringResource(R.string.settings_clear_input_after_submit_title),
                summary = stringResource(R.string.settings_clear_input_after_submit_summary),
                checked = settings.clearInputAfterSubmit,
                testTag = "settings_clear_input_after_submit",
                onCheckedChange = { onSaveSettings(settings.copy(clearInputAfterSubmit = it)) },
            )
        }
        item {
            SettingsSwitchItem(
                title = stringResource(R.string.settings_keep_draft_title),
                summary = stringResource(R.string.settings_keep_draft_summary),
                checked = settings.persistInputOnRestart,
                testTag = "settings_persist_input_on_restart",
                onCheckedChange = { onSaveSettings(settings.copy(persistInputOnRestart = it)) },
            )
        }
        item {
            SettingsSwitchItem(
                title = stringResource(R.string.settings_reuse_task_profile_title),
                summary = stringResource(R.string.settings_reuse_task_profile_summary),
                checked = settings.reuseTaskApiProfileTemporarily,
                testTag = "settings_reuse_task_api_profile",
                onCheckedChange = { onSaveSettings(settings.copy(reuseTaskApiProfileTemporarily = it)) },
            )
        }
        item {
            SettingsSwitchItem(
                title = stringResource(R.string.settings_always_show_retry_title),
                summary = stringResource(R.string.settings_always_show_retry_summary),
                checked = settings.alwaysShowRetryButton,
                testTag = "settings_always_show_retry",
                onCheckedChange = { onSaveSettings(settings.copy(alwaysShowRetryButton = it)) },
            )
        }
    }
}

@Composable
fun SettingsProfilesScreen(
    state: MainUiState,
    padding: PaddingValues,
    onUpdateProfile: (ApiProfile) -> Unit,
    onCreateProfile: (String, String, String) -> Unit,
    onSetActiveProfile: (String) -> Unit,
    onDuplicateProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onMoveProfile: (String, Int) -> Unit,
) {
    val settings = state.settings ?: return
    val activeProfile = settings.profiles.firstOrNull { it.id == settings.activeProfileId }
    var expandedProfileId by rememberSaveable {
        mutableStateOf(settings.activeProfileId)
    }
    var showCreateProfileDialog by rememberSaveable { mutableStateOf(false) }
    var newProfileDraft by remember { mutableStateOf(NewProfileDraft()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).testTag("settings_profiles_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ProfilesOverviewCard(
                settings = settings,
                activeProfile = activeProfile,
                onCreateProfile = {
                    newProfileDraft = NewProfileDraft()
                    showCreateProfileDialog = true
                },
            )
        }
        if (settings.profiles.isEmpty()) {
            item {
                EmptyProfilesCard()
            }
        }
        itemsIndexed(settings.profiles, key = { _, item -> item.id }) { index, profile ->
            val expanded = expandedProfileId == profile.id
            ProfileCard(
                settings = settings,
                profile = profile,
                index = index,
                expanded = expanded,
                canDelete = true,
                canMoveUp = index > 0,
                canMoveDown = index < settings.profiles.lastIndex,
                onToggleExpanded = { expandedProfileId = if (expanded) "" else profile.id },
                onUpdateProfile = onUpdateProfile,
                onSetActiveProfile = onSetActiveProfile,
                onDuplicateProfile = onDuplicateProfile,
                onDeleteProfile = onDeleteProfile,
                onMoveProfile = onMoveProfile,
            )
        }
    }

    if (showCreateProfileDialog) {
        val canConfirm = newProfileDraft.baseUrl.isNotBlank() && newProfileDraft.apiKey.isNotBlank()
        AlertDialog(
            onDismissRequest = { showCreateProfileDialog = false },
            confirmButton = {
                TextButton(
                    enabled = canConfirm,
                    modifier = Modifier.testTag("settings_create_profile_confirm"),
                    onClick = {
                        expandedProfileId = ""
                        showCreateProfileDialog = false
                        onCreateProfile(
                            newProfileDraft.name,
                            newProfileDraft.baseUrl,
                            newProfileDraft.apiKey,
                        )
                    },
                ) {
                    Text(stringResource(R.string.dialog_ok))
                }
            },
            dismissButton = {
                TextButton(
                    modifier = Modifier.testTag("settings_create_profile_cancel"),
                    onClick = { showCreateProfileDialog = false },
                ) {
                    Text(stringResource(R.string.cancel_action))
                }
            },
            title = { Text(stringResource(R.string.new_profile)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newProfileDraft.name,
                        onValueChange = { newProfileDraft = newProfileDraft.copy(name = it) },
                        modifier = Modifier.fillMaxWidth().testTag("settings_new_profile_name"),
                        label = { Text(stringResource(R.string.profile_name)) },
                    )
                    OutlinedTextField(
                        value = newProfileDraft.baseUrl,
                        onValueChange = { newProfileDraft = newProfileDraft.copy(baseUrl = it) },
                        modifier = Modifier.fillMaxWidth().testTag("settings_new_profile_base_url"),
                        label = { Text(stringResource(R.string.relay_address)) },
                    )
                    OutlinedTextField(
                        value = newProfileDraft.apiKey,
                        onValueChange = { newProfileDraft = newProfileDraft.copy(apiKey = it) },
                        modifier = Modifier.fillMaxWidth().testTag("settings_new_profile_api_key"),
                        label = { Text(stringResource(R.string.upstream_api_key)) },
                    )
                }
            },
        )
    }
}

@Composable
private fun SettingsLanguageItem(
    title: String,
    summary: String,
    value: AppLanguage,
    onValueChange: (AppLanguage) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val selectedLabel = stringResource(appLanguageOptions.first { it.value == value }.labelRes)

    Card(shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().testTag("settings_language")) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(summary)
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth().testTag("settings_language_selector"),
                        onClick = { expanded = true },
                    ) {
                        Text(selectedLabel)
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        appLanguageOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(stringResource(option.labelRes)) },
                                onClick = {
                                    expanded = false
                                    if (option.value != value) onValueChange(option.value)
                                },
                            )
                        }
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
private fun ProfilesOverviewCard(
    settings: AppSettingsState,
    activeProfile: ApiProfile?,
    onCreateProfile: () -> Unit,
) {
    val notSetValue = stringResource(R.string.not_set_value)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_api_profiles_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
            )
            FilledTonalButton(
                modifier = Modifier.testTag("settings_new_profile"),
                onClick = onCreateProfile,
            ) {
                Text(stringResource(R.string.new_profile))
            }
        }
        Text(
            text = stringResource(
                R.string.settings_current_profile_summary,
                activeProfile?.name ?: notSetValue,
                settings.profiles.size,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyProfilesCard() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_empty_profiles_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.settings_empty_profiles_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProfileCard(
    settings: AppSettingsState,
    profile: ApiProfile,
    index: Int,
    expanded: Boolean,
    canDelete: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggleExpanded: () -> Unit,
    onUpdateProfile: (ApiProfile) -> Unit,
    onSetActiveProfile: (String) -> Unit,
    onDuplicateProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onMoveProfile: (String, Int) -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val isActive = profile.id == settings.activeProfileId
    val displayName = if (profile.name.isBlank()) stringResource(R.string.new_profile_name) else profile.name
    val previewText = profilePreviewText(
        profile = profile,
        requestUrlLabel = stringResource(R.string.profile_preview_request_url_label),
        apiKeyLabel = stringResource(R.string.profile_preview_api_key_label),
        emptyValue = stringResource(R.string.profile_preview_empty_value),
    )

    Card(
        modifier = Modifier.fillMaxWidth().testTag("settings_profile_${profile.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (isActive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = displayName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (isActive) {
                    Text(
                        text = stringResource(R.string.active_profile),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = previewText,
                modifier = Modifier.fillMaxWidth().testTag("settings_profile_preview_${profile.id}"),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    modifier = Modifier.testTag("settings_toggle_profile_${profile.id}"),
                    onClick = onToggleExpanded,
                ) {
                    Text(
                        stringResource(
                            if (expanded) R.string.profile_collapse_action else R.string.profile_expand_action,
                        ),
                    )
                }
                if (!isActive) {
                    OutlinedButton(
                        modifier = Modifier.testTag("settings_set_active_${profile.id}"),
                        onClick = { onSetActiveProfile(profile.id) },
                    ) {
                        Text(stringResource(R.string.set_active))
                    }
                }
                OutlinedButton(
                    modifier = Modifier.testTag("settings_duplicate_${profile.id}"),
                    onClick = { onDuplicateProfile(profile.id) },
                ) {
                    Text(stringResource(R.string.profile_duplicate_action))
                }
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ProfileEditor(
                    settings = settings,
                    profile = profile,
                    onUpdateProfile = onUpdateProfile,
                )
            }
            if (canMoveUp || canMoveDown || canDelete) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (canMoveUp) {
                        TextButton(
                            modifier = Modifier.testTag("settings_move_up_${profile.id}"),
                            onClick = { onMoveProfile(profile.id, -1) },
                        ) {
                            Text(stringResource(R.string.profile_move_up_action))
                        }
                    }
                    if (canMoveDown) {
                        TextButton(
                            modifier = Modifier.testTag("settings_move_down_${profile.id}"),
                            onClick = { onMoveProfile(profile.id, 1) },
                        ) {
                            Text(stringResource(R.string.profile_move_down_action))
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (canDelete) {
                        TextButton(
                            modifier = Modifier.testTag("settings_delete_profile_${profile.id}"),
                            onClick = { showDeleteConfirm = true },
                        ) {
                            Text(
                                stringResource(R.string.delete_action),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteProfile(profile.id)
                    },
                ) {
                    Text(stringResource(R.string.delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel_action))
                }
            },
            title = { Text(stringResource(R.string.delete_profile_title)) },
            text = { Text(stringResource(R.string.delete_profile_message, displayName)) },
        )
    }
}

@Composable
private fun ProfileEditor(
    settings: AppSettingsState,
    profile: ApiProfile,
    onUpdateProfile: (ApiProfile) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ProfileEditorSection(title = stringResource(R.string.profile_section_basic)) {
            OutlinedTextField(
                value = profile.name,
                onValueChange = { onUpdateProfile(profile.copy(name = it)) },
                modifier = Modifier.fillMaxWidth().testTag("settings_profile_name_${profile.id}"),
                label = { Text(stringResource(R.string.profile_name)) },
            )
            ProviderPicker(
                settings = settings,
                profile = profile,
                onProviderSelected = { provider ->
                    onUpdateProfile(switchApiProfileProvider(profile, provider, settings))
                },
            )
            OutlinedTextField(
                value = profile.baseUrl,
                onValueChange = { onUpdateProfile(profile.copy(baseUrl = it)) },
                modifier = Modifier.fillMaxWidth().testTag("settings_profile_base_url_${profile.id}"),
                label = { Text(stringResource(R.string.relay_address)) },
            )
            OutlinedTextField(
                value = profile.apiKey,
                onValueChange = { onUpdateProfile(profile.copy(apiKey = it)) },
                modifier = Modifier.fillMaxWidth().testTag("settings_profile_api_key_${profile.id}"),
                label = { Text(stringResource(R.string.upstream_api_key)) },
            )
        }
        ProfileEditorSection(title = stringResource(R.string.profile_section_request)) {
            if (profile.provider == "openai") {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    apiModeOptions.forEachIndexed { optionIndex, option ->
                        SegmentedButton(
                            selected = profile.apiMode == option.mode,
                            onClick = {
                                val nextProfile = profileWithApiModeDefault(profile, option.mode)
                                if (nextProfile != profile) onUpdateProfile(nextProfile)
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = optionIndex, count = apiModeOptions.size),
                        ) {
                            Text(stringResource(option.labelRes))
                        }
                    }
                }
            }
            OutlinedTextField(
                value = profile.model,
                onValueChange = { onUpdateProfile(profile.copy(model = it)) },
                modifier = Modifier.fillMaxWidth().testTag("settings_profile_model_${profile.id}"),
                label = { Text(stringResource(R.string.model_label)) },
            )
            if (profile.provider == "openai") {
                ProfileToggleRow(
                    title = stringResource(R.string.profile_codex_cli_title),
                    summary = stringResource(R.string.profile_codex_cli_summary),
                    checked = profile.codexCliLikeMode,
                    testTag = "settings_profile_codex_${profile.id}",
                    onCheckedChange = { onUpdateProfile(profile.copy(codexCliLikeMode = it)) },
                )
            }
            if (isOpenAiCompatibleProvider(settings, profile.provider)) {
                ProfileToggleRow(
                    title = stringResource(R.string.profile_b64_title),
                    summary = stringResource(R.string.profile_b64_summary),
                    checked = profile.responseFormatB64Json,
                    testTag = "settings_profile_b64_${profile.id}",
                    onCheckedChange = { onUpdateProfile(profile.copy(responseFormatB64Json = it)) },
                )
                OutlinedTextField(
                    value = profile.timeoutSec.toString(),
                    onValueChange = { value ->
                        value.toIntOrNull()?.let {
                            onUpdateProfile(profile.copy(timeoutSec = it.coerceIn(10, DEFAULT_TIMEOUT_SEC)))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("settings_profile_timeout_${profile.id}"),
                    label = { Text(stringResource(R.string.profile_timeout_label)) },
                )
            }
        }
    }
}

@Composable
private fun ProviderPicker(
    settings: AppSettingsState,
    profile: ApiProfile,
    onProviderSelected: (String) -> Unit,
) {
    var expanded by rememberSaveable(profile.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.provider_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            modifier = Modifier.fillMaxWidth().testTag("settings_profile_provider_${profile.id}"),
            onClick = { expanded = true },
        ) {
            Text(providerLabel(settings, profile.provider))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            orderedProviderIds(settings).forEach { providerId ->
                DropdownMenuItem(
                    text = { Text(providerLabel(settings, providerId)) },
                    onClick = {
                        expanded = false
                        onProviderSelected(providerId)
                    },
                )
            }
        }
    }
}

@Composable
private fun ProfileEditorSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

@Composable
private fun ProfileToggleRow(
    title: String,
    summary: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(summary) },
            trailingContent = {
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
fun SettingsCustomProvidersScreen(
    state: MainUiState,
    padding: PaddingValues,
    onImportProvider: (String) -> Unit,
    onUpdateProvider: (String, String) -> Unit,
    onDeleteProvider: (String) -> Unit,
) {
    val settings = state.settings ?: return
    var providerJson by rememberSaveable { mutableStateOf("") }
    var editingProviderId by rememberSaveable { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).testTag("settings_custom_providers_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (settings.customProviders.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.settings_no_custom_providers),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            itemsIndexed(settings.customProviders, key = { _, item -> item.id }) { _, provider ->
                CustomProviderCard(
                    provider = provider,
                    onEdit = {
                        editingProviderId = provider.id
                        providerJson = provider.toJson()
                    },
                    onDelete = { onDeleteProvider(provider.id) },
                )
            }
        }
        item {
            OutlinedTextField(
                value = providerJson,
                onValueChange = { providerJson = it },
                modifier = Modifier.fillMaxWidth().testTag("settings_provider_json"),
                minLines = 8,
                label = { Text(stringResource(R.string.import_provider_json)) },
                supportingText = { Text(stringResource(R.string.settings_import_provider_description)) },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    modifier = Modifier.testTag("settings_import_provider"),
                    onClick = {
                        val editingId = editingProviderId
                        if (editingId == null) onImportProvider(providerJson) else onUpdateProvider(editingId, providerJson)
                        editingProviderId = null
                    },
                ) {
                    Text(
                        stringResource(
                            if (editingProviderId == null) R.string.import_provider else R.string.provider_save_action,
                        ),
                    )
                }
                if (editingProviderId != null) {
                    TextButton(
                        onClick = {
                            editingProviderId = null
                            providerJson = ""
                        },
                    ) {
                        Text(stringResource(R.string.cancel_action))
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomProviderCard(
    provider: CustomProviderDefinition,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().testTag("settings_custom_provider_${provider.id}"),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ListItem(
                headlineContent = { Text(provider.name) },
                supportingContent = { Text(provider.id) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    modifier = Modifier.testTag("settings_edit_provider_${provider.id}"),
                    onClick = onEdit,
                ) {
                    Text(stringResource(R.string.provider_edit_action))
                }
                TextButton(
                    modifier = Modifier.testTag("settings_delete_provider_${provider.id}"),
                    onClick = { showDeleteConfirm = true },
                ) {
                    Text(
                        stringResource(R.string.delete_action),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                ) {
                    Text(stringResource(R.string.delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel_action))
                }
            },
            title = { Text(stringResource(R.string.delete_provider_title)) },
            text = { Text(stringResource(R.string.delete_provider_message)) },
        )
    }
}

@Composable
fun SettingsDataScreen(
    state: MainUiState,
    padding: PaddingValues,
    onExportData: (Boolean, Boolean) -> Unit,
    onImportData: (Boolean, Boolean) -> Unit,
    onClearData: (Boolean, Boolean) -> Unit,
) {
    var exportConfig by rememberSaveable { mutableStateOf(true) }
    var exportTasks by rememberSaveable { mutableStateOf(true) }
    var importConfig by rememberSaveable { mutableStateOf(true) }
    var importTasks by rememberSaveable { mutableStateOf(true) }
    var clearConfig by rememberSaveable { mutableStateOf(true) }
    var clearTasks by rememberSaveable { mutableStateOf(true) }
    var confirmClear by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).testTag("settings_data_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            DataActionCard(title = stringResource(R.string.data_export_title)) {
                OptionRow(stringResource(R.string.data_include_settings), exportConfig) { exportConfig = it }
                OptionRow(stringResource(R.string.data_include_tasks_images), exportTasks) { exportTasks = it }
                Button(
                    modifier = Modifier.fillMaxWidth().testTag("settings_export_data"),
                    enabled = exportConfig || exportTasks,
                    onClick = { onExportData(exportConfig, exportTasks) },
                ) {
                    Text(stringResource(R.string.data_export_zip_action))
                }
            }
        }
        item {
            DataActionCard(title = stringResource(R.string.data_import_title)) {
                OptionRow(stringResource(R.string.data_include_settings), importConfig) { importConfig = it }
                OptionRow(stringResource(R.string.data_include_tasks_images), importTasks) { importTasks = it }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth().testTag("settings_import_data"),
                    enabled = importConfig || importTasks,
                    onClick = { onImportData(importConfig, importTasks) },
                ) {
                    Text(stringResource(R.string.data_import_zip_action))
                }
            }
        }
        item {
            DataActionCard(title = stringResource(R.string.data_clear_title)) {
                OptionRow(stringResource(R.string.data_include_settings), clearConfig) { clearConfig = it }
                OptionRow(stringResource(R.string.data_include_tasks_images), clearTasks) { clearTasks = it }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth().testTag("settings_clear_data"),
                    enabled = clearConfig || clearTasks,
                    onClick = { confirmClear = true },
                ) {
                    Text(
                        stringResource(R.string.data_clear_selected_action),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onClearData(clearConfig, clearTasks)
                    },
                ) {
                    Text(
                        stringResource(R.string.data_clear_confirm_action),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.cancel_action))
                }
            },
            title = { Text(stringResource(R.string.data_clear_selected_title)) },
            text = { Text(stringResource(R.string.data_clear_selected_message)) },
        )
    }
}

@Composable
fun SettingsAboutScreen(padding: PaddingValues) {
    val uriHandler = LocalUriHandler.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).testTag("settings_about_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.about_maintainer), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = stringResource(R.string.about_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = stringResource(R.string.about_inspiration),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item {
            Button(
                onClick = { uriHandler.openUri("https://github.com/shj56166/image2-android") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.about_github_action))
            }
        }
        item {
            OutlinedButton(
                onClick = { uriHandler.openUri("https://github.com/shj56166/image2-android/issues") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.about_feedback_action))
            }
        }
        item {
            OutlinedButton(
                onClick = { uriHandler.openUri("https://github.com/shj56166/image2-android/releases") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.about_update_action))
            }
        }
        item {
            OutlinedButton(
                onClick = { uriHandler.openUri("https://github.com/CookSleep/gpt_image_playground") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.about_web_inspiration_action))
            }
        }
    }
}

@Composable
private fun DataActionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun OptionRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(title)
    }
}

@Composable
private fun SettingsSwitchItem(
    title: String,
    summary: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().testTag(testTag)) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(summary) },
            trailingContent = {
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
private fun SettingsNavigationItem(
    title: String,
    summary: String,
    iconRes: Int,
    testTag: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        shape = RoundedCornerShape(8.dp),
    ) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(summary) },
            leadingContent = { Icon(painter = painterResource(iconRes), contentDescription = null) },
            trailingContent = {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_forward),
                    contentDescription = null,
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

private fun CustomProviderDefinition.toJson(): String =
    AppJson.encodeToString(CustomProviderDefinition.serializer(), this)
