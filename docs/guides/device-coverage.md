# Additional coverage execution

[Suite map](android-testing.md) · [Measured results](../status/current-state.md#verification-and-limits)

These procedures cover the external parts of the latest test expansion. The
offline compile, 98 focused JVM cases and 51 distinct focused device cases passed,
including assisted native selection after its privacy-lifecycle fix. See Current
state for the separate runs, preserved earlier timeouts and physical limits.
Use synthetic patients/doctors and disposable projects; never
select a clinical database for fault or two-phone experiments. Follow `AGENTS.md`
authorization, active-build, RAM/swap and cached-toolchain checks before Gradle.

## Focused automated verification

From `android/`, first compile the changed production/JVM/device sources without
packaging. Keep full logs in ignored local verification storage or `/tmp`:

```sh
./gradlew --offline --no-daemon --max-workers=1 -Dorg.gradle.parallel=false \
  -Pkotlin.compiler.execution.strategy=in-process -Pshiftreport.autoVersion=false \
  :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin \
  > /tmp/shiftreport-coverage-compile.log 2>&1
```

Run the affected JVM report and Telegram suites:

```sh
./gradlew --offline --no-daemon --max-workers=1 -Dorg.gradle.parallel=false \
  -Pkotlin.compiler.execution.strategy=in-process -Pshiftreport.autoVersion=false \
  :app:testDebugUnitTest --tests 'com.hos.rushdpatients.domain.report.*' \
  --tests 'com.hos.rushdpatients.network.telegram.*' \
  > /tmp/shiftreport-coverage-jvm.log 2>&1
```

With the owner-selected test device connected, run the affected instrumented
suites. This builds/installs debug app/test APKs, so it needs explicit approval:

```sh
./gradlew --offline --no-daemon --max-workers=1 -Dorg.gradle.parallel=false \
  -Pkotlin.compiler.execution.strategy=in-process -Pshiftreport.autoVersion=false \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.hos.rushdpatients.domain.report.ReportBuilderTest,com.hos.rushdpatients.domain.report.ReportSnapshotConsistencyTest,com.hos.rushdpatients.data.db.ProcessDeathWorkflowTest,com.hos.rushdpatients.pdf.PdfRenderingTest,com.hos.rushdpatients.ui.ProductionNavigationJourneyTest,com.hos.rushdpatients.ui.doctors.DoctorPickerContractTest,com.hos.rushdpatients.ui.ward.ProductionAdaptiveScreensTest,com.hos.rushdpatients.ui.NativeDeviceLayoutTest,com.hos.rushdpatients.ui.doctors.RealDocumentProviderImportTest \
  :app:connectedDebugAndroidTest > /tmp/shiftreport-coverage-device.log 2>&1
```

The real-provider case skips unless explicitly enabled and prepared below. A
validated connectivity prerequisite can also skip the send journeys even though
their HTTP is intercepted. Count results from JUnit XML, including skip reasons.
Preserve compile/run logs and artifacts with the source snapshot and device/API.

## Real document provider

1. Copy the committed synthetic fixture
   `android/app/src/androidTest/assets/shiftreport-provider-doctors.csv` to the
   test device's Downloads folder or the provider being examined. Keep its name.
2. Run only `com.hos.rushdpatients.ui.doctors.RealDocumentProviderImportTest` using
   the device command above with the class property replaced and add
   `-Pandroid.testInstrumentationRunnerArguments.realDocumentProvider=true`.
3. The real system picker opens. Within 30 seconds, navigate to the folder/provider
   containing the fixture; add
   `-Pandroid.testInstrumentationRunnerArguments.providerSelectionTimeoutMs=120000`
   for up to two minutes of manual OEM navigation. The test can select the filename
   through native accessibility or accept a manual native selection. It does not
   inject an ActivityResult or URI grant, and still checks the exact fixture preview
   before confirming its isolated commit.
4. Expected: one new doctor in preview, no registry writes before confirmation,
   then one isolated committed row. HTTP publication is intercepted locally.
   The test never opens the installed clinical registry.

The provider remains responsible for making its document visible and granting
access; some OEMs expose a different filename or an extra confirmation. Record that
as an unsupported automation path, not an import failure. The test leaves the
owner-provisioned fixture in place. App artifacts are under external app files:
`verification-artifacts/real-provider/result.txt` (device and preview/error result).

To reproduce the original private-file failure, use its actual provider flow only
with the owner present. Record provider/model/API, whether preview appeared and the
displayed error. Keep any file, screenshot or redacted crash stack private. A
passing synthetic fixture does not explain the unknown original exception or
justify changing identity/clinical-reference checks.

## PDF visual review

`PdfRenderingTest` retains PDFs and every rendered page under external app files
`verification-artifacts/pdf/`. Review `classic-a5-task-heavy` and
`cards-dark-task-heavy`, alongside the original classic/A5 fixtures:

- Read the beginning/end of the treatment text, first/last task and completed-task
  attribution. Check Arabic shaping, emoji availability, mixed `12.5 mg` text and
  line endings. A rasterization assertion only establishes nonblank pages.
- Inspect page transitions for clipped/repeated/missing lines and task labels;
  inspect header/footer, index links and page numbers at normal reading zoom.
- Compare dark/light contrast, narrow A5 columns, strong rules and print settings.
  Record the page number and a private crop for any failure. Text formatting checks
  before export do not prove the PDF contains every glyph.

## TalkBack, IME and real windows

`NativeDeviceLayoutTest` uses actual device density, font scale and window size;
it does not apply the fixture's simulated 320dp/large-font overrides. Report
screenshots and configuration are in `verification-artifacts/native-layout/`.
`ProductionAdaptiveScreensTest` separately exercises simulated constrained windows,
200% font, labelled 48dp footer controls, focus and unsaved drafts during resizing.

On a test device, with the owner's chosen accessibility/window settings:

1. With TalkBack, traverse drawer → ward card actions → editor → report roster →
   review/confirmation → Settings. Check Arabic reading order, control names,
   selected/disabled states and task completion announcements. Back must not send.
2. Open labs and a task description with the real IME. At large system font sizes,
   scroll to all fields; Save/Cancel must remain reachable and keep the draft.
3. On supported hardware, resize split-screen and fold/unfold with an unsaved
   editor and then with report review open. Check both hinge orientations, footer
   reachability and draft/review behavior; explicitly save or cancel afterward.
4. Run the native-layout suite on each actual OS/device/window configuration and
   archive its XML and screenshots separately. Record a real configuration change
   as such; Activity recreation and synthetic geometry are different evidence.

Do not toggle device settings automatically. Semantics/focus assertions do not
establish TalkBack speech, actual IME occlusion or physical hinge behavior.

## Two physical phones and interrupted publication

Use an owner-designated disposable Telegram project, not an existing clinical
project. No automated suite here contacts a real bot or changes a live pinned index.

1. Create the synthetic project on phone A; join B through its protected join file.
   Add hot-beverage patients/tree-named doctors. Publish A and explicitly retrieve B;
   compare clinical fields, task status, roster and saved order.
2. Edit A, publish its reviewed snapshot, and verify B retains its local state until
   retrieval. Exercise B's pending-edit warning/review before accepting a download.
3. From known snapshots, make reviewed publications on both phones in recorded
   order. Verify the final accepted pinned snapshot follows the documented last
   successful write; do not expect automatic field merging or atomic multi-writer
   protection. Retain the replaced snapshot/history as supported by the app.
4. Send one synthetic reviewed report. Interrupt connectivity at upload/index and
   delivery boundaries in separate trials. After reconnect/relaunch, inspect both
   Telegram and local receipt/publication state before any explicit retry. An
   uncertain delivery must not automatically resend; saving a token is not revocation.
5. Compare current Android versions and a second vendor. Record model/API, source
   snapshot, action order, receipt/snapshot identifiers and observed result without
   credentials or clinical content.

The process-death suites already kill a dedicated debug worker around real Room,
receipt, restore, Undo and encrypted-export operations. The new acceptance-before-
confirmation case uses a durable synthetic acceptance marker, not a live remote.
These checks do not simulate every power-loss, disk-full or network crash window.

Record executed evidence and remaining limits in Current state/handoff; do not
convert unexecuted hardware checks into passing results or roadmap implementation.
