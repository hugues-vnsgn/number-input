# OFNumpad Styling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give `dev.viethung:number-input` enough generic styling API that a consumer can render the BFSOne OFNumpad design (`numpad-design/OFNumpad Spec.html`) pixel-for-pixel, without any brand value entering the published library.

**Architecture:** `NumberInputStyle` splits into three nested value types — field tokens stay flat, keypad tokens move to `NumberInputKeypadStyle`, toolbar tokens move to `NumberInputToolbarStyle`. Per-key appearance is four `NumberInputKeyStyle` objects (`restKey`, `utilityKey`, `pressedKey`, `disabledKey`), one per swatch in the spec. Every new token defaults so that an unstyled keypad renders exactly as it does at 1.0.0; the olive palette lives only in the sample app. Two new behaviours (haptics, backspace repeat) and three new accessory slots (logo, hint, prev/next) are added alongside.

**Tech Stack:** Kotlin 2.2.20, Compose Multiplatform 1.9.0, `compose.runtime` / `foundation` / `ui` only (no Material3), Android + iOS (`iosX64`, `iosArm64`, `iosSimulatorArm64`).

## Global Constraints

- **Brand neutrality is absolute.** No olive hex value, no `OF1` asset, and no `ofNumpad()` factory may appear in `number-input/src/`. Every OFNumpad value lives in `/Users/hugues_mini/Codes/cmp` (the sample app) only.
- **Defaults render as today.** After this work, `NumberInputStyle()` with nothing set must produce a keypad and toolbar visually identical to v1.0.0. Every new token falls back to an existing resolved token or to "off". The single deliberate exception is the pressed state, which gains a themed default.
- **Version:** bump `number-input/build.gradle.kts:14` from `version = "1.0.0"` to `version = "2.0.0"`. This is a source-breaking release.
- **No new production or test dependencies.** `commonTest` keeps only `kotlin-test`; `iosTest` keeps only `compose.uiTest`. Do not add `kotlinx-coroutines-test`.
- **Toolchain floors are deliberate, not stale.** Do not touch `gradle/libs.versions.toml`. Read the comments there before considering any change.
- **Every Gradle invocation uses the logging convention:**
  ```bash
  mkdir -p .agent/logs && ./gradlew <task> 2>&1 | tee .agent/logs/gradle-$(date +%H%M%S).log | tail -40
  ```
  Never read a full build log into context. To find an error: `grep -n -m5 -E 'e: |error:|FAILURE' .agent/logs/gradle-*.log | tail`.
- **Copy rules:** user-visible labels stay English by default (`Clear`, `Done`) — the spec keeps English labels even in a Vietnamese UI. Every new user-visible string must be a style parameter, never a literal in a composable.
- **The `Key` semantics invariant is load-bearing** and must survive every task: `clickable` supplies `Role.Button` + `onClickLabel`, `semantics(mergeDescendants = true)` layers the `contentDescription` after it, and `clearAndSetSemantics {}` on the inner `BasicText` stops the glyph publishing its own node. Dropping any one reopens a real iOS accessibility defect. `NumberInputKeypadSemanticsTest` guards this; it must stay green in every task.

## Known limitation to record, not to solve

`onPrevious` / `onNext` (Task 3) render only in the **Compose** toolbar bar. On iOS the field is a `UIKitView`-hosted `UITextField` and the library exposes no API for moving focus into another field, so a consumer literally cannot implement a working `onNext` on the iOS system-keyboard path today. The buttons are therefore Android-usable and iOS-usable only via the built-in keypad path where the consumer drives focus itself. Document this in the KDoc; do not attempt to add focus-transfer API in this plan.

## File Structure

**Created in `number-input/src/commonMain/kotlin/dev/viethung/numberinput/`:**

| File | Responsibility |
|---|---|
| `NumberInputKeyStyle.kt` | One key's appearance — a value type used four times (rest / utility / pressed / disabled), plus the role-fallback and state-merge functions. |
| `NumberInputKeypadStyle.kt` | Keypad-wide tokens: container colour, grid metrics, key height/radius, font, backspace icon, and the four key styles. |
| `NumberInputToolbarStyle.kt` | Toolbar tokens: bar colours/metrics, labels, hint, and two `NumberInputToolbarActionStyle` instances (pill actions, Done). |

**Created in `number-input/src/commonTest/kotlin/dev/viethung/numberinput/`:**

| File | Responsibility |
|---|---|
| `NumberInputKeyStyleMergeTest.kt` | The role-fallback and state-merge rules, tested as pure functions. |

**Created in `number-input/src/iosTest/kotlin/dev/viethung/numberinput/`:**

| File | Responsibility |
|---|---|
| `NumberInputKeypadBehaviourTest.kt` | Haptic count and the 400 ms / 80 ms backspace repeat, driven through `runComposeUiTest` with a controlled `mainClock`. |

**Modified:**

| File | Change |
|---|---|
| `NumberInputStyle.kt` | Field tokens only + `toolbar` and `keypad` sub-objects. KDoc rewritten. |
| `NumberInputThemedColors.kt` | Resolves nested styles; supplies role fallback and the pressed default. |
| `NumberInputKeypad.kt` | Per-role/state rendering, shadow lip, grid metrics, icon support, haptics, backspace repeat. |
| `NumberInputHost.kt` | `leadingAccessory` slot; `NumberInputToolbarBar` chrome, hint, prev/next, ± hiding; request carries the new lambdas. |
| `NumberInputField.kt` | `onPrevious` / `onNext` on both overloads and the `expect` seam. |
| `NumberInputField.android.kt` | Passes the new lambdas into the host; reads relocated style properties. |
| `NumberInputField.ios.kt` | Reads relocated style properties; `buildToolbar` omits ± when negatives are locked. |
| `NumberInputConfig.kt` | `keypadHaptics: Boolean = true`. |
| `NumberInputToolbarRules.kt` | `signVisible(allowNegative)`. |
| `TestTags.kt` | Tags for hint, logo, prev, next. |
| `NumberInputStyleResolveTest.kt` | Updated for nested paths + new fallback assertions. |
| `NumberInputKeypadSemanticsTest.kt` | Re-run against the gesture-bearing backspace key. |
| `IosKeypadSuppressionTest.kt` | Updated for relocated style properties + ± omission. |
| `number-input/build.gradle.kts` | Version → `2.0.0`. |
| `README.md`, `CLAUDE.md` | Migration note and architecture updates. |

**Modified in `/Users/hugues_mini/Codes/cmp`:** `gradle/libs.versions.toml`, `shared/.../App.kt`, `shared/.../NumberInputSampleScreen.kt`, `shared/.../NoHostKeypadSampleScreen.kt`, plus new `shared/.../OFNumpadSampleScreen.kt` and `shared/.../OFNumpadTokens.kt`.

---

### Task 0: Land the in-flight dark-mode and keypad-animation work

There are ~758 uncommitted insertions on `master` implementing the keypad enter/exit animation and dark-mode resolution. Every later task modifies those same files. Land them first so the restructure has a green, bisectable baseline.

**Files:**
- Commit (modified): `CLAUDE.md`, `README.md`, `number-input/src/androidMain/kotlin/dev/viethung/numberinput/NumberInputField.android.kt`, `number-input/src/commonMain/kotlin/dev/viethung/numberinput/NumberInputHost.kt`, `number-input/src/commonMain/kotlin/dev/viethung/numberinput/NumberInputStyle.kt`, `number-input/src/iosMain/kotlin/dev/viethung/numberinput/NumberInputField.ios.kt`, `number-input/src/iosTest/kotlin/dev/viethung/numberinput/IosKeypadSuppressionTest.kt`
- Commit (untracked): `number-input/src/commonMain/kotlin/dev/viethung/numberinput/NumberInputThemedColors.kt`, `number-input/src/commonTest/kotlin/dev/viethung/numberinput/NumberInputStyleResolveTest.kt`, `numpad-design/`
- Do **not** commit: `.agent/`, `.agents/`, `skills-lock.json`

**Interfaces:**
- Produces: a clean working tree at a known-green commit. No code changes.

- [ ] **Step 1: Confirm the JVM suite is green**

```bash
mkdir -p .agent/logs && ./gradlew :number-input:testDebugUnitTest 2>&1 | tee .agent/logs/gradle-$(date +%H%M%S).log | tail -40
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Confirm the iOS simulator suite is green**

```bash
./gradlew :number-input:iosSimulatorArm64Test 2>&1 | tee .agent/logs/gradle-$(date +%H%M%S).log | tail -40
```
Expected: `BUILD SUCCESSFUL`. If it fails, stop and report — do not start Task 1 on a red baseline.

- [ ] **Step 3: Add the agent scratch directories to `.gitignore`**

Append to `.gitignore`:
```
.agent/
.agents/
skills-lock.json
```

- [ ] **Step 4: Commit the in-flight feature**

```bash
git add .gitignore CLAUDE.md README.md numpad-design \
  number-input/src/androidMain/kotlin/dev/viethung/numberinput/NumberInputField.android.kt \
  number-input/src/commonMain/kotlin/dev/viethung/numberinput/NumberInputHost.kt \
  number-input/src/commonMain/kotlin/dev/viethung/numberinput/NumberInputStyle.kt \
  number-input/src/commonMain/kotlin/dev/viethung/numberinput/NumberInputThemedColors.kt \
  number-input/src/commonTest/kotlin/dev/viethung/numberinput/NumberInputStyleResolveTest.kt \
  number-input/src/iosMain/kotlin/dev/viethung/numberinput/NumberInputField.ios.kt \
  number-input/src/iosTest/kotlin/dev/viethung/numberinput/IosKeypadSuppressionTest.kt
git commit -m "Animate the built-in keypad and follow the device appearance"
```

- [ ] **Step 5: Verify the tree is clean apart from ignored scratch**

```bash
git status --short
```
Expected: no output.

---

### Task 1: Split `NumberInputStyle` into nested sub-styles (2.0.0, no visual change)

A pure refactor. Nothing renders differently; every existing test must pass unchanged except for property paths. This is the commit that proves the restructure is behaviour-preserving before any styling can mask a regression.

**Files:**
- Create: `number-input/src/commonMain/kotlin/dev/viethung/numberinput/NumberInputKeypadStyle.kt`
- Create: `number-input/src/commonMain/kotlin/dev/viethung/numberinput/NumberInputToolbarStyle.kt`
- Modify: `number-input/src/commonMain/kotlin/dev/viethung/numberinput/NumberInputStyle.kt`
- Modify: `number-input/src/commonMain/kotlin/dev/viethung/numberinput/NumberInputThemedColors.kt`
- Modify: `number-input/src/commonMain/kotlin/dev/viethung/numberinput/NumberInputKeypad.kt`
- Modify: `number-input/src/commonMain/kotlin/dev/viethung/numberinput/NumberInputHost.kt`
- Modify: `number-input/src/androidMain/kotlin/dev/viethung/numberinput/NumberInputField.android.kt`
- Modify: `number-input/src/iosMain/kotlin/dev/viethung/numberinput/NumberInputField.ios.kt`
- Modify: `number-input/build.gradle.kts:14`
- Test: `number-input/src/commonTest/kotlin/dev/viethung/numberinput/NumberInputStyleResolveTest.kt`
- Test: `number-input/src/iosTest/kotlin/dev/viethung/numberinput/IosKeypadSuppressionTest.kt`

**Interfaces:**
- Produces:
  - `data class NumberInputKeypadStyle(backgroundColor: Color, keyHeight: Dp, keyCornerRadius: Dp, backspaceLabel: String, backspaceContentDescription: String, decimalContentDescription: String, restKey: NumberInputKeyStyle, utilityKey: NumberInputKeyStyle, pressedKey: NumberInputKeyStyle, disabledKey: NumberInputKeyStyle)` — Task 2 adds grid metrics, font and icon fields.
  - `data class NumberInputToolbarStyle(backgroundColor: Color, tint: Color, clearLabel: String, signLabel: String, doneLabel: String)` — Task 3 adds chrome, hint and nav fields.
  - `NumberInputStyle.keypad: NumberInputKeypadStyle`, `NumberInputStyle.toolbar: NumberInputToolbarStyle`.
  - `internal fun resolveThemedColors(style: NumberInputStyle, dark: Boolean): NumberInputStyle` — unchanged signature, nested body.

- [ ] **Step 1: Write `NumberInputKeyStyle.kt` with the merge rules and their tests first**

Create `number-input/src/commonTest/kotlin/dev/viethung/numberinput/NumberInputKeyStyleMergeTest.kt`:

```kotlin
package dev.viethung.numberinput

import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The two rules that decide what a key actually looks like.
 *
 * They are separate on purpose and easy to conflate. *Role fallback* runs once, at resolution:
 * anything the utility key leaves unset comes from the rest key, so a consumer restyling only the
 * decimal fill does not have to restate its glyph size. *State merge* runs at draw time and
 * contributes colours only — geometry always stays with the role, because an integer-only field
 * disables its decimal key permanently, and a disabled state that carried geometry would leave that
 * one key centred at the digit size while every neighbour is bottom-aligned at 26sp.
 */
class NumberInputKeyStyleMergeTest {

    private val role = NumberInputKeyStyle(
        backgroundColor = Color.White,
        contentColor = Color.Black,
        textSize = 26.sp,
        fontWeight = FontWeight.SemiBold,
        contentAlignment = Alignment.BottomCenter,
        contentBottomPadding = 10.dp,
        shadowColor = Color.Gray,
    )

