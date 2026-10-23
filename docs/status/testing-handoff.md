# Testing and patient-scroll handoff — 24 October 2026

**Current authorization: expand Android test source only.** The owner explicitly
reserved builds for later permission. No build, compilation, test execution, APK
installation or live Telegram request was performed during this expansion. Read
`AGENTS.md`, the working tree and [coverage/limits](../guides/deep-testing.md) before
resuming. Preserve earlier pending source; do not stage/commit.

## Latest test-source additions

The selected-gap pass adds **5 JVM and 30 device cases** (5 new device suites,
2 extended JVM suites). Unexecuted additions across the source expansions now total
**247 JVM and 77 device methods**. Total source: **623 JVM methods/81 classes and
252 device methods/50 classes**; desktop remains unchanged.
[Suite scope and prerequisites](../guides/android-testing.md#latest-source-expansion--awaiting-execution).
All additions are uncompiled/unrun. Source signatures, referenced string resources,
case names and whitespace were checked; those checks are not execution evidence.
The only production seam added in the selected-gap pass is the optional root
navigation-controller factory, called in its existing logged-in branch; a non-exported
debug host installs isolated ViewModels before ordinary Hilt screen lookup. No
clinical/auth/sync rule, toolchain/dependency/schema change or migration work.
Two cases are intentionally expected to expose source defects when executed:
`WardShiftOperationsTest.confirmedRolloverReachesRepositoryWithFreshIdsPendingTasksAndExpectedRevision`
(nested `saving` guard prevents delegation), and
`BootstrapSeederTest.unconfirmedPinReceiptCannotReturnSuccessfulSeed` (false pin
receipt is ignored). They have not run; do not mark them passed, ignore them or
weaken their assertions. Source fixes are tracked in the roadmap.

Once fresh permission is given, use the narrow three-task offline compile below
first, after the required resource/toolchain preflight. Test execution requires its
own authorization. The prior focused device list below does **not** include the
new `ShiftDoctorPickerInteractionTest`, `UiPreferencesCompositionTest` and
`AuditAndSettingsRepositoryTest`, `WardTaskBoardInteractionTest`,
`StaleEditReviewInteractionTest`, `HandoverDashboardInteractionTest` and expanded
`PendingExportStoreTest`, plus `DoctorPickerContractTest`,
`IndependentClientsWorkflowTest`, `ProductionNavigationJourneyTest`,
`ProductionAdaptiveScreensTest` and `FoldSafePaneLayoutTest`; include those when authorizing a focused device
run, or run the full device suite under explicit permission. Existing pending
crash/scroll checks remain necessary and must not be credited from older evidence.

The new host/graph/Hilt-store wiring and controlled picker registry need compilation
and runtime validation. The full send journey skips without validated device
connectivity (its HTTP is still intercepted); the phone must be unlocked for UI/IME
checks. Do not change device settings or perform live Telegram sends by assumption.
Controlled picker callbacks do not reproduce OEM picker/grant behavior or the
original unknown exception. Independent clients, synthetic hinges and injected
storage/transport failures do not establish physical two-phone/fold/split-screen,
power-loss or actual disk exhaustion. Preserve these limits when reporting results.

## Earlier paused work (historical permissions and evidence)

The remainder records the earlier session. Its build/test permissions do not carry
over to the latest source expansion; the current owner instruction takes priority.

### Earlier authorized scope and source

The owner requested both annotated gap lists, deeper testing and sluggish **patient
list/card** scrolling. Test creation/modification, offline JVM compilation/execution
and new connected-device tests were explicitly authorized. No release/distribution,
version/toolchain/schema changes or migration inspection/work is authorized.

Earlier source counts: **376 JVM methods/64 classes; 175 device methods/39 classes;
60 desktop methods**. Counts are not passed-run counts or line/branch coverage.
Most broad coverage is in `ae5479d`, with later biometric/native-scroll commits.

Pending, unverified source changes:

- `PatientCardState.kt`: immutable display-snapshot contract; collections must be
  replaced, never mutated in place. Actions are immutable callback references.
- `WardScreen.kt`: remember actions by patient, bulk-expansion flag, ViewModel,
  editor and haptics; confirm no stale patient/revision callbacks. Earlier verified
  change isolated scroll-driven dock state from the clinical workspace.
- `WardPatientGrid.kt`: stop placement animation during active scrolling and when
  motion is disabled; preserve idle reorder/filter/individual-expansion animation.
- `PatientCard.kt`: keep size/chevron animator history, use `snap()` for bulk/disabled
  motion, remove the redundant no-transition `AnimatedVisibility` wrapper. Retaining
  history matters: attaching an animator only on the first expansion can snap.
- New `PatientCardExpansionTest`: two checks for intermediate height on the first
  individual expansion and immediate bulk expansion. **Not compiled/run yet.**
- Debug `CrashWorkflowHarness` and extended `TransactionCrashService`, plus new
  `ProcessDeathWorkflowTest`: real receipt UNKNOWN/SENT durability, backup restore
  and download Undo before/after commit. Unique file DBs/preferences, synthetic
  data, no network; never open the installed clinical DB.
- Latest crash-fixture correction adds required birth date and an IPC marker
  (`what=101`, matching mode) before the intentional kill. Both generic and workflow
  tests must receive that marker. **Correction not compiled/run yet.**

## Verified evidence and failed attempts

Evidence root (ignored): `local/verification/deep-tests-2026-10-23/`.

- `final-verification.log`, `final-jvm-results/`: **376 JVM, zero failures/errors/skips**.
- `final-device-results/`: full **166-case** Android 10/PPA-LX2 run: **165 passed,
  zero failures/errors, one deliberate biometric-success skip**. Applies before
  the newest scroll/stability/crash-marker/expansion edits above.
- `physical-biometric.log`: owner used enrolled fingerprint; production success
  callback passed separately. `biometric-cancel.log`: real native negative button
  was automatically pressed; production cancel callback passed, without success.
- `desktop-full.log`: **60/60 passed, no skips**, including all four reverse Android
  interoperability checks and all seven temporary-source mutation checks.
- `live-telegram.json`: **3 live synthetic checks passed**, message IDs retained.
  Two independent clients edited their own synthetic index; no existing project
  pin was changed. No second physical device was available. Do not repeat live sends
  merely to resume this work.
- Original private CSV: actual production ViewModel preview **and commit into an
  isolated in-memory registry passed**. Synthetic 33-row provider import also passed.
  Actual system-picker/provider failure remains unreproduced; do not claim it fixed.
- Native PDF tests passed; first/last Classic and A5 pages plus a content-heavy A5
  page were visually inspected. Artifacts in `pdf/`; this is not every-glyph approval.
- Earlier runs exposed fixture timestamp, secondary-process WorkManager, provider
  classloading, duplicate doctor identity and reused-date/destination assumptions;
  corrected baseline passed. Locked-phone UI failures passed after owner unlocked.
- `workflow-first-results/` / `workflow-first.log`: newest nine-case run had **two
  failures**, actual backup/Undo after-commit cases. Missing birth date caused CSV
  rejection. Precommit cases could falsely pass from that unrelated worker crash;
  **do not credit them**. The pending birth-date and explicit-marker changes address
  this; rerun all eight process-death cases, not just the two visible failures.

## Scroll evidence — no release speedup claim

Old Compose-clock gesture totals were dominated by unknown delay and are unsuitable
for responsiveness comparisons. Use `PatientScrollPerformanceTest`, which launches
a normal Activity and injects native touches, with 200 real synthetic cards.

Before the newest scroll/stability changes, `patient-scroll-native-normal.json`
recorded **175 frames, median 16.65 ms, p95 61.35 ms, 87 over 16.67 ms**. The paired
reduced-motion sample recorded **206 frames, median 9.89 ms, p95 21.22 ms, 40 over
16.67 ms**. An earlier reduced sample was faster, demonstrating run variability.
These are single-device debug measurements, not release baselines. The latest
optimizations remain unmeasured. Retain old artifacts; save new results separately.

## Next steps, in order

1. Review pending changes and verify the immutable-snapshot/callback assumptions.
   Fix compilation/runtime failures evidenced by the next checks; preserve original
   clinical/revision/authority/branding behavior. Do not add new speculative scope.
2. Before **each** Gradle attempt check all visible build/compiler activity and
   RAM/swap pressure against the 2 GB heap. Only an idle VS Code GradleServer was
   visible at pause. Recheck; do not stop others or clear caches. Use installed Java
   21, SDK/build-tools 34 and pinned cached Gradle 8.7. No dependency downloads.
3. From `android/`, compile narrowly first; keep full logs local:

```sh
./gradlew --offline --no-daemon --max-workers=1 -Dorg.gradle.parallel=false \
  -Pkotlin.compiler.execution.strategy=in-process -Pshiftreport.autoVersion=false \
  :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin
```

4. After separate execution authorization, if compile passes, run the full JVM suite
   and the earlier **13 focused device methods** plus the new suites above:

```sh
./gradlew --offline --no-daemon --max-workers=1 -Dorg.gradle.parallel=false \
  -Pkotlin.compiler.execution.strategy=in-process -Pshiftreport.autoVersion=false \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.hos.rushdpatients.data.db.ProcessDeathTransactionTest,com.hos.rushdpatients.data.db.ProcessDeathWorkflowTest,com.hos.rushdpatients.ui.ward.PatientGridScrollTest,com.hos.rushdpatients.ui.ward.PatientScrollPerformanceTest,com.hos.rushdpatients.ui.ward.PatientCardExpansionTest \
  :app:testDebugUnitTest :app:connectedDebugAndroidTest
```

5. The phone must be unlocked for UI tests. Original USB stay-awake value **0 was
   restored at pause**. If temporarily enabling it for testing, record and restore
   the current value afterward. No private original CSV is needed for this focused
   run. Its phone copy was deleted and absence confirmed; preserve the PC original.
6. Confirm XML outcomes, all intended crash markers and both native-motion reports.
   Actual output root: `/home/mhmmd/.gradle/shiftreport-project-cache/build-output/app/`.
   Pull only synthetic `verification-artifacts/patient-scroll-native-frames.json`
   and `patient-scroll-native-reduced-frames.json` from the debug app's external
   files directory. Investigate remaining scroll hitches; do not infer release
   smoothness from passing interaction tests or old Compose-clock metrics.
7. Save logs/XML, update [current results](current-state.md#verification-and-limits)
   and coverage counts/limits. Run `git diff --check` and, if staged, cached check.
   Do not report all 175 device methods as passed before accounting for the newer
   cases and the separately executed physical-success/cancellation checks.

## External/manual limits after the pending checks

The [coverage matrix](../guides/deep-testing.md) records the boundaries. Remaining
external work includes second-device UI, Windows native GUI/DPAPI, TalkBack and
foldable/IME/other-OS coverage, physical screenshot/recents/privacy transitions,
real BotFather revocation and release/workload performance baselines. Selected
process-kill boundaries do not cover every network publication/download crash
window or power loss. Timezone/clock changes across every complete workflow and
all PDF glyph/content combinations are not exhaustively checked.

Protected bot config is ignored at `local/verification/telegram-test-config.local.json`;
never print it. Original CSV remains ignored at `android/doctors-import.local.csv`.
Owner approved only a private phone test copy, isolated testing and deletion;
cleanup is complete (`pause-cleanup.json`). No secrets or original CSV are fixtures.
