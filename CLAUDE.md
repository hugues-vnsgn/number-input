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
*is* the toolbar) and needs it on **both** platforms now, since the keypad is Compose everywhere — on iOS
it is effectively required, not merely recommended; see the fail-safe under Toolbar placement.

The decimal key is the reason the feature exists: it reads `NumberInputState.decimalKeyLabel`, this
field's own locale separator, rather than following the device region the system decimal pad is stuck
with.

Two things the keypad needs that the system keyboard gets from the OS, both found on a simulator after
the keypad itself worked:

- **It publishes its own inset.** Compose exposes the system keyboard as `WindowInsets.ime`, so
  `imePadding()` keeps content clear of it; nothing does that for a keypad the library draws. The host
  measures the keypad (after `navigationBarsPadding`, so the figure covers everything below its top
  edge) and publishes `LocalNumberInputKeypadHeight`, with `Modifier.numberInputKeypadPadding()` as the
  `imePadding()` counterpart. It is measured, not computed from `keyHeight` — the toolbar row, row
  spacing and nav-bar inset all contribute. `onSizeChanged` does not fire on removal, so a
  `DisposableEffect` resets it or the reserved space outlives the keypad. Consumers must apply it
  **before** `verticalScroll`: padding the container shrinks the viewport, which is what makes a
  focused field scrollable out from behind the keypad, whereas padding the content leaves the obscured
  region exactly as it was.
- **The iOS field scrolls itself into view.** A Compose `BasicTextField` gets this free, but focus for
  the iOS field lives in UIKit, so Compose has no focus event and a low field just stays covered. The
  field holds a `BringIntoViewRequester` and requests on focus, keyed on the published keypad height so
  the scroll targets the already-shrunken viewport rather than the full-height one.

**The keypad animates, and that is what forces the host to outlive the field's focus.** One
`animateDpAsState` in `NumberInputHost` is *both* the keypad's `offset` and the published inset, so the
drawn edge and the reserved space cannot drift apart. The exit is the hard half: `host.request` goes
null the instant focus is lost, which would drop the keypad out of composition with nothing left to
animate, so the host latches a `retainedRequest` and draws from that until the animation's
`finishedListener` reports it settled at zero — `onSizeChanged` never fires on removal, and disposal now
happens at the *end* of the exit, so neither can release it. Three details each look like noise and are
not: only a *keypad* request is latched (latching a toolbar request would animate out the wrong field
when focus moves keypad → toolbar), the latch is written during composition rather than in a
`LaunchedEffect` (a frame late is long enough for the keypad to blink out before sliding), and a
different incoming request cuts the retention immediately so the new field never waits on the old
keypad's exit. `measuredKeypadHeight` is deliberately *not* cleared on close: it is the height to
animate from on the next open. Because the published height now interpolates, there are two locals —
`LocalNumberInputKeypadHeight` to lay out against, `LocalNumberInputKeypadTargetHeight` to key effects
on. The iOS field's `BringIntoViewRequester` keys on the target for exactly this reason; keying on the
animating value re-issues the scroll every frame.

**Styling is three objects, not one** (2.0.0). Field properties stay on `NumberInputStyle`; the toolbar
row's live on `NumberInputToolbarStyle`, the keypad's on `NumberInputKeypadStyle`. The split exists
because reproducing a real design spec needed ~20 more tokens and 45 flat parameters would have been
unreadable — see the migration table in the README.

A key's appearance is **four `NumberInputKeyStyle` objects** — `restKey`, `utilityKey`, `pressedKey`,
`disabledKey` — one per swatch in a typical spec rather than a role×state matrix, because pressed and
disabled look the same whichever key is in them. Two rules decide what is drawn, and conflating them is
the easy mistake: *role fallback* (`fallingBackTo`) runs once at resolution and fills `utilityKey` from
`restKey`, colours **and** geometry; *state merge* (`mergedWithState`) runs at draw time and contributes
**colours only**. Geometry stays with the role deliberately — an integer-only field disables its decimal
key for the field's whole life, so a disabled state carrying geometry would strand that one key centred
at the digit size while every neighbour kept the utility treatment. `shadowColor` is outside the merge
entirely: the lip is a resting affordance, and a pressed key that keeps one does not read as pressed.

The governing constraint for every new token is that **an unstyled keypad renders exactly as 1.x did**.
That is why the utility key falls back to the rest key, why the lip defaults to absent, and why
`disabledKey`'s colours are the one thing `resolveThemedColors` deliberately does *not* substitute —
unset there means "multiply by `disabledAlpha`", which is what 1.x drew. `NumberInputStyleResolveTest`
pins all three. The single exception is the pressed state, which gets a themed default because 1.x gave
a held key no feedback at all.

