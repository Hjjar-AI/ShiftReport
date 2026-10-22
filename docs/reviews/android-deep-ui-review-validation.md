# Validation of `android/chick.md`

Initial review against production source on 22 October 2026. This disposition
records the first correction pass; the owner subsequently selected additional
optional consolidation. Current behavior/build evidence is in [Current state](../current-state.md),
and all 43 choices are in the [decision register](../decisions/android-ui-options.md).
`android/chick.md` is now the maintained work guide rather than the old proposal list.
“Fixed” below means source implementation, not a successful build or device result.
Counts, file sizes and possible visual problems are not by themselves defects.
The review's P0 labels do not establish clinical-safety failures.

Paths below are relative to `android/app/src/main/java/com/hos/rushdpatients/`.
No toolchain/dependency/app/schema versions, migration files or test suites were
changed. No Gradle, compilation, packaging, device, profiler or Telegram run occurred.

## Behavior and safety

| ID | Current-source finding and disposition |
|---|---|
| A1 | Confirmed. `WardScreen` phone clicks opened editing. Fixed: ward and demo clicks open details; explicit Edit remains. |
| A2 | UI flow decisions were inline, but the claim that sending relies on UI guards is false. `ReportPreviewViewModel.prepareReviewedReport`, `send` and `sendSupervisorReports` already validate actor, current shift, report revisions and delivery; `ReportPdfWorkspace.checkedPdf` checks exact artifacts. Extracted the presentation decisions into pure `ReportFlowGate`, preserving branches and resource IDs. Authoritative checks remain in the ViewModel and delivery layer. No unit-test work was authorized. |
| A3 | Partly inaccurate: UNKNOWN already offered Received/Not received; only SENT offered Allow copy, and all decisions already had consequence text and confirmation. Kept that distinction; added the extra-copy explanation beside an outlined Allow copy action. No new sheet or delivery workflow. |
| A4 | Confirmed unused. Removed `LongPressLoadingButton`; retained the existing reviewed send confirmation. |
| A5 | Confirmed relocation of the same strip. Fixed: routine status remains in the list during syncing; a separate `WardDeliveryNotice` stays in the header for unavailable/uncertain delivery history. |
| A6 | Not four competing primary actions: `showAddFloatingAction` and `WardHeader.showAddAction` are mutually exclusive; empty wards use one inline Add. Workflow/fetch actions are text buttons. Retained the owner-selected Add FAB and advisory fetch reminder. |
| A7 | Confirmed: targets, edit/copy drafts and insertion baselines were composition-owned. Fixed with ViewModel-owned `WardEditorState`, `PatientFormState` and `EditPatientState`. Original patient/shift revisions, insertion snapshot, pending field text, nested task entry and stale-review choices survive Activity recreation. Demo uses the same memory-only holders. Dismissal/save-next creates or clears the appropriate session. No clinical draft was added to a Bundle or new persistent store. Process-death recovery of edit/copy sessions is not claimed. Also removed Add-form reinitialization on each autosaved draft update. |
| A8 | Confirmed ordinary clipboard copy. Fixed with a sensitive clip on API 24+, expiry/disposal cleanup restricted to the matching nonce, and an API 23 fallback for clearing. Android can deny clipboard access after focus is lost; cleanup is best effort. No clipboard background service or claim of guaranteed erasure. |
| A9 | Confirmed. `PasswordField` now forwards `KeyboardActions`; PIN login/unlock and PIN confirmation submit through their existing guarded handlers on Done. Next keeps the native focus behavior. PINs remain outside saved state. |
| A10 | Confirmed low-emphasis plain label. Fixed with a shared notice palette, status icon and wrapping label chip. Details/next action remain available. |
| A11 | The proposed removal conflicts with the owner's required timer/hold/tap behavior. Current source actually used three seconds despite the five-second rule. Corrected to five seconds; retained the saved monotonic deadline, counting while held, release dismissal and quick skip. |
| A12 | Confirmed missing system-bar appearance handling. Full-window theme owners now apply icon appearance and bar colors from the rendered scheme; embedded previews do not affect the window, and demo disposal restores its enclosing appearance. API 23–25 retains a dark navigation background because dark navigation icons are unavailable there. Device appearance remains unverified. |
| A13 | Confirmed light-only storage gate. It now uses system appearance and an explicit background Surface. Saved Light/Dark/AMOLED settings are encrypted repository data and remain unavailable until the gate verifies storage. Failure/loading must not construct repository-backed ViewModels early merely to obtain a theme; after success the existing saved app theme takes over. |

## State and architecture

