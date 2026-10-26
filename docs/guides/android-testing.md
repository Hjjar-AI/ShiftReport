# Android tests

[Results](../status/current-state.md#verification-and-limits) · [Remaining checks](../status/testing-handoff.md) · [Device procedures](device-coverage.md) · [Desktop/live](deep-testing.md)

Historical inventory/results: [Current state](../status/current-state.md#verification-and-limits). Suite descriptions below predate retired features; they are not a current source audit.

## Coverage map

| Area | Existing coverage |
|---|---|
| Clinical/tasks | Validation/search/filter/counts, clinical/task persistence, rollback, sorting and report/export; retired ownership/stamp/rollover expectations need authorized review |
| Security/storage | PIN/lockout/live authority, encrypted DB/settings/join/backup, same-project reconnect, key/topic protection, cancellation and secret cleanup |
| Sync/delivery | Reviewed uploads/download Undo, pointer/history/mutex races, independent clients, UNKNOWN receipts, duplicate permission, write failure and actor revocation |
| Reports/Telegram | Readiness/fingerprints, exact PDF review, Room snapshot consistency, long Markdown/Unicode chunks, retry/rate-limit/cancellation and intercepted transport |
| Device/UI | Production navigation, privacy/recreation/picker results, editor/task/roster controls, setup 200% text/scrolling, adaptive/synthetic folds, clipboard/splash, native PDF/process-kill and scrolling |
| Shared fixtures | `Fixtures`, `ViewModelFixture`, `DeliveryFixture`; device database/repository/sync/production-UI fixtures. Each case owns isolated state. |

## Run deliberately

[AGENTS.md](../../AGENTS.md) requires explicit build/test authorization and active-build, RAM/swap and durable-toolchain/cache checks before every Gradle attempt. Do not stop others' work, clear caches, download temporary toolchains or change versions. Installed Gradle 8.7/Java 21 have been used with Java/Kotlin target 17 and SDK 34.

From `android/`, define this helper in the current shell; it preserves offline, one-worker, in-process Kotlin and disabled version reservation for every command:

```sh
srgradle() {
  ./gradlew --offline --no-daemon --max-workers=1 -Dorg.gradle.parallel=false \
    -Pkotlin.compiler.execution.strategy=in-process -Pshiftreport.autoVersion=false "$@"
}
srgradle :app:compileDebugKotlin :app:compileDebugUnitTestKotlin \
  > /tmp/shiftreport-test-compile.log 2>&1
srgradle :app:testDebugUnitTest > /tmp/shiftreport-jvm.log 2>&1
```

For a focused JVM run, add one or more `--tests 'fully.qualified.ClassName'` filters. The delivery-fixture scope is `domain.report.ReportDeliveryLedgerTest`, `domain.report.ReportDeliveryLedgerSafetyTest` and `network.ReportSenderTest`, all under `com.hos.rushdpatients`.

Device checks build/install debug app/test APKs. Compile first, then run on the owner-selected connected device; exclude the automatic Desktop export task:

```sh
srgradle :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin \
  > /tmp/shiftreport-device-compile.log 2>&1
srgradle -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  -x :app:exportDebugApk :app:connectedDebugAndroidTest \
  > /tmp/shiftreport-device.log 2>&1
```

To focus the device command, add `-Pandroid.testInstrumentationRunnerArguments.class=com.hos.rushdpatients.ui.ProjectSetupOverrideTest` (or another fully qualified class, comma-separated classes, or `Class#method`). Select one phone with `ANDROID_SERIAL` when multiple devices are connected. Opt-in prerequisites/flags are in [Device procedures](device-coverage.md).

Count failures/errors/skips from JUnit XML, not duplicated console messages. Default output: `~/.gradle/shiftreport-project-cache/build-output/app/`; JVM XML: `test-results/testDebugUnitTest/`; device XML: `outputs/androidTest-results/connected/debug/`. Archive exact source/device/API, XML and failure details in ignored `local/verification/`; update Current state.

## Fixture rules

Use synthetic beverage patients/tree doctors, fresh isolated Room/preferences/files and intercepted HTTP; never open installed clinical storage or use real credentials. Use deferred barriers/virtual clocks rather than scheduling sleeps. Cancel ViewModel collectors, close databases and remove only fixture-owned temporary files. Device method names use camelCase; spaced backticks can produce D8-invalid coroutine names. Native hardware/provider/live probes remain opt-in; a skip is not verification. Do not convert the [remaining limits](../status/testing-handoff.md) into an edge-case backlog.
