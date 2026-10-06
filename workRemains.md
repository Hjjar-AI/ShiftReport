# ShiftReport remaining work

This file contains only remaining work. Completed items are tracked in [workDone.md](workDone.md).

## Accepted synchronization scope

Telegram remains the shared storage and delivery channel. Users retrieve published data and publish updates; the last successful pinned-state write wins. Simultaneous collaborative editing and guaranteed preservation of competing remote publications are outside the required scope. Telegram pinned state is not an atomic multi-writer database; this limitation is accepted, not a release blocker. A separate authoritative service is not required. Existing local stale-edit checks, merge/conflict handling, authorization, and report confirmation remain in place.

## P0 — local correctness and release safety

1. Verify the main retrieve/edit/publish workflow before release.
   - Retrieve the published shift and doctor registry, edit local data, and publish updates.
   - Verify doctor import, administrator permissions, and supervisor-group settings.
   - Confirm that report review and sending work as expected.

2. Configure production signing before distribution.
   - Release currently uses the debug signing configuration. Configure a protected release keystore through local/CI secrets without committing keys or passwords.
   - Verify the signed release only when build/package work is explicitly authorized.

## P1 — structured, closed-loop handoff

1. Build the structured clinical handoff model before adding more handoff UI.
   - Illness severity: stable, watcher, or unstable as a dedicated field—not inferred from card color or priority.
   - Patient summary: the concise current clinical picture.
   - Situation awareness: explicit “if this happens, then do this” contingency entries.
   - Receiver synthesis and acceptance as explicit records.
   - Requires dedicated schema/model work.

2. Promote structured patient tasks into the core model.
   - Description, owner, due time or shift, priority, pending/done state, completion actor, and completion timestamp.
   - Overdue and unassigned tasks appear in the shift dashboard and “My patients.”
   - Pending tasks carry forward explicitly during rollover rather than being hidden in follow-up text.

3. Add a guided I-PASS handoff flow using the structured model instead of treating publication as the end of handoff.
   - Walk through severity, summary, actions, contingency planning, and receiver synthesis.
   - Depends on the structured fields and tasks above.

4. Complete the handoff readiness check before publication.
   - Extend the existing resident/supervisor/diagnosis/treatment warnings after structured fields and tasks exist: missing ownership, unresolved urgent warnings, unassigned or overdue tasks, and missing contingency plans for unstable patients.
   - Retain existing sync-conflict review and stale-write protection.
   - This is a completeness check, not diagnostic or treatment advice.

5. Make “What changed since my last accepted handoff?” the primary returning-user experience.
   - Show only meaningful patient additions, removals, field changes, new warnings, completed tasks, and reassignment.
   - Group changes by patient and severity; allow one-tap navigation to the exact changed section.
   - Requires explicit receiver-acceptance records; the existing local-change briefing is not an accepted-handoff history.
   - Track a device-local per-user acknowledgment cursor so the badge clears for that receiver.

6. Add closed-loop critical-change acknowledgment.
   - Use the structured task/receiver model with Telegram-compatible delivery. Record owner, recipients, sent time, seen time, and acknowledged time.
   - Record observation and acknowledgment explicitly in the app; Telegram delivery alone does not establish either.
   - Keep unacknowledged critical items pinned above routine activity.
   - Escalate by age and severity without repeatedly alerting for low-value updates.
   - Never use color alone; show a label, icon, and timestamp.

7. Add a verified downtime view.
   - Store the last verified handoff separately with its verification time.
   - Connect the live connectivity indicator to snapshot age and a prominent stale-data banner, preserving pending local changes separately.
   - Provide a compact printable/exportable downtime sheet and record later reconciliation.

## P2 — setup and access

1. Complete secure multi-device provisioning and credential lifecycle.
   - Retain encrypted join-file provisioning for the Telegram-based app; verify the intended project identity before import and never place a raw reusable bot token in a QR code.
   - Add bot-token rotation/revocation and reconnect with explicit protection for local project data.

2. Enforce authorization inside the legacy VBA importer itself, in addition to its navigation guard, when importer/migration files are explicitly in scope for review.

## P3 — remaining UI model dependencies and device validation

1. Complete the card hierarchy after the structured task and handoff models in P1 exist.
   - Show overdue/pending task counts and the latest meaningful acknowledged change in the collapsed card.
   - Do not infer these clinical states from free-form follow-up text.

2. Validate the UI on representative phones, tablets, and foldables.
   - Check light/dark contrast, large fonts, keyboard use, TalkBack, and 48dp interaction targets.
   - Check navigation, list/detail panes, resizing, patient forms, and report/support sheets.
   - Check Activity search, filters, and saved scroll position.
   - Verify launcher and system-screen emblem rendering.
   - Build/compile verification remains pending until explicitly authorized.

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
