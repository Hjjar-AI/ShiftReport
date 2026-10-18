# Shared Telegram protocol

Contract for the standalone `desktop_protocol.py`, `desktop_store.py` and
`desktop_gui.py`, based on current Android production source.
[Usage](README.md) · [Verification limits](../docs/current-state.md#verification-and-limits)

## Formats

| Payload | Current representation |
|---|---|
| Join | `.srjoin.json`, `format=ShiftReportJoin`, encrypted `RUSHDJOIN1` payload, inner version 1 |
| Shared pointer | Pinned JSON `kind=shift-report-sync-state`, version 4 |
| Patient document | Bundle 1, `# rushdShiftBundle=1`, boundary `#==RUSHD_SHIFT_BOUNDARY_7F3A9C==` |
| Shift CSV | Schema 7, exact 22 columns and full quoted metadata field |
| Doctor Telegram wire | Current nine-column records; distinct from schema-1 spreadsheet CSV |
| Encrypted file | `SRDATA1` + 12-byte nonce + AES-GCM ciphertext/tag |
| Encrypted text | `SRMSG1:` + Base64 of encrypted gzip text |

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
  Doctor-registry/role/destination mutation remains in Android.
- Project fingerprint binds bot identity, group/topics and data key. Different
  projects/keys/topics cannot be mixed. Baselines use the stable project key or a
  random local key protected by Windows DPAPI or a Linux passphrase; same-bot token
  replacement preserves them. Linux stores only the authenticated wrapped key;
  its local passphrase is entered interactively and never saved.

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

1. Compare draft against its sealed baseline and freshly fetched remote data.
   Preserve remote additions; require field/deletion/roster/order choices for conflicts.
2. Recheck the live actor, registry/pointers, exact reviewed plan and draft hash.
   A changed review requires new approval. Missing saved rows are not silent deletes.
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

## PDF delivery

Generate a fresh paginated ReportLab PDF with all clinical fields, structured tasks/completions,
badges, roster and context. Omit routine/unassigned and patient last-edit labels,
without dropping content. Preview the exact file and confirm destination; recheck
the current snapshot/registry and reviewed draft before sending.

General PDFs go to Reports; supervisor PDFs use the live registry destination.
Check Telegram `ok` and message identity before reporting sent. Keep local delivery
receipts; they do not populate Android's local sent/audit view. Ambiguous operations
remain UNKNOWN until explicitly resolved. Confirmed reports need explicit approval
for another copy. A confirmed send followed by failed local bookkeeping is still a send.

## Evidence

Offline crypto/Unicode CSV/bundle/baseline/merge checks and source/artifact checks
passed for the implementation snapshot. A long dummy Arabic PDF generated eight pages and its first page was visually
inspected. Windows 7/DPAPI/Tk execution, live Telegram and current Android
compilation/device behavior remain unverified. See the desktop README.
