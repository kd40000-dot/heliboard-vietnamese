# HeliBoard Vietnamese

Canonical repository for all current and future work on this project:
https://github.com/kd40000-dot/heliboard-vietnamese

The complete HeliBoard source snapshot, Vietnamese Telex integration, tests, licenses, and build notes are checked in here. Edit the source files directly for future changes.

## Provenance and restore point

- HeliBoard: https://github.com/HeliBorg/HeliBoard at `415c45f15c47de3de74eeeb9fdb8e56e46d1389a`.
- Android ViKey-Telex: https://github.com/ngocthanhgl/ViKey-Telex at `ac2db266228331f59f157d12bb5a07112a73853c`.
- Original integration patch: `.project-migration/telex.patch`.
- Original upstream CI workflows are preserved in `docs/upstream-workflows/` as reference files.
- Attribution, behavior, and examples: `docs/vietnamese-telex.md`.

## Validation checkpoint

The debug APK built successfully and all 15 focused automated tests passed on 2026-10-03. Results are recorded in `docs/checkpoint-validation.json`. No physical-device result was recorded in this conversation. Repository migration does not change application behavior.

## Build and test

Use JDK 21 for Robolectric's Android 16 runtime and install the SDK/NDK versions declared in the Gradle files.

```sh
./gradlew :app:testDebugUnitTest --tests '*VietnameseTelexTest' --tests '*TelexChainTest' --tests '*InputLogicTest*telex*'
./gradlew :app:assembleDebugNoMinify
```

The debug APK uses `helium314.keyboard.debug` and installs alongside the official release. Keep generated APKs in release assets or build artifacts, and keep signing keys out of source control.

The one-time import workflow does nothing after `app/build.gradle.kts` exists. Do not use it to overwrite later work.


## Translator

See [translator setup and behavior](docs/translator.md). The GitHub Actions
`Translator APK and focused tests` workflow builds the translator APK and runs
the DeepL client, destination guards, and Vietnamese Telex regression tests.

## Permanent APK signing

This repository's translator workflow builds and tests on GitHub Actions. It can
also sign an updatable APK using a dedicated stable PKCS12 certificate instead
of the runner-generated Android debug certificate.

The key is NOT checked into this public repository. The owner has the private
key backup named `HeliBoard-PERMANENT-SIGNING-KEY-BACKUP.zip`.

**One-time setup:** Open the backup ZIP locally. Copy the full, single line
inside `github-actions-secret.txt`. On GitHub, open this repository's
Settings → Secrets and variables → Actions → New repository secret. Name the
secret `HELIBOARD_SIGNING_BUNDLE_B64` and paste the copied line as its value.
Do not upload the ZIP or the secret value to a public issue or repository.

Trigger the "Translator APK and focused tests" GitHub Actions workflow manually
or change app code. After tests pass, the workflow signs using Android's
apksigner and checks the expected public certificate SHA-256 fingerprint:

`07DF108DE8865D1B7C4F70D4674A8E5AEC114C5BAC1F907616981638556E85E9`

The signed artifact is `HeliBoard-Vietnamese-Telex-DeepL-signed.apk` inside
the `HeliBoard-signed-<run_number>` artifact. All future signed builds reuse the
same certificate and have monotonically increasing CI version codes. The APK
is still the independent `helium314.keyboard.debug` package, not a replacement
for the separately signed upstream `helium314.keyboard`.

The prior APKs were signed by different debug identities. Android requires
a matching signature for in-place updates, so switching from an old debug APK
may require a one-time settings export, uninstall and restore. Subsequent
builds signed with this key can update without wiping settings.

For future ChatGPT deliverables: download the GitHub workflow artifact, extract
the APK, and share the APK directly rather than making the user extract a ZIP.

## Update signing policy (2026-10-11)

The translator workflow **never distributes debug-signed APKs**. Pull requests compile
and run tests without APK distribution. Branch pushes and manual release workflow
runs require the repository Actions secret `HELIBOARD_SIGNING_BUNDLE_B64`; if it
is absent, the release job fails explicitly rather than publishing an incompatible
APK. Only signed artifacts with the pinned certificate SHA-256
`07DF108DE8865D1B7C4F70D4674A8E5AEC114C5BAC1F907616981638556E85E9`
are released. Version codes increase with workflow run number.

To activate: find your privately held `HeliBoard-PERMANENT-SIGNING-KEY-BACKUP.zip`,
open `github-actions-secret.txt` locally, and place its one-line value in
GitHub Settings > Secrets and variables > Actions > New repository secret under
`HELIBOARD_SIGNING_BUNDLE_B64`. Never commit the ZIP or paste the value into a
public conversation. Then re-run the workflow and verify the signed artifact.

Android cannot install an APK with a different signing certificate over the
current one. If your last debug build had a different certificate, export
HeliBoard settings, uninstall that copy, install the first permanently-signed
APK, and restore settings. This final one-time migration may not retain the
non-exported Gemini API credential; re-enter it if needed. All subsequent
permanently-signed builds can install over one another without uninstalling.
