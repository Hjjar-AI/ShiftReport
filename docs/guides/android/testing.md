# Android tests

[Results](../../status/current-state.md#verification-and-limits) · [Remaining checks](../../status/testing-handoff.md) · [Device procedures](device-checks.md) · [Desktop/live](../desktop/testing-and-interop.md)

Inventory/results/limits belong in Current state; handoff alone lists remaining checks. Separate source inventory, focused passes and device runtime. Record opt-ins/owner skips; overlapping runs add no unique coverage. This guide grants no test/build permission.

## Coverage map

| Area | Existing coverage |
|---|---|
| Clinical/tasks/lab groups | Optional profiles, validation/search/filter/counts, guarded last-save-wins, checklists, grouped-lab codec/mapper/draft/search/review/wire, rollback/sort/report/export and stored metadata round trips |
| Security/storage | PIN/lockout/live authority, encrypted DB/settings/join/backup, 7→8 lab row-preservation case (execution in Current state), same-project reconnect, key/topic protection, cancellation/secret cleanup |
| Sync/delivery | Reviewed uploads/download Undo, pointer/history/mutex races, independent clients, UNKNOWN receipts, duplicate permission, write failure and actor revocation |
| Reports/Telegram | Readiness/fingerprints, exact PDF review, Room consistency, long Markdown/Unicode chunks, retry/rate-limit/cancellation/intercepted transport |
| Device/UI | Production navigation, privacy/recreation/pickers, editor/tasks/roster, setup 200% text/scrolling, adaptive/synthetic folds, clipboard/splash/native PDF/process-kill/scrolling |
| Shared fixtures | `Fixtures`, `ViewModelFixture`, `DeliveryFixture`; device DB/repository/sync/production UI, isolated per case |

## Run deliberately

[AGENTS.md](../../../AGENTS.md#authorization-and-execution) requires separate build/test approval and preflight before every Gradle attempt. Preserve others' work/caches/versions; use durable tools. Recorded environment: Gradle 8.7/Java 21, Java/Kotlin target 17, SDK 34.

From `android/`, define this shell helper: offline, one worker, in-process Kotlin, no version reservation.

```sh
srgradle() {
  ./gradlew --offline --no-daemon --max-workers=1 -Dorg.gradle.parallel=false \
    -Pkotlin.compiler.execution.strategy=in-process -Pshiftreport.autoVersion=false \
    -Pshiftreport.isolatedTests=true "$@"
}
srgradle :app:compileDebugKotlin :app:compileDebugUnitTestKotlin \
  > /tmp/shiftreport-test-compile.log 2>&1
srgradle :app:testDebugUnitTest > /tmp/shiftreport-jvm.log 2>&1
```

Focus JVM with `--tests 'fully.qualified.ClassName'`. Delivery fixtures: `domain.report.ReportDeliveryLedgerTest`, `domain.report.ReportDeliveryLedgerSafetyTest`, `network.ReportSenderTest`, prefixed `com.hos.rushdpatients`.

Device checks build/install debug app/test APKs: compile first, use the owner-selected phone and exclude Desktop export. Keep `shiftreport.isolatedTests=true` through compile/execution: debug ID `com.hos.rushdpatients.verification` and package-relative provider authorities isolate startup/DB/preferences/keystore/WorkManager from the clinical app, alongside per-case fixtures. Normal IDs/versions stay unchanged. Pull/inspect only the verification package's artifacts/storage.

```sh
srgradle :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin \
  > /tmp/shiftreport-device-compile.log 2>&1
srgradle -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  -x :app:exportDebugApk :app:connectedDebugAndroidTest \
  > /tmp/shiftreport-device.log 2>&1
```

Focus with `-Pandroid.testInstrumentationRunnerArguments.class=com.hos.rushdpatients.ui.ProjectSetupOverrideTest`; accepts other classes, comma-separated classes or `Class#method`. Select `ANDROID_SERIAL` for multiple phones. [Device procedures](device-checks.md) owns opt-ins/prerequisites.

Count failures/errors/skips from JUnit XML, not console duplicates. Default root: `~/.gradle/shiftreport-project-cache/build-output/app/`; JVM: `test-results/testDebugUnitTest/`; device: `outputs/androidTest-results/connected/debug/`. Archive source/device/API/XML/failures in ignored `local/verification/`; update Current state.

## Fixture rules

Use beverage patients/tree doctors, isolated Room/preferences/files and intercepted HTTP; never clinical storage/real credentials. Prefer deferred barriers/virtual clocks to sleeps. Cancel collectors, close DBs, remove only owned temporary files. Device methods use camelCase; spaced backticks can yield D8-invalid coroutine names.

PDF/FileProvider fixtures use `IsolatedPdfContext`, verification-app cache roots and unique owned previews. Per-case overrides cannot change roots for one cached provider authority; never broaden provider paths. Hardware/provider/live probes remain opt-in; skips are not verification. [Remaining limits](../../status/testing-handoff.md) are no edge-case backlog.
