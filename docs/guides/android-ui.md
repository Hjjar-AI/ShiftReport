# Android UI work guide

[Behavior/evidence](../status/current-state.md) · [Tokens/components](../reference/design-catalog.md) · [Unselected choices](../decisions/android-ui-options.md) · [Implementation roadmap](../status/roadmap.md).

- ViewModels own mutations; preserve exclusive drafts/overlays, clinical last-save-wins and privileged review guards. [Write safety](../status/current-state.md#clinical-write-safety).
- Card tap→details, long press→guarded Edit; explicit expansion/copy/delete/stored-order actions. Add separate from roster-first reviewed reporting/exact-artifact delivery; preserve visible UNKNOWN/recovery and service authorization.
- Reuse shared RTL/wrapping/≥48dp/fold-safe components and semantic colors; retain full clinical text, Tajawal/emblem/credits/splash behavior. Demo reuses real UI with memory-only data and explicit labelled PDFs.

[AGENTS](../../AGENTS.md) owns authorization/build preflight/evidence rules; [build guide](android-build.md) owns commands/cache/signing. Source checks do not establish runtime/accessibility/performance.
