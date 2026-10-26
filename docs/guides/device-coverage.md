# Device and manual checks

[Remaining work](../status/testing-handoff.md) · [Results](../status/current-state.md#verification-and-limits) · [Gradle commands](android-testing.md#run-deliberately)

Use the selected test device, synthetic data and isolated storage. Get required build/install/test permission, preflight resources and keep auto-versioning disabled. Do not toggle device settings automatically or contact a live project by assumption. The following are procedures, not a list of new failures or automatic rerun requests.

## Focused device probes

Set the class/extra properties on the device command in the Android guide. Class names below have prefix `com.hos.rushdpatients.`.

| Class | Preparation / evidence |
|---|---|
| `ui.ProjectSetupOverrideTest` | Five cases already passed, including actual 200% typography, 48dp fixed actions, scroll-to-end, pin races and recreation. Captures: external app files `verification-artifacts/setup-override/`; inspect normal, `large-font.png` and `large-font-end.png`. |
| `ui.doctors.RealDocumentProviderImportTest` | Copy committed `android/app/src/androidTest/assets/shiftreport-provider-doctors.csv` to Downloads/chosen provider. Add `-Pandroid.testInstrumentationRunnerArguments.realDocumentProvider=true`; optionally `-Pandroid.testInstrumentationRunnerArguments.providerSelectionTimeoutMs=120000` (30–120 s). Wait for native search results to settle, select the exact synthetic file, then verify preview before isolated commit. Do not run competing UiAutomator dumps during instrumentation. Controlled callbacks are not a real URI grant. Artifact: `verification-artifacts/real-provider/result.txt`. |
| `ui.doctors.DoctorImportWorkflowTest#ownersOriginalPrivateCsvCommitsOnlyIntoIsolatedRegistry` | Skips unless the owner privately provisions app-internal `filesDir/verification-inputs/doctors-import.local.csv`. Uses an isolated registry and `Uri.fromFile`; this is separate from reproducing the original OEM picker failure. |
| `ui.login.PhysicalBiometricTest` | Success needs `-Pandroid.testInstrumentationRunnerArguments.physicalBiometric=true`, enrolled biometrics and owner touch; it waits up to two minutes. Native cancellation is separate. Both passed historically on API29; owner skipped success this run. |
| `config.CrossClientInteropTest` | Produces `verification-artifacts/interop/` for the four reverse desktop checks; pull it and set `SHIFTREPORT_ANDROID_INTEROP`. |
| `pdf.PdfRenderingTest` | Retains PDFs and every rasterized page in `verification-artifacts/pdf/`; selected classic/A5/task-heavy pages already passed visual review. |
| `ui.NativeDeviceLayoutTest` | Actual density/font/window artifacts in `verification-artifacts/native-layout/`; constrained geometry/200% tests are separate from real OS configuration changes. |
| `ui.ward.PatientScrollPerformanceTest` | Native frame-clock/touch 200-patient profile. Retain device/build/motion/frame artifacts; compare a named release baseline, not Compose gesture totals. |

The original private-CSV case requires owner-provisioned private input. Reproduce its actual provider/picker flow with the owner and retain the displayed error/stack privately. A synthetic native-picker pass does not explain the original exception. Production send journeys may skip without validated device connectivity despite intercepted HTTP; record the prerequisite, never report that skip as a pass.

## Manual acceptance

| Area | Check |
|---|---|
| Accessibility/windows | TalkBack: drawer → ward → editor/tasks → roster → review/confirmation → Settings; Arabic order, names, state and announcements. Real IME/large system fonts: all fields and fixed Save/Cancel. Supported physical split-screen/fold/unfold: retained drafts/reviews and hinge/footer reachability. Record model/API/settings. |
| Privacy/auth | Screenshot/recents protection, background clipboard expiry without deleting a replacement clip, lock/resume and biometric failure. Do not infer these from semantics assertions. |
| PDF | In real viewers inspect Arabic/mixed text, first/last clinical lines/tasks, page transitions, contrast, links/index/footer, selection/copy/search and printing. Nonblank rasterization is not every-glyph approval. |
| Two physical clients | Disposable project: create A, protected-file join B, publish/retrieve, retain local edits until explicit download, review pending replacement, publish both in recorded order. Final accepted snapshot follows last successful write; no atomic multi-writer guarantee. |
| Interrupted live operations | Separately interrupt upload/index/report delivery; relaunch, inspect remote/local receipts before explicit retry. UNKNOWN must not resend automatically. BotFather revocation needs owner action; token replacement is not revocation. |

Existing process-kill/SQLite-trigger/independent-client tests exercise selected boundaries, not every power-loss/disk-full/network window or two physical phones. Record observed failures and exact scope in Current state; keep private files and credentials out of screenshots committed to Git.
