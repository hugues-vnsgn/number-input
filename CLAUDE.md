# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Compose Multiplatform (Android + iOS) library publishing a single component: a locale-aware numeric
text field with live thousands grouping and a Clear / ± / Done keyboard toolbar. Gradle root project
`number-input` with one module, `:number-input`, coordinates `dev.viethung:number-input`.

It is a Kotlin port of an existing Swift library (`NumberInputKit`) plus an Android-only predecessor.
Comments reference the Swift originals (`NumberInputToolbarRules.swift`,
`IosLocaleNumberFormatter.swift`, `NumberInputUITextField.swift`) — behaviour and test tag strings
are kept identical on purpose so UI tests written against either implementation address the same
elements.

## Commands

```bash
./gradlew :number-input:testDebugUnitTest        # commonTest + androidTest on the JVM (fast loop)
./gradlew :number-input:iosSimulatorArm64Test    # commonTest + iosTest on a real simulator (Apple silicon)
./gradlew :number-input:allTests                 # every target, aggregated report
./gradlew :number-input:assemble                 # Android AAR + iOS klibs
./gradlew :number-input:publishToMavenLocal      # consume from another project via mavenLocal()
```

Single test class / method — `--tests` works on both the JVM and the Native task:

```bash
./gradlew :number-input:testDebugUnitTest --tests '*NumberInputStateTest'
./gradlew :number-input:testDebugUnitTest --tests '*NumberInputStateTest.commit_*'
./gradlew :number-input:iosSimulatorArm64Test --tests '*NumberInputIosBridgeTest'
```

There is no lint or formatter configured. `kotlin.code.style=official`.

## Toolchain constraints

`gradle/libs.versions.toml` carries a deliberate version floor, not a stale one. Kotlin klibs have no
forward compatibility, so the Kotlin/CMP versions here are the hard minimum for every consumer:

- Kotlin 2.2.20 / CMP 1.9.0 match the primary consumer (`of1-freight-mobile` / BFSOne) exactly.
- `iosX64()` is retained because that consumer still declares it; CMP 1.11 removes it. Do not bump to
  1.11 without confirming every consumer dropped the target.
- `jvmTarget`/Java 11 and `minSdk = 23` are set below the consumer's own floor so the library is never
  the binding constraint.

Read the comments in `libs.versions.toml` and `number-input/build.gradle.kts` before changing any of
these. Dependencies are `compose.runtime`/`foundation`/`ui` only — **no Material3**, by design.
Styling arrives through `NumberInputStyle`, since the library has no theme to read.

## Architecture

The core idea: all shared logic lives in `commonMain` and is UI-framework-free; each platform only
renders. Two things are genuinely platform-specific — number formatting and the field widget itself —
and both sit behind explicit seams.

**State (`NumberInputState`)** is a plain class holding Compose snapshot state (`mutableStateOf`), not
a ViewModel: the component is a field, not a screen. Updates are synchronous, so tests need no
dispatcher control. It owns `value: Double?`, `rawText: String`, and a two-value `phase`
(Idle/Editing). Three invariants matter:

- `rawText` is **always ungrouped** on every platform. Grouping is display-only.
- `syncExternalValue()` ignores pushes while `phase == Editing` and no-ops when equal — that pair is
  what breaks the outward/inward binding loop in the stateless `NumberInputField` overload.
- `commit()` is reached only by losing focus. Both platforms route Done through focus loss
  (`focusManager.clearFocus()` / `resignFirstResponder()`) so there is a single commit path.

`NumberInputToolbarRules` is a UI-type-free object so the Compose toolbar and the `UIToolbar` derive
item enablement from the same rules.

