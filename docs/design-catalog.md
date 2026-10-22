# Android design catalog

Current values and selectable local style profiles. Values below are
presentation settings: they do not change clinical records, report readiness,
permissions, synchronization or PDF appearance.

Appearance → «تخصيص الواجهة» applies presentation choices on this device. The
current profile is the default; relaxed, quiet and clear are selectable. Preferences
are persisted in the existing encrypted settings table, with memory-only choices
in demo. They never enter Telegram snapshots or clinical exports.

## Find the source of a style

| Area | Source |
|---|---|
| Basic spacing | [UiSpacing.kt](../android/app/src/main/java/com/hos/rushdpatients/ui/theme/UiSpacing.kt) |
| Control/icon sizes | [UiSize.kt](../android/app/src/main/java/com/hos/rushdpatients/ui/theme/UiSize.kt) |
| General/field padding | [UiPadding.kt](../android/app/src/main/java/com/hos/rushdpatients/ui/theme/UiPadding.kt) |
| Card density, borders, elevation, opacity, motion, dialog/layout roles | [UiTokens.kt](../android/app/src/main/java/com/hos/rushdpatients/ui/theme/UiTokens.kt) |
| Selectable profile definitions | [UiStyleCatalog.kt](../android/app/src/main/java/com/hos/rushdpatients/ui/theme/UiStyleCatalog.kt) |
| Typography | [Type.kt](../android/app/src/main/java/com/hos/rushdpatients/ui/theme/Type.kt) |
| Theme surfaces, accent and shapes | [Theme.kt](../android/app/src/main/java/com/hos/rushdpatients/ui/theme/Theme.kt) |
| Clinical badge severity | [PatientBadgeColors.kt](../android/app/src/main/java/com/hos/rushdpatients/ui/theme/PatientBadgeColors.kt) |

`dp` describes layout size; `sp` describes text size and follows font scaling.
Motion durations are milliseconds. These are source defaults, not measurements
from a rendered device. Existing typography, accent and Light/Dark/AMOLED/System
settings remain separate choices.

## Current spacing and density

`UiSpace` names the numeric 1/2/4/6/8/12/16/24/32dp ladder; existing
`UiSpacing` names remain aliases at their previous values. Prefer semantic roles
for new callers: screenGutter, cardPadding, sectionGap (8dp), blockGap (12dp)
and controlGap (4dp). The 32dp endpoint is spare. Additional icon/border/elevation
stops remain available; runtime density and profile choices use their semantic roles.

| Token | Value | Purpose |
|---|---|---|
| `UiSpacing.micro` | 1dp | Small internal adjustment |
| `tiny` | 2dp | Closely related content |
| `small` | 4dp | Compact gap |
| `medium` | 6dp | Normal compact-app rhythm |
| `screen` | 8dp | Screen gutter |
| `section` | 12dp | Separation between larger sections |
| `largeSection` | 16dp | Larger section separation |
| `pageSection` | 24dp | Page-level separation |
| `touchTarget` | 48dp minimum | Interactive controls; can grow for labels/fonts |

| Patient-card metric | Compact | Normal |
|---|---|---|
| Horizontal padding | 4dp | 12dp |
| Vertical padding | 2dp | 8dp |
| Section gap | 4dp | 8dp |
| List gap | 8dp | 8dp |

Other padding: general content is 4dp horizontal / 2dp vertical; compact content
is 2dp / 1dp. Fields use 4dp horizontal, 8dp above the floating label and 2dp below.
A density choice changes padding/gaps, not font size or minimum action targets.

## Current borders, surfaces and motion

| Role | Current value |
|---|---|
| Normal border | 1dp, theme outline variant |
| Patient card border | 1dp, outline variant blended 22% toward the theme accent; selected/highlighted cards retain the stronger selected border |
| Selected border | 2dp, theme primary |
| Focused border | 2dp, theme primary |
| Flat surface elevation | 0dp |
| Navigation elevation | 2dp |
| Action-bar elevation | 3dp |
| Enabled content opacity | 1.0 |
| Disabled content opacity | 0.38 |
| Disabled action container opacity | 0.12 |
| Individual expansion | 160ms |
| Individual collapse | 130ms |
| Recent-edit highlight transition | 220ms |
| Bulk expansion/collapse | Immediate |

