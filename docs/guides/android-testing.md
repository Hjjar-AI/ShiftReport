# Android test coverage

[Docs](../README.md) · [Build/cache setup](android-build.md) · [Current verification](../status/current-state.md)

The suite has 34 JVM test classes (146 methods) and 10 device test classes
(41 methods). These are source counts, not passed-test counts or measured code
coverage. New cases exercise current production APIs; no production behavior,
versions or database/interchange schemas were changed for this expansion.

## Coverage map

| Area | JVM coverage | Device coverage |
|---|---|---|
| Clinical records | Required fields, invalid dates, optional assignments, predicates and dashboard totals | Patient DAO ordering, shift isolation, recycle visibility and foreign keys |
| Tasks | Counts, exact overdue boundary, ordering, team filters, validation, rollover, metadata and JSON | Repository-stamped completion actor/time and audit/pending writes |
| Doctors | Name rules, team membership/cover/aliases, CSV parsing, duplicate identities and import preview retention | Existing doctor DAO identity, rank and soft-deletion cases |
| Persistence | Patient/doctor/shift mapper round trips including tasks, badges, revisions and publication lineage | Stale patient edits/deletes/restores, nonempty rollover rejection, shift revision guards, settings and sync channel isolation |
| Security/onboarding | Live admin registry checks; same-project reconnect, topic/key protection, stale actor/config guards and password wiping | Project AES-GCM round trips, tampering/wrong keys, fresh nonces, readable-PDF exception and salted password hashes |
| Backup | — | Encrypted export, password wiping, wrong-password/tampering rejection without clinical mutation, content restore |
| Synchronization | Patient equality distinctions, known-snapshot state, current CSV, multi-shift bundles, registry codec and unlimited roster round trips | Snapshot soft deletion scoped to the selected shift |
| Report safety | Readiness, reviewed-content fingerprints, changed clinical/tasks/roster/destination rejection, UNKNOWN receipts, repeat permission, actor/status guards | Explicit enabled confirmation and cancellation in an RTL dialog |
| Telegram | Markdown, request/envelope handling, encrypted-message parse-mode omission, topic routing, retry/backoff, ambiguous send and cancellation behavior | — |
| Presentation/export | PDF clinical content retention, compact task labels, settings visibility, rounds order, report text/PDF review gates | Confirmation button enabled state |
| Demo/time/sorting | Valid synthetic demo records, independent in-memory state, 08:30 rollover/leap-day deadlines, sort rules and serialization | — |

## Run deliberately

Run from `android/`, following the resource/toolchain checks in
[AGENTS.md](../../AGENTS.md). Keep the existing pinned distribution and durable
caches; do not change toolchain versions to unblock verification.

First compile the JVM suite without packaging:

```sh
./gradlew --offline -Pshiftreport.autoVersion=false :app:compileDebugKotlin :app:compileDebugUnitTestKotlin > /tmp/shiftreport-test-compile.log 2>&1
```

Then execute it:

```sh
./gradlew --offline -Pshiftreport.autoVersion=false :app:testDebugUnitTest > /tmp/shiftreport-unit-tests.log 2>&1
```

Device work needs separate authorization and an emulator/disposable device. The
following command builds/installs the debug app and instrumentation APKs; it was
not authorized or run as part of the JVM verification:

```sh
./gradlew --offline -Pshiftreport.autoVersion=false :app:connectedDebugAndroidTest > /tmp/shiftreport-device-tests.log 2>&1
```

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
