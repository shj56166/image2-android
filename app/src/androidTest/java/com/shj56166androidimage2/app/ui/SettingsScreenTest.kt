package com.shj56166androidimage2.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shj56166androidimage2.app.data.model.ApiMode
import com.shj56166androidimage2.app.data.model.ApiProfile
import com.shj56166androidimage2.app.data.model.AppSettingsState
import com.shj56166androidimage2.app.data.model.CustomProviderDefinition
import com.shj56166androidimage2.app.data.model.CustomProviderResultMapping
import com.shj56166androidimage2.app.data.model.CustomProviderSubmitMapping
import com.shj56166androidimage2.app.ui.screens.CreateProfileDraft
import com.shj56166androidimage2.app.ui.screens.CreateProfileTestState
import com.shj56166androidimage2.app.ui.screens.CreateProfileTestStatus
import com.shj56166androidimage2.app.ui.screens.ProfileTestCaseResult
import com.shj56166androidimage2.app.ui.screens.ProfileTestCombination
import com.shj56166androidimage2.app.ui.screens.SettingsCustomProvidersScreen
import com.shj56166androidimage2.app.ui.screens.SettingsProfilesScreen
import com.shj56166androidimage2.app.ui.screens.SettingsScreen
import com.shj56166androidimage2.app.ui.screens.createDefaultProfile
import com.shj56166androidimage2.app.ui.screens.nextDefaultProfileName
import com.shj56166androidimage2.app.ui.theme.ImagePlaygroundTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsHomeShowsSecondLevelEntries() {
        var selectedSubpage: SettingsSubpage? = null
        composeRule.setSettingsHomeContent(buildSettingsState()) {
            selectedSubpage = it
        }

        composeRule.onNodeWithTag("settings_home").assertExists()
        composeRule.onNodeWithTag("settings_general_entry").assertExists()
        composeRule.onNodeWithTag("settings_api_profiles_entry").assertExists()
        composeRule.onNodeWithTag("settings_custom_providers_entry").assertExists()
        composeRule.onNodeWithTag("settings_data_entry").assertExists()
        composeRule.onNodeWithTag("settings_about_entry").assertExists()

        composeRule.onNodeWithTag("settings_api_profiles_entry").performClick()

        composeRule.runOnIdle {
            assertEquals(SettingsSubpage.PROFILES, selectedSubpage)
        }
    }

    @Test
    fun profilesScreenEditsProfilesAndActiveSelection() {
        var state by mutableStateOf(buildSettingsState())
        composeRule.setProfilesContent(
            stateProvider = { state },
            onStateChange = { state = it },
        )

        composeRule.onNodeWithTag("settings_profiles_screen").assertExists()
        composeRule.onNodeWithTag("settings_profile_preview_profile-1").assertExists()
        composeRule.onNodeWithTag("settings_profile_name_profile-1").performTextReplacement("Edited")
        composeRule.onNodeWithTag("settings_set_active_profile-2").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_duplicate_profile-1").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_delete_profile_profile-2").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_delete_profile_confirm").performClick()
        composeRule.onNodeWithTag("settings_new_profile").performClick()
        composeRule.onNodeWithTag("settings_new_profile_name").assertExists().performTextInput("Images Config")
        composeRule.onNodeWithTag("settings_new_profile_base_url").performTextInput("https://images.example.com/v1")
        composeRule.onNodeWithTag("settings_new_profile_api_key").performTextInput("secret-key-3")
        composeRule.onNodeWithTag("settings_create_profile_confirm").performClick()

        composeRule.runOnIdle {
            val settings = state.settings ?: error("Missing settings")
            assertEquals("Edited", settings.profiles.first { it.id == "profile-1" }.name)
            assertEquals(3, settings.profiles.size)
            val created = settings.profiles.first { it.id == "profile-3" }
            assertEquals("Images Config", created.name)
            assertEquals("https://images.example.com/v1", created.baseUrl)
            assertEquals("secret-key-3", created.apiKey)
            assertEquals(ApiMode.IMAGES, created.apiMode)
            assertEquals("gpt-image-2", created.model)
            assertEquals("profile-3", settings.activeProfileId)
        }
    }

    @Test
    fun newProfileOpensDialogBeforeCreatingCard() {
        var state by mutableStateOf(buildEmptySettingsState())
        composeRule.setProfilesContent(
            stateProvider = { state },
            onStateChange = { state = it },
        )

        composeRule.onNodeWithTag("settings_new_profile").performClick()

        composeRule.onNodeWithTag("settings_new_profile_name").assertExists()
        composeRule.onNodeWithTag("settings_new_profile_base_url").assertExists()
        composeRule.onNodeWithTag("settings_new_profile_api_key").assertExists()

        composeRule.runOnIdle {
            val settings = state.settings ?: error("Missing settings")
            assertEquals(0, settings.profiles.size)
        }
    }

    @Test
    fun blankProfileNameFallsBackToNextDefaultName() {
        var state by mutableStateOf(buildEmptySettingsState())
        composeRule.setProfilesContent(
            stateProvider = { state },
            onStateChange = { state = it },
        )

        composeRule.onNodeWithTag("settings_new_profile").performClick()
        composeRule.onNodeWithTag("settings_new_profile_base_url").performTextInput("https://images.example.com/v1")
        composeRule.onNodeWithTag("settings_new_profile_api_key").performTextInput("secret-key-3")
        composeRule.onNodeWithTag("settings_create_profile_confirm").performClick()

        composeRule.onNodeWithTag("settings_new_profile").performClick()
        composeRule.onNodeWithTag("settings_new_profile_base_url").performTextInput("https://images2.example.com/v1")
        composeRule.onNodeWithTag("settings_new_profile_api_key").performTextInput("secret-key-4")
        composeRule.onNodeWithTag("settings_create_profile_confirm").performClick()

        composeRule.runOnIdle {
            val settings = state.settings ?: error("Missing settings")
            assertEquals("默认配置1", settings.profiles[0].name)
            assertEquals("默认配置2", settings.profiles[1].name)
        }
    }

    @Test
    fun newProfileAvailabilityTestShowsProgressAndAppliesRecommendedConfig() {
        var state by mutableStateOf(buildEmptySettingsState())
        composeRule.setProfilesContent(
            stateProvider = { state },
            onStateChange = { state = it },
            onTestDraft = { draft ->
                state =
                    state.copy(
                        createProfileTestState =
                            CreateProfileTestState(
                                status = CreateProfileTestStatus.RUNNING,
                                testedCount = 1,
                                totalCount = 4,
                            ),
                    )
                state =
                    state.copy(
                        createProfileTestState =
                            CreateProfileTestState(
                                status = CreateProfileTestStatus.FINISHED,
                                testedCount = 4,
                                totalCount = 4,
                                results =
                                    listOf(
                                        ProfileTestCaseResult(
                                            combination = ProfileTestCombination(ApiMode.IMAGES, false, false),
                                            success = true,
                                            message = "Images API / CLI Off / Base64 Off succeeded.",
                                        ),
                                    ),
                                recommendedCombination = ProfileTestCombination(ApiMode.IMAGES, false, false),
                            ),
                    )
            },
        )

        composeRule.onNodeWithTag("settings_new_profile").performClick()
        composeRule.onNodeWithTag("settings_new_profile_base_url").performTextInput("https://images.example.com/v1")
        composeRule.onNodeWithTag("settings_new_profile_api_key").performTextInput("secret-key-3")
        composeRule.onNodeWithTag("settings_create_profile_test").performClick()
        composeRule.onNodeWithTag("settings_create_profile_test_confirm").performClick()
        composeRule.onNodeWithTag("settings_create_profile_test_progress").assertExists()
        composeRule.onNodeWithTag("settings_create_profile_test_progress").assertExists()
        composeRule.onNodeWithTag("settings_create_profile_confirm").performClick()

        composeRule.runOnIdle {
            val created = state.settings?.profiles?.first() ?: error("Missing created profile")
            assertEquals(ApiMode.IMAGES, created.apiMode)
            assertEquals(false, created.codexCliLikeMode)
            assertEquals(false, created.responseFormatB64Json)
        }
    }

    @Test
    fun customProvidersScreenImportsJson() {
        var imported = ""
        composeRule.setCustomProvidersContent(buildSettingsState(customProviders = listOf(buildCustomProvider()))) {
            imported = it
        }

        composeRule.onNodeWithTag("settings_custom_providers_screen").assertExists()
        composeRule.onNodeWithTag("settings_custom_provider_provider-1").assertExists()
        composeRule.onNodeWithTag("settings_provider_json").performTextReplacement("""{"id":"next"}""")
        composeRule.onNodeWithTag("settings_import_provider").performClick()

        composeRule.runOnIdle {
            assertEquals("""{"id":"next"}""", imported)
        }
    }
}

