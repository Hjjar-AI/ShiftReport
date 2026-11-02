# Device and manual checks

[Remaining work](../../status/testing-handoff.md) · [Results](../../status/current-state.md#verification-and-limits) · [Gradle commands](testing.md#run-deliberately)

Use the owner-selected device, synthetic data and isolated storage with [build/install/test approval/preflight](../../../AGENTS.md); disable auto-versioning. No automatic device-setting changes/live-project contact. These are procedures, not failures or rerun requests.

## Focused device probes

Add class/extra properties to the linked Android device command. Classes below use prefix `com.hos.rushdpatients.`; results are historical, not proof of later source.

| Class | Preparation / evidence |
|---|---|
| `ui.ProjectSetupOverrideTest` | Five cases passed: actual 200% typography, 48dp fixed actions, scroll end, pin races/recreation. External files: `verification-artifacts/setup-override/`; normal/`large-font.png`/`large-font-end.png`. |
| `ui.doctors.RealDocumentProviderImportTest` | Copy `android/app/src/androidTest/assets/shiftreport-provider-doctors.csv` to Downloads/provider. Add `-Pandroid.testInstrumentationRunnerArguments.realDocumentProvider=true`; optional `-Pandroid.testInstrumentationRunnerArguments.providerSelectionTimeoutMs=120000` (30–120 s). Wait for settled results, select exact dummy file, verify preview→isolated commit. No competing UiAutomator. Controlled callbacks do not prove URI grants. Receipt: `verification-artifacts/real-provider/result.txt`. |
| `ui.doctors.RealDocumentProviderImportTest#originalPrivateCsvThroughNativePickerCommitsOnlyIntoIsolatedRegistry` | Owner approval for temporary private Downloads `shiftreport-owner-import.csv`; `ownerPrivateDocumentProvider=true`, selection ≤120 s. OEM grant→preview→isolated commit; retain counts/booleans only, remove owned phone copy. Owner-assisted path passed; no live publication. |
| `ui.demo.DemoWorkflowDeviceTest`, `ui.demo.DemoWorkspaceInteractionTest` | Memory workflows, shared report/assignments, exact Save/Share/stale rejection. PDFs/rasters: `verification-artifacts/demo-coverage/`; every-page DEMO/content/link receipts local. |
| `pdf.NativePdfViewerTest` | Dummy report/real viewer read grant; `verification-artifacts/native-pdf-viewer/`. Launch/sample visual cannot establish all-page/viewer/print acceptance. |
| `ui.doctors.DoctorImportWorkflowTest#ownersOriginalPrivateCsvCommitsOnlyIntoIsolatedRegistry` | Opt-in owner-provisioned `filesDir/verification-inputs/doctors-import.local.csv`; isolated registry/`Uri.fromFile`, separate from OEM-picker reproduction. |
| `ui.login.PhysicalBiometricTest` | `-Pandroid.testInstrumentationRunnerArguments.physicalBiometric=true`, enrolled biometrics/owner touch, wait ≤2 min. Cancellation separate. Both historically passed API29; owner skipped success in recorded run. |
| `config.CrossClientInteropTest` | Pull `verification-artifacts/interop/`; set `SHIFTREPORT_ANDROID_INTEROP` for four reverse desktop checks. |
| `pdf.PdfRenderingTest` | PDFs/all rasters: `verification-artifacts/pdf/`; selected classic/A5/task-heavy visuals passed. |
| `ui.NativeDeviceLayoutTest` | Real density/font/window: `verification-artifacts/native-layout/`; constrained/200% cases do not change OS configuration. |
| `ui.ward.PatientScrollPerformanceTest` | Native frame-clock/touch, 200 patients. Retain device/build/motion/frame artifacts; compare named release baseline, not Compose gesture totals. |

Private CSV inputs need owner approval. File-URI and OEM grant/preview/commit passed 26 October; retain earlier failure/resolving receipts privately. This neither verifies live publication nor explains all OEM failures. Record missing-connectivity send skips even when intercepted HTTP passed.

## Manual acceptance

| Area | Check |
|---|---|
| Accessibility/windows | TalkBack: drawer→ward→editor/tasks→roster→review/confirmation→Settings; Arabic order/names/state/announcements. Real IME/large fonts: fields/fixed Save/Cancel. Physical split/fold/unfold: drafts/reviews/hinge/footer reachability. Record model/API/settings. |
| Privacy/auth | Screenshot/recents, background clipboard expiry preserving replacement clips, lock/resume/biometric failure; semantics alone cannot verify these. |
| PDF | Real viewers: Arabic/mixed text, first/last clinical lines/tasks, transitions/contrast/links/index/footer, selection/copy/search/print. Nonblank rasters do not verify every glyph. |
| Two physical clients | Disposable project: create A, protected join B, publish/retrieve; retain local edits until explicit download/replacement review; publish both in recorded order. Last successful write wins, no atomic multi-writer guarantee. |
| Interrupted live operations | Interrupt upload/index/report separately; relaunch, inspect remote/local receipts before explicit retry. UNKNOWN never auto-resends. BotFather revocation needs owner action; replacement does not revoke. |

Process-kill/SQLite-trigger/independent-client cases cover selected boundaries, not all power/disk/network windows or two phones. Record failures/scope in Current state; no private files/credentials in committed captures.
