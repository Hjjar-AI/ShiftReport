# Shared Telegram protocol

Contract for the standalone `desktop_protocol.py`, `desktop_store.py` and
`desktop_gui.py`, based on current Android production source.
[Usage](../../desktop/README.md) · [Verification limits](../status/current-state.md#verification-and-limits)

## Formats

| Payload | Current representation |
|---|---|
| Join | `.srjoin.json`, `format=ShiftReportJoin`, encrypted `RUSHDJOIN1` payload, inner version 1 |
| Shared pointer | Pinned JSON `kind=shift-report-sync-state`, version 4 |
| Patient document | Bundle 1, `# rushdShiftBundle=1`, boundary `#==RUSHD_SHIFT_BOUNDARY_7F3A9C==` |
| Shift CSV | Schema 7, exact 22 columns and full quoted metadata field |
| Doctor Telegram wire | Ten columns including teamSupervisorId; distinct from schema-1 spreadsheet CSV |
| Encrypted file | `SRDATA1` + 12-byte nonce + AES-GCM ciphertext/tag |
| Encrypted text | `SRMSG1:` + Base64 of encrypted gzip text |
| Portable shift backup | `.rpb`: `RUSHDBK1` + 16-byte salt + 12-byte nonce + AES-GCM ciphertext/tag; single CSV7 document; password PBKDF2-HMAC-SHA256/210000/256-bit, no AAD |

The desktop accepts the current contract only. No schema renumbering or old-schema
conversion was added. The Android metadata-quoting fix preserves roster commas,
sort JSON, snapshot/base IDs and revisions without altering clinical columns or
the decoder. Android must rebuild/republish before shared use.

## Connection, crypto and identity

- Read connection/topics/optional shared key from the protected join file by default.
  Explicit desktop `CONNECTION_MODE=raw` accepts local plaintext credentials for
  occasional admin startup. It verifies a current-registry admin over Telegram
  before opening the workspace, and rechecks admin status below the UI on shared
  actions. Existing-session reconnect remains protected-file-only. This client
  gate cannot prevent direct Telegram access with an extracted token.
  Passwords are prompted interactively, never passed as process arguments or saved in .env.
- Join derives a 32-byte key using PBKDF2-HMAC-SHA256, 210000 iterations and a
  16-byte salt; AES-GCM uses a 12-byte nonce and no join AAD. Data envelopes retain
  Android's purpose-specific authenticated bytes. Wrong keys/tampering fail explicitly.
- Encryption is chosen at project creation. No hidden-sheet/Base64 substitute or
  plaintext fallback is allowed; readable PDF/captions are the deliberate exception.
- Desktop identity is proven by a private six-digit Telegram code, expiring after
  three minutes/five attempts; verified sessions last thirty minutes. The client
  does not consume `getUpdates`. Mutations recheck live doctor linkage/membership.
- Bot possession, actor IDs and editable actor fields are not authorization.
  Registry/role/destination mutations require live admin authorization below the GUI.
- Project fingerprint binds bot identity, group/topics and data key. Different
  projects/keys/topics cannot be mixed. Baselines use the stable project key or a
  random local key protected by Windows DPAPI or a Linux passphrase; same-bot token
  replacement preserves them. Linux stores only the authenticated wrapped key;
  its local passphrase is entered interactively and never saved.

## First-admin bootstrap and join export

`desktop_project.py` creates the existing registry/index formats without changing
versions. No registry exists to authorize the first actor, so bootstrap requires
both a private Telegram challenge and live human group owner/admin status. The bot
must be an admin with pin rights. Before each group write, recheck authority,
frozen configuration and absence of any pinned message. Review seals the profile;
UI bypass, stale reviews and reused approvals fail below the UI.

A new, unused project workspace records an encrypted UNKNOWN creation receipt
before uploading the registry. Existing baseline/draft/receipts block initialization.
Upload the first permanent admin with a stable UUID, then send the JSON index in
General, pin it and read back the exact index and registry. Only then establish
the clinician session and confirm the receipt. Failures leave the receipt for
manual inspection/reconnect; there is no automatic publication retry or deletion.
These checks do not make concurrent creation atomic or prove empty chat history.

Protected join export requires a current verified registry admin. Serialize only
connection/topics/key into the existing password-encrypted Android join envelope;
refuse overwrites, recheck authorization before writing and reopen/verify the
new file. Secrets/passwords never enter the clinical workspace or receipt payload.

## Download and draft

Resolve/decrypt the pinned index, registry and bundle; validate everything before
replacing a draft. Back up the encrypted workspace, select the shift explicitly and preserve
all stable patient/doctor/task IDs, revisions, sort/roster and app-only fields.
Encrypted local drafts never contain raw connection secrets. Stored patient list
order is published as CSV row order; search/filtering never removes hidden patients
from publication. Concurrent order changes require review.

Only the current 08:30-boundary shift can be edited/published/sent, using the same
IANA time zone as Android. All active doctors can appear in the roster, without a
fixed cap. Patient capacity follows the current app limit. New tasks may be
unassigned; completion is stamped with verified identity/time and notes never
become tasks. Continuing patients/unfinished tasks require explicit new-shift review.

## Publication

1. Fetch the current remote snapshot for authority/pointer review. The full local
   shift is the proposed replacement, including deletions, roster and stored order.
   Do not merge remote fields or additions into it.
2. Recheck the live actor, registry/pointers, exact reviewed plan and draft hash.
   A changed review requires new approval. Removed rows are part of the reviewed full replacement.
3. Persist an UNKNOWN receipt before requesting an immutable upload. Recheck
   pointers, then update the existing pinned index while retaining unrelated
   registry/announcement/recovery fields.
4. Preserve ten-hour recovery and the journal (up to twelve entries, within the
   message-size budget). Read back the actual current pointer before success.

Pointer checks do not provide atomic multi-client transactions. The accepted
workflow remains retrieve/edit/publish with last-successful-write-wins. No blind
force endpoint or automatic upload retry exists. An upload alone does not prove
its snapshot became current. A matching journal entry may confirm a publication;
absence from the bounded journal does not prove failure.

## Desktop registry mutation and local recovery

`desktop_doctors.py` publishes the ten-column registry with explicit teamSupervisorId.
The preceding nine-column header is accepted as unassigned membership on retrieval. Review binds action, proposed registry, actor, local draft and
remote patient/doctor pointers. Recheck live membership/admin status below the UI,
protect permanent admins and demotion rank rules, and block unsafe clinical
reference removal. Persist UNKNOWN before upload; re-read both pointers and
admin authority before updating the existing index; verify the exact new registry
and pointer afterward. Keep the patient baseline/draft and unrelated index fields.
This uses the accepted last-successful-write-wins model, not atomic transactions.

Deleted local rows and their previously published completion history remain in
optional encrypted workspace recovery records; SQLite and interchange schemas
are unchanged. Restoration checks shift, expected draft and duplicate ID, retains
full content and position, and requires complete-snapshot review before publication.
Recovery travels with `.srdraft` backups; delivery receipts remain device-local.

Desktop local patient Save establishes completion identity/time from an authenticated
local PIN session or valid Telegram identity. Saved encrypted completion evidence
travels with draft recovery; publication preserves that evidence or previously
published stamps and rejects unstamped form completions. It never assigns a new
completion to the publishing doctor. Local clinical audit is admin-only and excluded
from portable drafts; report preferences persist privately in the encrypted workspace.
Patient editor/time metadata is likewise established locally. Publication takes the
saved patient snapshot as evidence, checks its clinical content, and preserves the
editor even when a different clinician publishes. Derived task doctor labels do not
change that identity. Announcements use authenticated project text encryption when
enabled; uncertain delivery blocks repetition until manual resolution, with safe
repair of the expected announcement reference and preservation of newer references.

## Desktop local access, CSV and demo

Local PIN hashes and persistent throttling are optional private records inside
existing encrypted workspace storage; they never enter `.srdraft`, patient CSV,
doctor CSV or Telegram. Enrollment requires fresh verified clinician membership.
Idle/manual locking hides clinical windows, preserves unsaved editors and clears
network authority. PIN checks use the cached doctor/account binding and unlock
local operations only; shared writes retain fresh Telegram authorization.

Doctor spreadsheet import/export uses the existing schema-1 columns; patient
export uses a single schema-7 CSV rather than a bundle envelope. Registry import
is a reviewed admin publication using the guards above. It retains local stable
IDs, omitted active doctors and protected permissions, rejects ambiguous identity
matches/unsupported extra options and never imports PIN/sync aliases. Wire-only
storage does not acquire username/timestamp/tombstone fields from the CSV.
The shared team link is carried by the Telegram teamSupervisorId column and the
existing schema-1 CSV extra_options entry teamSupervisor:<id>. Each resident has
at most one supervisor; unset links represent an unassigned resident. Both updated
clients are needed when publishing team membership. Android persists the link in
its existing options storage, so no Room migration/version change is needed.
Multi-level sorting retains Android's existing compact sort metadata and saved
row positions, normalized before publication review. Grouping is local presentation.
Demo is a separate memory-only workspace/client without Telegram transport;
explicit local PDFs carry DEMO on every page.

## Report delivery

Report sending prepares/reviews the full replacement and report before final
confirmation. Pending changes are published through the same guarded publication
path, followed by exact-content delivery. Unchanged accepted content reuses the
remote publication and existing report receipt key. Cancelling before final
confirmation performs no publication. A delivery failure cannot roll back a
successful data publication. Separate manual publication remains available.


PDF options cover Android-style seven-column Classic tables, paragraphs, linked
A5 cards, paper/orientation, five matching palettes, dark mode and ward context.
Separate local supervisor files include unassigned patients. A separate local
names-only export requires a current verified admin and live membership/registry
checks below the GUI, including inside the renderer and before saving. It groups
patient names by supervisor, with independently enrolled team residents in one
comma-separated row (including residents without patients and empty supervisor teams); it uses fixed A4 portrait pages with three 56.7 mm tables per row and continuous
left-to-right patient numbering. Long tables/pages continue without dropping names. Logical text reports retain the selected patients/roster and all
clinical content. Review then explicitly confirm. Encrypted projects upload a
protected text document; unencrypted projects split plain text by UTF-16 units.
One UNKNOWN receipt precedes the first message and remains UNKNOWN if a later
part fails. Never silently retry or claim that partial delivery rolled back.


Generate a fresh paginated ReportLab PDF with all clinical fields, structured tasks/completions,
badges, roster and context. Omit routine/unassigned and patient last-edit labels,
without dropping content. Preview the exact file and confirm destination; recheck
the current snapshot/registry and reviewed draft before sending.

General PDFs go to Reports; supervisor PDFs use the live registry destination.
Check Telegram `ok` and message identity before reporting sent. Keep local delivery
receipts; they do not populate Android's local sent/audit view. Ambiguous operations
remain UNKNOWN until explicitly resolved. Report UNKNOWN blocking uses shift,
chat and topic independently of format/publication keys, before publication and
at the delivery boundary. Local receipt metadata retains the report shift ID;
unlabelled unresolved receipts conservatively block their destination. Manual
resolution requires live same-project sender/admin authorization below the UI.
Confirmed reports need explicit approval
for another copy. A confirmed send followed by failed local bookkeeping is still a send.

Single PDF/text delivery and supervisor PDF batches share `desktop_delivery.ReportDelivery`.
Preview/confirmation remain with the caller; the coordinator owns draft/destination
checks, publication reuse and durable receipt handling. `desktop_tasks` supplies
task validation, counts, deadline/display and rollover rules to draft, wire and
report paths. Completion validation does not establish trust: clinical Save stamps
the authenticated actor, and publication still requires saved completion evidence.

## Evidence

Patient details navigate stable IDs from the captured visible sequence; scoped
local activity requires current admin authorization for every read. The reusable
readonly pane copies the current logical patient text when its content changes.

Desktop `.rpb` import/export reuses the Android production format. Import requires
the selected shift ID/date in the current project and valid registry references,
an expected-draft recheck, review and confirmation. Recovery keeps current receipts
and saved imported task attribution. `.srdraft` remains separately labelled desktop
workspace recovery. Both clients block downloaded snapshots over pending saved
clinical changes until publication. Current-format backup/join/cipher fixtures passed
both Android → desktop and desktop → Android checks. This does not establish live cross-client GUI workflows.
[Exact results](../status/current-state.md#verification-and-limits) ·
[Remaining checks](../status/testing-handoff.md).
