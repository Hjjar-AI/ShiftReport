# Architecture

[Implementation/limits](current-state.md) · [Protocol](../desktop/PROTOCOL.md) · [Roadmap](roadmap.md)

## Accepted synchronization scope

Telegram stores manually retrieved/published complete snapshots and reviewed reports.
The last successful pinned-state write wins; no atomic multi-writer database,
remote field merge, automatic synchronization or required collaboration backend.
Local revisions, replacement review and authorization protect local work without
promising preservation of competing remote publications. Timestamps report known
events, not current remote freshness. Confirmed local-copy publication retains
clinician/registry/delivery guards and does not publish unrelated doctor edits.

## Ownership and local commits

Screens own navigation/drafts; ViewModels delegate through the same lifecycle scope.
Repositories commit revision/audit/pending changes atomically. Patient/doctor sync
shares its singleton state store/mutex. Extracting files must not introduce duplicate
sessions, review tokens, artifact workspaces, draft copies or locks. Desktop facades
retain public imports; defer optional high-level imports to avoid module cycles.
Blocking database/file/crypto/network work stays off the main thread; cancellation
is separate from ordinary failures.

## Access and project boundaries

Protected join files configure devices; creation owns manual group/topics. Same-project
reconnect validates before replacing credentials, preserves clinical/key/topic state
and works before login. Cross-project/key/topic substitutions are blocked. Signing
out retains project enrollment; local clinician PINs support return/offline access,
while first enrollment and privileged shared operations retain identity checks.
Token revocation belongs to BotFather; a token holder can access Telegram outside
client restrictions.

Authenticated Telegram encryption is optional at creation; keys remain protected and
PDFs/Telegram metadata deliberately readable. Device-key failures preserve files.
Admin authorization belongs below UI. Desktop bootstrap verifies human group admin,
private challenge, empty pin/workspace and authority again before publication; an
encrypted UNKNOWN receipt precedes writes. Explicit raw-token desktop mode keeps
plaintext settings outside bundles. [Security/setup](guides/telegram-setup.md).

## Clinical and presentation boundaries

Tasks are structured, never inferred from follow-up; optional ownership cannot block
Save/report readiness. Completion identity/time belongs to local Save, not publication.
Pending includes overdue. Rollover is reviewed whole-shift copying into an empty
target with source/target checks and keep/clear assignments. Resident team membership
and patient cover are independent; registry CSV retains omitted active doctors.
Backup/restore changes local state, never implicitly publishes or delivers.

Shared fields/actions/tokens standardize presentation while callers own drafts,
validation, busy/selection state and review callbacks. Preserve RTL, growing 48dp
targets, compact clinical-first editing/demographic-first creation, labs/per-field
tools and fixed keyboard-safe actions. Appearance/accent/clinical severity/PDF settings
are independent; preferences do not rewrite clinical content. Individual motion is
brief, bulk/reduced motion immediate. Stable IDs define filtered/grouped/pinned rounds;
buttons remain available alongside gestures. [UI guide](../android/chick.md).

## Reviewed PDF and delivery identity

Report review binds actor/date/revisions/destination and exact opened PDF content,
options and SHA-256. Changed/missing artifacts require another preview. Publication
review precedes delivery, including supervisor subsets. UNKNOWN persists before
network access and blocks retries for the shift/destination until authorized manual
resolution. Confirmed extra copies require permission. Local receipts cannot prove
non-delivery after timeout or guarantee exactly-once delivery across devices.

Classic tables and linked portrait A5 cards paginate readable full clinical content;
A4 names-only team PDFs remain admin-only and label current-roster limitations for
historical shifts. Exporting/opening a PDF is separate from sending authorization.

## Isolation and platform choices

Demo injects memory-only state into real forms/cards/renderer; explicitly requested
PDFs are labelled on every page. Reset/exit discards it; no production store/session/
transport dependency. [Demo guide](android-demo.md).

Desktop stores fingerprint-bound AES-GCM workspace state before SQLite commit, with
Windows DPAPI or Linux passphrase-wrapped keys and platform path/lock/PDF adapters.
Logical Arabic input/caret/clipboard/Save stays separate from UI/PDF shaping. Local
unfinished drafts, cached enrollment and reviewed shared operations remain distinct.
[Desktop guide](../desktop/README.md) contains operational details.

Design basis: Android adaptive layouts/Compose accessibility, structured I-PASS
handoff, NHS warning guidance and WCAG 2.2. These inform choices, not verification
claims. Deferred voice/templates and their technology notes stay in Roadmap;
unselected presentation choices stay in the [decision register](decisions/android-ui-options.md).