Shapes use the shared theme: extra-small 4dp, small 8dp, medium 12dp, large 16dp,
extra-large 24dp corner radii. Control icons are 16/20/24dp by role; inline progress
uses a 16dp slot, page/loading progress uses 40dp, and the ring stroke is 2dp.
The noninteractive patient ordinal uses 32dp; patient actions retain 48dp targets.
The reserved inline slot keeps a button label from shifting when its spinner appears.

Clinical urgency, overdue tasks and badge severity keep their existing semantic
palette. Accent colors, selected-state borders and disabled-state colors serve
interaction roles; a style sample does not redefine clinical meaning.

## Selectable style profiles

`UiStyleCatalog` defines the profiles; `LocalUiStyle` supplies the selected one to
shared components. Typography, 48dp targets, clinical meaning, dialog limits and
fold thresholds stay independent. The reduced-motion switch overrides the profile's
card/nav transitions to zero duration. Programmatic section/search jumps become
immediate; platform sheet/ripple behavior still follows Android settings.

| Profile | Arabic name | Density/list gap | Border normal/selected | Elevation navigation/action | Expand/collapse/highlight |
|---|---|---|---|---|---|
| `current` | الحالي | Current table above; list 8dp | 1/2dp | 2/3dp | 160/130/220ms |
| `relaxed` | أوسع | Compact: padding 6h/4v, gap 6dp; normal: 16h/12v, gap 12dp; list 12dp | 1/2dp | 2/3dp | 160/130/220ms |
| `quiet` | هادئ | Current density; list 8dp | 1/2dp | 0/0dp | 120/100/180ms |
| `clear` | أوضح | Current density; list 8dp | 1.5/2dp | 2/3dp | 160/130/220ms |

`h` and `v` mean horizontal and vertical padding. These are complete examples,
not promises that increasing every value improves usability. `current` references
the active semantic tokens so its values follow later intentional token edits.
The alternate profiles copy it and override only the values shown.

Example of applying local preferences to a component region:

```kotlin
ProvideUiPreferences(UiPreferences(profile = "relaxed", density = UiDensity.RELAXED)) {
    // Shared components read LocalUiStyle/LocalUiPreferences here.
}
```


## Current typography

| Role | Size / line height (sp) | Weight |
|---|---|---|
| Display large / medium / small | 40/48 · 34/42 · 30/38 | Bold |
| Headline large / medium / small | 28/34 · 26/32 · 24/30 | Bold |
| Title large / medium / small | 22/28 · 16/25 · 14/22 | Bold |
| Body large / medium / small | 16/25 · 15/24 · 14/22 | Normal |
| Label large | 14/22 | Bold |
| Label medium / small | 13/20 · 13/20 | Medium |

Shared app typography uses bundled Tajawal Regular/Bold from `res/font`, with
asynchronous Compose font loading and the existing bundled SIL Open Font License
in `assets/fonts/OFL.txt`. PDF export resolves the same resources during IO export.
No runtime font download is needed. Explicit monospace technical/lab fields keep
their existing font family; system/app font scaling remains. Small is now 0.92; the combined app/system
font scale is bounded to 0.92–2.0.
Rendered Arabic shaping and large-font wrapping require device verification.

Patient names use the shared name component with a bold role appropriate to their
context. Card names, the details top bar and the editor's saved-name context do not
need the same size. Labs use content-aware LTR text and a monospace font; other
clinical text is content-aware RTL. This preserves mixed Arabic/Latin text rather
than reversing the saved strings.

## UI wording resources

