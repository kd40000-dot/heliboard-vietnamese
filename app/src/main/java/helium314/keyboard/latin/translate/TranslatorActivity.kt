// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.translate

import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A translucent bottom panel, using the regular IME editing pipeline for its source box. */
class TranslatorActivity : ComponentActivity() {
    private var task: Job? = null
    private var revision = 0
    private val token get() = intent.getStringExtra("session")
    private lateinit var source: EditText
    private lateinit var status: TextView
    private lateinit var submit: Button
    private var apiKey = ""
    private var target = "VI"
    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!TranslationSession.valid(token)) { finish(); return }
        enableEdgeToEdge()
        val colors = Settings.getValues().mColors
        val foreground = colors.get(ColorType.KEY_TEXT)
        val root = FrameLayout(this).apply { setBackgroundColor(0x40000000) }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(colors.get(ColorType.MAIN_BACKGROUND))
        }
        root.addView(ScrollView(this).apply { addView(panel) }, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        // Keep the panel above the IME, including Android's enforced edge-to-edge layouts.
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }
        val header = LinearLayout(this)
        val title = TextView(this).apply { setText(R.string.translate_title); textSize = 18f; setTextColor(foreground) }
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(Button(this).apply { setText(R.string.translate_close); setOnClickListener { cancel() } })
        panel.addView(header)
        val targets = arrayOf("Vietnamese", "English (UK)", "English (US)", "French", "German", "Japanese", "Korean", "Spanish", "Chinese (simplified)")
        val codes = arrayOf("VI", "EN-GB", "EN-US", "FR", "DE", "JA", "KO", "ES", "ZH-HANS")
        val languages = LinearLayout(this)
        languages.addView(TextView(this).apply { setText(R.string.translate_from_english); setTextColor(foreground) })
        languages.addView(Spinner(this).apply {
            adapter = ArrayAdapter(this@TranslatorActivity, android.R.layout.simple_spinner_dropdown_item, targets)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    target = codes[position]; invalidateRequest()
                }
            }
        })
        panel.addView(languages)
        source = EditText(this).apply {
            hint = getString(R.string.translate_hint)
            setTextColor(foreground); setHintTextColor(foreground and 0x00ffffff or 0x99000000.toInt())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            privateImeOptions = TranslationSession.EDITOR
            minLines = 1; maxLines = 3
            filters = arrayOf(InputFilter.LengthFilter(5000))
            isSaveEnabled = false
            doAfterTextChanged { invalidateRequest() }
        }
        panel.addView(source, LinearLayout.LayoutParams(-1, -2))
        status = TextView(this).apply { setTextColor(foreground); setText(R.string.translate_disclosure) }
        panel.addView(status)
        val keyStore = TranslationKeyStore(this)
        apiKey = keyStore.read()
        val keyArea = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        val keyInput = EditText(this).apply {
            hint = getString(R.string.translate_key_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            privateImeOptions = TranslationSession.EDITOR
            setSingleLine(); isSaveEnabled = false; setText(apiKey)
            setTextColor(foreground)
        }
        keyArea.addView(keyInput)
        keyArea.addView(Button(this).apply {
            setText(R.string.translate_save_key)
            setOnClickListener {
                val value = keyInput.text.toString().trim()
                if (value.any { it.isWhitespace() || it.code < 32 }) {
                    status.setText(R.string.translate_key_invalid); return@setOnClickListener
                }
                runCatching { keyStore.save(value) }.onSuccess {
                    apiKey = value; invalidateRequest(); keyArea.visibility = View.GONE
                    source.requestFocus(); status.setText(R.string.translate_disclosure)
                }.onFailure { status.setText(R.string.translate_key_storage_error) }
            }
        })
        panel.addView(keyArea)
        val actions = LinearLayout(this)
        actions.addView(Button(this).apply {
            setText(R.string.translate_key)
            setOnClickListener { keyArea.visibility = if (keyArea.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
        })
        submit = Button(this).apply { setText(R.string.translate_insert); setOnClickListener { translate() } }
        actions.addView(submit, LinearLayout.LayoutParams(0, -2, 1f))
        panel.addView(actions)
        source.requestFocus()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        source.post { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(source, InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun invalidateRequest() {
        revision++
        task?.cancel()
        if (::submit.isInitialized) submit.isEnabled = true
    }

    private fun translate() {
        if (apiKey.isBlank()) { status.setText(R.string.translate_need_key); return }
        val text = source.text.toString()
        if (text.isBlank()) { status.setText(R.string.translate_hint); return }
        val current = ++revision
        val key = apiKey
        val language = target
        submit.isEnabled = false
        status.setText(R.string.translate_working)
        task = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { DeepLClient.translate(key, text, language) } }
            if (current != revision || !TranslationSession.valid(token)) return@launch
            submit.isEnabled = true
            result.onSuccess {
                finished = true
                TranslationSession.complete(token, it)
                finish()
            }.onFailure {
                status.setText(when ((it as? TranslationFailure)?.status) {
                    401, 403 -> R.string.translate_key_invalid
                    456 -> R.string.translate_quota
                    429 -> R.string.translate_rate_limit
                    400 -> R.string.translate_language_error
                    else -> R.string.translate_network_error
                })
            }
        }
    }

    private fun cancel() { finished = true; TranslationSession.complete(token, null); finish() }
    override fun onStop() {
        super.onStop()
        if (!finished && !isChangingConfigurations) cancel()
    }
    override fun onDestroy() { task?.cancel(); super.onDestroy() }
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
}
