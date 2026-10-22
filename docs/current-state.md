# Current implementation

Current behavior and verification limits live here. [Roadmap](roadmap.md) contains
unfinished implementation, [Changelog](changelog.md) summarizes completed features,
and [Architecture](architecture.md) explains design choices. Older chronological
implementation narratives have been consolidated; guides retain operational detail.

## Product and source layout

ShiftReport is an Arabic, offline-first clinical handover application with an
Android Compose client and a standalone Python desktop client. Telegram stores
manually published project snapshots and delivers reviewed reports; it is not an
atomic multi-writer database. Retrieve → edit locally → review → publish remains
the accepted workflow, with last-successful-write-wins publication.

| Area | Location / ownership |
|---|---|
| Android project | `android/`; Java 17 bytecode, Kotlin 1.9, Compose BOM 2023.10.01, SDK 34/minimum 23. Existing toolchain versions are unchanged. |
| Android UI | `ui/` below `android/app/src/main/java/com/hos/rushdpatients/`; Hilt ViewModels own lifecycle and operations. |
| Rules | `domain/`; shared report readiness and explicit task rules. |
| Local data | Room/SQLCipher behind repositories; expected revisions and transactional mutation/audit/pending state. |
| Telegram | `network/telegram/`; explicit synchronization under `sync/`. |
| Desktop | `desktop/`; CustomTkinter, encrypted local workspace and shared Android-compatible protocol. |
| Guides | [Documentation index](README.md), [Telegram setup](guides/telegram-setup.md), [build guide](android-shared-workstation-build-fixes.md), [desktop guide](../desktop/README.md). |
| Private material | Ignored `docs/local/`, signing/`*.local.properties`, and `local/excel/`; no credentials in source or clinical exports. |

## Security and project access

- Project joining is file-only through password-protected `.srjoin.json` files.
  Creation owns manual group/topic configuration. Runtime project configuration
  stores credentials; login never seeds a registry.
- Optional Telegram-data encryption is chosen at project creation. Authenticated
  encryption, the shared data key in protected device settings/join files and
  explicit decryption failures remain. Exported PDFs are deliberately readable.
- Same-project reconnect validates candidate credentials before replacing a live
  bot token. Existing patient data, pending edits, topics and encryption key stay;
  cross-project/key/topic substitution is blocked. Reconnect remains available
  before login. BotFather revokes tokens; a local save does not.
- Privileged mutations check live admin authorization below navigation. Permanent
  administrator/reference protections apply to doctor/role/destination changes.
- Existing database/WAL/SHM/journal files without a usable stored key cannot cause
  silent key creation, database deletion or unsent-record replacement. The storage
  gate resolves encrypted storage on IO before repository-backed ViewModels.
  Failure offers preservation/retry. Demo does not open the clinical database.
- `FLAG_SECURE` protects app windows. External PDF viewers and deliberate exports
  are outside that window's protection. Private reviewed PDFs are cleaned on
  replacement/invalidation/session end; cleanup is best effort.
- Numeric clinician-bound PINs, throttling and optional biometric unlock remain.
  PINs/passphrases do not enter saved UI state or logs. Login clipboard codes use
  sensitive metadata where supported; expiry/disposal clears only the matching
  nonce, best effort when Android denies clipboard access after focus loss.
- Release signing uses protected local/CI inputs with no debug-key fallback. The
  owner must supply the production key/certificate before distribution.

## Synchronization and publication

Patients/doctors transfer as complete snapshots. There is no periodic automatic
synchronization, remote field merge, required collaboration backend or automatic
publication-race retry. Local saves retain audit/editor identity, revisions and
pending state. Retrieval/replacement, rollover and stale edits remain reviewed.

`SyncService` owns patient retrieval/publication and their shared mutex.
`DoctorSyncCoordinator` owns doctor transfer. Patient/doctor pinned references
share the singleton `RemoteSyncStateStore` mutex. Publication preparation tokens,
recovery journals and exact review artifacts have one owner; extracted helpers do
not create independent locks, sessions or review copies.

Known snapshot equality is separate from the device-wide pending flag. Publication
validates published doctor identities/assignments while allowing unrelated pending
registry edits. Device-only/unchanged doctor saves do not create false dirty state.
Confirmed local-copy publication follows the accepted last-write-wins scope and
never bypasses stale local edits, identity checks or uncertain delivery.

Last fetch/publication timestamps describe known events, not current remote
freshness. Prior downloaded snapshots and undo-fetch remain separate from whole-
shift copy. Recovery publication and permanent-superadmin empty start retain
explicit review and their own authorization/preservation boundaries.

See [Architecture](architecture.md), [Telegram protocol](../desktop/PROTOCOL.md)
and [Troubleshooting](troubleshooting.md) for rationale, formats and recovery steps.

