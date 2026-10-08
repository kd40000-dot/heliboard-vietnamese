# Vietnamese Telex

Select Vietnamese in HeliBoard's language settings. Telex is enabled for the
built-in Vietnamese subtype and Vietnamese custom layouts. Other languages keep
their existing input rules. An explicitly configured CombiningRules value takes
precedence over the Vietnamese default.

The implementation uses the Android ViKey-Telex engine:
https://github.com/ngocthanhgl/ViKey-Telex

Upstream revision: ac2db266228331f59f157d12bb5a07112a73853c
Source: app/src/main/kotlin/dev/ngocthanhgl/vikey/ime/text/composing/AlgorithmicTelex.kt
Copyright (C) 2026 NgocThanhGL / Nguyễn Ngọc Thành.
Licensed under Apache-2.0; see ../LICENSE-Apache-2.0 and the retained source header.

The port changes the package/class name and removes FlorisBoard's Composer,
serialization and preference dependencies. Standalone w → ư is enabled, as in
ViKey's default. The conversion rules are otherwise retained from that revision.
This does not port ViKey's dictionary, UI, or other keyboard features.

Examples:

| Keys | Text |
| --- | --- |
| aa, aw, ee, oo, ow, uw | â, ă, ê, ô, ơ, ư |
| dd, uow | đ, ươ |
| as, af, ar, ax, aj | á, à, ả, ã, ạ |
| tieengs Vieetj | tiếng Việt |
| dduocwj | được |
| aaa, ass, ww | aa, as, w |
| aas followed by z | a |

Conversion is applied when a typed key enters the composing word, after normal
selection/cursor handling. Backspace deletes a displayed character, not an
invisible Telex keystroke. Already typed text and gesture results are restored
literally, so strings such as `ass` are not converted a second time. Pasted text
uses the existing text-input path. Composition is allowed with suggestions off
for text fields, while passwords and numeric fields retain literal input.

Validation commands:

Use JDK 21 for Robolectric's Android 16 test runtime. The app itself targets Java 17.

    ./gradlew :app:testDebugUnitTest --tests '*VietnameseTelexTest' --tests '*TelexChainTest' --tests '*InputLogicTest*telex*'
    ./gradlew :app:assembleDebugNoMinify

The debug APK has application ID helium314.keyboard.debug and installs alongside
the official release. No release signing key is included.