**The built-in keypad (`NumberInputConfig.useBuiltInKeypad`, off by default)** is one Compose
implementation shared by both platforms, not a Compose keypad plus a UIKit one — a keypad is a grid of
buttons over shared state, with none of the caret/selection/input-method behaviour that forced the field
itself to be native on iOS. Each platform only suppresses its own system keyboard: Android sets
`readOnly = true` on the `BasicTextField` (focusable, so the caret and the focus-loss commit path
survive; `enabled = false` would have taken focus with it), iOS gives the `UITextField` an empty
`inputView` and drops the `inputAccessoryView`, since the Compose keypad carries the toolbar row itself.
Every press (`NumberInputState.pressDigit/pressDecimalSeparator/pressBackspace`) is expressed as a
`rawText` edit and routed through the existing `onTextChange`, so the fraction cap, the one-separator
rule and the keystroke filter apply to the keypad for free rather than being restated —
`NumberInputKeypadRules` only asks the same conditions in advance so a key can be greyed out before it's
pressed. `NumberInputHost` renders the keypad in place of the bare toolbar (never both; the keypad's row
*is* the toolbar) and needs it on **both** platforms now, since the keypad is Compose everywhere.

The decimal key is the reason the feature exists: it reads `NumberInputState.decimalKeyLabel`, this
field's own locale separator, rather than following the device region the system decimal pad is stuck
with.