## Patients and tasks

Patient clinical fields, labs, badges, optional responsible doctors and structured
tasks share model/persistence/synchronization/backup/conflict/text/PDF paths. Task
counts are never inferred from follow-up text. Overdue is a subset of pending;
ownership is optional and new tasks start unassigned. Save stamps task completion
identity/time, and whole-shift copy carries unfinished tasks forward.

Expected patient/shift revisions guard stale saves, roster changes, sorting,
insertion and destructive operations. Explicit stale review can retain a draft or
use saved data. Sorting updates stored patient positions and agrees across formats;
grouping, filtering and pinning are view choices. Recycle/restore retains clinical
content and completion history.

Clinical-first editing and demographic-first creation use the same full-screen
RTL form, fixed keyboard-safe actions, discard review and optional Save-next.
Per-field tools share one draft, including unfinished treatment/follow-up/lab text
and nested task description/deadline/owner choices. Optional ownership does not
block saving or report readiness.

`WardEditorState` holds one sealed Add/Edit/Copy/Delete dialog and its original
revision/draft. A separate exclusive overlay state owns secondary sheets/reviews.
These memory sessions survive Activity recreation; edit/copy process-death recovery
is not claimed. Add-form autosave callbacks no longer reinitialize typed values.

## Startup and navigation

The five-second monotonic splash deadline continues while held; release dismisses
when expired, quick tap skips, and Activity recreation retains the deadline while
clearing the physical hold. The white-field emblem and About/credits remain.

Storage verification uses system appearance until encrypted saved appearance
preferences are readable. The normal app then uses the saved System/Light/Dark/
AMOLED appearance, independent accent and font multiplier. Full-window theme owners
set system-bar appearance; embedded previews do not change it. API 23–25 keeps a
dark navigation background because dark navigation icons are unavailable.

Patients/Dashboard are ordinary destinations; Activity/admin controls remain admin-
only across drawer, dock, rail and supporting panes. `WardNavigation` groups explicit
callbacks; `AdminRoute` shares presentation guards without replacing service-level
checks. Focused Settings workspaces and quick drawer shortcuts use the same handlers.
The Add FAB remains separate from reviewed «إرسال تقرير» publication/delivery.

Phone/pane taps open patient details. Edit/Copy/Delete/Pin/Expand use explicit
`PatientCardActions`; display flags and clinical values live in `PatientCardState`.
Rounds follow the filtered/grouped/pinned order; Save-next and return/highlight retain
revision checks and explain filter-hidden results.

Fold-aware panes keep content mounted while obtaining their origin, then use safe
regions. Compact layouts use full-screen details/support sheets. Native and Compose
RTL boundaries, labelled 48dp controls and growing/wrapping Arabic text remain.

## Shared Android presentation

- `ContainerColors` and tone helpers share notice/chip/metric containers while
  `patientBadgeColors` preserves clinical severity independently of accent.
  Shared button styles include primary, tonal, outlined, text and destructive;
  all respect form-local disabled state. `AppDivider` uses one neutral role.
- `AppChip` is informational; `AppFilterChip` retains selected/click semantics and
  48dp targets. Empty-state icon/action slots, destination/clinical/action cards
  replace duplicate presentation; specialized clinical content remains.
- Semantic gutter/card/control/section spacing extends the existing compact scale.
  Shared theme shapes and Arabic typography remain. Tajawal Regular 400, genuine
  Medium 500 and Bold 700 are bundled; upstream has no 600/SemiBold face. Shared
  emphasis uses Bold. License/provenance are in the [design catalog](design-catalog.md).
- Icon actions share Arabic TalkBack/long-press tooltip labels via `AppIconButton`.
  Back/forward/send mirroring is centralized; protected About content is unchanged.
- Ward list controls combine count/group context, options with active-count badge,
  reset and horizontally scrolling presets. Search and full advanced filters stay
  available. Requested fetch reminders and persistent delivery warnings remain.
- Dashboard totals use actionable two-column metric tiles; narrow panes/large fonts
  stack them. Urgent/overdue attention and all-clear state remain explicit.
- `WardFilters` is the saveable filter owner. Domain predicates/summary, cached
  per-field search indexes and ID sets serve real/demo views. A separate minute
  clock refreshes tasks/freshness; overdue filtering uses its minute value, and
  new admissions also use the current date. No zero-recomposition claim is made.
- Focused input waits briefly for IME inset changes to settle before requesting
  visibility. Display-only clinical date markers use Arabic digits without an emoji;
  saved clinical text and export representations remain unchanged.

## Reports and exports

The Android flow remains Doctors → Destination → Preview → Send. Unlimited active-
doctor roster selection is available first and checks revisions. Preview opening
is required for the current text or every selected PDF before final confirmation.
`ReportFlowGate` owns presentation decisions; the ViewModel/delivery layer remains
authoritative for actor/date/revision/destination checks, publish-before-delivery,
uncertain/duplicate delivery and exact reviewed PDF content/options/hash.

