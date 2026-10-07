# ShiftReport completed work

Completed features only. Technical details and verification limits: [currentState.md](currentState.md). Remaining work: [workRemains.md](workRemains.md). Design basis: [designAndArchitecture.md](designAndArchitecture.md).

## Setup and protection

- Hospital-independent configuration with Demo, Join, and Create flows. Interactive offline demo uses hot-beverage patients/tree-named doctors, shared editing/tasks/cards/sorting, dashboard, roster and appearance previews, and clearly labelled local PDF export; demo clinical data stays in memory.
- File-only joining through password-protected `.srjoin.json`, importing connection settings, topics, and the optional data key. Join export now uses an encrypted JSON wrapper, private staged ciphertext with a saved random ID, failed-file cleanup, and byte-for-byte write verification (static checks only).
- P2: administrator token replacement with BotFather revocation guidance; file-based reconnect in Settings and login. Candidate credentials are verified before saving; project/key/topic changes are blocked to protect local data. Updated credentials are shared through new join files.
- Optional AES-GCM Telegram-data encryption with a random shared key stored in protected settings/join files. Records decrypt locally; text reports use encrypted documents. PDFs remain readable.

## Patients and tasks

- Full-screen add/edit/copy with compact fields, fixed Save/Cancel, shared General/Advanced drafts (including labs), validation, diagnosis examples, numeric admission/PIN keyboards, birth-year «أو» age, and custom badges. Native/Compose RTL is set before the initial form layout. Editing puts clinical fields first and personal details last; new-patient entry reverses that order.
- Structured tasks with priority, time-picker deadlines, completion history, and optional assignment. **Tasks start unassigned; no user acknowledgment is required.**
- Per-patient pending/overdue counts, ward totals/filters, and assigned-task inclusion in My patients. Overdue is included in pending.
- Tasks persist through sync, backups, conflict review, and reports. Rollover carries unfinished tasks and original deadlines; reassignment clears owners, and copies start without tasks. Database schema 5; patient CSV 7; doctor CSV 1.

## Navigation and accessibility

- Unified neutral light/dark themes with independent appearance/accent settings, shared typography/spacing/shapes, distinct teal/green/blue/gold/purple accents across controls and headings, consistent badge severity colors, and neutral diagnosis categories.
- Simplified collapsed cards and Settings decoration; moved uncommon PDF options under Advanced. Individual card expansion has brief transitions; bulk expansion remains immediate and applies explicit expand/collapse targets before preference IO.
- Patients-first navigation with Dashboard and admin-only Activity tabs, compact 48dp bottom navigation, organized drawer/settings with compact 48dp drawer rows and a two-column identity/shift header, unified search/filter/sort controls, and live data-health status.
- Compact/comfortable cards, pins, individual/global expansion, direct editing, and tabbed patient details/history. Activity retains search, filters, scroll positions, and cached results; patient details retain individual history.
- Adaptive list/detail panes, resizable dividers, fold-safe layouts, and retained selection/scroll state.
- RTL navigation, wrapping labels, 48dp controls, TalkBack actions, accessible status announcements, and stable clinical colors. New accent text pairs passed a static contrast check (minimum 6.28:1); rendered accessibility remains unverified.

## Data correctness and synchronization

- Repository transactions combine patient/doctor mutations, pending-sync state, and actor audit. Stale edits retain drafts for explicit review and a separate Save.
- Live admin authorization protects privileged operations. Doctor CSV import previews/rechecks changes, excludes PINs, and preserves protected identities and clinical references. Doctor CSV export stages an encrypted snapshot before the picker, restores its random ID after recreation, verifies saved bytes, and cleans up failures.
- Patient/doctor three-way merging, explicit conflicts, recovery snapshots, publication journal, and protection of unsent/in-flight edits. Doctor identity changes invalidate old local PINs.
- Encrypted backups prepare before the picker without retaining its password; backup/doctor exports use private encrypted staging and verified writes. Patient CSV validates streams, verifies saved bytes before publication, and removes failed files. Static checks only for this export hardening.
- Lifecycle-aware connectivity and persistent sync states. Telegram last-write-wins remains the accepted remote-publication model.

## Reports and presentation

- Readiness sheet leads to explicit report review; shift doctors can be selected at the top of preview without a fixed maximum; long rosters wrap/paginate in PDF and split safely in text. Snapshot checks require renewed confirmation when reviewed content changes, with supervisor-specific partial-delivery handling.
- Classic/Elegant Rows PDFs include clinical fields, badges, priorities, and tasks; complete rows stay together where possible. PDFs omit unassigned/routine-priority labels and patient edit metadata while retaining tasks and clinical content. Preview/export uses current snapshots, with shared CSV export logic. Sorting saves actual patient positions; CSV/text/PDF share patient ordering. The compact «إرسال تقرير» button retains readiness/review/confirmation.
- Earlier splash rendering, a visible Arabic countdown tied to the saved 10-second deadline, automatic version on both Splash/About, dismissal on any tap or expiry, preserved white-field emblem, and owner-edited shared Splash/About text: الطبيب محمد علي حجار, the emblem, technical contributors أيهم شيخة/محمد زاهر شقير under «الأطباء», and the initial Excel/report credit to الطبيب الاختصاصي نديم العباس.

## Documentation and verification

- Split ward UI, patient form fields, doctor synchronization, pinned-state IO, and pure merge/snapshot rules into focused files. Corrected the CSV exporter directory; removed the unused catalog, duplicate legacy Telegram properties, and redundant source-package ignore file. Static checks only for this refactor.

- Application version is `1.1.1.20261007` (code 2); debug automatically adds `-debug`, release has no suffix.
- Added an Arabic Telegram token/ID/topic setup guide. Updated project docs and agent rules; preserved README build commands and ignored private/export files. Fixed reported Kotlin errors, task-editor Material opt-ins, and redundant-safe-call/unused-variable warnings.
- Authorized `assembleDebug` passed on **2026-10-07** in 2m 55s (43 tasks: 21 executed, 22 up-to-date), covering changes at that point; the later source/configuration refactor was checked statically. APK metadata confirms `1.1.1.20261007-debug`, code 2. Gradle reports deprecated-feature usage. Release/device behavior remain unverified; detail is in [currentState.md](currentState.md).