| ID | Current-source finding and disposition |
|---|---|
| B1 | Large coordinators exist, but current source already delegates navigation, dialogs, grid, validation, submission, settings operations and PDF workspace responsibilities. This pass extracts the evidenced editor/filter/readiness ownership problems; line count alone does not justify another broad split. |
| B2 | Confirmed repeated filter ownership. Added one saveable `WardFilters`, including reset/preset rules, selected-state calculation and labels. The options dialog transforms the live holder snapshot, avoiding stale captured filter writes. |
| B3 | Targets now have one ViewModel-owned editor holder. Replacing every independent modal with a sealed route is optional; the reported recreation defect is covered by A7. |
| B4 | Data/preferences/operation status in one `WardUiState` is real, but not an evidenced correctness defect. Existing preference and operation coordinators already own their mutations. Removed the minute clock from that state; no speculative state-schema rewrite. |
| B5 | Snackbar/error state is deliberately retained until dismissed; errors and last-operation summaries are durable visible state rather than exclusively one-shot events. A Channel replacement could change delivery/recreation behavior. No evidenced loss to fix. |
| B6 | Confirmed minute updates to the large state and unconditional filter invalidation. Moved the clock to a separate flow; only overdue filtering uses its minute value, while new-admission filtering also keys on the current date. The screen still observes the clock to refresh task/freshness displays; elimination of all card recomposition is not claimed. |
| B7 | Confirmed duplicated predicates and overly broad task-filter type. Added domain `PatientPredicates`/`WardSummary` and UI `WardTaskFilter`. Dashboard, real ward and demo reuse the shared rules. |
| B8 | Confirmed needless task counting and repeated normalization. Filtering now takes an options snapshot and cached field search index, normalizes the query once, and checks task predicates only when selected. Per-field substring matching and pin order remain. Debouncing is unnecessary to fix that repeated work and would delay current live filtering. |
| B9 | Editable fields now belong to `PatientFormState`; validation/submission/demographic/clinical/task helpers remain shared. A further one-component-per-section rewrite is optional, not a demonstrated field-loss fix. |
| B10 | Inline settings/doctor dialogs exist. Their different review, import, error and privileged actions do not establish a bug merely by count. Existing section/operation owners remain. |
| B11 | Navigation callbacks and repeated admin route guards exist. Callbacks preserve existing focused destinations; UI guards supplement authorization below navigation. A callback object/helper is an optional API change. |
| B12 | A fake production repository is not needed: demo already shares real offline forms/cards/dashboard/navigation and deliberately avoids production settings/session/network dependencies. This pass also shares filtering and retained editor/task state. Kept its isolation instead of rewiring DI. |
| B13 | Opaque resource names are real; sharing identical strings across files is valid and was deliberate deduplication. Introduced semantic `ui_back` for active back controls. Did not mechanically rename 1,291 resources or prohibit shared vocabulary. Protected About wording remains. |
| B14 | Removed the silent default mapping from Edit to card click: callers now supply Edit explicitly. Removed the unused badge-edit callback and its duplicate accessibility action. Standard long-click fallback and optional view/copy controls remain; read-only/gallery callers explicitly supply inert Edit. A complete action/state object redesign is unnecessary. |

## Shared components

| ID | Current-source finding and disposition |
|---|---|
| C1 | All inspected expanded headers supply toggle behavior; static headers supply neither. Counts, completion and summary have independent valid uses. No observed impossible caller state; splitting the public API is optional. |
| C2 | Diagnosis labels, clinical badges and selected filter controls have different interaction/severity semantics. Their different sizes alone do not justify merging them. |
| C3 | Drawer groups, navigation destinations and selectable choices have different roles. Drawer submenu headers already reuse `CompactDrawerItem`; a universal list item is optional. |
| C4 | Added heading semantics to generic `EmptyState`. `EmptyWardPatients` additionally owns a viewport, source explanations, guarded Add/Fetch/Copy actions and read-only behavior; it is not a duplicate generic empty label. |
| C5 | Confirmed duplicate bordered destination surfaces. Reused `AppCard` for both empty-ward source rows. |
| C6 | Confirmed duplicate destination/icon definitions. Bar and rail now render one `WardDestination` definition and apply the same admin filter. Their different platform presentations remain. |
| C7 | Dialog/sheet counts do not prove inconsistency or lost safeguards. Specialized full-screen clinical forms, confirmations and scrolling choice sheets remain; no blanket overlay replacement. |
| C8 | Premise is partly wrong: `AppActionColors` has four enabled/disabled roles, not two. Clinical badge roles and notice container pairs are not interchangeable action colors. |
| C9 | `NoticeKind` already shares notice/metric tones; clinical severity stays separately mapped through `patientBadgeColors` as required. Renaming it Tone or collapsing palettes is optional. |
| C10 | Fixed filled/outlined button padding with `UiPadding.button`. Retained focused button functions. The body-level `LocalFieldEnabled` check must also gate explicitly enabled callers; the default alone cannot do that. |
| C11 | Different screens need different fixed actions, fold regions and scroll owners. Shared top bars/centered content already exist; no evidenced inset failure requiring a universal scaffold. |
| C12 | Density/card-height assessment needs rendering. Collapsed cards already omit routine/zero metadata; expanded content is preserved. No arbitrary clinical-summary truncation. |
| C13 | Confirmed all three dead helpers: removed `LongPressLoadingButton`, `WardToolbarContent`, `wardPatientFilterSummary`. Production callers were checked first. |
| C14 | `DoctorCard` already uses `AppCard`; admin actions have their own action/review content and setup instructions vary by mode. Splash cards are protected branding. No further merge follows from their names. |

