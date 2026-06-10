package com.shj56166androidimage2.app.data.network

import com.shj56166androidimage2.app.util.AppDisplayLanguage
import com.shj56166androidimage2.app.util.currentAppDisplayLanguage

private const val UNKNOWN_IMAGE_GENERATION_ERROR = "Unknown image generation error."
private val DIAGNOSTIC_LINE_REGEX = Regex("""^\d{2}-\d{2} \d{2}:\d{2}:\d{2} """)
private val HTTP_STATUS_REGEX = Regex("""\bHTTP\s+(\d{3})\b""", RegexOption.IGNORE_CASE)
private val REMOTE_IMAGE_DOWNLOAD_REGEX = Regex("""^Failed to download remote image: HTTP\s+(\d{3})$""", RegexOption.IGNORE_CASE)

internal fun formatImageGenerationError(message: String): String {
    val normalized = message.trim()
    if (normalized.isEmpty()) {
        return localizedUnknownImageGenerationError()
    }

    val (body, diagnostics) = splitErrorBodyAndDiagnostics(normalized)
    if (body.isBlank()) return normalized
    if (containsChinese(body) || isAlreadyBilingual(body)) return normalized

    val localizedBody = localizeImageGenerationError(body)
    return if (diagnostics.isEmpty()) localizedBody else listOf(localizedBody, diagnostics.joinToString("\n")).joinToString("\n")
}

internal fun formatImageGenerationThrowable(throwable: Throwable): String {
    val message = throwable.message?.takeIf { it.isNotBlank() } ?: throwable::class.java.simpleName
    return formatImageGenerationError(message)
}

internal fun formatImageGenerationErrors(messages: List<String>): List<String> =
    messages.map(::formatImageGenerationError)

private fun splitErrorBodyAndDiagnostics(message: String): Pair<String, List<String>> {
    val lines = message.lines()
    val diagnosticStart = lines.indexOfFirst(::isDiagnosticLine)
    if (diagnosticStart < 0) return message to emptyList()

    val body = lines.take(diagnosticStart).joinToString("\n").trim()
    val diagnostics = lines.drop(diagnosticStart).filter { it.isNotBlank() }
    return body to diagnostics
}

private fun isDiagnosticLine(line: String): Boolean =
    DIAGNOSTIC_LINE_REGEX.containsMatchIn(line)

private fun containsChinese(text: String): Boolean =
    text.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }

private fun isAlreadyBilingual(text: String): Boolean {
    val englishIndex = text.indexOf("English:")
    return englishIndex > 0 && containsChinese(text.substring(0, englishIndex))
}

private fun localizedUnknownImageGenerationError(): String =
    when (currentAppDisplayLanguage()) {
        AppDisplayLanguage.EN -> "Image generation failed."
        AppDisplayLanguage.ZH_HANS -> "生图失败。"
        AppDisplayLanguage.ZH_HANT -> "生圖失敗。"
    }

