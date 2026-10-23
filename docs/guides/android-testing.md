# Android test coverage

[Docs](../README.md) · [Build/cache setup](android-build.md) · [Current verification](../status/current-state.md)

The suite now has 60 JVM test classes (337 methods) and 25 device test classes
(110 methods). JUnit reports confirm all 337 JVM tests passed on 23 October 2026,
including the newest 72 cases; the earlier 110-case device run passed on
PPA-LX2/Android 10 and was not repeated for these JVM-only additions. Source counts
do not measure line/branch coverage. The tests exercise production entry points.
Regression-driven fixes advance backup restore revisions, enforce transactional
live clinical assignments, and preserve
cancellation/password cleanup. No versions or database/interchange schemas changed.

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

## Integration and device suites

| Suites | Boundaries exercised |
|---|---|
| `DoctorMutationRepositoryTest` | Real Room/admin authorization, stale editors/import previews, clinical references, protected ranks, PIN retention/invalidation, identity merging, synthetic 33-row CSV commit |
| `WardMutationSafetyTest`, `WardAssignmentSafetyTest`, `BackupRestoreSafetyTest` | Mutation rollback, task attribution, live clinical roles at add/save/restore/rollover, restore invalidation of patient/shift drafts, rejected restore pending-state rollback |
| `PatientRetrievalCoordinatorTest`, `LocalDownloadUndoTest`, `DoctorSyncCoordinatorTest` | Intercepted HTTP with real repositories: download/apply/Undo, persisted Undo after recreation, concurrent local edits, recovery cursor/baseline protection, doctor pull/identity/secret retention |
| `PatientPublicationCoordinatorTest`, `RemoteSyncStateStoreTest` | Exact reviewed upload, actor/source/local edit races, interrupted upload, index failure, temporary cleanup, pointer/history/journal rules |
| `ReportPreviewViewModelTest`, `ReportSenderTest`, `AnnouncementPublisherTest` | Explicit review-to-publication-to-delivery, changed review rejection, partial/uncertain delivery, durable duplicate blocking after recreation, announcement review/index failure |
| `ReportPdfWorkspaceTest`, `PdfRenderingTest` | Reviewed file digest/options/content/existence guards; native rendering of every page in multipage Arabic classic and A5 card PDFs |
| `ProjectProvisioningManagerTest`, `PendingExportStoreTest` | Real encrypted join-file round trip, staging/recreation/discard, wrong passwords/tampering, password wiping and encrypted export staging |
| `EncryptedDatabasePersistenceTest`, `SessionPersistenceTest` | SQLCipher close/reopen/wrong key, retained device key, absent-key preservation guard, encrypted session reload/unlock/clear in isolated storage |
| `TelegramLoginManagerTest`, `BootstrapManagerTest` | Challenge identity/offset handling, nonce format, connection failures/cancellation, guarded bootstrap and temporary cleanup |
| `PatientEditorInteractionTest`, `PinUnlockInteractionTest`, `SplashIntroScreenTest` | RTL editor Save/labs/tasks, large-font busy footer, task checkbox draft metadata, PIN IME submit/clearing, explicit biometric/sign-out actions, splash tap/hold/accessibility actions |

## Additional safety coverage

| New suite | Cases | Behaviors asserted |
|---|---:|---|
| `ReportDeliveryLedgerSafetyTest` | 16 | Twelve competing attempts against an active delivery; cancellation releases the active guard while retaining UNKNOWN; registration/receipt storage failures; corrupt ledger rejection; durable duplicate blocking and one-use copy permission after recreation; live actor checks and receipt/metadata integrity |
| `WardEditorStateTest` | 10 | Captured insertion revisions, retained unsaved drafts/conflict review, mutually exclusive dialogs, delayed dismissal callbacks, independent copied identities and cleared admission/task/editor metadata |
| `DemoWardViewModelTest` | 24 | Production demo controller save/recycle/sort/roster actions, stale edit/delete/insertion rejection, completion attribution/reopening, callbacks with saved revisions, reset/session isolation and local-only remote actions |
| `PatientTasksInvariantTest` | 12 | Deterministic 48-record completion/owner/deadline/priority matrix; count partitions, monotonic overdue rules, stable/idempotent ordering and rollover, wire round trips, incomplete attribution and resident/supervisor filter combinations |
| `CsvClinicalIntegrityTest` | 10 | Arabic/quotes/CR/LF and nested JSON, long clinical fields/200 tasks, rejection of invalid nested records without partial imports, reordered columns, persisted positions and a 120-doctor roster |

Concurrency cases use explicit coroutine barriers and bounded timeouts, without
sleep-based scheduling. Storage faults are injected at the settings boundary;
these are JVM behavior checks, not power-loss or filesystem-durability simulations.
The task matrix uses deterministic combinations without another testing dependency.
No production behavior, dependencies, versions or schema definitions changed.
All 72 additions passed in a focused run, followed by a passing full 337-case JVM
run. Both used the existing pinned toolchain offline with one worker and automatic
version reservation disabled. Logs and JUnit XML are retained under
`local/verification/expanded-tests-2026-10-23/` (ignored).

The original domain/codec/transport/report-review suites remain. Fixture records are
synthetic. HTTP interceptors handle every request locally; these suites never
contact a real bot. Pinned-state tests model observed reads/writes and the documented
last-write-wins behavior, not cross-device atomicity. Check Current state for actual
results and source counts; a compiled test is not a passed test.

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

The owner authorized compilation, focused JVM execution and full JVM regression
execution for the newest additions. Future runs still require the applicable
AGENTS.md build/resource checks and authorization. Device execution was authorized
for the earlier expansion only. The connected command builds
and installs debug app/test APKs on the selected authorized device, retaining the
installation after execution:

```sh
./gradlew --offline --no-daemon --max-workers=1 -Dorg.gradle.parallel=false \
  -Pkotlin.compiler.execution.strategy=in-process -Pshiftreport.autoVersion=false \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  :app:connectedDebugAndroidTest > /tmp/shiftreport-device-tests.log 2>&1
```

Use camelCase method names in `androidTest`; spaced backtick names can produce
coroutine class names that D8 rejects at the current Android level.

Most database cases create fresh in-memory databases and seed required parent
shifts. SQLCipher persistence cases use uniquely isolated on-disk test databases.
They do not open or upgrade an installed clinical database. Cryptographic cases use
synthetic data, uniquely prefixed encrypted preferences and temporary files, then
clean up their test data. Telegram JVM cases intercept HTTP inside OkHttp and never
open a socket or contact Telegram. No real credentials or clinical records belong
in fixtures.

## Limits

This is broad regression coverage, not measured complete line/branch coverage.
Recreating repositories/managers and reopening a database exercise persistence;
they do not simulate Android process death. Rendered PDF checks establish page
creation and nonblank native rasterization, not visual approval of every glyph or
clipping boundary. Touch/semantics tests do not establish physical fingerprint
success, full TalkBack operation, real foldable/IME configurations, or performance.
Splash screen interaction tests do not establish the Activity's deadline behavior
across recreation. Third-party document-provider failures, actual BotFather/token
revocation and competing multi-device publication remain external/manual checks.
The synthetic 33-row import validates the repository route; it does not reproduce
the owner's original unavailable CSV exception.

Record actual compile/run outcomes in [Current state](../status/current-state.md),
not as verification-only roadmap items.
