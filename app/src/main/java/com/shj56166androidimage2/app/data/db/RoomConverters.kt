package com.shj56166androidimage2.app.data.db

import androidx.room.TypeConverter
import com.shj56166androidimage2.app.data.model.ImageSource
import com.shj56166androidimage2.app.data.model.TaskStatus

class RoomConverters {
    @TypeConverter
    fun imageSourceFromString(value: String): ImageSource = ImageSource.valueOf(value)

    @TypeConverter
    fun imageSourceToString(value: ImageSource): String = value.name

    @TypeConverter
    fun taskStatusFromString(value: String): TaskStatus = TaskStatus.valueOf(value)

    @TypeConverter
    fun taskStatusToString(value: TaskStatus): String = value.name
}
