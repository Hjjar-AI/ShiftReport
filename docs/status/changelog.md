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

## Android regression suite

Expanded from 8 to 80 test classes (55 JVM/25 device). Coverage includes
clinical/tasks, doctors/import and live repository authorization, auth/reconnect,
codecs/sorting/mappers, intercepted sync/recovery/Undo, reviewed publication and
report/announcement delivery, PDF artifact guards/native rendering, editor/PIN/
splash interactions, isolated SQLCipher/session persistence, crypto and backup.
The latest expansion adds 38 JVM and 58 device cases to the earlier 227/52 source
snapshot. All **265 JVM and 110 connected device tests pass**, with zero failures/
errors/skips; device execution used the owner's PPA-LX2 (Android 10). A single
incomplete Undo fixture was corrected and the complete device suite rerun. Repaired
obsolete patient/grouping/DAO fixtures and D8-incompatible spaced device names.

Regression-driven fixes invalidate patient/shift drafts after backup restore,
validate live clinical assignments transactionally at save/restore/rollover/backup,
roll back rejected restore pending state, wipe join/backup passwords on every exit,
and propagate cancellation from Telegram login's initial drain.
[Coverage and commands](../guides/android-testing.md) ·
[Verification evidence/limits](current-state.md#verification-and-limits).
No versions, schemas or migration files changed.

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
