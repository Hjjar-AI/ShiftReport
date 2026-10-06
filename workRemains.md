# ShiftReport remaining work

This file contains only remaining work. Completed items are tracked in [workDone.md](workDone.md).

## P0 — multi-user correctness and safety

1. Establish an authoritative multi-writer service before expanding cross-device workflows.
   - Telegram documents are useful immutable artifacts, but the Bot API does not provide an atomic compare-and-swap operation for the pinned JSON state.
   - Use a small authoritative service/database with row versions or transactions for shifts, patients, doctors, and publication ownership.
   - Keep Telegram as the delivery/archive channel after the authoritative transaction commits.

2. Manually verify the high-risk concurrency scenarios before release.
   - Two devices edit different fields of the same patient.
   - Two devices edit the same field and resolve the conflict both ways.
   - Delete versus edit, add versus add, simultaneous publish, and interrupted publish.
   - Doctor edit versus doctor edit and doctor deletion while assigned to an active patient.
   - Patient publish concurrent with doctor-registry or announcement updates.
   - Doctor merge with no saved base, duplicate Telegram/name additions, and concurrent admin-rank assignments.
   - Local or remote registry changes while conflict review is open, edits during upload, and retry after interrupted merge publication.
   - Re-linking a Telegram identity invalidates the old device-local PIN; permanent-admin and assigned-patient deletion protections still hold.
   - Recreate a pending doctor review after process death by synchronizing again from the persisted base and unchanged local registry.

## P1 — structured, closed-loop handoff

1. Build the structured clinical handoff model before adding more handoff UI.
   - Illness severity: stable, watcher, or unstable as a dedicated field—not inferred from card color or priority.
   - Patient summary: the concise current clinical picture.
   - Situation awareness: explicit “if this happens, then do this” contingency entries.
   - Receiver synthesis and acceptance as explicit records.
   - This requires deliberate schema/model work and is not bundled into unrelated UI changes.

2. Promote structured patient tasks into the core model.
   - Description, owner, due time or shift, priority, pending/done state, completion actor, and completion timestamp.
   - Overdue and unassigned tasks appear in the shift dashboard and “My patients.”
   - Pending tasks carry forward explicitly during rollover rather than being hidden in follow-up text.

3. Add a guided I-PASS handoff flow using the structured model instead of treating publication as the end of handoff.
   - Walk through severity, summary, actions, contingency planning, and receiver synthesis.
   - Preserve a fast emergency path while recording incomplete sections and any authorized override reason.

4. Complete the handoff readiness check before publication.
   - Highlight missing ownership, unresolved urgent warnings, unassigned or overdue tasks, missing contingency plans for unstable patients, and unresolved sync conflicts.
   - This is a completeness check, not diagnostic or treatment advice.
   - Allow an authorized override with a recorded reason; never silently block emergency work.

5. Make “What changed since my last accepted handoff?” the primary returning-user experience.
   - Show only meaningful patient additions, removals, field changes, new warnings, completed tasks, and reassignment.
   - Group changes by patient and severity; allow one-tap navigation to the exact changed section.
   - Track a per-user acknowledgment cursor so the badge clears only for that receiver.

6. Add closed-loop critical-change acknowledgment.
   - Critical changes have an owner, recipients, sent time, seen time, and acknowledged time.
   - Keep unacknowledged critical items pinned above routine activity.
   - Escalate by age and severity without repeatedly alerting for low-value updates.
   - Never use color alone; show a label, icon, and timestamp.

7. Add a verified downtime view.
   - Keep the last successfully verified handoff available offline with a prominent snapshot time and stale-data banner.
   - Provide a compact printable/exportable downtime sheet and record later reconciliation.

## P2 — setup and access

1. Complete secure multi-device provisioning and credential lifecycle.
   - After the P0 service exists, replace the current reusable join file with a server-issued, signed, one-time, short-lived provisioning bundle or QR flow; a client-side timestamp alone is not meaningful expiry.
   - Verify the intended project identity and expiry before import; never place a raw reusable bot token in a QR code.
   - Add explicit bot-token rotation/revocation and a safe re-connect flow that cannot silently mix one hospital's local clinical database with another project.
   - Longer term, remove the broadly privileged bot token from ordinary client devices by proxying Telegram operations through the authoritative service proposed in P0.

2. Enforce authorization inside the legacy VBA importer itself, in addition to its navigation guard, when importer/migration files are explicitly in scope for review.

## P3 — remaining UI validation and large-screen refinement

1. Upgrade expanded windows from an adaptive grid plus full-screen detail to a simultaneous list-detail pane.
   - Keep the current one-pane full-screen details experience on phones.
   - Preserve the selected patient when switching between one- and two-pane layouts or crossing a fold posture.

2. Add a supporting report/activity pane on medium and expanded windows.
   - Show report readiness, recent meaningful changes, or the selected patient's activity without obscuring the primary ward content.
   - On compact windows, present the same supporting content in the existing sheet or full-screen destination.

3. Add a resizable pane divider for expanded layouts.
   - Preserve the user's list/detail width during the session.
   - Enforce readable minimum widths and reset safely after a window-size change.

4. Make expanded layouts fold-aware.
   - Do not place patient content or primary controls beneath a separating hinge.
   - Place the list and detail panes on opposite sides when posture and available width allow it.

5. Add an optional three-pane mode for very wide windows.
   - Use patient list, selected-patient detail, and tasks/history as the three panes.
   - Collapse predictably to list-detail and then single-pane navigation as width decreases.

6. Complete the card hierarchy after the structured task and handoff models in P1 exist.
   - Show overdue/pending task counts and the latest meaningful acknowledged change in the collapsed card.
   - Do not infer these clinical states from free-form follow-up text.

7. Promote Activity from the current focused overlay to a persistent primary destination with restorable filters and scroll state.

8. Make offline status lifecycle-aware and connect it to verified snapshot metadata.
   - The ward now distinguishes offline local data visually, but connectivity changes should update without requiring another screen recomposition.
   - Complete this with the verified downtime snapshot, age, and later reconciliation workflow in P1.

9. Finish the remaining secondary-screen icon and interaction vocabulary audit.
   - Apply the established ward vocabulary to administration and setup screens, and remove any ambiguous duplicate action icons.
   - Verify the adaptive, round, launcher, recents, and Android app-settings emblem rendering on representative real devices after a fresh install or launcher-cache refresh.

10. Perform hands-on accessibility and palette validation on real devices.
   - Confirm the statically audited contrast in rendered light/dark screens, then verify large font, landscape, split-screen, keyboard, TalkBack, Switch Access, and reduced motion.
   - Audit traversal order and expose custom accessibility actions for patient edit, badge, priority, copy, and delete operations.
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