private fun ComposeContentTestRule.setSettingsHomeContent(
    state: MainUiState,
    onOpenSubpage: (SettingsSubpage) -> Unit,
) {
    setContent {
        ImagePlaygroundTheme {
            SettingsScreen(
                state = state,
                padding = PaddingValues(0.dp),
                onOpenSubpage = onOpenSubpage,
            )
        }
    }
}

private fun ComposeContentTestRule.setProfilesContent(
    stateProvider: () -> MainUiState,
    onStateChange: (MainUiState) -> Unit,
    onTestDraft: (CreateProfileDraft) -> Unit = {},
) {
    setContent {
        val state = stateProvider()
        ImagePlaygroundTheme {
            SettingsProfilesScreen(
                state = state,
                padding = PaddingValues(0.dp),
                onUpdateProfile = { profile ->
                    val current = stateProvider().settings
                    if (current != null) {
                        onStateChange(
                            stateProvider().copy(
                                settings =
                                    current.copy(
                                        profiles = current.profiles.map { if (it.id == profile.id) profile else it },
                                    ),
                            ),
                        )
                    }
                },
                onSetActiveProfile = { profileId ->
                    stateProvider().settings?.let { current ->
                        onStateChange(stateProvider().copy(settings = current.copy(activeProfileId = profileId)))
                    }
                },
                onDuplicateProfile = { profileId ->
                    stateProvider().settings?.let { current ->
                        val source = current.profiles.first { it.id == profileId }
                        onStateChange(
                            stateProvider().copy(
                                settings = current.copy(
                                    profiles = current.profiles + source.copy(id = "profile-copy", name = "${source.name} (Copy)"),
                                    activeProfileId = "profile-copy",
                                ),
                            ),
                        )
                    }
                },
                onDeleteProfile = { profileId ->
                    stateProvider().settings?.let { current ->
                        val nextProfiles = current.profiles.filterNot { it.id == profileId }
                        onStateChange(
                            stateProvider().copy(
                                settings = current.copy(
                                    profiles = nextProfiles,
                                    activeProfileId = if (current.activeProfileId == profileId) nextProfiles.first().id else current.activeProfileId,
                                ),
                            ),
                        )
                    }
                },
                onMoveProfile = { _, _ -> },
                onCreateProfile = { draft ->
                    val current = stateProvider().settings
                    if (current != null) {
                        val nextId = "profile-${current.profiles.size + 1}"
                        val resolvedName =
                            draft.name.ifBlank {
                                nextDefaultProfileName(current, "默认配置")
                            }
                        val created =
                            createDefaultProfile(
                                name = resolvedName,
                                baseUrl = draft.baseUrl,
                                apiKey = draft.apiKey,
                                apiMode = draft.apiMode,
                                codexCliLikeMode = draft.codexCliLikeMode,
                                responseFormatB64Json = draft.responseFormatB64Json,
                                id = nextId,
                            )
                        onStateChange(
                            stateProvider().copy(
                                settings =
                                    current.copy(
                                        profiles = current.profiles + created,
                                        activeProfileId = nextId,
                                    ),
                                createProfileTestState = CreateProfileTestState(),
                            ),
                        )
                    }
                },
                onTestProfileConnection = {},
                onTestCreateProfileDraft = onTestDraft,
                onResetCreateProfileTest = {
                    onStateChange(stateProvider().copy(createProfileTestState = CreateProfileTestState()))
                },
            )
        }
    }
}

