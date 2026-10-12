// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.translate

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.IOException

/** Network requests only after the user pauses typing. Never logs text or credentials. */
internal object GeminiClient {
    private const val MODELS_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models"
    internal const val DEFAULT_MODEL = "gemini-3.1-flash-lite"

    /** Stable text-only low-latency models; avoid Pro, preview, image and experimental models. */
    internal fun isValidFlashLiteModel(name: String): Boolean =
        name.matches(Regex("gemini-[0-9]+(?:\\.[0-9]+)?-flash-lite"))

    /** Only consider text-capable Flash-Lite models, never paid-tier Pro or preview variants. */
    internal fun parseAvailableFlashLiteModels(json: String): List<String> {
        val models = JSONObject(json).optJSONArray("models") ?: return emptyList()
        val supported = mutableListOf<String>()
        for (i in 0 until models.length()) {
            val item = models.optJSONObject(i) ?: continue
            val name = item.optString("name").removePrefix("models/")
            if (!isValidFlashLiteModel(name)) continue
            val methods = item.optJSONArray("supportedGenerationMethods") ?: continue
            if ((0 until methods.length()).none { methods.optString(it) == "generateContent" }) continue
            supported.add(name)
        }
        return supported.distinct().sortedDescending()
    }

    private fun listFlashLiteModels(key: String, cancellation: TranslationCancellation?): List<String> {
        val connection = URL("$MODELS_ENDPOINT?pageSize=1000").openConnection() as HttpURLConnection
        cancellation?.register(connection)
        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("x-goog-api-key", key)
            val status = connection.responseCode
            if (status != 200) throw GeminiFailure(status, "MODEL_DISCOVERY_FAILED")
            return parseAvailableFlashLiteModels(
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            )
        } finally {
            cancellation?.unregister(connection)
            connection.disconnect()
        }
    }

    fun request(text: String, reverse: Boolean, model: String = DEFAULT_MODEL): String {
        val instruction = if (reverse) {
            "Translate this Vietnamese message from em to anh into natural English. " +
                "Preserve meaning, affection, punctuation and line breaks. Output only the translation."
        } else {
            "Translate into natural, informal Vietnamese: the male speaker is anh and " +
                "the recipient Tran is em. Use anh/em where needed, not tôi/bạn. " +
                "Preserve meaning, tone, punctuation and line breaks without added affection. " +
                "Output only the translation."
        }
        val tokenLimit = (text.length * 2 + 96).coerceIn(160, 2048)
        val config = JSONObject()
            .put("temperature", 0.1)
            .put("maxOutputTokens", tokenLimit)
        // Gemini 3 Flash-Lite accepts minimal thinking; Gemini 2.5 Flash-Lite
        // does not accept thinkingLevel (and defaults to zero thinking tokens).
        if (!model.startsWith("gemini-2.5-")) {
            config.put("thinkingConfig", JSONObject().put("thinkingLevel", "minimal"))
        }
        return JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", instruction))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", text)))))
            .put("generationConfig", config)
            .toString()
    }

    /** Each SSE line contains an incremental (not cumulative) text chunk. */
    internal fun parseStreamText(line: String): String {
        if (!line.startsWith("data:")) return ""
        return runCatching {
            val candidate = JSONObject(line.substringAfter("data:").trim())
                .optJSONArray("candidates")?.optJSONObject(0) ?: return@runCatching ""
            val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
                ?: return@runCatching ""
            buildString {
                for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i) ?: continue
                    // Do not show thought tokens or expose any model-internal output.
                    if (!part.optBoolean("thought", false)) append(part.optString("text"))
                }
            }
        }.getOrDefault("")
    }

    fun translate(
        key: String, text: String, reverse: Boolean, cache: TranslationModelCache? = null,
        cancellation: TranslationCancellation? = null, onPartial: ((String) -> Unit)? = null
    ): String {
        val cachedModel = cache?.read(key)
        val firstModel = cachedModel ?: DEFAULT_MODEL
        try {
            val translated = translateWithModel(key, text, reverse, firstModel, cancellation, onPartial)
            cache?.save(key, firstModel)
            return translated
        } catch (missing: GeminiFailure) {
            if (missing.httpStatus != 404) throw missing
            cache?.invalidate(key)
        }

        // A 404 is a model error. Network timeouts are NOT retried automatically:
        // repeating a POST may consume the user's limited free request quota.
        val available = listFlashLiteModels(key, cancellation)
        val candidates = (listOf(DEFAULT_MODEL) + available).distinct()
            .filterNot { it == firstModel }
        for (model in candidates) {
            if (!isValidFlashLiteModel(model)) continue
            cancellation?.check()
            try {
                val translated = translateWithModel(key, text, reverse, model, cancellation, onPartial)
                cache?.save(key, model)
                return translated
            } catch (missing: GeminiFailure) {
                if (missing.httpStatus != 404) throw missing
            }
        }
        throw GeminiFailure(404, "NO_FLASH_LITE_MODEL")
    }

    private fun translateWithModel(
        key: String, text: String, reverse: Boolean, model: String,
        cancellation: TranslationCancellation?, onPartial: ((String) -> Unit)?
    ): String {
        val connection = URL("$MODELS_ENDPOINT/$model:streamGenerateContent?alt=sse")
            .openConnection() as HttpURLConnection
        cancellation?.register(connection)
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 12000
            // A read timeout measures network inactivity, not the total response time.
            // The UI has a separate end-to-end watchdog.
            connection.readTimeout = 45000
            connection.doOutput = true
            connection.setRequestProperty("x-goog-api-key", key)
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "text/event-stream")
            connection.setRequestProperty("Accept-Encoding", "identity")
            val payload = request(text, reverse, model).toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(payload.size)
            cancellation?.check()
            connection.outputStream.use { it.write(payload) }
            cancellation?.check()
            val status = connection.responseCode
            if (status != 200) {
                val errorBody = connection.errorStream?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText().take(8192) }.orEmpty()
                val code = runCatching {
                    JSONObject(errorBody).getJSONObject("error").optString("status")
                }.getOrDefault("")
                throw GeminiFailure(status, code)
            }
            val translated = StringBuilder()
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                while (true) {
                    cancellation?.check()
                    val line = reader.readLine() ?: break
                    val chunk = parseStreamText(line)
                    if (chunk.isNotEmpty()) {
                        translated.append(chunk)
                        if (translated.length > 20000) throw IOException("Translation too long")
                        onPartial?.invoke(translated.toString())
                    }
                }
            }
            cancellation?.check()
            val result = translated.toString().trim()
            if (result.isBlank()) throw GeminiFailure(200, "EMPTY_RESPONSE")
            return result
        } finally {
            cancellation?.unregister(connection)
            connection.disconnect()
        }
    }
}

