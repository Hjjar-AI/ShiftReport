# Android style and component guide

Source defaults (not measurements). [UI guide](../guides/android-ui.md) · [Current behavior/verification](../status/current-state.md#shared-android-presentation).

## Source map

| Area | Source |
|---|---|
| Basic spacing | [UiSpacing.kt](../../android/app/src/main/java/com/hos/rushdpatients/ui/theme/UiSpacing.kt) |
| Control/icon sizes | [UiSize.kt](../../android/app/src/main/java/com/hos/rushdpatients/ui/theme/UiSize.kt) |
| General/field padding | [UiPadding.kt](../../android/app/src/main/java/com/hos/rushdpatients/ui/theme/UiPadding.kt) |
| Card density, borders, elevation, opacity, motion, dialog/layout roles | [UiTokens.kt](../../android/app/src/main/java/com/hos/rushdpatients/ui/theme/UiTokens.kt) |
| Selectable profile definitions | [UiStyleCatalog.kt](../../android/app/src/main/java/com/hos/rushdpatients/ui/theme/UiStyleCatalog.kt) |
| Typography | [Type.kt](../../android/app/src/main/java/com/hos/rushdpatients/ui/theme/Type.kt) |
| Theme surfaces, accent and shapes | [Theme.kt](../../android/app/src/main/java/com/hos/rushdpatients/ui/theme/Theme.kt) |
| Clinical badge severity | [PatientBadgeColors.kt](../../android/app/src/main/java/com/hos/rushdpatients/ui/theme/PatientBadgeColors.kt) |

UI Arabic wording: `res/values/strings_ui.xml` and `strings.xml`; reuse common actions, numbered placeholders, `rememberUiStrings`, `UiLabels`, `uiNumber` and `uiPlural`. Model/interchange codes keep their own ownership. Never rewrite saved clinical text for display; labs use content-aware LTR/monospace, other clinical text content-aware RTL.

## Metrics

| Role | Default / rule |
|---|---|
| Numeric spacing | `UiSpace`: 1/2/4/6/8/12/16/24/32dp; semantic aliases: gutter/card/section 8, block 12, control gap 4. |
| Targets / controls | Always ≥48dp interactive; visual compact/standard/comfortable minima 32/40/48dp, growing for text. |
| Patient density | Compact padding 4h/2v, gap 4; normal 12h/8v, gap 8; list gap 8dp. Density changes spacing, not fonts/targets. |
| Padding | Content 4h/2v; compact 2h/1v; fields 4h/8top/2bottom; chips 8h/2v (dp). |
| Borders | Hairline .5, normal 1, strong 1.5, selected/focused 2dp. Neutral cards blend surface→outline65%; selected/focused retain accent. |
| Elevation / opacity | Flat/low/raised/overlay 0/1/2/6dp; navigation/action 2/3dp. Enabled 1, disabled content .38/container .12. |
| Motion | Expand/collapse/highlight 160/130/220ms; indicator 160, placement 200, fast token 90. Bulk/reduced motion immediate. |
| Shapes / sizes | Corners 4/8/12/16/24dp; icons 12/16/20/24/32/40dp; progress/loading 40dp, stroke 2; severity edge 4dp. |
| Dialogs | Viewport fraction .85; compact/standard/review/sheet caps 260/420/440/640dp. Preserve incoming minimums/scroll/actions. |
| Content widths | General 720; patient form 840; empty ward 520; dock 480; rail 80dp. |
| Adaptive thresholds | Fields/grid 360; card columns 440; toolbar actions 240; metric pair 280dp. Fields/toolbar/metrics account for font scale. |
| Panes | Split width 760; primary/support minima 320/360; fold width/height 360/360 and clearance 8dp. Medium support ≥520×640, height 240dp. |
| Rail / gestures | Rail window ≥600×480dp; patient transfer tap/long press opens review; rounds swipe ≥64dp and 20% width. |

Keep purpose-specific tokens independent; whole-row controls own selection/toggle semantics, labels wrap. Severity differs from accent/density; diagnoses are not errors. [UiTokens](../../android/app/src/main/java/com/hos/rushdpatients/ui/theme/UiTokens.kt) is authoritative for layout/border/motion constants.

## Internal profiles and local overrides

Profile definitions remain internal/compatible; Appearance exposes theme/text size/normal spacing/reduced motion. Retired overrides normalize to current profile, comfortable controls, visible navigation, compact Add and explicit expansion. `LocalUiStyle` owns spacing/motion; `UiPreferences` stays encrypted/device-local (demo memory). Accent/PDF independent; reduced motion zeroes app-owned animation, platform sheets/ripples follow Android.

```kotlin
ProvideUiPreferences(UiPreferences(reducedMotion = true)) {
    // Shared components consume LocalUiStyle/LocalUiPreferences.
}
```

Use `ProvideUiDefaults` for a local size/density override without a new saved setting.

## Typography and font provenance

| Role | Size / line height (sp) | Weight |
|---|---|---|
| Display large / medium / small | 40/48 · 34/42 · 30/38 | Bold |
| Headline large / medium / small | 28/34 · 26/32 · 24/30 | Bold |
| Title large / medium / small | 22/28 · 16/25 · 14/22 | Bold |
| Body large / medium / small | 16/25 · 15/24 · 14/22 | Normal |
| Label large | 14/22 | Bold |
| Label medium / small | 13/20 · 13/20 | Medium |

Tajawal Regular 400/Medium 500/Bold 700 is bundled and loaded asynchronously; PDFs resolve the same resources on IO. Upstream has no SemiBold 600; emphasized roles use real Bold. Small app scale and combined lower bound are .92; combined upper bound 2.0. Keep patient names bold in their context; font size need not match every screen.

[Upstream Tajawal](https://github.com/google/fonts/tree/main/ofl/tajawal); license: `android/app/src/main/assets/fonts/OFL.txt`. Medium SHA-256: `1ff8bd943a261c9dd7906bdb1993cab87b6d925a7fb5f848cdb51ed301531090`. No runtime font download; technical/lab fields retain system monospace.

## Component ownership

| Need | Shared owner | Caller retains |
|---|---|---|
| Fields/pickers/auth layouts | `AppTextField`, `AppPickerField`, `PasswordField`, `AppCenteredContent` | Draft, validation, keyboard and busy state. |
| Actions/identity/clinical text | `UiAction`, `AppActionLabel`, `PatientIdentityText`, `AppClinicalSection` | Patient/revision, full content and explicit mutations. |
| Chips/choices/sections | `AppChip`, `AppFilterChip`, `AppChoiceChips`, `AppSelectableSurface`, `AppSectionHeader` | Informational vs selectable semantics and selection. |
| Clinical/neutral containers | `ContainerColors`, `toneColors`, `patientBadgeColors`, `AppNotice`, `AppMetricGrid` | Severity, counts and actions; pending is a semantic tone. |
| Dialogs/transient UI | `AppAlertDialog`, dialog action helpers, `AppSheet`, `AppMenu`, `AppSnackbarHost` | Body scrolling, confirmation, stale review, dismissal and permissions. |
| Icon/text/loading/list chrome | `AppIcons`, `AppText`, `AppLoadingState`, `AppStateHost`, `AppListToolbar` | Labels, retry/operation lifecycle and navigation. |

Visible text makes adjacent icons decorative. Generic actions (Edit/Full record/ Restore/Review/«إرسال تقرير»/Save/Cancel) must not hide specific consequences such as recycle removal or previous-shift fetch. Specialized clinical/report reviews retain their detailed callbacks and fixed keyboard-safe controls. Previews use dummy data and do not alter system bars. Selected device/layout checks passed; full rendered accessibility remains in [Remaining checks](../status/testing-handoff.md).
