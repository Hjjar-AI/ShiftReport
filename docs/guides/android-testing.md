# Android test coverage

[Docs](../README.md) · [Build/cache setup](android-build.md) · [Current verification](../status/current-state.md)

The suite has 47 JVM test classes (227 methods) and 11 device test classes
(52 methods). These are source counts, not passed-test counts or measured code
coverage. New cases exercise current production APIs; no production behavior,
versions or database/interchange schemas were changed for this expansion.

## Coverage map

| Area | JVM coverage | Device coverage |
|---|---|---|
| Clinical records | Required fields, invalid dates, optional assignments, predicates and dashboard totals | Patient DAO ordering, shift isolation, recycle visibility and foreign keys |
| Tasks | Ward search/filter intersections and stable pin ordering; counts, exact overdue boundary, ordering, team filters, validation, rollover, metadata and JSON | Repository-stamped completion actor/time, attribution preservation/reopening, rejected anonymous completion/unknown owners and audit/pending rollback |
| Doctors | Name rules, team membership/cover/aliases, CSV parsing, duplicate identities and import preview retention | Existing doctor DAO identity, rank and soft-deletion cases |
| Persistence | Patient/doctor/shift mapper round trips including tasks, badges, revisions and publication lineage | Stale patient edits/deletes/restores, nonempty rollover rejection, shift revision guards, settings and sync channel isolation, duplicate-add/cross-shift-delete rejection, stale roster/sort rollback and whole-shift rollover guards |
| Security/onboarding | PIN format/enrollment, lockout persistence/expiry, live identity recheck and cancellation; role-use-case delegation; live admin registry checks; same-project reconnect, topic/key protection, stale actor/config guards and password wiping | Project AES-GCM round trips, tampering/wrong keys, fresh nonces, readable-PDF exception and salted password hashes |
| Backup | — | Encrypted export, password wiping, wrong-password/tampering rejection without clinical mutation, content restore |
| Synchronization | Recovery rotation by message ID, publication-source invalidation, pinned-index disappearance/confirmation, stale-pointer merge and bounded journal; patient equality distinctions, known-snapshot state, current CSV, multi-shift bundles, registry codec and unlimited roster round trips | Snapshot soft deletion scoped to the selected shift |
| Report safety | Readiness, reviewed-content fingerprints, changed clinical/tasks/roster/destination rejection, UNKNOWN receipts, repeat permission, actor/status guards | Explicit enabled confirmation and cancellation in an RTL dialog |
| Telegram | Markdown, request/envelope handling, encrypted-message parse-mode omission, topic routing, retry/backoff, ambiguous send and cancellation behavior | — |
| Presentation/export | Editor draft retention/submission/validation, save/error/cancellation handling, text report content and long rosters, PDF clinical content retention, compact task labels, settings visibility, rounds/list return, report text/PDF review gates | Confirmation button enabled state |
| Demo/time/sorting | Valid synthetic demo records, independent in-memory state, 08:30 rollover/leap-day deadlines, sort rules and serialization | — |

## Continued regression expansion

The latest additions contribute **81 JVM cases in 13 classes** and **11 device
cases in one class**. They exercise production entry points without changing app
behavior or schemas. All 227 JVM cases passed after offline production/test
compilation. The 11 new device cases have source checks only. Test names describe
expected outcomes; fixtures are synthetic.

| Suite files | Main regression boundaries |
|---|---|
| `LocalPinLoginManagerTest`, `AdminRoleUseCasesTest` | Attempt persistence before hashing, lockout/expiry, live account/PIN changes, repository rejection and cancellation |
| `CsvSnapshotsTest`, `CsvPublicationReviewTest`, `PatientSyncMergeTest` | Recovery retention/rotation, reviewed source changes, clinical changes versus local metadata |
| `RemoteSyncStateStoreTest` | Recognized pins, missing/disappearing index, protocol upgrade, stale pointer merge, explicit history replacement, journal bound, confirmation/errors |
| `PatientFormStateTest`, `PatientFormSubmissionTest` | Retained drafts across callbacks, explicit revision refresh, labs/tasks/badges, identity/revision preservation and cleared fields |
| `PatientFormValidationTest`, `WardSaveGuardTest` | Required-field/age/date/team rules, busy state, failed-save draft retention, conflict review and cancellation |
| `WardPatientFilteringTest`, `PatientListReturnTest` | Arabic clinical/task search, combined filters, pin order, grouped/collapsed list return |
| `TextReportBuilderTest` | Clinical fields and completed tasks, escaped labels, intact long-roster links, patient order and summary totals |
| Device `WardMutationSafetyTest` | Task attribution/owner handling and rejected mutation rollback for patient rows, audit, pending state and shift revisions |

These are focused suites alongside the existing transport, codec, cryptography,
backup, DAO, report-review/delivery, sort, doctor-import and demo suites. A JVM pass
cannot establish device suite results. The pinned-state mocks model observed reads
and writes, not atomic publication between devices.

## Run deliberately

Run from `android/`, following the resource/toolchain checks in
[AGENTS.md](../../AGENTS.md). Keep the existing pinned distribution and durable
caches; do not change toolchain versions to unblock verification.

First compile the JVM suite without packaging:

```sh
./gradlew --offline --no-daemon --max-workers=1 -Dorg.gradle.parallel=false \
  -Pkotlin.compiler.execution.strategy=in-process -Pshiftreport.autoVersion=false \
  :app:compileDebugKotlin :app:compileDebugUnitTestKotlin > /tmp/shiftreport-test-compile.log 2>&1
```

Then execute it:

```sh
./gradlew --offline --no-daemon --max-workers=1 -Dorg.gradle.parallel=false \
  -Pkotlin.compiler.execution.strategy=in-process -Pshiftreport.autoVersion=false \
  :app:testDebugUnitTest > /tmp/shiftreport-unit-tests.log 2>&1
```

Compiling/running device tests needs separate authorization; execution also needs
an emulator/disposable device. The
following command builds/installs the debug app and instrumentation APKs; it was
not authorized or run as part of the JVM verification:

```sh
./gradlew --offline -Pshiftreport.autoVersion=false :app:connectedDebugAndroidTest > /tmp/shiftreport-device-tests.log 2>&1
```

Use camelCase method names in `androidTest`; spaced backtick names can produce
coroutine class names that D8 rejects at the current Android level.

Database cases create fresh in-memory databases and seed required parent shifts.
They do not open or upgrade an installed clinical database. Cryptographic cases use
synthetic data, uniquely prefixed encrypted preferences and temporary files, then
clean up their test data. Telegram JVM cases intercept HTTP inside OkHttp and never
open a socket or contact Telegram. No real credentials or clinical records belong
in fixtures.

## Limits

This is broad regression coverage, not a claim of complete line/branch coverage or
full end-to-end verification. Real BotFather/token revocation, protected join-file
provider interactions, multi-device Telegram publication, SQLCipher/Keystore
lifecycle, biometric/PIN screens, splash recreation/hold gestures, the full patient
editor, process death, rendered PDF layout, large-font/fold/IME/TalkBack behavior and
performance still need targeted integration/device work. Report/database tests do
not erase the retained production findings in Current state, including backup
revision reuse and responsible-doctor role checks.

Record actual compile/run outcomes in [Current state](../status/current-state.md),
not as verification-only roadmap items.
