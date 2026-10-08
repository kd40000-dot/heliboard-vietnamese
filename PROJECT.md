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
