// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.translate

import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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

    @Test fun reverseDirectionChangesInstructions() {
        val body = JSONObject(GeminiClient.request("Em nhớ anh.", true))
        val system = body.getJSONObject("systemInstruction").getJSONArray("parts")
            .getJSONObject(0).getString("text")
        assertTrue(system.contains("natural English"))
        assertTrue(system.contains("em to anh"))
    }
}
