package com.shj56166androidimage2.app.ui.screens

import com.shj56166androidimage2.app.data.model.ApiMode
import com.shj56166androidimage2.app.data.model.ApiProfile
import com.shj56166androidimage2.app.data.model.AppSettingsState
import com.shj56166androidimage2.app.data.model.CustomProviderDefinition
import com.shj56166androidimage2.app.data.model.CustomProviderSubmitMapping
import com.shj56166androidimage2.app.data.repo.createDefaultSettingsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsProfilesSupportTest {
    private val defaultProfilePrefix = "默认配置"
    private val previewRequestUrlLabel = "Request URL"
    private val previewApiKeyLabel = "API Key"
    private val previewEmptyValue = "(empty)"

    @Test
    fun `create default profile uses images defaults`() {
        val profile = createDefaultProfile(name = "Images Config", baseUrl = "https://example.com/v1", apiKey = "secret")

        assertEquals("Images Config", profile.name)
        assertEquals("https://example.com/v1", profile.baseUrl)
        assertEquals("secret", profile.apiKey)
        assertEquals(ApiMode.IMAGES, profile.apiMode)
        assertEquals(IMAGES_API_DEFAULT_MODEL, profile.model)
        assertFalse(profile.codexCliLikeMode)
        assertFalse(profile.responseFormatB64Json)
    }

    @Test
    fun `default settings state starts empty`() {
        val settings = createDefaultSettingsState()

        assertEquals("", settings.activeProfileId)
        assertTrue(settings.profiles.isEmpty())
    }

    @Test
    fun `blank profile names increment default suffix`() {
        val settings =
            AppSettingsState(
                activeProfileId = "profile-2",
                profiles =
                    listOf(
                        profile(ApiMode.IMAGES, IMAGES_API_DEFAULT_MODEL, id = "profile-1").copy(name = "${defaultProfilePrefix}1"),
                        profile(ApiMode.IMAGES, IMAGES_API_DEFAULT_MODEL, id = "profile-2").copy(name = "${defaultProfilePrefix}2"),
                        profile(ApiMode.IMAGES, IMAGES_API_DEFAULT_MODEL, id = "profile-3").copy(name = "Custom"),
                    ),
            )

        assertEquals("${defaultProfilePrefix}3", nextDefaultProfileName(settings, defaultProfilePrefix))
    }

    @Test
    fun `switching to images api applies default image model`() {
        val profile = profile(apiMode = ApiMode.RESPONSES, model = "gpt-5.5")

        val result = profileWithApiModeDefault(profile, ApiMode.IMAGES)

        assertEquals(ApiMode.IMAGES, result.apiMode)
        assertEquals(IMAGES_API_DEFAULT_MODEL, result.model)
    }

    @Test
    fun `selecting current images api keeps existing custom model`() {
        val profile = profile(apiMode = ApiMode.IMAGES, model = "custom-image-model")

        val result = profileWithApiModeDefault(profile, ApiMode.IMAGES)

        assertEquals(profile, result)
    }

    @Test
    fun `switching to responses api applies default responses model`() {
        val profile = profile(apiMode = ApiMode.IMAGES, model = IMAGES_API_DEFAULT_MODEL)

        val result = profileWithApiModeDefault(profile, ApiMode.RESPONSES)

        assertEquals(ApiMode.RESPONSES, result.apiMode)
        assertEquals(DEFAULT_RESPONSES_MODEL, result.model)
    }

    @Test
    fun `deleting active profile falls back to first remaining profile`() {
        val settings = settings(activeProfileId = "profile-2")

        val result = deleteProfileFromSettings(settings, "profile-2")

        assertEquals("profile-1", result.activeProfileId)
        assertEquals(listOf("profile-1"), result.profiles.map { it.id })
    }

    @Test
    fun `deleting last profile leaves empty settings`() {
        val settings =
            AppSettingsState(
                activeProfileId = "profile-1",
                profiles = listOf(profile(ApiMode.IMAGES, IMAGES_API_DEFAULT_MODEL, id = "profile-1")),
            )

        val result = deleteProfileFromSettings(settings, "profile-1")

        assertEquals("", result.activeProfileId)
        assertTrue(result.profiles.isEmpty())
    }

    @Test
    fun `profile preview masks key and only includes request url and api key`() {
        val settings = settings()

        val preview = profilePreviewText(
            profile = settings.profiles.first(),
            requestUrlLabel = previewRequestUrlLabel,
            apiKeyLabel = previewApiKeyLabel,
            emptyValue = previewEmptyValue,
        )

        assertTrue(preview.contains("$previewRequestUrlLabel: https://api.openai.com/v1"))
        assertTrue(preview.contains("$previewApiKeyLabel: secr...ey-1"))
        assertFalse(preview.contains("secret-key-1"))
        assertFalse(preview.contains("Provider:"))
        assertFalse(preview.contains("Mode:"))
        assertFalse(preview.contains("Model:"))
        assertFalse(preview.contains("Timeout:"))
        assertFalse(preview.contains("Codex CLI:"))
        assertFalse(preview.contains("Base64:"))
    }

    @Test
    fun `switching provider applies provider defaults`() {
        val settings = settings()
        val result = switchApiProfileProvider(settings.profiles.first(), "fal", settings)

        assertEquals("fal", result.provider)
        assertEquals(DEFAULT_FAL_BASE_URL, result.baseUrl)
        assertEquals(DEFAULT_FAL_MODEL, result.model)
        assertEquals(ApiMode.IMAGES, result.apiMode)
    }

    @Test
    fun `imported custom provider profile remains on known provider`() {
        val provider =
            CustomProviderDefinition(
                id = "provider-1",
                name = "Provider One",
                submit = CustomProviderSubmitMapping(path = "images/generations"),
            )
        val settings = settings(customProviders = listOf(provider))

        val result = normalizeImportedProfile(
            profile(apiMode = ApiMode.IMAGES, model = "", provider = "provider-1"),
            settings,
        )

        assertEquals("provider-1", result.provider)
        assertEquals(IMAGES_API_DEFAULT_MODEL, result.model)
    }

    private fun profile(
        apiMode: ApiMode,
        model: String,
        id: String = "profile-id",
        provider: String = "openai",
        apiKey: String = "key",
    ): ApiProfile =
        ApiProfile(
            id = id,
            name = "Profile",
            provider = provider,
            baseUrl = "https://api.openai.com/v1",
            apiKey = apiKey,
            model = model,
            apiMode = apiMode,
        )

    private fun settings(
        activeProfileId: String = "profile-1",
        customProviders: List<CustomProviderDefinition> = emptyList(),
    ): AppSettingsState =
        AppSettingsState(
            activeProfileId = activeProfileId,
            profiles =
                listOf(
                    profile(ApiMode.RESPONSES, "gpt-5.5", id = "profile-1", apiKey = "secret-key-1"),
                    profile(ApiMode.IMAGES, IMAGES_API_DEFAULT_MODEL, id = "profile-2", apiKey = "secret-key-2"),
                ),
            customProviders = customProviders,
        )
}
