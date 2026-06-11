package com.shj56166androidimage2.app.data.network

import android.content.Context
import com.shj56166androidimage2.app.R
import com.shj56166androidimage2.app.data.model.ApiMode
import com.shj56166androidimage2.app.data.model.ApiProfile
import com.shj56166androidimage2.app.data.model.TaskParams
import com.shj56166androidimage2.app.domain.engine.ImageExecutionEngine
import com.shj56166androidimage2.app.domain.engine.ImageRequest
import com.shj56166androidimage2.app.ui.screens.BASE64_PROFILE_TEST_COMBINATIONS
import com.shj56166androidimage2.app.ui.screens.CreateProfileDraft
import com.shj56166androidimage2.app.ui.screens.CreateProfileTestState
import com.shj56166androidimage2.app.ui.screens.CreateProfileTestStatus
import com.shj56166androidimage2.app.ui.screens.NON_BASE64_PROFILE_TEST_COMBINATIONS
import com.shj56166androidimage2.app.ui.screens.ORDERED_PROFILE_TEST_COMBINATIONS
import com.shj56166androidimage2.app.ui.screens.ProfileTestCaseResult
import com.shj56166androidimage2.app.ui.screens.ProfileTestCombination
import com.shj56166androidimage2.app.ui.screens.applyCombination
import com.shj56166androidimage2.app.ui.screens.orderedProfileTestResults
import com.shj56166androidimage2.app.ui.screens.recommendedProfileTestCombination
import com.shj56166androidimage2.app.ui.screens.shouldRunBase64Fallback
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

sealed interface ProfileConnectionTestResult {
    data class Success(
        val message: String,
    ) : ProfileConnectionTestResult

    data class Error(
        val message: String,
    ) : ProfileConnectionTestResult
}

class ProfileConnectionTester internal constructor(
    private val appContext: Context,
    private val executeProfileValidation: suspend (ApiProfile) -> Unit,
) {
    constructor(
        context: Context,
        engine: ImageExecutionEngine,
    ) : this(
        appContext = context.applicationContext,
        executeProfileValidation = { profile -> validateProfile(engine, profile) },
    )

    suspend fun test(profile: ApiProfile): ProfileConnectionTestResult {
        val trimmedBaseUrl = profile.baseUrl.trim()
        if (trimmedBaseUrl.isBlank()) {
            return ProfileConnectionTestResult.Error(appContext.getString(R.string.profile_test_base_url_required))
        }
        if (profile.apiKey.isBlank()) {
            return ProfileConnectionTestResult.Error(appContext.getString(R.string.profile_test_api_key_required))
        }

        val normalizedProfile = profile.copy(baseUrl = trimmedBaseUrl, apiKey = profile.apiKey.trim())
        return runCatching {
            executeProfileValidation(normalizedProfile)
        }.fold(
            onSuccess = {
                ProfileConnectionTestResult.Success(
                    appContext.getString(
                        R.string.profile_test_single_success,
                        profileTestCombinationLabel(
                            appContext = appContext,
                            combination = ProfileTestCombination(
                                apiMode = normalizedProfile.apiMode,
                                codexCliLikeMode = normalizedProfile.codexCliLikeMode,
                                responseFormatB64Json = normalizedProfile.responseFormatB64Json,
                            ),
                        ),
                    ),
                )
            },
            onFailure = { throwable ->
                ProfileConnectionTestResult.Error(
                    throwable.message ?: appContext.getString(R.string.profile_test_unknown_error),
                )
            },
        )
    }

    suspend fun testDraft(
        draft: CreateProfileDraft,
        onProgress: suspend (CreateProfileTestState) -> Unit = {},
    ): CreateProfileTestState = coroutineScope {
        val startedAtMillis = System.currentTimeMillis()
        if (draft.baseUrl.trim().isBlank()) {
            return@coroutineScope CreateProfileTestState(
                status = CreateProfileTestStatus.FINISHED,
                totalCount = NON_BASE64_PROFILE_TEST_COMBINATIONS.size,
                startedAtMillis = startedAtMillis,
                results =
                    listOf(
                        ProfileTestCaseResult(
                            combination = NON_BASE64_PROFILE_TEST_COMBINATIONS.first(),
                            success = false,
                            message = appContext.getString(R.string.profile_test_base_url_required),
                        ),
                    ),
            )
        }
        if (draft.apiKey.trim().isBlank()) {
            return@coroutineScope CreateProfileTestState(
                status = CreateProfileTestStatus.FINISHED,
                totalCount = NON_BASE64_PROFILE_TEST_COMBINATIONS.size,
                startedAtMillis = startedAtMillis,
                results =
                    listOf(
                        ProfileTestCaseResult(
                            combination = NON_BASE64_PROFILE_TEST_COMBINATIONS.first(),
                            success = false,
                            message = appContext.getString(R.string.profile_test_api_key_required),
                        ),
                    ),
            )
        }

        val mutex = Mutex()
        val semaphore = Semaphore(3)
        val collectedResults = mutableListOf<ProfileTestCaseResult>()
        var testedCount = 0
        var totalCount = NON_BASE64_PROFILE_TEST_COMBINATIONS.size

        suspend fun snapshot(status: CreateProfileTestStatus): CreateProfileTestState =
            mutex.withLock {
                val orderedResults = orderedProfileTestResults(collectedResults)
                CreateProfileTestState(
                    status = status,
                    testedCount = testedCount,
                    totalCount = totalCount,
                    results = orderedResults,
                    recommendedCombination = recommendedProfileTestCombination(orderedResults),
                    startedAtMillis = startedAtMillis,
                )
            }

        suspend fun runStage(
            combinations: List<ProfileTestCombination>,
            stageTotalCount: Int,
        ) {
            mutex.withLock { totalCount = stageTotalCount }
            onProgress(snapshot(CreateProfileTestStatus.RUNNING))
            combinations.map { combination ->
                async {
                    semaphore.withPermit {
                        val result = testCombination(draft, combination)
                        val nextState =
                            mutex.withLock {
                                testedCount += 1
                                collectedResults += result
                                val orderedResults = orderedProfileTestResults(collectedResults)
                                CreateProfileTestState(
                                    status = CreateProfileTestStatus.RUNNING,
                                    testedCount = testedCount,
                                    totalCount = totalCount,
                                    results = orderedResults,
                                    recommendedCombination = recommendedProfileTestCombination(orderedResults),
                                    startedAtMillis = startedAtMillis,
                                )
                            }
                        onProgress(nextState)
                    }
                }
            }.awaitAll()
        }

        onProgress(snapshot(CreateProfileTestStatus.RUNNING))
        runStage(NON_BASE64_PROFILE_TEST_COMBINATIONS, NON_BASE64_PROFILE_TEST_COMBINATIONS.size)
        if (shouldRunBase64Fallback(collectedResults)) {
            runStage(BASE64_PROFILE_TEST_COMBINATIONS, ORDERED_PROFILE_TEST_COMBINATIONS.size)
        }

        val finalState = snapshot(CreateProfileTestStatus.FINISHED)
        onProgress(finalState)
        finalState
    }

    private suspend fun testCombination(
        draft: CreateProfileDraft,
        combination: ProfileTestCombination,
    ): ProfileTestCaseResult {
        val profile = buildDraftProfile(draft.applyCombination(combination))
        return runCatching {
            executeProfileValidation(profile)
        }.fold(
            onSuccess = {
                ProfileTestCaseResult(
                    combination = combination,
                    success = true,
                    message = appContext.getString(
                        R.string.profile_test_case_success,
                        profileTestCombinationLabel(appContext, combination),
                    ),
                )
            },
            onFailure = { throwable ->
                ProfileTestCaseResult(
                    combination = combination,
                    success = false,
                    message = throwable.message ?: appContext.getString(R.string.profile_test_unknown_error),
                )
            },
        )
    }
}