A real accessibility defect surfaced building this, worth knowing about before touching
`NumberInputKeypad.kt` again: a `clickable` `Box` with a `BasicText` child publishes the box and the
text as **separate** semantics nodes. On iOS every digit key reached the accessibility tree as bare
static text — no button role, no identifier, a frame the size of the glyph rather than the key —
unreachable by VoiceOver and untappable by a UI test, while backspace looked fine only because it
happened to carry a `contentDescription`. The fix is three things together on the `Key` composable, and
dropping any one reopens the tree: `Role.Button` and `onClickLabel` on `clickable` itself,
`semantics(mergeDescendants = true) { contentDescription = ... }` layered after it, and
`clearAndSetSemantics {}` on the inner `BasicText` so the glyph stops publishing a node of its own.
`NumberInputKeypadSemanticsTest` (`iosTest`, via Compose's `runComposeUiTest`) pins this — asserting
only `assertHasClickAction()` on the tagged node is not enough, since the tag sits on the `Box` and that
assertion passes against the broken code too; the test has to check the *same* node also carries the
role and the spoken name, and that the glyph text is unreachable by `onAllNodesWithText`.

**Formatting (`LocaleNumberFormatter`)** is a public, injectable interface with an `expect fun
newLocaleNumberFormatter()` factory — `DecimalFormat` on Android, `NSNumberFormatter` on iOS, both
cached per locale and both mutating a shared instance, so every method sets the full set of properties
it depends on. Consumers with their own locale strategy can supply an implementation. The shared
digit/sign/decimal splitting for live grouping lives in the internal `liveFormat()` inline helper; each
platform passes only its separators and an integer-grouping lambda.

**Rendering (`PlatformNumberInputField`, `expect`/`actual`)** is where the two platforms diverge most:

- *Android* (`NumberInputField.android.kt`): a `BasicTextField` whose buffer stays ungrouped;
  `NumberGroupingVisualTransformation` inserts separators for display with an `OffsetMapping` built by
  construction (each raw index records the display offset it landed at), so the caret never drifts.
- *iOS* (`NumberInputField.ios.kt`): a native `UITextField` via `UIKitView`, because only UIKit can
  attach a real `inputAccessoryView` toolbar to the keyboard. Its buffer *is* grouped, so the composable
  converts at the boundary — `formatLive()` out, `ungroupTypedText()` on the way in. `NumberInputCoordinator`
  (an `NSObject` + `UITextFieldDelegateProtocol`) owns selector/target-action wiring and exposes
  lambda properties the composable refreshes each recomposition.

Three things on the iOS path are load-bearing and each looks removable:

- `UIKitInteropProperties(isNativeAccessibilityEnabled = true)` on the `UIKitView`. It defaults to
  **false**, which makes Compose publish its own semantics for the interop subtree and drop the hosted
  view from the accessibility hierarchy entirely — `TAG_FIELD` then resolves to nothing for UI tests
  and VoiceOver, however correctly KVC set it. `NumberInputIosBridgeTest` still passes in that state,
  because the property really is set; nothing is reading it. Symptom: no `text-field` role anywhere in
  the tree.
- Ungrouping cannot be a blanket `replace(groupingSeparator, "")`. On de-DE/vi-VN the grouping
  separator *is* `"."` — the character the decimal keypad emits — so stripping it swallows a typed
  decimal point and the fraction merges into the integer (`25500.8` → `255008`). `ungroupTypedText()`
  resolves that against the previous buffer before stripping.
- That diff must use `NumberInputCoordinator.lastWrittenText`, never the composition's `displayText`.
  The latter is only a frame-accurate mirror while recomposition keeps pace with typing; hardware
  keyboards and paste outrun it, and a stale previous-buffer misreads which characters were inserted.
  `resyncText()` is the single write path so the tracked value cannot drift — it also repairs the
  buffer after a *rejected* keystroke, which nothing else will, since a rejection mutates no
  observable state and therefore schedules no recomposition.

**Toolbar placement** is the other asymmetry. iOS gets it from the system. Android has no equivalent,
so `NumberInputHost` provides a single-slot `compositionLocalOf` holder that the focused field publishes
itself into; the host renders the toolbar bottom-aligned with `imePadding()` so Compose animates it in
lockstep with the IME. Consumers must supply `windowSoftInputMode="adjustResize"` and
`enableEdgeToEdge()` — a library cannot. Without a host the field falls back to rendering the toolbar
inline beneath itself. `NumberInputToolbarBar` is shared between both paths so they cannot drift.

`NumberInputField.android.kt` documents a rejected `Popup` approach; it looks obvious and is a dead
end (`WindowInsets.ime` reads 0 inside a separate window). Don't reintroduce it.

## Sample app for end-to-end testing

`/Users/hugues_mini/Codes/cmp` is a separate CMP app (`:androidApp`, `:shared`, `iosApp/`) that
consumes this library and can be launched on a device/simulator. It resolves
`dev.viethung:number-input` from **mavenLocal** at the version pinned in that repo's
`gradle/libs.versions.toml` (`numberInput`), so any change here must be published before the app sees
it — and a version bump here needs the same bump there:

```bash
./gradlew :number-input:publishToMavenLocal                    # in this repo, first
cd /Users/hugues_mini/Codes/cmp && ./gradlew :androidApp:assembleDebug
```

For iOS e2e (build, boot simulator, install, launch, UI interaction), **use xcodeBuildMCP** rather
than shelling out to `xcodebuild`/`simctl`. It is registered project-scoped in this repo's `.mcp.json`
(`npx -y xcodebuildmcp@2.7.0 mcp`) — not globally — so its tools are available whenever Claude Code runs
here. Verify the environment with `npx -y -p xcodebuildmcp@2.7.0 xcodebuildmcp-doctor`.

`.mcp.json` enables no workflows explicitly, so only the `simulator` group is exposed as MCP tools —
`build_run_sim`, `screenshot`, `snapshot_ui`. The `ui-automation` group (`tap`, `type_text`, `swipe`)
is **not** available as tools. Two ways round it, no restart needed for either:

- the same tools as a CLI: `npx -y -p xcodebuildmcp@2.7.0 xcodebuildmcp ui-automation <tool> --help`
- `axe` directly, e.g. `axe tap --id numberInput.field --udid <udid>`, which selects by identifier and
  avoids brittle coordinates

2.7.0 **bundles** `axe` at `node_modules/xcodebuildmcp/bundled/axe` inside its npx cache, so UI
automation works with nothing on PATH — `which axe` failing does not mean it is missing. Add
`enabledWorkflows: ["simulator", "ui-automation"]` to a project-local `.xcodebuildmcp/config.yaml` to
get the MCP tools proper (that one does need a restart).

The Xcode project is `cmp/iosApp/iosApp.xcodeproj`, scheme `iosApp`, bundle id
`org.example.project.cmp`. Drive elements by the `TestTags.kt` identifiers, which are set as
`accessibilityIdentifier` on iOS and surface in the tree as `AXUniqueId` (not `AXIdentifier`). All
four sample fields carry the same `numberInput.field` tag — it identifies the component, not the
instance — so address them by index. Toolbar items live in the keyboard window rather than the Compose
hierarchy and are exposed regardless of the interop accessibility setting.

Note: `cmp/androidApp/src/main/AndroidManifest.xml` does **not** set
`windowSoftInputMode="adjustResize"`, so the Android keyboard-tracking toolbar will not behave
correctly there until it does. Verify that before concluding a host/IME bug lives in this library.

The sample carries a fifth field for the built-in keypad (de-DE, `useBuiltInKeypad = true`), which is
how that path was verified on the iOS simulator. **The Android keypad path is compile-verified only** —
no emulator or device was available, so `readOnly = true` suppressing the IME while keeping focus, the
caret and the focus-loss commit is unconfirmed on a real Android runtime. That is the first thing to
check there.

A trap when iterating on iOS: publishing to mavenLocal is not enough. The `:shared` framework has to be
relinked (`./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 --refresh-dependencies` in the `cmp`
repo) *and* the running app stopped and relaunched, or `build_run_sim` will report success in a few
seconds while the simulator keeps executing the previous build. To check which code is actually running,
grep the installed binary — Kotlin/Native stores string literals as **UTF-16LE**, so a plain
`grep`/`strings` for a literal finds nothing and looks like proof the code is missing:
`python3 -c "d=open(p,'rb').read(); print(d.count('literal'.encode('utf-16-le')))"`.

## Tests

- `commonTest` uses `FakeLocaleNumberFormatter` (deterministic, no platform APIs) and covers
  `NumberInputState`, the grouping/offset-mapping transformation, and the keypad's press handlers and
  enablement rules (`NumberInputKeypadTest` — state-level, no composition).
- `androidUnitTest` (`AndroidLocaleNumberFormatterTest`, JVM, no device) is the counterweight to that
  fake. The fake models exactly two conventions, `,`/`.` and `.`/`,`, which is an assumption about the
  platform rather than a measurement of it; this suite measures it. It pins that the separators the
  fake hard-codes are what the JDK reports for en-US/de-DE/vi-VN, runs the whole-number and keystroke
  paths through the real `DecimalFormat`, and covers locales the fake cannot express — fr-FR grouping
  is U+202F (narrow no-break space) and de-CH is U+2019, neither of them typeable. Non-ASCII
  separators are written as `\u` escapes in that file on purpose: NNBSP against an ordinary space
  decides whether a case resolves or is refused, and no reader can tell them apart by eye.
  It also pins the documented limits as tests so they stay visible (a plain space in fr-FR, an ASCII
  apostrophe in de-CH, and en-IN lakh grouping are all refused), and the reason resolving must precede
  parsing: handed `1234.5` directly, a de-DE `DecimalFormat` returns **12345.0**, reading `.` as its
  own grouping separator.
- `iosTest` runs on a simulator and covers only what a Compose test cannot reach: selector dispatch,
  key-value coding (`accessibilityIdentifier` must be set via KVC — casting to
  `UIAccessibilityIdentificationProtocol` compiles but throws at runtime), and the real `UIToolbar`.
  `IosLiveFormatProbeTest` is a regression probe for a specific on-device vi-VN grouping defect.
  `IosTypedDecimalTest` covers the grouped-buffer boundary — it replays keypad sequences through the
  real formatter and state machine, and its
  `typing_faster_than_recomposition_still_resolves_the_decimal_point` case drives a real `UITextField`
  with *no* recomposition at all, which is the only way to catch a stale previous-buffer diff.
  `IosKeypadSuppressionTest` covers the built-in keypad's UIKit half: that an empty `inputView` is what
  actually replaces the system keyboard, and that the keypad's Done reaches the same
  `resignFocus()`/commit path the `UIToolbar`'s Done does. `NumberInputKeypadSemanticsTest` uses
  Compose's `runComposeUiTest` — the same semantics tree Compose hands the platform accessibility
  service, run on this target's real simulator — to pin the keypad's button roles and spoken names;
  see the keypad note under Architecture for why a plain click-action assertion does not catch the
  defect this guards.
- There are no instrumented Android UI tests wired up (`androidUnitTest` above is JVM-only), so the
  built-in keypad's Android half (`readOnly` suppressing the IME) is compile-verified only — see the
  caveat under "Sample app for end-to-end testing".
