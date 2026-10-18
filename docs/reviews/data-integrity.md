# App consistency and data-integrity review

Reviewed 2026-10-13. Historical source findings, without device reproduction.
The first three data-boundary findings have no recorded fix in the work log;
this documentation cleanup did not re-review or implement them. Recheck current
source before treating them as an active implementation plan.

## Accepted policy and scope

The app uses retrieve/edit/publish with **last successful write wins** at the Telegram pinned-state boundary. This is the accepted policy, not a defect. This review does not propose an atomic multi-writer database, stronger remote concurrency guarantees, or a separate backend. See [Architecture](../architecture.md#accepted-synchronization-scope).

Local expected-revision checks and explicit stale-draft review are existing safeguards within that policy. Findings about these checks concern their consistency on the device, not preservation of competing remote publications. Explicitly restoring a backup may replace existing data; replacement itself is not a finding.

## Findings

1. **High — backup restoration can reuse revisions and fail to invalidate stale local drafts.**
   - `EncryptedBackupManager.restore` writes patient revisions from the backup and changes the existing shift's roster/sort without advancing its local revision. If an outstanding local draft has a matching revision, its later save can pass the expected-revision check despite changed content.
   - Preserve intentional backup replacement while ensuring restored content invalidates outstanding local drafts, for example by advancing local revisions for replaced records and changed shift settings.
   - Source: [EncryptedBackupManager.kt](../../android/app/src/main/java/com/hos/rushdpatients/domain/backup/EncryptedBackupManager.kt), [PatientRepository.kt](../../android/app/src/main/java/com/hos/rushdpatients/data/repository/PatientRepository.kt), [ShiftRepository.kt](../../android/app/src/main/java/com/hos/rushdpatients/data/repository/ShiftRepository.kt).

2. **High — patient mutation paths do not consistently validate live clinician references at commit.**
   - Ordinary saves validate resident/supervisor roles against ViewModel state before the transaction; `WardMutationRepository` rechecks task owners but not those clinician assignments. Recycle-bin restoration and rollover also bypass this live reference validation.
   - A doctor deleted or reclassified before saving, or while a patient is deleted, can become an invalid reference when the patient is saved/restored.
   - Reuse live transactional reference validation across add, update, restore, and rollover, retaining optional task ownership and expected-revision checks.
   - Source: [WardViewModel.kt](../../android/app/src/main/java/com/hos/rushdpatients/ui/ward/WardViewModel.kt), [WardMutationRepository.kt](../../android/app/src/main/java/com/hos/rushdpatients/data/repository/WardMutationRepository.kt).

3. **Medium — backup reference validation differs from editor and synchronization rules.**
   - Restore checks clinician existence but not resident/supervisor roles, and reads the doctor registry before its commit transaction. It can therefore accept assignments rejected by ordinary editing or synchronization, or use a registry that changes before commit.
   - Reuse the same role/reference rules and recheck them inside the restore transaction. This complements finding 2.
   - Source: [EncryptedBackupManager.kt](../../android/app/src/main/java/com/hos/rushdpatients/domain/backup/EncryptedBackupManager.kt), [SyncService.kt](../../android/app/src/main/java/com/hos/rushdpatients/sync/SyncService.kt), [WardViewModel.kt](../../android/app/src/main/java/com/hos/rushdpatients/ui/ward/WardViewModel.kt).

## Subsequent UI consolidation

The original Retry accessibility and duplicated long-press styling findings were
addressed by the later UI consolidation. See [the UI record](../current-state.md)
and [current implementation](../current-state.md#visual-design-system).

## Evidence and scope

The original review inspected production source only. It did not run builds,
macros, device workflows or live Telegram operations, or inspect test suites or
migration files. Documentation cleanup is not verification of the original findings.
