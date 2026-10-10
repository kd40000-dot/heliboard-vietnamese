// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.translate

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Network requests only after the user pauses typing. Never logs text or credentials. */
internal object GeminiClient {
    private const val ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"

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
        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 12000
            connection.readTimeout = 30000
            connection.doOutput = true
            connection.setRequestProperty("x-goog-api-key", key)
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(request(text, reverse).toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status != 200) throw TranslationFailure(status)
            val response = JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            val parts = response.getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
            val translation = (0 until parts.length()).joinToString("") {
                parts.getJSONObject(it).optString("text")
            }.trim()
            if (translation.isBlank()) throw TranslationFailure(0)
            return translation
        } finally {
            connection.disconnect()
        }
    }
}
