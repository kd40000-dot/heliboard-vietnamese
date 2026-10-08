// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.event

import android.view.inputmethod.InputMethodSubtype
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.WordComposer
import helium314.keyboard.latin.utils.SubtypeLocaleUtils
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class TelexChainTest {
    private fun CombinerChain.type(keys: String) {
        for (key in keys) {
            val event = Event.createEventForCodePointFromUnknownSource(key.code)
            applyProcessedEvent(processEvent(arrayListOf(), event))
        }
    }

    @Test fun typedKeysAndDeletion() {
        val chain = CombinerChain("", "vi_telex")
        chain.type("aas")
        assertEquals("ấ", chain.composingWordWithCombiningFeedback.toString())
        chain.applyProcessedEvent(Event.createSoftwareKeypressEvent(
            Event.NOT_A_CODE_POINT, KeyCode.DELETE, 0, 0, 0, false))
        assertEquals("", chain.composingWordWithCombiningFeedback.toString())
        chain.type("as")
        assertEquals("á", chain.composingWordWithCombiningFeedback.toString())
        chain.reset()
        chain.type("s")
        assertEquals("s", chain.composingWordWithCombiningFeedback.toString())
    }

    @Test fun replayDoesNotConvertExistingText() {
        val composer = WordComposer()
        composer.restartCombining("vi_telex")
        val points = "ass wood".codePoints().toArray()
        composer.setComposingWord(points, IntArray(points.size * 2))
        assertEquals("ass wood", composer.typedWord)
        composer.setBatchInputWord("ass wood")
        assertEquals("ass wood", composer.typedWord)
    }

    @Test fun unchangedOtherLanguagesAndTextEvents() {
        val ordinary = CombinerChain("", "")
        ordinary.type("aas")
        assertEquals("aas", ordinary.composingWordWithCombiningFeedback.toString())
        val vietnamese = CombinerChain("", "vi_telex")
        vietnamese.applyProcessedEvent(Event.createSoftwareTextEvent("aas", KeyCode.MULTIPLE_CODE_POINTS))
        assertEquals("aas", vietnamese.composingWordWithCombiningFeedback.toString())
    }

    @Test fun decomposedEditorTextUsesNormalizedOffsets() {
        val chain = CombinerChain("a\u0302", "vi_telex")
        chain.type("s")
        assertEquals("ấ", chain.composingWordWithCombiningFeedback.toString())
    }

    @Test fun vietnameseCustomLayoutsAndOtherLanguages() {
        fun rules(tag: String, extra: String = "") = SubtypeLocaleUtils.getCombiningRulesExtraValue(
            InputMethodSubtype.InputMethodSubtypeBuilder().setLanguageTag(tag)
                .setSubtypeExtraValue(extra).build())
        assertEquals("vi_telex", rules("vi"))
        assertEquals("vi_telex", rules("vi-VN", "KeyboardLayoutSet=MAIN:azerty"))
        assertEquals(null, rules("en-US"))
        assertEquals("none", rules("vi", "CombiningRules=none"))
    }
}
