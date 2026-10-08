# DeepL translator

Open **Translate** from the keyboard toolbar. The panel has its own source text
box above the keyboard. It defaults to English → Vietnamese. Tap **API key** to
enter a DeepL API Free or API Pro key on the device, then **Save key**. A normal
DeepL translator subscription is not an API subscription. Free keys ending in
`:fx` use api-free.deepl.com; other keys use api.deepl.com.

Type in the source box and tap **Translate & insert**. The panel closes and the
translated text is inserted at the original app selection. This does not press
Send in the app. Closing the panel cancels. Keyboard English is temporary and
the previous language is restored on return, including Vietnamese Telex.
English need not be enabled in the language list. Translation is explicit, not
sent on every keystroke. The first version accepts English source text and
provides a small target-language menu with Vietnamese as the default.

Only submitted source text goes to DeepL. The API key is encrypted with Android
Keystore and stored outside backups, separate from exported keyboard settings.
Android 6+ is required for key storage. Saving an empty key deletes it. This
feature adds Internet permission; ordinary keyboard input makes no DeepL calls.
Errors preserve the source box. Editing while a request is pending invalidates
its result. Closing/backgrounding cancels the handoff. Delivery checks the
originating package, field ID/name, surrounding text and selection. It refuses
changed destinations. A process restart discards the in-memory translation
session. No API key or text is logged by the translation code.

API contract: https://developers.deepl.com/api-reference/translate/request-translation

The panel uses a non-exported translucent activity, following HeliBoard's emoji
search approach, so normal IME editing, suggestions and paste remain available.
Device testing should cover returning to messaging apps, rotation, back,
selection replacement, API failures, and restoring Vietnamese after closing.