    @Test
    fun role_fallback_fills_only_the_unset_values() {
        val base = NumberInputKeyStyle(backgroundColor = Color.White, contentColor = Color.Black, textSize = 24.sp)
        val utility = NumberInputKeyStyle(backgroundColor = Color.Red)

        val resolved = utility.fallingBackTo(base)

        assertEquals(Color.Red, resolved.backgroundColor)
        assertEquals(Color.Black, resolved.contentColor)
        assertEquals(24.sp, resolved.textSize)
    }

    @Test
    fun state_merge_replaces_colours() {
        val pressed = NumberInputKeyStyle(backgroundColor = Color.Yellow, contentColor = Color.Blue)

        val merged = role.mergedWithState(pressed)

        assertEquals(Color.Yellow, merged.backgroundColor)
        assertEquals(Color.Blue, merged.contentColor)
    }

    @Test
    fun state_merge_keeps_the_roles_geometry() {
        val pressed = NumberInputKeyStyle(backgroundColor = Color.Yellow)

        val merged = role.mergedWithState(pressed)

        assertEquals(26.sp, merged.textSize)
        assertEquals(FontWeight.SemiBold, merged.fontWeight)
        assertEquals(Alignment.BottomCenter, merged.contentAlignment)
        assertEquals(10.dp, merged.contentBottomPadding)
    }

    @Test
    fun an_unset_state_colour_leaves_the_roles_colour_alone() {
        val merged = role.mergedWithState(NumberInputKeyStyle())

        assertEquals(Color.White, merged.backgroundColor)
        assertEquals(Color.Black, merged.contentColor)
    }

    /** Border width follows border colour, so a state without a border cannot erase the role's. */
    @Test
    fun border_width_travels_with_border_colour() {
        val bordered = role.copy(borderColor = Color.Green, borderWidth = 2.dp)

        val withStateBorder = bordered.mergedWithState(
            NumberInputKeyStyle(borderColor = Color.Magenta, borderWidth = 5.dp),
        )
        assertEquals(Color.Magenta, withStateBorder.borderColor)
        assertEquals(5.dp, withStateBorder.borderWidth)

        val withoutStateBorder = bordered.mergedWithState(NumberInputKeyStyle(backgroundColor = Color.Yellow))
        assertEquals(Color.Green, withoutStateBorder.borderColor)
        assertEquals(2.dp, withoutStateBorder.borderWidth)
    }

