package com.shj56166androidimage2.app.data.network

import android.content.Context
import com.shj56166androidimage2.app.domain.engine.ImageExecutionEngine
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class EngineFactory(context: Context) {
    private val appContext = context.applicationContext
    private val dispatcher = Dispatcher().apply {
        maxRequests = 64
        maxRequestsPerHost = 32
    }
    private val client = OkHttpClient.Builder()
        .dispatcher(dispatcher)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.MINUTES)
        .writeTimeout(30, TimeUnit.MINUTES)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()
    private val imageExecutionEngine = DirectUpstreamExecutionEngine(
        client = client,
        appContext = appContext,
    )
    private val profileConnectionTester = ProfileConnectionTester(appContext, imageExecutionEngine)

    fun create(): ImageExecutionEngine = imageExecutionEngine

    fun connectionTester(): ProfileConnectionTester = profileConnectionTester
}