/** Never contains the API key, source text, response body, or account details. */
internal class GeminiFailure(val httpStatus: Int, val apiStatus: String) : Exception() {
    fun userMessage(): String = when {
        httpStatus == 400 && apiStatus == "FAILED_PRECONDITION" ->
            "Gemini HTTP 400: Free tier unavailable for this project or region."
        httpStatus == 400 -> "Gemini HTTP 400: Request rejected (INVALID_ARGUMENT)."
        httpStatus == 401 -> "Gemini HTTP 401: Invalid API key."
        httpStatus == 403 -> "Gemini HTTP 403: API key or project access denied."
        httpStatus == 404 -> "Gemini HTTP 404: No compatible Flash-Lite model for this API key."
        httpStatus == 429 -> "Gemini HTTP 429: Free quota or rate limit reached."
        httpStatus in 500..599 -> "Gemini HTTP $httpStatus: Google server temporarily unavailable."
        httpStatus == 200 -> "Gemini returned no translation. Try different text."
        else -> "Gemini HTTP $httpStatus: Request failed ($apiStatus)."
    }
}

/** Disconnects a stale in-flight request when typing resumes or the UI closes. */
internal class TranslationCancellation {
    @Volatile private var cancelled = false
    private var connection: HttpURLConnection? = null

    @Synchronized fun register(active: HttpURLConnection) {
        if (cancelled) {
            active.disconnect()
            throw IOException("Request cancelled")
        }
        connection = active
    }

    @Synchronized fun unregister(active: HttpURLConnection) {
        if (connection === active) connection = null
    }

    @Synchronized fun cancel() {
        cancelled = true
        connection?.disconnect()
        connection = null
    }

    fun check() {
        if (cancelled) throw IOException("Request cancelled")
    }
}
