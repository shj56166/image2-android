package com.shj56166androidimage2.app.data.repo

import com.shj56166androidimage2.app.data.db.MaskDraftDao
import com.shj56166androidimage2.app.data.model.MaskDraft

class MaskDraftRepository(private val dao: MaskDraftDao) {
    suspend fun get(targetImageId: String): MaskDraft? = dao.getByTargetId(targetImageId)?.toModel()

    suspend fun upsert(draft: MaskDraft) {
        dao.upsert(draft.toEntity())
    }

    suspend fun delete(targetImageId: String) {
        dao.deleteByTargetId(targetImageId)
    }

    suspend fun deleteAll() {
        dao.deleteAll()
    }
}
