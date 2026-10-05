# ShiftReport plan

## Completed / removed from the backlog

- Patient sync already has three-way field merging, explicit conflict review, immutable uploaded CSV files, recovery snapshots, and a publication journal.
- The ward screen already exposes persistent local/pending/uploading/published/conflict sync states; this is no longer a standalone backlog item.
- Concurrent sync operations on one device are now serialized per patient and doctor channel.
- Edits made while a patient upload is in flight remain pending for the next publication, and sync metadata updates no longer replace concurrent shift doctor/sort changes.
- Fetching the latest patient snapshot now refuses to replace unsent local edits.
- Doctor publishing now rejects a stale registry instead of replacing a newer remote registry; an explicit “bring latest” action is the recovery path.
- Pinned sync-state writes now preserve the newest patient, doctor, and announcement pointers instead of allowing one channel to erase another.
- The patient list is now the initial ward view; the dashboard remains one tap away.
- The Telegram doctor picker now respects safe system insets, patient overflow actions use a 48dp target, and the password visibility action has an accessible label.
- The APK is now hospital-agnostic: bot credentials, group/topic IDs, hospital name, and first-admin identity are no longer compiled into the app.
- First launch now offers create-project and join-project flows, validates the bot and group, creates or downloads the pinned doctor registry, and stores the project connection in encrypted device-local preferences.
- The configured hospital/project name now appears in the clinical header and drawer while the existing logo and About/credits page remain unchanged.
- Patient entry now has a concise General mode and a detailed Advanced mode without discarding hidden clinical data.
- Opening a patient now presents a full-screen details experience with Overview, Clinical, Tasks, Warnings, and History sections; editing is a separate explicit action.
- The patient list keeps its selected patient and scroll position through configuration changes.
- Ward search, filters, grouping, and sorting share one panel, and active filters are visible as individually removable chips.
- Live ward cards now use one predictable clinical hierarchy; the former visual variants remain PDF-only.
- Primary navigation now uses Patients, Dashboard, and Activity in a bottom bar on compact windows and a rail on tablet/landscape widths.
- The UI now has stable semantic colors for normal, warning, urgent, pending, conflict, and success states, independent of dynamic color.
- App and PDF palettes were consolidated into distinct color families; the duplicate cyan-night variants were removed and stale saved selections safely fall back to System/Teal.
- All remaining explicit app theme role pairs, clinical semantic pairs, and PDF header pairs pass the WCAG AA 4.5:1 normal-text contrast threshold in the static audit.
- The ward header now shows hospital, shift date, patient count, freshness, semantic headings, and polite sync-status announcements.

## P0 — multi-user correctness and safety

1. Replace the Telegram pinned-message pointer as the authoritative multi-writer database.
   - Telegram documents are useful immutable artifacts, but the Bot API does not provide an atomic compare-and-swap operation for the pinned JSON state.
   - Use a small authoritative service/database with row versions or transactions for shifts, patients, doctors, and publication ownership.
   - Keep Telegram as the delivery/archive channel after the authoritative transaction commits.

2. Add a real doctor-registry merge workflow.
   - Persist a last-synced base registry.
   - Three-way merge additions, edits, deletions, admin rank, and Telegram identity.
   - Present conflicts for explicit review instead of requiring the admin to accept the entire remote registry.

3. Make patient edits optimistic at the database/repository boundary.
   - Save with an expected revision and reject stale local editor submissions.
   - Apply patient mutation, pending-sync flag, and audit entry in one Room transaction.
   - Do the same for delete, restore, priority, warning, rollover, and shift doctor/sort changes.

4. Manually verify the high-risk concurrency scenarios before release.
   - Two devices edit different fields of the same patient.
   - Two devices edit the same field and resolve the conflict both ways.
   - Delete versus edit, add versus add, simultaneous publish, and interrupted publish.
   - Doctor edit versus doctor edit and doctor deletion while assigned to an active patient.
   - Patient publish concurrent with doctor-registry or announcement updates.

## P1 — signature handoff workflow

1. Add a guided I-PASS handoff mode instead of treating publishing as the end of handoff.
   - Illness severity: stable, watcher, or unstable as a dedicated field—not inferred from card color or priority.
   - Patient summary: the concise current clinical picture.
   - Action list: structured tasks assigned to a person or incoming shift.
   - Situation awareness: explicit “if this happens, then do this” contingency entries.
   - Synthesis by receiver: receiver reviews, asks questions, and accepts the handoff.