    /** The lip is a resting affordance; a pressed key that still has one does not look pressed. */
    @Test
    fun the_shadow_lip_belongs_to_the_role_and_is_never_merged_from_a_state() {
        val merged = role.mergedWithState(NumberInputKeyStyle(backgroundColor = Color.Yellow, shadowColor = Color.Cyan))

        assertEquals(Color.Gray, merged.shadowColor)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :number-input:testDebugUnitTest --tests '*NumberInputKeyStyleMergeTest' 2>&1 | tee .agent/logs/gradle-$(date +%H%M%S).log | tail -40
```
Expected: FAIL — `Unresolved reference 'NumberInputKeyStyle'`.

- [ ] **Step 3: Create `NumberInputKeyStyle.kt`**

Create `number-input/src/commonMain/kotlin/dev/viethung/numberinput/NumberInputKeyStyle.kt`:

```kotlin
package dev.viethung.numberinput

import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified

/**
 * How one key of the built-in keypad is drawn.
 *
 * Four of these make up a keypad — `restKey`, `utilityKey`, `pressedKey`, `disabledKey` on
 * [NumberInputKeypadStyle] — which is one per swatch in a design spec rather than a role-by-state
 * matrix, because pressed and disabled look the same whichever key is in them.
 *
 * Two rules decide the value actually drawn, and they do different jobs:
 *
 * - **Role fallback** ([fallingBackTo]) runs once, during theme resolution: `utilityKey` takes
 *   anything it left unset from `restKey`. Restyling only the decimal key's fill should not require
 *   restating its glyph size.
 * - **State merge** ([mergedWithState]) runs at draw time and contributes **colours only**. Geometry
 *   always stays with the role. An integer-only field disables its decimal key for the field's whole
 *   life, so a disabled state that carried geometry would strand that one key at the digit size and
 *   alignment while every neighbour kept the utility treatment.
 *
 * [shadowColor] is deliberately outside the state merge: the lip is a resting affordance, and a
 * pressed key that still has one does not read as pressed.
 */
data class NumberInputKeyStyle(
    val backgroundColor: Color = Color.Unspecified,
    val contentColor: Color = Color.Unspecified,
    val borderColor: Color = Color.Unspecified,
    val borderWidth: Dp = 0.dp,
    /** A hard 1dp lip under the key, not an elevation shadow. Unspecified draws no lip at all. */
    val shadowColor: Color = Color.Unspecified,
    val shadowHeight: Dp = 1.dp,
    val textSize: TextUnit = TextUnit.Unspecified,
    val fontWeight: FontWeight? = null,
    val contentAlignment: Alignment = Alignment.Center,
    /** Only meaningful with a bottom-ish [contentAlignment]; the spec's decimal key sits 10dp up. */
    val contentBottomPadding: Dp = 0.dp,
)

/** Role fallback: anything unset here comes from [base]. Resolution-time, colours *and* geometry. */
internal fun NumberInputKeyStyle.fallingBackTo(base: NumberInputKeyStyle): NumberInputKeyStyle = copy(
    backgroundColor = backgroundColor.takeOrElse { base.backgroundColor },
    contentColor = contentColor.takeOrElse { base.contentColor },
    borderColor = borderColor.takeOrElse { base.borderColor },
    borderWidth = if (borderColor.isSpecified) borderWidth else base.borderWidth,
    shadowColor = shadowColor.takeOrElse { base.shadowColor },
    shadowHeight = if (shadowColor.isSpecified) shadowHeight else base.shadowHeight,
    textSize = if (textSize.isSpecified) textSize else base.textSize,
    fontWeight = fontWeight ?: base.fontWeight,
)

/**
 * State merge: [state]'s colours win, this role's geometry is kept. Border width travels with border
 * colour so a state that specifies no border cannot erase the role's.
 */
internal fun NumberInputKeyStyle.mergedWithState(state: NumberInputKeyStyle): NumberInputKeyStyle = copy(
    backgroundColor = state.backgroundColor.takeOrElse { backgroundColor },
    contentColor = state.contentColor.takeOrElse { contentColor },
    borderColor = state.borderColor.takeOrElse { borderColor },
    borderWidth = if (state.borderColor.isSpecified) state.borderWidth else borderWidth,
)
```

- [ ] **Step 4: Run the merge test to verify it passes**

```bash
./gradlew :number-input:testDebugUnitTest --tests '*NumberInputKeyStyleMergeTest' 2>&1 | tee .agent/logs/gradle-$(date +%H%M%S).log | tail -40
```
Expected: PASS.

- [ ] **Step 5: Create `NumberInputKeypadStyle.kt`**

```kotlin
package dev.viethung.numberinput

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Styling for the built-in keypad. Only read when [NumberInputConfig.useBuiltInKeypad] is on.
 *
 * Separate from the field's own colours because the keypad stands in for the system keyboard: it
 * should look like a keyboard sitting under the content, not a larger version of the field.
 *
 * [backgroundColor] and the four key styles' colours default to [Color.Unspecified] and are resolved
 * against the device appearance — see [resolveThemedColors]. Set any of them to pin one value in both
 * appearances. Everything else is a literal default matching the library's 1.x rendering.
 */
data class NumberInputKeypadStyle(
    val backgroundColor: Color = Color.Unspecified,
    val keyHeight: Dp = 48.dp,
    val keyCornerRadius: Dp = 5.dp,
    /**
     * The backspace glyph, and the names a screen reader speaks for the two keys whose labels are
     * punctuation or a symbol. Localise both descriptions: a symbol has no spoken name of its own, and
     * "." and "," sound identical read aloud even when the reader announces them at all.
     *
     * The decimal key's *label* is not configurable — it is the locale's separator, which is the whole
     * reason the keypad exists.
     */
    val backspaceLabel: String = "⌫",
    val backspaceContentDescription: String = "Delete",
    val decimalContentDescription: String = "Decimal separator",

    /** Digit keys, and the base every other key style falls back to. */
    val restKey: NumberInputKeyStyle = NumberInputKeyStyle(),
    /** Decimal and backspace keys. Anything left unset here comes from [restKey]. */
    val utilityKey: NumberInputKeyStyle = NumberInputKeyStyle(),
    /** Colours applied while a key is held. Geometry is ignored — see [NumberInputKeyStyle]. */
    val pressedKey: NumberInputKeyStyle = NumberInputKeyStyle(),
    /**
     * Colours applied while a key is refused by [NumberInputKeypadRules]. Left unset, the key falls
     * back to multiplying its content colour by [NumberInputStyle.disabledAlpha], which is what 1.x did.
     */
    val disabledKey: NumberInputKeyStyle = NumberInputKeyStyle(),
)
```

- [ ] **Step 6: Create `NumberInputToolbarStyle.kt`**

```kotlin
package dev.viethung.numberinput

import androidx.compose.ui.graphics.Color

/**
 * Styling for the Clear / ± / Done row.
 *
 * Drawn in Compose on Android (riding the IME inside [NumberInputHost], or inline without one) and as
 * the built-in keypad's own top row on both platforms. iOS's system-keyboard path builds a native
 * `UIToolbar` instead, which reads [backgroundColor], [tint] and the three labels and nothing else —
 * that accessory is the system's, and it should look like one.
 *
 * [backgroundColor] and [tint] default to [Color.Unspecified] and follow the device appearance; see
 * [resolveThemedColors].
 */
data class NumberInputToolbarStyle(
    val backgroundColor: Color = Color.Unspecified,
    val tint: Color = Color.Unspecified,
    /** Localise these — the defaults are English and will otherwise ship to every user. */
    val clearLabel: String = "Clear",
    val signLabel: String = "±",
    val doneLabel: String = "Done",
)
```

- [ ] **Step 7: Rewrite `NumberInputStyle.kt`**

Replace the whole file with:

```kotlin
package dev.viethung.numberinput

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Styling for a number input.
 *
 * The properties here are the *field's* — each one maps to both a Compose `BasicTextField` (Android)
 * and a `UITextField` (iOS). Anything that maps to only one platform is omitted rather than accepted
 * and silently ignored, which is why there is no `FontFamily` here: a Compose `FontFamily` cannot
 * cross into UIKit, so setting one would style the field on Android and do nothing on iOS. The keypad
 * and toolbar *are* Compose on both platforms, so [NumberInputKeypadStyle.fontFamily] and
 * [NumberInputToolbarStyle.fontFamily] do exist.
 *
 * **Still not offered, by design:** `Brush`/gradients, arbitrary `Shape` beyond [cornerRadius],
 * `letterSpacing`, `lineHeight`, and `textDecoration`.
 *
 * Field defaults are neutral rather than themed — this library depends on `compose.foundation`, not
 * Material, so it has no theme to read. Pass your own design system's values.
 *
 * The colours this library draws *itself* — inside [keypad] and [toolbar] — are the exception. They
 * default to [Color.Unspecified] and are resolved against the device's light/dark appearance, since
 * there is no design system a consumer can bring for a stand-in system keyboard. Set any of them
 * explicitly to opt out and pin one colour in both appearances. See [resolveThemedColors].
 */
data class NumberInputStyle(
    val textColor: Color = Color.Black,
    val textSize: TextUnit = 16.sp,
    val textWeight: FontWeight = FontWeight.Normal,
    val textAlign: TextAlign = TextAlign.Start,
    val placeholderColor: Color = Color(0xFF9E9E9E),
    val backgroundColor: Color = Color.Transparent,
    val borderColor: Color = Color(0xFF9E9E9E),
    val borderWidth: Dp = 1.dp,
    val cornerRadius: Dp = 4.dp,
    val cursorColor: Color = Color.Black,
    /**
     * Multiplied into a disabled element's content colour. Still the fallback for a disabled key when
     * [NumberInputKeypadStyle.disabledKey] leaves its colours unset.
     */
    val disabledAlpha: Float = 0.38f,

    val toolbar: NumberInputToolbarStyle = NumberInputToolbarStyle(),
    val keypad: NumberInputKeypadStyle = NumberInputKeypadStyle(),
)
```

- [ ] **Step 8: Rewrite `NumberInputThemedColors.kt` for the nested shape**

Replace `resolveThemedColors` with:

```kotlin
internal fun resolveThemedColors(style: NumberInputStyle, dark: Boolean): NumberInputStyle {
    val restKey = style.keypad.restKey.fallingBackTo(
        NumberInputKeyStyle(
            backgroundColor = if (dark) Color(0xFF6B6B6E) else Color.White,
            contentColor = if (dark) Color.White else Color.Black,
            textSize = 22.sp,
        ),
    )
    return style.copy(
        toolbar = style.toolbar.copy(
            backgroundColor = style.toolbar.backgroundColor.takeOrElse {
                if (dark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
            },
            tint = style.toolbar.tint.takeOrElse {
                if (dark) Color(0xFF0A84FF) else Color(0xFF007AFF)
            },
        ),
        keypad = style.keypad.copy(
            backgroundColor = style.keypad.backgroundColor.takeOrElse {
                if (dark) Color(0xFF2C2C2E) else Color(0xFFD1D3D9)
            },
            restKey = restKey,
            // Role fallback: an unstyled utility key is indistinguishable from a digit key, which is
            // exactly what 1.x drew.
            utilityKey = style.keypad.utilityKey.fallingBackTo(restKey),
            // The one new token with a themed default rather than a "render as before" fallback: 1.x
            // gave a held key no feedback at all, and a replacement keyboard that does not respond to
            // touch reads as broken next to the system one it stands in for.
            pressedKey = style.keypad.pressedKey.copy(
                backgroundColor = style.keypad.pressedKey.backgroundColor.takeOrElse {
                    if (dark) Color(0xFF8A8A8E) else Color(0xFFD8D8DD)
                },
            ),
            // Deliberately *not* given a palette: unset disabled colours mean "multiply by
            // disabledAlpha", which is 1.x's rendering. See NumberInputKeypad.Key.
            disabledKey = style.keypad.disabledKey,
        ),
    )
}
```

Add the imports `androidx.compose.ui.unit.sp` and keep `androidx.compose.ui.graphics.takeOrElse`.

- [ ] **Step 9: Update every call site to the nested paths**

Mechanical renames — apply each exactly:

| Old | New |
|---|---|
| `style.toolbarBackgroundColor` | `style.toolbar.backgroundColor` |
| `style.toolbarTint` | `style.toolbar.tint` |
| `style.clearLabel` / `signLabel` / `doneLabel` | `style.toolbar.clearLabel` / `.signLabel` / `.doneLabel` |
| `style.keypadBackgroundColor` | `style.keypad.backgroundColor` |
| `style.keyBackgroundColor` | `style.keypad.restKey.backgroundColor` |
| `style.keyTextColor` | `style.keypad.restKey.contentColor` |
| `style.keyTextSize` | `style.keypad.restKey.textSize` |
| `style.keyHeight` | `style.keypad.keyHeight` |
| `style.keyCornerRadius` | `style.keypad.keyCornerRadius` |
| `style.backspaceLabel` | `style.keypad.backspaceLabel` |
| `style.backspaceContentDescription` | `style.keypad.backspaceContentDescription` |
| `style.decimalContentDescription` | `style.keypad.decimalContentDescription` |

Files to sweep: `NumberInputKeypad.kt`, `NumberInputHost.kt`, `NumberInputField.android.kt`, `NumberInputField.ios.kt`, `IosKeypadSuppressionTest.kt`, `NumberInputKeypadSemanticsTest.kt`.

Find them all with:
```bash
grep -rn "toolbarBackgroundColor\|toolbarTint\|keypadBackgroundColor\|keyBackgroundColor\|keyTextColor\|keyTextSize\|keyHeight\|keyCornerRadius\|backspaceLabel\|backspaceContentDescription\|decimalContentDescription\|\.clearLabel\|\.signLabel\|\.doneLabel" number-input/src
```

In `NumberInputKeypad.kt`'s `Key`, the `restKey.textSize` is now a `TextUnit` that resolution guarantees is specified; keep the existing alpha multiply exactly as it was:

```kotlin
val alpha = if (enabled) 1f else style.disabledAlpha
// ...
color = style.keypad.restKey.contentColor.copy(
    alpha = style.keypad.restKey.contentColor.alpha * alpha,
),
fontSize = style.keypad.restKey.textSize,
```

- [ ] **Step 10: Update `NumberInputStyleResolveTest.kt` to the nested paths and the new fallbacks**

Rewrite the helpers and assertions. The palette values are unchanged, so the existing expectations hold — only the paths move. Replace `styleWithEveryThemedColourSet()` and `assertEveryThemedColourIs()` with:

```kotlin
    private fun styleWithEveryThemedColourSet() = NumberInputStyle(
        keypad = NumberInputKeypadStyle(
            backgroundColor = sentinelOverride,
            restKey = NumberInputKeyStyle(
                backgroundColor = sentinelOverride,
                contentColor = sentinelOverride,
            ),
        ),
        toolbar = NumberInputToolbarStyle(
            backgroundColor = sentinelOverride,
            tint = sentinelOverride,
        ),
    )

    private fun assertEveryThemedColourIs(expected: Color, resolved: NumberInputStyle) {
        assertEquals(expected, resolved.keypad.backgroundColor)
        assertEquals(expected, resolved.keypad.restKey.backgroundColor)
        assertEquals(expected, resolved.keypad.restKey.contentColor)
        assertEquals(expected, resolved.toolbar.backgroundColor)
        assertEquals(expected, resolved.toolbar.tint)
    }
```

Add these two tests, which are what pin the "renders as today" guarantee:

```kotlin
    /**
     * An unstyled utility key must be indistinguishable from a digit key — that is what 1.x drew, and
     * the role fallback is the only thing keeping it that way.
     */
    @Test
    fun an_unset_utility_key_is_identical_to_the_rest_key() {
        val resolved = resolveThemedColors(NumberInputStyle(), dark = false)

        assertEquals(resolved.keypad.restKey, resolved.keypad.utilityKey)
    }

    /**
     * Disabled colours are deliberately left unset by the palette: unset means "multiply the content
     * colour by disabledAlpha", which is exactly 1.x's disabled key. Giving them a palette entry here
     * would change how every existing consumer's keypad renders on upgrade.
     */
    @Test
    fun disabled_key_colours_are_not_substituted_by_either_palette() {
        for (dark in listOf(false, true)) {
            val resolved = resolveThemedColors(NumberInputStyle(), dark = dark)

            assertEquals(Color.Unspecified, resolved.keypad.disabledKey.backgroundColor)
            assertEquals(Color.Unspecified, resolved.keypad.disabledKey.contentColor)
        }
    }
```

Update `non_colour_properties_are_left_alone` to use the nested paths:

```kotlin
    @Test
    fun non_colour_properties_are_left_alone() {
        val style = NumberInputStyle(
            toolbar = NumberInputToolbarStyle(clearLabel = "Xoá", doneLabel = "Xong"),
            keypad = NumberInputKeypadStyle(backspaceContentDescription = "Xoá ký tự"),
        )
        val resolved = resolveThemedColors(style, dark = true)

        assertEquals("Xoá", resolved.toolbar.clearLabel)
        assertEquals("Xong", resolved.toolbar.doneLabel)
        assertEquals("Xoá ký tự", resolved.keypad.backspaceContentDescription)
        assertEquals(style.keypad.keyHeight, resolved.keypad.keyHeight)
        assertEquals(style.disabledAlpha, resolved.disabledAlpha)
    }
```

Update `a_partial_override_leaves_the_rest_to_the_palette` and `an_explicit_transparent_is_a_choice_not_an_absence` to construct through `NumberInputKeypadStyle(...)` / `NumberInputKeyStyle(...)` and assert on `resolved.keypad.*`.

- [ ] **Step 11: Bump the version**

In `number-input/build.gradle.kts:14`, change `version = "1.0.0"` to `version = "2.0.0"`.

- [ ] **Step 12: Run the full JVM suite**

```bash
./gradlew :number-input:testDebugUnitTest 2>&1 | tee .agent/logs/gradle-$(date +%H%M%S).log | tail -40
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 13: Run the full iOS suite**

```bash
./gradlew :number-input:iosSimulatorArm64Test 2>&1 | tee .agent/logs/gradle-$(date +%H%M%S).log | tail -40
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 14: Commit**

```bash
git add -A number-input docs
git commit -m "Group styling into field, toolbar and keypad objects

Breaking: the eight keypad and toolbar properties move off NumberInputStyle
into NumberInputKeypadStyle and NumberInputToolbarStyle. Nothing renders
differently — resolveThemedColors substitutes the same palettes at the new
paths, and an unstyled utility key falls back to the rest key so it stays
indistinguishable from a digit key."
```

---

### Task 2: Keypad visual parity

Adds the tokens the spec's keypad needs: grid metrics, per-role fills, pressed and disabled treatments, the hard shadow lip, font family and tabular figures, and the optional backspace icon.

**Files:**
- Modify: `number-input/src/commonMain/kotlin/dev/viethung/numberinput/NumberInputKeypadStyle.kt`
- Modify: `number-input/src/commonMain/kotlin/dev/viethung/numberinput/NumberInputKeypad.kt`
- Test: `number-input/src/iosTest/kotlin/dev/viethung/numberinput/NumberInputKeypadSemanticsTest.kt`

**Interfaces:**
- Consumes: `NumberInputKeyStyle`, `fallingBackTo`, `mergedWithState`, `NumberInputKeypadStyle` from Task 1.
- Produces: `NumberInputKeypadStyle` gains `contentPadding: Dp`, `keySpacing: Dp`, `fontFamily: FontFamily?`, `tabularFigures: Boolean`, `backspaceIcon: ImageVector?`, `backspaceIconWidth: Dp`, `backspaceIconHeight: Dp`.

- [ ] **Step 1: Add the new fields to `NumberInputKeypadStyle`**

Insert after `keyCornerRadius`:

```kotlin
    /** Padding around the whole key grid. 1.x used 3dp on every side; the OFNumpad spec is 8dp. */
    val contentPadding: Dp = 3.dp,
    /** Gap between keys, horizontally and vertically. 1.x used 6dp; the OFNumpad spec is 7dp. */
    val keySpacing: Dp = 6.dp,
    /**
     * Face for every key glyph. Unlike the field's text — which is a `UITextField` on iOS and so
     * cannot take a Compose family — the keypad is Compose on both platforms, so this works
     * everywhere. The consumer bundles and registers the font; the library only accepts the family it
     * is handed.
     */
    val fontFamily: FontFamily? = null,
    /**
     * Fixed-width figures (`tnum`). Off by default because it changes the metrics of every existing
     * consumer's keypad. Design specs that show a grid of digits almost always want it on.
     */
    val tabularFigures: Boolean = false,
    /**
     * Drawn instead of [backspaceLabel] when set, tinted with the key's resolved content colour.
     *
     * Worth setting alongside [fontFamily]: `⌫` is U+232B, which brand fonts frequently omit, and a
     * missing glyph renders as a blank box rather than failing loudly.
     */
    val backspaceIcon: ImageVector? = null,
    val backspaceIconWidth: Dp = 26.dp,
    val backspaceIconHeight: Dp = 20.dp,
```

Add imports: `androidx.compose.ui.graphics.vector.ImageVector`, `androidx.compose.ui.text.font.FontFamily`.

- [ ] **Step 2: Rewrite `KeyRow` to use the metric tokens**

In `NumberInputKeypad.kt`, replace `KeyRow` and its call sites so the style reaches it:

```kotlin
@Composable
private fun KeyRow(style: NumberInputStyle, content: @Composable (Modifier) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = style.keypad.contentPadding, vertical = style.keypad.keySpacing / 2),
        horizontalArrangement = Arrangement.spacedBy(style.keypad.keySpacing),
    ) {
        content(Modifier.weight(1f))
    }
}
```

Update the three call sites in `NumberInputKeypad` to `KeyRow(style) { keyModifier -> ... }`.

Note the vertical padding: the spec states an 8dp grid padding and a 7dp gap, and rows are separate `Row`s, so each row contributes `keySpacing / 2` above and below — half-gaps meet to make one full gap between rows. Adjacent rows therefore sit `keySpacing` apart, and the grid's outer vertical inset is `keySpacing / 2` rather than `contentPadding`. Record this in the token table when verifying: with the spec's 7dp gap the outer vertical inset is 3.5dp, not 8dp, so the sample must add the difference as padding on the keypad container if the top/bottom inset matters visually.

- [ ] **Step 3: Rewrite `Key` for roles, states and the lip**

Replace the `Key` composable with:

```kotlin
/**
 * One key. Width comes from the row's weight so three columns fill any screen; the height is fixed so
 * the grid does not stretch on a tablet.
 *
 * The key publishes itself as a single button node, and the ordering that achieves it is load-bearing.
 *
 * `clickable` supplies the `Role.Button` and the click action, and it must come *after* `testTag` and
 * *before* the label's own semantics, with `clearAndSetSemantics` on the inner text so the glyph does
 * not surface as a node of its own. Verified on a simulator: with the text left to publish itself, the
 * digit keys reached the iOS accessibility tree as bare static text — no identifier, no button role,
 * frames the size of the glyph rather than the key — so VoiceOver could not operate them and a UI test
 * could not tap them. The backspace key looked fine in the same tree only because it carried a
 * `contentDescription`, which masked the same defect.
 *
 * The shadow lip is drawn behind the key rather than with `Modifier.shadow`: the spec's
 * `0 1px 0 #C7C7B5` is a hard offset edge with no blur and no spread, which an elevation shadow cannot
 * produce. It is suppressed while pressed or disabled — a key that keeps its lip does not read as
 * either.
 */
@Composable
private fun Key(
    label: String,
    enabled: Boolean,
    role: NumberInputKeyStyle,
    style: NumberInputStyle,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier,
    contentDescription: String = label,
    icon: ImageVector? = null,
    iconWidth: Dp = 0.dp,
    iconHeight: Dp = 0.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val drawn = when {
        !enabled -> role.mergedWithState(style.keypad.disabledKey)
        pressed -> role.mergedWithState(style.keypad.pressedKey)
        else -> role
    }
    // An unset disabled colour means "multiply by disabledAlpha", which is what 1.x drew. Set colours
    // are used as-is, so a spec that names an explicit disabled fill is not also dimmed.
    val contentAlpha =
        if (!enabled && style.keypad.disabledKey.contentColor == Color.Unspecified) style.disabledAlpha else 1f
    val shape = RoundedCornerShape(style.keypad.keyCornerRadius)
    val lipVisible = enabled && !pressed && drawn.shadowColor != Color.Unspecified

    Box(
        modifier = modifier
            .height(style.keypad.keyHeight + if (lipVisible) drawn.shadowHeight else 0.dp)
            .testTag(testTag)
            .drawBehind {
                if (!lipVisible) return@drawBehind
                // The lip is the key's own silhouette, pushed down by shadowHeight and drawn first, so
                // only the sliver below the key's bottom edge remains visible.
                drawRoundRect(
                    color = drawn.shadowColor,
                    topLeft = Offset(0f, drawn.shadowHeight.toPx()),
                    size = Size(size.width, size.height - drawn.shadowHeight.toPx()),
                    cornerRadius = CornerRadius(style.keypad.keyCornerRadius.toPx()),
                )
            }
            .then(
                if (lipVisible) Modifier.padding(bottom = drawn.shadowHeight) else Modifier,
            )
            .background(drawn.backgroundColor, shape)
            .then(
                if (drawn.borderColor != Color.Unspecified && drawn.borderWidth > 0.dp) {
                    Modifier.border(drawn.borderWidth, drawn.borderColor, shape)
                } else {
                    Modifier
                },
            )
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = contentDescription,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                if (!enabled) disabled()
            },
        contentAlignment = drawn.contentAlignment,
    ) {
        val contentColor = drawn.contentColor.copy(alpha = drawn.contentColor.alpha * contentAlpha)
        val contentModifier = Modifier
            .padding(bottom = drawn.contentBottomPadding)
            // The glyph is decoration: the key above already carries the spoken name, and leaving the
            // text to publish itself is what put a second, unlabelled node in the tree.
            .clearAndSetSemantics {}

        if (icon != null) {
            Image(
                imageVector = icon,
                contentDescription = null,
                colorFilter = ColorFilter.tint(contentColor),
                modifier = contentModifier.size(iconWidth, iconHeight),
            )
        } else {
            BasicText(
                text = label,
                style = TextStyle(
                    color = contentColor,
                    fontSize = drawn.textSize,
                    fontWeight = drawn.fontWeight,
                    fontFamily = style.keypad.fontFamily,
                    fontFeatureSettings = if (style.keypad.tabularFigures) "tnum" else null,
                    textAlign = TextAlign.Center,
                ),
                modifier = contentModifier,
            )
        }
    }
}
```

Add imports: `androidx.compose.foundation.Image`, `androidx.compose.foundation.border`, `androidx.compose.foundation.interaction.MutableInteractionSource`, `androidx.compose.foundation.interaction.collectIsPressedAsState`, `androidx.compose.foundation.layout.size`, `androidx.compose.runtime.getValue`, `androidx.compose.runtime.remember`, `androidx.compose.ui.draw.drawBehind`, `androidx.compose.ui.geometry.CornerRadius`, `androidx.compose.ui.geometry.Offset`, `androidx.compose.ui.geometry.Size`, `androidx.compose.ui.graphics.Color`, `androidx.compose.ui.graphics.ColorFilter`, `androidx.compose.ui.graphics.vector.ImageVector`, `androidx.compose.ui.unit.Dp`.

`indication = null` is deliberate: the spec's pressed state *is* the indication, and Compose's default ripple would draw a second, un-styleable one on top of it. (`compose.foundation`'s default indication is a platform ripple; leaving it on would also contradict "renders as today", since 1.x's `clickable` had no `interactionSource` to drive one consistently.)

- [ ] **Step 4: Route the roles into the three key composables**

```kotlin
@Composable
private fun DigitKey(
    digit: Int,
    state: NumberInputState,
    style: NumberInputStyle,
    modifier: Modifier,
) {
    Key(
        label = digit.toString(),
        enabled = state.digitEnabled,
        role = style.keypad.restKey,
        style = style,
        testTag = keypadDigitTag(digit),
        onClick = { state.pressDigit(digit) },
        modifier = modifier,
    )
}

