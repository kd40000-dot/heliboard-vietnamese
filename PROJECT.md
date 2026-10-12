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

## Stable APK signing

The custom HeliBoard package is `helium314.keyboard.debug`, separate from the
upstream `helium314.keyboard` package. All published custom APKs must use the
**same signing key** and a strictly increasing versionCode.

The GitHub Actions workflow `.github/workflows/translator.yml` now signs using
a persistent PKCS12 certificate stored in `.github/signing/`:

- `heliboard-release.p12.b64` — the fixed signing key, Base64 encoded
- `keystore-password.txt` — keystore password
- `certificate-sha256.txt` — pinned certificate fingerprint

**Public-key exposure warning:** This repository is public and the files include
the signing private key and its password. The project owner explicitly requested
that they be stored here to avoid manual secret configuration. This permits
anyone with repository access to sign APKs with the same certificate, so users
must obtain releases from the trusted GitHub Actions workflow. Do not use this
key for any other application. For stronger security in the future, migrate the
key and password to a GitHub Actions secret without changing the certificate.

The signing job verifies its certificate against the pinned fingerprint and
publishes only permanently signed APKs. Never regenerate or rotate the key:
that would break Android update compatibility. GitHub CI `GITHUB_RUN_NUMBER`
is used to increase `versionCode`.

Old test builds used GitHub runner-generated Android debug certificates, which
do not match the permanent certificate. One **last uninstall and reinstall** is
required when migrating from one of those debug-signed APKs. Export keyboard
settings first and restore after installing this fixed-certificate release.
The Gemini API key may need to be entered again. All future APKs signed by
the committed certificate can install in place, preserving app data.

For each future ChatGPT deliverable, obtain the `HeliBoard-signed-<run_number>`
GitHub artifact, extract the signed APK, verify its certificate against
`certificate-sha256.txt`, and provide the APK directly, not inside a ZIP.
