package com.shj56166androidimage2.app.data.repo

import com.shj56166androidimage2.app.data.db.ImageSessionDao
import com.shj56166androidimage2.app.data.db.SessionTurnDao
import com.shj56166androidimage2.app.data.model.ImageSession
import com.shj56166androidimage2.app.data.model.SessionTurn
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SessionRepository(
    private val sessionDao: ImageSessionDao,
    private val turnDao: SessionTurnDao,
) {
    fun observeSessions(): Flow<List<ImageSession>> =
        sessionDao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun getTurns(sessionId: String): List<SessionTurn> =
        turnDao.getForSession(sessionId).map { it.toModel() }

    suspend fun upsertSession(session: ImageSession) {
        sessionDao.upsert(session.toEntity())
    }

    suspend fun upsertSessions(sessions: List<ImageSession>) {
        sessionDao.upsertAll(sessions.map { it.toEntity() })
    }

    suspend fun deleteAll() {
        turnDao.deleteAll()
        sessionDao.deleteAll()
    }

    suspend fun appendTurns(session: ImageSession, turns: List<SessionTurn>, maxTurns: Int = 12) {
        sessionDao.upsert(session.toEntity())
        val retained = turns.takeLast(maxTurns)
        val ids = retained.mapIndexed { index, _ -> "${session.id}-${index + 1}" }
        retained.forEachIndexed { index, turn ->
            turnDao.upsert(turn.toEntity(session.id, ids[index]))
        }
        turnDao.pruneForSession(session.id, ids)
    }

    fun newSession(name: String): ImageSession = ImageSession(
        id = UUID.randomUUID().toString(),
        name = name,
        updatedAt = System.currentTimeMillis(),
    )
}
