# Optional UI decisions

Unselected choices only; existing behavior remains the default. Pick IDs or narrower parts. Completed selections live in [Changelog](../status/changelog.md); current behavior, safety boundaries and verification limits live in [Current state](../status/current-state.md).

| ID | Optional choice |
|---|---|
| O1 | Fades/slides between panes, routes, details and report steps. |
| O2 | Additional haptics or selection-border interpolation; chevron/tab motion is complete. |
| O3 | Skeleton cards instead of the labelled spinner; static under reduced motion. |
| O4 | Animate divider keyboard/reset changes; keep dragging direct. |
| O5 | Animate list removal/group collapse with current-toolchain/reduced-motion support. |
| O7 | Dashboard attention hero/task progress; choose Android/desktop separately. |
| O8 | Collapse Add presentation with scrolling; retain a labelled 48dp target. |
| O9 | Cap dashboard/settings reading width on wide screens. |
| O11 | Regroup Settings into fewer top-level areas; search/drawer cleanup is complete. |
| O12 | Tappable earlier report steps with existing review/validation gates. |
| O14 | Undo without initial reversible-delete confirmation; optional per-task draft Undo. |
| O15 | Labelled one-tap priority toggle. |
| O17 | Task/priority swipes with Undo/revision guards and optional haptics. |
| O18 | Sunken/raised surfaces and quieter borders, preserving clinical colors. |
| O19 | Bundled lab font and/or desktop Tajawal; assess Arabic coverage first. |
| O20 | Automatic biometric prompt once on resume with PIN fallback. |
| O21 | Optional compact desktop mouse controls. |
| O22 | Dock desktop details/dashboard beside patients on wide windows. |
| O23 | Automated screenshot matrix; production previews are expanded. |

[AGENTS.md](../../AGENTS.md) governs verification/version authorization; O23 requires separate explicit test work approval. Voice/templates remain deferred in [Roadmap](../status/roadmap.md).