Two drawing details are load-bearing. The lip is `drawBehind`, not `Modifier.shadow`: a `0 1px 0` spec
is a hard offset edge with no blur or spread, which an elevation shadow cannot produce. And `clickable`
takes `indication = null`, because the pressed style *is* the indication — a default ripple would draw a
second, un-styleable one over it.

**Dark mode** covers only the colours the library draws itself (the keypad's and the toolbar row's).
They default to `Color.Unspecified` and `resolveThemedColors` substitutes a light or dark
palette for whichever were left unset. `Color.Unspecified` rather than a nullable `Color?` is what makes
an explicit `Color.Transparent` a real choice instead of another kind of absence — `Color.takeOrElse`
falls back on `Unspecified` only. The field's own colours keep their literal defaults: a consumer's
design system has values for those, and none for a stand-in system keyboard.

Resolution is not optional plumbing. `Color.Unspecified` draws as *transparent*, so an unresolved
sentinel reaching `toUIColor()` yields an invisible toolbar rather than an error — nothing downstream can
tell it from a colour someone chose (`IosKeypadSuppressionTest` pins that alpha at 0 so the requirement
has a stated reason). It happens once per entry point that draws: each platform's
`PlatformNumberInputField`, and `NumberInputHost` for the request it renders. iOS needs it at the
composable frame because `buildToolbar`/`applyStyle`/`configureInputViews` run inside `UIKitView`'s
`update`, which is not `@Composable`. Resolving is idempotent but **not** reversible — a resolved style
is *set*, so re-resolving it for the other appearance keeps the first palette. That is why the field
publishes its **unresolved** style into the host: the host's request can outlive that field's focus by a
full exit animation, and a style resolved in the field would freeze at the appearance that held when
focus arrived, keeping a dark keypad on a device flipped to light mid-edit.

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

**Backspace hold-to-repeat** (400 ms, then a delete every 80 ms) sits next to that invariant and nearly
broke it twice. Two facts about pointer dispatch decide the whole implementation, and both were found on
a simulator rather than by reading:

- The hold detector must sit **inside** `clickable`, not alongside it. Pointer events reach the
  innermost node first on the main pass, so a `pointerInput` applied before `clickable` — the natural
  reading of "add a gesture to this key" — never sees a down at all. A real three-second hold produced
  no repeat whatsoever in that arrangement, which looks exactly like a timing bug and is not one. `Key`
  therefore takes a `holdGesture: Modifier` applied *after* its own `clickable`.
- Moving it inside then inverts the problem: the detector consumes the touch, so `clickable`'s `onClick`
  stops firing for real taps. The tap therefore lives in the detector too (`onTap`), and `clickable`'s
  `onClick` becomes the **accessibility-activation** path — VoiceOver fires the semantics action, not a
  pointer event. Both call one `deleteOnce`, so they cannot drift, and `clickable` still supplies
  `Role.Button`, the click action and the spoken name, which is why the semantics tree is unchanged.

`repeatFired` stops the release landing one extra delete: with `onLongPress` unset, `detectTapGestures`
reports a tap on *any* release however long the hold. It is read on tap and cleared on the *next* press,
which is deterministic; clearing it on release would race that tap. The repeat is scoped to the gesture
(`coroutineScope` inside `onPress`) rather than launched on the composition, so it cannot outlive the
pointer. `NumberInputKeypadBehaviourTest` drives all of this through `runComposeUiTest` with
`mainClock.autoAdvance = false`, which is how the timings are pinned without a `kotlinx-coroutines-test`
dependency this module deliberately does not carry.

Haptics need **no `expect`/`actual`**: CMP 1.9.0's iOS target already implements `HapticFeedback`
(`CupertinoHapticFeedback`, over `UIImpactFeedbackGenerator`), so `LocalHapticFeedback` works straight
from `commonMain`. A held backspace ticks once, at the threshold — at 80 ms intervals a tick per delete
is a continuous buzz, and the platform generators are not built to be driven that fast.

**Visibility and enablement are different questions**, and the library now answers them differently for
two controls on purpose. `allowNegative = false` **hides** ± (`NumberInputToolbarRules.signVisible`), on
the Compose row and the native `UIToolbar` alike, because it could never become enabled for the life of
the field. An integer-only field **disables** its decimal key instead, because hiding it would leave a
hole in a fixed grid. Anyone "tidying" these into one rule will break one of them.

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

Four things on the iOS path are load-bearing and each looks removable:

