# Completed work

Feature summaries replace repeated chronological implementation notes. Current
behavior and verification limits belong in [Current state](current-state.md);
unfinished work belongs in [Roadmap](roadmap.md). Operational detail remains in
the linked guides. Source completion does not imply a device/runtime result.

## Project access and safety

- Protected file-only join, optional authenticated Telegram-data encryption,
  validated same-project reconnect before login, preserved clinical/key/topic
  state and blocked cross-project substitution.
- Clinician PIN/biometric access, throttling, below-navigation admin authorization,
  permanent-admin/reference guards, storage-key preservation gate and secure windows.
- Protected production signing inputs without debug fallback, private reviewed-PDF
  cleanup and sensitive login-code clipboard handling. Owner signing credentials
  and runtime limits remain documented.

## Clinical workflow and shared data

- Full-screen RTL patient drafts, clinical-first editing/demographic-first creation,
  labs/per-field tools, explicit discard, stale review and optional Save-next.
- Structured optional-owner tasks with repository-stamped completion identity/time,
  pending/overdue counts, unfinished-task rollover and consistent backup/sync/report paths.
- Transactional revision/audit/pending state, patient editor attribution, recycle/
  restore, shared stored sorting, whole-shift copying and reviewed recovery.
- Manual complete-snapshot patient/doctor retrieval/publication with accepted
  last-successful-write-wins scope, shared synchronization locks and known-snapshot
  comparison. No automatic synchronization or remote field merging.
- Independent resident/supervisor membership, explicit cross-team cover, portable
  identity/reference validation, CSV metadata quoting and reviewed registry changes.

## Reports and exports

- Reviewed publication/delivery with durable UNKNOWN/duplicate guards and separate
  outcome reporting. Current opened PDF files are validated by content/options/hash.
- Unlimited active-doctor roster, general/supervisor destinations and exact batch
  review. Android retains Doctors → Destination → Preview → Send with final confirmation.
- Compact Classic tables, linked A5 cards, contents/navigation, advanced appearance,
  complete clinical/task content and admin-only A4 names-only team tables.
- Explicit local exports/portable recovery remain distinct from project publication.

## Android interface

- Owner debug assembly passed for `1.1.23.20261022` (code 24) in 37 seconds
  after the patient-header import and unused drawer-variable fixes, with no Kotlin
  warning reported. The Gradle deprecation notice remains nonfatal; device/test
  behavior remains unverified.

- Implemented the owner's selected follow-up: local style profiles, app-wide
  density/control sizes, reduced motion, optional scroll-hidden navigation and
  header expansion, and a compact labelled Add action. Small labels are 13sp and
  Small scaling is 0.92. Centralized icons, semantic text, transient UI wrappers,
  list toolbars and state/loading presentation. Demo choices remain temporary;
  clinical colors/data, permissions, report review and schema versions stay intact.
  Owner debug assembly passed; test/device behavior remains unverified.

- Consolidation follow-up shares compact chip sizing, dialog title headings,
  selected-surface strokes, disclosure headers and accent/validation notices.
  Ward controls/cards and report progress use less repeated chrome; doctor
  registry actions share a disclosure while pending warnings remain visible.
  Added numeric spacing aliases and semantic style tokens without changing
  toolchains, clinical data or safety gates. The subsequent selected typography
  changes are recorded above. Further agent builds/tests need confirmation.

- Focused Settings/drawer workspaces, compact dock/rail, admin-only Activity, adaptive
  fold-safe panes, patient rounds, retained filters and saved-patient return/highlight.
- Validated all 88 supplied UI findings; corrected evidenced card-tap, editor
  recreation/draft reset, PIN Done, system-bar, clipboard, IME and repeated-filter work.
- Owner-selected consolidation adds exclusive dialog/overlay state, explicit card
  state/actions, grouped navigation/admin presentation guards, shared chips/colors/
  buttons/dividers/cards and semantic compact spacing. Ward tools are shorter;
  dashboard tiles adapt to narrow panes and large text. Arabic tooltips share action labels.
- Real ward screens now serve the injected in-memory demo repository; local demo
  PDFs remain per-page labelled, with no clinical database or synchronization dependency.
- Bundled genuine Tajawal Medium alongside Regular/Bold and shared emphasized roles.
  Preserved logo/About, Arabic resources, clinical severity, 48dp targets and the
  five-second hold/skip/recreation splash behavior.
- Earlier offline Kotlin/resource/KSP compilation also passed for its source
  snapshot. Current build evidence and runtime limitations are recorded in
  [Current state](current-state.md#verification-and-limits).
- Pending optional decisions are preserved in the [decision register](decisions/android-ui-options.md).

## Desktop client

- Shared desktop presentation tokens and button styles replace repeated shared
  control recipes while retaining minimum targets, platform fonts and existing
  appearance settings. Changed Python source parses successfully; runtime UI
  verification is not claimed.

- Standalone CustomTkinter workflow replaces the old Excel-based operational path;
  encrypted drafts, reviewed Android-compatible publication/reconnect and durable receipts.
- Patient/tasks/team/admin tools, grouping/sorting, recycle/audit, PIN locking,
  reviewed CSV, Arabic PDF formats and local demo/export support.
- Linux launch/setup, XDG storage/locking, passphrase protection, font/PDF discovery
  and logical-Unicode Arabic editing. Separate Windows compatibility pins and
  [EXE packaging instructions](windows-exe-packaging.md) remain.

## Repository and documentation

- Android lives under `android/`, desktop under `desktop/`, shared guides under
  `docs/`, and ignored private Excel/local configuration artifacts outside shared docs.
- Shared-workstation build fixes reuse durable caches, separate account-generated
  output, retain protected signing, and export versioned artifacts for ordinary owner
  packaging. Verification builds disable automatic version reservation.
- Consolidated four overlapping Android reviews and temporary selection notes
  into one review. Retained three source-evidenced data-boundary concerns, removed
  stale questionnaires/unsuitable proposals and updated links and the work guide.
  Current behavior/history, optional decisions and planned work retain separate homes.
