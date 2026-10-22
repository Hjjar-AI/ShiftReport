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
- Offline Kotlin/resource/KSP compilation passed; the owner's subsequent
  `assembleDebug` also succeeded for `1.1.19.20261022` (code 20) on 22 October.
  Device rendering/interaction remains unverified; see
  [build evidence and limits](current-state.md#verification-and-limits).
- Pending optional decisions are preserved in the [decision register](decisions/android-ui-options.md).

## Desktop client

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
- Consolidated current-state/history records, preserved operational guides and
  explicit verification limits, improved the documentation index and retained all
  unanswered UI choices separately from the active roadmap.
