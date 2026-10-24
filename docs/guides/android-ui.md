# Android UI work guide

Use current implementation records rather than the retired review questionnaires.

| Need | Source of truth |
|---|---|
| Current behavior and exact verification limits | [Current state](../status/current-state.md) |
| Clinical write safety | [Current state](../status/current-state.md#clinical-write-safety) |
| Adopted boundaries and remaining unselected proposals | [UI decisions](../decisions/android-ui-options.md) |
| Planned unfinished implementation | [Roadmap](../status/roadmap.md) |
| Shared components, profiles and typography | [Design catalog](../reference/design-catalog.md) |
| Build commands and durable cache/toolchain setup | [Build guide](android-build.md) |
| Authorization and safety restrictions | [AGENTS.md](../../AGENTS.md) |

## Maintaining the UI

- ViewModels own mutations; retain original revisions, exclusive draft/overlay owners and conflict review.
- Card body opens details; header expansion/Edit/Copy/Delete/Pin remain explicit. Add stays separate from roster-first reviewed reporting/exact-artifact delivery.
- Shared tokens/components, Arabic RTL/wrapping/fold safety and ≥48dp; clinical meaning independent of accent/density/control size. Keep UNKNOWN visible/fetch advice accessible/admin filtering plus service authorization.
- Demo reuses real UI with temporary isolated data/preferences and explicit labelled PDFs; no clinical/sync dependencies.
- Preserve Tajawal,emblem/About and five-second hold/skip splash; styling never rewrites clinical text.

## Verification policy

[AGENTS.md](../../AGENTS.md): separate build/test approval, per-build active-work/RAM/swap/tool/cache preflight; narrow offline compile with version reservation disabled. Keep logs local; Git whitespace checks after edits. Static checks do not prove runtime/performance; no assumed migrations/version changes/packaging/distribution.
