// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.translate

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * Remembers the model that actually worked for this key. This avoids a 404 +
 * model-list call on every translation for keys that cannot use DEFAULT_MODEL.
 *
 * Only a SHA-256 fingerprint of the high-entropy API key and a public model ID
 * are stored. No messages or API credentials are cached; exclude from backups.
 */
internal class TranslationModelCache(context: Context) {
    private val file = File(context.noBackupFilesDir, "gemini-selected-model")

    private fun fingerprint(apiKey: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(apiKey.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    @Synchronized fun read(apiKey: String): String? = runCatching {
        val lines = file.readLines(Charsets.UTF_8)
        if (lines.size != 2 || lines[0] != fingerprint(apiKey)) null
        else lines[1].takeIf { GeminiClient.isValidFlashLiteModel(it) }
    }.getOrNull()

    @Synchronized fun save(apiKey: String, model: String) {
        if (!GeminiClient.isValidFlashLiteModel(model)) return
        runCatching {
            file.writeText(fingerprint(apiKey) + "\n" + model + "\n", Charsets.UTF_8)
        }
    }

    @Synchronized fun invalidate(apiKey: String) {
        if (read(apiKey) != null) file.delete()
    }
}