private fun ComposeContentTestRule.setCustomProvidersContent(
    state: MainUiState,
    onImportProvider: (String) -> Unit,
) {
    setContent {
        ImagePlaygroundTheme {
            SettingsCustomProvidersScreen(
                state = state,
                padding = PaddingValues(0.dp),
                onImportProvider = onImportProvider,
                onUpdateProvider = { _, _ -> },
                onDeleteProvider = {},
            )
        }
    }
}

private fun buildSettingsState(
    customProviders: List<CustomProviderDefinition> = emptyList(),
): MainUiState =
    MainUiState(
        settings =
            AppSettingsState(
                activeProfileId = "profile-1",
                profiles =
                    listOf(
                        ApiProfile(
                            id = "profile-1",
                            name = "Primary",
                            provider = "openai",
                            baseUrl = "https://api.openai.com/v1",
                            apiKey = "key-1",
                            model = "gpt-5.5",
                            apiMode = ApiMode.RESPONSES,
                        ),
                        ApiProfile(
                            id = "profile-2",
                            name = "Backup",
                            provider = "openai",
                            baseUrl = "https://api.openai.com/v1",
                            apiKey = "key-2",
                            model = "gpt-5.5",
                            apiMode = ApiMode.IMAGES,
                        ),
                    ),
                customProviders = customProviders,
            ),
    )

private fun buildEmptySettingsState(): MainUiState =
    MainUiState(
        settings =
            AppSettingsState(
                activeProfileId = "",
                profiles = emptyList(),
            ),
    )

private fun buildCustomProvider(): CustomProviderDefinition =
    CustomProviderDefinition(
        id = "provider-1",
        name = "Provider One",
        submit =
            CustomProviderSubmitMapping(
                path = "/v1/images",
                method = "POST",
                body = emptyMap(),
                result = CustomProviderResultMapping(imageUrlPaths = listOf("$.images")),
            ),
    )
