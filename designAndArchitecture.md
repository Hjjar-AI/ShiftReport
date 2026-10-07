# ShiftReport design and architecture basis

This file records the accepted architecture scope and external design basis. The implemented application architecture is described in [currentState.md](currentState.md); unfinished work is tracked in [workRemains.md](workRemains.md).

## Accepted synchronization scope

Telegram remains the shared storage and delivery channel. Users retrieve published data and publish updates; the last successful pinned-state write wins. Simultaneous collaborative editing and guaranteed preservation of competing remote publications are outside the required scope. Telegram pinned state is not an atomic multi-writer database; this limitation is accepted, not a release blocker. A separate authoritative service is not required. Existing local stale-edit checks, merge/conflict handling, authorization, and report confirmation remain in place.

## External design basis

- Android adaptive canonical layouts: list-detail for patient browsing and detail, plus adaptive navigation for bar/rail switching.
- Android Compose accessibility: 48dp interactive targets, meaningful semantics, headings, and live-region announcements.
- I-PASS / structured handoff guidance: acuity, summary, actions, contingency planning, and receiver synthesis.
- NHS warning guidance: concise, specific warnings reserved for significant or time-critical information.
- WCAG 2.2: visible focus, target size, programmatic status messages, and interaction that does not rely on color alone.
- Coolors palette exploration: distinct olive, oceanic, navy/gold, and muted-violet families, adjusted where required for readable foreground contrast.
