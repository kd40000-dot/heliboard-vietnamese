# Built-in keyboard defaults

These defaults were derived from the 2026-10-11 HeliBoard settings export.

On a fresh install, use:
- English (US) with touch correction and IME switcher suppression, Spanish (US) with qwerty+, and Vietnamese with ViKey Telex.
- English (US) as the initial selected language.
- Light theme by day and the user's Dracula palette by night. Android's existing day/night behavior remains unchanged.
- Toolbar buttons and their enabled states from the provided export.
- Save subtype per app enabled; offensive-word blocking disabled.

These are **defaults**, not a forced configuration. Existing stored values take precedence,
and the embedded Dracula palette is seeded only if that named palette has never been saved.
Other custom named themes are untouched.

Not embedded: the 69 per-app language choices, usage history, user dictionaries,
clipboard database, selected emoji category, version metadata, personal preferences in
protected storage, or any API keys. User data stays in the user's own backup.

To restore historical learned words or app-by-app language assignments, use HeliBoard's
normal settings import rather than the default preset.
