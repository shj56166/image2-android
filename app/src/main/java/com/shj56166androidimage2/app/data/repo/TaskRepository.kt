package com.shj56166androidimage2.app.data.repo

import com.shj56166androidimage2.app.data.db.ImageTaskDao
import com.shj56166androidimage2.app.data.model.ImageTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TaskRepository(private val dao: ImageTaskDao) {
    fun observeAll(): Flow<List<ImageTask>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    fun observeById(id: String): Flow<ImageTask?> = dao.observeById(id).map { it?.toModel() }

    suspend fun getById(id: String): ImageTask? = dao.getById(id)?.toModel()

    suspend fun getAll(): List<ImageTask> = dao.getAll().map { it.toModel() }

    suspend fun upsert(task: ImageTask) {
        dao.upsert(task.toEntity())
    }

    suspend fun upsertAll(tasks: List<ImageTask>) {
        dao.upsertAll(tasks.map { it.toEntity() })
    }

    suspend fun deleteById(id: String) {
        dao.deleteById(id)
    }

    suspend fun deleteAll() {
        dao.deleteAll()
    }
}
