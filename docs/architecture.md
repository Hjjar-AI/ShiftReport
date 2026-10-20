# ShiftReport design and architecture basis

This file records the accepted architecture scope and external design basis. The implemented application architecture is described in [Current state](current-state.md); unfinished work is tracked in [Roadmap](roadmap.md).

## Accepted synchronization scope

Telegram remains the shared storage and delivery channel. Users retrieve published data and publish updates; the last successful pinned-state write wins. Simultaneous collaborative editing and guaranteed preservation of competing remote publications are outside the required scope. Telegram pinned state is not an atomic multi-writer database; this limitation is accepted, not a release blocker. A separate authoritative service is not required. Downloads and publications transfer complete snapshots; remote field-by-field merging is removed. Local stale-edit checks, explicit replacement review, authorization and report confirmation remain in place. Report/publication confirmation may explicitly select the complete local patient copy over competing patient publications; this never bypasses clinician authorization, doctor-registry checks or delivery uncertainty guards. Synchronization is manual on both platforms; there is no periodic background upload/download.

## Current architecture decisions

- Keep Room/SQLCipher as the working device-local store, with repositories and ViewModels separating clinical mutations from Compose screens. Preserve expected revisions, atomic local mutation/audit/pending state, and explicit conflict review.
- Joining is exclusively through password-protected project files that carry connection settings, topics, and any shared data key. Manual group/topic entry is limited to project creation; administrators can replace the existing bot token after live authorization. Same-project file recovery is available before login.
- Signing out ends the clinician session while retaining the configured project and local registry. Returning on the same enrolled device can use the doctor's existing local PIN, checked against the live active record with persistent attempt limits. Telegram verification remains required for first-time enrollment and available when the PIN is forgotten. A join file is for configuring another device or recovering connection credentials; it is not required merely to sign back in. An enrolled administrator can authenticate locally before repairing the same bot's token through the existing protected reconnect flow.
- Optional Telegram-data encryption is selected at creation. Encrypt application records before upload and decrypt locally; keep PDFs deliberately readable, and do not imply that Telegram metadata is encrypted. Retain the project key during same-project credential replacement. Reject different-project/key/topic files before changing credentials; cross-project switching is blocked to protect local clinical records.
- Store structured tasks separately from narrative follow-up. Pending includes overdue; card counts are per patient, dashboard totals are ward-wide, and completion records retain actor/time. New tasks start unassigned; assignment is optional and no user acknowledgment is required.
- Offer Classic tables for compact tabular output and colored portrait A5 cards for reading one patient vertically. Cards begin with a linked patient index and return there through the name-only patient heading; Previous/Index/Next stays available on continuation pages. Keep report context explicit and color mainly on headings/labelled urgency. Display tasks overdue-first/completed-last through shared domain rules, preserving full metadata and stored order; paginate long clinical content at a readable font size rather than shrinking it. Table paper/orientation settings do not change the A5 card format.
- Preserve reviewed report snapshots and explicit send confirmation. Template ideas must not change report inclusion rules or existing clinical content automatically.
- Use app-specific shared fields, actions, cards, pickers, and authentication layouts to apply theme tokens consistently. Fields/actions use the small shape and cards use medium; use minimal shared internal padding separately from screen gutters and growing minimum control heights, reserving square surfaces for full-screen/chrome roles. Keep layout gaps and line heights compact through shared tokens; retain font sizes, floating-label clearance, and growing 48dp touch targets. PDF line spacing must retain full glyph bounds with minimal border clearance. Use one typography/spacing system with restrained tinted surfaces across screens and distinct accents on controls/headings. Appearance (including black AMOLED Night) and accent are independent; badge severity colors are shared, and diagnosis categories do not imply errors. Simplify collapsed content without removing saved clinical detail; animate individual expansion briefly and apply bulk changes immediately.
- Use semantic tokens for patient density, borders/elevation, disabled appearance, motion, dialog caps and adaptive layout thresholds. Keep purpose-specific thresholds independent, preserve caller constraints and scroll state, and size dialog bodies from their available viewport rather than screen estimates. Clinical severity remains separate from interaction styling.
- Share dialog headings/actions, patient identity formatting, read-only clinical sections, action vocabulary and progress presentation while callers own validation and operation state. Keep the design catalog and spare appearance profiles descriptive until a local picker is explicitly implemented; appearance profiles do not change clinical colors, reports, permissions or stored clinical content.
- Editable and picker fields share native Material decoration and compact app insets; section headers and semantic status notices share app components. Make frequent draft actions primary and secondary insertion/destructive actions discoverable in menus. Task completion remains part of the patient draft, saved through the existing clinical mutation path. Whole-row controls own selection/toggle semantics, and form-level busy state cannot be overridden by a local enabled condition. Secondary screens share presentation primitives while specialized patient draft/conflict/publication workflows keep their own callbacks.
- Keep preferences separate from data operations, project administration and diagnostics. Reuse operation/state owners across focused navigation routes; privileged project controls still require live authorization below navigation. Put view filters, grouping, card layout and stored sorting in one options surface while keeping their different persistence rules explicit. Report setup exposes roster/scope/destination/format before advanced styling and preserves reviewed delivery. Desktop reuses the same separation through focused child windows and retained options sections; preview appearance using dummy content and preserve clinical drafts/revision checks. Keep dialog/view state local to the running session and appearance preferences in the existing encrypted workspace (in memory for demo).
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

