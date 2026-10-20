# Completed work

Feature summaries only. [Current implementation and verification](current-state.md)
contains technical detail; [Roadmap](roadmap.md) contains unfinished work.

## Project access and protection

- Added a permanent-superadmin reviewed empty patient/shift seed on Android and
  desktop, retaining doctors/accounts, connection/key and Telegram history. Local
  stale-state checks preserve racing edits; audit/delivery records remain.
  Scope and runtime limits are in [current state](current-state.md#permanent-superadmin-empty-start).

- Hospital-independent Demo/Join/Create, protected `.srjoin.json`, optional
  Telegram AES-GCM encryption and deliberate readable-PDF exception.
- Verified join staging, direct Android 10+ Downloads saving and shared/picker
  alternatives; encrypted backup/doctor exports and verified patient CSV writes.
- Same-project reconnect before login, live-admin token replacement, preserved
  data key/local state and blocked cross-project/key/topic changes.
- Live-role Settings gating hides project administration and supervisor
  destinations from non-admins and clears credential dialogs on access changes.
- Optional desktop raw `.env` credentials with explicit encryption validation,
  Telegram admin verification before local access and protocol-level role checks.
- Same-device PIN re-entry after sign-out, persistent attempt limits, optional
  biometric locking and below-UI live admin authorization.

- Desktop first-admin creation for new Telegram groups, with group-admin/code
  verification, explicit review, persistent creation receipts and verified registry/
  index publication. Added live-admin protected join export for Android/desktop.
  Fake-transport and Linux dialog probes passed; live/Windows limits are recorded
  in current state.

## Clinical data and reports

- Added explicitly confirmed local-copy patient publication on Android/desktop,
  including supervisor reports, to replace competing publications without applying
  downloaded patients locally. Desktop reviews same-day shift-ID conflicts and
  opens review from Publish; Android binds sending to a CSV publication review.
  Authorization, local stale checks and delivery guards remain. Source-check and
  runtime limits are in [current state](current-state.md#explicit-local-copy-publication).

- Organized the [Android demo](android-demo.md) around shared Patients/Dashboard
  navigation, a secondary-tools drawer, real dashboard/options components and
  separate report/team/preferences screens. Added memory-only actor selection,
  temporary font/card options and confirmed reset/exit; retained real forms,
  task/revision handling and labelled local PDF export. Source checks passed;
  no build/device verification ran. Desktop demo is unchanged by this work.

- Reorganized Android ward controls, patient-card hierarchy, display options,
  report setup with basic scope/destination/format before Advanced PDF styling,
  and rounds save-and-next discovery. Separated preferences, Data Management,
  Project Management and collapsed Diagnostics into dedicated routes; unified
  filters/grouping/card layout/saved sorting in View Options while retaining
  revision review and admin gates. Added wrapping current-value headers, a real-card
  dummy appearance preview (with the Material API opt-in required by the owner's
  compile log), fixed patient/section orientation, explicit local-save
  feedback and restored View Options/filter/group state; loading preserves card
  expansion and publication status stays visible beside delivery warnings. Patient
  cards now use a thin, softly accent-tinted border in real/demo/preview views,
  retaining stronger selection/highlight emphasis. Source/whitespace checks only. Desktop now has a slimmer daily-action sidebar, separate settings/tools,
  compact toolbar/cards, report scope selection inside report setup and reusable
  patient widgets with filter/reset scroll preservation. Desktop now separates
  preferences, project tools and collapsed diagnostics; unifies View Options with
  inline guarded sorting and local card density; adds a dummy-card appearance
  preview, fixed patient/section headings, brief local-save feedback and session
  retention of dialog sizes, detail/list scroll and selected options. Consolidated data/recovery
  tools, standardized common desktop dialog footers, distinguished clinical card
  expansion from the full patient record, and added persistent actionable uncertainty
  notices with brief routine-success feedback. Android polish adds slightly roomier
  neutral cards, shorter form/action labels, one filter summary/reset and explicit
  routine confirmation actions. Shared semantic tokens now cover density, adaptive
  dialog caps, borders/elevation, disabled opacity, motion and layout thresholds;
  see [token details](current-state.md#android-semantic-ui-tokens). Unified dialog
  headers/actions, patient identity, clinical sections, action vocabulary and stable
  loading slots. Added a [design catalog](design-catalog.md) with typed spare style
  profiles; a future local picker remains a proposal. Review and authorization paths remain; [implementation and verification limits](current-state.md#ward-interface-and-desktop-workflow-organization).

- Modernized Android/desktop PDFs with bundled Tajawal Regular/Bold, centered bold
  headers, darker configurable horizontal separators and neutral summary bands. Assignment
  overviews use centered equal-width columns, tinted subheaders and filler rows
  for level bottoms. A5 cards have 1.5 cm margins, a thin frame, two-column patient/
  admission details, section dividers and dated page-count footers. Regenerated
  dummy samples; desktop rendering/content/link checks and Android source-only
  limits are recorded in [current state](current-state.md#modern-pdf-presentation).
  PDF settings now offer thickness, contrast, row shading, separator previews and
  Balanced/Clear/Print presets; see [preferences](current-state.md#pdf-appearance-preferences).

- Compacted Classic PDF headers, changed card index returns to full-page fit, and
  corrected assignment tables to right-side Arabic numbering and RTL team flow,
  with thin vertical gutters separating supervisor tables.
  Removed hospital/ward names from every PDF and the shift date from assignment
  PDFs. Separated roster titles from small metadata, simplified index/assignment
  headings and shortened repeated explanatory text. Table/assignment creation
  timestamps appear once below the final content with a 30-point gap (about 1 cm)
  and matching reserved pagination room.
  Cards retain enforced A5
  portrait. Samples and verification limits are in
  [current state](current-state.md#compact-classic-header-page-fit-card-index-and-rtl-assignment-layout).

- Simplified patient-entry insertion options and made report controls follow the
  selected format/layout. Preserved hidden selections and corrected forced-PDF
  output while retaining the user's ordinary format preference. Details and checks
  are in [current state](current-state.md#patient-entry-and-report-option-presentation).

- Consolidated ward controls across Android and desktop: clearer action groups,
  compact secondary view options, visible active filters and complete filter reset.
  Desktop cards group secondary actions under More; Android drawer shares view
  options and consistent patient presets. Preserved roster access and report review.
  Details and verification limits are in
  [current state](current-state.md#ward-layout-and-view-control-consolidation).

- Added positioned patient insertion, guarded local undo-download and the Excel
  sort preset on both clients. Added adaptive table striping, roster-aware PDF
  headings; the former Excel PDF style is merged into compact Classic. Desktop also normalizes typed
  admission dates and includes companion totals in text reports. Evidence and
  limits are in [current state](current-state.md#selected-workbook-workflow-and-report-features).

- Fixed desktop stale sort/snapshot saves, verified unfinished-form ownership and
  protected shift rollover with an explicitly saved independent encrypted archive
  of unpublished work. Evidence and runtime limits are in
  [current state](current-state.md#desktop-stale-saves-draft-identity-and-rollover-protection).

- Added desktop handover dashboard actions, Android-aligned report completeness
  notes and a shift-doctor picker at the start of report setup. Roster edits retain
  stale-draft protection and report preparation reloads the saved selection.
  Evidence and limits are in [current state](current-state.md#desktop-handover-and-report-setup-alignment).

- Added desktop read-only patient rounds and scoped admin activity. Unified Android
  Settings/recovery publication behind snapshot review and removed the force bypass.
  Details and verification limits are in [current state](current-state.md#patient-rounds-and-reviewed-recovery-publication).

- Aligned desktop pending-download protection, Save and next, General/Advanced
  clinical fields, appearance controls and Android-compatible `.rpb` recovery.
  Preserved task attribution and device delivery receipts. Evidence and limits are
  in [current state](current-state.md#download-editor-appearance-and-portable-backup-alignment).

- Consolidated desktop single/batch delivery safeguards and shared task validation,
  counts, deadlines, display order, rollover and task presentation across editors,
  patient views, ward lists and reports. Ward task actions retain project/shift/lock
  checks. Dummy transport/clinical checks
  and runtime limits are in [current state](current-state.md#shared-desktop-report-delivery-and-task-rules).

- Added both-platform ward status with recorded download/publication/delivery state,
  individual ward task lists and reviewed batch team management; retained clinical
  save, admin and stale-edit protections. Desktop manually confirmed arrival now
  permits an explicitly approved extra copy. Local/graphical/dummy-workflow checks
  and device limits are in [current state](current-state.md#workflow-status-ward-tasks-and-batch-teams).
  The owner's Android debug build passed after the doctor-conflict-dialog call fix.

- Consolidated Android ward synchronization metadata and next-action selection.
  Removed unused remote field-merge dialogs, state and callbacks; local stale-edit
  review remains. Failed downloads no longer invent unpublished edits. Details and
  source-only verification limits are in [current state](current-state.md#android-synchronization-consolidation).

- Brought desktop closer to Android: reviewed multi-supervisor PDF delivery with
  individual receipts, encrypted unfinished patient-form recovery, combined ward
  filters and Arabic search, action identifiers, and admin announcement management.
  Android now compares each shift with its locally known snapshot separately from
  the device-wide pending flag. Evidence and remaining runtime limits are in
  [current state](current-state.md#cross-platform-workflow-parity).

- Aligned desktop task completion with Android's local-save attribution and retained
  it through publication/recovery. Added admin local clinical history, persistent
  encrypted report preferences, inline patient details, expand/collapse all and
  view-only pinning. Evidence and runtime limits are in
  [current state](current-state.md#local-clinical-attribution-activity-and-desktop-presentation).

- Preserved desktop local patient-editor metadata during publication and refreshed
  time-sensitive ward/task displays every minute. Android announcements now use
  literal-text review/confirmation and durable uncertainty tracking; both clients
  preserve project encryption and safely repair confirmed announcement references.
  Evidence and Android source-only limits are in
  [current state](current-state.md#patient-editor-clock-refresh-and-reviewed-announcements).

- Persisted Android report-delivery uncertainty across restarts, with sender/admin
  resolution and explicit permission for an extra copy. PDF sending now uses the
  exact reviewed file, checked against current content/options and its hash.
  Supervisor identities stay distinct in shared destinations. Desktop blocks
  unresolved delivery across format/publication changes and checks live sender/admin
  permission before manual resolution. Historical assignment PDFs on both clients
  identify current team membership. Checks and runtime limits are in [current state](current-state.md#durable-android-deliveries-and-exact-reviewed-pdfs).

- Removed Android automatic synchronization and its controls/scheduler; both
  clients retrieve/publish complete snapshots with last-successful-write-wins.
  Removed remote field merges and automatic publication-race retries, retaining
  local stale-edit checks, authorization, explicit review and recovery.
- Added admin-only A4 portrait names-only team PDFs on Android and desktop: a separate
  table per supervisor, a comma-separated resident row and patient-name rows.
  Three 56.7 mm tables per row use continuous left-to-right patient numbering;
  overflow continues on A4 pages without dropping names.
  Authorization is checked below the UI during generation and before export.
- Full-screen RTL patient drafts, General/Advanced labs, compact paired inputs,
  validation shortcuts, explicit discard and optional validated Save and next.
- Structured optional-owner tasks, completion identity/time, per-patient/ward
  counts, overdue subset, rollover and consistent backup/sync/conflict/report output.
- Transactional mutation/audit/pending state, expected revisions, reviewed stale
  drafts, full-snapshot patient/doctor transfer, recovery and publication journals.
- Reviewed main/supervisor delivery, unlimited active-doctor roster selection,
  shared sorting/order, Classic tables and linked portrait A5 cards with full content.
- Correct CSV metadata quoting for multi-doctor rosters, sort JSON and snapshot IDs,
  with clinical columns/decoder/schema versions preserved.

## Interface

- Corrected the missing shared-button import for the ward-task filter action
  reported by the owner’s build; source checks passed, rebuild pending.

- Defaulted fresh settings/demo to Light/Turquoise with white surfaces and eight
  more distinct named palettes. Reordered Android report preview and daily/data/
  project drawer groups, grouped desktop sidebar actions, and moved ward tasks
  into filter controls. Doctor import now gives persistent errors and visible
  progress instead of only a transient failure snackbar. The supplied CSV is
  structurally valid; its original device-side failure remains under investigation.
  Source checks and limits are in [current state](current-state.md#report-navigation-and-doctor-import-feedback-refresh).

- Shared fields/actions/choices/search/notices/indexes/metrics/loading/review
  components, consistent busy guards, accessible Retry and Arabic formatting.
- Distinct palettes, independent appearance, contrasting input fills and native
  label-cutout painting without rectangular patches.
- Compact top bars/floating bottom navigation with transparent gutters; organized
  drawer, admin-only Activity, adaptive/fold-safe panes and patient rounds.
- Compact card headers, full expanded content, immediate bulk expansion, retained
  filters and saved-patient return/highlight.
- Animated grouped splash credits with 120dp emblem and automatic version now
  use a five-second countdown that continues while held: hold keeps the splash
  open, release dismisses once expired, and a quick tap skips. The saved deadline
  survives recreation. Existing logo/About preserved. Setup
  defaults to Demo, with centered prominent Demo/Join/Create cards, focused forms,
  optional help and a fixed primary action. Source/timer checks passed; device
  behavior and Android compilation remain unverified.

## Team and workflow consolidation

- Separated report publication/delivery outcomes and next actions, preserving
  confirmed results through bookkeeping failures and identifying uncertain or
  partial delivery. Disabled desktop manual moves in grouped/filtered/search
  views, with a handler guard. Renamed Android sync states as publication states.
  Checks and limits are in [current state](current-state.md#explicit-report-outcomes-and-movement-restrictions).

- Made cross-team patient cover explicit without changing permanent membership;
  aligned desktop reviewed sending with publish-before-delivery, preserving
  cancellation and UNKNOWN retry guards; and exposed shift-copy source dates and
  recorded download/fetch freshness. Checks/limits are in
  [current state](current-state.md#cover-reviewed-sending-and-source-freshness).

- Added independent one-supervisor resident membership across Android/desktop
  editing, registry synchronization, doctor CSV and assignments PDFs, including
  residents without patients and empty supervisor teams. Reused existing Android
  storage and guarded references/identity remapping.
- Replaced individual rollover choices with whole-shift copying, separate from
  retrieval, preserving unfinished tasks and stale/empty-target protections.
  Clarified active sorting/grouping, desktop save/publish/report navigation and
  CSV added/changed/unchanged/retained previews. Checks and runtime limits are in
  [current state](current-state.md#team-membership-and-workflow-consolidation).

## Standalone desktop

- Added ward grouping and saved multi-level sorting; clinician-bound local PINs
  with persistent throttling and automatic/manual screen locking; patient CSV
  export and live-admin reviewed Android-format doctor CSV import/export; and a
  separate in-memory demo with labelled local PDFs. Preserved local drafts and
  Telegram publication authorization. Checks/limits are in
  [current state](current-state.md#desktop-grouping-local-access-csv-and-demo).

- Added encrypted local patient recycle/restore with original task completion
  history; reviewed text/PDF reports, Android-style Classic tables/linked A5 cards,
  five palettes/dark mode, paper/ward settings and local per-supervisor files; and
  live-admin reviewed doctor/role/destination management. Preserved patient drafts,
  pointer checks, permanent-admin/reference guards and UNKNOWN delivery receipts.
  Source/local/fake-transport/PDF checks and remaining runtime limits are recorded
  in [current state](current-state.md).
- Replaced the active Excel workflow with source in `desktop/`: CustomTkinter
  patient/task/badge/roster editing, search/counts/order, encrypted offline drafts,
  protected-file reconnect and reviewed current-format Android/Telegram publication.
- Added Office-free paginated Arabic PDFs, preview/confirmed delivery, local export,
  durable UNKNOWN receipts and ledger-preserving encrypted draft restore. Kept
  existing protocol/encryption/schema versions and clinical safety checks.
- Added Python 3.8.10 source launch/setup, optional `.env` preferences and isolated
  Windows 7 dependency pins. Source/offline/PDF and x86/x64 wheel checks passed;
  target runtime/live Telegram remain unverified. Owner will bundle separately.
- Added Linux launch/setup, XDG workspace paths, passphrase-protected local keys,
  process locking, default Arabic font discovery and PDF opening while retaining
  encrypted SQLite, Windows 7 pins and explicit report confirmation. Linux offline
  storage/locking/opener/PDF checks passed; graphical/live-project behavior remains
  unverified. See [current state](current-state.md).
- Corrected Linux Arabic labels/choices/reviews and form/search inputs. Editors
  render shaped Arabic in-place with mapped caret/selection, logical Unicode
  clipboard/getters/Save, wrapping, undo/redo and preserved numeric validation.
  Dummy GUI editing/Save probes and screenshots passed; full live workflows remain
  unverified. Passwords/native mode retain native widgets.
- Linux setup resolves unpinned binary dependencies in its own environment and
  accepts current system Python. Actual setup, GUI/input, encrypted-draft,
  protocol and Arabic PDF probes passed; Windows compatibility requirements stay
  separate. See current state for verification scope.
- Old workbook/builder/extractor/packaging work is superseded; original artifacts
  remain preserved. Protected `tools/` and old Windows guides could not be removed/
  rewritten due filesystem permissions. See [current state](current-state.md).

## Repository organization

- Guarded automatic shared Gradle-cache selection against inaccessible
  version-specific daemon directories, with personal-cache fallback. Read-only
  permission evidence, shell checks and runtime limits are in the
  [workstation build guide](android-shared-workstation-build-fixes.md#verification-and-remaining-limits).

- The POSIX Gradle wrapper reuses the writable workstation cache under
  `/opt/android-shared/.gradle` when the cache variable is unset or set to the
  normal home cache; custom locations retain precedence. Gradle stays at 8.7;
  shell syntax and cache-selection checks passed without running Gradle. Project
  caches default to a per-account home directory, retaining explicit overrides so
  both accounts can use the ordinary wrapper command. The launcher also routes
  generated resource/Kotlin/APK output to an account-local build root, avoiding
  shared `app/build` ownership failures. Explicit output-root overrides and IDE
  defaults remain available; source/shell checks passed without a build. Documented
  the encountered download/ownership failures and fixes in the
  [shared-workstation build guide](android-shared-workstation-build-fixes.md). Assemble tasks now export
  project/version/variant-named APK copies into the current user's Desktop/ShiftReport
  folder, with XDG Desktop discovery in the POSIX launcher and an explicit export
  override. Source/path probes passed; Gradle export execution remains unverified. The guide
  includes a reusable preference checklist, project-name/path adaptation table,
  cross-PC notes and a request template for future projects. Replaced the APK
  rename callback with a regex rule after the owner's cache-serialization failure.
  Added explicit owner-requested patch/version-code reservation for POSIX debug/
  release packaging, with a locked atomic project counter and documented gaps,
  overrides and launcher/PC scope. The date suffix is regenerated from each build's
  start date in Asia/Damascus, rather than preserving the old date. Source/date/
  concurrency probes passed; no build ran. The consolidated guide now includes
  daily debug/release/both commands, Desktop/original APK paths, complete overrides,
  build-date rules, encountered source/warning messages and cross-PC reuse steps.

- Grouped the complete Android project/settings/caches under `android/`, retained
  the PC client under `desktop/`, and moved private Excel artifacts to ignored
  `local/excel/`. Shared Git/docs/tooling stay at root.
- Updated entry documentation, private-file ignore patterns and portable IDE
  mappings. Verified 274 source/resource/Gradle hashes and private artifact moves;
  no builds or test-suite/migration review. Protected historical review links retain
  old `app/` paths; current source is under `android/app/`.

## Documentation

- Centralized guides/reference/reviews under `docs/`, normalized kebab-case names,
  consolidated repeated status/proposals, separated Android/Windows diagnostics,
  and updated links, agent rules and package documentation paths.
- Private Git reference moved to ignored `docs/local/`. README build commands retained.
  Verification limitations remain in Current state rather than roadmap-only checks.

- Consolidated configuration guidance into `.env.example`; removed the extra raw
  template and Android README. Removed obsolete Excel tools/guides, duplicate
  protocol notes and completed-review docs; retained unresolved findings.

## Packaging documentation

- Added the [Windows EXE packaging guide](windows-exe-packaging.md), covering
  isolated setup, folder output, bundled resources, single-file caveats,
  distribution, updates and troubleshooting. Commands are documented but have
  not been executed on Windows; no application artifact was produced.
