// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.event

import kotlin.test.Test
import kotlin.test.assertEquals

class VietnameseTelexTest {
    private val engine = VietnameseTelex()

    private fun type(keys: String): String {
        var text = ""
        for (key in keys) {
            val (delete, insert) = engine.getActions(text, key.toString())
            text = text.dropLast(delete) + insert
        }
        return text
    }

    @Test fun vowelsAndStroke() {
        mapOf("aa" to "â", "aw" to "ă", "ee" to "ê", "oo" to "ô",
            "ow" to "ơ", "uw" to "ư", "uow" to "ươ", "dd" to "đ",
            "w" to "ư", "W" to "Ư").forEach { (keys, expected) ->
            assertEquals(expected, type(keys), keys)
        }
    }

    @Test fun tonesAndVietnameseWords() {
        mapOf("as" to "á", "af" to "à", "ar" to "ả", "ax" to "ã", "aj" to "ạ",
            "tieengs" to "tiếng", "Vieetj" to "Việt", "ddwowngf" to "đường",
            "nguowif" to "người", "quas" to "quá", "gias" to "giá",
            "TUOWRNG" to "TƯỞNG", "hoaf" to "hoà",
            "thuese" to "thuế", "dduocwj" to "được").forEach { (keys, expected) ->
            assertEquals(expected, type(keys), keys)
        }
    }

    @Test fun repeatedKeysAndCancel() {
        mapOf("aaa" to "aa", "ddd" to "dd", "ass" to "as", "ww" to "w",
            "aasz" to "a", "asf" to "à", "loio" to "lôi",
            "loioo" to "loio").forEach { (keys, expected) ->
            assertEquals(expected, type(keys), keys)
        }
    }

    @Test fun boundariesAndLiteralText() {
        assertEquals("xin chào, Việt!", type("xin chaof, Vieetj!"))
        // Multi-character inserts must bypass Telex.
        assertEquals(0 to "aas", engine.getActions("", "aas"))
        assertEquals("a s", type("a s"))
        assertEquals("a.s", type("a.s"))
    }
}
