package com.shj56166androidimage2.app.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull as ktxFirstOrNull

suspend fun <T> Flow<List<T>>.firstOrNull(): List<T> = ktxFirstOrNull() ?: emptyList()
