# Android UI work guide

[Behavior/evidence](../status/current-state.md) · [Tokens/components](../reference/design-catalog.md) · [Unselected choices](../decisions/android-ui-options.md) · [Implementation roadmap](../status/roadmap.md).

- ViewModels own mutations; retain exclusive drafts/overlays, clinical last-save-wins and privileged [write/review guards](../status/current-state.md#clinical-write-safety).
- Card tap→details, long press→guarded Edit. Preserve expansion/copy/delete/stored order, separate Add, roster-first exact-artifact reporting, visible UNKNOWN/recovery and service authorization.
- Reuse RTL/wrapping/≥48dp/fold-safe components; keep feature sections local and state with existing owners. Preserve semantic colors/full clinical text/Tajawal/emblem/credits/splash and [demo isolation](android-demo.md).

[AGENTS](../../AGENTS.md) owns authorization/build preflight/evidence rules; [build guide](android-build.md) owns commands/cache/signing. Source checks do not establish runtime/accessibility/performance.
