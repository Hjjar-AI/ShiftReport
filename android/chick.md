# Android UI work guide

This replaces the old static-review proposal list with the owner's selected scope,
current implementation links and concrete acceptance checks. Historical assertions
are not treated as current defects without checking source.

## Start here

| Need | Source of truth |
|---|---|
| What is implemented and verified? | [Current state](../docs/current-state.md) |
| What did each of the 88 original findings mean? | [Finding dispositions](../docs/reviews/android-deep-ui-review-validation.md) |
| Which optional changes were selected, and what still needs an answer? | [UI decisions](../docs/decisions/android-ui-options.md) |
| What implementation remains? | [Roadmap](../docs/roadmap.md) |
| Shared components, colors and typography | [Design catalog](../docs/design-catalog.md) |
| Efficient cached verification | [Build guide](../docs/android-shared-workstation-build-fixes.md) |
| Contributor scope/safety rules | [AGENTS.md](../AGENTS.md) |

## Selected implementation

The approved work is one explicit modal state; explicit card state/actions;
grouped navigation and shared admin route presentation; shared production/demo
ward screens backed by isolated injected repositories; chips, color containers,
button styles, dividers and reusable content cards; consistent compact spacing;
genuine Tajawal Medium/Bold roles; compact ward controls; adaptive dashboard tiles;
and Arabic icon tooltips with shared directional handling.

Keep the same patient fields, revision/conflict checks, task completion attribution,
report review and exact-file/delivery safeguards. Demo must use only memory and
explicit local demo-labelled PDF export. No optional item in the decision file is
automatically authorized merely because it was recommended.

## Acceptance checklist

- Clinical dialogs are mutually exclusive; rotation retains their draft/baseline.
- Tap opens patient details; Edit/Copy/Delete/Pin/Expand retain distinct actions.
- Activity/admin destinations remain permission-filtered; service authorization remains.
- Demo uses the production patient grid, dashboard, options, forms and details,
  without constructing clinical repositories or exposing live fetch/publication.
- Urgent/overdue tasks and all ward totals retain their filter actions. Metric
  tiles stack when two columns would crowd Arabic text at large font sizes.
- Controls preserve search, presets, advanced filters and reset while shortening
  the header; delivery warnings and the requested fetch reminder remain visible.
- Clinical badge severity is unchanged; informational chips and interactive
  selected filters remain distinct. Buttons/icon controls retain 48dp targets.
- Tooltips and TalkBack share Arabic action labels. Directional mirroring happens
  once; About/logo/credits and native first-layout RTL remain preserved.
- Fonts use real bundled weights. No fabricated Tajawal SemiBold file or changed
  font family; source/license/provenance stay documented.

## Verification policy

Use installed Java, the pinned cached Gradle and existing SDK components. Prefer
an offline debug compile/resource check over packaging. Disable automatic app
version reservation, retain full logs locally, and read only concise failures.
If tools/dependencies are missing, request durable one-time installation instead
of repeatedly downloading temporary toolchains. See the exact current result in
[Current state](../docs/current-state.md); source checks alone do not prove rendered
Arabic, touch/TalkBack/fold behavior or performance.
