# Android style and component guide

Source defaults, not measurements. [UI workflow](../guides/android/ui.md) · [Behavior/evidence](../status/current-state.md#shared-android-presentation).

## Source map

| Area | Source |
|---|---|
| Spacing / sizes / padding | [UiSpacing.kt](../../android/app/src/main/java/com/hos/rushdpatients/ui/theme/UiSpacing.kt), [UiSize.kt](../../android/app/src/main/java/com/hos/rushdpatients/ui/theme/UiSize.kt), [UiPadding.kt](../../android/app/src/main/java/com/hos/rushdpatients/ui/theme/UiPadding.kt) |
| Density/borders/elevation/opacity/motion/dialog/layout | [UiTokens.kt](../../android/app/src/main/java/com/hos/rushdpatients/ui/theme/UiTokens.kt) |
| Internal profiles | [UiStyleCatalog.kt](../../android/app/src/main/java/com/hos/rushdpatients/ui/theme/UiStyleCatalog.kt) |
| Typography | [Type.kt](../../android/app/src/main/java/com/hos/rushdpatients/ui/theme/Type.kt) |
| Theme surfaces, accent and shapes | [Theme.kt](../../android/app/src/main/java/com/hos/rushdpatients/ui/theme/Theme.kt) |
| Clinical badge severity | [PatientBadgeColors.kt](../../android/app/src/main/java/com/hos/rushdpatients/ui/theme/PatientBadgeColors.kt) |

Arabic: `res/values/strings_ui.xml`/`strings.xml`; reuse actions, numbered placeholders, `rememberUiStrings`, `UiLabels`, `uiNumber`, `uiPlural`. Keep model/interchange codes separate. Never rewrite saved text: labs use content-aware LTR/monospace, other clinical text content-aware RTL.

## Metrics

| Role | Default / rule |
|---|---|
| Numeric spacing | `UiSpace`: 1/2/4/6/8/12/16/24/32dp; semantic aliases: gutter/card/section 8, block 12, control gap 4. |
| Targets / controls | Always ≥48dp interactive; visual compact/standard/comfortable minima 32/40/48dp, growing for text. |
| Patient density | Compact padding 4h/2v, gap 4; normal 12h/8v, gap 8; list gap 8dp. Density changes spacing, not fonts/targets. |
| Padding | Content 4h/2v; compact 2h/1v; fields 4h/8top/2bottom; chips 8h/2v (dp). |
| Surfaces / fields | Light: muted gray-green canvas/off-white panels; Dark: slate layers; AMOLED: black canvas/near-black panels. Neutral outlines separate layers; shared text/picker fill blends surface→surfaceVariant85%. Accent and severity retain separate roles. |
| Borders | Hairline .5, normal 1, strong 1.5, selected/focused 2dp. Neutral cards blend surface→outline65%; selected/focused retain accent. |
| Elevation / opacity | Flat/low/raised/overlay 0/1/2/6dp; navigation/action 2/3dp. Enabled 1, disabled content .38/container .12. |
| Motion | Expand/collapse/highlight 160/130/220ms; indicator 160, placement 200, fast token 90. Bulk/reduced motion immediate. |
| Shapes / sizes | Corners 4/8/12/16/24dp; icons 12/16/20/24/32/40dp; progress/loading 40dp, stroke 2; severity edge 8dp; colored patient cards add4dp start clearance. |
| Dialogs | Viewport fraction .85; compact/standard/review/sheet caps 260/420/440/640dp. Preserve incoming minimums/scroll/actions. |
| Content widths | General 720; patient form 840; empty ward 520; dock 480; rail 80dp. |
| Adaptive thresholds | Fields/grid 360; card columns 440; toolbar actions 240; metric pair 280dp. Fields/toolbar/metrics account for font scale. |
| Panes | Split width 760; primary/support minima 320/360; fold width/height 360/360 and clearance 8dp. Medium support ≥520×640, height 240dp. |
| Rail / gestures | Rail window ≥600×480dp; patient transfer tap/long press opens review; rounds swipe ≥64dp and 20% width. |

Keep tokens independent, row selection/toggle semantics explicit and labels wrapping. Severity is independent of accent/density; diagnosis is not an error. Source tokens are authoritative.

## Internal profiles and local overrides

Appearance exposes theme/text size/reduced motion; spacing follows shared defaults and compatible profiles stay internal. Retired overrides normalize to current profile, comfortable controls, visible navigation, compact Add and explicit expansion. `LocalUiStyle` owns spacing/motion; live `UiPreferences` is encrypted/device-local, demo memory-only except [accent/appearance](../guides/android/demo.md#isolation-and-lifecycle). PDF colors follow app accent; reduced motion zeroes app animation, platform sheets/ripples follow Android.

```kotlin
ProvideUiPreferences(UiPreferences(reducedMotion = true)) {
    // Shared components consume LocalUiStyle/LocalUiPreferences.
}
```

Use `ProvideUiDefaults` for local size/density without a saved setting.

## Typography and font provenance

| Role | Size / line height (sp) | Weight |
|---|---|---|
| Display large / medium / small | 40/48 · 34/42 · 30/38 | Bold |
| Headline large / medium / small | 28/34 · 26/32 · 24/30 | Bold |
| Title large / medium / small | 22/28 · 16/25 · 14/22 | Bold |
| Body large / medium / small | 16/25 · 15/24 · 14/22 | Normal |
| Label large | 14/22 | Bold |
| Label medium / small | 13/20 · 13/20 | Medium |

Bundled Tajawal Regular 400/Medium 500/Bold 700 loads before first text layout; PDF resolution runs on IO. No upstream SemiBold 600; emphasis uses real Bold. Small app/combined lower scale: .92; combined upper: 2.0. Names stay bold; sizes may differ by context.

[Tajawal](https://github.com/google/fonts/tree/main/ofl/tajawal), license `android/app/src/main/assets/fonts/OFL.txt`. Medium SHA-256: `1ff8bd943a261c9dd7906bdb1993cab87b6d925a7fb5f848cdb51ed301531090`. No runtime download; technical/lab fields keep system monospace.

## Component ownership

| Need | Shared owner | Caller retains |
|---|---|---|
| Fields/pickers/auth | `AppTextField`, `AppPickerField`, `PasswordField`, `AppCenteredContent` | Draft/validation/keyboard/busy state. |
| PIN / Telegram ID | `PinField` owns digit limit/numeric-password input; `TelegramIdField` owns signed/numeric keyboards | Memory-only PIN, ID validation and stale-value guards. |
| Actions/identity/text | `UiAction`, `AppActionLabel`, `PatientIdentityText`, `AppClinicalSection` | Patient/revision, full content, mutations. |
| Chips/choices/sections | `AppChip`, `AppFilterChip`, `AppChoiceChips`, `AppChoiceRow`, `AppSelectableSurface`, `AppSectionHeader` | Selection/grouping and informational/selectable intent. |
| Clinical/neutral containers | `ContainerColors`, `toneColors`, `patientBadgeColors`, `AppNotice`, `AppMetricGrid` | Severity, counts and actions; pending is a semantic tone. |
| Dialogs/transient UI | `AppAlertDialog`, action helpers, `AppSheet`, `AppMenu`, `AppSnackbarHost` | Scrolling/confirmation/stale review/dismissal/permissions. |
| Ward full-screen frame | `WardFullscreenDialog`: native/Compose RTL, safeDrawing/IME/fold handling | Content/actions/properties/dismissal and state. |
| Icon/text/loading/list chrome | `AppIcons`, `AppText`, `AppLoadingState`, `AppStateHost`, `AppListToolbar` | Labels, retry/operation lifecycle and navigation. |

Reuse a component when it owns repeated behavior/semantics; extract substantial feature sections locally. Avoid pass-through wrappers or moving drafts/launchers/review state to gain reuse. Single-choice rows use `selectableGroup()`; caller owns the selected value.

Icons beside visible labels are decorative. Actions (Edit/Full record/Restore/Review/«إرسال تقرير»/Save/Cancel) must reveal consequences, including recycle removal or pending-edit replacement on Fetch. Clinical/report reviews keep detailed callbacks/fixed keyboard-safe controls. Previews use dummy data, preserving system bars. Historical layout passes do not establish full [rendered accessibility](../status/testing-handoff.md).