- Input views are set from `configureInputViews()` in `update` **and** re-asserted from
  `textFieldDidBeginEditing`, followed by `reloadInputViews()`. `factory` is the obvious home and is
  wrong twice over: it runs once, pinning the first `useBuiltInKeypad` value for the view's lifetime, and
  it cannot handle focus moving *between* fields while a keyboard is already up — UIKit goes on
  presenting the outgoing responder's input view and never queries the incoming one, so tapping a keypad
  field straight after a system-keyboard field left the system keyboard covering the keypad. A cold tap
  worked, which made this look like a timing bug. `reloadInputViews()` forces the re-read and is only
  valid while first responder, which is why that call site is the delegate callback and not `update`. It
  runs for the keypad path only — reloading the system-keyboard path would dismiss and re-present an
  identical keyboard. `configureInputViews` is idempotent (one reused zero-size suppressor) because
  `update` calls it on every recomposition. Note the suppressor must be a real zero-size `UIView`: `nil`
  means "use the default input view", i.e. the keyboard being replaced.
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
`enableEdgeToEdge()` — a library cannot. Without a host the Android field falls back to rendering the
toolbar inline beneath itself. `NumberInputToolbarBar` is shared between both paths so they cannot drift.

The hostless *keypad* case is the one asymmetry left, and it is a fail-safe rather than a fallback. The
iOS field is a `UIKitView` with no inline fallback of any kind, so suppressing the system keyboard
without a host left no keyboard **and** no keypad — a field that focuses, shows a caret and cannot be
typed into. Suppression is therefore conditional on `NumberInputCoordinator.keypadHosted`, which the
composable refreshes from `LocalNumberInputToolbarHost` each recomposition (a `CompositionLocal` the
coordinator cannot read itself); with no host the field takes the ordinary system-keyboard-plus-`UIToolbar`
path. That means a wrong-looking decimal key — the thing the keypad exists to fix — but the value is
still right, since `NumberInputState` translates a keypad "." into the field's own separator whichever
keyboard sent it. `shouldSuppressSystemKeyboard()` is the single condition, read by both
`configureInputViews` and `textFieldDidBeginEditing`, so the two cannot disagree about which keyboard
this field is on.

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

- the same tools as a CLI: `npx -y -p xcodebuildmcp@2.7.0 xcodebuildmcp ui-automation <tool> --help`.
  Note its flags are `--simulator-id`/`--element-ref`, not `--udid`/`--id`, and its `elementRef`s come
  from a snapshot the **CLI process** holds — refs from the MCP `snapshot_ui` are not visible to it and
  fail with `SNAPSHOT_MISSING`. For one-off taps `axe` is less trouble.
- `axe` directly, e.g. `axe tap --id numberInput.field --udid <udid>`, which selects by identifier and
  avoids brittle coordinates. It refuses rather than guesses when an id is ambiguous, which the five
  same-tagged fields trigger — read frames from `describe-ui` and tap `-x/-y` for those.

2.7.0 **bundles** `axe` at `node_modules/xcodebuildmcp/bundled/axe` inside its npx cache, so UI
automation works with nothing on PATH — `which axe` failing does not mean it is missing. Add
`enabledWorkflows: ["simulator", "ui-automation"]` to a project-local `.xcodebuildmcp/config.yaml` to
get the MCP tools proper (that one does need a restart). Locate the binary with
`find ~/.npm/_npx -type f -name axe -path "*bundled*"`.

**The simulator here has a hardware keyboard connected, and that changes what a screenshot proves.** The
software keyboard is still created and still in the accessibility tree, but it is positioned *below* the
screen — keys at y≈898–1114 against a screen bottom of ~874 — so only the `inputAccessoryView` toolbar is
visible at the bottom of the frame. A screenshot therefore looks exactly like "no keyboard appeared",
which is the same symptom as the suppression defect. Do not read it as one. `describe-ui` shows the keys;
compare geometry against a *plain* field on the main tab, which has always used the system keyboard, and
identical numbers mean the path is behaving normally. Tapping those keys is impossible while they are
off-screen, so drive text with `axe type` instead.

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

A third tab, `NoHostKeypadSampleScreen` (`tabNoHostKeypad`), holds a keypad field composed **outside** any
`NumberInputHost` — the hostless fail-safe, which the main screen cannot show because it wraps everything
in a host. Verified on the iOS simulator: the field takes focus, gets the system decimal pad and the
`UIToolbar`, and typing `1.5` lands `1,50` after Fertig with a bound value of `1.5` — so the de-DE
separator translation survives a "." that came from the *system* keyboard, and the commit path still runs.
Its toolbar colours are deliberately left unset, so it doubles as the light/dark palette check. The tab
row scrolls horizontally now; four buttons do not fit a phone width and the fourth was clipped rather
than wrapped.