private fun localizeImageGenerationError(message: String): String {
    val language = currentAppDisplayLanguage()

    REMOTE_IMAGE_DOWNLOAD_REGEX.matchEntire(message)?.groupValues?.getOrNull(1)?.let { code ->
        return when (language) {
            AppDisplayLanguage.EN -> "Failed to download remote image (HTTP $code)."
            AppDisplayLanguage.ZH_HANS -> "远程图片下载失败（HTTP $code）。"
            AppDisplayLanguage.ZH_HANT -> "遠程圖片下載失敗（HTTP $code）。"
        }
    }

    HTTP_STATUS_REGEX.find(message)?.groupValues?.getOrNull(1)?.let { code ->
        return localizeHttpStatusError(code, language)
    }

    val lowered = message.lowercase()
    return when {
        message == "All concurrent response requests failed." ->
            when (language) {
                AppDisplayLanguage.EN -> "All concurrent Responses API requests failed."
                AppDisplayLanguage.ZH_HANS -> "并发 Responses API 请求全部失败。"
                AppDisplayLanguage.ZH_HANT -> "並發 Responses API 請求全部失敗。"
            }
        message == "All concurrent image requests failed." ->
            when (language) {
                AppDisplayLanguage.EN -> "All concurrent image requests failed."
                AppDisplayLanguage.ZH_HANS -> "并发生图请求全部失败。"
                AppDisplayLanguage.ZH_HANT -> "並發生圖請求全部失敗。"
            }
        message == "fal.ai returned no images." ->
            when (language) {
                AppDisplayLanguage.EN -> "fal.ai returned no images."
                AppDisplayLanguage.ZH_HANS -> "fal.ai 未返回图片结果。"
                AppDisplayLanguage.ZH_HANT -> "fal.ai 未返回圖片結果。"
            }
        message == "No image payload found in /responses result." ->
            when (language) {
                AppDisplayLanguage.EN -> "No image payload found in the /responses result."
                AppDisplayLanguage.ZH_HANS -> "Responses API 未返回可识别的图片数据。"
                AppDisplayLanguage.ZH_HANT -> "Responses API 未返回可識別的圖片數據。"
            }
        message == "No image payload found in Images API result." ->
            when (language) {
                AppDisplayLanguage.EN -> "No image payload found in the Images API result."
                AppDisplayLanguage.ZH_HANS -> "Images API 未返回可识别的图片数据。"
                AppDisplayLanguage.ZH_HANT -> "Images API 未返回可識別的圖片數據。"
            }
        message == "Remote task failed." ->
            when (language) {
                AppDisplayLanguage.EN -> "The remote task failed."
                AppDisplayLanguage.ZH_HANS -> "远程任务执行失败。"
                AppDisplayLanguage.ZH_HANT -> "遠程任務執行失敗。"
            }
        message == "Task polling timed out." ->
            when (language) {
                AppDisplayLanguage.EN -> "Task polling timed out."
                AppDisplayLanguage.ZH_HANS -> "任务轮询超时。"
                AppDisplayLanguage.ZH_HANT -> "任務輪詢超時。"
            }
        message == "Image execution failed." ->
            when (language) {
                AppDisplayLanguage.EN -> "Image execution failed."
                AppDisplayLanguage.ZH_HANS -> "生图执行失败。"
                AppDisplayLanguage.ZH_HANT -> "生圖執行失敗。"
            }
        message == "Remote image body was empty." ->
            when (language) {
                AppDisplayLanguage.EN -> "The remote image response body was empty."
                AppDisplayLanguage.ZH_HANS -> "远程图片响应体为空。"
                AppDisplayLanguage.ZH_HANT -> "遠程圖片響應體為空。"
            }
        message == "Task resume is only supported for custom async providers." ->
            when (language) {
                AppDisplayLanguage.EN -> "Task resume is only supported for custom async providers."
                AppDisplayLanguage.ZH_HANS -> "仅自定义异步 Provider 支持恢复远程任务。"
                AppDisplayLanguage.ZH_HANT -> "僅自定義異步 Provider 支援恢復遠程任務。"
            }
        "timeout" in lowered ->
            when (language) {
                AppDisplayLanguage.EN -> "The request timed out."
                AppDisplayLanguage.ZH_HANS -> "请求超时。"
                AppDisplayLanguage.ZH_HANT -> "請求超時。"
            }
        "temporarily" in lowered ->
            when (language) {
                AppDisplayLanguage.EN -> "The service is temporarily unavailable."
                AppDisplayLanguage.ZH_HANS -> "服务暂时不可用。"
                AppDisplayLanguage.ZH_HANT -> "服務暫時不可用。"
            }
        listOf("socket closed", "connection reset", "failed to connect", "connection refused", "network").any(lowered::contains) ->
            when (language) {
                AppDisplayLanguage.EN -> "The network connection failed."
                AppDisplayLanguage.ZH_HANS -> "网络连接失败。"
                AppDisplayLanguage.ZH_HANT -> "網絡連線失敗。"
            }
        listOf("model", "not found", "unsupported", "invalid").any(lowered::contains) ->
            when (language) {
                AppDisplayLanguage.EN -> "The model or request parameters are invalid."
                AppDisplayLanguage.ZH_HANS -> "模型或请求参数无效。"
                AppDisplayLanguage.ZH_HANT -> "模型或請求參數無效。"
            }
        else ->
            when (language) {
                AppDisplayLanguage.EN -> "The image request failed."
                AppDisplayLanguage.ZH_HANS -> "生图请求失败。"
                AppDisplayLanguage.ZH_HANT -> "生圖請求失敗。"
            }
    }
}

private fun localizeHttpStatusError(
    code: String,
    language: AppDisplayLanguage,
): String =
    when (code) {
        "401", "403" ->
            when (language) {
                AppDisplayLanguage.EN -> "Authentication failed (HTTP $code)."
                AppDisplayLanguage.ZH_HANS -> "接口鉴权失败（HTTP $code）。"
                AppDisplayLanguage.ZH_HANT -> "接口鑑權失敗（HTTP $code）。"
            }
        "404" ->
            when (language) {
                AppDisplayLanguage.EN -> "The endpoint or resource was not found (HTTP $code)."
                AppDisplayLanguage.ZH_HANS -> "接口地址或资源不存在（HTTP $code）。"
                AppDisplayLanguage.ZH_HANT -> "接口地址或資源不存在（HTTP $code）。"
            }
        "408" ->
            when (language) {
                AppDisplayLanguage.EN -> "The request timed out (HTTP $code)."
                AppDisplayLanguage.ZH_HANS -> "接口请求超时（HTTP $code）。"
                AppDisplayLanguage.ZH_HANT -> "接口請求超時（HTTP $code）。"
            }
        "409" ->
            when (language) {
                AppDisplayLanguage.EN -> "The request conflicted with the current state (HTTP $code)."
                AppDisplayLanguage.ZH_HANS -> "接口请求冲突（HTTP $code）。"
                AppDisplayLanguage.ZH_HANT -> "接口請求衝突（HTTP $code）。"
            }
        "429" ->
            when (language) {
                AppDisplayLanguage.EN -> "The request was rate limited (HTTP $code)."
                AppDisplayLanguage.ZH_HANS -> "请求过于频繁（HTTP $code）。"
                AppDisplayLanguage.ZH_HANT -> "請求過於頻繁（HTTP $code）。"
            }
        "500", "502", "503", "504" ->
            when (language) {
                AppDisplayLanguage.EN -> "The upstream service failed (HTTP $code)."
                AppDisplayLanguage.ZH_HANS -> "上游服务请求失败（HTTP $code）。"
                AppDisplayLanguage.ZH_HANT -> "上游服務請求失敗（HTTP $code）。"
            }
        else ->
            when (language) {
                AppDisplayLanguage.EN -> "The request failed (HTTP $code)."
                AppDisplayLanguage.ZH_HANS -> "接口请求失败（HTTP $code）。"
                AppDisplayLanguage.ZH_HANT -> "接口請求失敗（HTTP $code）。"
            }
    }
