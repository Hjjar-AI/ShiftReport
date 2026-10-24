# Android verification handoff — 24 October 2026

The owner authorized full Android builds/JVM/device execution and connected the
PPA-LX2 (Android 10/API 29). The optional physical fingerprint-success check was
also authorized and passed with the owner's touch. Read `AGENTS.md` and preserve
unrelated working-tree changes; do not stage/commit or inspect migration files.

## Current execution

Subsequent additions total **33 JVM and 30 device cases**, bringing
source totals to **658 JVM/84 classes and 283 device/54 classes**. They cover
report assembly/review, bounded long messages, rate limiting, concurrent snapshot
reads, revoked authority, process-death recovery, PDF/task pages, accessibility
semantics and actual-window/provider paths. The chunker and rate-limiter clock
seam also changed. The owner authorized offline compilation and focused JVM tests:
production/JVM/device-test Kotlin compilation passed, and **98 report/Telegram JVM
cases in 12 classes passed with zero failures/errors/skips**, including all 33 new
JVM cases. No corrections were needed for the JVM run. Logs and copied XML are
in ignored `local/verification/coverage-2026-10-24/`. The full JVM suite was not rerun.

The owner then connected the PPA-LX2/Android 10 and approved focused device
packaging/installation/execution. The main run passed **49 cases with zero
failures/errors and one expected opt-in skip**, 50 cases/9 classes in XML.
`device-01.log` and `device-01-results/` are archived under the same verification
directory. Actual native selection exposed lost ActivityResult registration state
when background privacy removed NavHost. A session-owned saveable-state holder
now retains it alongside the controller/ViewModels while clinical content remains
hidden and sign-out discards both owners. The **22-case affected UI rerun passed
with no failures/skips**, including the new background-result regression.
`device-root-fix-01.log` and `device-root-fix-01-results/` record it. The actual
picker/grant/preview/isolated commit then passed separately with assisted native
selection: `real-provider-06.log`, `OK (1 test)`. Earlier selection/result timeouts
remain separately archived. The opt-in test accepts manual native selection and
an optional 30–120-second selection window, preserving all fixture assertions.

**51 distinct focused device cases passed**, including all 30 new cases; do not
sum repeated cases or claim a full 283-case run. All 11 new task-heavy PDF pages
and selected native report images were visually reviewed. Artifacts are copied
under `device-artifacts/`. The two CSV copies and temporary empty folder created
for this run were removed; existing files/settings were preserved. Debug APKs
remain installed. No build/test remains running. Full TalkBack, physical two-phone/
fold/split-screen and other OS/vendor checks remain outside this evidence.
[Focused commands and hardware/provider preparation](../guides/device-coverage.md).
The full-run results below describe the preceding snapshot, not these additions.

The final complete run **succeeded**: **625 JVM tests/81 classes passed, no
failures/errors/skips**; **253 device cases/50 classes: 251 passed, zero failures/
errors, two expected skips**. XML is authoritative: the Gradle console counts
skip notifications twice and printed 255. Physical fingerprint success passed
separately with the owner's touch, so **252 distinct device cases are verified**.
Only the optional original-private-CSV case remains unrun because its fixture was
not provisioned. That private file was not recopied to the phone.

Final log: ignored `local/verification/android-tests-2026-10-24/all-final.log`.
Archived final XML: `unit-final-results/` and `device-final-results/` alongside it.
The first device run's 8 failures and all focused reruns remain archived separately;
follow-up runs confirmed the corrections rather than suppressing failed cases.

## Findings and corrections

- Rollover no longer pre-sets the busy flag before entering the shared guard. It
  enters immediately, blocks duplicate confirmation, retains expected revision and
  releases busy state on failure. Two extra JVM regressions cover these boundaries.
- Initial registry seeding requires a positive Telegram pin receipt, retaining
  existing pin/project race checks, cancellation and temporary-file cleanup.
- The pinned Compose version remembers its initial size-animation spec. A stable
  spec reads the current duration per transition, so first individual expansion/
  collapse animate and bulk expansion remains immediate. Three device cases pass.
- Background privacy locking now retains the signed-in navigation controller and
  editor ViewModels. Clinical screens still leave composition while locked;
  sign-out discards the controller. The real background/resume draft test passes.
- Test corrections use actual Settings workspaces, fixed-button visibility,
  complete comparison text, real card actions and foreground authorization waits.
  Clipboard tests wait for Activity focus. LazyColumn may retain unplaced semantics;
  the ownership-filter test checks that the excluded task is not displayed.
- Native biometric cancellation recycles nodes only before API 33. The narrow
  legacy-call suppression removes deprecation warnings while retaining Android 10
  cleanup. Native cancellation passed after the change.

## Evidence and prerequisites

Logs and archived XML: `local/verification/android-tests-2026-10-24/`.
Initial failed runs remain separate from focused/final results. All eight corrected
process-death cases, fourteen independent-client cases, five controlled picker
cases, synthetic fold geometry and scrolling suites passed both full device runs.
All HTTP is intercepted; no installed clinical database or real report destination
is used. No device settings were changed; no build/test remains running. Earlier private phone CSV was not copied
again. Desktop/live transport checks were not rerun for this Android request.

Use the existing cached Gradle 8.7, Java 21 and SDK/build-tools 34. Before every
attempt check visible builds/compiler activity and RAM/swap pressure; do not stop
others or clear caches. Commands and source coverage are in the
[Android testing guide](../guides/android-testing.md). The latest output root is
`/home/work/.gradle/shiftreport-project-cache/build-output/app/`.

## Remaining limits

Original unknown OEM picker failure remains unreproduced. Controlled results and
provider faults do not exercise every OEM grant path. Independent clients share
one process, not two physical phones; hinges/window constraints and storage faults
are synthetic. Power loss, actual disk exhaustion, every workflow clock change,
full TalkBack, physical split-screen/foldables, other OS versions and release
performance remain outside this evidence. Existing signing/deferred proposals are
in the [roadmap](roadmap.md); keep verification-only limits out of that list.
