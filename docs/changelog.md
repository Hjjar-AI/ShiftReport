# Completed work

Feature summaries only. [Current implementation and verification](current-state.md)
contains technical detail; [Roadmap](roadmap.md) contains unfinished work.

## Project access and protection

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

## Clinical data and reports

- Full-screen RTL patient drafts, General/Advanced labs, compact paired inputs,
  validation shortcuts, explicit discard and optional validated Save and next.
- Structured optional-owner tasks, completion identity/time, per-patient/ward
  counts, overdue subset, rollover and consistent backup/sync/conflict/report output.
- Transactional mutation/audit/pending state, expected revisions, reviewed stale
  drafts, patient/doctor three-way merge, recovery and publication journals.
- Reviewed main/supervisor delivery, unlimited active-doctor roster selection,
  shared sorting/order, Classic tables and linked portrait A5 cards with full content.
- Correct CSV metadata quoting for multi-doctor rosters, sort JSON and snapshot IDs,
  with clinical columns/decoder/schema versions preserved.

## Interface

- Shared fields/actions/choices/search/notices/indexes/metrics/loading/review
  components, consistent busy guards, accessible Retry and Arabic formatting.
- Distinct palettes, independent appearance, contrasting input fills and native
  label-cutout painting without rectangular patches.
- Compact top bars/floating bottom navigation with transparent gutters; organized
  drawer, admin-only Activity, adaptive/fold-safe panes and patient rounds.
- Compact card headers, full expanded content, immediate bulk expansion, retained
  filters and saved-patient return/highlight.
- Animated grouped splash credits with 120dp emblem, automatic version and
  dismissible ten-second deadline; existing logo/About preserved.

## Standalone desktop

- Replaced the active Excel workflow with source in `desktop/`: CustomTkinter
  patient/task/badge/roster editing, search/counts/order, encrypted offline drafts,
  protected-file reconnect and reviewed current-format Android/Telegram publication.
- Added Office-free paginated Arabic PDFs, preview/confirmed delivery, local export,
  durable UNKNOWN receipts and ledger-preserving encrypted draft restore. Kept
  existing protocol/encryption/schema versions and Android administration boundaries.
- Added Python 3.8.10 source launch/setup, optional `.env` preferences and isolated
  Windows 7 dependency pins. Source/offline/PDF and x86/x64 wheel checks passed;
  target runtime/live Telegram remain unverified. Owner will bundle separately.
- Added Linux launch/setup, XDG workspace paths, passphrase-protected local keys,
  process locking, default Arabic font discovery and PDF opening while retaining
  encrypted SQLite, Windows 7 pins and explicit report confirmation. Linux offline
  storage/locking/opener/PDF checks passed; graphical/live-project behavior remains
  unverified. See [current state](current-state.md).
- Corrected Linux Arabic labels/choices/status/reviews with presentation-only
  shaping and line-aware bidi layout; kept logical values and added form readbacks
  without transforming editable clinical text. Actual dummy GUI/screenshots and
  selection/copy checks passed. Native bidi cursor editing remains a Tk limitation.
- Old workbook/builder/extractor/packaging work is superseded; original artifacts
  remain preserved. Protected `tools/` and old Windows guides could not be removed/
  rewritten due filesystem permissions. See [current state](current-state.md).

## Repository organization

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
