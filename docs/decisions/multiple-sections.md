# Multiple sections per project — far far future design

Requested 26 October 2026: fix current selection, use “section” in visible terminology, and design multiple sections. Current source still exposes **one active section per project**. This document proposes a later feature; it does not authorize schema/interchange/version changes or implement multiple sections.

Owner decision: defer the entire multi-section feature to **Far far future**. No implementation is requested now. Retain the choices and proposals below for future reconsideration; the existing single-section correction remains implemented.

## Current correction

Android persists the current section ID in existing settings. Fetch, backup restore and Empty Start select their applied section; Fetch Undo restores the previous selection. Current queries observe both shifts and settings, preferring the selected ID over calendar date. Missing selection falls back to the existing newest-date behavior until a section is selected. Existing IDs/dates/rows remain; no automatic repair or deletion of historical data.

The stored `Shift` name remains for compatibility. A clinician’s shift and the captured08:30 report date remain separate concepts from the patient section. Review navigation can still return to its exact captured section; it is not a section-management UI.

## Proposed behavior

- Project-wide doctor registry, connection and existing project encryption model; named sections with stable UUIDs. Section names need not imply different hospitals or Telegram groups. Owner-selected access default: all clinicians can access all active sections, with admin-configurable section restrictions. Restricted sections are hidden and unavailable through clinical app entry points; the shared CSV still contains all sections' data. Enforcement and compatibility details remain to be designed before implementation.
- A labelled section selector above patients/dashboard; switching preserves that section’s filters, roster, order, recycle bin and unfinished drafts. Reports/readiness/counts describe the selected section. Unpublished changes remain visible across sections before any project-wide replacement.
- Admin-reviewed create/rename/archive; archive preserves patients, receipts and backups. No section deletion or implicit patient transfer. Owner-requested note: an explicit Move patient to another section action can be included when implementation scope is revisited; transfers are not ruled out of the first version. Review/Undo semantics and identity/assignment-day preservation need definition before implementation.
- Owner-selected destination behavior: General/supervisor destinations inherit the existing project settings by default, with optional admin-configured section overrides. Every preview, caption and final confirmation identifies the section and resolves the actual destinations for review. Distinct sections have distinct delivery identities even when sharing a destination.
- Owner-selected snapshot scope: the shared CSV contains all sections' data, including sections hidden from the current clinician. Fetch/publication operate on the complete project snapshot; never filter hidden sections out of publication. Proposed review mechanics: a global pending-change guard and a single project Undo, with clinical review details limited to authorized sections. Ordinary report delivery targets only the chosen authorized section. Hidden-section data must survive snapshot round trips without clinical edits by unauthorized users. The transport remains last-successful-write-wins without an atomic multi-device claim.

## Storage and compatibility design

Owner clarification: restrictions govern access within the app; the shared CSV deliberately retains every section's data. Keep the existing project encryption model; separate section keys or a backend are not part of this scope. Someone with direct access to the readable/decrypted CSV can read all sections. Within the app, hiding navigation alone leaves other entry points unguarded: clinical reads/writes, reports/exports, search/counts and draft access must respect permissions. Internal snapshot synchronization, backup and Undo must preserve all sections without presenting hidden clinical content or granting unauthorized clinical edits.

Owner-selected revocation timing: offline access may continue under the last known permissions; revocation takes effect when the app next connects and successfully refreshes authoritative permissions. Connectivity alone is not confirmation of a permission update. Once revocation is learned, block the affected section across app entry points and remove its affected unsaved patient drafts, including any currently open draft; do not retain them for later access restoration. This discards unsaved edits only, without deleting saved clinical records or removing sections from the complete CSV. Previously obtained readable copies cannot be recalled. Optional report destinations must also be reviewed for who can read delivered PDFs, which remain deliberately readable. The section selector and counts must not expose unauthorized clinical content.

Introduce an explicit section identity/name/archive model and patient association; stop using dates as section keys. Keep captured report dates and historical legacy shift rows. Existing data needs an owner-reviewed mapping into the initial section; do not infer multiple real sections from historical dates. The current-section setting can remain the device’s selection, while stable section IDs belong to the shared model.

A new snapshot container must carry sections, roster/sort data and patients with stable IDs. The legacy three-date-list container is a compatibility reader, not a suitable multi-section schema: it limits lists and uses dates for selection. Android and desktop must change together across persistence, fetch/Undo, backup, pending counts, report text/PDF, drafts and delivery identity. Old clients must explicitly refuse unsupported multi-section publications rather than silently uploading one section over the whole project. Snapshot journal size remains bounded independently of section count.

Database migration design, wire-version changes and any suite/build/device work require separate explicit authorization. Do not inspect or modify migrations as part of this design.

## Selected decisions and remaining proposals

Access defaults/configurability, app-level restrictions with all sections retained in the CSV, revocation on the next successful online permission refresh and optional destination overrides were selected by the owner on 26 October 2026. Other rows remain proposals.

| Decision | Selection or proposed starting point |
|---|---|
| Who manages sections? | Proposed: existing live project admins. |
| Section access? | Selected: all clinicians have access by default; admins can restrict clinical access within the app. Shared CSV retains all sections; no separate section encryption. |
| Revocation timing? | Selected: apply on the next successful online permission refresh; last known permissions remain usable offline. Remove affected unsaved drafts when revocation is learned; preserve saved records. |
| Snapshot scope? | Selected: complete-project CSV containing all sections, including hidden sections; preserve them through fetch/publication. Review/Undo mechanics remain proposed. |
| Destinations? | Selected: inherit project General/supervisor chats, with optional section overrides; label every report with its section. |
| Patient membership? | Proposed: one active section per patient. Retain an explicit transfer action as an option for future implementation scope; review/Undo semantics remain open. |
| Existing stored lists? | One reviewed initial-section mapping; retain historical rows without treating dates as section names. |

[Current behavior](../status/current-state.md) · [Architecture](../reference/architecture.md)
