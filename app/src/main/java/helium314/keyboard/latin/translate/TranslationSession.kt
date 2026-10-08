// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.translate

import android.content.Intent
import android.os.SystemClock
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodSubtype
import android.widget.Toast
import helium314.keyboard.compat.locale
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.utils.SubtypeSettings
import java.util.UUID

/** In-process handoff. Results can only return once to the unchanged originating editor. */
object TranslationSession {
    const val EDITOR = "helium314.translate"
    private data class Session(val token: String, val packageName: String, val fieldId: Int,
        val fieldName: String?, val snapshot: EditorSnapshot,
        val subtype: InputMethodSubtype, val started: Long = SystemClock.elapsedRealtime(),
        var entered: Boolean = false, var completed: Boolean = false, var result: String? = null)
    private var session: Session? = null

    @JvmStatic fun isEditor(ime: LatinIME, info: EditorInfo?) =
        info?.packageName == ime.packageName && info.privateImeOptions == EDITOR

    @JvmStatic fun launch(ime: LatinIME) {
        if (isEditor(ime, ime.currentInputEditorInfo)) return
        val info = ime.currentInputEditorInfo ?: return
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        if ((info.inputType and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT ||
            variation in listOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) {
            Toast.makeText(ime, R.string.translate_text_only, Toast.LENGTH_SHORT).show(); return
        }
        val connection = ime.currentInputConnection ?: return
        connection.finishComposingText()
        val snapshot = EditorSnapshot.capture(connection) ?: run {
            Toast.makeText(ime, R.string.translate_editor_unsupported, Toast.LENGTH_LONG).show(); return
        }
        val s = Session(UUID.randomUUID().toString(), info.packageName, info.fieldId, info.fieldName,
            snapshot,
            RichInputMethodManager.getInstance().currentSubtype.rawSubtype)
        session = s
        ime.startActivity(Intent(ime, TranslatorActivity::class.java)
            .putExtra("session", s.token).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK))
    }

    fun valid(token: String?) = session?.let { it.token == token && !it.completed } == true
    fun complete(token: String?, text: String?) {
        session?.takeIf { it.token == token && !it.completed }?.let { it.result = text; it.completed = true }
    }

    /** Returns true for the translator editor, suppressing the app's usual locale hints. */
    @JvmStatic fun prepareEditor(ime: LatinIME, info: EditorInfo?): Boolean {
        val s = session ?: return false
        val imm = RichInputMethodManager.getInstance()
        if (isEditor(ime, info)) {
            if (!s.entered) {
                s.entered = true
                val english = SubtypeSettings.getEnabledSubtypes().firstOrNull { it.locale().language == "en" }
                    ?: SubtypeSettings.getAllAvailableSubtypes().firstOrNull { it.locale().toLanguageTag() == "en-US" }
                    ?: SubtypeSettings.getAllAvailableSubtypes().firstOrNull { it.locale().language == "en" }
                if (english != null) {
                    imm.beginTemporarySubtype()
                    ime.switchToSubtype(english)
                }
            }
            return true
        }
        if (s.entered) {
            imm.endTemporarySubtype()
            ime.switchToSubtype(s.subtype)
        }
        return false
    }

    @JvmStatic fun deliver(ime: LatinIME, info: EditorInfo?) {
        val s = session ?: return
        if (isEditor(ime, info) || !s.entered) return
        session = null
        val text = s.result ?: return
        val ic = ime.currentInputConnection
        if (info?.packageName != s.packageName || info.fieldId != s.fieldId || info.fieldName != s.fieldName ||
            SystemClock.elapsedRealtime() - s.started > 15 * 60 * 1000 || ic == null || EditorSnapshot.capture(ic) != s.snapshot) {
            Toast.makeText(ime, R.string.translate_editor_changed, Toast.LENGTH_LONG).show(); return
        }
        // A literal insertion avoids autocorrection or Vietnamese Telex transforming the result.
        ime.onTextInput(text)
    }

}

/** Selection offsets distinguish identical surrounding text at different positions. */
internal data class EditorSnapshot(val before: String, val selected: String, val after: String,
    val selectionStart: Int, val selectionEnd: Int) {
    companion object {
        fun capture(ic: InputConnection): EditorSnapshot? {
            val extracted = ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0) ?: return null
            return EditorSnapshot(ic.getTextBeforeCursor(128, 0)?.toString() ?: return null,
                ic.getSelectedText(0)?.toString().orEmpty(), ic.getTextAfterCursor(128, 0)?.toString() ?: return null,
                extracted.startOffset + extracted.selectionStart, extracted.startOffset + extracted.selectionEnd)
        }
    }
}
