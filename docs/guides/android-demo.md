# Android demo workspace

The demo follows the real ward's navigation and patient workflow with a smaller set of offline features. It opens from the initial page's selected Demo choice. It never joins or creates a Telegram project.

## Navigation

| Destination | Behavior |
|---|---|
| Patients/Dashboard | Real compact bar/rail, search/count/filter/cards/details/forms/metrics; options group filters/layout/sort, no demo grouping or sync/admin Activity. |
| Shift doctors | Roster plus tree-named actor; changes My patients/new completion attribution, never real login. |
| Report review | Roster first, scope/destination/layout then Advanced; explicit local picker PDF export only, no Telegram publication. |
| Settings | Temporary shared appearance/font controls, dummy preview and Save-next. |
| Recycle/reset/exit | Drawer actions; confirmed reset restores dummy data, confirmed exit returns to setup/discards session. |

Persistent compact demo strip identifies the workspace.

## Shared behavior

Real cards/fullscreen forms/task editor/details/rounds/roster/sort/dashboard/theme/PDF; one draft including labs. Compact/two-column cards, brief individual/immediate bulk expansion. Stored order drives PDF; filters never drop report patients. Navigation retains viewport/expanded/pinned/per-page state; saved patients reveal/highlight or explain hidden filters. Minute task refresh, adaptive/fold-aware labelled controls and report-action clearance.

## Isolation and data

`DemoWardViewModel`: memory-only clinical/roster/actor; no real repository/settings/login/session/Telegram. Beverage patients/tree doctors, optional-owner new tasks, revision/task validation and selected-actor completion stamps preserving existing attribution.

Explicit PDF only: options survive recreation, cancellation clears pending request; snapshot/file work off-main, cancellation preserved, bytes verified, DEMO every page. Reset/exit clears selection and is blocked during export. Process death starts fresh, never loads clinical state.

## Source organization

- [Screen coordinator](../../android/app/src/main/java/com/hos/rushdpatients/ui/demo/DemoWardScreen.kt): navigation, view state, list and shared form/detail callbacks.
- [Drawer and options](../../android/app/src/main/java/com/hos/rushdpatients/ui/demo/DemoNavigation.kt).
- [Secondary screens](../../android/app/src/main/java/com/hos/rushdpatients/ui/demo/DemoSecondaryScreens.kt): report review, roster/actor selection and local preferences.
- [ViewModel](../../android/app/src/main/java/com/hos/rushdpatients/ui/demo/DemoWardViewModel.kt): memory-only mutations and explicit PDF export.
- [Dummy data](../../android/app/src/main/java/com/hos/rushdpatients/ui/demo/DemoData.kt).

## Verification

Memory-only demo mutations, stale revisions, task attribution and local-only actions have JVM regression coverage. Source/layout checks are not complete demo-device/PDF acceptance. [Results](../status/current-state.md#verification-and-limits) · [Remaining checks](../status/testing-handoff.md).
