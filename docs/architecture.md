# ShiftReport design and architecture basis

This file records the accepted architecture scope and external design basis. The implemented application architecture is described in [Current state](current-state.md); unfinished work is tracked in [Roadmap](roadmap.md).

## Accepted synchronization scope

Telegram remains the shared storage and delivery channel. Users retrieve published data and publish updates; the last successful pinned-state write wins. Simultaneous collaborative editing and guaranteed preservation of competing remote publications are outside the required scope. Telegram pinned state is not an atomic multi-writer database; this limitation is accepted, not a release blocker. A separate authoritative service is not required. Existing local stale-edit checks, merge/conflict handling, authorization, and report confirmation remain in place.

## Current architecture decisions

- Keep Room/SQLCipher as the working device-local store, with repositories and ViewModels separating clinical mutations from Compose screens. Preserve expected revisions, atomic local mutation/audit/pending state, and explicit conflict review.
- Joining is exclusively through password-protected project files that carry connection settings, topics, and any shared data key. Manual group/topic entry is limited to project creation; administrators can replace the existing bot token after live authorization. Same-project file recovery is available before login.
- Signing out ends the clinician session while retaining the configured project and local registry. Returning on the same enrolled device can use the doctor's existing local PIN, checked against the live active record with persistent attempt limits. Telegram verification remains required for first-time enrollment and available when the PIN is forgotten. A join file is for configuring another device or recovering connection credentials; it is not required merely to sign back in. An enrolled administrator can authenticate locally before repairing the same bot's token through the existing protected reconnect flow.
- Optional Telegram-data encryption is selected at creation. Encrypt application records before upload and decrypt locally; keep PDFs deliberately readable, and do not imply that Telegram metadata is encrypted. Retain the project key during same-project credential replacement. Reject different-project/key/topic files before changing credentials; cross-project switching is blocked to protect local clinical records.
- Store structured tasks separately from narrative follow-up. Pending includes overdue; card counts are per patient, dashboard totals are ward-wide, and completion records retain actor/time. New tasks start unassigned; assignment is optional and no user acknowledgment is required.
- Offer Classic tables for compact tabular output and colored portrait A5 cards for reading one patient vertically. Cards begin with a linked patient index and return there through the name-only patient heading; Previous/Index/Next stays available on continuation pages. Keep report context explicit and color mainly on headings/labelled urgency. Display tasks overdue-first/completed-last through shared domain rules, preserving full metadata and stored order; paginate long clinical content at a readable font size rather than shrinking it. Table paper/orientation settings do not change the A5 card format.
- Preserve reviewed report snapshots and explicit send confirmation. Template ideas must not change report inclusion rules or existing clinical content automatically.
- Use app-specific shared fields, actions, cards, pickers, and authentication layouts to apply theme tokens consistently. Fields/actions use the small shape and cards use medium; use minimal shared internal padding separately from screen gutters and growing minimum control heights, reserving square surfaces for full-screen/chrome roles. Keep layout gaps and line heights compact through shared tokens; retain font sizes, floating-label clearance, and growing 48dp touch targets. PDF line spacing must retain full glyph bounds with minimal border clearance. Use one typography/spacing system with restrained tinted surfaces across screens and distinct accents on controls/headings. Appearance (including black AMOLED Night) and accent are independent; badge severity colors are shared, and diagnosis categories do not imply errors. Simplify collapsed content without removing saved clinical detail; animate individual expansion briefly and apply bulk changes immediately.
- Editable and picker fields share native Material decoration and compact app insets; section headers and semantic status notices share app components. Make frequent draft actions primary and secondary insertion/destructive actions discoverable in menus. Task completion remains part of the patient draft, saved through the existing clinical mutation path. Whole-row controls own selection/toggle semantics, and form-level busy state cannot be overridden by a local enabled condition. Secondary screens share presentation primitives while specialized patient draft/conflict/publication workflows keep their own callbacks.
- Patient rounds navigate the current filtered/pinned/grouped list from details, retaining patient identity and per-patient UI state. Navigation changes selection only and is guarded while forms/save are active; preserve explicit draft discard and reviewed report sending.
- Keep patient editing full-screen with clinical/task sections before demographics. New-patient entry begins with demographics. Preserve RTL, labelled controls, 48dp interaction targets, and direct access to frequent navigation actions. Keep the saved patient name visible and use scroll shortcuts within a single draft. An optional default-off Save-and-next action uses the same validated/revision-checked save and advances only after success, following the displayed rounds order. Successful ordinary edits reveal the saved patient without clearing filters or moving an already-visible viewport; briefly highlight the card and explain filter-hidden results.

## Scope and deferred ideas

Production signing remains in the active roadmap. Same-project credential replacement and file-based reconnect are implemented; revocation is handled through BotFather. Structured tasks and card counts are implemented. Voice dictation is deferred to the far future; presentation-only specialty templates and their proposed Telegram synchronization are deferred to the far far future. See [Roadmap](roadmap.md) for their details.

The owner generally starts fresh projects after edits. Do not treat historical-project conversion, simultaneous collaboration guarantees, or removed verification/edge-case tasks as standing requirements. Current source/static checks and runtime limitations are recorded in [Current state](current-state.md); design references below are inspiration, not claims that every referenced workflow is implemented.

