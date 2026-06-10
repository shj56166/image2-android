package com.shj56166androidimage2.app.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ResponsesRequest(
    val model: String,
    val input: JsonElement,
    val tools: List<ImageGenerationTool>,
    @SerialName("tool_choice") val toolChoice: JsonElement,
    val user: String? = null,
)

@Serializable
data class ImageGenerationTool(
    val type: String = "image_generation",
    val model: String? = null,
    val action: String? = null,
    val size: String? = null,
    val quality: String? = null,
    @SerialName("output_format") val outputFormat: String? = null,
    @SerialName("output_compression") val outputCompression: Int? = null,
    val moderation: String? = null,
    @SerialName("partial_images") val partialImages: Int? = null,
    @SerialName("input_image_mask") val inputImageMask: InputImageMask? = null,
)

@Serializable
data class InputImageMask(
    @SerialName("image_url") val imageUrl: String,
)

@Serializable
data class ResponsesApiResponse(
    val id: String? = null,
    val output: List<ResponsesOutputItem> = emptyList(),
)

@Serializable
data class ResponsesOutputItem(
    val type: String? = null,
    val result: JsonElement? = null,
    @SerialName("revised_prompt") val revisedPrompt: String? = null,
    val size: String? = null,
    val quality: String? = null,
    @SerialName("output_format") val outputFormat: String? = null,
    @SerialName("output_compression") val outputCompression: Int? = null,
    val moderation: String? = null,
)

@Serializable
data class ImagesApiResponse(
    val created: Long? = null,
    val data: List<ImageDataItem> = emptyList(),
)

@Serializable
data class ImageDataItem(
    @SerialName("b64_json") val b64Json: String? = null,
    val url: String? = null,
    @SerialName("revised_prompt") val revisedPrompt: String? = null,
    val size: String? = null,
    val quality: String? = null,
    @SerialName("output_format") val outputFormat: String? = null,
    @SerialName("output_compression") val outputCompression: Int? = null,
    val moderation: String? = null,
)

@Serializable
data class ErrorEnvelope(
    val error: ErrorPayload? = null,
    val message: String? = null,
)

@Serializable
data class ErrorPayload(
    val message: String? = null,
    val code: String? = null,
)
