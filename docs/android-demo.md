# Android demo workspace

The demo follows the real ward's navigation and patient workflow with a smaller
set of offline features. It opens from the initial page's selected Demo choice.
It never joins or creates a Telegram project.

## Navigation

- **Patients and Dashboard** are the main destinations, using the real ward's
  compact navigation bar/rail. The drawer holds secondary destinations.
- **Patients** uses the real search/count/filter toolbar, patient cards, full
  details and patient/task forms. View Options groups filtering, card layout and
  session sorting. Grouping is omitted from this reduced demo.
- **Dashboard** reuses the real ward metrics and filter actions. Synchronization
  and admin Activity controls are omitted, rather than simulating network work.
- **Shift doctors** groups roster selection and the current demo doctor. Selecting
  another tree-named doctor changes “My patients” and task-completion identity for
  subsequent demo edits; it does not log in or alter a real account.
- **Report review** is a separate destination, with the actionable roster first,
  scope/destination/PDF layout next and uncommon styling under Advanced. The ward's
  report action opens this review step. Demo delivery is exclusively a local PDF
  export through the system file picker; there is no Telegram send/publish action.
- **Settings** groups temporary appearance/font controls and Save-and-next. The
  appearance section reuses the real settings controls and dummy-card preview.
- **Recycle bin, reset and exit** are secondary drawer actions. Reset and exit
  require confirmation; reset restores demo clinical data, while exit discards
  the session and returns to initial setup.

A short, persistent demo strip identifies the workspace. It replaces the repeated
large explanation and avoids mixing appearance controls into dashboard metrics.

## Shared behavior

The demo reuses real patient cards, full-screen forms, structured task editor,
patient details/rounds, roster picker, saved-sort editor, dashboard, theme controls
and PDF renderer. General/Advanced form modes share the same draft and expose labs.
The list offers all shared dashboard filters, compact/two-column cards, brief
individual expansion and immediate bulk expansion. Sorting also determines PDF
order; temporary filtering does not remove patients from the report.

Main-list position, expanded/pinned cards and per-page UI state remain available
when navigating the demo. Edited patients are revealed/highlighted when visible;
filter-hidden results are explained. Task deadline counts refresh during the
session. Width-adaptive bar/rail navigation and the shared fold-aware content
container retain labelled controls and scroll clearance below the report action.

## Isolation and data

`DemoWardViewModel` holds clinical records, roster and the chosen demo actor in
memory. It does not depend on clinical repositories, a settings store, login/session
authorization or Telegram. Presentation choices are local Compose state; real
settings controls receive only callbacks that update that state.

Patients retain hot-beverage names and doctors retain tree names. New tasks remain
unassigned unless explicitly chosen in the form. Saving still checks the demo
patient revision, validates structured tasks and stamps task completion with the
selected demo actor. Existing completion identity/time is retained.

The one explicit file output is a user-requested PDF. Options are prepared before
opening the file picker and kept in the ViewModel across Activity recreation.
Cancellation clears that pending request. Export snapshots the demo records, runs
file/PDF work off the main thread, preserves cancellation, verifies written bytes
and keeps the existing demo label on every exported page. Exit/reset clears pending
export selection; controls prevent ending/resetting during active file export.
Process death starts a fresh clinical demo rather than restoring real project data.

## Source organization

- [Screen coordinator](../android/app/src/main/java/com/hos/rushdpatients/ui/demo/DemoWardScreen.kt):
  navigation, view state, list and shared form/detail callbacks.
- [Drawer and options](../android/app/src/main/java/com/hos/rushdpatients/ui/demo/DemoNavigation.kt).
- [Secondary screens](../android/app/src/main/java/com/hos/rushdpatients/ui/demo/DemoSecondaryScreens.kt):
  report review, roster/actor selection and local preferences.
- [ViewModel](../android/app/src/main/java/com/hos/rushdpatients/ui/demo/DemoWardViewModel.kt):
  memory-only mutations and explicit PDF export.
- [Dummy data](../android/app/src/main/java/com/hos/rushdpatients/ui/demo/DemoData.kt).

## Verification

Source checks cover Kotlin delimiters/imports, complete destination/filter branches,
retained real form callbacks, unchanged PDF write/verification logic, actor-based
completion identity, memory-only dependencies and default-preserving dashboard
reuse. Git whitespace checks also passed, including new source files.

No Gradle, compilation, packaging, device rendering or test-suite work ran for
this reorganization. Source checks do not establish compilation or accessibility
on devices. A desktop demo redesign is outside this change.
