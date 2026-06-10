package com.shj56166androidimage2.app.data.network

import android.content.Context
import android.util.Base64
import android.util.Log
import com.shj56166androidimage2.app.data.model.ApiMode
import com.shj56166androidimage2.app.data.model.ApiProfile
import com.shj56166androidimage2.app.data.model.CustomProviderDefinition
import com.shj56166androidimage2.app.data.model.CustomProviderPollMapping
import com.shj56166androidimage2.app.data.model.CustomProviderResultMapping
import com.shj56166androidimage2.app.data.model.CustomProviderSubmitMapping
import com.shj56166androidimage2.app.data.model.SessionTurn
import com.shj56166androidimage2.app.data.model.TaskParams
import com.shj56166androidimage2.app.data.repo.AppJson
import com.shj56166androidimage2.app.domain.engine.ImageExecutionEngine
import com.shj56166androidimage2.app.domain.engine.ImageRequest
import com.shj56166androidimage2.app.domain.engine.ImageResult
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

private const val NETWORK_TAG = "DirectImageEngine"
private const val MAX_RAW_RESPONSE_PAYLOAD_LENGTH = 4_096
private const val MIN_IMAGE_REQUEST_TIMEOUT_SEC = 1_800

class DirectUpstreamExecutionEngine(
    private val client: OkHttpClient,
    private val appContext: Context,
) : ImageExecutionEngine {
    override suspend fun generate(request: ImageRequest): ImageResult = perform(request, isEdit = false)

    override suspend fun edit(request: ImageRequest): ImageResult = perform(request, isEdit = true)

    override suspend fun generateBatch(requests: List<ImageRequest>): List<Result<ImageResult>> = coroutineScope {
        requests.map { req ->
            async {
                runCatching { perform(req, isEdit = req.inputImages.isNotEmpty() || req.maskImage != null) }
            }
        }.awaitAll()
    }

    override suspend fun resumeTask(
        profile: ApiProfile,
        customProvider: CustomProviderDefinition?,
        remoteTaskId: String,
        params: TaskParams,
    ): ImageResult {
        requireNotNull(customProvider?.poll) { formatImageGenerationError("Task resume is only supported for custom async providers.") }
        return pollCustomTask(profile, customProvider.poll, remoteTaskId, params)
    }

    private suspend fun perform(request: ImageRequest, isEdit: Boolean): ImageResult {
        return when {
            request.customProvider != null -> performCustom(request, isEdit)
            request.profile.provider == "fal" -> performFal(request, isEdit)
            request.profile.apiMode == ApiMode.RESPONSES -> performResponses(request, isEdit)
            else -> performImages(request, isEdit)
        }
    }

    private suspend fun performResponses(request: ImageRequest, isEdit: Boolean): ImageResult {
        val count = request.params.count.coerceAtLeast(1)
        if (count == 1) {
            return performResponsesSingle(request, isEdit)
        }

        val singleRequest = request.copy(params = request.params.copy(count = 1))
        val results = coroutineScope {
            List(count) {
                async {
                    runCatching {
                        performResponsesSingle(singleRequest, isEdit).also { request.onPartialResult(it) }
                    }
                }
            }.awaitAll()
        }
        val successfulResults = results.mapNotNull { it.getOrNull() }
        val partialErrors = summarizeFanOutFailures(results)
        if (successfulResults.isEmpty()) {
            throw results.firstNotNullOfOrNull { it.exceptionOrNull() }
                ?: IllegalStateException(formatImageGenerationError("All concurrent response requests failed."))
        }
        return ImageResult(
            images = successfulResults.flatMap { it.images },
            actualParamsList = successfulResults.flatMap { result ->
                result.actualParamsList.ifEmpty { result.images.map { singleRequest.params.copy(count = 1) } }
            },
            revisedPrompts = successfulResults.flatMap { result ->
                result.revisedPrompts.ifEmpty { result.images.map { null } }
            },
            responseIds = successfulResults.flatMap { it.responseIds },
            rawImageUrls = successfulResults.flatMap { it.rawImageUrls },
            rawResponsePayload =
                successfulResults
                    .mapNotNull { it.rawResponsePayload }
                    .takeIf { it.isNotEmpty() }
                    ?.let { AppJson.encodeToString(ListSerializer(String.serializer()), it) },
            partialErrors = partialErrors,
        )
    }

    private suspend fun performResponsesSingle(request: ImageRequest, isEdit: Boolean): ImageResult {
        val input = buildResponsesInput(
            prompt = augmentPrompt(request.prompt, request.sessionTurns, request.includeSessionReferenceImages),
            inputImages = request.inputImages,
            maskImage = request.maskImage,
        )
        val tool = ImageGenerationTool(
            model = if (request.profile.model.startsWith("gpt-5")) "gpt-image-2" else null,
            action = if (isEdit) "edit" else "generate",
            size = request.params.size,
            quality = request.params.quality.takeUnless { request.profile.codexCliLikeMode },
            outputFormat = request.params.outputFormat,
            outputCompression = request.params.outputCompression.takeIf { request.params.outputFormat != "png" },
            moderation = request.params.moderation.takeUnless { request.profile.apiMode == ApiMode.RESPONSES && request.params.moderation == "auto" },
            partialImages = if (request.savePartials) 3 else null,
            inputImageMask = request.maskImage?.let { InputImageMask(it) },
        )
        val payload = ResponsesRequest(
            model = request.profile.model,
            input = input,
            tools = listOf(tool),
            toolChoice = JsonObject(mapOf("type" to JsonPrimitive("image_generation"))),
        )

        return executeWithFallback(request.profile, request.outerModelFallbacks, request.maxAttempts) { profile ->
            val json = AppJson.encodeToString(ResponsesRequest.serializer(), payload.copy(model = profile.model))
            val responseText = executeJsonRequest(profile, "responses", json)
            val parsed = AppJson.decodeFromString(ResponsesApiResponse.serializer(), responseText)
            parseResponsesResult(parsed, responseText, request.params)
        }
    }

    private suspend fun performImages(request: ImageRequest, isEdit: Boolean): ImageResult {
        val count = request.params.count.coerceAtLeast(1)
        if (shouldFanOutImageRequests(request.profile, count)) {
            val singleRequest = request.copy(params = request.params.copy(count = 1))
            val results = coroutineScope {
                List(count) {
                    async {
                        runCatching {
                            performImagesSingle(singleRequest, isEdit).also { request.onPartialResult(it) }
                        }
                    }
                }.awaitAll()
            }
            val successfulResults = results.mapNotNull { it.getOrNull() }
            val partialErrors = summarizeFanOutFailures(results)
            if (successfulResults.isEmpty()) {
                throw results.firstNotNullOfOrNull { it.exceptionOrNull() }
                    ?: IllegalStateException(formatImageGenerationError("All concurrent image requests failed."))
            }
            return ImageResult(
                images = successfulResults.flatMap { it.images },
                actualParamsList = successfulResults.flatMap { result ->
                    result.actualParamsList.ifEmpty { result.images.map { singleRequest.params.copy(count = 1) } }
                },
                revisedPrompts = successfulResults.flatMap { result ->
                    result.revisedPrompts.ifEmpty { result.images.map { null } }
                },
                responseIds = successfulResults.flatMap { it.responseIds },
                rawImageUrls = successfulResults.flatMap { it.rawImageUrls },
                rawResponsePayload =
                    successfulResults
                        .mapNotNull { it.rawResponsePayload }
                        .takeIf { it.isNotEmpty() }
                        ?.let { AppJson.encodeToString(ListSerializer(String.serializer()), it) },
                partialErrors = partialErrors,
            )
        }
        return performImagesSingle(request, isEdit)
    }

    private fun summarizeFanOutFailures(results: List<Result<ImageResult>>): List<String> =
        results.mapNotNull { result ->
            result.exceptionOrNull()?.let { throwable ->
                formatImageGenerationThrowable(throwable)
            }
        }

    private suspend fun performImagesSingle(request: ImageRequest, isEdit: Boolean): ImageResult {
        return if (isEdit) {
            val path = "images/edits"
            val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("model", request.profile.model)
                .addFormDataPart("prompt", request.prompt)
                .addFormDataPart("size", request.params.size)
                .addFormDataPart("output_format", request.params.outputFormat)
                .addFormDataPart("moderation", request.params.moderation)
                .addFormDataPart("n", request.params.count.toString())
                .apply {
                    if (!request.profile.codexCliLikeMode) addFormDataPart("quality", request.params.quality)
                    if (request.profile.responseFormatB64Json) addFormDataPart("response_format", "b64_json")
                    if (request.params.outputCompression != null && request.params.outputFormat != "png") {
                        addFormDataPart("output_compression", request.params.outputCompression.toString())
                    }
                    request.inputImages.forEachIndexed { index, dataUrl ->
                        val file = writeTempDataUrl(dataUrl, "input-$index")
                        addFormDataPart("image[]", file.name, file.asRequestBody(guessMimeType(file).toMediaType()))
                    }
                    request.maskImage?.let { dataUrl ->
                        val file = writeTempDataUrl(dataUrl, "mask")
                        addFormDataPart("mask", file.name, file.asRequestBody("image/png".toMediaType()))
                    }
                }
                .build()
            val responseText = executeMultipartRequest(request.profile, path, multipart)
            val parsed = AppJson.decodeFromString(ImagesApiResponse.serializer(), responseText)
            parseImagesResult(parsed, responseText, request.params)
        } else {
            val body = buildJsonObject {
                put("model", request.profile.model)
                put("prompt", request.prompt)
                put("size", request.params.size)
                put("output_format", request.params.outputFormat)
                put("moderation", request.params.moderation)
                put("n", request.params.count)
                if (!request.profile.codexCliLikeMode) put("quality", request.params.quality)
                if (request.profile.responseFormatB64Json) put("response_format", "b64_json")
                request.params.outputCompression?.let {
                    if (request.params.outputFormat != "png") put("output_compression", it)
                }
            }
            val responseText = executeJsonRequest(
                profile = request.profile,
                relativePath = "images/generations",
                payload = AppJson.encodeToString(JsonObject.serializer(), body),
            )
            val parsed = AppJson.decodeFromString(ImagesApiResponse.serializer(), responseText)
            parseImagesResult(parsed, responseText, request.params)
        }
    }

    private fun shouldFanOutImageRequests(profile: ApiProfile, count: Int): Boolean {
        if (count <= 1) return false
        if (profile.codexCliLikeMode) return true
        val host = runCatching { URI(profile.baseUrl).host?.lowercase().orEmpty() }.getOrDefault("")
        return host.isNotBlank() && host != "api.openai.com"
    }

    private suspend fun performFal(request: ImageRequest, isEdit: Boolean): ImageResult {
        val endpoint = request.profile.model.trim().trim('/').ifBlank { "openai/gpt-image-2" }
            .let { if (isEdit && !it.endsWith("/edit")) "$it/edit" else it }
        val body = buildJsonObject {
            put("prompt", request.prompt)
            put("quality", if (request.params.quality == "auto") "high" else request.params.quality)
            put("num_images", request.params.count.coerceIn(1, 4))
            put("output_format", request.params.outputFormat)
            val size = request.params.size
            if (!isEdit && size == "auto") {
                put("image_size", buildJsonObject {
                    put("width", 1360)
                    put("height", 1024)
                })
            } else if (isEdit && size == "auto") {
                put("image_size", "auto")
            } else {
                val match = Regex("^(\\d+)x(\\d+)$").matchEntire(size)
                if (match != null) {
                    put("image_size", buildJsonObject {
                        put("width", match.groupValues[1].toInt())
                        put("height", match.groupValues[2].toInt())
                    })
                } else {
                    put("image_size", buildJsonObject {
                        put("width", 1360)
                        put("height", 1024)
                    })
                }
            }
            if (isEdit) {
                put("image_urls", buildJsonArray { request.inputImages.forEach { add(JsonPrimitive(it)) } })
            }
            request.maskImage?.let { put("mask_url", it) }
        }
        val base = request.profile.baseUrl.trim().trimEnd('/').ifBlank { "https://fal.run" }
        val responseText = executeJsonRequestAbsolute(
            profile = request.profile,
            url = "$base/$endpoint",
            payload = AppJson.encodeToString(JsonObject.serializer(), body),
        )
        return parseFalResult(AppJson.parseToJsonElement(responseText), responseText, request.params)
    }

    private suspend fun parseFalResult(payload: JsonElement, rawText: String, requestParams: TaskParams): ImageResult {
        val candidates =
            getAllByPath(payload, "images.*") +
                listOfNotNull(getByPath(payload, "image"), getByPath(payload, "url"))
        val fallbackMime = mimeForOutputFormat(requestParams.outputFormat)
        val images = mutableListOf<String>()
        val rawUrls = mutableListOf<String>()
        val actualParams = mutableListOf<TaskParams?>()

        candidates.forEach { candidate ->
            val value = readFalImageValue(candidate) ?: return@forEach
            if (value.startsWith("http://") || value.startsWith("https://")) {
                rawUrls += value
                images += fetchUrlAsDataUrl(value)
            } else {
                images += if (value.startsWith("data:")) value else "data:$fallbackMime;base64,$value"
            }
            actualParams += readFalImageSize(candidate)?.let { requestParams.copy(size = it) } ?: requestParams.copy(count = 1)
        }
        if (images.isEmpty()) error(formatImageGenerationError("fal.ai returned no images."))
        return ImageResult(
            images = images,
            rawImageUrls = rawUrls,
            rawResponsePayload = summarizeRawResponse(rawText),
            actualParamsList = actualParams,
            revisedPrompts = images.map { null },
        )
    }

    private fun readFalImageValue(value: JsonElement): String? {
        if (value is JsonPrimitive) return value.asStringOrNull()
        if (value !is JsonObject) return null
        return value["url"]?.asStringOrNull()
            ?: value["b64_json"]?.asStringOrNull()
            ?: value["base64"]?.asStringOrNull()
            ?: value["data"]?.asStringOrNull()
    }

    private fun readFalImageSize(value: JsonElement): String? {
        if (value !is JsonObject) return null
        val width = value["width"]?.asStringOrNull()?.toDoubleOrNull()?.toInt()
        val height = value["height"]?.asStringOrNull()?.toDoubleOrNull()?.toInt()
        return if (width != null && height != null && width > 0 && height > 0) "${width}x${height}" else null
    }

    private suspend fun performCustom(request: ImageRequest, isEdit: Boolean): ImageResult {
        val provider = requireNotNull(request.customProvider)
        val mapping = if (isEdit) provider.editSubmit ?: provider.submit else provider.submit
        val payloadText = submitCustomRequest(request, mapping, isEdit)
        val payloadJson = AppJson.parseToJsonElement(payloadText)
        val taskId = mapping.taskIdPath?.let { getByPath(payloadJson, it)?.asStringOrNull() }?.takeIf { it.isNotBlank() }
        return if (taskId != null && provider.poll != null) {
            request.onRemoteTaskAccepted(taskId)
            pollCustomTask(request.profile, provider.poll, taskId, request.params)
        } else {
            val parsedImages = extractImagesFromCustomResult(payloadJson, mapping.result ?: CustomProviderResultMapping(), request.params)
            parsedImages.copy(rawResponsePayload = summarizeRawResponse(payloadText))
        }
    }

    private suspend fun pollCustomTask(
        profile: ApiProfile,
        poll: CustomProviderPollMapping,
        remoteTaskId: String,
        params: TaskParams,
    ): ImageResult {
        repeat(240) {
            val path = poll.path
                .replace("{task_id}", remoteTaskId)
                .replace("{taskId}", remoteTaskId)
            val responseText = executeSimpleRequest(profile, path, poll.method, query = poll.query)
            val payload = AppJson.parseToJsonElement(responseText)
            val status = getByPath(payload, poll.statusPath)?.asStringOrNull()
            when {
                status != null && poll.successValues.any { it.equals(status, ignoreCase = true) } -> {
                    return extractImagesFromCustomResult(payload, poll.result, params).copy(
                        rawResponsePayload = summarizeRawResponse(responseText),
                        remoteTaskId = remoteTaskId,
                    )
                }
                status != null && poll.failureValues.any { it.equals(status, ignoreCase = true) } -> {
                    error(formatImageGenerationError(getByPath(payload, poll.errorPath)?.asStringOrNull() ?: "Remote task failed."))
                }
            }
            delay(poll.intervalSeconds.coerceAtLeast(1) * 1_000L)
        }
        error(formatImageGenerationError("Task polling timed out."))
    }

    private suspend fun submitCustomRequest(
        request: ImageRequest,
        mapping: CustomProviderSubmitMapping,
        isEdit: Boolean,
    ): String {
        val context = mapOf(
            "prompt" to request.prompt,
            "profile" to request.profile,
            "params" to request.params,
            "inputImages" to mapOf("dataUrls" to request.inputImages),
            "mask" to mapOf("dataUrl" to request.maskImage),
        )
        return if (mapping.contentType == "multipart" || isEdit) {
            val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
            mapping.body.forEach { (key, value) ->
                resolveTemplateValue(value, context)?.let { builder.addFormDataPart(key, it) }
            }
            if (request.profile.responseFormatB64Json && !mapping.body.containsKey("response_format")) {
                builder.addFormDataPart("response_format", "b64_json")
            }
            mapping.files.forEach { file ->
                when (file.source) {
                    "inputImages" -> request.inputImages.forEachIndexed { index, dataUrl ->
                        val tmp = writeTempDataUrl(dataUrl, "ref-$index")
                        builder.addFormDataPart(file.field, tmp.name, tmp.asRequestBody(guessMimeType(tmp).toMediaType()))
                    }
                    "mask" -> request.maskImage?.let { dataUrl ->
                        val tmp = writeTempDataUrl(dataUrl, "mask")
                        builder.addFormDataPart(file.field, tmp.name, tmp.asRequestBody("image/png".toMediaType()))
                    }
                }
            }
            executeMultipartRequest(request.profile, mapping.path, builder.build(), mapping.method, mapping.query)
        } else {
            val json = buildJsonObject {
                mapping.body.forEach { (key, value) ->
                    resolveTemplateValue(value, context)?.let { put(key, JsonPrimitive(it)) }
                }
                if (request.profile.responseFormatB64Json && !mapping.body.containsKey("response_format")) {
                    put("response_format", "b64_json")
                }
            }
            executeJsonRequest(
                profile = request.profile,
                relativePath = mapping.path,
                payload = AppJson.encodeToString(JsonObject.serializer(), json),
                method = mapping.method,
                query = mapping.query,
            )
        }
    }

    private fun resolveTemplateValue(template: String, context: Map<String, Any?>): String? {
        if (!template.startsWith("$")) return template
        val path = template.removePrefix("$")
        val parts = path.split('.')
        var current: Any? = context
        parts.forEach { part ->
            current = when (current) {
                is Map<*, *> -> current[part]
                is ApiProfile -> when (part) {
                    "model" -> current.model
                    "baseUrl" -> current.baseUrl
                    else -> null
                }
                is TaskParams -> when (part) {
                    "size" -> current.size
                    "quality" -> current.quality
                    "output_format" -> current.outputFormat
                    "output_compression" -> current.outputCompression
                    "moderation" -> current.moderation
                    "n" -> current.count
                    else -> null
                }
                is List<*> -> if (part == "dataUrls") current.filterIsInstance<String>().joinToString(",") else null
                else -> null
            }
        }
        return when (current) {
            null -> null
            is List<*> -> current.joinToString(",")
            else -> current.toString()
        }
    }

    private suspend fun extractImagesFromCustomResult(
        payload: JsonElement,
        mapping: CustomProviderResultMapping,
        params: TaskParams,
    ): ImageResult {
        val b64 = mapping.b64JsonPaths.flatMap { path ->
            getAllByPath(payload, path).mapNotNull { it.asStringOrNull() }
        }.map { ensureDataUrl(it, params.outputFormat) }
        val urls = mapping.imageUrlPaths.flatMap { path ->
            getAllByPath(payload, path).mapNotNull { it.asStringOrNull() }
        }
        val fetched = urls.map { fetchUrlAsDataUrl(it) }
        return ImageResult(
            images = b64 + fetched,
            rawImageUrls = urls,
        )
    }

    private suspend fun executeWithFallback(
        profile: ApiProfile,
        fallbacks: List<String>,
        maxAttempts: Int,
        block: suspend (ApiProfile) -> ImageResult,
    ): ImageResult {
        val candidates = buildList {
            add(profile.model)
            addAll(fallbacks.filterNot { it == profile.model })
        }
        var lastError: Throwable? = null
        candidates.forEach { model ->
            repeat(maxAttempts.coerceAtLeast(1)) { attempt ->
                try {
                    return block(profile.copy(model = model))
                } catch (t: Throwable) {
                    lastError = t
                    if (attempt < maxAttempts - 1 && isTransient(t)) delay((attempt + 1) * 1_500L)
                    else if (!shouldTryNextModel(t)) throw t
                }
            }
        }
        throw lastError ?: IllegalStateException(formatImageGenerationError("Image execution failed."))
    }

    private fun shouldTryNextModel(throwable: Throwable): Boolean {
        val message = throwable.message.orEmpty().lowercase()
        return listOf("model", "not found", "unsupported", "invalid").any(message::contains)
    }

    private fun isTransient(throwable: Throwable): Boolean {
        val message = throwable.message.orEmpty().lowercase()
        return listOf("timeout", "temporarily", "503", "502", "504", "429").any(message::contains)
    }

    private fun parseResponsesResult(
        response: ResponsesApiResponse,
        rawText: String,
        requestParams: TaskParams,
    ): ImageResult {
        val images = mutableListOf<String>()
        val revisedPrompts = mutableListOf<String?>()
        val actualParams = mutableListOf<TaskParams?>()
        response.output.forEach { item ->
            if (item.type != "image_generation_call") return@forEach
            val resultString = when (val result = item.result) {
                is JsonPrimitive -> result.content
                is JsonObject -> result["b64_json"]?.asStringOrNull()
                    ?: result["image"]?.asStringOrNull()
                    ?: result["data"]?.asStringOrNull()
                else -> null
            }
            if (resultString != null) {
                images += ensureDataUrl(resultString, item.outputFormat ?: requestParams.outputFormat)
                revisedPrompts += item.revisedPrompt
                actualParams += requestParams.copy(
                    size = item.size ?: requestParams.size,
                    quality = item.quality ?: requestParams.quality,
                    outputFormat = item.outputFormat ?: requestParams.outputFormat,
                    outputCompression = item.outputCompression ?: requestParams.outputCompression,
                    moderation = item.moderation ?: requestParams.moderation,
                )
            }
        }
        if (images.isEmpty()) error(formatImageGenerationError("No image payload found in /responses result."))
        return ImageResult(
            images = images,
            actualParamsList = actualParams,
            revisedPrompts = revisedPrompts,
            responseIds = listOfNotNull(response.id),
            rawResponsePayload = summarizeRawResponse(rawText),
        )
    }

    private suspend fun parseImagesResult(
        response: ImagesApiResponse,
        rawText: String,
        requestParams: TaskParams,
    ): ImageResult {
        val images = mutableListOf<String>()
        val revisedPrompts = mutableListOf<String?>()
        val urls = mutableListOf<String>()
        response.data.forEach { item ->
            item.b64Json?.let {
                images += ensureDataUrl(it, item.outputFormat ?: requestParams.outputFormat)
                revisedPrompts += item.revisedPrompt
            }
            item.url?.let {
                urls += it
                images += fetchUrlAsDataUrl(it)
                revisedPrompts += item.revisedPrompt
            }
        }
        if (images.isEmpty()) error(formatImageGenerationError("No image payload found in Images API result."))
        return ImageResult(
            images = images,
            revisedPrompts = revisedPrompts,
            rawImageUrls = urls,
            rawResponsePayload = summarizeRawResponse(rawText),
            actualParamsList = images.map {
                requestParams.copy(count = 1)
            },
        )
    }

    private suspend fun executeJsonRequest(
        profile: ApiProfile,
        relativePath: String,
        payload: String,
        method: String = "POST",
        query: Map<String, String> = emptyMap(),
    ): String {
        val request = Request.Builder()
            .url(buildUrl(profile.baseUrl, relativePath, query))
            .applyHeaders(profile)
            .method(method, if (method == "GET") null else payload.toRequestBody("application/json".toMediaType()))
            .build()
        return executeRequest(request, profile.timeoutSec)
    }

    private suspend fun executeJsonRequestAbsolute(
        profile: ApiProfile,
        url: String,
        payload: String,
        method: String = "POST",
    ): String {
        val request = Request.Builder()
            .url(url)
            .applyHeaders(profile)
            .method(method, if (method == "GET") null else payload.toRequestBody("application/json".toMediaType()))
            .build()
        return executeRequest(request, profile.timeoutSec)
    }

    private suspend fun executeMultipartRequest(
        profile: ApiProfile,
        relativePath: String,
        multipartBody: MultipartBody,
        method: String = "POST",
        query: Map<String, String> = emptyMap(),
    ): String {
        val request = Request.Builder()
            .url(buildUrl(profile.baseUrl, relativePath, query))
            .applyHeaders(profile)
            .method(method, if (method == "GET") null else multipartBody)
            .build()
        return executeRequest(request, profile.timeoutSec)
    }

    private suspend fun executeSimpleRequest(
        profile: ApiProfile,
        relativePath: String,
        method: String,
        query: Map<String, String> = emptyMap(),
    ): String {
        val body = if (method == "GET") null else FormBody.Builder().build()
        val request = Request.Builder()
            .url(buildUrl(profile.baseUrl, relativePath, query))
            .applyHeaders(profile)
            .method(method, body)
            .build()
        return executeRequest(request, profile.timeoutSec)
    }

    private suspend fun executeRequest(request: Request, timeoutSec: Int): String {
        val effectiveTimeoutSec = effectiveImageRequestTimeoutSec(timeoutSec)
        val requestClient = client.newBuilder()
            .callTimeout(effectiveTimeoutSec.toLong(), TimeUnit.SECONDS)
            .build()
        Log.i(
            NETWORK_TAG,
            "request ${request.method} ${request.url.encodedPath} timeout=${effectiveTimeoutSec}s",
        )
        return suspendCancellableCoroutine { continuation ->
            val call = requestClient.newCall(request)
            continuation.invokeOnCancellation {
                call.cancel()
                Log.w(NETWORK_TAG, "cancelled ${request.method} ${request.url.encodedPath}")
            }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.w(
                        NETWORK_TAG,
                        "failure ${request.method} ${request.url.encodedPath} error=${e.message.orEmpty().take(200)}",
                    )
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use {
                            val text = it.body?.string().orEmpty()
                            if (!it.isSuccessful) {
                                val message = runCatching {
                                    AppJson.decodeFromString(ErrorEnvelope.serializer(), text).error?.message
                                        ?: AppJson.decodeFromString(ErrorEnvelope.serializer(), text).message
                                }.getOrNull() ?: text.ifBlank { "HTTP ${it.code}" }
                                val formattedMessage = formatImageGenerationError(message)
                                Log.w(
                                    NETWORK_TAG,
                                    "http failure ${request.method} ${request.url.encodedPath} code=${it.code} message=${message.take(200)}",
                                )
                                if (continuation.isActive) continuation.resumeWithException(IllegalStateException(formattedMessage))
                                return
                            }
                            Log.i(
                                NETWORK_TAG,
                                "success ${request.method} ${request.url.encodedPath} code=${it.code} bytes=${text.length}",
                            )
                            if (continuation.isActive) continuation.resume(text)
                        }
                    } catch (throwable: Throwable) {
                        if (continuation.isActive) continuation.resumeWithException(throwable)
                    }
                }
            })
        }
    }

    private fun Request.Builder.applyHeaders(profile: ApiProfile): Request.Builder = apply {
        addHeader("Authorization", "Bearer ${profile.apiKey}")
        addHeader("Accept", "application/json")
        profile.extraHeaders.forEach { (key, value) -> addHeader(key, value) }
    }

    private fun buildUrl(baseUrl: String, relativePath: String, query: Map<String, String> = emptyMap()): String {
        val base = baseUrl.trim().trimEnd('/').let { if (it.endsWith("/v1")) it else "$it/v1" }
        val path = relativePath.trim().trimStart('/')
        val queryString = if (query.isEmpty()) "" else query.entries.joinToString("&", prefix = "?") { "${it.key}=${it.value}" }
        return "$base/$path$queryString"
    }

    private fun buildResponsesInput(prompt: String, inputImages: List<String>, maskImage: String?): JsonElement {
        val content = buildJsonArray {
            add(buildJsonObject {
                put("type", "input_text")
                put("text", prompt)
            })
            inputImages.forEach { image ->
                add(buildJsonObject {
                    put("type", "input_image")
                    put("image_url", image)
                })
            }
            maskImage?.let { image ->
                add(buildJsonObject {
                    put("type", "input_image")
                    put("image_url", image)
                })
            }
        }
        return JsonArray(
            listOf(
                buildJsonObject {
                    put("role", "user")
                    put("content", content)
                },
            ),
        )
    }

    private fun augmentPrompt(
        prompt: String,
        sessionTurns: List<SessionTurn>,
        includeSessionReferenceImages: Boolean,
    ): String {
        if (sessionTurns.isEmpty()) return prompt
        val summary = sessionTurns.takeLast(3).joinToString("\n") { turn ->
            buildString {
                append("Turn command=")
                append(turn.command)
                append("; prompt=")
                append(turn.prompt)
                turn.revisedPrompts.lastOrNull()?.let {
                    append("; revised_prompt=")
                    append(it)
                }
            }
        }
        return buildString {
            append("Session context: continue the recent image conversation below. ")
            append("Preserve visual continuity with the latest useful result unless the current request explicitly changes it.\n")
            append(summary)
            if (includeSessionReferenceImages) {
                append("\nUse the latest output image from the local session as continuity reference when possible.")
            }
            append("\nPrimary request: ")
            append(prompt)
        }
    }

    private suspend fun fetchUrlAsDataUrl(url: String): String = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) error(formatImageGenerationError("Failed to download remote image: HTTP ${response.code}"))
            val bytes = response.body?.bytes() ?: error(formatImageGenerationError("Remote image body was empty."))
            val mime = response.header("Content-Type") ?: "image/png"
            "data:$mime;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
        }
    }

    private fun ensureDataUrl(value: String, outputFormat: String): String {
        return if (value.startsWith("data:")) value else "data:${mimeForOutputFormat(outputFormat)};base64,$value"
    }

    private fun mimeForOutputFormat(outputFormat: String): String = when (outputFormat.lowercase()) {
        "jpeg", "jpg" -> "image/jpeg"
        "webp" -> "image/webp"
        else -> "image/png"
    }

    private fun writeTempDataUrl(dataUrl: String, prefix: String): File {
        val bytes = Base64.decode(dataUrl.substringAfter(","), Base64.DEFAULT)
        val extension = when {
            dataUrl.startsWith("data:image/jpeg") -> "jpg"
            dataUrl.startsWith("data:image/webp") -> "webp"
            else -> "png"
        }
        val file = File(appContext.cacheDir, "$prefix-${UUID.randomUUID()}.$extension")
        file.writeBytes(bytes)
        return file
    }

    private fun guessMimeType(file: File): String = when (file.extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        else -> "image/png"
    }

    private fun summarizeRawResponse(rawText: String): String =
        if (rawText.length <= MAX_RAW_RESPONSE_PAYLOAD_LENGTH) {
            rawText
        } else {
            rawText.take(MAX_RAW_RESPONSE_PAYLOAD_LENGTH) +
                "\n... truncated raw response, originalLength=${rawText.length}"
        }
}

internal fun effectiveImageRequestTimeoutSec(timeoutSec: Int): Int =
    timeoutSec.coerceAtLeast(MIN_IMAGE_REQUEST_TIMEOUT_SEC)
