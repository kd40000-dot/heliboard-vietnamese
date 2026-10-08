// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.translate

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** No editor context, credentials or translated text are logged. */
internal object DeepLClient {
    fun endpoint(key: String) = if (key.endsWith(":fx"))
        "https://api-free.deepl.com/v2/translate" else "https://api.deepl.com/v2/translate"

    fun request(text: String, target: String): String = JSONObject()
        .put("text", JSONArray().put(text)).put("source_lang", "EN")
        .put("target_lang", target).toString()

    fun translate(key: String, text: String, target: String,
        connection: HttpURLConnection = URL(endpoint(key)).openConnection() as HttpURLConnection): String {
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "DeepL-Auth-Key $key")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(request(text, target).toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status != 200) throw TranslationFailure(status)
            val result = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return JSONObject(result).getJSONArray("translations").getJSONObject(0).getString("text")
                .takeIf { it.isNotBlank() } ?: throw TranslationFailure(0)
        } finally {
            connection.disconnect()
        }
    }
}
internal class TranslationFailure(val status: Int) : Exception()
