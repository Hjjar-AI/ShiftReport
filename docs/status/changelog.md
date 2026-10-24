# Completed work

Feature summaries only; [current behavior/verification](current-state.md),
[remaining work](roadmap.md) and [optional decisions](../decisions/android-ui-options.md)
have separate homes. Source completion is not runtime verification.

## Clinical and security

- Android project creation now offers a confirmed existing-pin override, tied to the
  reviewed group/configuration and pin, with live group-admin checks below the UI.
  New bootstrap rechecks the expected pin; previous group content remains intact.
  Large-text clipping was fixed locally with a scrollable warning and fixed actions.

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

- Broad isolated JVM/Room/UI/transport coverage now includes structured tasks,
  authority/revision races, encrypted storage/join/backup, file interop, process death,
  publication/delivery recovery, report review/chunking, PDF pages and real picker paths.
- Shared delivery fixtures retain explicit non-admin actors; six regressions add
  resolution/permission write failures, repeat recovery, concurrent routes and authority.
- Verified fixes include rollover busy guards, false pin receipts, card timing,
  retained background drafts/picker keys and the setup warning at 200% typography.
- CSV/overflow/cancellation/password cleanup and long PDF Unicode boundaries were
  hardened. Splash recreation retains its deadline; debug workers stay isolated.
- Desktop storage/publication/authority/transport suites, seven selected mutants,
  synthetic live transport and bidirectional Android file fixtures passed.

[Exact results/evidence](current-state.md#verification-and-limits) ·
[Remaining checks](testing-handoff.md) · [Commands](../guides/android-testing.md).
No versions, dependencies or schemas changed.

## Documentation

Consolidated test work into one remaining-check table, current results and compact
execution guides; removed duplicated histories and stale pending claims. Pruned
older successful logs while retaining XML, reports, receipts, captures, failures
and latest JVM/device success logs. Build instructions and private notes remain intact.
