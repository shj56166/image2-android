package com.shj56166androidimage2.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageAssetDao {
    @Query("SELECT * FROM image_assets ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<StoredImageAssetEntity>>

    @Query("SELECT * FROM image_assets WHERE id = :id")
    suspend fun getById(id: String): StoredImageAssetEntity?

    @Query("SELECT * FROM image_assets ORDER BY createdAt DESC")
    suspend fun getAll(): List<StoredImageAssetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: StoredImageAssetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<StoredImageAssetEntity>)

    @Query("DELETE FROM image_assets WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM image_assets")
    suspend fun deleteAll()
}

@Dao
interface ImageTaskDao {
    @Query(
        """
        SELECT
            id,
            prompt,
            paramsJson,
            sessionId,
            apiProvider,
            apiProfileId,
            apiProfileName,
            apiModel,
            remoteTaskId,
            inputImageIdsJson,
            maskTargetImageId,
            maskImageId,
            outputImageIdsJson,
            rawImageUrlsJson,
            rawResponsePayload,
            actualParamsByImageJson,
            revisedPromptByImageJson,
            status,
            error,
            createdAt,
            finishedAt,
            elapsedMillis,
            isFavorite
        FROM image_tasks
        ORDER BY createdAt DESC
        """,
    )
    fun observeAll(): Flow<List<ImageTaskEntity>>

    @Query(
        """
        SELECT
            id,
            prompt,
            paramsJson,
            sessionId,
            apiProvider,
            apiProfileId,
            apiProfileName,
            apiModel,
            remoteTaskId,
            inputImageIdsJson,
            maskTargetImageId,
            maskImageId,
            outputImageIdsJson,
            rawImageUrlsJson,
            rawResponsePayload,
            actualParamsByImageJson,
            revisedPromptByImageJson,
            status,
            error,
            createdAt,
            finishedAt,
            elapsedMillis,
            isFavorite
        FROM image_tasks
        WHERE id = :id
        """,
    )
    fun observeById(id: String): Flow<ImageTaskEntity?>

    @Query(
        """
        SELECT
            id,
            prompt,
            paramsJson,
            sessionId,
            apiProvider,
            apiProfileId,
            apiProfileName,
            apiModel,
            remoteTaskId,
            inputImageIdsJson,
            maskTargetImageId,
            maskImageId,
            outputImageIdsJson,
            rawImageUrlsJson,
            rawResponsePayload,
            actualParamsByImageJson,
            revisedPromptByImageJson,
            status,
            error,
            createdAt,
            finishedAt,
            elapsedMillis,
            isFavorite
        FROM image_tasks
        WHERE id = :id
        """,
    )
    suspend fun getById(id: String): ImageTaskEntity?

    @Query(
        """
        SELECT
            id,
            prompt,
            paramsJson,
            sessionId,
            apiProvider,
            apiProfileId,
            apiProfileName,
            apiModel,
            remoteTaskId,
            inputImageIdsJson,
            maskTargetImageId,
            maskImageId,
            outputImageIdsJson,
            rawImageUrlsJson,
            rawResponsePayload,
            actualParamsByImageJson,
            revisedPromptByImageJson,
            status,
            error,
            createdAt,
            finishedAt,
            elapsedMillis,
            isFavorite
        FROM image_tasks
        ORDER BY createdAt DESC
        """,
    )
    suspend fun getAll(): List<ImageTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ImageTaskEntity)

    @Query("DELETE FROM image_tasks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ImageTaskEntity>)

    @Query("DELETE FROM image_tasks")
    suspend fun deleteAll()
}

@Dao
interface ImageSessionDao {
    @Query("SELECT * FROM image_sessions ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ImageSessionEntity>>

    @Query("SELECT * FROM image_sessions WHERE id = :id")
    suspend fun getById(id: String): ImageSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ImageSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ImageSessionEntity>)

    @Query("DELETE FROM image_sessions")
    suspend fun deleteAll()
}

@Dao
interface SessionTurnDao {
    @Query("SELECT * FROM session_turns WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getForSession(sessionId: String): List<SessionTurnEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SessionTurnEntity)

    @Query("DELETE FROM session_turns WHERE sessionId = :sessionId AND id NOT IN (:retainIds)")
    suspend fun pruneForSession(sessionId: String, retainIds: List<String>)

    @Query("DELETE FROM session_turns")
    suspend fun deleteAll()
}

@Dao
interface MaskDraftDao {
    @Query("SELECT * FROM mask_drafts WHERE targetImageId = :targetImageId")
    suspend fun getByTargetId(targetImageId: String): MaskDraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MaskDraftEntity)

    @Query("DELETE FROM mask_drafts WHERE targetImageId = :targetImageId")
    suspend fun deleteByTargetId(targetImageId: String)

    @Query("DELETE FROM mask_drafts")
    suspend fun deleteAll()
}