@Composable
private fun DecimalKey(state: NumberInputState, style: NumberInputStyle, modifier: Modifier) {
    Key(
        // The locale's separator, so the key never disagrees with the text it produces.
        label = state.decimalKeyLabel,
        enabled = state.decimalEnabled,
        role = style.keypad.utilityKey,
        style = style,
        testTag = TAG_KEYPAD_DECIMAL,
        // "." and "," are punctuation: a screen reader may announce the glyph as nothing at all, and
        // the two are indistinguishable spoken even when it does. The label stays the glyph.
        contentDescription = style.keypad.decimalContentDescription,
        onClick = state::pressDecimalSeparator,
        modifier = modifier,
    )
}

@Composable
private fun BackspaceKey(state: NumberInputState, style: NumberInputStyle, modifier: Modifier) {
    Key(
        label = style.keypad.backspaceLabel,
        enabled = state.backspaceEnabled,
        role = style.keypad.utilityKey,
        style = style,
        testTag = TAG_KEYPAD_BACKSPACE,
        // The glyph is a symbol, so it needs a spoken name of its own — a screen reader would
        // otherwise announce the character itself, or nothing.
        contentDescription = style.keypad.backspaceContentDescription,
        onClick = state::pressBackspace,
        modifier = modifier,
        icon = style.keypad.backspaceIcon,
        iconWidth = style.keypad.backspaceIconWidth,
        iconHeight = style.keypad.backspaceIconHeight,
    )
}
```

- [ ] **Step 5: Add a semantics test for the icon path**

Append to `NumberInputKeypadSemanticsTest.kt`:

```kotlin
    /**
     * Swapping the backspace glyph for an `ImageVector` must not change what the accessibility tree
     * sees. An `Image` with a non-null `contentDescription` would publish its own node, which is the
     * same class of defect the glyph originally had.
     */
    @Test
    fun a_backspace_icon_still_leaves_exactly_one_button_node() = runComposeUiTest {
        val s = state()
        s.pressDigit(1)
        val icon = ImageVector.Builder(
            defaultWidth = 26.dp,
            defaultHeight = 20.dp,
            viewportWidth = 26f,
            viewportHeight = 20f,
        ).build()
        val style = NumberInputStyle(keypad = NumberInputKeypadStyle(backspaceIcon = icon))
        setContent { NumberInputKeypad(state = s, style = style, onDone = {}) }

        onNodeWithTag(TAG_KEYPAD_BACKSPACE)
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertContentDescriptionEquals(style.keypad.backspaceContentDescription)
        onAllNodesWithContentDescription(style.keypad.backspaceContentDescription).assertCountEquals(1)
    }
```

Add imports `androidx.compose.ui.graphics.vector.ImageVector`, `androidx.compose.ui.unit.dp`, `androidx.compose.ui.test.onAllNodesWithContentDescription`.

- [ ] **Step 6: Run the iOS suite**

```bash
./gradlew :number-input:iosSimulatorArm64Test 2>&1 | tee .agent/logs/gradle-$(date +%H%M%S).log | tail -40
```
Expected: `BUILD SUCCESSFUL`, including every pre-existing `NumberInputKeypadSemanticsTest` case.

- [ ] **Step 7: Run the JVM suite**

```bash
./gradlew :number-input:testDebugUnitTest 2>&1 | tee .agent/logs/gradle-$(date +%H%M%S).log | tail -40
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add -A number-input
git commit -m "Let a keypad key be styled per role and per state

Digit and utility keys take separate fills and glyph treatments, held and
refused keys take their own colours, and the key carries an optional hard
1dp lip. Unset, every one of these falls back to what 1.x drew: a utility
key identical to a digit key, no lip, and a disabled key dimmed by
disabledAlpha."
```

---

### Task 3: Accessory bar — chrome, hint, logo, prev/next, and ± visibility

**Files:**
- Modify: `number-input/src/commonMain/kotlin/dev/viethung/numberinput/NumberInputToolbarStyle.kt`
- Modify: `number-input/src/commonMain/kotlin/dev/viethung/numberinput/NumberInputToolbarRules.kt`
- Modify: `number-input/src/commonMain/kotlin/dev/viethung/numberinput/NumberInputHost.kt`
- Modify: `number-input/src/commonMain/kotlin/dev/viethung/numberinput/NumberInputField.kt`
- Modify: `number-input/src/androidMain/kotlin/dev/viethung/numberinput/NumberInputField.android.kt`
- Modify: `number-input/src/iosMain/kotlin/dev/viethung/numberinput/NumberInputField.ios.kt`
- Modify: `number-input/src/commonMain/kotlin/dev/viethung/numberinput/TestTags.kt`
- Test: `number-input/src/commonTest/kotlin/dev/viethung/numberinput/NumberInputToolbarRulesTest.kt` (create if absent)
- Test: `number-input/src/iosTest/kotlin/dev/viethung/numberinput/IosKeypadSuppressionTest.kt`

**Interfaces:**
- Consumes: `NumberInputToolbarStyle` from Task 1.
- Produces:
  - `data class NumberInputToolbarActionStyle(backgroundColor, contentColor, borderColor, borderWidth, cornerRadius, height, horizontalPadding, verticalPadding)`
  - `NumberInputToolbarStyle` gains `height`, `contentPadding`, `itemSpacing`, `labelTextSize`, `labelFontWeight`, `fontFamily`, `hint`, `hintTextSize`, `hintFontWeight`, `hintColor`, `bottomBorderColor`, `bottomBorderWidth`, `action`, `done`, `navigation`, `previousLabel`, `nextLabel`, `previousIcon`, `nextIcon`, `previousContentDescription`, `nextContentDescription`.
  - `NumberInputToolbarRules.signVisible(allowNegative: Boolean): Boolean`
  - `NumberInputField(..., onPrevious: (() -> Unit)? = null, onNext: (() -> Unit)? = null)` on both overloads and `PlatformNumberInputField`.
  - `NumberInputHost(modifier, leadingAccessory: (@Composable () -> Unit)? = null, content)`
  - `TAG_TOOLBAR_HINT`, `TAG_TOOLBAR_LOGO`, `TAG_TOOLBAR_PREVIOUS`, `TAG_TOOLBAR_NEXT`

- [ ] **Step 1: Write the failing rule test**

Create `number-input/src/commonTest/kotlin/dev/viethung/numberinput/NumberInputToolbarRulesTest.kt`:

```kotlin
package dev.viethung.numberinput

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Visibility and enablement are different questions, and the spec answers them differently for the
 * same button: a quantity field *omits* ±, while an integer-only field *disables* its decimal key.
 * A control that can never become enabled for the life of the field is dead weight on the bar.
 */
class NumberInputToolbarRulesTest {

    @Test
    fun sign_is_hidden_entirely_when_negatives_are_locked() {
        assertFalse(NumberInputToolbarRules.signVisible(allowNegative = false))
    }

    @Test
    fun sign_is_visible_when_negatives_are_allowed() {
        assertTrue(NumberInputToolbarRules.signVisible(allowNegative = true))
    }

