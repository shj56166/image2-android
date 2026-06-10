package com.shj56166androidimage2.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ApiMode {
    @SerialName("images")
    IMAGES,

    @SerialName("responses")
    RESPONSES,
}

@Serializable
enum class TaskStatus {
    @SerialName("queued")
    QUEUED,

    @SerialName("running")
    RUNNING,

    @SerialName("done")
    DONE,

    @SerialName("error")
    ERROR,
}

@Serializable
enum class ImageSource {
    @SerialName("upload")
    UPLOAD,

    @SerialName("generated")
    GENERATED,

    @SerialName("mask")
    MASK,
}

@Serializable
enum class AppLanguage {
    @SerialName("system")
    SYSTEM,

    @SerialName("en")
    ENGLISH,

    @SerialName("zh-Hans")
    SIMPLIFIED_CHINESE,

    @SerialName("zh-Hant")
    TRADITIONAL_CHINESE,
}

@Serializable
data class TaskParams(
    val size: String = "auto",
    val quality: String = "auto",
    val outputFormat: String = "png",
    val outputCompression: Int? = null,
    val moderation: String = "auto",
    val count: Int = 2,
)

@Serializable
data class ApiProfile(
    val id: String,
    val name: String,
    val provider: String = "openai",
    val baseUrl: String,
    val apiKey: String = "",
    val model: String,
    val apiMode: ApiMode = ApiMode.RESPONSES,
    val timeoutSec: Int = 1_800,
    val extraHeaders: Map<String, String> = emptyMap(),
    val codexCliLikeMode: Boolean = false,
    val responseFormatB64Json: Boolean = false,
)

@Serializable
data class StoredImageAsset(
    val id: String,
    val filePath: String,
    val thumbnailPath: String? = null,
    val mimeType: String,
    val source: ImageSource,
    val width: Int? = null,
    val height: Int? = null,
    val createdAt: Long,
    val sha256: String,
)

@Serializable
data class ImageTask(
    val id: String,
    val prompt: String,
    val params: TaskParams,
    val sessionId: String? = null,
    val apiProvider: String = "openai",
    val apiProfileId: String? = null,
    val apiProfileName: String? = null,
    val apiModel: String? = null,
    val remoteTaskId: String? = null,
    val inputImageIds: List<String> = emptyList(),
    val maskTargetImageId: String? = null,
    val maskImageId: String? = null,
    val outputImageIds: List<String> = emptyList(),
    val rawImageUrls: List<String> = emptyList(),
    val rawResponsePayload: String? = null,
    val actualParamsByImage: Map<String, TaskParams> = emptyMap(),
    val revisedPromptByImage: Map<String, String> = emptyMap(),
    val status: TaskStatus = TaskStatus.QUEUED,
    val error: String? = null,
    val createdAt: Long,
    val finishedAt: Long? = null,
    val elapsedMillis: Long? = null,
    val isFavorite: Boolean = false,
)

@Serializable
data class SessionTurn(
    val timestamp: Long,
    val command: String,
    val prompt: String,
    val revisedPrompts: List<String> = emptyList(),
    val responseIds: List<String> = emptyList(),
    val outputImageIds: List<String> = emptyList(),
    val inputImageIds: List<String> = emptyList(),
    val maskImageId: String? = null,
    val model: String? = null,
    val imageModel: String? = null,
)

@Serializable
data class ImageSession(
    val id: String,
    val name: String,
    val updatedAt: Long,
)

@Serializable
data class CustomProviderFileMapping(
    val field: String,
    val source: String,
    val array: Boolean = false,
)

@Serializable
data class CustomProviderResultMapping(
    val imageUrlPaths: List<String> = emptyList(),
    val b64JsonPaths: List<String> = emptyList(),
)

@Serializable
data class CustomProviderSubmitMapping(
    val path: String,
    val method: String = "POST",
    val contentType: String = "json",
    val query: Map<String, String> = emptyMap(),
    val body: Map<String, String> = emptyMap(),
    val files: List<CustomProviderFileMapping> = emptyList(),
    val taskIdPath: String? = null,
    val result: CustomProviderResultMapping? = null,
)

@Serializable
data class CustomProviderPollMapping(
    val path: String,
    val method: String = "GET",
    val query: Map<String, String> = emptyMap(),
    val intervalSeconds: Int = 5,
    val statusPath: String,
    val successValues: List<String>,
    val failureValues: List<String>,
    val errorPath: String? = null,
    val result: CustomProviderResultMapping,
)

@Serializable
data class CustomProviderDefinition(
    val id: String,
    val name: String,
    val submit: CustomProviderSubmitMapping,
    val editSubmit: CustomProviderSubmitMapping? = null,
    val poll: CustomProviderPollMapping? = null,
)

@Serializable
data class AppSettingsState(
    val activeProfileId: String,
    val profiles: List<ApiProfile>,
    val customProviders: List<CustomProviderDefinition> = emptyList(),
    val providerOrder: List<String> = emptyList(),
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val clearInputAfterSubmit: Boolean = false,
    val persistInputOnRestart: Boolean = true,
    val reuseTaskApiProfileTemporarily: Boolean = true,
    val alwaysShowRetryButton: Boolean = true,
    val enterSubmit: Boolean = false,
)

@Serializable
data class MaskDraft(
    val targetImageId: String,
    val maskImageId: String,
    val updatedAt: Long,
)