Demo is an isolated in-memory workspace using the real offline patient forms, cards, task controls, roster picker, sorting, and PDF renderer. Android also reuses the real Patients/Dashboard navigation, metrics and list controls while separating report review, team/actor selection and temporary preferences into focused destinations. Omit unsupported network/admin actions; do not implement fake synchronization or invoke production settings/session ViewModels. See [Android demo](android-demo.md). It has no clinical repository or Telegram dependency. Local PDF export is explicitly available for demonstrating reports, with a demo label on every page; exit/reset discards the temporary session. Drawer density preserves 48dp targets while using a two-column context header and compact wrapping rows.

## Standalone desktop client

The current desktop source is [`desktop/`](../desktop/README.md). CustomTkinter
forms replace Excel; the existing Telegram formats and accepted retrieve/edit/
review/publish model stay unchanged. A worker serializes blocking network/file/
crypto operations. The GUI owns logical Arabic input text; PDF shaping happens
only during rendering. Authentication and publication checks live in the protocol
layer, not just navigation. Desktop can bootstrap the first admin and export join
files and perform reviewed ongoing doctor/role/destination administration.
Live authorization, stale-review checks and reference guards remain below the UI.
Unfinished desktop patient forms are encrypted device-local recovery data, separate
from saved clinical records and portable exports. Both clients describe the selected
shift against a locally known snapshot without claiming current remote freshness;
Android's device-wide pending flag is separate. Batch supervisor delivery reviews
each PDF, publishes the complete shift once when needed and records delivery per
destination. Desktop announcements reuse the existing shared announcement pointers,
with reviewed text, live admin authorization and durable uncertainty tracking.
Task completion identity/time is established at local clinical Save on both clients;
desktop publication consumes saved encrypted evidence instead of assigning credit
to its publisher. Desktop local clinical audit and report preferences remain in the
encrypted workspace. Activity access follows the authenticated local admin session;
patient-card pinning/expansion affects presentation only.
Patient editor identity/time also comes from local Save, including explicit anonymous
edits, rather than the later publisher. Desktop clock refresh changes presentation
only. Both announcement workflows review literal text, retain project encryption,
persist uncertainty before sending and repair confirmed references without replacing
a subsequently changed announcement. Android reuses its report delivery ledger in a
separate scope and refuses to create a new synchronization pin during this workflow.
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

Both clients protect saved pending edits from complete downloads until publication.
Desktop patient rounds use the captured displayed order for Save and next, while
general/detail clinical editors share one draft. Appearance is device-local and
independent from report colors and clinical severity. Portable shift recovery
reuses Android's existing encrypted `.rpb` CSV format; desktop `.srdraft` remains
workspace recovery. Because `.rpb` lacks a project identifier, desktop binds import
to the selected project shift and existing registry, requires explicit replacement
review, and keeps device delivery receipts outside restored state.

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

