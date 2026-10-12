// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.settings

import org.junit.Test
import kotlin.test.*

class BackupDefaultsTest {
    @Test fun defaultsMatchSuppliedKeyboardConfiguration() {
        assertEquals("light", Defaults.PREF_THEME_COLORS)
        assertEquals("Dracula", Defaults.PREF_THEME_COLORS_NIGHT)
        assertFalse(Defaults.PREF_BLOCK_POTENTIALLY_OFFENSIVE)
        assertTrue(Defaults.PREF_SAVE_SUBTYPE_PER_APP)
        assertEquals(
            "en-US§SupportTouchPositionCorrection,TrySuppressingImeSwitcher;es-US§KeyboardLayoutSet=MAIN:qwerty+;vi§CombiningRules=vi_telex",
            Defaults.PREF_ENABLED_SUBTYPES
        )
        assertEquals("en-US§SupportTouchPositionCorrection,TrySuppressingImeSwitcher", Defaults.PREF_SELECTED_SUBTYPE)
        assertTrue(Defaults.PREF_TOOLBAR_KEYS.contains("CLIPBOARD:true"))
        assertTrue(Defaults.PREF_TOOLBAR_KEYS.contains("EMOJI:true"))
    }
}
