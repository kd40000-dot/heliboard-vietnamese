// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.translate

import android.view.inputmethod.ExtractedText
import android.view.inputmethod.InputConnection
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
class DeepLClientTest {
    @Test fun freeAndProEndpoints() {
        assertEquals("https://api-free.deepl.com/v2/translate", DeepLClient.endpoint("test:fx"))
        assertEquals("https://api.deepl.com/v2/translate", DeepLClient.endpoint("test"))
    }

    @Test fun requestEscapesTextWithoutSendingEditorContext() {
        val text = "Hello \"friend\"\nHow are you? 👋"
        val body = JSONObject(DeepLClient.request(text, "VI"))
        assertEquals(3, body.length())
        assertEquals(text, body.getJSONArray("text").getString(0))
        assertEquals("EN", body.getString("source_lang"))
        assertEquals("VI", body.getString("target_lang"))
    }

    @Test fun translatesAuthenticatedUtf8AndClosesConnection() {
        val connection = mock(HttpURLConnection::class.java)
        val output = ByteArrayOutputStream()
        `when`(connection.outputStream).thenReturn(output)
        `when`(connection.responseCode).thenReturn(200)
        `when`(connection.inputStream).thenReturn(ByteArrayInputStream("""{"translations":[{"text":"Xin chào"}]}""".toByteArray()))
        assertEquals("Xin chào", DeepLClient.translate("test:fx", "Hello", "VI", connection))
        verify(connection).setRequestProperty("Authorization", "DeepL-Auth-Key test:fx")
        verify(connection).setInstanceFollowRedirects(false)
        verify(connection).disconnect()
        assertEquals("Hello", JSONObject(output.toString("UTF-8")).getJSONArray("text").getString(0))
    }

    @Test fun apiErrorsDoNotReturnErrorBodyAsTranslation() {
        for (status in listOf(403, 429, 456, 500)) {
            val connection = mock(HttpURLConnection::class.java)
            `when`(connection.outputStream).thenReturn(ByteArrayOutputStream())
            `when`(connection.responseCode).thenReturn(status)
            assertEquals(status, assertFailsWith<TranslationFailure> {
                DeepLClient.translate("key", "hello", "VI", connection)
            }.status)
            verify(connection).disconnect()
        }
    }

    @Test fun changedDestinationOrSelectionFailsSnapshotMatch() {
        val ic = mock(InputConnection::class.java)
        val extracted = ExtractedText().apply { selectionStart = 5; selectionEnd = 5 }
        `when`(ic.getExtractedText(any(), anyInt())).thenReturn(extracted)
        `when`(ic.getTextBeforeCursor(128, 0)).thenReturn("Hello")
        `when`(ic.getTextAfterCursor(128, 0)).thenReturn("")
        val original = EditorSnapshot.capture(ic)
        assertNotNull(original)
        assertEquals(original, EditorSnapshot.capture(ic))
        extracted.selectionStart = 10; extracted.selectionEnd = 10
        assertNotEquals(original, EditorSnapshot.capture(ic))
        extracted.selectionStart = 5; extracted.selectionEnd = 5
        `when`(ic.getTextBeforeCursor(128, 0)).thenReturn("Other")
        assertNotEquals(original, EditorSnapshot.capture(ic))
        `when`(ic.getExtractedText(any(), anyInt())).thenReturn(null)
        assertNull(EditorSnapshot.capture(ic))
    }
}