The admin-only names-only team PDF groups patients by their assigned supervisor.
Resident membership is an independent one-supervisor registry link, stored through
Android's existing doctor options and a shared Telegram column. Unassigned residents
are allowed during setup. Both platforms list all linked residents, including those
without a patient, in one row, followed by numbered
patient-name rows. Fixed A4 portrait pages contain three 56.7 mm tables per row,
with global numbers flowing left to right and onto subsequent A4 pages as needed. Full desktop reports
mirror Android's current Classic/card layouts and palette semantics; local
per-supervisor exports and readable names/PDF exports do not publish clinical data.

Assignments-PDF access is enforced below navigation: Android reuses its session/
current-registry AdminAuthorizer in rendering and final export; desktop uses live
Telegram session/membership and registry-role checks. Recheck the same actor
before saving so an open dialog does not retain withdrawn authority.


Desktop local access uses clinician/account-bound PIN hashes inside its encrypted
workspace, with persistent delays and an idle screen lock that preserves hidden
editors. It unlocks offline work from the cached roster; shared publication and
administration retain fresh Telegram identity/membership checks. PIN records never
travel through CSV, Telegram or portable draft backups. Desktop demo is a separate
in-memory workspace with no Telegram transport, using the real local forms and
labelled PDF renderer. Portable doctor CSV is an explicit reviewed admin operation
through the existing registry publication path; it does not enable background sync.


New-shift creation and retrieval are explicit separate actions. New-shift creation
copies the entire preceding shift and unfinished tasks into new patient records,
with one keep/clear responsibility choice. It cannot overwrite a nonempty target;
Android checks source membership/revisions and target revision inside the clinical
transaction, and desktop rechecks remote pointers after review. Copying is local
work pending publication. CSV import retains omitted active doctors; its preview
separates added, changed, unchanged imported and retained records before a complete
registry publication.


Patient-level cross-team cover is permitted without mutating permanent team
membership or the patient's supervisor. Forms make that policy explicit rather
than filtering away valid covering residents. Reviewed report sending ensures data
publication before delivery on both platforms; desktop skips a redundant publication
for identical accepted content so its existing delivery identity/UNKNOWN guards
survive retries. Full-ward publication review remains required even when the sent
report is limited to one supervisor. Cancellation before final confirmation has no
publication side effect. Shift-copy confirmation exposes source date and freshness
rather than silently treating a local snapshot as a current remote snapshot.

## Reviewed PDF and delivery identity

Android binds each general/supervisor PDF send to the exact private-cache artifact
opened for review, its SHA-256 and the reviewed clinical content/export options.
Changed content/options or a missing artifact require another preview. Delivery
metadata persists UNKNOWN before network access and blocks retry by shift and
destination until the sender or an active admin explicitly resolves the outcome.
Supervisor report identity includes the supervisor ID, while UNKNOWN blocks the
shift/destination regardless of report subset. Desktop likewise blocks unresolved
shift/chat/topic deliveries independently of format and publication identity,
checks before publication and at the send boundary, and requires live sender/admin
authorization for manual resolution. Confirmed identical content requires explicit
extra-copy permission. These local ledgers complement review and last-write-wins publication; it cannot guarantee
exactly-once delivery across devices or prove non-delivery after a timeout.
Historical assignment PDFs use the current independent team roster and label that
limitation, while patients retain the selected shift's assignments.

## Ward workflow controls

Show known local publication/download/delivery state together, with a clear next
action; a recorded download time does not claim remote freshness. Ward-wide task
lists reuse structured tasks and existing patient save flows, changing display
only. Batch resident-team changes require one review and authorization/stale-state
checks below the UI, preserving patient assignments and individual cover.

Patient details and editing rounds use stable patient IDs from the displayed
sequence; local activity is scoped by patient/shift and remains admin-only. Android
Settings recovery publishes through the same reviewed snapshot flow as ordinary
Settings publication. There is no force path around a changed remote pointer;
review tokens bind local CSV, published clinician and patient/doctor pointers and
stay in device memory. Restoring local data does not itself publish or send a report.