Delivery receipts survive restarts. UNKNOWN offers Received/Not received; SENT
alone offers explicit permission for an extra copy, with adjacent consequence text
and confirmation. Publication and delivery outcomes are shown separately, including
partial/uncertain delivery. No stage transition or retry silently publishes/sends.

Reports retain all saved clinical/task content and shared patient order. PDF output
omits routine/unassigned task labels and patient last-edit metadata. Classic compact
tables, linked A5 cards, contents/navigation and advanced appearance options remain.
Admin-only names-only team PDFs use A4 portrait supervisor tables and continuous
numbering. Local exports are distinct from reviewed Telegram publication/delivery.

## Isolated demo

`DemoWardViewModel` receives `InMemoryDemoWardRepository` through Hilt. The same
`WardWorkspace`, patient grid, dashboard, options, details and forms render through
`WardController`. Production delegates to its existing ViewModel; demo uses memory-
only clinical/settings state and exposes no live fetch/publication/Activity controls.
No production clinical repository, session or Telegram service is injected into demo.

Temporary team/actor/appearance selection, recycle/restore and reset/exit remain.
The demo uses hot-beverage patients and tree-named doctors. Explicit local PDF
export uses the real renderer and labels every page as demo. Reset/exit discards
the session; process death starts a new demo.

## Standalone desktop

The Python client retains protected-file join/reconnect, encrypted SQLite drafts,
reviewed complete-snapshot publication and durable uncertain-delivery receipts.
It uses the same clinical/task/doctor/team/sort/backup/report rules and shared
protocol versions as Android. Team membership, optional cross-team cover and
historical assignment output preserve identity/reference checks.

The UI offers grouping/search/order, inline details, expand/collapse/pinning,
patient/task editing, local audit/recycle/restore, reviewed doctor CSV import/export,
admin roles/destinations and independently displayed publication/delivery results.
Reports include Arabic paginated PDFs, Classic/linked cards, names-only team views,
local exports and reviewed supervisor batches with exact opened artifacts.

Linux uses XDG paths, process locking, passphrase-protected keys, font discovery
and native PDF opening. Arabic entry preserves logical Unicode, caret/selection,
clipboard, wrapping and Save while shaping display. Windows keeps its separate
compatibility dependency pins and key protection. See the [desktop guide](../desktop/README.md),
[desktop troubleshooting](../desktop/TROUBLESHOOTING.md) and
[Windows packaging guide](windows-exe-packaging.md). This Android pass changes no desktop code.

## Verification and limits

The selected UI pass uses the installed Gradle 8.7 distribution, existing Android
SDK and durable personal dependency cache, in offline mode. Temporary generated
build output/logs are separate from source; automatic version reservation is
explicitly disabled. A shared-cache daemon ownership failure was avoided by using
the existing personal cache. No toolchain/dependency download was needed for builds.

Final selected-source verification: `compileDebugKotlin` **passed offline** on
22 October 2026, including Android resource processing and KSP. The final run took
46 seconds, reusing configuration/dependency caches; no source warnings were
reported. Earlier compile findings (filter-chip argument order, the PDF-selection
local and a missing UiStrings import) were corrected before the successful run.
Full local log: `/tmp/shiftreport-ui-verification/compile.log`. This agent-run
compile did not produce an APK.

Owner-supplied 22 October 2026 log confirms `./gradlew assembleDebug` succeeded
for `1.1.19.20261022` (code 20) in 2m 14s: 44 actionable tasks, 18 executed,
5 from cache and 21 up-to-date. This establishes successful debug assembly for
that build snapshot; the APK was not independently inspected or run by the agent.
Configuration-cache recalculation followed changed Gradle properties, and the
cache was stored successfully. The Gradle deprecation warning was nonfatal; no
toolchain upgrade or further build was performed in response.

Earlier successful builds apply only to their own snapshots. Source checks cover
resource/XML references, changed Kotlin structure/call sites, retained revision/
confirmation guards, font metadata/provenance, Markdown links and Git whitespace.

No test-suite or migration inspection/change, agent-run APK packaging/distribution, device/
rendered-preview, TalkBack/fold/large-font, performance-profile or live Telegram
verification is claimed. Clipboard cleanup after focus loss and memory-session
loss after process death remain explicit limits. The original device-side doctor
CSV import failure still lacks its original exception, and production signing
still requires the owner's protected key.

Optional proposals and unanswered preferences remain in the
[decision register](decisions/android-ui-options.md); they are not verification-only
roadmap tasks. Original findings are retained in the compact
[review disposition](reviews/android-deep-ui-review-validation.md).
