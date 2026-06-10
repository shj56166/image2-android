package com.shj56166androidimage2.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        StoredImageAssetEntity::class,
        ImageTaskEntity::class,
        ImageSessionEntity::class,
        SessionTurnEntity::class,
        MaskDraftEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun imageAssetDao(): ImageAssetDao
    abstract fun imageTaskDao(): ImageTaskDao
    abstract fun imageSessionDao(): ImageSessionDao
    abstract fun sessionTurnDao(): SessionTurnDao
    abstract fun maskDraftDao(): MaskDraftDao
}
