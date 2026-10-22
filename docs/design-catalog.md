# Android design catalog

Current values and spare style samples for the owner to review. Values below are
presentation settings: they do not change clinical records, report readiness,
permissions, synchronization or PDF appearance.

The current appearance remains active. The three spare profiles are **design
references only**; there is no advanced theme picker, profile persistence or
profile synchronization yet. A future picker is a proposal, not unfinished work
added to the roadmap.

## Find the source of a style

| Area | Source |
|---|---|
| Basic spacing | [UiSpacing.kt](../android/app/src/main/java/com/hos/rushdpatients/ui/theme/UiSpacing.kt) |
| Control/icon sizes | [UiSize.kt](../android/app/src/main/java/com/hos/rushdpatients/ui/theme/UiSize.kt) |
| General/field padding | [UiPadding.kt](../android/app/src/main/java/com/hos/rushdpatients/ui/theme/UiPadding.kt) |
| Card density, borders, elevation, opacity, motion, dialog/layout roles | [UiTokens.kt](../android/app/src/main/java/com/hos/rushdpatients/ui/theme/UiTokens.kt) |
| Typed current/spare profile samples | [UiStyleCatalog.kt](../android/app/src/main/java/com/hos/rushdpatients/ui/theme/UiStyleCatalog.kt) |
| Typography | [Type.kt](../android/app/src/main/java/com/hos/rushdpatients/ui/theme/Type.kt) |
| Theme surfaces, accent and shapes | [Theme.kt](../android/app/src/main/java/com/hos/rushdpatients/ui/theme/Theme.kt) |
| Clinical badge severity | [PatientBadgeColors.kt](../android/app/src/main/java/com/hos/rushdpatients/ui/theme/PatientBadgeColors.kt) |

`dp` describes layout size; `sp` describes text size and follows font scaling.
Motion durations are milliseconds. These are source defaults, not measurements
from a rendered device. Existing typography, accent and Light/Dark/AMOLED/System
settings remain separate choices.

## Current spacing and density

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

## Spare style profiles

These samples exist as typed values in `UiStyleCatalog`. They are not read by the
runtime theme. All four use the same typography, minimum targets, clinical colors,
dialog constraints and fold/layout thresholds unless a future change is explicitly
implemented and checked.

| Profile | Arabic name | Density/list gap | Border normal/selected | Elevation navigation/action | Expand/collapse/highlight |
|---|---|---|---|---|---|
| `current` | الحالي | Current table above; list 8dp | 1/2dp | 2/3dp | 160/130/220ms |
| `relaxed` | أوسع | Compact: padding 6h/4v, gap 6dp; normal: 16h/12v, gap 12dp; list 12dp | 1/2dp | 2/3dp | 160/130/220ms |
| `quiet` | هادئ | Current density; list 8dp | 1/2dp | 0/0dp | 120/100/180ms |
| `clear` | أوضح | Current density; list 8dp | 1.5/2dp | 2/3dp | 160/130/220ms |

`h` and `v` mean horizontal and vertical padding. These are complete examples,
not promises that increasing every value improves usability. `current` references
the active semantic tokens so its values follow later intentional token edits.
The spare profiles copy it and override only the values shown.

Example of inspecting a spare profile in future UI work:

```kotlin
val sample = UiStyleCatalog.relaxed
// sample.normalDensity.padding, sample.normalDensity.sectionGap, sample.listGap
// This lookup alone does not apply a theme or save a preference.
```

## Current typography

| Role | Size / line height (sp) | Weight |
|---|---|---|
| Display large / medium / small | 40/48 · 34/42 · 30/38 | Bold |
| Headline large / medium / small | 28/34 · 26/32 · 24/30 | Bold |
| Title large / medium / small | 22/28 · 16/25 · 14/22 | SemiBold |
| Body large / medium / small | 16/25 · 15/24 · 14/22 | Normal |
| Label large | 14/22 | SemiBold |
| Label medium / small | 13/20 · 12/18 | Medium |

Shared app typography uses bundled Tajawal Regular/Bold from `res/font`, with
asynchronous Compose font loading and the existing bundled SIL Open Font License
in `assets/fonts/OFL.txt`. PDF export resolves the same resources during IO export.
No runtime font download is needed. Explicit monospace technical/lab fields keep
their existing font family; system/app font scaling and the sizes above remain.
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

## Possible future local advanced style picker

A future settings entry could be «تخصيص الواجهة» with a preset row, a fabricated
patient preview, and optional advanced controls. Proposed behavior:

1. Start from the active appearance and show `current` plus the spare profiles.
2. Preview card density, spacing, neutral borders/elevation and individual motion.
3. Allow local Apply, Cancel and Reset-to-current actions. Cancel restores the
   previous preview; profile changes do not edit patient data.
4. Persist approved presentation choices on the current device only, independently
   of Telegram project data, credentials, join files and PDF options. Demo preview
   remains in memory. The storage format is deliberately not introduced here.
5. Reuse existing accent, appearance and font-scale choices rather than adding
   duplicate controls with different meanings.

Suggested advanced ranges for design review, not active validation rules:

| Proposed adjustable value | Candidate range |
|---|---|
| Card horizontal padding | 4–12dp |
| Card vertical padding | 2–8dp |
| Internal section/list gap | 4–12dp |
| Neutral border | 1–1.5dp |
| Selected border | 2dp, retained |
| Navigation/action elevation | 0–3dp |
| Individual expand/collapse | 0–220ms, including a reduced-motion choice |

The picker must preserve labelled 48dp minimum targets, RTL, readable font scaling,
clinical colors, fixed Save/Cancel, complete clinical content and report review.
Keep dialog/fold geometry and bulk expansion behavior governed by their existing
rules. No picker, new settings key, schema change or import/export format is
implemented by this catalog.

## Verification limits

Current values were compared against the source tokens, typography and shapes.
Shared-component extraction was checked at source level, including callback,
clinical-content and inactive-profile boundaries. The catalog and spare profiles
have not been visually evaluated on a device. No build or compilation, packaging,
clinical database change, live Telegram action or test-suite task was run.

## Consolidated Android components

The selected UI pass uses ContainerColors/toneColors for notice/chip/metric pairs,
with patientBadgeColors retaining clinical severity. AppButtonStyle owns primary,
tonal, outlined, text and destructive variants; existing short button entry points
share that implementation. AppDivider, AppChip/AppFilterChip, AppMetricGrid,
AppIconButton and content-card helpers are the shared presentation primitives.
Informational badges are separate from selected filters; compact controls still
have 48dp targets. Semantic gutters/card/control/section roles extend UiSpacing.

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
