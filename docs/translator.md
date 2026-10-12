# Gemini inline translator

Open **Translate** in HeliBoard's toolbar while editing a message in Zalo or another text field.
A compact panel appears directly above the normal keyboard. Type an English message and
a Vietnamese preview appears after you stop typing for about one second. Tap **Insert**
to place the preview into the original message field. **Insert does not press Send**.

The default profile translates as **anh** speaking to **em** (Tran). The direction
button switches to Vietnamese → English. Each translation is a separate request:
no chat history, clipboard content, or other app text is submitted automatically.
Corrections or changes invalidate outstanding previews.

## Setup

Create a Gemini API key in Google AI Studio at https://aistudio.google.com/api-keys .
Open Translate, tap **Gemini key**, paste the key, and save it. The credential
is encrypted with Android Keystore and kept outside app backups. The old DeepL
credential is separate and is not re-used.

The implementation uses Gemini 2.5 Flash and sends source text to Google's Gemini
API when typing pauses. Free-tier quotas are controlled by Google and may change.
Quota errors display a retry-later message; this app does not enable billing.

## Safety

The translator does not automatically send a message. Text is inserted only after
the explicit **Insert** action, and only if the originating editor, field, text,
selection and package still match the captured snapshot. It refuses insertion
if the destination changed. The keyboard's original subtype (including Telex)
is restored on exit. Network requests and translation results are not logged.

Physical-device validation is required for Zalo navigation, rotation, keyboard
visibility, API failures, theme appearance, and reliable insertion.
