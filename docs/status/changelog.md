# Completed work

Feature summaries only; [current behavior/verification](current-state.md),
[remaining work](roadmap.md) and [optional decisions](../decisions/android-ui-options.md)
have separate homes. Source completion is not runtime verification.

## Clinical and security

- Added shared debug-keystore selection for both Linux accounts, preserving the
  existing certificate and separate caches/release signing.
- Protected file-only join, optional authenticated Telegram encryption, safe same-project
  reconnect, clinician PIN/biometrics, service authorization and storage-key preservation.
- Revision/audit/pending saves, full-screen retained drafts, per-field labs/tools,
  structured optional-owner tasks, stamped completion, recycle/restore and whole-shift copy.
- Shared sort/team/cover/CSV/report rules, manual snapshot synchronization and reviewed
  exact-artifact publication/delivery with durable UNKNOWN and duplicate-copy controls.
- Classic/A5 reports, admin A4 names-only team PDFs, local exports and isolated demo PDFs.

## Interface and desktop

- Shared Android/demo workspace, compact adaptive RTL navigation, genuine Tajawal,
  independent appearance/accent, local profiles/density, reduced motion and retained branding.
- UI reviews fixed draft/input/system-bar/clipboard paths, shared components, stable dock
  clearance/task time, grid reuse/placement, whole-card expansion, severity notices,
  PIN submission and launch colors; task board adds revision-safe completion/Undo.
- Selected O2/O6/O10/O11/O13/O16 and preview portion of O23: chevron/tab motion,
  ultra compact sticky filters, card age/sex, drawer cleanup/Settings search,
  guarded RTL rounds swipes, confirmed pull-to-fetch and broader production previews.
- Standalone encrypted Windows/Linux desktop: shared clinical/protocol rules, reviewed
  administration/recovery/reports, logical Arabic editing and local PIN/demo.
  Search/resize debouncing, retained widgets, sidebar shortcuts, clinical stripes,
  native card menus and guarded keyboard shortcuts improve presentation.

## Regression coverage and hardening

Added 33 JVM and 30 device cases: report-review scope and Room report assembly,
long-message boundaries, virtual-clock rate limiting, concurrent snapshots,
revoked authority in open import/export/report workflows, export/uncertain-delivery
process death, task-heavy PDF pages, 200% font/48dp controls, native-window journeys
and an opt-in actual document picker. Fixed oversized report chunks to preserve
escaped Unicode/formatting and whole links within message limits; indivisible
oversized links fail explicitly. The rate limiter retains its Hilt constructor
with a clock seam for deterministic tests. Debug crash export files are isolated.
Added [focused execution and external-device procedures](../guides/device-coverage.md).
Offline production/JVM/device-source compilation passed, followed by **98 focused
JVM cases/12 classes**, zero failures/errors/skips, including all 33 new JVM cases.
The main focused device run passed **49 cases**, zero failures/errors and one
expected opt-in skip, on PPA-LX2/Android 10. Native selection exposed a lost-result
boundary when privacy removed NavHost: retaining its controller did not retain
the screen's saveable picker registration keys. A session-owned saveable-state
holder now retains these while clinical content stays hidden; sign-out discards
both owners. All **22 affected UI cases passed** after the fix, and the real native
picker/preview/isolated commit passed separately with assisted selection. This
verifies **51 distinct device cases**, including all 30 additions; earlier native
timeouts remain archived. Visually reviewed the 11 new task-heavy PDF pages and
native report samples; physical two-phone/fold/TalkBack limits remain.
Logs/XML: ignored `local/verification/coverage-2026-10-24/`. The full-run
evidence below applies to the preceding snapshot. Dependencies, versions and
schemas are unchanged.

The earlier 23–24 October expansion added **249 JVM and 78 device cases**; its verified totals were
**625 JVM methods/81 classes and 253 device methods/50 classes**. Coverage includes
controlled picker results, independent clients, response-loss/storage/clock
boundaries, actual root/navigation journeys and constrained/fold-model UI.

Execution confirmed and verified fixes for rollover's nested busy guard, ignored
false initial pin receipts, the first individual card animation and editor loss
across background privacy locking. Duplicate/revision/authority checks remain;
locked clinical screens still leave composition and sign-out discards navigation.
Fixtures now use the actual workspace/text/focus/lifecycle contracts; excluded lazy
items are checked for visibility. Biometric native-node cleanup remains compatible
with old Android without deprecation warnings. The final full run passed **625 JVM tests** and **251 device cases**, with zero
failures and two expected device skips; physical fingerprint success passed
separately. Only the optional private-CSV fixture remains unrun.
[Exact evidence and remaining limits](current-state.md#verification-and-limits).
No dependency/version/schema changes.

Expanded Android coverage for destructive-reset review/failure boundaries, real
transaction process death, trigger-driven rollback, long clinical sequences,
production ward/login/settings/import orchestration, adversarial CSV input,
provider failures, Android–desktop encrypted files, native PDF wrapping/rendering,
clipboard cleanup and Activity splash recreation. Added desktop encrypted storage,
publication/authority/receipt/transport suites and seven isolated mutation checks.
Synthetic live Telegram upload/download, two-client edit ordering and report checks
passed; the owner's physical biometric success callback also passed.

Regression-driven fixes reject malformed CSV headers/quotes/revisions and bundle
boundary injection, reject backup revision overflow, preserve settings-restore
cancellation/password cleanup, guard repeated saves/exports and preserve long PDF
text/surrogate pairs. Splash launch state retains its deadline across recreation;
secondary processes skip main-process WorkManager/cache startup. Patient scrolling
isolates dock visibility from the clinical workspace and removes redundant card
animation layers. Native-clock profiling is separate from Compose interaction tests.

[Coverage and commands](../guides/android-testing.md) ·
[Deep scenarios/limits](../guides/deep-testing.md) ·
[Measured results](current-state.md#verification-and-limits).
No versions, dependencies, schemas or migration files changed.
Corrected process-death and scrolling suites passed on the connected phone.
[Verification handoff](testing-handoff.md).

## Documentation

Consolidated status/history/rationale/choices; absorbed review ledgers and `full.md`.
Grouped documentation under `docs/{guides,reference,status,decisions}` with updated
links; root/desktop READMEs remain entry points.
Retained source concerns and exact build/runtime limits live in Current state.
Merged duplicate build/cache instructions and replaced superseded failure history
with targeted diagnostics; retained operational guides/commands and consolidated
shared debug signing, production signing and optional read-only snapshot setup.
Latest UI changes have static checks only; consult Current state before reusing
earlier build evidence.
