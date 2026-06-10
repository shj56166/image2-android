package com.shj56166androidimage2.app.data.repo

import android.content.Context
import androidx.room.Room
import com.shj56166androidimage2.app.data.db.AppDatabase
import com.shj56166androidimage2.app.data.network.EngineFactory
import com.shj56166androidimage2.app.domain.engine.ImageExecutionEngine

class AppContainer(context: Context) {
    val appContext = context.applicationContext

    val database: AppDatabase = Room.databaseBuilder(
        appContext,
        AppDatabase::class.java,
        "android_native_image_app.db",
    ).build()

    val settingsRepository = SettingsRepository(appContext)
    val taskRepository = TaskRepository(database.imageTaskDao())
    val sessionRepository = SessionRepository(database.imageSessionDao(), database.sessionTurnDao())
    val maskDraftRepository = MaskDraftRepository(database.maskDraftDao())
    val imageStorageRepository = ImageStorageRepository(appContext, database.imageAssetDao())
    val engineFactory = EngineFactory(appContext)

    fun imageExecutionEngine(): ImageExecutionEngine = engineFactory.create()
}