    /** Visible but not yet usable: negatives allowed, nothing typed to negate. */
    @Test
    fun a_visible_sign_is_still_disabled_until_there_is_a_value() {
        assertTrue(NumberInputToolbarRules.signVisible(allowNegative = true))
        assertFalse(NumberInputToolbarRules.signEnabled(allowNegative = true, value = null))
        assertTrue(NumberInputToolbarRules.signEnabled(allowNegative = true, value = 1.0))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
./gradlew :number-input:testDebugUnitTest --tests '*NumberInputToolbarRulesTest' 2>&1 | tee .agent/logs/gradle-$(date +%H%M%S).log | tail -40
```
Expected: FAIL — `Unresolved reference 'signVisible'`.

- [ ] **Step 3: Add the rule**

In `NumberInputToolbarRules.kt`, after `signEnabled`:

```kotlin
    /**
     * "±" is *shown* only when negatives are allowed at all.
     *
     * Distinct from [signEnabled], which asks whether it is usable right now. With
     * `allowNegative = false` the button could never become enabled for the life of the field, so it
     * is omitted rather than greyed — the decimal key's integer-only case is the opposite call
     * deliberately, because that one becomes usable again as soon as the field's config changes.
     */
    fun signVisible(allowNegative: Boolean): Boolean = allowNegative
```

- [ ] **Step 4: Verify the rule test passes**

```bash
./gradlew :number-input:testDebugUnitTest --tests '*NumberInputToolbarRulesTest' 2>&1 | tee .agent/logs/gradle-$(date +%H%M%S).log | tail -40
```
Expected: PASS.

- [ ] **Step 5: Add the chrome tokens to `NumberInputToolbarStyle.kt`**

Append to the file:

```kotlin
/**
 * Chrome for one toolbar button.
 *
 * Every default is "no chrome" — no fill, no border, no radius — which draws the bare tinted text
 * 1.x drew. A design that gives Clear and ± a bordered pill and Done a filled block sets two of these.
 */
data class NumberInputToolbarActionStyle(
    val backgroundColor: Color = Color.Unspecified,
    /** Unspecified falls back to [NumberInputToolbarStyle.tint]. */
    val contentColor: Color = Color.Unspecified,
    val borderColor: Color = Color.Unspecified,
    val borderWidth: Dp = 0.dp,
    val cornerRadius: Dp = 0.dp,
    /** Unspecified wraps the label. */
    val height: Dp = Dp.Unspecified,
    val horizontalPadding: Dp = 12.dp,
    val verticalPadding: Dp = 10.dp,
)
```

And extend `NumberInputToolbarStyle` with:

```kotlin
    /** Unspecified wraps the tallest item, which is what 1.x did. */
    val height: Dp = Dp.Unspecified,
    val contentPadding: Dp = 4.dp,
    val itemSpacing: Dp = 4.dp,
    val labelTextSize: TextUnit = 16.sp,
    val labelFontWeight: FontWeight? = null,
    /** As with the keypad, this works on both platforms because the row is Compose on both. */
    val fontFamily: FontFamily? = null,

    /**
     * Optional caption centred in the bar — a unit or a field name, e.g. "Chargeable weight · KG".
     * Per-field, so it lives here rather than on [NumberInputHost]: the row it appears in is drawn
     * from whichever field currently holds focus.
     */
    val hint: String? = null,
    val hintTextSize: TextUnit = 12.sp,
    val hintFontWeight: FontWeight? = null,
    /** Unspecified falls back to [tint]. */
    val hintColor: Color = Color.Unspecified,

    val bottomBorderColor: Color = Color.Unspecified,
    val bottomBorderWidth: Dp = 1.dp,

    /** Chrome for Clear and ±. */
    val action: NumberInputToolbarActionStyle = NumberInputToolbarActionStyle(),
    /** Chrome for Done. */
    val done: NumberInputToolbarActionStyle = NumberInputToolbarActionStyle(),
    /** Chrome for the prev/next buttons. */
    val navigation: NumberInputToolbarActionStyle = NumberInputToolbarActionStyle(),
    val previousLabel: String = "‹",
    val nextLabel: String = "›",
    val previousIcon: ImageVector? = null,
    val nextIcon: ImageVector? = null,
    /** Localise: chevrons have no useful spoken form. */
    val previousContentDescription: String = "Previous field",
    val nextContentDescription: String = "Next field",
```

Add imports: `androidx.compose.ui.graphics.vector.ImageVector`, `androidx.compose.ui.text.font.FontFamily`, `androidx.compose.ui.text.font.FontWeight`, `androidx.compose.ui.unit.Dp`, `androidx.compose.ui.unit.TextUnit`, `androidx.compose.ui.unit.dp`, `androidx.compose.ui.unit.sp`.

- [ ] **Step 6: Add the new test tags**

Append to `TestTags.kt`:

```kotlin
// Accessory bar additions. Same "component, not instance" convention as the tags above.
internal const val TAG_TOOLBAR_HINT = "numberInput.toolbar.hint"
internal const val TAG_TOOLBAR_LOGO = "numberInput.toolbar.logo"
internal const val TAG_TOOLBAR_PREVIOUS = "numberInput.toolbar.previous"
internal const val TAG_TOOLBAR_NEXT = "numberInput.toolbar.next"
```

- [ ] **Step 7: Rewrite `NumberInputToolbarBar` and `ToolbarAction`**

In `NumberInputHost.kt`, replace both composables:

```kotlin
/**
 * The toolbar itself. Shared so the host, the inline fallback and the keypad's own top row cannot
 * drift apart.
 *
 * Layout follows the spec's three variants without branching on a mode: a leading slot (the logo, or
 * prev/next when the caller supplied them), then either a centred [NumberInputToolbarStyle.hint] or a
 * plain spacer, then the right-aligned actions in the fixed order ± → Clear → Done. ± is omitted
 * entirely when negatives are locked — see [NumberInputToolbarRules.signVisible].
 *
 * [onDone] is supplied by the field rather than defaulted here. It has to drop focus, not just
 * commit — committing alone leaves the keyboard up and the toolbar on screen. Routing it from the
 * field keeps one dismissal path for every call site.
 */
@Composable
internal fun NumberInputToolbarBar(
    state: NumberInputState,
    style: NumberInputStyle,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    leadingAccessory: (@Composable () -> Unit)? = null,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
) {
    val toolbar = style.toolbar
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (toolbar.height != Dp.Unspecified) Modifier.height(toolbar.height) else Modifier)
            .background(toolbar.backgroundColor)
            .then(
                if (toolbar.bottomBorderColor != Color.Unspecified) {
                    Modifier.drawBehind {
                        val h = toolbar.bottomBorderWidth.toPx()
                        drawRect(
                            color = toolbar.bottomBorderColor,
                            topLeft = Offset(0f, size.height - h),
                            size = Size(size.width, h),
                        )
                    }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = toolbar.contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(toolbar.itemSpacing),
    ) {
        leadingAccessory?.let {
            Box(Modifier.testTag(TAG_TOOLBAR_LOGO)) { it() }
        }
        if (onPrevious != null) {
            ToolbarAction(
                label = toolbar.previousLabel,
                icon = toolbar.previousIcon,
                enabled = true,
                contentDescription = toolbar.previousContentDescription,
                chrome = toolbar.navigation,
                style = style,
                testTag = TAG_TOOLBAR_PREVIOUS,
                onClick = onPrevious,
            )
        }
        if (onNext != null) {
            ToolbarAction(
                label = toolbar.nextLabel,
                icon = toolbar.nextIcon,
                enabled = true,
                contentDescription = toolbar.nextContentDescription,
                chrome = toolbar.navigation,
                style = style,
                testTag = TAG_TOOLBAR_NEXT,
                onClick = onNext,
            )
        }

        if (toolbar.hint != null) {
            BasicText(
                text = toolbar.hint,
                style = TextStyle(
                    color = toolbar.hintColor.takeOrElse { toolbar.tint },
                    fontSize = toolbar.hintTextSize,
                    fontWeight = toolbar.hintFontWeight,
                    fontFamily = toolbar.fontFamily,
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier.weight(1f).testTag(TAG_TOOLBAR_HINT),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }

        if (NumberInputToolbarRules.signVisible(state.config.allowNegative)) {
            ToolbarAction(
                label = toolbar.signLabel,
                enabled = state.signEnabled,
                chrome = toolbar.action,
                style = style,
                testTag = TAG_SIGN,
                onClick = state::toggleSign,
            )
        }
        ToolbarAction(
            label = toolbar.clearLabel,
            enabled = state.clearEnabled,
            chrome = toolbar.action,
            style = style,
            testTag = TAG_CLEAR,
            onClick = state::clear,
        )
        ToolbarAction(
            label = toolbar.doneLabel,
            enabled = true,
            chrome = toolbar.done,
            style = style,
            testTag = TAG_DONE,
            onClick = onDone,
        )
    }
}

@Composable
private fun ToolbarAction(
    label: String,
    enabled: Boolean,
    chrome: NumberInputToolbarActionStyle,
    style: NumberInputStyle,
    testTag: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    contentDescription: String = label,
) {
    val toolbar = style.toolbar
    val tint = chrome.contentColor.takeOrElse { toolbar.tint }
    val content = tint.copy(alpha = if (enabled) tint.alpha else tint.alpha * style.disabledAlpha)
    val shape = RoundedCornerShape(chrome.cornerRadius)

    Box(
        modifier = Modifier
            .testTag(testTag)
            .then(if (chrome.height != Dp.Unspecified) Modifier.height(chrome.height) else Modifier)
            .background(chrome.backgroundColor, shape)
            .then(
                if (chrome.borderColor != Color.Unspecified && chrome.borderWidth > 0.dp) {
                    Modifier.border(chrome.borderWidth, chrome.borderColor, shape)
                } else {
                    Modifier
                },
            )
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                if (!enabled) disabled()
            }
            .padding(horizontal = chrome.horizontalPadding, vertical = chrome.verticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(
                imageVector = icon,
                contentDescription = null,
                colorFilter = ColorFilter.tint(content),
                modifier = Modifier.clearAndSetSemantics {},
            )
        } else {
            BasicText(
                text = label,
                style = TextStyle(
                    color = content,
                    fontSize = toolbar.labelTextSize,
                    fontWeight = toolbar.labelFontWeight,
                    fontFamily = toolbar.fontFamily,
                ),
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}
```

Note the ordering change: 1.x drew Clear then ±; the spec's order is ± → Clear → Done. `TAG_CLEAR` and `TAG_SIGN` still resolve, so existing tests address the same elements — only their left-to-right positions swap.

Add imports to `NumberInputHost.kt`: `androidx.compose.foundation.Image`, `androidx.compose.foundation.border`, `androidx.compose.foundation.layout.Arrangement`, `androidx.compose.foundation.layout.height`, `androidx.compose.foundation.shape.RoundedCornerShape`, `androidx.compose.ui.draw.drawBehind`, `androidx.compose.ui.geometry.Offset`, `androidx.compose.ui.geometry.Size`, `androidx.compose.ui.graphics.ColorFilter`, `androidx.compose.ui.graphics.takeOrElse`, `androidx.compose.ui.graphics.vector.ImageVector`, `androidx.compose.ui.semantics.Role`, `androidx.compose.ui.semantics.clearAndSetSemantics`, `androidx.compose.ui.semantics.contentDescription`, `androidx.compose.ui.semantics.disabled`, `androidx.compose.ui.semantics.semantics`, `androidx.compose.ui.text.style.TextAlign`.

- [ ] **Step 8: Carry the new lambdas through the request and host**

In `NumberInputHost.kt`:

```kotlin
internal data class NumberInputToolbarRequest(
    val state: NumberInputState,
    val style: NumberInputStyle,
    val onDone: () -> Unit,
    val onPrevious: (() -> Unit)? = null,
    val onNext: (() -> Unit)? = null,
)

internal class NumberInputToolbarHost {
    var request: NumberInputToolbarRequest? by mutableStateOf(null)
        private set

    fun show(
        state: NumberInputState,
        style: NumberInputStyle,
        onPrevious: (() -> Unit)? = null,
        onNext: (() -> Unit)? = null,
        onDone: () -> Unit,
    ) {
        request = NumberInputToolbarRequest(state, style, onDone, onPrevious, onNext)
    }

    /** Ignores stale hides from a field that already lost the slot to another one. */
    fun hide(state: NumberInputState) {
        if (request?.state === state) request = null
    }
}
```

Add the `leadingAccessory` parameter to `NumberInputHost` and thread it plus the request's lambdas into both branches:

```kotlin
@Composable
fun NumberInputHost(
    modifier: Modifier = Modifier,
    leadingAccessory: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
```

Inside, the keypad branch becomes `NumberInputKeypad(state = request.state, style = resolvedStyle, onDone = request.onDone, leadingAccessory = leadingAccessory, onPrevious = request.onPrevious, onNext = request.onNext, modifier = bottom.offset { ... }.onSizeChanged { ... })`, and the toolbar branch `NumberInputToolbarBar(state = request.state, style = resolvedStyle, onDone = request.onDone, leadingAccessory = leadingAccessory, onPrevious = request.onPrevious, onNext = request.onNext, modifier = bottom.imePadding())`.

Add to the `NumberInputHost` KDoc:

```
 * [leadingAccessory] is drawn at the left end of the toolbar row — a brand mark, typically. It is on
 * the host rather than the field because it is constant for an app, and it is therefore absent from
 * the Android hostless inline fallback and from iOS's native `UIToolbar`, which keeps system styling.
```

- [ ] **Step 9: Pass the slots through `NumberInputKeypad`**

In `NumberInputKeypad.kt`, add the three parameters and forward them:

```kotlin
@Composable
internal fun NumberInputKeypad(
    state: NumberInputState,
    style: NumberInputStyle,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    leadingAccessory: (@Composable () -> Unit)? = null,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TAG_KEYPAD)
            .background(style.keypad.backgroundColor),
    ) {
        NumberInputToolbarBar(
            state = state,
            style = style,
            onDone = onDone,
            leadingAccessory = leadingAccessory,
            onPrevious = onPrevious,
            onNext = onNext,
        )
        // ... rows unchanged
```

- [ ] **Step 10: Add `onPrevious` / `onNext` to the public field API**

In `NumberInputField.kt`, add the parameters to both overloads and to the `expect`:

```kotlin
@Composable
fun NumberInputField(
    value: Double?,
    onValueChange: (Double?) -> Unit,
    modifier: Modifier = Modifier,
    config: NumberInputConfig = NumberInputConfig(),
    style: NumberInputStyle = NumberInputStyle(),
    enabled: Boolean = true,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
) {
```
…forwarding to the stateful overload, which forwards to `PlatformNumberInputField`.

KDoc to add on both overloads:

```
 * [onPrevious] and [onNext] add field-navigation buttons to the left of the toolbar row; null hides
 * each. **Compose row only.** The iOS system-keyboard path builds a native `UIToolbar` and this
 * library exposes no way to move focus into another `UITextField`, so on iOS these are usable only
 * with [NumberInputConfig.useBuiltInKeypad] and a host, where the caller drives focus itself.
```

- [ ] **Step 11: Publish them from the Android field**

In `NumberInputField.android.kt`, add the parameters to the `actual` signature and change the effect:

```kotlin
    val currentOnPrevious by rememberUpdatedState(onPrevious)
    val currentOnNext by rememberUpdatedState(onNext)

    // Nullability, not the lambdas themselves, is a key: a caller's lambda is usually a new instance
    // each composition, and keying on it would restart this effect on every frame.
    DisposableEffect(host, showToolbar, state, style, onPrevious != null, onNext != null) {
        if (host != null && showToolbar) {
            host.show(
                state = state,
                style = style,
                onPrevious = if (currentOnPrevious != null) ({ currentOnPrevious?.invoke() }) else null,
                onNext = if (currentOnNext != null) ({ currentOnNext?.invoke() }) else null,
                onDone = { focusManager.clearFocus() },
            )
        }
        onDispose { host?.hide(state) }
    }
```

Add import `androidx.compose.runtime.rememberUpdatedState`.

- [ ] **Step 12: Publish them from the iOS field, and omit ± from the `UIToolbar`**

In `NumberInputField.ios.kt`, mirror Step 11 for the `DisposableEffect` that calls `host.show(...)`.

In `buildToolbar`, make the sign item conditional:

```kotlin
        val items = mutableListOf<UIBarButtonItem>()
        // Same rule the Compose row applies: a button that can never become enabled is omitted, not
        // greyed. Expressible here because dropping a UIBarButtonItem needs no custom view — unlike
        // the pill/filled chrome, which stays Compose-only so this accessory keeps system styling.
        if (NumberInputToolbarRules.signVisible(state?.config?.allowNegative != false)) {
            items += sign
            signItem = sign
        } else {
            signItem = null
        }
        items += clear
        items += spacer
        items += done

        toolbar.setItems(items, animated = false)
```

Reorder the declarations so `sign` is built before this block, keep `clearItem = clear`, and leave `syncToolbar`/`updateToolbarLabels` untouched — both already null-check `signItem`.

- [ ] **Step 13: Add the iOS assertions**

Append to `IosKeypadSuppressionTest.kt`:

```kotlin
    /**
     * A quantity field's ± is omitted from the native accessory, not greyed. Asserted here rather than
     * in commonTest because only a real `UIToolbar` can show whether the item reached the bar.
     */
    @Test
    fun the_toolbar_omits_the_sign_item_when_negatives_are_locked() {
        val coordinator = NumberInputCoordinator()
        coordinator.state = NumberInputState(
            formatter = FakeLocaleNumberFormatter(),
            config = NumberInputConfig(allowNegative = false),
        )

        val toolbar = coordinator.buildToolbar(NumberInputStyle().let { resolveThemedColors(it, dark = false) })

        val identifiers = (toolbar.items ?: emptyList<Any?>()).mapNotNull {
            (it as? UIBarButtonItem)?.accessibilityIdentifier
        }
        assertFalse(TAG_SIGN in identifiers, "expected no ± item, got $identifiers")
        assertTrue(TAG_CLEAR in identifiers)
        assertTrue(TAG_DONE in identifiers)
    }

    @Test
    fun the_toolbar_keeps_the_sign_item_when_negatives_are_allowed() {
        val coordinator = NumberInputCoordinator()
        coordinator.state = NumberInputState(
            formatter = FakeLocaleNumberFormatter(),
            config = NumberInputConfig(allowNegative = true),
        )

        val toolbar = coordinator.buildToolbar(resolveThemedColors(NumberInputStyle(), dark = false))

        val identifiers = (toolbar.items ?: emptyList<Any?>()).mapNotNull {
            (it as? UIBarButtonItem)?.accessibilityIdentifier
        }
        assertTrue(TAG_SIGN in identifiers, "expected a ± item, got $identifiers")
    }
```

If `NumberInputCoordinator.state` is not settable from the test, set it through whatever the existing tests in this file already use to seed a coordinator — read the file's existing helpers first and follow them rather than adding a new seam.

- [ ] **Step 14: Add a Compose-side visibility test**

Append to `NumberInputKeypadSemanticsTest.kt`:

```kotlin
    @Test
    fun the_sign_key_is_absent_from_the_row_when_negatives_are_locked() = runComposeUiTest {
        val s = NumberInputState(
            formatter = FakeLocaleNumberFormatter(),
            config = NumberInputConfig(allowNegative = false, useBuiltInKeypad = true),
        ).also { it.onFocusChanged(true) }
        setContent { NumberInputKeypad(state = s, style = NumberInputStyle(), onDone = {}) }

        onAllNodesWithTag(TAG_SIGN).assertCountEquals(0)
        onNodeWithTag(TAG_CLEAR).assertHasClickAction()
        onNodeWithTag(TAG_DONE).assertHasClickAction()
    }

    @Test
    fun the_hint_and_navigation_buttons_appear_only_when_supplied() = runComposeUiTest {
        val s = state()
        val style = NumberInputStyle(toolbar = NumberInputToolbarStyle(hint = "Chargeable weight · KG"))
        setContent {
            NumberInputKeypad(state = s, style = style, onDone = {}, onNext = {})
        }

        onNodeWithTag(TAG_TOOLBAR_HINT).assertExists()
        onNodeWithTag(TAG_TOOLBAR_NEXT).assertHasClickAction()
        onAllNodesWithTag(TAG_TOOLBAR_PREVIOUS).assertCountEquals(0)
        onAllNodesWithTag(TAG_TOOLBAR_LOGO).assertCountEquals(0)
    }
```

Add imports `androidx.compose.ui.test.onAllNodesWithTag`, `androidx.compose.ui.test.assertExists`.

- [ ] **Step 15: Run both suites**

```bash
./gradlew :number-input:testDebugUnitTest 2>&1 | tee .agent/logs/gradle-$(date +%H%M%S).log | tail -40
./gradlew :number-input:iosSimulatorArm64Test 2>&1 | tee .agent/logs/gradle-$(date +%H%M%S).log | tail -40
```
Expected: `BUILD SUCCESSFUL` for both.

- [ ] **Step 16: Commit**

```bash
git add -A number-input
git commit -m "Give the accessory bar chrome, a hint, a logo slot and field navigation

Clear, ± and Done take per-button fill, border and radius; the row takes a
height, a bottom rule and a font. A centred hint and prev/next buttons are
per-field, the leading slot is per-host. ± is now omitted rather than greyed
when negatives are locked, on both the Compose row and the native UIToolbar.
Unset, every token still draws the bare tinted text 1.x drew."
```

---

### Task 4: Haptics and backspace repeat

**Files:**
- Modify: `number-input/src/commonMain/kotlin/dev/viethung/numberinput/NumberInputConfig.kt`
- Modify: `number-input/src/commonMain/kotlin/dev/viethung/numberinput/NumberInputKeypad.kt`
- Create: `number-input/src/iosTest/kotlin/dev/viethung/numberinput/NumberInputKeypadBehaviourTest.kt`

**Interfaces:**
- Consumes: the `Key` composable from Task 2.
- Produces: `NumberInputConfig.keypadHaptics: Boolean`, and the constants `BackspaceRepeatDelayMillis = 400L` / `BackspaceRepeatIntervalMillis = 80L` in `NumberInputKeypad.kt` (internal).

- [ ] **Step 1: Confirm the haptic API name on this Compose version**

```bash
grep -rn "KeyboardTap\|VirtualKey" ~/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.ui/ui-uikitsimarm64/1.9.0/*/ui-uikitSimArm64Main-1.9.0.klib 2>/dev/null | head -1 || \
  (f=$(find ~/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.ui -name "ui-uikitSimArm64Main-1.9.0.klib" | head -1); unzip -p "$f" | strings | grep -c "KeyboardTap")
```
Expected: a non-zero count, confirming `HapticFeedbackType.KeyboardTap` exists. If it is zero, use `HapticFeedbackType.VirtualKey` everywhere below instead — it is present in the same value class.

- [ ] **Step 2: Add the config flag**

In `NumberInputConfig.kt`, add the parameter and its KDoc:

```kotlin
 * @param keypadHaptics fire a light haptic on each accepted built-in-keypad press. On by default: a
 *   keypad replaces the system keyboard, and a replacement that does not respond to touch reads as
 *   broken next to the one it stands in for. Ignored entirely on the system-keyboard path, where the
 *   OS supplies its own feedback. During a held backspace the haptic fires once, on the initial press:
 *   at the repeat interval a tick per delete is a continuous buzz rather than feedback.
```

```kotlin
    val useBuiltInKeypad: Boolean = false,
    val keypadHaptics: Boolean = true,
```

- [ ] **Step 3: Write the failing behaviour test**

Create `number-input/src/iosTest/kotlin/dev/viethung/numberinput/NumberInputKeypadBehaviourTest.kt`:

```kotlin
package dev.viethung.numberinput

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two keypad behaviours the pixels cannot show.
 *
 * Driven through `runComposeUiTest` with `mainClock.autoAdvance = false`, so the 400 ms hold and the
 * 80 ms repeat are exercised in virtual time — no real waiting, and no `kotlinx-coroutines-test`
 * dependency, which this module deliberately does not carry.
 */
@OptIn(ExperimentalTestApi::class)
class NumberInputKeypadBehaviourTest {

    private class CountingHaptics : HapticFeedback {
        var count = 0
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            count++
        }
    }

    private fun state(haptics: Boolean = true) = NumberInputState(
        formatter = FakeLocaleNumberFormatter(),
        config = NumberInputConfig(
            significantDigits = 2,
            useBuiltInKeypad = true,
            keypadHaptics = haptics,
        ),
    ).also { it.onFocusChanged(true) }

    @Test
    fun each_accepted_key_press_fires_one_haptic() = runComposeUiTest {
        val s = state()
        val haptics = CountingHaptics()
        setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                NumberInputKeypad(state = s, style = NumberInputStyle(), onDone = {})
            }
        }

        onNodeWithTag(keypadDigitTag(1)).performClick()
        onNodeWithTag(keypadDigitTag(2)).performClick()
        waitForIdle()

        assertEquals(2, haptics.count)
        assertEquals("12", s.rawText)
    }

    @Test
    fun a_refused_key_fires_no_haptic() = runComposeUiTest {
        // significantDigits = 0 refuses the separator outright, so the key is disabled.
        val s = NumberInputState(
            formatter = FakeLocaleNumberFormatter(),
            config = NumberInputConfig(significantDigits = 0, useBuiltInKeypad = true),
        ).also { it.onFocusChanged(true) }
        val haptics = CountingHaptics()
        setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                NumberInputKeypad(state = s, style = NumberInputStyle(), onDone = {})
            }
        }

        onNodeWithTag(TAG_KEYPAD_DECIMAL).performClick()
        waitForIdle()

        assertEquals(0, haptics.count)
    }

    @Test
    fun haptics_can_be_turned_off() = runComposeUiTest {
        val s = state(haptics = false)
        val haptics = CountingHaptics()
        setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                NumberInputKeypad(state = s, style = NumberInputStyle(), onDone = {})
            }
        }

        onNodeWithTag(keypadDigitTag(1)).performClick()
        waitForIdle()

        assertEquals(0, haptics.count)
    }

    /** Below the threshold the hold is an ordinary tap: exactly one character goes. */
    @Test
    fun a_short_press_on_backspace_deletes_exactly_one_character() = runComposeUiTest {
        val s = state()
        s.onTextChange("12345")
        mainClock.autoAdvance = false
        setContent { NumberInputKeypad(state = s, style = NumberInputStyle(), onDone = {}) }
        mainClock.advanceTimeBy(16)

        onNodeWithTag(TAG_KEYPAD_BACKSPACE).performTouchInput { down(center) }
        mainClock.advanceTimeBy(100)
        onNodeWithTag(TAG_KEYPAD_BACKSPACE).performTouchInput { up() }
        mainClock.advanceTimeBy(50)

        assertEquals("1234", s.rawText)
    }

    /** Past 400 ms the key repeats every 80 ms, and the release must not add one more delete. */
    @Test
    fun holding_backspace_past_the_threshold_repeats_at_the_stated_rate() = runComposeUiTest {
        val s = state()
        s.onTextChange("123456789")
        mainClock.autoAdvance = false
        setContent { NumberInputKeypad(state = s, style = NumberInputStyle(), onDone = {}) }
        mainClock.advanceTimeBy(16)

        onNodeWithTag(TAG_KEYPAD_BACKSPACE).performTouchInput { down(center) }
        // 400ms threshold, then three intervals of 80ms.
        mainClock.advanceTimeBy(400 + 80 * 3 + 8)
        onNodeWithTag(TAG_KEYPAD_BACKSPACE).performTouchInput { up() }
        mainClock.advanceTimeBy(50)

        // Four deletes: the one at the threshold plus three repeats. The release adds none.
        assertEquals("12345", s.rawText)
    }

    /** The repeat stops itself when there is nothing left, rather than spinning on an empty buffer. */
    @Test
    fun the_repeat_stops_when_the_buffer_empties() = runComposeUiTest {
        val s = state()
        s.onTextChange("12")
        mainClock.autoAdvance = false
        setContent { NumberInputKeypad(state = s, style = NumberInputStyle(), onDone = {}) }
        mainClock.advanceTimeBy(16)

        onNodeWithTag(TAG_KEYPAD_BACKSPACE).performTouchInput { down(center) }
        mainClock.advanceTimeBy(400 + 80 * 20)
        onNodeWithTag(TAG_KEYPAD_BACKSPACE).performTouchInput { up() }
        mainClock.advanceTimeBy(50)

        assertEquals("", s.rawText)
        assertTrue(s.rawText.isEmpty())
    }

    /** One haptic for the whole hold — a tick per repeat is a buzz, not feedback. */
    @Test
    fun a_held_backspace_fires_one_haptic_not_one_per_repeat() = runComposeUiTest {
        val s = state()
        s.onTextChange("123456789")
        val haptics = CountingHaptics()
        mainClock.autoAdvance = false
        setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                NumberInputKeypad(state = s, style = NumberInputStyle(), onDone = {})
            }
        }
        mainClock.advanceTimeBy(16)

