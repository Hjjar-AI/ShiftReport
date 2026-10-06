# ShiftReport remaining work

This file contains only remaining work. Completed items are tracked in [workDone.md](workDone.md).

## Accepted synchronization scope

Telegram remains the shared storage and delivery channel. Users retrieve published data and publish updates; the last successful pinned-state write wins. Simultaneous collaborative editing and guaranteed preservation of competing remote publications are outside the required scope. Telegram pinned state is not an atomic multi-writer database; this limitation is accepted, not a release blocker. A separate authoritative service is not required. Existing local stale-edit checks, merge/conflict handling, authorization, and report confirmation remain in place.

## P0 — local correctness and release safety

1. Manually verify the intended retrieve/edit/publish workflow before release.
   - Retrieve the latest published shift and doctor registry on another device after publication completes.
   - Preserve pending local drafts when retrieving data; exercise any existing merge review and its recovery after process death.
   - Retry interrupted uploads and handle offline-to-online recovery without claiming publication succeeded before it completes.
   - Verify local stale patient/doctor edits and stale deletion, including patient-reference and current-shift-roster protections.
   - Report confirmation while patient fields, shift roster, supervisor destinations, or doctor identities change; synchronization that changes reviewed content must return to preview before text/PDF delivery.
   - Partial supervisor delivery and interrupted text/PDF delivery; recovery must account for recipients already reached.
   - Re-linking a Telegram identity invalidates the old device-local PIN; permanent-admin deletion protections still hold.

2. Finish transactional protection for the remaining administrative workflows.
   - Basic doctor add/edit/delete now have live authorization, snapshot checks, pending markers, and audit in one transaction; CSV import and admin promotion/demotion still need equivalent guarded orchestration.
   - Recheck import preview assumptions and actor/target permissions at commit, preserve intervening edits, and commit the mutation, pending state, and audit together.
   - Apply expected-snapshot protection to supervisor-group destination changes; handle intervening role or identity changes explicitly.

3. Configure production signing before distribution.
   - Release currently uses the debug signing configuration. Configure a protected release keystore through local/CI secrets without committing keys or passwords.
   - Verify the signed release only when build/package work is explicitly authorized.

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
   - Depends on the structured fields and tasks above. Preserve a fast emergency path while persisting incomplete sections and any authorized completeness-override reason.

4. Complete the handoff readiness check before publication.
   - Extend the existing resident/supervisor/diagnosis/treatment warnings after structured fields and tasks exist: missing ownership, unresolved urgent warnings, unassigned or overdue tasks, and missing contingency plans for unstable patients.
   - Present unresolved sync conflicts separately as consistency blockers; a completeness override must never bypass conflict resolution or stale-write checks.
   - This is a completeness check, not diagnostic or treatment advice.
   - Allow an authorized completeness override with a persisted reason and actor; keep emergency drafting available without silently publishing unresolved data.

5. Make “What changed since my last accepted handoff?” the primary returning-user experience.
   - Show only meaningful patient additions, removals, field changes, new warnings, completed tasks, and reassignment.
   - Group changes by patient and severity; allow one-tap navigation to the exact changed section.
   - Requires explicit receiver-acceptance records; the existing local-change briefing is not an accepted-handoff history.
   - Track a per-user acknowledgment cursor so the badge clears only for that receiver. Start with device-local acceptance/cursors; any future cross-device acknowledgment transport must state its delivery and last-write-wins limits.

6. Add closed-loop critical-change acknowledgment.
   - Depends on structured task/receiver records and an explicit acknowledgment transport design compatible with Telegram; guaranteed concurrent acknowledgment delivery is outside the current scope. Critical changes have an owner, recipients, sent time, seen time, and acknowledged time.
   - Record observation and acknowledgment explicitly in the app; Telegram delivery alone does not establish either.
   - Keep unacknowledged critical items pinned above routine activity.
   - Escalate by age and severity without repeatedly alerting for low-value updates.
   - Never use color alone; show a label, icon, and timestamp.

7. Add a verified downtime view.
   - Persist a separate last successfully verified handoff with its verification time; the mutable working database, connectivity status, and generic last-sync timestamp are not a verified snapshot.
   - Connect the live connectivity indicator to snapshot age and a prominent stale-data banner, preserving pending local changes separately.
   - Provide a compact printable/exportable downtime sheet and record later reconciliation.

## P2 — setup and access

1. Complete secure multi-device provisioning and credential lifecycle.
   - Retain encrypted join-file provisioning for the Telegram-based app; verify the intended project identity before import and never place a raw reusable bot token in a QR code.
   - Do not claim reusable join files have enforceable one-time use or expiry; stronger server-enforced provisioning is an optional future capability.
   - Add explicit bot-token rotation/revocation and a safe re-connect flow that cannot silently mix one hospital's local clinical database with another project.

2. Enforce authorization inside the legacy VBA importer itself, in addition to its navigation guard, when importer/migration files are explicitly in scope for review.

## P3 — remaining UI validation and large-screen refinement

1. Upgrade expanded windows from an adaptive grid plus full-screen detail to a simultaneous list-detail pane.
   - Keep the current one-pane full-screen details experience on phones.
   - Preserve the selected patient when switching between one- and two-pane layouts or crossing a fold posture.

2. Add a supporting report/activity pane on medium and expanded windows.
   - Follow the primary list-detail layout; show report readiness, recent meaningful changes, or the selected patient's activity without obscuring the primary ward content.
   - On compact windows, present the same supporting content in the existing sheet or full-screen destination.

3. Add a resizable pane divider for expanded layouts.
   - Requires the list-detail layout; preserve the user's list/detail width during the session. This is a refinement, not a prerequisite for the initial two-pane layout.
   - Enforce readable minimum widths and reset safely after a window-size change.

4. Make expanded layouts fold-aware.
   - Do not place patient content or primary controls beneath a separating hinge.
   - Place the list and detail panes on opposite sides when posture and available width allow it.

5. Complete the card hierarchy after the structured task and handoff models in P1 exist.
   - Show overdue/pending task counts and the latest meaningful acknowledged change in the collapsed card.
   - Do not infer these clinical states from free-form follow-up text.

6. Promote Activity from the current focused overlay to a persistent primary destination with restorable filters and scroll state.

7. Finish the remaining secondary-screen icon and interaction vocabulary audit.
   - Review remaining action icons and semantics for ambiguity; the completed grouping, descriptions, and labelled import/export actions should not be repeated.

8. Perform hands-on accessibility and palette validation on real devices.
   - Confirm the statically audited contrast in rendered light/dark screens, then verify large font, landscape, split-screen, keyboard, TalkBack, Switch Access, and reduced motion.
   - Audit traversal order and expose custom accessibility actions for patient edit, badge, priority, copy, and delete operations.
   - Validate the 600dp navigation transition and full-screen editor behavior on tablets and foldables.
   - Verify adaptive, round, launcher, recents, and Android app-settings emblem rendering after a fresh install or launcher-cache refresh; preserve the existing artwork.

## Later — optional differentiators

- Optional service-backed provisioning/token proxy or authoritative collaboration, only if future requirements call for server-enforced expiry, client token removal, or stronger concurrent-write guarantees.
- Optional three-pane layout for very wide windows: patient list, detail, and tasks/history. Requires list-detail, fold handling, and structured task/history content; collapse predictably to two panes and then one.
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