A fourth tab, `OFNumpadSampleScreen` (`tabOFNumpad`), rebuilds the BFSOne OFNumpad spec's in-context
frame from `numpad-design/OFNumpad Spec.html`. It is the proof that a real design is reachable through
public parameters alone: every olive value lives in the sample's `OFNumpadTokens.kt`, none in the
library. Its Amount field is `significantDigits = 0` (the spec's integer-VND rule), so the decimal key
shows the *disabled* swatch without contriving anything, and its VAT field sets `allowNegative = false`,
which removes ± and produces the spec's Quantity bar variant. Between them the two fields put all four
key swatches and both bar variants on one screen.

Note the tab row now needs **two** swipes' worth of scrolling to reach it, and `tabOFNumpad` sits off
screen at x≈440 on a 402pt device until you do — `describe-ui` will list it with an off-screen frame,
which is not a layout bug.

**Measure parity from `describe-ui` frames and pixel samples, not from the MCP screenshot.** That
screenshot comes back scaled (368×800 for a 402×874 device), so nothing measured on it is in dp. Use
`xcrun simctl io <udid> screenshot --type=png` for a true 3× capture and sample colours directly; the
accessibility frames are already in points and compare 1:1 with the spec's dp. Note also that a key's
accessibility frame is its *face* (52dp), not its face plus lip (53dp) — the lip is drawn outside the
padded region the click target occupies, which is correct and will otherwise read as a 1dp error.

The OFNumpad screen sizes its fields explicitly (`fillMaxWidth().height(48.dp)`). Without that the iOS
`UIKitView` collapses to the width of its own text — it has no intrinsic width worth having, and the
symptom is a field rendered as a few points wide rather than an error.

The sample's light/dark toggle moves **the app's** Material colours only. The keypad reads
`isSystemInDarkTheme()` directly, so it follows the OS and not that button — deliberately, since it
stands in for the system keyboard. To check the keypad's own palette, change the device appearance for
real: `xcrun simctl ui <udid> appearance light|dark`. The button is still the faster check for the case a
consumer with a light-only or dark-only design system hits, where the keypad holds the platform
appearance while the app around it is themed against it.

That fifth field sits at the bottom of the column, which is what surfaced the missing keypad inset: with
no padding it was behind its own keypad, and behind the system keyboard from any other field, so it was
unreachable by tap. The sample now applies `imePadding().numberInputKeypadPadding()` to the scroll
container. Two traps when driving it from a UI test: an `axe tap` at a y-coordinate below ~566 lands on
the *keyboard* rather than the field, so read frames from `describe-ui` rather than assuming positions,
and check the toolbar's language (`Löschen` vs `Clear`) to confirm which field actually holds focus — a
tap that misses still leaves a keyboard on screen and looks like success. A fast `axe swipe` over a
Compose scrollable is also dropped; pass `--duration 0.6`.

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
  `NumberInputStyleResolveTest` covers dark-mode resolution against the plain `resolveThemedColors`
  rather than the `@Composable` wrapper, for the same reason the keypad's rules are tested at state
  level: the branch table *is* the behaviour. What it pins is the substitution rule, not the palette —
  an explicitly-set colour surviving both appearances, `Color.Transparent` counting as a choice rather
  than an absence, and re-resolution being a no-op (the property the multi-entry-point resolution
  depends on).
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
  actually replaces the system keyboard, that `textFieldDidBeginEditing` re-asserts it (the
  field-to-field focus case above, which `update` alone does not cover) while leaving the
  system-keyboard path untouched, that `configureInputViews` is idempotent, and that the keypad's Done
  reaches the same `resignFocus()`/commit path the `UIToolbar`'s Done does. It also pins the hostless
  fail-safe from both directions — no host keeps the system keyboard and its toolbar, and a host
  arriving later starts suppressing — which is why every suppression test there has to set
  `keypadHosted` explicitly. It also holds the two
  dark-mode guards that need a real `UIColor`: that an unresolved `Color.Unspecified` crosses into UIKit
  as alpha 0 — stated first, so the guard has a reason — and that `buildToolbar` receives opaque tints in
  both appearances. `NumberInputKeypadSemanticsTest` uses
  Compose's `runComposeUiTest` — the same semantics tree Compose hands the platform accessibility
  service, run on this target's real simulator — to pin the keypad's button roles and spoken names;
  see the keypad note under Architecture for why a plain click-action assertion does not catch the
  defect this guards.
- There are no instrumented Android UI tests wired up (`androidUnitTest` above is JVM-only), so the
  built-in keypad's Android half (`readOnly` suppressing the IME) is compile-verified only — see the
  caveat under "Sample app for end-to-end testing".
