# Consolidated Android review

Consolidated on 22 October 2026 from the UI reliability/security review, the deep
UI review, the consolidation/polish review and the earlier data-integrity notes.
Superseded finding-by-finding ledgers and temporary selection notes were removed.
This is a disposition summary, not a second implementation roadmap.

[Current state](../current-state.md) owns current behavior and verification;
[Changelog](../changelog.md) owns completed work; [Roadmap](../roadmap.md) owns
planned unfinished implementation; [UI decisions](../decisions/android-ui-options.md)
own remaining unselected proposals. Component values live in the
[Design catalog](../design-catalog.md).

## Completed review outcomes

| Area | Retained outcome |
|---|---|
| Storage and login | Removed login seeding; project creation remains its separate guarded operation. Storage access is checked on IO before repository-backed ViewModels. Existing database/key failures preserve files and do not silently generate replacement keys. PIN Done actions use guarded handlers. Clipboard nonces have sensitive metadata and matching-nonce cleanup. |
| Security and release | App windows use `FLAG_SECURE`. Private reviewed PDF cleanup retains exact-artifact delivery checks; exported/opened copies remain outside that cleanup boundary. Release signing has no debug fallback; production credentials remain the owner's prerequisite. |
| Drafts and mutations | ViewModel-owned editor/form sessions retain drafts and original revisions across Activity recreation. Add autosave no longer reinitializes the draft. Clinical actions keep expected-revision and stale-review handling. Process-death recovery of edit/copy sessions is not claimed. |
| Clinical/report workflow | Card-body taps open details; clinical actions remain explicit. Reports retain roster-first staged review, current preview opening, final confirmation and below-UI authorization/exact-file/delivery checks. Uncertain deliveries remain visibly actionable. |
| Startup and adaptive layout | The splash uses the required five-second monotonic deadline, hold/release and tap-to-skip behavior. Full-window themes own system-bar appearance. Storage bootstrap uses system appearance until encrypted preferences are available. Fold-aware content remains mounted while its origin is measured. |
| Components and accessibility | Shared chips, dialog headings, selection strokes, disclosures, notices, icon/text registries, menus/sheets/snackbar hosts, list toolbars and state/loading presentation replace repeated recipes. Arabic/RTL, 48dp interaction targets, meaningful action labels and clinical badge severity remain. |
| Presentation choices selected later | Local profiles, app-wide density/control sizes, reduced motion, optional dock auto-hide/header expansion and compact labelled Add are implemented. Touch exploration keeps the dock visible. Small labels are 13/20sp; Small scaling and the combined lower bound are 0.92, with the existing 2.0 upper bound. Demo preferences stay temporary. |
| Filtering and repeated work | One filter holder and shared patient/task/report rules serve real/demo views. Field search indexes, ID sets, task counts and grouped/rounds calculations are cached. Minute/date invalidation is scoped without a claim of zero recomposition. |
| Typography and branding | Genuine bundled Tajawal Regular/Medium/Bold serve shared roles; no fabricated SemiBold face is required. About, credits and the emblem are preserved. Saved clinical text and export representations are not rewritten by display formatting. |

## Retained source findings

The following three findings from the 13 October data-integrity review still have
source evidence on 22 October. This cleanup did not fix them or reproduce a device
failure, and does not authorize a new implementation project. They are retained so
real data-boundary concerns are not lost with the obsolete review ledgers.

1. **Backup replacement can reuse local revisions.**
   [EncryptedBackupManager.restore](../../android/app/src/main/java/com/hos/rushdpatients/domain/backup/EncryptedBackupManager.kt)
   upserts backed-up patient revisions and copies roster/sort settings onto an
   existing shift without explicitly advancing its revision.
   [PatientRepository.upsertAll](../../android/app/src/main/java/com/hos/rushdpatients/data/repository/PatientRepository.kt)
   and [ShiftRepository.upsert](../../android/app/src/main/java/com/hos/rushdpatients/data/repository/ShiftRepository.kt)
   stamp update time but retain those revisions. A stale draft whose revision
   still matches can therefore pass a later expected-revision check. Intentional
   restore replacement is accepted; failure to invalidate stale drafts is the concern.
2. **Live clinician-role validation is incomplete at patient commit boundaries.**
   [WardSaveGuard](../../android/app/src/main/java/com/hos/rushdpatients/ui/ward/WardSaveGuard.kt)
   checks assignments against UI-held doctors.
   [WardMutationRepository](../../android/app/src/main/java/com/hos/rushdpatients/data/repository/WardMutationRepository.kt)
   validates task owners during ordinary saves, but does not recheck responsible
   resident/supervisor roles there; recycle restoration and rollover also lack
   those live assignment checks. Existing revision checks do not validate clinician roles.
3. **Backup clinician validation uses existence checks before the transaction.**
   [EncryptedBackupManager.restore](../../android/app/src/main/java/com/hos/rushdpatients/domain/backup/EncryptedBackupManager.kt)
   reads doctors and checks referenced IDs before its restore transaction, without
   resident/supervisor-role checks or a transactional registry recheck. This differs
   from the editor's assignment rules and can use a registry that changes before commit.

The doctor CSV device failure and production-signing prerequisite already have
homes in [Roadmap](../roadmap.md); they are not duplicated as review tasks.

## Removed or superseded recommendations

- Accepted Telegram last-successful-write-wins publication and readable report
  PDFs are product boundaries, not defects. A required backend, atomic publication
  claims, password-format rewrites or automatic data/key replacement are unsuitable.
- Three-second splash descriptions, the separate ordinal square, inactive-profile
  statements, pending status for now-selected controls and blanket build permission
  were stale. Current source and owner instructions supersede them.
- File/line/icon counts, duplicated summary sections, estimated dp savings and
  hypothetical all-card recomposition are not evidence of runtime defects. Compact
  chip visuals still require at least 48dp interaction layout; no measured height
  saving or performance improvement is claimed.
- Equal typography sizes, different valid accessibility roles and different screen
  scroll/action owners do not require a universal scaffold or model rewrite.
  Unsupported version/package/theme changes, custom PIN keypads, removed drawer
  shortcuts, hidden safety notices and protected branding changes are not pending work.
- Completed feature checklists now live in the implementation/history documents.
  Remaining optional proposals are confined to the decision register; removing
  unsuitable proposals here does not imply they were implemented.

## Verification boundary

The owner supplied a successful `assembleDebug` result for `1.1.23.20261022`
(code 24), including the new UI implementation and both the patient-header import
and unused drawer-variable fixes. It completed in 37 seconds with no Kotlin warning
reported; the Gradle deprecation notice was nonfatal. See
[Current state](../current-state.md#verification-and-limits) for evidence.

This documentation pass checked the three retained findings against their named
production sources, local document links and Git whitespace. It ran no build,
test suite, migration, device, rendered preview, profiler or live Telegram action.
Compilation does not establish runtime preference persistence, Arabic wrapping,
TalkBack/fold behavior, clipboard cleanup or delivery behavior. Builds and all
specified test-suite work still require confirmation under [AGENTS.md](../../AGENTS.md).
