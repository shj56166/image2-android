package com.shj56166androidimage2.app.ui.screens

import com.shj56166androidimage2.app.data.model.ApiMode
import com.shj56166androidimage2.app.data.model.ApiProfile
import com.shj56166androidimage2.app.data.model.AppSettingsState
import com.shj56166androidimage2.app.data.model.CustomProviderDefinition
import java.util.UUID

internal const val DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/v1"
internal const val DEFAULT_FAL_BASE_URL = "https://fal.run"
internal const val DEFAULT_RESPONSES_MODEL = "gpt-5.5"
internal const val DEFAULT_FAL_MODEL = "openai/gpt-image-2"
internal const val IMAGES_API_DEFAULT_MODEL = "gpt-image-2"
internal const val DEFAULT_TIMEOUT_SEC = 1_800

internal fun profileWithApiModeDefault(
    profile: ApiProfile,
    apiMode: ApiMode,
): ApiProfile {
    if (profile.apiMode == apiMode) return profile
    return profile.copy(
        apiMode = apiMode,
        model = if (apiMode == ApiMode.IMAGES) IMAGES_API_DEFAULT_MODEL else DEFAULT_RESPONSES_MODEL,
    )
}

internal fun providerLabel(settings: AppSettingsState, provider: String): String =
    when (provider) {
        "openai" -> "OpenAI"
        "fal" -> "fal.ai"
        else -> settings.customProviders.firstOrNull { it.id == provider }?.name ?: provider
    }

internal fun isOpenAiCompatibleProvider(settings: AppSettingsState, provider: String): Boolean =
    provider == "openai" || settings.customProviders.any { it.id == provider }

internal fun isKnownProvider(settings: AppSettingsState, provider: String): Boolean =
    provider == "openai" || provider == "fal" || settings.customProviders.any { it.id == provider }

internal fun switchApiProfileProvider(
    profile: ApiProfile,
    provider: String,
    settings: AppSettingsState,
): ApiProfile =
    when (provider) {
        "fal" -> profile.copy(
            provider = "fal",
            baseUrl = profile.baseUrl.takeIf { it.isNotBlank() && profile.provider == "fal" } ?: DEFAULT_FAL_BASE_URL,
            model = profile.model.takeIf { it.isNotBlank() && profile.provider == "fal" } ?: DEFAULT_FAL_MODEL,
            apiMode = ApiMode.IMAGES,
            codexCliLikeMode = false,
            responseFormatB64Json = false,
        )
        "openai" -> profile.copy(
            provider = "openai",
            baseUrl = if (profile.provider == "fal") DEFAULT_OPENAI_BASE_URL else profile.baseUrl.ifBlank { DEFAULT_OPENAI_BASE_URL },
            model = if (profile.provider == "fal") IMAGES_API_DEFAULT_MODEL else profile.model.ifBlank { IMAGES_API_DEFAULT_MODEL },
        )
        else -> profile.copy(
            provider = if (settings.customProviders.any { it.id == provider }) provider else "openai",
            baseUrl = if (profile.provider == "fal") DEFAULT_OPENAI_BASE_URL else profile.baseUrl,
            model = if (profile.provider == "fal") IMAGES_API_DEFAULT_MODEL else profile.model.ifBlank { IMAGES_API_DEFAULT_MODEL },
            apiMode = ApiMode.IMAGES,
            codexCliLikeMode = false,
        )
    }

internal fun createDefaultProfile(
    name: String,
    baseUrl: String = DEFAULT_OPENAI_BASE_URL,
    apiKey: String = "",
    apiMode: ApiMode = ApiMode.IMAGES,
    codexCliLikeMode: Boolean = false,
    responseFormatB64Json: Boolean = false,
    id: String = UUID.randomUUID().toString(),
): ApiProfile =
    ApiProfile(
        id = id,
        name = name,
        provider = "openai",
        baseUrl = baseUrl,
        apiKey = apiKey,
        model = if (apiMode == ApiMode.RESPONSES) DEFAULT_RESPONSES_MODEL else IMAGES_API_DEFAULT_MODEL,
        apiMode = apiMode,
        timeoutSec = DEFAULT_TIMEOUT_SEC,
        codexCliLikeMode = codexCliLikeMode,
        responseFormatB64Json = responseFormatB64Json,
    )

