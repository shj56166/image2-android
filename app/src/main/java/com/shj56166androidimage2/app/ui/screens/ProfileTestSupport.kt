package com.shj56166androidimage2.app.ui.screens

import com.shj56166androidimage2.app.data.model.ApiMode

data class CreateProfileDraft(
    val name: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",
    val apiMode: ApiMode = ApiMode.IMAGES,
    val codexCliLikeMode: Boolean = false,
    val responseFormatB64Json: Boolean = false,
)

data class ProfileTestCombination(
    val apiMode: ApiMode,
    val codexCliLikeMode: Boolean,
    val responseFormatB64Json: Boolean,
)

data class ProfileTestCaseResult(
    val combination: ProfileTestCombination,
    val success: Boolean,
    val message: String,
)

enum class CreateProfileTestStatus {
    IDLE,
    RUNNING,
    FINISHED,
}

data class CreateProfileTestState(
    val status: CreateProfileTestStatus = CreateProfileTestStatus.IDLE,
    val testedCount: Int = 0,
    val totalCount: Int = NON_BASE64_PROFILE_TEST_COMBINATIONS.size,
    val results: List<ProfileTestCaseResult> = emptyList(),
    val recommendedCombination: ProfileTestCombination? = null,
    val startedAtMillis: Long? = null,
) {
    val isRunning: Boolean
        get() = status == CreateProfileTestStatus.RUNNING
}

internal val ORDERED_PROFILE_TEST_COMBINATIONS =
    listOf(
        ProfileTestCombination(ApiMode.IMAGES, codexCliLikeMode = false, responseFormatB64Json = false),
        ProfileTestCombination(ApiMode.RESPONSES, codexCliLikeMode = false, responseFormatB64Json = false),
        ProfileTestCombination(ApiMode.IMAGES, codexCliLikeMode = true, responseFormatB64Json = false),
        ProfileTestCombination(ApiMode.RESPONSES, codexCliLikeMode = true, responseFormatB64Json = false),
        ProfileTestCombination(ApiMode.IMAGES, codexCliLikeMode = false, responseFormatB64Json = true),
        ProfileTestCombination(ApiMode.RESPONSES, codexCliLikeMode = false, responseFormatB64Json = true),
        ProfileTestCombination(ApiMode.IMAGES, codexCliLikeMode = true, responseFormatB64Json = true),
        ProfileTestCombination(ApiMode.RESPONSES, codexCliLikeMode = true, responseFormatB64Json = true),
    )

internal val NON_BASE64_PROFILE_TEST_COMBINATIONS = ORDERED_PROFILE_TEST_COMBINATIONS.take(4)
internal val BASE64_PROFILE_TEST_COMBINATIONS = ORDERED_PROFILE_TEST_COMBINATIONS.drop(4)

internal fun CreateProfileDraft.applyCombination(combination: ProfileTestCombination): CreateProfileDraft =
    copy(
        apiMode = combination.apiMode,
        codexCliLikeMode = combination.codexCliLikeMode,
        responseFormatB64Json = combination.responseFormatB64Json,
    )

internal fun shouldRunBase64Fallback(results: List<ProfileTestCaseResult>): Boolean =
    NON_BASE64_PROFILE_TEST_COMBINATIONS.all { combination ->
        results.any { result -> result.combination == combination && result.success.not() }
    }

internal fun recommendedProfileTestCombination(results: List<ProfileTestCaseResult>): ProfileTestCombination? =
    ORDERED_PROFILE_TEST_COMBINATIONS.firstOrNull { combination ->
        results.any { result -> result.combination == combination && result.success }
    }

internal fun orderedProfileTestResults(results: List<ProfileTestCaseResult>): List<ProfileTestCaseResult> =
    ORDERED_PROFILE_TEST_COMBINATIONS.mapNotNull { combination ->
        results.firstOrNull { it.combination == combination }
    }
