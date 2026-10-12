// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.translate

import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
class GeminiClientTest {
    @Test fun englishToVietnameseIncludesConversationContext() {
        val body = JSONObject(GeminiClient.request("I miss you.", false))
        val system = body.getJSONObject("systemInstruction").getJSONArray("parts")
            .getJSONObject(0).getString("text")
        assertTrue(system.contains("anh"))
        assertTrue(system.contains("em"))
        assertTrue(system.contains("Tran"))
        val contents = body.getJSONArray("contents").getJSONObject(0)
        assertEquals("I miss you.", contents.getJSONArray("parts").getJSONObject(0).getString("text"))
        assertFalse(body.toString().contains("API_KEY"))
    }

    @Test fun displaysUsefulErrorsWithoutExposingCredentials() {
        assertTrue(GeminiFailure(403, "PERMISSION_DENIED").userMessage().contains("403"))
        assertTrue(GeminiFailure(429, "RESOURCE_EXHAUSTED").userMessage().contains("quota"))
        assertTrue(GeminiFailure(404, "NOT_FOUND").userMessage().contains("model"))
        assertTrue(GeminiFailure(400, "FAILED_PRECONDITION").userMessage().contains("region"))
        assertFalse(GeminiFailure(403, "PERMISSION_DENIED").userMessage().contains("API_KEY"))
    }

    @Test fun parsesOnlyTextCapableStableFlashLiteModels() {
        val response = """
            {
              "models": [
                {"name":"models/gemini-3.1-flash-lite","supportedGenerationMethods":["generateContent"]},
                {"name":"models/gemini-2.5-flash-lite","supportedGenerationMethods":["generateContent"]},
                {"name":"models/gemini-3.1-flash-lite-preview","supportedGenerationMethods":["generateContent"]},
                {"name":"models/gemini-3.1-flash-lite-image","supportedGenerationMethods":["generateContent"]},
                {"name":"models/gemini-3.5-pro","supportedGenerationMethods":["generateContent"]},
                {"name":"models/gemini-3.1-flash-lite-incompatible","supportedGenerationMethods":["embedContent"]},
                {"name":"models/gemini-3.1-flash-lite","supportedGenerationMethods":["generateContent"]}
              ]
            }
        """.trimIndent()
        assertEquals(
            listOf("gemini-3.1-flash-lite", "gemini-2.5-flash-lite"),
            GeminiClient.parseAvailableFlashLiteModels(response)
        )
        assertEquals("gemini-3.1-flash-lite", GeminiClient.DEFAULT_MODEL)
        assertTrue(GeminiClient.parseAvailableFlashLiteModels("{}").isEmpty())
    }

    @Test fun minimalThinkingOnlyForGemini3() {
        val request = JSONObject(GeminiClient.request("Hello", false))
        val config = request.getJSONObject("generationConfig")
        assertEquals("minimal", config.getJSONObject("thinkingConfig").getString("thinkingLevel"))
        assertEquals(160, config.getInt("maxOutputTokens"))
        val fallback = JSONObject(GeminiClient.request("Hello", false, "gemini-2.5-flash-lite"))
        assertFalse(fallback.getJSONObject("generationConfig").has("thinkingConfig"))
    }

    @Test fun cacheUsesApiKeyFingerprintAndRejectsUnsupportedModels() {
        val cache = TranslationModelCache(RuntimeEnvironment.getApplication())
        cache.save("test-key-one", "gemini-3.1-flash-lite")
        assertEquals("gemini-3.1-flash-lite", cache.read("test-key-one"))
        assertNull(cache.read("test-key-two"))
        cache.save("test-key-one", "gemini-3.1-pro")
        assertEquals("gemini-3.1-flash-lite", cache.read("test-key-one"))
        cache.invalidate("test-key-two")
        assertEquals("gemini-3.1-flash-lite", cache.read("test-key-one"))
        cache.invalidate("test-key-one")
        assertNull(cache.read("test-key-one"))
    }

    @Test fun reverseDirectionChangesInstructions() {
        val body = JSONObject(GeminiClient.request("Em nhớ anh.", true))
        val system = body.getJSONObject("systemInstruction").getJSONArray("parts")
            .getJSONObject(0).getString("text")
        assertTrue(system.contains("natural English"))
        assertTrue(system.contains("em to anh"))
    }
}
