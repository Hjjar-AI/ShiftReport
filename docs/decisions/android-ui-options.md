# Android UI decisions

Current implementation belongs in [Current state](../current-state.md), completed
work in [Changelog](../changelog.md), and review dispositions in the
[Consolidated Android review](../reviews/android-review.md). This register retains
design choices and the few unselected proposals; it is not an implementation backlog.
The old 43-question ledger has been retired because many answers became code,
standing repository rules or superseded proposals.

## Adopted choices and boundaries

| Choice | Boundary |
|---|---|
| Shared production/demo UI and retained editor state | Demo uses isolated memory state. Draft baselines/review guards survive Activity recreation; process-death recovery is not promised. |
| Compact ward/report workflow and shared presentation primitives | Card-body taps open details; explicit clinical actions and reviewed report sending remain. Keep Arabic/RTL, 48dp targets, clinical severity and admin-only destinations. |
| Local profiles, density, control sizes and reduced motion | Persist in encrypted local settings; demo stays temporary. Keep appearance/accent/PDF settings separate. No clinical or interchange schema change. |
| Compact Add and optional auto-hide/header expansion | Navigation returns on upward scrolling or either boundary and remains visible during touch exploration. Header expansion is optional; body-tap behavior stays explicit. |
| Bundled Tajawal and larger minimum text | Use genuine weights, 13sp small labels and 0.92 Small scaling. Arabic/device rendering remains unverified. |
| Branding, access and data safety | Preserve logo/About/splash behavior, native PIN input, revision checks, encryption and below-UI authorization. Do not turn these requirements into open redesign questions. |

## Unselected proposals

| Proposal | Status |
|---|---|
| Larger component-preview matrix or debug visual catalog | Not selected. Existing previews remain; this is an optional development tool, not a device-verification claim. |
| Desktop UI-font replacement, adaptive wrapping or Android/desktop density/font-scale parity | Not selected. These need their own platform scope and Arabic rendering assessment; no split-pane desktop redesign is planned. |

Broad tone/emphasis/surface remapping, universal scaffolds, package/toolchain
renaming or upgrades, speculative state/performance rewrites and alternate splash/
PIN/navigation redesigns have been removed from the proposal list. Their removal
is scope cleanup, not implementation.

## Verification authorization

[AGENTS.md](../../AGENTS.md) is authoritative: obtain explicit confirmation before
builds/compilation or extensive test runs. Test-suite inspection, modification and
execution need their own explicit authorization; confirmation to run existing tests
does not authorize creating or modifying tests. Keep the resource/toolchain preflight,
offline caches and disabled automatic version reservation for verification builds.

Operational sources: [UI work guide](../../android/chick.md),
[Design catalog](../design-catalog.md), [Build guide](../android-shared-workstation-build-fixes.md).
