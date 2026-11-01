# Android demo workspace

Choose Demo on setup to use the app screens with isolated temporary data, without joining or creating a project.

## Shared screens

| Destination | Shared behavior |
|---|---|
| Patients/Dashboard | Production `WardWorkspace`: navigation, search/filter/cards/details/forms/tasks/sort/dashboard, including simulated transfer controls and canonical pending counts. |
| Doctors | Production `DoctorsWorkspace`: search/filter/cards/Add/Edit/delete/team management and registry review. Registry Publish/Fetch are memory-only; CSV import/export are disabled. |
| Report | Production `ReportWorkspace`: shift doctors, General/supervisor selection, PDF/text preview, readiness links, final confirmation and receipt display. Publish/send only update the in-memory snapshot/receipts. |
| Assignments | Production `AssignmentsExportWorkspace`: sidebar action, preview then save, no roster/upload prerequisite; shared `PdfAssignmentRenderer` uses A5 portrait with three team tables per row, dummy data and DEMO on every page. |
| About | Production `IntroAboutScreen`: logo, app/version information and credits. |
| Appearance | Production `SettingsAppearanceSection` with palette/font/reduced-motion controls and no patient preview; only accent/appearance persist locally. Connection/security/project administration is outside the demo. |
| Drawer | Production `WardDrawer` with a demo-only session slot for actor selection, download Undo, reset and exit. |

The ward, report and doctor screens display demo identification. Report screens explicitly state that transfer/delivery is simulated. The screens, drawer layout and exhaustive drawer dispatcher are shared. Changes inside these shared components apply automatically in both modes. New destinations must supply explicit navigation callbacks; `onOpenAssignments` has no silent default. New data/action contracts still require a memory adapter or simulated result; UI sharing cannot invent those behaviors.

## Isolation and lifecycle

`DemoWardViewModel` injects `InMemoryDemoWardRepository`, pure text/PDF builders (including the assignments renderer) and an explicit PDF saver. It never constructs production clinical/settings/session/Telegram services. `WardController`, `ReportController` and `DoctorsController` select production or demo operations below the shared UI; production authorization and durable delivery rules stay in their existing ViewModels/services.

The 50 fictional food/drink patients come from `samples/synthetic-patients-50-expanded.csv`; tree doctors, actor/roster/teams, drafts, fake remote snapshot and delivery receipts exist only in memory. Entered doctor PIN/Telegram identity values are discarded. Fetch uses the fake published snapshot; pending replacement uses the production chooser/review dialogs, with local demo Undo. Reset/exit/process death discards the clinical session. Demo accent and appearance are the explicit cosmetic exception: `DemoAppearanceStore` keeps only these two enum choices in private, unencrypted `demo_appearance` preferences, with IO off-main. They are loaded before demo rendering and survive exit/reset; no clinical, credential or live-project settings enter this file. The day label refreshes at08:30/foreground; report review captures its own day.

## Explicit local files

PDF preview uses the production renderer with **DEMO on every page**, including Cards. Save/Share copies the opened general preview; supervisor Save requires each supervisor preview to have been opened. PDF generation/copy runs off-main; private previews are removed on invalidation/reset/exit/ViewModel disposal. User-saved copies remain. Names-only assignments save as `Assignments_YYYYMMDD_DEMO.pdf`, using the captured review day, including with an empty shift roster. Save checks the current dummy snapshot and the hash of the opened preview; regeneration invalidates the prior opening. No CSV exports, real admin operations, real upload or Telegram delivery.

## Keeping new features shared

Implement presentation and user flows in controller-driven workspaces, then let the live route supply its Hilt controller and the demo supply a memory controller. Keep destination dispatch in `WardNavigation.openDrawerAction`, not a separate demo action switch. Keep PDF layout in renderers shared by both modes; production authorization wraps those renderers and is never disabled for demo. Demo must use only dummy snapshots and every-page DEMO labels. Network/session/persistence operations stay in the production adapters; the demo simulates only their visible results. Update memory adapters when action/state contracts change, rather than copying screens or hiding supported local actions.

## Source ownership

- [Coordinator](../../android/app/src/main/java/com/hos/rushdpatients/ui/demo/DemoWardScreen.kt) and [drawer adapter](../../android/app/src/main/java/com/hos/rushdpatients/ui/demo/DemoNavigation.kt): session navigation, appearance and reset/exit.
- [Ward adapter](../../android/app/src/main/java/com/hos/rushdpatients/ui/demo/DemoWardViewModel.kt), [report adapter](../../android/app/src/main/java/com/hos/rushdpatients/ui/demo/DemoReportController.kt), [doctor adapter](../../android/app/src/main/java/com/hos/rushdpatients/ui/demo/DemoDoctorsController.kt).
- [Memory repository](../../android/app/src/main/java/com/hos/rushdpatients/ui/demo/InMemoryDemoWardRepository.kt) and [dummy data](../../android/app/src/main/java/com/hos/rushdpatients/ui/demo/DemoData.kt).

The sample is embedded in generated `DemoSamplePatients.kt` and decoded once using the current production CSV codec; no runtime file/Room/network access is added. Run `python3 samples/generate_android_demo.py` after editing the fictional expanded CSV, then rebuild normally. All 50 rows retain the sample demographics, stored dates, clinical text, badges/tasks and priority. Lab groups use the sample’s explicit panel headings and retained results:104 panels have fictional26–27 October2026 dates and34 intentionally have no date; the same add/remove/reorder/calendar editor is used in live/demo. Treatment examples contain mostly individual medicine/dose lines followed by a few short notes; they are fictional layout data. Demo supplies its own patient IDs and fictional resident/supervisor assignments, spread approximately equally across three linked resident/supervisor teams (17/17/16 patients). The source checksum is recorded in the generated file.

## Evidence

26 October PPA-LX2/API29 focused tests passed selected memory-only edit/fetch/Undo/reset, discarded credential inputs, shared report/assignments navigation/recreation, simulated receipts and exact reviewed-byte Save/Share with stale-data rejection. Five dummy PDFs/31pages passed rendering, every-page DEMO and continuation-content checks with92 valid Fit links; changed-assignment marking and sampled visuals checked. Xodo read-grant launch passed. These selected results do not establish physical printing, all viewer/glyph behavior or full UI-matrix acceptance. [Current evidence](../status/current-state.md#verification-and-limits) · [Remaining checks](../status/testing-handoff.md).
