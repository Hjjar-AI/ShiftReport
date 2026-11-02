# Android demo workspace

Choose Demo on setup for shared app screens with isolated dummy data; no project join/create is needed.

## Shared screens

| Destination | Shared behavior |
|---|---|
| Patients/Dashboard | `WardWorkspace`: navigation, search/filter/cards/details/forms/tasks/sort/dashboard, simulated transfers and canonical pending counts. |
| Doctors | `DoctorsWorkspace`: search/filter/cards/Add/Edit/delete/teams/registry review; memory-only Publish/Fetch, no CSV import/export. |
| Report | `ReportWorkspace`: shift doctors, General/supervisor choice, PDF/text/readiness, confirmation/receipts; publication/delivery are simulated. |
| Assignments | `AssignmentsExportWorkspace`: sidebar Preview→Save without roster/upload prerequisites; A5 portrait, three team tables per row. |
| About | `IntroAboutScreen`: logo, app/version and credits. |
| Appearance | `SettingsAppearanceSection`: palette/font/reduced motion, no patient preview. Only accent/appearance persist. |
| Drawer | `WardDrawer`: demo session slot for actor, Fetch Undo, reset and exit. |

Ward/report/doctors identify demo mode; reports label simulated delivery. Shared UI changes reach both modes, including the fixed two-column A5 cards below full-width demographics/badges. Setup/login, connection/security and project administration remain outside demo. New actions need memory adapters; new destinations need explicit callbacks (`onOpenAssignments` has no default).

## Isolation and lifecycle

`DemoWardViewModel` uses `InMemoryDemoWardRepository`, pure text/PDF builders and an explicit PDF saver; never production clinical/settings/session/Telegram services. `WardController`/`ReportController`/`DoctorsController` choose operations below shared UI; live authorization/durable delivery stay in production ViewModels/services.

Fifty food/drink patients come from `samples/data/patients.csv`. Tree doctors, actor/roster/teams, drafts, remote snapshot and receipts are memory-only; entered doctor PIN/Telegram identities are discarded. Fetch uses the fake snapshot and production replacement/review dialogs with local Undo. Reset/exit/process death discards the session. Only `DemoAppearanceStore` persists accent/appearance enums in private, unencrypted `demo_appearance` preferences, off-main and loaded before rendering; these survive reset/exit. No clinical/credential/live settings enter that file. The day label refreshes at 08:30/foreground; review captures its own day.

## Explicit local files

Production renderers put **DEMO on every page**, including Cards/assignments. Save/Share copies the opened General preview; each supervisor preview must be opened before its Save. Save checks the current dummy snapshot and opened-preview hash; regeneration invalidates that opening. Generation/copy runs off-main; invalidation/reset/exit/ViewModel disposal removes private previews, never user-saved copies. Assignments use the captured review day in `Assignments_YYYYMMDD_DEMO.pdf`, including with an empty roster. No CSV export, real administration/upload/Telegram delivery.

## Keeping new features shared

Use controller-driven workspaces: live supplies its Hilt controller, demo a memory controller. Keep exhaustive drawer dispatch in `WardNavigation.openDrawerAction`, PDF layout in shared renderers and network/session/persistence in production adapters. Preserve live authorization; demo simulates visible results with dummy snapshots. Update memory adapters when contracts change; reuse screens and supported local actions.

## Source ownership

- [Coordinator](../../../android/app/src/main/java/com/hos/rushdpatients/ui/demo/DemoWardScreen.kt) and [drawer adapter](../../../android/app/src/main/java/com/hos/rushdpatients/ui/demo/DemoNavigation.kt): session navigation, appearance and reset/exit.
- [Ward adapter](../../../android/app/src/main/java/com/hos/rushdpatients/ui/demo/DemoWardViewModel.kt), [report adapter](../../../android/app/src/main/java/com/hos/rushdpatients/ui/demo/DemoReportController.kt), [doctor adapter](../../../android/app/src/main/java/com/hos/rushdpatients/ui/demo/DemoDoctorsController.kt).
- [Memory repository](../../../android/app/src/main/java/com/hos/rushdpatients/ui/demo/InMemoryDemoWardRepository.kt) and [dummy data](../../../android/app/src/main/java/com/hos/rushdpatients/ui/demo/DemoData.kt).

`DemoSamplePatients.kt` embeds the CSV and checksum; the production codec decodes it once without runtime file/Room/network access. After fictional CSV edits, run `python3 samples/generators/generate_android_demo.py`; rebuilding requires [approval](../../../AGENTS.md). All 50 rows retain demographics/dates/text/badges/tasks/priority. Explicit lab headings/results include 104 panels dated 26–27 October 2026 and 34 undated; live/demo share add/remove/reorder/calendar controls. Medicine/dose lines and short treatment notes are fictional layout data. Demo assigns its own IDs and three linked resident/supervisor teams (17/17/16 patients).

## Evidence

26 October, PPA-LX2/API29: selected edit/Fetch/Undo/reset, discarded credentials, shared navigation/recreation, simulated receipts and exact-byte/stale-reject Save/Share passed. Five dummy PDFs/31 pages passed rendering, every-page DEMO, continuation content and 92 Fit links; changed-assignment marking, sampled visuals and Xodo read-grant launch passed. This predates later edits and does not establish printing/all viewers/glyphs/full UI acceptance. [Current evidence](../../status/current-state.md#verification-and-limits) · [Remaining checks](../../status/testing-handoff.md).