private suspend fun validateProfile(
    engine: ImageExecutionEngine,
    profile: ApiProfile,
) {
    val result =
        engine.generate(
            ImageRequest(
                profile = profile,
                prompt = PROFILE_TEST_PROMPT,
                params = PROFILE_TEST_PARAMS,
                maxAttempts = 1,
                outerModelFallbacks = emptyList(),
            ),
        )
    if (result.images.isEmpty()) {
        error("No image returned.")
    }
}

private fun buildDraftProfile(draft: CreateProfileDraft): ApiProfile =
    ApiProfile(
        id = "profile-test",
        name = "profile-test",
        provider = "openai",
        baseUrl = draft.baseUrl.trim(),
        apiKey = draft.apiKey.trim(),
        model = if (draft.apiMode == ApiMode.RESPONSES) DEFAULT_RESPONSES_TEST_MODEL else DEFAULT_IMAGES_TEST_MODEL,
        apiMode = draft.apiMode,
        timeoutSec = DEFAULT_PROFILE_TEST_TIMEOUT_SEC,
        codexCliLikeMode = draft.codexCliLikeMode,
        responseFormatB64Json = draft.responseFormatB64Json,
    )

internal fun profileTestCombinationLabel(
    appContext: Context,
    combination: ProfileTestCombination,
): String =
    appContext.getString(
        R.string.profile_test_combination_format,
        appContext.getString(
            if (combination.apiMode == ApiMode.IMAGES) {
                R.string.mode_images
            } else {
                R.string.mode_responses
            },
        ),
        appContext.getString(
            if (combination.codexCliLikeMode) {
                R.string.profile_test_on
            } else {
                R.string.profile_test_off
            },
        ),
        appContext.getString(
            if (combination.responseFormatB64Json) {
                R.string.profile_test_on
            } else {
                R.string.profile_test_off
            },
        ),
    )

private val PROFILE_TEST_PARAMS =
    TaskParams(
        size = "1024x1024",
        quality = "low",
        count = 1,
    )

private const val PROFILE_TEST_PROMPT = "Validation image"
private const val DEFAULT_IMAGES_TEST_MODEL = "gpt-image-2"
private const val DEFAULT_RESPONSES_TEST_MODEL = "gpt-5.5"
private const val DEFAULT_PROFILE_TEST_TIMEOUT_SEC = 120