internal fun copyApiProfile(
    profile: ApiProfile,
    defaultName: String,
    copySuffix: String,
): ApiProfile {
    val baseName = profile.name.ifBlank { defaultName }
    return profile.copy(
        id = UUID.randomUUID().toString(),
        name = "$baseName $copySuffix".trim(),
    )
}

internal fun deleteProfileFromSettings(settings: AppSettingsState, profileId: String): AppSettingsState {
    val nextProfiles = settings.profiles.filterNot { it.id == profileId }
    val nextActiveId =
        when {
            nextProfiles.isEmpty() -> ""
            settings.activeProfileId == profileId -> nextProfiles.first().id
            else -> settings.activeProfileId
        }
    return settings.copy(profiles = nextProfiles, activeProfileId = nextActiveId)
}

internal fun removeCustomProviderFromSettings(settings: AppSettingsState, providerId: String): AppSettingsState =
    settings.copy(
        customProviders = settings.customProviders.filterNot { it.id == providerId },
        providerOrder = settings.providerOrder.filterNot { it == providerId },
        profiles = settings.profiles.map { profile ->
            if (profile.provider == providerId) switchApiProfileProvider(profile, "openai", settings) else profile
        },
    )

internal fun maskApiKey(
    value: String,
    emptyValue: String,
): String =
    when {
        value.isBlank() -> emptyValue
        value.length <= 8 -> "****"
        else -> "${value.take(4)}...${value.takeLast(4)}"
    }

internal fun profilePreviewText(
    profile: ApiProfile,
    requestUrlLabel: String,
    apiKeyLabel: String,
    emptyValue: String,
): String =
    listOf(
        "$requestUrlLabel: ${profile.baseUrl.ifBlank { emptyValue }}",
        "$apiKeyLabel: ${maskApiKey(profile.apiKey, emptyValue)}",
    ).joinToString("\n")

internal fun ApiMode.serializedName(): String =
    when (this) {
        ApiMode.IMAGES -> "images"
        ApiMode.RESPONSES -> "responses"
    }

internal fun orderedProviderIds(settings: AppSettingsState): List<String> {
    val defaultOrder = listOf("openai", "fal") + settings.customProviders.map { it.id }
    return (settings.providerOrder + defaultOrder).distinct().filter { isKnownProvider(settings, it) }
}

internal fun normalizeImportedProfile(profile: ApiProfile, settings: AppSettingsState): ApiProfile =
    profile.copy(
        id = profile.id.ifBlank { UUID.randomUUID().toString() },
        provider = profile.provider.takeIf { isKnownProvider(settings, it) } ?: "openai",
        baseUrl = profile.baseUrl.ifBlank {
            if (profile.provider == "fal") DEFAULT_FAL_BASE_URL else DEFAULT_OPENAI_BASE_URL
        },
        model = profile.model.ifBlank {
            if (profile.apiMode == ApiMode.RESPONSES) DEFAULT_RESPONSES_MODEL else IMAGES_API_DEFAULT_MODEL
        },
        timeoutSec = profile.timeoutSec.coerceIn(10, DEFAULT_TIMEOUT_SEC),
    )

internal fun nextDefaultProfileName(
    settings: AppSettingsState,
    prefix: String,
): String {
    val nextIndex =
        settings.profiles.mapNotNull { profile ->
            profile.name
                .takeIf { it.startsWith(prefix) }
                ?.removePrefix(prefix)
                ?.toIntOrNull()
        }.maxOrNull()?.plus(1) ?: 1
    return "$prefix$nextIndex"
}