Active Android UI Arabic wording lives in `res/values/strings_ui.xml`; existing
six-form count resources remain in `strings.xml`. Reuse common action resources
and screen-prefixed names. Use numbered placeholders for interpolated values and
retain `uiNumber`/`uiPlural` at presentation call sites.

Capture `rememberUiStrings()` in composables for callbacks and presentation
helpers; UI state producers receive `UiStrings` through injection. Composable
default arguments may use `stringResource`. Use the `UiLabels.kt` bridges for
model/PDF enum labels in the UI; portable model codes and shared clinical/export
wording have separate ownership. About/credits and dummy clinical fixtures retain
their existing content. See [scope and verification limits](current-state.md#shared-android-presentation).

## Dialog and adaptive layout roles

Scrollable dialog bodies measure against their actual parent constraints. The
available-height fraction is 0.85, with these caps:

| Body role | Maximum |
|---|---|
| Compact list | 260dp |
| Standard | 420dp |
| Longer review | 440dp |
| Sheet | 640dp |

Explicit incoming minimums are respected. Scrolling and fixed actions remain owned
by the calling screen; the helper does not replace clinical review callbacks.
The full-screen patient editor retains its dedicated keyboard-safe action area.

| Layout role | Current threshold |
|---|---|
| Paired fields | 360dp, adjusted upward for larger font scale |
| Patient grid minimum card width | 360dp |
| Two-column patient content | 440dp |
| Maximum patient-form width | 840dp |
| Maximum bottom navigation width | 480dp |
| Navigation rail | 80dp |
| Toolbar action arrangement | 240dp, adjusted for font scale |
| Fold clearance | 8dp |
| Minimum fold-pane width / pane height | 360/360dp |
| Regular split width | 760dp |
| Minimum primary / supporting pane widths | 320/360dp |
| Medium support minimum width / height | 520/640dp |
| Medium support pane height | 240dp |

Equal numbers can serve different purposes. Keep those role names independent;
changing the grid width should not silently change field arrangement or fold rules.

## Shared component vocabulary

Selectable chips all use `AppFilterChip`: M3 compact visuals may grow for text,
with a minimum 48dp interaction layout. A compact visual does not imply a 32dp
row. `AppChoiceChips` owns repeated single-choice groups. `AppAlertDialog` merges
heading semantics into its title while callers retain body scrolling and guarded
actions. `AppSelectableSurface` adds a selected stroke and preserves caller roles;
`AppSectionHeader` also supports a decorative leading icon for disclosures.
`AppNotice` has neutral/accent/clinical tones and optional dismiss/content slots.
Empty-state icons use `UiSize.emptyStateIcon` rather than a progress-indicator role.

| Presentation | Shared implementation | Caller still owns |
|---|---|---|
| Dialog title/explanation and primary/cancel actions | `AppDialogHeader`, `AppDialogConfirmAction`, `AppDialogDismissAction` | Validation, selection, stale-edit review and dismissal rules |
| Patient name/admission/personal context | `PatientName`, `PatientIdentityHeading`, `PatientIdentityText` | Patient identity, selection and clinical mutations |
| Clinical section heading/body/empty value and text direction | `AppClinicalSection`, `ClinicalTextKind` | Which saved content is shown and whether a collapsed summary is truncated |
| Inline/page busy state | `AppActionContent`, `AppBusyIndicator`, `AppLoadingState` | Busy flags and operation lifecycle |
| Common action vocabulary | `UiAction`, `AppActionLabel` | Confirmation and permission checks |

| Action | Arabic label | Icon role |
|---|---|---|
| Edit | تحرير | Edit |
| Full record | الملف الكامل | Info |
| Restore | استعادة | History |
| Review | مراجعة | Visibility |
| Send report | إرسال تقرير | Send |
| Save | حفظ | Save |
| Cancel | إلغاء | Close |

A visible label makes its adjacent icon decorative. Operation-specific phrases
such as «نقل إلى السلة» or «جلب النسخة السابقة» remain explicit; a generic label
must not obscure what a confirmation will do. Specialized report/publication/
conflict reviews keep their detailed content and their own callback flow.

## Local interface controls

The expandable «تخصيص الواجهة» section contains:

- Current/relaxed/quiet/clear profile selection and compact/normal/relaxed density.
- Default/compact/standard/comfortable control visuals. Default keeps each control's
  established visual height; explicit options request 32/40/48dp minimums. Large
  text may grow them, and interaction targets always remain at least 48dp.
- Reduced motion, optional phone-dock auto-hide, compact Add and optional header
  expansion. Auto-hide/header expansion default off; compact labelled Add defaults
  on. Upward scrolling or either list boundary restores navigation. Touch
  exploration keeps the dock visible.
- Reset for these interface controls; appearance, accent, font and PDF choices
  retain their separate existing settings. Changes apply immediately, consistent
  with the rest of Appearance settings, and the dummy patient preview follows them.

`UiPreferences` is encoded under `SETTING_UI_PREFERENCES` in the encrypted local
settings table. Theme observation starts after the storage gate. Writes are
serialized, ordinary failures are shown and cancellation is rethrown. Demo keeps
its encoded value in temporary UI state. No database/interchange version changes
or migration work are involved.

Explicit Compact/Relaxed density overrides patient-card spacing; Normal preserves
the existing card-only compact setting. `ProvideUiDefaults` can locally override
size/density while retaining the selected profile. Shared containers, selection
rows, patient cards and list spacing consume the local roles; spacing is not a
text-size adjustment.

`AppIcons` retains the existing 60 filled/outlined symbols. `AppText` owns semantic
caption/supporting/body/heading/numeric/clinical/lab roles and explicit lab direction.
`AppMenu`/`AppMenuItem`, `AppSheet`, `AppSnackbarHost`, `AppListToolbar`,
`AppStateHost` and loading-size roles consolidate existing presentation while
callers own data, validation, retries, state, permissions and reviews.

## Verification limits

Source checks cover changed XML/Python syntax, Kotlin delimiter structure,
string/icon references, component call sites and local documentation links. Cached
Material3 API signatures were inspected without compilation. Git unstaged/staged whitespace checks
passed after the edits. The owner's `1.1.23.20261022` debug assembly succeeded
after the import/warning fixes, with no Kotlin warning reported. Further agent builds and
test-suite work require owner confirmation. Runtime preference persistence,
rendered previews, Arabic typography/wrapping,
TalkBack, header taps, navigation auto-hide and fold/large-font behavior have not
been verified on a device. No agent packaging, live Telegram, tests or migration work ran.

## Consolidated Android components

The selected UI pass uses ContainerColors/toneColors for notice/chip/metric pairs,
with patientBadgeColors retaining clinical severity. AppButtonStyle owns primary,
tonal, outlined, text and destructive variants; existing short button entry points
share that implementation. AppDivider, AppChip/AppFilterChip, AppMetricGrid,
AppIconButton and content-card helpers are the shared presentation primitives.
Informational badges are separate from selected filters; all selectable control sizes retain
48dp targets. Semantic gutters/card/control/section roles extend UiSpacing.

Tajawal Medium 500 is permanently bundled beside Regular 400/Bold 700. Upstream
has no SemiBold 600 face, so UiTypography.emphasizedWeight uses genuine Bold.
Source: [Google Fonts Tajawal](https://github.com/google/fonts/tree/main/ofl/tajawal),
SIL OFL retained at `android/app/src/main/assets/fonts/OFL.txt`.
Medium SHA-256: `1ff8bd943a261c9dd7906bdb1993cab87b6d925a7fb5f848cdb51ed301531090`.
No runtime font download or dependency/toolchain upgrade was added.

Ward controls keep count/group context and active-count options/reset in one row,
then scrolling presets and optional search/advanced summaries. Dashboard tiles use
two columns only when labels fit the available width/font scale. Actual rendering,
TalkBack, tooltip touch behavior and fold/large-font layouts require device review;
source or compiler checks do not establish those results.