        onNodeWithTag(TAG_KEYPAD_BACKSPACE).performTouchInput { down(center) }
        mainClock.advanceTimeBy(400 + 80 * 4 + 8)
        onNodeWithTag(TAG_KEYPAD_BACKSPACE).performTouchInput { up() }
        mainClock.advanceTimeBy(50)

        assertEquals(1, haptics.count)
    }
}
```

- [ ] **Step 4: Run it to verify it fails**

```bash
./gradlew :number-input:iosSimulatorArm64Test --tests '*NumberInputKeypadBehaviourTest' 2>&1 | tee .agent/logs/gradle-$(date +%H%M%S).log | tail -40
```
Expected: FAIL — `keypadHaptics` unresolved, or the repeat assertions failing with a single delete.

- [ ] **Step 5: Add the haptic to `Key`**

In `NumberInputKeypad.kt`, wrap the click:

```kotlin
private const val BackspaceRepeatDelayMillis = 400L
private const val BackspaceRepeatIntervalMillis = 80L
```

Inside `Key`, add a `haptics` parameter path:

```kotlin
    val haptics = LocalHapticFeedback.current
    val hapticsEnabled = state.config.keypadHaptics
```

`Key` does not currently receive `state`. Rather than pass it, hoist the decision to the three call sites: give `Key` a `onClick: () -> Unit` that already includes the haptic. In `NumberInputKeypad`, add a local helper and use it in `DigitKey`/`DecimalKey`/`BackspaceKey`:

```kotlin
/**
 * Wraps a press so an accepted key ticks. Applied at the call site rather than inside [Key] so the
 * held-backspace repeat can delete without ticking each time — see [BackspaceKey].
 */
