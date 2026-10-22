# Android UI work guide

Use current implementation records rather than the retired review questionnaires.

| Need | Source of truth |
|---|---|
| Current behavior and exact verification limits | [Current state](../docs/current-state.md) |
| Review outcomes and unresolved source findings | [Consolidated review](../docs/reviews/android-review.md) |
| Adopted boundaries and remaining unselected proposals | [UI decisions](../docs/decisions/android-ui-options.md) |
| Planned unfinished implementation | [Roadmap](../docs/roadmap.md) |
| Shared components, profiles and typography | [Design catalog](../docs/design-catalog.md) |
| Build commands and durable cache/toolchain setup | [Build guide](../docs/android-shared-workstation-build-fixes.md) |
| Authorization and safety restrictions | [AGENTS.md](../AGENTS.md) |

## Maintaining the UI

- Screens delegate state/mutations to their ViewModels. Preserve original draft
  revisions, exclusive editor/overlay sessions and explicit conflict review.
- Card-body taps open details; optional header expansion and explicit Edit/Copy/
  Delete/Pin/Expand actions retain their distinct purposes. Keep Add separate from
  the roster-first reviewed report flow and its exact-file/delivery guards.
- Use shared components and runtime style roles. Keep clinical meaning independent
  of accent, density and visual control size; interactive targets stay at least
  48dp and Arabic labels can wrap/grow. Preserve first-layout RTL and fold safety.
- Keep delivery uncertainty visible, fetch advice accessible, and admin destinations
  permission-filtered. UI filtering does not replace service-level authorization.
- Demo uses the production UI with isolated temporary data/preferences and explicit
  demo-labelled local PDF export. Do not construct real clinical/sync dependencies.
- Preserve genuine Tajawal weights, the emblem, About/credits and five-second
  hold/skip splash behavior. Profile/font changes do not rewrite saved clinical text.

## Verification policy

Source checks do not establish compilation, device behavior or performance.
Run Git unstaged/staged whitespace checks after edits. Obtain owner confirmation
before builds or test-suite work under AGENTS.md; then preflight active compilers,
RAM/swap, installed Java, pinned Gradle, SDK and durable dependencies. Prefer the
narrow offline compile in the build guide, with automatic version reservation
disabled. Keep full logs locally and report concise results. Do not change versions,
inspect migrations or package/distribute artifacts merely to verify UI source.
