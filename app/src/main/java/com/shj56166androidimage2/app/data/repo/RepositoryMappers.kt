package com.shj56166androidimage2.app.data.repo

import com.shj56166androidimage2.app.data.db.ImageSessionEntity
import com.shj56166androidimage2.app.data.db.ImageTaskEntity
import com.shj56166androidimage2.app.data.db.MaskDraftEntity
import com.shj56166androidimage2.app.data.db.SessionTurnEntity
import com.shj56166androidimage2.app.data.db.StoredImageAssetEntity
import com.shj56166androidimage2.app.data.model.ImageSession
import com.shj56166androidimage2.app.data.model.ImageTask
import com.shj56166androidimage2.app.data.model.MaskDraft
import com.shj56166androidimage2.app.data.model.SessionTurn
import com.shj56166androidimage2.app.data.model.StoredImageAsset
import com.shj56166androidimage2.app.data.model.TaskParams
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

private val stringListSerializer = ListSerializer(String.serializer())
private val taskParamsMapSerializer = MapSerializer(String.serializer(), TaskParams.serializer())
private val stringMapSerializer = MapSerializer(String.serializer(), String.serializer())
private const val MAX_RAW_RESPONSE_PAYLOAD_LENGTH = 32_768

fun StoredImageAssetEntity.toModel(): StoredImageAsset = StoredImageAsset(
    id = id,
    filePath = filePath,
    thumbnailPath = thumbnailPath,
    mimeType = mimeType,
    source = source,
    width = width,
    height = height,
    createdAt = createdAt,
    sha256 = sha256,
)

fun StoredImageAsset.toEntity(): StoredImageAssetEntity = StoredImageAssetEntity(
    id = id,
    filePath = filePath,
    thumbnailPath = thumbnailPath,
    mimeType = mimeType,
    source = source,
    width = width,
    height = height,
    createdAt = createdAt,
    sha256 = sha256,
)

fun ImageTaskEntity.toModel(): ImageTask = ImageTask(
    id = id,
    prompt = prompt,
    params = decodeJsonOrDefault(paramsJson, TaskParams()),
    sessionId = sessionId,
    apiProvider = apiProvider,
    apiProfileId = apiProfileId,
    apiProfileName = apiProfileName,
    apiModel = apiModel,
    remoteTaskId = remoteTaskId,
    inputImageIds = decodeJsonOrDefault(inputImageIdsJson, emptyList()),
    maskTargetImageId = maskTargetImageId,
    maskImageId = maskImageId,
    outputImageIds = decodeJsonOrDefault(outputImageIdsJson, emptyList()),
    rawImageUrls = decodeJsonOrDefault(rawImageUrlsJson, emptyList()),
    rawResponsePayload = rawResponsePayload,
    actualParamsByImage = decodeJsonOrDefault(actualParamsByImageJson, emptyMap()),
    revisedPromptByImage = decodeJsonOrDefault(revisedPromptByImageJson, emptyMap()),
    status = status,
    error = error,
    createdAt = createdAt,
    finishedAt = finishedAt,
    elapsedMillis = elapsedMillis,
    isFavorite = isFavorite,
)

private inline fun <reified T> decodeJsonOrDefault(text: String, defaultValue: T): T =
    runCatching { AppJson.decodeFromString<T>(text) }.getOrElse { defaultValue }

fun ImageTask.toEntity(): ImageTaskEntity = ImageTaskEntity(
    id = id,
    prompt = prompt,
    paramsJson = AppJson.encodeToString(TaskParams.serializer(), params),
    sessionId = sessionId,
    apiProvider = apiProvider,
    apiProfileId = apiProfileId,
    apiProfileName = apiProfileName,
    apiModel = apiModel,
    remoteTaskId = remoteTaskId,
    inputImageIdsJson = AppJson.encodeToString(stringListSerializer, inputImageIds),
    maskTargetImageId = maskTargetImageId,
    maskImageId = maskImageId,
    outputImageIdsJson = AppJson.encodeToString(stringListSerializer, outputImageIds),
    rawImageUrlsJson = AppJson.encodeToString(stringListSerializer, rawImageUrls),
    rawResponsePayload = rawResponsePayload?.take(MAX_RAW_RESPONSE_PAYLOAD_LENGTH),
    actualParamsByImageJson = AppJson.encodeToString(taskParamsMapSerializer, actualParamsByImage),
    revisedPromptByImageJson = AppJson.encodeToString(stringMapSerializer, revisedPromptByImage),
    status = status,
    error = error,
    createdAt = createdAt,
    finishedAt = finishedAt,
    elapsedMillis = elapsedMillis,
    isFavorite = isFavorite,
)

fun ImageSessionEntity.toModel(): ImageSession = ImageSession(
    id = id,
    name = name,
    updatedAt = updatedAt,
)

fun ImageSession.toEntity(): ImageSessionEntity = ImageSessionEntity(
    id = id,
    name = name,
    updatedAt = updatedAt,
)

fun SessionTurnEntity.toModel(): SessionTurn = SessionTurn(
    timestamp = timestamp,
    command = command,
    prompt = prompt,
    revisedPrompts = AppJson.decodeFromString(stringListSerializer, revisedPromptsJson),
    responseIds = AppJson.decodeFromString(stringListSerializer, responseIdsJson),
    outputImageIds = AppJson.decodeFromString(stringListSerializer, outputImageIdsJson),
    inputImageIds = AppJson.decodeFromString(stringListSerializer, inputImageIdsJson),
    maskImageId = maskImageId,
    model = model,
    imageModel = imageModel,
)

fun SessionTurn.toEntity(sessionId: String, id: String): SessionTurnEntity = SessionTurnEntity(
    id = id,
    sessionId = sessionId,
    timestamp = timestamp,
    command = command,
    prompt = prompt,
    revisedPromptsJson = AppJson.encodeToString(stringListSerializer, revisedPrompts),
    responseIdsJson = AppJson.encodeToString(stringListSerializer, responseIds),
    outputImageIdsJson = AppJson.encodeToString(stringListSerializer, outputImageIds),
    inputImageIdsJson = AppJson.encodeToString(stringListSerializer, inputImageIds),
    maskImageId = maskImageId,
    model = model,
    imageModel = imageModel,
)

fun MaskDraftEntity.toModel(): MaskDraft = MaskDraft(
    targetImageId = targetImageId,
    maskImageId = maskImageId,
    updatedAt = updatedAt,
)

fun MaskDraft.toEntity(): MaskDraftEntity = MaskDraftEntity(
    targetImageId = targetImageId,
    maskImageId = maskImageId,
    updatedAt = updatedAt,
)