@Composable
private fun rememberHapticPress(state: NumberInputState, action: () -> Unit): () -> Unit {
    val haptics = LocalHapticFeedback.current
    val enabled = state.config.keypadHaptics
    return {
        if (enabled) haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
        action()
    }
}
```

`DigitKey` uses `onClick = rememberHapticPress(state) { state.pressDigit(digit) }`, `DecimalKey` uses `rememberHapticPress(state) { state.pressDecimalSeparator() }`.

Add imports `androidx.compose.ui.hapticfeedback.HapticFeedbackType`, `androidx.compose.ui.platform.LocalHapticFeedback`.

- [ ] **Step 6: Add the hold-to-repeat gesture to the backspace key**

Replace `BackspaceKey`:

```kotlin
/**
 * Backspace, with hold-to-repeat.
 *
 * The single tap stays on `clickable` — the key's `Role.Button`, click action and spoken name all come
 * from there, and moving the tap into the gesture detector is what would take the accessibility tree
 * down with it. `pointerInput` only adds the hold, and `repeatFired` stops the release from landing a
 * second delete on top of the repeats: `clickable`'s `onClick` runs on release regardless, so the flag
 * is read there and reset on the *next* press, which is deterministic because a press always precedes
 * the click that follows it.
 */
@Composable
private fun BackspaceKey(state: NumberInputState, style: NumberInputStyle, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val hapticsEnabled = state.config.keypadHaptics
    var repeatFired by remember { mutableStateOf(false) }
    val enabled = state.backspaceEnabled

    Key(
        label = style.keypad.backspaceLabel,
        enabled = enabled,
        role = style.keypad.utilityKey,
        style = style,
        testTag = TAG_KEYPAD_BACKSPACE,
        // The glyph is a symbol, so it needs a spoken name of its own — a screen reader would
        // otherwise announce the character itself, or nothing.
        contentDescription = style.keypad.backspaceContentDescription,
        onClick = {
            if (repeatFired) {
                repeatFired = false
            } else {
                if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                state.pressBackspace()
            }
        },
        modifier = modifier.pointerInput(enabled, state) {
            if (!enabled) return@pointerInput
            detectTapGestures(
                onPress = {
                    repeatFired = false
                    val repeat = scope.launch {
                        delay(BackspaceRepeatDelayMillis)
                        // One tick for the whole hold: at the repeat interval a haptic per delete is a
                        // continuous buzz rather than feedback.
                        if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                        while (state.backspaceEnabled) {
                            repeatFired = true
                            state.pressBackspace()
                            delay(BackspaceRepeatIntervalMillis)
                        }
                    }
                    tryAwaitRelease()
                    repeat.cancel()
                },
            )
        },
        icon = style.keypad.backspaceIcon,
        iconWidth = style.keypad.backspaceIconWidth,
        iconHeight = style.keypad.backspaceIconHeight,
    )
}
```

`Key` must accept the extra modifier — it already takes `modifier`, so `pointerInput` chains before `Key`'s own chain and receives events first. Add imports `androidx.compose.foundation.gestures.detectTapGestures`, `androidx.compose.runtime.mutableStateOf`, `androidx.compose.runtime.rememberCoroutineScope`, `androidx.compose.runtime.setValue`, `androidx.compose.ui.input.pointer.pointerInput`, `kotlinx.coroutines.delay`, `kotlinx.coroutines.launch`.

- [ ] **Step 7: Run the behaviour test**

```bash
./gradlew :number-input:iosSimulatorArm64Test --tests '*NumberInputKeypadBehaviourTest' 2>&1 | tee .agent/logs/gradle-$(date +%H%M%S).log | tail -40
```
Expected: PASS. If `holding_backspace_past_the_threshold_repeats_at_the_stated_rate` is off by one delete, the cause is the release-click guard — check `repeatFired` is read *before* being reset, not after.

- [ ] **Step 8: Re-run the semantics test, which is what the gesture endangers**

```bash
./gradlew :number-input:iosSimulatorArm64Test --tests '*NumberInputKeypadSemanticsTest' 2>&1 | tee .agent/logs/gradle-$(date +%H%M%S).log | tail -40
```
Expected: PASS — every case, in particular `the_decimal_and_backspace_keys_are_button_nodes_with_spoken_names`.

- [ ] **Step 9: Run both full suites**

```bash
./gradlew :number-input:testDebugUnitTest 2>&1 | tee .agent/logs/gradle-$(date +%H%M%S).log | tail -40
./gradlew :number-input:iosSimulatorArm64Test 2>&1 | tee .agent/logs/gradle-$(date +%H%M%S).log | tail -40
```
Expected: `BUILD SUCCESSFUL` for both.

- [ ] **Step 10: Commit**

```bash
git add -A number-input
git commit -m "Tick on each key, and repeat a held backspace

Light haptic per accepted press, opt out with NumberInputConfig.keypadHaptics.
Holding backspace 400ms starts deleting every 80ms until the finger lifts or
the buffer empties, with one haptic for the whole hold. The tap stays on
clickable so the key keeps its button role and spoken name; pointerInput adds
only the hold."
```

---

### Task 5: OFNumpad sample screen, publish, and the parity pass

**Files:**
- Modify: `number-input/build.gradle.kts` (already at 2.0.0 from Task 1 — verify)
- Modify: `README.md`, `CLAUDE.md`
- Modify: `/Users/hugues_mini/Codes/cmp/gradle/libs.versions.toml:16`
- Modify: `/Users/hugues_mini/Codes/cmp/shared/src/commonMain/kotlin/org/example/project/App.kt`
- Modify: `/Users/hugues_mini/Codes/cmp/shared/src/commonMain/kotlin/org/example/project/NumberInputSampleScreen.kt`
- Modify: `/Users/hugues_mini/Codes/cmp/shared/src/commonMain/kotlin/org/example/project/NoHostKeypadSampleScreen.kt`
- Create: `/Users/hugues_mini/Codes/cmp/shared/src/commonMain/kotlin/org/example/project/OFNumpadTokens.kt`
- Create: `/Users/hugues_mini/Codes/cmp/shared/src/commonMain/kotlin/org/example/project/OFNumpadSampleScreen.kt`

**Interfaces:**
- Consumes: every public type from Tasks 1–4.
- Produces: `OFNumpadTokens.ofNumpadStyle(): NumberInputStyle` and `OFNumpadSampleScreen()` in the sample app only.

- [ ] **Step 1: Publish 2.0.0 to mavenLocal**

```bash
cd /Users/hugues_mini/Codes/number-input && ./gradlew :number-input:publishToMavenLocal 2>&1 | tee .agent/logs/gradle-$(date +%H%M%S).log | tail -40
```
Expected: `BUILD SUCCESSFUL`. Confirm the coordinates:
```bash
ls ~/.m2/repository/dev/viethung/number-input/
```
Expected: a `2.0.0` directory.

- [ ] **Step 2: Point the sample at 2.0.0 and migrate its existing styles**

In `/Users/hugues_mini/Codes/cmp/gradle/libs.versions.toml:16`, change `numberInput = "1.0.0"` to `numberInput = "2.0.0"`.

In `NumberInputSampleScreen.kt:56-65`, replace the flat toolbar properties:
```kotlin
    val baseStyle = NumberInputStyle(
        textColor = colors.onSurface,
        textSize = MaterialTheme.typography.bodyLarge.fontSize,
        placeholderColor = colors.onSurfaceVariant,
        backgroundColor = colors.surface,
        borderColor = colors.outline,
        cursorColor = colors.primary,
        toolbar = NumberInputToolbarStyle(
            backgroundColor = colors.surfaceVariant,
            tint = colors.primary,
        ),
    )
```

In `NoHostKeypadSampleScreen.kt:52-63`, likewise:
```kotlin
    val style = NumberInputStyle(
        textColor = colors.onSurface,
        textSize = MaterialTheme.typography.bodyLarge.fontSize,
        placeholderColor = colors.onSurfaceVariant,
        backgroundColor = colors.surface,
        borderColor = colors.outline,
        cursorColor = colors.primary,
        // Toolbar colours left unset (Color.Unspecified) on purpose: the toolbar this field falls back
        // to should show the library's own light/dark palette, which is the other thing worth
        // eyeballing here.
        toolbar = NumberInputToolbarStyle(clearLabel = "Löschen", doneLabel = "Fertig"),
    )
```

Add `import dev.viethung.numberinput.NumberInputToolbarStyle` to both files.

- [ ] **Step 3: Create the OFNumpad token file in the sample**

Create `/Users/hugues_mini/Codes/cmp/shared/src/commonMain/kotlin/org/example/project/OFNumpadTokens.kt`:

```kotlin
package org.example.project

import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.viethung.numberinput.NumberInputKeyStyle
import dev.viethung.numberinput.NumberInputKeypadStyle
import dev.viethung.numberinput.NumberInputStyle
import dev.viethung.numberinput.NumberInputToolbarActionStyle
import dev.viethung.numberinput.NumberInputToolbarStyle

// BFSOne OFNumpad palette. Lives in the sample, never in the library — see the spec at
// number-input/numpad-design/OFNumpad Spec.html, section 14.
// The in-context frame is 402×874 points ("iPhone 17 Pro"), so 1 CSS px = 1 dp exactly.
val Olive = Color(0xFF808028)
val OliveDark = Color(0xFF63631E)
val Ink = Color(0xFF1F1F16)
val Muted = Color(0xFF9B9B8D)
val KeypadBg = Color(0xFFE7E7DA)
val AccessoryBg = Color(0xFFF0F0DF)
val AccessoryBorder = Color(0xFFDDDDC8)
val KeyRest = Color(0xFFFFFFFF)
val KeyUtility = Color(0xFFF7F7F0)
val KeyShadow = Color(0xFFC7C7B5)
val PressedBorder = Color(0xFFE0E0C6)
val KeyDisabledBg = Color(0xFFEDEDE4)
val KeyDisabledText = Color(0xFFB9B9AA)
val KeyDisabledBorder = Color(0xFFE3E3D8)
val BtnBorder = Color(0xFFB7B78A)
val BtnBg = Color(0xFFFBFBF2)

/** The spec's outlined delete-left glyph: 26×20, 1.6dp stroke, `olive-dark`. */
val OFBackspaceIcon: ImageVector = ImageVector.Builder(
    name = "OFBackspace",
    defaultWidth = 26.dp,
    defaultHeight = 20.dp,
    viewportWidth = 26f,
    viewportHeight = 20f,
).apply {
    addPath(
        pathData = PathData {
            moveTo(8.4f, 2.5f)
            horizontalLineTo(23f)
            arcToRelative(1.8f, 1.8f, 0f, false, true, 1.8f, 1.8f)
            verticalLineToRelative(11.4f)
            arcTo(1.8f, 1.8f, 0f, false, true, 23f, 17.5f)
            horizontalLineTo(8.4f)
            lineTo(1.6f, 10f)
            close()
        },
        fill = null,
        stroke = SolidColor(OliveDark),
        strokeLineWidth = 1.6f,
        strokeLineJoin = StrokeJoin.Round,
    )
    addPath(
        pathData = PathData {
            moveTo(12.6f, 7.2f)
            lineTo(17.8f, 12.8f)
            moveTo(17.8f, 7.2f)
            lineTo(12.6f, 12.8f)
        },
        fill = null,
        stroke = SolidColor(OliveDark),
        strokeLineWidth = 1.6f,
        strokeLineCap = StrokeCap.Round,
    )
}.build()

