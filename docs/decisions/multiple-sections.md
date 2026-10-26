# Multiple sections per project — design for owner review

Requested 26 October 2026: fix current selection, use “section” in visible terminology, and design multiple sections. Current source still exposes **one active section per project**. This document proposes a later feature; it does not authorize schema/interchange/version changes or implement multiple sections.

## Current correction

Android persists the current section ID in existing settings. Fetch, backup restore and Empty Start select their applied section; Fetch Undo restores the previous selection. Current queries observe both shifts and settings, preferring the selected ID over calendar date. Missing selection falls back to the existing newest-date behavior until a section is selected. Existing IDs/dates/rows remain; no automatic repair or deletion of historical data.

The stored `Shift` name remains for compatibility. A clinician’s shift and the captured08:30 report date remain separate concepts from the patient section. Review navigation can still return to its exact captured section; it is not a section-management UI.

## Proposed behavior

- Project-wide doctor registry, access rules, connection and encryption key; named sections with stable UUIDs. Section names need not imply different hospitals or Telegram groups.
- A labelled section selector above patients/dashboard; switching preserves that section’s filters, roster, order, recycle bin and unfinished drafts. Reports/readiness/counts describe the selected section. Unpublished changes remain visible across sections before any project-wide replacement.
- Admin-reviewed create/rename/archive; archive preserves patients, receipts and backups. No section deletion or implicit patient transfer in the first implementation. Moving a patient would be a separate reviewed action preserving identity and assignment-day rules.
- General/supervisor destinations remain the existing project settings by default. Every preview, caption and final confirmation identifies the section. Distinct sections have distinct delivery identities even when sharing a destination.
- Fetch replaces the reviewed **complete project snapshot** of all active sections, with a global pending-change guard and a single project Undo. Publication review inventories changes by section; ordinary report delivery still targets only the chosen section. One Telegram pointer keeps the existing last-successful-write-wins model, without an atomic multi-device claim.

## Storage and compatibility design

Introduce an explicit section identity/name/archive model and patient association; stop using dates as section keys. Keep captured report dates and historical legacy shift rows. Existing data needs an owner-reviewed mapping into the initial section; do not infer multiple real sections from historical dates. The current-section setting can remain the device’s selection, while stable section IDs belong to the shared model.

A new snapshot container must carry sections, roster/sort data and patients with stable IDs. The legacy three-date-list container is a compatibility reader, not a suitable multi-section schema: it limits lists and uses dates for selection. Android and desktop must change together across persistence, fetch/Undo, backup, pending counts, report text/PDF, drafts and delivery identity. Old clients must explicitly refuse unsupported multi-section publications rather than silently uploading one section over the whole project. Snapshot journal size remains bounded independently of section count.

Database migration design, wire-version changes and any suite/build/device work require separate explicit authorization. Do not inspect or modify migrations as part of this design.

## Decisions before implementation

| Decision | Proposed starting point |
|---|---|
| Who manages sections? | Existing live project admins; clinicians work in any active section. Per-section permissions would be additional scope. |
| Snapshot scope? | One complete project snapshot, preserving every section; show all pending changes before replacement. |
| Destinations? | Reuse project General/supervisor chats, label every report with its section. Per-section chats would be additional scope. |
| Patient membership? | One active section per patient; defer transfers until their review/undo semantics are selected. |
| Existing stored lists? | One reviewed initial-section mapping; retain historical rows without treating dates as section names. |

[Current behavior](../status/current-state.md) · [Architecture](../reference/architecture.md)
