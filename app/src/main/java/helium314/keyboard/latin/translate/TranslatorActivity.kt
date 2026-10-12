// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.translate

import android.os.Bundle
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.Color
import android.content.res.Configuration
import androidx.core.graphics.ColorUtils
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
    private var activeCancellation: TranslationCancellation? = null
    private var revision = 0
    private val token get() = intent.getStringExtra("session")
    private lateinit var source: EditText
    private lateinit var preview: TextView
    private lateinit var action: Button
    private lateinit var direction: Button
    private var result: String? = null
    private var key = ""
    private lateinit var modelCache: TranslationModelCache
    private var reverse = false
    private var finished = false
    private var lastThemeSignature: Int = 0
    private val debounce = Runnable { translate() }
    private var timeoutGuard: Runnable? = null
    private fun clearTimeout() {
        timeoutGuard?.let { handler.removeCallbacks(it) }
        timeoutGuard = null
    }

    private fun stopRequest() {
        activeCancellation?.cancel()
        activeCancellation = null
        request?.cancel()
        request = null
    }
    private fun showFailure(message: String) {
        if (!::preview.isInitialized || !::action.isInitialized) return
        result = null
        preview.text = message
        action.text = "Retry"
        action.isEnabled = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!TranslationSession.valid(token)) { finish(); return }
        val colors = Settings.getValues().mColors
        val foreground = colors.get(ColorType.KEY_TEXT)
        val background = colors.get(ColorType.MAIN_BACKGROUND)
        val inputBackground = colors.get(ColorType.SPACE_BAR_BACKGROUND)
        val controlBackground = colors.get(ColorType.FUNCTIONAL_KEY_BACKGROUND)
        val accent = colors.get(ColorType.TOOL_BAR_KEY_ENABLED_BACKGROUND)
        lastThemeSignature = foreground xor background xor accent
        val buttonText = foreground
        fun styleButton(button: Button) {
            button.typeface = Typeface.DEFAULT
            button.setTextColor(buttonText)
            button.backgroundTintList = ColorStateList.valueOf(controlBackground)
            button.isAllCaps = false
        }
        val root = FrameLayout(this).apply { setBackgroundColor(Color.TRANSPARENT) }
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
            styleButton(this)
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
            styleButton(this)
            setOnClickListener { cancel() }
        })
        panel.addView(header)

        source = EditText(this).apply {
            this.hint = "Type English…"
            setTextColor(foreground)
            setHintTextColor(ColorUtils.setAlphaComponent(foreground, 180))
            setHighlightColor(ColorUtils.setAlphaComponent(accent, 110))
            typeface = Typeface.DEFAULT
            this.background = GradientDrawable().apply {
                setColor(inputBackground)
                cornerRadius = dp(6).toFloat()
            }
            setPadding(dp(10), dp(8), dp(10), dp(8))
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                textCursorDrawable = GradientDrawable().apply {
                    setColor(accent)
                    setSize(dp(2), dp(24))
                }
            }
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
            typeface = Typeface.DEFAULT
            setPadding(dp(8), dp(8), dp(8), dp(8))
            minHeight = dp(48)
        }
        panel.addView(preview)
        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val store = TranslationKeyStore(this)
        modelCache = TranslationModelCache(applicationContext)
        key = store.read()
        actions.addView(Button(this).apply {
            text = "Gemini key"
            styleButton(this)
            setOnClickListener {
                val entry = EditText(this@TranslatorActivity).apply {
                    this.hint = "Google AI Studio API key"
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    setSingleLine(true)
                    setTextColor(foreground)
                    setHintTextColor(ColorUtils.setAlphaComponent(foreground, 180))
                    backgroundTintList = ColorStateList.valueOf(accent)
                    typeface = Typeface.DEFAULT
                    setText(key)
                    isSaveEnabled = false
                    privateImeOptions = TranslationSession.EDITOR
                }
                val dialog = android.app.AlertDialog.Builder(this@TranslatorActivity)
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
                    }.create()
                dialog.setOnShowListener {
                    dialog.window?.setBackgroundDrawable(GradientDrawable().apply {
                        setColor(background)
                        cornerRadius = dp(12).toFloat()
                    })
                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.apply { setTextColor(accent) }
                    dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)?.apply { setTextColor(accent) }
                }
                dialog.show()
            }
        })
        action = Button(this).apply {
            text = "Insert"
            styleButton(this)
            isEnabled = false
            setOnClickListener {
                val translated = result
                if (translated == null) {
                    if (key.isNotBlank() && source.text.isNotBlank()) translate()
                    return@setOnClickListener
                }
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
        stopRequest()
        clearTimeout()
        result = null
        if (::action.isInitialized) {
            // Typing pauses trigger an automatic request, but users can skip the pause
            // by pressing Translate now (also useful for one-word messages).
            action.text = "Translate now"
            action.isEnabled = key.isNotBlank() && ::source.isInitialized && source.text.isNotBlank()
        }
        if (!::source.isInitialized || !::preview.isInitialized) return
        if (key.isBlank()) {
            preview.text = "Set your free Gemini API key to begin"
        } else if (source.text.isBlank()) {
            preview.text = "Translation appears here"
        } else {
            preview.text = "Translating…"
            handler.postDelayed(debounce, 450L)
        }
    }

    private fun translate() {
        if (!::source.isInitialized || !::preview.isInitialized) return
        val input = source.text.toString()
        if (key.isBlank()) { showFailure("Enter your Gemini API key first."); return }
        if (input.isBlank()) { showFailure("Type a message before translating."); return }

        handler.removeCallbacks(debounce)
        clearTimeout()
        stopRequest()
        val sequence = ++revision
        val directionValue = reverse
        val credential = key
        val cancellation = TranslationCancellation()
        activeCancellation = cancellation
        result = null
        preview.text = "Translating…"
        action.text = "Insert"
        action.isEnabled = false

        // Hard upper bound for unusual server stalls. Short read timeouts were
        // incorrectly rejecting valid slow Gemini responses before they arrived.
        val guard = Runnable {
            if (sequence == revision && !finished && TranslationSession.valid(token)) {
                revision++
                stopRequest()
                showFailure("Gemini did not finish within 65 seconds. Tap Retry.")
            }
        }
        timeoutGuard = guard
        handler.postDelayed(guard, 65_000L)
        handler.postDelayed({
            if (sequence == revision && result == null && !finished &&
                preview.text.toString() == "Translating…") {
                preview.text = "Waiting for Gemini…"
            }
        }, 10_000L)

        request = lifecycleScope.launch {
            val answer = try {
                withContext(Dispatchers.IO) {
                    runCatching {
                        GeminiClient.translate(
                            credential, input, directionValue, modelCache, cancellation
                        ) { partial ->
                            handler.post {
                                if (sequence == revision && !finished && result == null &&
                                    TranslationSession.valid(token)) {
                                    preview.text = partial
                                }
                            }
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Invalidation or Activity shutdown; the watchdog handles unexpected stalls.
                return@launch
            }
            if (sequence != revision || !TranslationSession.valid(token) || finished) return@launch
            clearTimeout()
            activeCancellation = null
            answer.onSuccess {
                result = it
                preview.text = it
                action.text = "Insert"
                action.isEnabled = true
            }.onFailure {
                showFailure((it as? GeminiFailure)?.userMessage()
                    ?: "Network error: " + when (it) {
                        is java.net.UnknownHostException -> "No DNS connection"
                        is java.net.SocketTimeoutException -> "Gemini did not respond in time. Tap Retry."
                        is javax.net.ssl.SSLException -> "Secure connection failed"
                        else -> "Could not connect to Gemini"
                    })
            }
        }
    }

    private fun cancel() {
        finished = true
        handler.removeCallbacks(debounce)
        clearTimeout()
        stopRequest()
        TranslationSession.complete(token, null)
        finish()
    }
    override fun onStop() {
        super.onStop()
        if (!finished && !isChangingConfigurations) cancel()
    }
    override fun onDestroy() {
        handler.removeCallbacks(debounce)
        clearTimeout()
        stopRequest()
        super.onDestroy()
    }
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
}