## External design basis

- Android adaptive canonical layouts: list-detail for patient browsing and detail, plus adaptive navigation for bar/rail switching.
- Android Compose accessibility: 48dp interactive targets, meaningful semantics, headings, and live-region announcements.
- I-PASS / structured handoff guidance: acuity, summary, actions, contingency planning, and receiver synthesis.
- NHS warning guidance: concise, specific warnings reserved for significant or time-critical information.
- WCAG 2.2: visible focus, target size, programmatic status messages, and interaction that does not rely on color alone.
- Earlier Coolors/muted palettes are historical. Current owner-requested themes use distinct blue, forest-green, amethyst, amber, cherry, turquoise, copper, and olive families with tinted light/dark surfaces. Stable stored identifiers preserve selections; AMOLED Night remains independent, and clinical severity uses separate shared colors.

## Offline demonstration

Demo is an isolated in-memory workspace using the real offline patient forms, cards, task controls, roster picker, sorting, and PDF renderer. It has no clinical repository or Telegram dependency. Local PDF export is explicitly available for demonstrating reports, with a demo label on every page; exit/reset discards the temporary session. Drawer density preserves 48dp targets while using a two-column context header and compact wrapping rows.

## Standalone desktop client

The current desktop source is [`desktop/`](../desktop/README.md). CustomTkinter
forms replace Excel; the existing Telegram formats and accepted retrieve/edit/
review/publish model stay unchanged. A worker serializes blocking network/file/
crypto operations. The GUI owns logical Arabic input text; PDF shaping happens
only during rendering. Authentication and publication checks live in the protocol
layer, not just navigation. Desktop can bootstrap the first admin and export join
files and perform reviewed ongoing doctor/role/destination administration.
Live authorization, stale-review checks and reference guards remain below the UI.
Protected join files remain the normal desktop connection path. An explicit local
raw-credential mode supports occasional admin use, with Telegram verification
before workspace access and admin checks in protocol authorization. Its plaintext
settings are outside the encrypted workspace and must stay out of bundles; these
client checks cannot restrict direct Telegram access by a token holder.

Before a registry exists, desktop creation uses verified human Telegram group
administration as bootstrap authority. Keep this separate from ordinary registry
login: require a private challenge, explicit review, no existing pin and an unused
local workspace, with authority rechecks below the UI for every publication step.
Persist an encrypted UNKNOWN receipt before the first write. Do not silently
replace unknown pins, reset data or retry ambiguous creation. Current Android
registry/index/join formats remain the source of interoperability; no new backend
or atomic multi-writer guarantee is introduced.

A project-fingerprint workspace seals baseline, draft, registry cache and receipts
with AES-GCM before SQLite persistence. The stable shared project key or a random key protected by Windows DPAPI
CurrentUser / a Linux unlock passphrase protects local state; connection secrets are never
part of the workspace. Draft restoration cannot roll back delivery receipts or
restore publication approval. Local mutex/transactions do not imply distributed
Telegram atomicity. See [current state](current-state.md) for implementation and
runtime limits; protected legacy tools/guides remain historical on disk.

Linux setup resolves unpinned binary dependencies for its selected interpreter
inside `.venv`; Windows 7 requirements remain separate.

Linux uses the same encrypted SQLite and Telegram representations without a schema
change. A separate local passphrase wraps the random key for unencrypted Telegram
projects; it is never stored or derived from connection credentials. Missing keys
fail explicitly instead of silently replacing local encryption. OS adapters choose
the XDG data directory, PDF fonts/viewer and process lock. Windows keeps its DPAPI,
Arial/startfile and named mutex path. PDF opening is separate from the mandatory
review/confirmation and does not authorize delivery on its own.

Arabic UI presentation is isolated in `desktop_widgets.py`: adapters shape only
rendered labels/choices/reviews and keep logical values for callbacks and copies.
In auto/shaped mode, `desktop_input.py` keeps a logical Unicode editing model
separate from its shaped display, with visual-to-logical caret and selection maps.
Form/search getters, clipboard, bound variables and Save use the logical model;
passwords and native mode retain native controls. No presentation glyphs are
written back to clinical state. PDF shaping remains
independent, preventing double transformation across UI and export paths.

## Repository organization

`android/` owns the complete Gradle/Android Studio project and its local state;
`desktop/` owns the Python PC client. Shared documentation stays in `docs/`.
Ignored private Excel artifacts live under
`local/excel/`. The parent Git repository and AGENTS.md apply to both platforms.
This separates platform tooling without altering application/data versions or
shared Telegram contracts. Android commands and IDE imports use `android/` as
the project root. Historical protected reviews retain old source-root paths;
current source paths are prefixed with `android/`.

The names-only team PDF is a presentation of explicit patient assignments, not a
new persisted supervisor/resident hierarchy. Both platforms group by supervisor,
list the team's distinct assigned residents in one row and retain patient-name
rows in one portrait page whose height grows with content. Full desktop reports
mirror Android's current Classic/card layouts and palette semantics; local
per-supervisor exports and readable names/PDF exports do not publish clinical data.