/**
 * The OFNumpad style, assembled entirely from library parameters.
 *
 * Every number here is a row in the spec's token table; nothing is derived. The library ships none of
 * these values — that is the point of the exercise.
 */
fun ofNumpadStyle(
    hint: String? = null,
    clearLabel: String = "Clear",
    doneLabel: String = "Done",
) = NumberInputStyle(
    textColor = Ink,
    textSize = 17.sp,
    textWeight = FontWeight.Bold,
    backgroundColor = Color.White,
    borderColor = Olive,
    borderWidth = 1.5.dp,
    cornerRadius = 10.dp,
    cursorColor = Olive,
    toolbar = NumberInputToolbarStyle(
        backgroundColor = AccessoryBg,
        tint = OliveDark,
        clearLabel = clearLabel,
        doneLabel = doneLabel,
        height = 44.dp,
        contentPadding = 8.dp,
        itemSpacing = 6.dp,
        labelTextSize = 13.sp,
        labelFontWeight = FontWeight.SemiBold,
        hint = hint,
        hintTextSize = 11.5.sp,
        hintFontWeight = FontWeight.SemiBold,
        hintColor = Muted,
        bottomBorderColor = AccessoryBorder,
        bottomBorderWidth = 1.dp,
        action = NumberInputToolbarActionStyle(
            backgroundColor = BtnBg,
            contentColor = OliveDark,
            borderColor = BtnBorder,
            borderWidth = 1.2.dp,
            cornerRadius = 8.dp,
            height = 32.dp,
            horizontalPadding = 12.dp,
            verticalPadding = 0.dp,
        ),
        done = NumberInputToolbarActionStyle(
            backgroundColor = Olive,
            contentColor = Color.White,
            cornerRadius = 8.dp,
            height = 32.dp,
            horizontalPadding = 18.dp,
            verticalPadding = 0.dp,
        ),
        navigation = NumberInputToolbarActionStyle(
            backgroundColor = Color.White,
            contentColor = OliveDark,
            cornerRadius = 8.dp,
            height = 32.dp,
            horizontalPadding = 10.dp,
            verticalPadding = 0.dp,
        ),
    ),
    keypad = NumberInputKeypadStyle(
        backgroundColor = KeypadBg,
        keyHeight = 52.dp,
        keyCornerRadius = 12.dp,
        contentPadding = 8.dp,
        keySpacing = 7.dp,
        tabularFigures = true,
        backspaceIcon = OFBackspaceIcon,
        backspaceIconWidth = 26.dp,
        backspaceIconHeight = 20.dp,
        restKey = NumberInputKeyStyle(
            backgroundColor = KeyRest,
            contentColor = Ink,
            shadowColor = KeyShadow,
            shadowHeight = 1.dp,
            textSize = 24.sp,
            fontWeight = FontWeight.Medium,
        ),
        utilityKey = NumberInputKeyStyle(
            backgroundColor = KeyUtility,
            contentColor = OliveDark,
            textSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            contentAlignment = Alignment.BottomCenter,
            contentBottomPadding = 10.dp,
        ),
        pressedKey = NumberInputKeyStyle(
            backgroundColor = AccessoryBg,
            contentColor = OliveDark,
            borderColor = PressedBorder,
            borderWidth = 1.dp,
        ),
        disabledKey = NumberInputKeyStyle(
            backgroundColor = KeyDisabledBg,
            contentColor = KeyDisabledText,
            borderColor = KeyDisabledBorder,
            borderWidth = 1.dp,
        ),
    ),
)
```

Note: the spec's backspace icon is `key-utility` filled and bottom-aligned in the utility role, but the icon is centred in the mockup. Set `contentBottomPadding = 0.dp` for the backspace by giving it its own centred treatment if the screenshot shows the icon sitting low — record the delta in Step 7 either way rather than silently adjusting.

- [ ] **Step 4: Create the sample screen**

Create `/Users/hugues_mini/Codes/cmp/shared/src/commonMain/kotlin/org/example/project/OFNumpadSampleScreen.kt`. It must reproduce the spec's in-context frame: an olive 56dp top bar titled "Settlement Request", a scrolling body with an Amount field (label "Amount *", helper "Tối đa 12 chữ số · tự chèn dấu phân cách nghìn", `VND` suffix drawn by the screen, not the library) and a VAT rate field, and the keypad supplied by the library.

```kotlin
package org.example.project

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.viethung.numberinput.NumberInputConfig
import dev.viethung.numberinput.NumberInputField
import dev.viethung.numberinput.NumberInputHost
import dev.viethung.numberinput.numberInputKeypadPadding

/**
 * The spec's "Amount + OFNumpad — in context" frame, rebuilt in Compose.
 *
 * Target frame is 402×874 points, i.e. an iPhone 17 Pro. Everything the library does not own — the
 * nav bar, the field label, the VND suffix, the helper line — is drawn here, which is the point:
 * it demonstrates the split agreed for this work.
 */
@Composable
fun OFNumpadSampleScreen() {
    var amount by remember { mutableStateOf<Double?>(2_500_000.0) }
    var vat by remember { mutableStateOf<Double?>(8.0) }

    NumberInputHost(
        leadingAccessory = {
            // Stands in for the OF1 mark: 20dp tall, left-anchored, ≥8dp clear space.
            Box(
                Modifier
                    .padding(start = 4.dp, end = 8.dp)
                    .height(20.dp)
                    .background(Olive, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    "OF1",
                    style = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                )
            }
        },
    ) {
        Column(Modifier.fillMaxSize().background(Color(0xFFF3F6F9))) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).background(Olive).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                BasicText(
                    "Settlement Request",
                    style = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                )
            }

            Column(
                Modifier
                    .fillMaxSize()
                    // Shrink the viewport *before* the scroll, or a focused field low on the screen
                    // cannot be scrolled out from behind the keypad.
                    .numberInputKeypadPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                LabelledField(label = "Amount *", helper = "Tối đa 12 chữ số · tự chèn dấu phân cách nghìn") {
                    NumberInputField(
                        value = amount,
                        onValueChange = { amount = it },
                        modifier = Modifier.testTag("ofnumpad.amount"),
                        config = NumberInputConfig(
                            significantDigits = 0,
                            locale = "vi-VN",
                            allowNegative = true,
                            useBuiltInKeypad = true,
                        ),
                        style = ofNumpadStyle(),
                    )
                }
                LabelledField(label = "VAT rate", helper = null) {
                    NumberInputField(
                        value = vat,
                        onValueChange = { vat = it },
                        modifier = Modifier.testTag("ofnumpad.vat"),
                        config = NumberInputConfig(
                            significantDigits = 2,
                            locale = "vi-VN",
                            allowNegative = false,
                            useBuiltInKeypad = true,
                        ),
                        style = ofNumpadStyle(hint = "VAT rate · %"),
                    )
                }
            }
        }
    }
}

@Composable
private fun LabelledField(label: String, helper: String?, field: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        BasicText(label, style = TextStyle(color = OliveDark, fontSize = 10.5.sp))
        field()
        if (helper != null) {
            BasicText(helper, style = TextStyle(color = Muted, fontSize = 11.sp))
        }
    }
}
```

The Amount field uses `significantDigits = 0`, which is the spec's integer-VND rule and is what puts the decimal key into its disabled state — so this one screen exercises the rest, utility, pressed and disabled swatches together. The VAT field sets `allowNegative = false`, which is what removes ± and produces the spec's Quantity variant.

- [ ] **Step 5: Register the tab**

In `App.kt`, add `OFNumpad` to the `Sample` enum, a button tagged `tabOFNumpad`, and `Sample.OFNumpad -> OFNumpadSampleScreen()` to the `when`.

- [ ] **Step 6: Build and run on the simulator**

```bash
cd /Users/hugues_mini/Codes/cmp && ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 --refresh-dependencies 2>&1 | tail -20
```
Then stop any running instance and launch through XcodeBuildMCP (`build_run_sim`) against `iosApp/iosApp.xcodeproj`, scheme `iosApp`, bundle id `org.example.project.cmp`, on the **iPhone 17 Pro** simulator (`8C574E98-B729-4D62-AB7E-456183E6D49B`).

Publishing to mavenLocal alone is not enough — the framework must be relinked *and* the app relaunched, or the simulator keeps executing the previous build. To confirm which code is running, grep the installed binary for a literal, remembering Kotlin/Native stores strings as UTF-16LE:
```bash
python3 -c "d=open(PATH_TO_BINARY,'rb').read(); print(d.count('Settlement Request'.encode('utf-16-le')))"
```

- [ ] **Step 7: Screenshot and report measured deltas**

Set the simulator to light appearance first, since the spec has no dark variant:
```bash
xcrun simctl ui 8C574E98-B729-4D62-AB7E-456183E6D49B appearance light
```

Tap the Amount field, screenshot with the MCP `screenshot` tool, and compare region by region against the mockup in `numpad-design/OFNumpad Spec.html`. Report a table, not a verdict:

| Region | Spec | Measured | Δ |
|---|---|---|---|
| key height | 52dp | | |
| key radius | 12dp | | |
| horizontal gap between keys | 7dp | | |
| vertical gap between rows | 7dp | | |
| grid outer padding | 8dp | | |
| shadow lip | 1dp `#C7C7B5` | | |
| decimal glyph baseline offset from key bottom | 10dp | | |
| accessory bar height | 44dp | | |
| Done pill | 32dp tall, 18dp h-padding, `#808028` | | |
| ± / Clear pill | 32dp tall, 12dp h-padding, 1.2dp `#B7B78A` on `#FBFBF2` | | |
| disabled decimal key | `#EDEDE4` fill, `#B9B9AA` glyph | | |
| keypad container | `#E7E7DA` | | |

Then hold a digit key and screenshot again for the pressed swatch (`#F0F0DF` fill, 1dp `#E0E0C6` border, `#63631E` glyph, **no lip**).

"Looks right" is not a result. Any row whose Δ is non-zero is either fixed or recorded with a reason.

- [ ] **Step 8: Confirm the defaults regression check**

Switch to the `tabNumberInput` tab, focus the de-DE keypad field, and screenshot. Its style sets no keypad tokens, so it must look exactly as it did at 1.0.0: no shadow lip, a utility key indistinguishable from a digit key, and a disabled key dimmed rather than filled. Compare against `git stash`-free memory is not sufficient — if in doubt, check out the Task 0 commit into a second worktree, publish it as `1.0.0` to mavenLocal, and screenshot the same tab.

- [ ] **Step 9: Update the docs**

In `README.md`, add a "Migrating to 2.0.0" section giving the full old→new property table from Task 1 Step 9, and document `keypadHaptics`, `leadingAccessory`, `onPrevious`/`onNext` (with the iOS limitation), and the four key styles.

In `CLAUDE.md`, under **Architecture**, extend the keypad section with: the four-swatch key model and its two merge rules; that the shadow lip is drawn with `drawBehind` because `0 1px 0` has no blur and `Modifier.shadow` cannot produce it; that `indication = null` is deliberate because the pressed style *is* the indication; that the backspace tap stays on `clickable` while `pointerInput` carries only the hold, and why; and that ± is hidden rather than disabled when negatives are locked, on both platforms, while the decimal key is disabled rather than hidden — the spec's own asymmetry.

Also correct the stale line in `/Users/hugues_mini/.claude/skills/html-design-to-compose/OFNUMPAD.md`: haptics need no `expect`/`actual`, because CMP 1.9.0's iOS target implements `HapticFeedback` via `CupertinoHapticFeedback`.

- [ ] **Step 10: Commit both repos**

```bash
cd /Users/hugues_mini/Codes/number-input
git add -A README.md CLAUDE.md docs number-input
git commit -m "Document the 2.0.0 styling API and its migration"

cd /Users/hugues_mini/Codes/cmp
git add -A
git commit -m "Add an OFNumpad-styled sample screen

Reproduces the spec's in-context frame at 402x874 using only library
parameters — every olive value lives here, none in the library. The amount
field is integer-only so the decimal key shows its disabled state, and the
VAT field locks negatives so the accessory bar shows the Quantity variant."
```

---

## Self-Review

**Spec coverage.** Every row of the `OFNUMPAD.md` gap table maps to a task: grid metrics, per-key fills, shadow lip, pressed, disabled, decimal styling, digit weight, tabular-nums, font family → Task 2; logo, hint, prev/next, pill/Done chrome → Task 3; haptics, backspace repeat → Task 4; the values that already had parameters → Task 5's token file. Two rows are deliberately not implemented and are recorded as such: the **home indicator** belongs to the OS, and **field chrome** (focus ring, unit suffix, label, helper) is drawn by the sample screen rather than by the library, per the scope decision.

**Type consistency.** `NumberInputKeyStyle` fields are used with the same names in Tasks 1, 2 and 5 (`contentColor`, `contentAlignment`, `contentBottomPadding`, `shadowColor`, `shadowHeight`). `NumberInputToolbarActionStyle` is introduced in Task 3 and consumed in Task 5 with the same eight properties. `signVisible` is defined in Task 3 Step 3 and called in Steps 7 and 12. `rememberHapticPress` is defined and used only within Task 4.

**Known risk carried forward.** The `Key` semantics invariant is asserted after every task that touches it (Task 2 Step 6, Task 4 Step 8). If `NumberInputKeypadSemanticsTest` ever fails after adding the backspace gesture, the fix is not to relax the test — the defect it guards is real and was found on a device.
