package com.shj56166androidimage2.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.shj56166androidimage2.app.data.model.ImageSource
import com.shj56166androidimage2.app.data.model.TaskStatus

@Entity(tableName = "image_assets")
data class StoredImageAssetEntity(
    @PrimaryKey val id: String,
    val filePath: String,
    val thumbnailPath: String?,
    val mimeType: String,
    val source: ImageSource,
    val width: Int?,
    val height: Int?,
    val createdAt: Long,
    val sha256: String,
)

@Entity(tableName = "image_tasks")
data class ImageTaskEntity(
    @PrimaryKey val id: String,
    val prompt: String,
    val paramsJson: String,
    val sessionId: String?,
    val apiProvider: String,
    val apiProfileId: String?,
    val apiProfileName: String?,
    val apiModel: String?,
    val remoteTaskId: String?,
    val inputImageIdsJson: String,
    val maskTargetImageId: String?,
    val maskImageId: String?,
    val outputImageIdsJson: String,
    val rawImageUrlsJson: String,
    val rawResponsePayload: String?,
    val actualParamsByImageJson: String,
    val revisedPromptByImageJson: String,
    val status: TaskStatus,
    val error: String?,
    val createdAt: Long,
    val finishedAt: Long?,
    val elapsedMillis: Long?,
    val isFavorite: Boolean,
)

@Entity(tableName = "image_sessions")
data class ImageSessionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val updatedAt: Long,
)

@Entity(tableName = "session_turns")
data class SessionTurnEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val timestamp: Long,
    val command: String,
    val prompt: String,
    val revisedPromptsJson: String,
    val responseIdsJson: String,
    val outputImageIdsJson: String,
    val inputImageIdsJson: String,
    val maskImageId: String?,
    val model: String?,
    val imageModel: String?,
)

@Entity(tableName = "mask_drafts")
data class MaskDraftEntity(
    @PrimaryKey val targetImageId: String,
    val maskImageId: String,
    val updatedAt: Long,
)
