# number-input

[![Maven Central](https://img.shields.io/maven-central/v/dev.viethung/number-input)](https://central.sonatype.com/artifact/dev.viethung/number-input)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)
[![Platforms](https://img.shields.io/badge/platforms-Android%20%7C%20iOS-lightgrey)](#requirements)

A locale-aware numeric text field for **Compose Multiplatform** (Android + iOS), with live thousands
grouping as you type and a Clear / ± / Done toolbar attached to the keyboard.

<p align="center">
  <img src="docs/demo.gif" alt="Typing 2500000 into a vi-VN field: digits group live into 2.500.000, then ± flips the sign and Done commits" width="300">
  &nbsp;&nbsp;&nbsp;
  <img src="docs/screenshot.png" alt="Four fields configured for en-US, vi-VN, de-DE and an unsigned en-US, with the Vietnamese keyboard toolbar" width="300">
</p>

On iOS the toolbar is a real `UIInputAccessoryView` on a native `UITextField` — not a Compose
imitation floating above the keyboard. On Android it rides the IME animation via `imePadding()`.

## Why

Formatting money in a text field is deceptively fiddly: the caret drifts once you insert separators,
`.` on the decimal keypad is wrong on half the world's locales, and "1.234" means a thousand in Berlin
and one-point-two-three-four in Boston. This handles those cases and keeps the parsed `Double` and the
displayed string in agreement.

- **Live grouping** while typing, using the locale's real separators.
- **Locale-correct decimals** — the keypad always emits `.`, which is translated to `,` on de-DE,
  vi-VN and friends.
- **Caret never jumps** on Android; grouping is a display-only transformation with a proper
  `OffsetMapping`.
- **Fixed decimal precision** with `significantDigits`, including `0` for integer-only currencies
  such as the Vietnamese đồng.
- **Keyboard toolbar** with Clear, sign toggle and Done, enabled/disabled by shared rules on both
  platforms.
- **No Material dependency** — `compose.runtime` / `foundation` / `ui` only. Styling comes from
  `NumberInputStyle`, so it drops into any design system.

## Requirements

| | |
|---|---|
| Kotlin | 2.2.20 or newer |
| Compose Multiplatform | 1.9.0 or newer |
| Android | `minSdk` 23, JVM target 11 |
| iOS | `iosArm64`, `iosSimulatorArm64`, `iosX64` |

Kotlin klibs have no forward compatibility, so a consumer on an **older** Kotlin than 2.2.20 cannot
resolve the iOS artifacts at all. The floor is deliberately low; it is not a stale number.

## Installation

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}
```

```kotlin
// shared/build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("dev.viethung:number-input:1.0.0")
        }
    }
}
```

<details>
<summary>Using a version catalog</summary>

```toml
# gradle/libs.versions.toml
[versions]
numberInput = "1.0.0"

[libraries]
number-input = { module = "dev.viethung:number-input", version.ref = "numberInput" }
```

```kotlin
commonMain.dependencies {
    implementation(libs.number.input)
}
```
</details>

## Quick start

```kotlin
import dev.viethung.numberinput.NumberInputConfig
import dev.viethung.numberinput.NumberInputField
import dev.viethung.numberinput.NumberInputHost

@Composable
fun AmountScreen() {
    var amount by remember { mutableStateOf<Double?>(null) }

    NumberInputHost {
        Column(Modifier.padding(16.dp)) {
            NumberInputField(
                value = amount,
                onValueChange = { amount = it },
                modifier = Modifier.fillMaxWidth(),
                config = NumberInputConfig(
                    significantDigits = 2,
                    locale = "en-US",
                    placeholder = "Enter amount",
                ),
            )
        }
    }
}
```

`value` is a nullable `Double` — `null` means empty, which is distinct from `0.0`.

`onValueChange` fires only on genuine changes, and pushing a new `value` in from the parent is
ignored while the user is mid-edit, so this binds safely to a ViewModel without feedback loops.

## Android setup (required)

The keyboard toolbar cannot track the IME unless the host Activity opts in. A library cannot set
these for you:

```xml
<!-- AndroidManifest.xml -->
<activity
    android:name=".MainActivity"
    android:windowSoftInputMode="adjustResize" />
```

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()          // required for IME insets
        setContent { AmountScreen() }
    }
}
```

Then wrap your screen in `NumberInputHost`, which renders the toolbar bottom-aligned so Compose can
animate it in lockstep with the keyboard.

Without the host the field still works — it falls back to drawing the toolbar inline directly beneath
itself, so the actions stay reachable, but it will not follow the keyboard.

On **iOS** `NumberInputHost` does nothing; the system attaches the accessory view itself. It is
accepted on both platforms so the same code compiles everywhere.

## Hoisted state

To drive the field from a ViewModel or DI graph, own the `NumberInputState` and use the other
overload:

```kotlin
class CheckoutViewModel : ViewModel() {
    val amount = NumberInputState(
        initialValue = 1_234.5,
        config = NumberInputConfig(significantDigits = 2, locale = "de-DE"),
    )
}

@Composable
fun Checkout(viewModel: CheckoutViewModel) {
    NumberInputHost {
        NumberInputField(state = viewModel.amount)
    }
}
```

