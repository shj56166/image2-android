package com.shj56166androidimage2.app.domain.engine

import com.shj56166androidimage2.app.data.model.ApiProfile
import com.shj56166androidimage2.app.data.model.CustomProviderDefinition
import com.shj56166androidimage2.app.data.model.SessionTurn
import com.shj56166androidimage2.app.data.model.TaskParams

data class ImageRequest(
    val profile: ApiProfile,
    val customProvider: CustomProviderDefinition? = null,
    val prompt: String,
    val params: TaskParams,
    val inputImages: List<String> = emptyList(),
    val maskImage: String? = null,
    val sessionTurns: List<SessionTurn> = emptyList(),
    val includeSessionReferenceImages: Boolean = true,
    val maxAttempts: Int = 3,
    val outerModelFallbacks: List<String> = listOf("gpt-5.4"),
    val savePartials: Boolean = false,
    val onRemoteTaskAccepted: suspend (String) -> Unit = {},
    val onPartialResult: suspend (ImageResult) -> Unit = {},
)

data class ImageResult(
    val images: List<String>,
    val actualParamsList: List<TaskParams?> = emptyList(),
    val revisedPrompts: List<String?> = emptyList(),
    val responseIds: List<String> = emptyList(),
    val rawImageUrls: List<String> = emptyList(),
    val rawResponsePayload: String? = null,
    val remoteTaskId: String? = null,
    val partialImages: List<String> = emptyList(),
    val partialErrors: List<String> = emptyList(),
    val usedModel: String? = null,
)

interface ImageExecutionEngine {
    suspend fun generate(request: ImageRequest): ImageResult
    suspend fun edit(request: ImageRequest): ImageResult
    suspend fun generateBatch(requests: List<ImageRequest>): List<Result<ImageResult>>
    suspend fun resumeTask(
        profile: ApiProfile,
        customProvider: CustomProviderDefinition?,
        remoteTaskId: String,
        params: TaskParams,
    ): ImageResult
}