Removed the exact `SetupSection` pass-through. Retained `SectionTitle` (consistent
form spacing/completion semantics) and `DetailTextSection` (repeated card, clinical
formatting and empty-content behavior) rather than expanding their call sites.

## Tokens, theme and layout

| ID | Current-source finding and disposition |
|---|---|
| D1–D2 | Splash/About brand values and credits are protected. Filename components, dummy fixtures and inactive catalog wording were explicitly excluded by the existing resource-extraction scope. No blanket brand/color/string codemod. |
| D3 | The 6dp compact spacing step is deliberate, not a correctness violation; no repository rule requires a strict 4dp grid. Kept existing compact controls. |
| D4 | List/card/chrome spacing differs by purpose. No rendered gutter defect was supplied; a universal gutter change is optional. |
| D5 | Small section/notice padding is true; calling it unusable without rendering is not established and globally increasing it conflicts with the compact interface preference. Button padding is addressed separately. |
| D6 | Only Regular/Bold are bundled; Medium/SemiBold requests use font matching/synthesis. This establishes fewer source faces, not a demonstrated shaping/readability defect. No extra font download or changed typography assets. |
| D7 | Missing explicit font-padding/line-height-style overrides is not itself a defect. Current Arabic line heights are explicit; changing font metrics without rendering could affect diacritics/wrapping. |
| D8 | Local emphasized names/counts/selected states legitimately override weight; counting overrides does not identify a broken type role. |
| D9 | Confirmed scheme reconstruction. Color schemes are remembered by preset/appearance/dynamic-color/context inputs; shape/typography owners remain shared. |
| D10 | Equal values for clinical urgency and errors do not erase their different meanings. Severity mappings are deliberately independent of accent/dynamic Material colors. No evidenced missing content-color use to repair. |
| D11 | The cited low-contrast neutral borders separate noninteractive surfaces. Interactive fields have the stronger outline. A proposed 1.9:1 border is not a 3:1 interactive-boundary fix; no decorative-border darkening based solely on these ratios. |
| D12 | Centralized list fraction, keyboard/accessibility adjustment step and rail threshold in `UiLayout`; remaining purpose-specific pane thresholds stay independent. |
| D13 | Compact preference is specifically for patient cards, not a promised app-wide density mode. No global density redesign. |
| D14 | Instant expansion is valid; not every section requires motion. Existing standard animations remain; no speculative shared expansion controller. |
| D15 | Full-screen form dividers now explicitly use the shared outline-variant color. No helper needed merely to wrap the same call. |
| D16 | Confirmed emoji and ASCII date insertion. Display markers now use `uiNumber` without an emoji; marker position, saved clinical text and exports remain unchanged. |
| D17 | Platform base theme exists, but replacing it with a dependency-backed theme is not necessary for the reported bar defect. A12 handles system colors/appearance directly; splash brand background remains. |
| E1 | Confirmed known-fold early return could unmount content. Removed it: content remains mounted while the local origin arrives, then uses existing fold-safe regions. Retained fold support, clipping, keyboard/accessibility divider controls and 48dp target. A larger adaptive rewrite/divider redesign is optional. Fold rendering remains unverified. |
| E2 | The fetch reminder was explicitly requested, and delivery warnings must remain visible. A5 removes relocation without hiding safety notices. Replacing the controls with one icon row is a redesign, not an evidenced correctness fix. |
| E3 | Existing dashboard already leads with urgency/overdue items and has an all-clear message. Fixed repeated aggregate work with remembered shared `WardSummary`; KPI tiles are optional visual redesign. |
| E4 | 48dp filter minimums meet the owner's target requirement. Visual chip sizing needs device inspection; did not shrink targets based on a static inference. |
| E5 | Existing full-screen form already has fixed inset-safe actions, discard and stale-edit review. A7 fixes recreation ownership; a navigation route is not required to preserve those behaviors. |
| E6 | Removing focused drawer shortcuts reverses the accepted navigation design. Drawer and Settings deliberately offer multiple paths to the same guarded handlers. |
| E7 | Optional freshness detail avoids routine clutter as requested; no forced inline expansion. |
| E8 | Custom PIN keypad is explicitly optional and not an evidenced bug. Kept native numeric password input; fixed Done in A9. |
| E9 | Confirmed unkeyed header. Added `ward-header` key/content type; kept return-index item counts unchanged. |