`NumberInputState` is a plain class holding Compose snapshot state, not a `ViewModel` — the component
is a field, not a screen. Updates are synchronous, so tests need no dispatcher control.

| Member | Meaning |
|---|---|
| `value: Double?` | Parsed value; `null` when empty |
| `rawText: String` | Edit buffer, **always ungrouped**, using the locale's decimal separator |
| `phase: NumberInputPhase` | `Idle` or `Editing` |
| `clear()` / `toggleSign()` | The toolbar actions |
| `syncExternalValue(v)` | Push a value in; ignored mid-edit |

## Configuration

```kotlin
NumberInputConfig(
    significantDigits = 3,       // 0..9, fixed decimals. 0 = integer-only
    locale = "en-US",            // BCP-47 tag driving separators and parsing
    allowNegative = true,        // false disables ± and clamps a negative seed to 0
    placeholder = "",
)
```

`significantDigits = 0` rejects the decimal separator outright rather than merely capping it — which
is what you want for the Vietnamese đồng:

```kotlin
NumberInputConfig(significantDigits = 0, locale = "vi-VN", placeholder = "Nhập số tiền")
// 2500000 renders as 2.500.000, and "," is refused
```

## Styling and localisation

```kotlin
NumberInputStyle(
    textColor = MaterialTheme.colorScheme.onSurface,
    textSize = 18.sp,
    borderColor = MaterialTheme.colorScheme.outline,
    cornerRadius = 8.dp,
    toolbarBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
    toolbarTint = MaterialTheme.colorScheme.primary,
    clearLabel = "Xoá",          // defaults are English
    signLabel = "±",
    doneLabel = "Xong",
)
```

> The toolbar labels default to English and will ship to every user that way. Pass localised strings
> from your own resources.

The style surface is deliberately small: every property maps to *both* a Compose `BasicTextField` and
a UIKit `UITextField`. Anything that would work on only one platform — `FontFamily`, gradients,
arbitrary shapes, `letterSpacing` — is left out rather than accepted and silently ignored.

## Custom formatting

`LocaleNumberFormatter` is public and injectable. If your app already renders numbers its own way,
supply your implementation instead of inheriting this library's — mixing two strategies in one app
produces visibly inconsistent separators.

```kotlin
class MyFormatter : LocaleNumberFormatter {
    override fun format(value: Double, significantDigits: Int, locale: String): String = TODO()
    override fun parse(rawText: String, locale: String): Double? = TODO()
    override fun formatLive(rawText: String, locale: String): String = TODO()
    override fun decimalSeparator(locale: String): String = TODO()
    override fun groupingSeparator(locale: String): String = TODO()
}

NumberInputState(formatter = MyFormatter(), config = config)
```

The defaults are `DecimalFormat` on Android and `NSNumberFormatter` on iOS.

## Testing

Elements carry stable identifiers — Compose test tags on Android, `accessibilityIdentifier` on iOS:

| Element | Identifier |
|---|---|
| Field | `numberInput.field` |
| Clear | `numberInput.toolbar.clear` |
| Sign toggle | `numberInput.toolbar.toggleSign` |
| Done | `numberInput.toolbar.done` |

These identify the *component*, not the instance, so a screen with several fields addresses them by
index. In an iOS accessibility dump they appear as `AXUniqueId`.

## How it works

Shared logic lives in `commonMain` and is UI-framework-free; each platform only renders.

- **Android** — a `BasicTextField` whose buffer stays ungrouped. `NumberGroupingVisualTransformation`
  inserts separators for display with an `OffsetMapping` built by construction, so the caret never
  drifts.
- **iOS** — a native `UITextField` via `UIKitView`, because only UIKit can attach a real
  `inputAccessoryView` to the keyboard. Its buffer *is* grouped, so the field converts at the
  boundary.

`rawText` is always ungrouped on both platforms; grouping is display-only. `commit()` is reached only
by losing focus, and both platforms route Done through focus loss, so there is a single commit path.

## Building

```bash
./gradlew :number-input:testDebugUnitTest      # commonTest + androidTest on the JVM
./gradlew :number-input:iosSimulatorArm64Test  # commonTest + iosTest on a simulator
./gradlew :number-input:allTests               # every target
./gradlew :number-input:publishToMavenLocal    # consume from another project via mavenLocal()
```

<details>
<summary>Releasing to Maven Central (maintainers)</summary>

Publishing needs credentials that are not in this repo. Put them in `~/.gradle/gradle.properties`:

```properties
mavenCentralUsername=<Central Portal token username>
mavenCentralPassword=<Central Portal token password>

signingInMemoryKey=<ASCII-armoured GPG secret key, newlines as \n>
signingInMemoryKeyPassword=<key passphrase>
```

```bash
./gradlew :number-input:publishAndReleaseToMavenCentral
```

Signing is enabled only when a signing key is present, so `publishToMavenLocal` keeps working on a
machine with no GPG key.
</details>

## License

Apache 2.0 — see [LICENSE](LICENSE).