2. Make “What changed since my last accepted handoff?” the primary returning-user experience.
   - Show only meaningful patient additions, removals, field changes, new warnings, completed tasks, and reassignment.
   - Group changes by patient and severity; allow one-tap navigation to the exact changed section.
   - Track a per-user acknowledgment cursor so the badge clears only for that receiver.

3. Add closed-loop critical-change acknowledgment.
   - Critical changes have an owner, recipients, sent time, seen time, and acknowledged time.
   - Keep unacknowledged critical items pinned above routine activity.
   - Escalate by age and severity without repeatedly alerting for low-value updates.
   - Never use color alone; show a label, icon, and timestamp.

4. Promote structured patient tasks into the core model.
   - Description, owner, due time or shift, priority, pending/done state, completion actor, and completion timestamp.
   - Overdue and unassigned tasks appear in the shift dashboard and “My patients.”
   - Pending tasks carry forward explicitly during rollover rather than being hidden in follow-up text.

5. Add a handoff readiness check before publication.
   - Highlight missing ownership, unresolved urgent warnings, unassigned or overdue tasks, missing contingency plans for unstable patients, and unresolved sync conflicts.
   - This is a completeness check, not diagnostic or treatment advice.
   - Allow an authorized override with a recorded reason; never silently block emergency work.

6. Add a verified downtime view.
   - Keep the last successfully verified handoff available offline with a prominent snapshot time and stale-data banner.
   - Provide a compact printable/exportable downtime sheet and record later reconciliation.

## P2 — setup and access

1. Complete secure multi-device provisioning and credential lifecycle.
   - Replace manual token entry on joining devices with a signed, encrypted, short-lived provisioning bundle or QR flow.
   - Verify the intended project identity and expiry before import; never place a raw reusable bot token in a QR code.
   - Add explicit bot-token rotation/revocation and a safe re-connect flow that cannot silently mix one hospital's local clinical database with another project.
   - Longer term, remove the broadly privileged bot token from ordinary client devices by proxying Telegram operations through the authoritative service proposed in P0.

2. Keep admin-only actions enforced below the UI layer as well as through navigation guards.

## P3 — remaining UI validation and large-screen refinement

1. Upgrade expanded windows from an adaptive grid plus full-screen detail to a simultaneous list-detail pane.
   - Keep the current one-pane full-screen details experience on phones.
   - Preserve the selected patient when switching between one- and two-pane layouts or crossing a fold posture.

2. Complete the card hierarchy after the structured task and handoff models in P1 exist.
   - Show overdue/pending task counts and the latest meaningful acknowledged change in the collapsed card.
   - Do not infer these clinical states from free-form follow-up text.

3. Promote Activity from the current focused overlay to a persistent primary destination with restorable filters and scroll state.

4. Perform hands-on accessibility and palette validation on real devices.
   - Confirm the statically audited contrast in rendered light/dark screens, then verify large font, landscape, split-screen, keyboard, TalkBack, Switch Access, and reduced motion.
   - Audit traversal order and expose custom accessibility actions for patient edit, warning, priority, copy, and delete operations.
   - Validate the 600dp navigation transition and full-screen editor behavior on tablets and foldables.

## Later — optional differentiators

- On-device voice capture that fills a draft handoff but always requires visual confirmation before saving.
- A privacy-safe shift quality view showing handoff completeness and acknowledgment delays, never clinician “scores.”
- Saved ward rounds with a deliberate patient order, progress indicator, and pause/resume across devices.
- Configurable specialty templates built on the same core handoff fields rather than separate incompatible schemas.

## External design basis

- Android adaptive canonical layouts: list-detail for patient browsing and detail, plus adaptive navigation for bar/rail switching.
- Android Compose accessibility: 48dp interactive targets, meaningful semantics, headings, and live-region announcements.
- I-PASS / structured handoff guidance: acuity, summary, actions, contingency planning, and receiver synthesis.
- NHS warning guidance: concise, specific warnings reserved for significant or time-critical information.
- WCAG 2.2: visible focus, target size, programmatic status messages, and interaction that does not rely on color alone.
- Coolors palette exploration: distinct olive, oceanic, navy/gold, and muted-violet families, adjusted where required for readable foreground contrast.