## Accessibility, performance and platform

| ID | Current-source finding and disposition |
|---|---|
| F1 | Multiple RTL boundaries are intentional: Activity, Compose root and native dialog window need first-layout RTL. Removing them would conflict with the owner rule; retained them. |
| F2 | Back arrows already rotate correctly by layout direction, and destination chevrons already switch correctly. The reminder now uses CloudDownload for Fetch. A send symbol is not a back/forward navigation arrow; no unsupported AutoMirrored API or toolchain upgrade. |
| F3 | Icon actions already have Arabic content descriptions. Visual tooltips are optional enhancements, not missing accessible names. |
| F4 | TalkBack verbosity needs a device result. Removed the duplicate badge-edit action; kept task counts, explicit state and useful real actions instead of deleting accessibility operations by count. |
| F5 | Inspected null descriptions belong to labelled buttons/rows/tabs, status decorations or the divider with parent semantics. No standalone unlabeled action was evidenced. |
| F6 | Active Back controls use semantic `ui_back`; About's protected literal is retained. |
| F7 | The scale cap/multiplier is existing policy. 2× text/device wrapping remains unverified, not a source defect established by the report. |
| F8 | Absence of a custom reduced-motion check does not prove standard Compose animation primitives ignore duration settings. No independent animation clock was identified to replace; rendered system-scale behavior remains unverified. |
| F9 | Confirmed IME observation could request visibility every animation frame. Added a short cancellation-aware settle delay before the existing request. No input/IME work moved into composition. |
| G1 | Annotation count cannot establish actual skipping/performance without compiler/profiler evidence. Do not mark `Patient`'s arbitrary List inputs immutable by assertion. The observable `WardFilters` holder is correctly marked Stable; no compiler-report build or toolchain change. |
| G2 | Confirmed repeated rounds work/object membership. Remembered filtered rounds and use visible/pinned ID sets, including return-to-patient group calculations. |
| G3 | Retained saveable lists as serialization/ordering owners; cached sets now serve card expansion, pin order and collapsed-group lookups. Mutation-time list updates remain intentional. |
| G4 | Addressed with B6, without claiming zero per-minute recomposition. |
| G5 | Addressed with remembered shared dashboard summary. |
| G6 | Addressed with remembered color schemes and field fill interpolation. |
| G7 | Addressed with cached field search indexes on ward/demo. |
| H1 | Version upgrade is outside authorization and unnecessary for the implemented fixes. Existing APIs remain. |
| H2 | The custom splash and hold/skip behavior are explicitly required; platform-splash replacement/returning-user bypass is not adopted. |
| H3 | Separate splash, storage-bootstrap and saved app themes respect different availability boundaries. A13 replaces the light-only bootstrap fallback without opening encrypted clinical repositories early. |
| H4 | Internal class/package names are not a user-facing branding defect. No application/namespace renaming. |
| H5 | Arabic label helper names describe the current Arabic UI correctly. Renaming them is optional churn. |

The testing/catalog/preview proposals in section 11 were not treated as an explicit
test request. Existing RTL/dark/2× previews and the design catalog remain; no test
suite was inspected or modified, and no preview/rendering result is claimed.
Fully qualified calls are valid Kotlin, not an implementation failure. Sections
12–14 repeat optional API/architecture proposals; they do not create additional
requirements or override the owner's scope constraints. Appendix measurements
were historical static observations, not runtime evidence or acceptance criteria.

## Verification and limits

Production-source checks covered changed/new Kotlin delimiters, duplicate imports,
string/plural references, XML parsing, all six PatientCard callers, removed-helper
references and extracted preview/send decision equivalence. Manual source review
covered editor reset/retention, original revision ownership, live filter transforms,
clock/date invalidation, API 23 clipboard/navigation fallbacks and retained
confirmation/backend guards. Git whitespace checks passed.

These checks cannot establish Kotlin/resource compilation, actual Activity/fold
recreation, keyboard behavior, accessibility, rendered colors/large-text wrapping,
clipboard cleanup after focus loss, live delivery or performance. Edit/copy drafts
are retained for Activity recreation only; process death still ends those memory
sessions. The storage gate uses system appearance until encrypted settings become
available. No verification-only roadmap items were created.
