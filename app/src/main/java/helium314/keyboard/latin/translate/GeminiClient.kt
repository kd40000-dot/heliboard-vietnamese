// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.translate

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Network requests only after the user pauses typing. Never logs text or credentials. */
internal object GeminiClient {
    private const val MODELS_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models"
    internal const val DEFAULT_MODEL = "gemini-3.1-flash-lite"

    /** Only consider text-capable Flash-Lite models, never paid-tier Pro or preview variants. */
    internal fun parseAvailableFlashLiteModels(json: String): List<String> {
        val models = JSONObject(json).optJSONArray("models") ?: return emptyList()
        val supported = mutableListOf<String>()
        for (i in 0 until models.length()) {
            val item = models.optJSONObject(i) ?: continue
            val name = item.optString("name").removePrefix("models/")
            if (!name.startsWith("gemini-") || !name.contains("flash-lite") ||
                name.contains("preview") || name.contains("image") || name.contains("exp")) continue
            val methods = item.optJSONArray("supportedGenerationMethods") ?: continue
            if ((0 until methods.length()).none { methods.optString(it) == "generateContent" }) continue
            supported.add(name)
        }
        return supported.distinct().sortedDescending()
    }

    private fun listFlashLiteModels(key: String): List<String> {
        val connection = URL("$MODELS_ENDPOINT?pageSize=1000").openConnection() as HttpURLConnection
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
            connection.disconnect()
        }
    }

    fun request(text: String, reverse: Boolean): String {
        val instruction = if (reverse) {
            "Translate Vietnamese into natural English. The Vietnamese message is from em to anh. " +
                "Preserve meaning, affection and ambiguity. Output only the English translation."
        } else {
            "Translate this English message into natural, informal Vietnamese from a man (anh) " +
                "addressing a younger woman named Tran (em). Always use anh for the speaker " +
                "and em for the addressee where pronouns are needed. Avoid formal tôi/bạn, " +
                "do not invent affection not present in the source. Preserve punctuation, " +
                "meaning and line breaks. Output only the Vietnamese translation."
        }
        return JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", instruction))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", text)))))
            .put("generationConfig", JSONObject().put("temperature", 0.2).put("maxOutputTokens", 2048))
            .toString()
    }

    fun translate(key: String, text: String, reverse: Boolean): String {
        try {
            return translateWithModel(key, text, reverse, DEFAULT_MODEL)
        } catch (missing: GeminiFailure) {
            if (missing.httpStatus != 404) throw missing
            // API model availability is specific to each key/project. Refresh only on 404.
            val models = listFlashLiteModels(key).filterNot { it == DEFAULT_MODEL }
            for (model in models) {
                try {
                    return translateWithModel(key, text, reverse, model)
                } catch (e: GeminiFailure) {
                    if (e.httpStatus != 404) throw e
                }
            }
            throw GeminiFailure(404, "NO_FLASH_LITE_MODEL")
        }
    }

    private fun translateWithModel(key: String, text: String, reverse: Boolean, model: String): String {
        val connection = URL("$MODELS_ENDPOINT/$model:generateContent").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 8000
            connection.readTimeout = 15000
            connection.doOutput = true
            connection.setRequestProperty("x-goog-api-key", key)
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(request(text, reverse).toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status != 200) {
                // Google error bodies may include project metadata. Expose only the status and a
                // small, explicit allow-list of useful error categories, never the raw body.
                val errorBody = connection.errorStream?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText().take(8192) }.orEmpty()
                val code = runCatching { JSONObject(errorBody).getJSONObject("error")
                    .optString("status") }.getOrDefault("")
                throw GeminiFailure(status, code)
            }
            val response = JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            val parts = response.getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
            val translation = (0 until parts.length()).joinToString("") {
                parts.getJSONObject(it).optString("text")
            }.trim()
            if (translation.isBlank()) throw GeminiFailure(200, "EMPTY_RESPONSE")
            return translation
        } finally {
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
