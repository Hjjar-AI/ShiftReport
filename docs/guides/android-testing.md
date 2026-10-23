# Android test coverage

[Docs](../README.md) · [Build/cache setup](android-build.md) · [Current verification](../status/current-state.md)

The source suite contains **625 JVM methods in 81 classes** and **253 device
methods in 50 classes**. Source counts do not measure line/branch coverage or
establish that a run passed. See [current results and limits](../status/current-state.md#verification-and-limits)
for the exact verified snapshot. The tests exercise production entry points with
isolated clinical storage. [Deep scenarios and desktop/live commands](deep-testing.md)
map the expanded failure boundaries and hardware/external limits.

Regression-driven fixes preserve revision/authority checks, transaction rollback,
cancellation and password cleanup, reject malformed CSV/overflow and guard repeated
saves/exports. No versions, dependencies or database/interchange schemas changed.

## Latest expansion — October 2026

The source expansions add **249 JVM cases and 78 device cases** at the owner's
request, including the selected journey/provider/client/failure cases below and
follow-up rollover/animation regressions. On 24 October the owner authorized full
Android execution on the connected device. [Results, fixes and remaining limits](../status/current-state.md#verification-and-limits)
record the verified snapshot; earlier runs apply only to their recorded source.

### Selected journey/provider/client/failure gaps — 24 October 2026

| Suite | Added cases | Main boundaries |
|---|---:|---|
| `DoctorPickerContractTest` | 5 device | Real Doctors screen OpenDocument launch/result, preview without commit, cancellation, unavailable/revoked provider and delayed result after Activity recreation |
| `IndependentClientsWorkflowTest` | 14 device | Separate Room/preferences/sessions/cursors/locks, real uploaded bytes, retrieve/edit/publish ordering, pending/stale guards, explicit last-successful-writer behavior, token reconnect and cross-project rejection; upload/index/delivery response loss, interrupted/racing download and trigger-driven apply rollback |
| `ProductionNavigationJourneyTest` | 4 device | Actual WardAppRoot/WardNavHost and production models/screens: protected join → PIN login → patient edit → text review → confirmed publication/delivery, rotation before save/confirmation, background draft and sign-out navigation |
| `ProductionAdaptiveScreensTest` | 4 device | Narrow/short large-font drawer/settings/doctor/report controls, native text focus/IME path, settings persistence during width changes/navigation |
| `FoldSafePaneLayoutTest` | 3 device | Production pane geometry around synthetic vertical/horizontal hinges and preserved text while a hinge appears/disappears |
| Existing `PatientPublicationCoordinatorTest` | 3 JVM | Active-shift change after review and forward/backward clock correction during upload must reject index publication |
| Existing `ShiftDateTest` | 2 JVM | Local calendar deadline across DST changes and timezone switch; restores process timezone in finally |

`IsolatedWorkflowActivity` is debug-only, non-exported and requires an installed
fixture before composition. Real isolated ViewModels are installed under their
normal store keys before Hilt looks up each screen; unsupported destinations fail
closed. `WardAppRoot` accepts an optional composable navigation-controller factory,
retained for the signed-in session across temporary privacy/PIN screens so the
back stack and editor ViewModels survive backgrounding. Locked clinical screens
still leave composition; sign-out discards the controller. The default remains
`rememberNavController`. No dependency, schema or version changed.

The shared synthetic server intercepts **every** HTTP request; unexpected methods
fail locally. Client DB/preferences/keys/session metadata are independently scoped;
no installed clinical DB, real credentials or original private CSV are fixtures.
The complete send journey deliberately requires the device's validated connectivity
flag for the production send gate, even though HTTP remains intercepted. It skips
with an explicit reason if that prerequisite is absent; a skip is not journey
verification. No device/network setting is changed to satisfy it.

OpenDocument results come through a controlled ActivityResultRegistry and a real
ContentResolver/provider. This does **not** reproduce the original unknown picker
exception or exercise OEM system-picker UI/grant behavior. Independent clients
share one process/synthetic server, not two physical phones. Width/height/font
constraints and hinge models are synthetic, not physical split-screen/fold evidence.
Transport faults and SQLite-trigger storage failures are selected boundaries, not
power loss or actual disk exhaustion. The corrected eight process-kill cases passed the full device run. See the current
results for UI/lifecycle verification and any remaining failures or skips.

### Additional coverage — 24 October 2026

| Suite | Added cases | Main regression boundaries |
|---|---:|---|
| `SettingsEmptyStartAndRoutingTest` | 15 | Destructive review/confirmation separation and consumption, busy gates, publication invalidation, cancelled/uncertain reset and supervisor destination validation/stale/cancellation guards |
| `SettingsProvisioningOperationsTest` | 17 | Live authority before preparation and delivery, password/byte wiping, opaque saved IDs, picker recreation/cancellation/failure, partial-destination cleanup, download and share staging |
| `BootstrapSeederTest` | 13 | Demo/initialized/occupied project rejection, initial actor validation, actual registry contents, project/pin races, upload/pin failure, cancellation and temporary-file deletion |
| `DoctorsViewModelTest` | 23 | Local CRUD/team delegation, pending registry observation, stale edit/reload callbacks, cancellation, duplicate actions, guarded fetch and saved export cleanup/audit order |
| `WardPreferencesTest` | 8 | Independent live settings, strict booleans, regrouping without clinical loss, stored expansion and hint state |
| `WardSynchronizationTest` | 7 | Selected-snapshot status, unresolved delivery precedence, read-only publication gating and downloaded-reference freshness |
| `WardShiftOperationsTest` | 20 | Doctor-first fetch, latest/previous failure and duplicate guards, newest-bundle choices, reviewed snapshot selection, download Undo, historical export and rollover preview/confirmation |
| `WardTaskBoardInteractionTest` | 7 | Read-only completion disabled, exact task/patient revision callback, pending-only visibility, optional ownership filter and navigation |
| `StaleEditReviewInteractionTest` | 4 | Both clinical versions visible, unavailable record protection and explicit keep-draft/use-saved choices |
| `HandoverDashboardInteractionTest` | 6 | Admin-only Activity Center, optional synchronization action and urgent/overdue/pending filter actions |
| Existing `PendingExportStoreTest` | 5 | Fresh authenticated ciphertext, independent discard, truncated-file rejection before destination write, expiry cleanup and malformed-ID containment |

The first full JVM execution confirmed the rollover nested-busy-guard defect and
ignored false pin receipt. Production fixes preserve duplicate-action protection,
revision review and pin-race checks. Rollover additionally covers a suspended duplicate
confirmation and rejection recovery. Neither regression was ignored or changed to
accept the defect. Device execution also exposed the pinned Compose version retaining
the initial size-animation spec; the card now selects the current duration for each
size transition, preserving individual motion and immediate bulk expansion.

### Initial source expansion — 23 October 2026

| New suite | Cases | Main regression boundaries |
|---|---:|---|
| `ProjectSetupViewModelTest` | 21 | File-only join, invalid configuration, draft-before-bootstrap order, occupied pin, bot/group validation, seed/bootstrap failure, imported key/topics, cancellation and repeated initialization |
| `ProjectConnectionViewModelTest` | 19 | Prepare/review/apply separation, consumed/stale review, busy guards, safe transport messages, cancellation, provider metadata-only reads and cursor cleanup |
| `AnnouncementViewModelTest` | 22 | Exact review, edited/dismissed/consumed tokens, ledger filtering/failure, uncertain send, resolution, concurrent actions and lifecycle cancellation |
| `AdminViewModelTest` | 12 | Live registry actor/role, absent session, exact expected doctor delegation, failures, repeated actions and cancellation |
| `WardActivityOperationsTest` | 8 | Authority before privileged reads, denied-state clearing, duplicate load, audit/journal failure, retry and cancellation |
| `WardPatientOrderingTest` | 11 | All group partitions preserve clinical content, unknown/unassigned doctors, admission dates, stable within-group order, priority and consecutive export positions |
| `PatientCsvExporterTest` | 10 | Missing/empty shifts, actual UTF-8 CSV contents, tasks/badges/revisions, stored order and temporary plaintext cleanup on success/failure/cancellation |
| `SettingsDataOperationsTest` | 20 | Reviewed publication only, consumed/cancelled/stale tokens, offline and duplicate actions, doctors-before-patients fetch, Undo guards and local export results |
| `SettingsPreferencesTest` | 9 | Serialized preference writes, stale failure rollback, cancellation, grouped PDF print options, independent appearance/accent and lock settings |
| `UiPreferencesTest` | 9 | Malformed/partial/unknown settings, deterministic 192-combination round trip and independent appearance/font restoration |
| `ShiftDoctorPickerInteractionTest` | 11 | Real RTL selector, optional search, 40-doctor roster, deleted doctors, explicit discard, busy controls and keep-draft/use-saved revision review |
| `UiPreferencesCompositionTest` | 6 | Reduced-motion profile propagation/restoration, fallback profile, scoped overrides and large-font layout thresholds |
| `AuditAndSettingsRepositoryTest` | 8 | Real isolated Room audit scope/order/limits/metadata, strict typed settings, overflow, lock bounds and batch/delete observation |

New ViewModel cases share a dispatcher/store fixture that cancels collectors at
teardown. Concurrency cases use deferred barriers or suspended operations, without
wall-clock sleeps. CSV tests use a fresh temporary folder; Room cases use fresh
in-memory databases. No original patient records, credentials, private CSV or
installed clinical database are fixtures. The earlier expansions needed no production-source or dependency change; the
latest journey pass adds only the optional root navigation-controller factory
described above to production source.

This is broad regression coverage, not a claim of complete line/branch coverage.
Device, provider, hardware, performance and multi-device limits remain in
[Deep regression checks](deep-testing.md). Static source/resource-reference and
whitespace checks do not establish Kotlin compilation or runtime behavior.

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

The owner authorized full Android execution on 24 October. Future sessions must
follow the current session authorization and AGENTS.md resource checks before
using these commands. The connected command builds
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
