// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.translate

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Compact translation preview above the standard keyboard, without moving text to clipboard. */
class TranslatorActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var request: Job? = null
    private var revision = 0
    private val token get() = intent.getStringExtra("session")
    private lateinit var source: EditText
    private lateinit var preview: TextView
    private lateinit var action: Button
    private lateinit var direction: Button
    private var result: String? = null
    private var key = ""
    private var reverse = false
    private var finished = false
    private val debounce = Runnable { translate() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!TranslationSession.valid(token)) { finish(); return }
        val colors = Settings.getValues().mColors
        val foreground = colors.get(ColorType.KEY_TEXT)
        val background = colors.get(ColorType.MAIN_BACKGROUND)
        val root = FrameLayout(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(background)
        }
        root.addView(panel, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val system = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(system.left, system.top, system.right, maxOf(system.bottom, ime.bottom))
            insets
        }

        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        direction = Button(this).apply {
            text = "English → Vietnamese"
            setOnClickListener {
                reverse = !reverse
                text = if (reverse) "Vietnamese → English" else "English → Vietnamese"
                source.hint = if (reverse) "Type Vietnamese…" else "Type English…"
                invalidateTranslation()
            }
        }
        header.addView(direction, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(Button(this).apply {
            text = "×"
            setOnClickListener { cancel() }
        })
        panel.addView(header)

        source = EditText(this).apply {
            hint = "Type English…"
            setTextColor(foreground)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            privateImeOptions = TranslationSession.EDITOR
            minLines = 1
            maxLines = 3
            filters = arrayOf(InputFilter.LengthFilter(2500))
            isSaveEnabled = false
            doAfterTextChanged { invalidateTranslation() }
        }
        panel.addView(source)
        preview = TextView(this).apply {
            text = "Translation appears here"
            textSize = 15f
            setTextColor(foreground)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            minHeight = dp(48)
        }
        panel.addView(preview)
        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val store = TranslationKeyStore(this)
        key = store.read()
        actions.addView(Button(this).apply {
            text = "Gemini key"
            setOnClickListener {
                val entry = EditText(this@TranslatorActivity).apply {
                    hint = "Google AI Studio API key"
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    setSingleLine(true)
                    setText(key)
                    isSaveEnabled = false
                    privateImeOptions = TranslationSession.EDITOR
                }
                android.app.AlertDialog.Builder(this@TranslatorActivity)
                    .setTitle("Gemini API key")
                    .setMessage("Create a free-tier key at aistudio.google.com/api-keys. Text is sent to Google only for translation.")
                    .setView(entry)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Save") { _, _ ->
                        val value = entry.text.toString().trim()
                        if (value.any { it.isWhitespace() || it.code < 32 }) {
                            preview.text = "Invalid API key"
                        } else {
                            runCatching { store.save(value) }.onSuccess {
                                key = value
                                invalidateTranslation()
                            }.onFailure { preview.text = "Could not securely store key" }
                        }
                    }.show()
            }
        })
        action = Button(this).apply {
            text = "Insert"
            isEnabled = false
            setOnClickListener {
                val translated = result ?: return@setOnClickListener
                finished = true
                handler.removeCallbacks(debounce)
                TranslationSession.complete(token, translated)
                finish()
            }
        }
        actions.addView(action, LinearLayout.LayoutParams(0, -2, 1f))
        panel.addView(actions)
        source.requestFocus()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        source.post {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(source, InputMethodManager.SHOW_IMPLICIT)
        }
        if (key.isBlank()) preview.text = "Set your free Gemini API key to begin"
    }

    private fun invalidateTranslation() {
        revision++
        handler.removeCallbacks(debounce)
        request?.cancel()
        result = null
        if (::action.isInitialized) action.isEnabled = false
        if (!::source.isInitialized || !::preview.isInitialized) return
        if (key.isBlank()) {
            preview.text = "Set your free Gemini API key to begin"
        } else if (source.text.isBlank()) {
            preview.text = "Translation appears here"
        } else {
            preview.text = "Translating…"
            handler.postDelayed(debounce, 1000L)
        }
    }

    private fun translate() {
        val input = source.text.toString()
        if (key.isBlank() || input.isBlank()) return
        val sequence = revision
        val direction = reverse
        val credential = key
        request = lifecycleScope.launch {
            val answer = withContext(Dispatchers.IO) {
                runCatching { GeminiClient.translate(credential, input, direction) }
            }
            if (sequence != revision || !TranslationSession.valid(token) || finished) return@launch
            answer.onSuccess {
                result = it
                preview.text = it
                action.isEnabled = true
            }.onFailure {
                preview.text = when ((it as? TranslationFailure)?.status) {
                    400 -> "Invalid request"
                    401, 403 -> "Check your Gemini API key or API access"
                    429 -> "Free API limit reached. Try again later."
                    else -> "Translation failed. Check connection and retry."
                }
            }
        }
    }

    private fun cancel() {
        finished = true
        handler.removeCallbacks(debounce)
        TranslationSession.complete(token, null)
        finish()
    }
    override fun onStop() {
        super.onStop()
        if (!finished && !isChangingConfigurations) cancel()
    }
    override fun onDestroy() {
        handler.removeCallbacks(debounce)
        request?.cancel()
        super.onDestroy()
    }
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
}
