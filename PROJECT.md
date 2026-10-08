# HeliBoard Vietnamese

Canonical repository for our HeliBoard Vietnamese Telex work: https://github.com/kd40000-dot/heliboard-vietnamese

This repository will contain the HeliBoard source snapshot and the ViKey-Telex integration, tests, attribution, and build documentation. Use this repository for future development.

## Provenance

- HeliBoard: https://github.com/HeliBorg/HeliBoard at `415c45f15c47de3de74eeeb9fdb8e56e46d1389a`.
- Android ViKey-Telex: https://github.com/ngocthanhgl/ViKey-Telex at `ac2db266228331f59f157d12bb5a07112a73853c`.
- Telex engine attribution and license are retained in the source and `docs/vietnamese-telex.md`.

## Checkpoint

Vietnamese Telex implementation is complete. The debug APK built successfully and 15 focused tests passed on 2026-10-03. Physical-device validation was not recorded in this conversation.

The original integration patch is preserved under `.project-migration/` as a restore point. The import workflow populates the source only when `app/build.gradle.kts` is absent, and preserves the existing project notes and workflows.
