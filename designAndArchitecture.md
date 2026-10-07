# ShiftReport design and architecture basis

This file records the accepted architecture scope and external design basis. The implemented application architecture is described in [currentState.md](currentState.md); unfinished work is tracked in [workRemains.md](workRemains.md).

## Accepted synchronization scope

Telegram remains the shared storage and delivery channel. Users retrieve published data and publish updates; the last successful pinned-state write wins. Simultaneous collaborative editing and guaranteed preservation of competing remote publications are outside the required scope. Telegram pinned state is not an atomic multi-writer database; this limitation is accepted, not a release blocker. A separate authoritative service is not required. Existing local stale-edit checks, merge/conflict handling, authorization, and report confirmation remain in place.

## Current architecture decisions

- Keep Room/SQLCipher as the working device-local store, with repositories and ViewModels separating clinical mutations from Compose screens. Preserve expected revisions, atomic local mutation/audit/pending state, and explicit conflict review.
- Joining is exclusively through password-protected project files that carry connection settings, topics, and any shared data key. Manual group/topic entry is limited to project creation; administrators can replace the existing bot token after live authorization. Same-project file recovery is available before login.
- Optional Telegram-data encryption is selected at creation. Encrypt application records before upload and decrypt locally; keep PDFs deliberately readable, and do not imply that Telegram metadata is encrypted. Retain the project key during same-project credential replacement. Reject different-project/key/topic files before changing credentials; cross-project switching is blocked to protect local clinical records.
- Store structured tasks separately from narrative follow-up. Pending includes overdue; card counts are per patient, dashboard totals are ward-wide, and completion records retain actor/time. New tasks start unassigned; assignment is optional and no user acknowledgment is required.
- Preserve reviewed report snapshots and explicit send confirmation. Template ideas must not change report inclusion rules or existing clinical content automatically.
- Use one neutral surface/typography/spacing system across screens. Appearance and accent are independent; badge severity colors are shared, and diagnosis categories do not imply errors. Simplify collapsed content without removing saved clinical detail; animate individual expansion briefly and apply bulk changes immediately.
- Keep patient editing full-screen with clinical/task sections before demographics. New-patient entry begins with demographics. Preserve RTL, labelled controls, 48dp interaction targets, and direct access to frequent navigation actions.

## Scope and deferred ideas

Production signing remains in the active roadmap. Same-project credential replacement and file-based reconnect are implemented; revocation is handled through BotFather. Structured tasks and card counts are implemented. Voice dictation is deferred to the far future; presentation-only specialty templates and their proposed Telegram synchronization are deferred to the far far future. See [workRemains.md](workRemains.md) for their details.

The owner generally starts fresh projects after edits. Do not treat historical-project conversion, simultaneous collaboration guarantees, or removed verification/edge-case tasks as standing requirements. Current source/static checks and runtime limitations are recorded in [currentState.md](currentState.md); design references below are inspiration, not claims that every referenced workflow is implemented.

## External design basis

- Android adaptive canonical layouts: list-detail for patient browsing and detail, plus adaptive navigation for bar/rail switching.
- Android Compose accessibility: 48dp interactive targets, meaningful semantics, headings, and live-region announcements.
- I-PASS / structured handoff guidance: acuity, summary, actions, contingency planning, and receiver synthesis.
- NHS warning guidance: concise, specific warnings reserved for significant or time-critical information.
- WCAG 2.2: visible focus, target size, programmatic status messages, and interaction that does not rely on color alone.
- Earlier Coolors exploration informed the accent choices. Current palettes use teal, olive, blue, navy, and muted violet on shared neutral surfaces; the former navy/gold mix was replaced. Appearance is independent of accent, and clinical severity uses separate shared colors.
