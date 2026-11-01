# Shared Telegram protocol

Contract for the standalone `desktop_protocol.py`, `desktop_store.py` and `desktop_gui.py`, based on current Android production source. [Usage](../../desktop/README.md) · [Verification limits](../status/current-state.md#verification-and-limits)

## Formats

| Payload | Current representation |
|---|---|
| Join | `.srjoin.json`, `format=ShiftReportJoin`, encrypted `RUSHDJOIN1` payload, inner version 1 |
| Shared pointer | Pinned JSON `kind=shift-report-sync-state`, version 4 |
| Patient document | Bundle 1, `# rushdShiftBundle=1`, boundary `#==RUSHD_SHIFT_BOUNDARY_7F3A9C==` |
| Shift CSV | Schema 8, 24 required columns, including `assignmentChangedOn` ISO date (blank allowed) and final `labGroupsJson`; full quoted metadata field |
| Doctor Telegram wire | Ten columns including teamSupervisorId; distinct from schema-1 spreadsheet CSV |
| Encrypted file | `SRDATA1` + 12-byte nonce + AES-GCM ciphertext/tag |
| Encrypted text | `SRMSG1:` + Base64 of encrypted gzip text |
| Portable shift backup | `.rpb`: `RUSHDBK1` + 16-byte salt + 12-byte nonce + AES-GCM ciphertext/tag; single CSV8 document; password PBKDF2-HMAC-SHA256/210000/256-bit, no AAD |

Both clients require the current 24-column CSV8 header, including `assignmentChangedOn` and `labGroupsJson`; an empty assignment date remains unknown and is never restamped on retrieval/import. Current metadata quoting preserves roster commas, sort JSON, snapshot/base IDs and revisions. CSV8 appends `labGroupsJson`: an ordered array of `{id,title,date,results}` with optional ISO date (`null` when unknown). Existing `labs` is its readable text projection; inconsistent grouped text is rejected. Empty groups can retain an ungrouped `labs` field. Android database8 adds the JSON column and converts existing labs to one untitled, undated group without changing the stored text; database7 index changes remain. Desktop encrypted local snapshots gain the same groups on read. Both clients must be updated before sharing CSV8. Compilation/focused results and remaining live acceptance are recorded in Current state and the handoff.

## Connection, crypto and identity

- Optional `residentsChatId`/`residentsTopicId` join connection fields route general PDF/text reports to a separate group/topic; zero group retains main group/Reports topic. All topics may be omitted. CSV/registry/pinned index keep the main destination; login challenges remain private. Routing may change after creation under published admin authority; redistribute a fresh protected join file. Android reconnect applies reviewed residents routing while preserving main bot/group/data topics/key. Desktop excludes these mutable fields from clinical workspace identity and stores local overrides in a separate authenticated encrypted connection file, never clinical drafts. Report review binds the resolved route. Group/forum/bot-admin checks are read-only; topic existence/open state is established only by actual delivery. API metadata follows [Telegram ChatFullInfo](https://core.telegram.org/bots/api#chatfullinfo).
- Default protected join supplies connection/topics/key. Explicit desktop raw mode verifies live registry admin before workspace access and on shared mutations; reconnect remains file-only. Passwords interactive, never argv/env. Extracted tokens bypass client gates.
- Current join imports require the protected JSON envelope; binary-only join files are unsupported. Android pinned indices require the current version and file-backed registry, with no inline registry or directly pinned registry-document bootstrap.
- Join: PBKDF2-HMAC-SHA256/210000,16-byte salt,32-byte key; AES-GCM12-byte nonce,no AAD. Data envelopes preserve purpose-specific authenticated bytes. Wrong keys/tampering fail; no encoding/plaintext fallback. Encryption chosen at creation; PDF/captions readable.
- Private six-digit Telegram challenge, entered in the originating app: desktop3min/5attempts/session30min; Android5min/5attempts with separately configured local session locking. Neither login reads/acknowledges `getUpdates`. Android keeps only a salted challenge hash in memory, binds the challenge to project/doctor/linked identity, and reuses an unexpired challenge after an uncertain send/cancel. PIN re-entry requires device-local Telegram enrollment bound to the current identity and PIN hash; administratively preset PINs do not enroll accounts. Shared mutations recheck linked identity/live membership; actor fields/token possession are not authorization; registry/role/destination changes require live admin below GUI.
- Fingerprint binds bot/group/topics/key. No cross-project mixing; stable shared key or random DPAPI/Linux-passphrase-protected local key survives same-bot token replacement. Linux stores authenticated wrapped key, never passphrase.

## First-admin bootstrap and join export

`desktop_project.py`: unused workspace/no baseline/draft/receipts, private challenge, human group admin and bot pin rights. Bound single-use review rechecks authority/config/absent pin before each write. Persist encrypted UNKNOWN → permanent-admin stable UUID registry → General JSON index → pin → exact index/registry readback → clinician session/confirmed receipt. Failure requires inspection/reconnect, never automatic retry/deletion. Neither atomic creation nor proof of empty history. Android creation uploads the registry, sends/pins its current JSON index and bootstraps that exact index. Initial unrelated-pin review reads raw metadata under the creation authority/config checks; established-project reads retain strict decryption. Android emits and requires explicit current index kind/version.

Join export: current verified live registry admin, only connection/topics/key in existing protected Android envelope; refuse overwrite, recheck before write, reopen/verify. No secrets in clinical state/receipts.

## Download and draft

Resolve/decrypt/validate index/registry/bundle before replacement; encrypted pre-download backup; current bundle readers/writers require exactly one ward. Android manual full Fetch replaces pending local patients and doctors together, with baseline/cursor/Undo in one transaction; no local/remote merge. Undo restores pre-Fetch patients/registry locally after unchanged-download and assignment validation, never republishes. Doctor-only Fetch also replaces pending portable registry edits but requires retained current clinical references to remain valid; otherwise use full Fetch. Explicit ward IDs never resolve through another ward of the same date; both rows can retain their stored dates. Historical doctors may share a name with the incoming roster; active roster names/accounts remain validated. Registry replacement releases obsolete account bindings transactionally before inserting the incoming rows, retaining Telegram-account uniqueness. Current CSV rows must have the full header width and numeric revision/update metadata. Plain unwrapped CSV is not a Telegram bundle; the current `.rpb` backup still deliberately carries a single CSV document. Preserve patient/doctor/task IDs,revisions,sort/roster/app-only fields; no connection secrets in drafts. CSV row order is stored order; filters never remove unpublished rows. Desktop draft/order changes invalidate its publication review.

08:30 labels the work day only; no date-based edit/publication/send deadline or shift copy-forward. Keep the current ward across days. Old-app CSV containers are unsupported; dated-list browsing/copying is retired. Fetch Undo and reviewed admin republishing of the previous publication remain.

## Publication

1. Fetch authority/pointers; review **complete local replacement**, including deletions/roster/order; no remote merge.
2. Android sends captured reviewed CSV/text/PDF, leaving subsequent local edits pending; recheck live identity/authority/assignments/destinations. Combined reviewed registry publication runs before patients under admin authority. Desktop retains its existing plan/pointer checks.
3. Persist UNKNOWN before immutable upload; recheck pointers, update existing pinned index retaining unrelated fields.
4. Retain immediate previous immutable publication and bounded journal (≤12entries/message budget); read back actual pointer before success.

Last-successful-write-wins, not atomic transactions. No automatic retry. Android manual Fetch accepts the current pin, including an intentionally restored pointer with a lower document ID; historical high-water metadata does not veto it. Upload alone is not publication. Matching journal may confirm; absence from bounded journal does not prove failure.

## Desktop registry mutation and local recovery

`desktop_doctors.py`: ten-column team-aware registry with explicit UID and team column; old nine-column registries are unsupported. Bind action/proposal/actor/draft/both pointers; protect permanent admins/ranks/clinical references. UNKNOWN before upload; live admin/pointers rechecked before index update, exact registry/pointer verified after. Preserve patient baseline/unrelated index fields; same last-write-wins limits.

Encrypted recycle records retain full content/position/previous completion evidence. Restore checks shift/expected draft/duplicate ID and needs full publication review. Recovery travels in `.srdraft`; receipts stay local. Clinical saves create no new editor/completion/audit stamps; legacy fields remain compatible. Audit excluded from portable drafts; report preferences private. Announcements and their obsolete connection/index fields are removed.

## Desktop local access, CSV and demo

PIN hashes/throttling remain encrypted local records, excluded from drafts/CSV/Telegram. Fresh verified membership enrolls; lock hides clinical windows, retains editors and clears network authority. Cached binding unlocks local work; shared writes still require fresh authority.

Doctor spreadsheet schema1; patient export single CSV8. Reviewed admin import retains stable IDs/omitted active doctors/protected permissions; rejects ambiguous identity/unsupported options, excludes PIN/sync aliases and wire-inapplicable username/time/tombstones. Teams: Telegram `teamSupervisorId`, CSV `extra_options=teamSupervisor:<id>`; ≤1supervisor/resident, blank unassigned. Updated clients needed; Android existing options storage/no schema change. Sort metadata/positions normalized before review; grouping presentation-only. Demo memory-only/no transport, every PDF page DEMO.

## Report delivery

Review full replacement and exact report before final confirmation; publish pending changes through guarded path, then deliver. Unchanged content reuses publication/receipt; cancel publishes nothing. Delivery failure cannot undo publication; standalone publish remains.

ReportLab full clinical/tasks/badges/roster/context; omit routine/unassigned/last-edit labels. Classic7-column/paragraph/A5, palettes/shading/A4-A5 portrait-light/Medium separators and explicit local supervisor files including unassigned. Names-only A4: verified live admin below GUI/renderer/save,3×56.7mm tables/row, independent resident row/empty teams/unassigned group, global numbering/continuations. Exact PDF opening/options/hash/destination and current snapshot/registry/draft rechecked. Text: encrypted document or UTF-16-bounded plain messages, one UNKNOWN before first part; partial failure stays uncertain, no silent retry/rollback.

General destination Reports; supervisor live registry group. Telegram `ok`/message identity required. UNKNOWN keyed across shift/chat/topic regardless of format/publication; current report receipts require their shift label; malformed receipts fail validation. Live same-project sender/admin resolves; extra confirmed copy requires approval. Confirmed network send remains sent if local bookkeeping fails. Device-local receipts never populate Android audit or establish non-delivery elsewhere.

`desktop_delivery.ReportDelivery` owns draft/destination/publication reuse/durable receipts for single/batch reports; caller owns preview/confirmation. `desktop_tasks` supplies checklist validation/count/display; legacy task metadata is compatible storage only.

## Evidence

Details follow captured visible stable IDs; readonly copy returns logical text.

Android-compatible `.rpb`: same project/selected shift ID/date, valid registry references, expected-draft check/review/confirmation; retains receipts/imported completion attribution. `.srdraft` remains separate workspace recovery. Android manual Fetch confirms complete replacement, including pending saved changes, with local pre-Fetch Undo; successful publication does not immediately fetch its own snapshot; desktop retains its guarded replacement flow. Current-format backup/join/cipher fixtures passed both directions; live GUI workflows unverified. [Results](../status/current-state.md#verification-and-limits) · [Remaining](../status/testing-handoff.md).

Android delivery-copy permissions use the existing receipt fields to bind exact PDF SHA-256, full chat/topic, format and originating linked actor. Clinical fingerprints still bind reviewed report content; they alone cannot authorize a regenerated PDF. UNKNOWN remains durable and requires explicit resolution. Source changes of1 November are not compile/runtime/live evidence.
