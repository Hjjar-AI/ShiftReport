# Current implementation

Updated 2026-10-19. [Roadmap](roadmap.md) · [Architecture](architecture.md) ·
[Changelog](changelog.md) · [Troubleshooting](troubleshooting.md)

## Product and source layout

ShiftReport is an Arabic-first RTL Android ward/handoff app. Each device works
from encrypted local Room data; Telegram stores immutable documents and shared
pointers. Users retrieve complete snapshots, edit, review and publish complete replacements. Competing remote
pointer writes remain last-successful-write-wins; Telegram is not an atomic
multi-writer database and no separate backend is required.

| Layer | Responsibility |
|---|---|
| `android/app/src/main/java/com/hos/rushdpatients/ui/` | Compose screens and Hilt ViewModels; dedicated ward navigation, forms, dashboard and review components |
| `android/app/src/main/java/com/hos/rushdpatients/domain/` | Clinical rules; `report/ReportReadiness.kt`, `task/PatientTasks.kt`, sorting, backups and exports |
| `android/app/src/main/java/com/hos/rushdpatients/data/repository/` | Persistence, expected revisions, transactional mutations and audit |
| `android/app/src/main/java/com/hos/rushdpatients/network/telegram/` | Bot API transport |
| `android/app/src/main/java/com/hos/rushdpatients/sync/` | CSV/bundles, merging, synchronization and shared pinned-state coordination |
| `desktop/` | Standalone CustomTkinter Windows/Linux client, encrypted drafts and Office-free PDFs |

The repository is split into `android/` (the entire Android Studio/Gradle
project), `desktop/` (the Python Windows/Linux client), and shared `docs/`.
Android-local SDK/signing/import files and IDE/build caches were moved intact under
`android/`, with ignore rules updated before moving private files. The old ignored
Excel workspace is now `local/excel/`. Open `android/` in Android Studio and run
Gradle there; the existing command list is preserved in the root README.
Portable IDE mappings now resolve the Android modules and parent Git root.

The reorganization preserved hashes of 274 non-test/non-migration Android source,
resource and Gradle configuration files, plus the filesystem identities of moved
private artifacts. Source tests/migrations were not inspected or edited. No Gradle,
compilation, build or packaging task ran. Historical protected review documents
still show the old repository-root `app/` source paths: resolve those under
`android/app/`. Their file permissions prevent updating those historical links.
Current documentation and entry points use the platform folders.

Current stack: Java 17, Kotlin 1.9.0, AGP 8.5.0, Compose BOM 2023.10.01,
Hilt, WorkManager, coroutines and Room/SQLCipher. Android minimum API 23;
compile/target SDK 34. Application version `1.1.1.20261007`, code 2; debug adds
`-debug`. No toolchain or format versions were changed for Excel integration.

## Startup and navigation

`MainActivity` shows the RTL splash before resolving app repositories, then
project setup if needed, clinician login/PIN unlock, and ward navigation.

- Splash: dark gradient, unchanged emblem on a 120dp white field, animated grouped
  credit cards, consistent names and automatic app version. Any tap skips;
  a saved monotonic deadline limits it to ten seconds across Activity recreation.
  Existing About content and logo are preserved.
- Setup: in-memory Demo, protected-file Join (default), or Create. Demo uses
  hot-beverage patients/tree-named doctors, real offline forms/cards and explicit
  per-page-labelled local PDF export; it writes no clinical database or Telegram
  data. Reset/exit discards it; process death starts a new demo.
- Ward: Patients/Dashboard, plus Activity only for admins across all entry points.
  Compact floating rounded bottom tabs retain 48dp targets and transparent side
  gutters; list content scrolls behind them with end clearance. From 600dp a rail
  is used. Drawer has compact groups and a two-column hospital/user/shift header.
- Adaptive panes: list/detail at usable 760×360dp or larger; a 48dp accessible
  resizable divider, fold-safe regions and retained selection/scroll state.
  Compact layouts use full-screen details/support sheets. Phone card taps edit;
  pane taps select details.
- Rounds follow filtered/pinned/grouped order with Previous/Next, identity and
  position. Editors/save operations guard navigation. Successful edits retain
  filters and reveal/highlight the patient; filter-hidden results are explained.

## Visual design system

Shared fields, pickers, actions, notices, section indexes, searches, whole-row
choices/toggles, loading/metric/comparison rows and PDF-style controls use theme
shapes, typography and `UiSpacing`. Busy form state also gates locally enabled
controls. Retry retains its timed hold with native button/keyboard semantics.
Settings content and shortcuts share an ordered definition; filters apply live
and retain patient scope. Arabic number/time/role terminology is shared.

Appearance (System/Light/Dark/AMOLED) is independent of Atlas, Forest, Amethyst,
Amber, Cherry, Turquoise, Copper or Olive accents. Tinted surfaces replace the
plain white canvas; severity uses shared `patientBadgeColors`, and diagnosis
categories do not use error colors. Static checks covered 82 theme text pairs
(minimum 5.43:1); this is not rendered-accessibility verification.

Input fills contrast subtly with the background in every state. `AppField` paints
fill behind the measured native container while its decoration stays transparent,
so the floating-label cutout clips the border without a rectangular fill patch.
Native semantics, errors, helper text, shape and minimum constraints remain.

Cards keep horizontal header actions, pinning in the menu/accessibility actions,
concise collapsed summaries and nonzero task counts. Expansion retains full
clinical content. Individual transitions are brief; bulk expansion is immediate.
The missing `gridItems` import alias reported in a refresh build was restored.

## Patients, tasks and persistence

- Full-screen add/edit/copy retains one General/Advanced draft, labs in both modes,
  validation links, explicit discard, and keyboard-safe Save/Cancel. Editing starts
  with clinical/tasks; new entry starts with demographics. Saved identity and
  centered compact Clinical/Labs/Tasks/Personal shortcuts remain visible.
- Age first, «أو», birth year share a row; admission number/date, sex/companion,
  and both responsible-team pickers are paired. Numeric admission/PIN keyboards,
  diagnosis-specific placeholders and task time pickers are retained. Mode labels:
  «حقول إدخال عامة» / «حقول إدخال مفصلة».
- Default-off Save and next runs ordinary validation/revision checks and advances
  to the captured displayed successor only after successful save. Failed/stale
  saves retain the editor; missing successors return normally. Demo stays in memory.
- Room schema 5 stores tasks with the patient. CSV schema 7 has 22 clinical columns;
  doctor spreadsheet CSV is schema 1. Patient/shift revisions protect stale drafts.
- `WardMutationRepository` commits clinical changes, pending state and audit in
  one IO transaction. Sort writes positions and changed revisions together;
  local CSV, bundles, text and PDF share ordering. Rollover rejects nonempty targets.
- Stale patient/roster/sort edits retain a draft for explicit saved/draft review;
  choosing values does not save. A subsequent save uses the reviewed revision.
- Tasks have stable IDs, description, optional owner, priority, deadline, state and
  completion identity/time. New tasks start unassigned. Pending includes overdue;
  cards count per patient, dashboards ward-wide. No acknowledgment workflow exists.
- Repository save stamps completion and validates task owners; reopening clears
  completion. Narrative notes never become tasks. Rollover retains unfinished tasks
  and deadlines, clearing owners when reassigning; copies start without tasks.
- Tasks persist through backup, CSV, sync, conflict review and reports. Shared display
  puts overdue first/completed last without rewriting stored order. Deadlines use
  absolute instants and the 08:30 local shift boundary.

## Project connection and authorization

Encrypted device preferences hold connection, topic IDs and any shared data key;
credentials are absent from APK source, clinical exports and backups. Android
application backup is disabled. PINs use salted PBKDF2 and constant-time comparison.

Join uses a password-protected `.srjoin.json`. Create alone accepts manual
connection/topic entry and can generate a random shared 256-bit data key. Join
export stages complete ciphertext privately; Android 10+ primarily saves a
pending MediaStore row to `Downloads/ShiftReport`, verifies before/after publication
and deletes failed new files. Sharing/optional picker remain; older Android uses
sharing. Only random staging IDs enter saved state. The reported empty-file cause
has not been reproduced on the owner's device.

Same-project reconnect is accessible before login. Candidate bot/group/admin/
pinning and encrypted pinned-text checks run before committing the replacement;
manual token entry requires live admin authorization. Only the token changes:
clinical records, pending edits, topics, bases and shared key remain. Different
project/key/topic files are blocked. BotFather revokes old tokens; distribute a
fresh protected join file afterward. Join files are reusable, not one-time access.

Android Settings gates project administration against both navigation access and
the current session/active doctor registry. Non-admin Settings state excludes
supervisor destination records; token replacement and join-file export controls
are admin-only. Credential/passphrase dialogs close and clear their input when
admin access changes. File-based reconnect remains available to non-admins and
before login. Tokens and shared keys are not displayed in Settings. These UI
changes were checked by source inspection and whitespace checks only; no Android
build or device verification was performed.

Sign-out retains project, registry and local PINs. Enrolled doctors can re-enter
through `LocalPinLoginManager`, which rechecks active identity/hash and persists
attempt limits: five failures block for five minutes. First enrollment and forgotten
PIN verification use Telegram. PINs/passphrases never enter logs or saved UI state.
Biometric/automatic locking is optional. `AdminAuthorizer` rechecks the session and
live registry below navigation for privileged mutations.

Doctor mutation/import authorization, reviewed snapshots, protected identities,
reference guards, pending state and audit commit together. CSV import previews the
full proposed merge, rechecks actor/registry/references at commit, excludes PINs and
preserves local aliases/PINs for unchanged identities. Telegram-identity changes
invalidate old PINs. The initial manual patient importer is not the shared-update
path; historical data-integrity findings are tracked separately in
[the review record](reviews/data-integrity.md).

## Synchronization and encryption

Patient/doctor channels share `RemoteSyncStateStore` and its mutex for pinned IO;
doctor orchestration lives in `DoctorSyncCoordinator`. Both platforms use manual
full-snapshot downloads and publication. Patient records, deletions, stored order
and roster are taken from the reviewed local snapshot; remote fields/rows are not
merged into it. Android doctor synchronization likewise replaces the full portable
registry, preserving device-only PINs/aliases and protected identities/references.
Pending local work blocks a destructive Android download until publication; desktop
requires explicit draft replacement and keeps encrypted recovery. In-flight edits,
local revisions and report confirmation remain protected. Immutable documents,
three-shift bundles, recovery and the publication journal remain unchanged.

Android no longer schedules background synchronization. The worker, scheduling
hooks, interval and auto/Wi-Fi controls were removed. Startup cancels the old unique
job. A manual publish performs one attempt, without automatically re-uploading on
a publication race. Concurrent pointer changes require renewed review; the last
successful pinned write remains authoritative, not an atomic transaction.

Local revisions and pointer rechecks detect stale work; remote competing writers
can still race. A new backend or atomic compare-and-swap is outside accepted scope.

Optional AES-GCM encrypts patient bundles, doctor registries, pinned state,
announcements and text reports. Fresh nonces, authentication tags/purpose bytes and
explicit decryption failures are retained; compressed text uses `SRMSG1:` and
files `SRDATA1`. The shared key travels only in encrypted settings/protected join
files. PDFs/captions intentionally stay readable; Telegram metadata is visible.
No automatic plaintext fallback, key rotation or existing-project conversion exists.

## Reports and exports

The ward «إرسال تقرير» action opens readiness, then reviewed content and explicit
destination/format confirmation. Shift doctors are selectable first in preview,
with active-doctor/revision checks and no fixed maximum roster. Fresh snapshots
and supervisor partial-delivery checks prevent reusing stale review.

Classic tables and linked portrait A5 cards include saved clinical fields, badges
and tasks. Card indexes/name links/Previous–Index–Next support navigation; long
content paginates at readable sizes. Routine/unassigned labels and patient last-edit
metadata are omitted from PDF without dropping tasks or completion history.
Uncommon PDF settings remain under Advanced.

Doctor CSV and encrypted backup prepare encrypted private staged contents before
the picker; saved state holds only random IDs/counts. Writes close and verify bytes;
failed/cancelled exports discard staging and remove new destinations where possible.
Patient CSV verifies nonempty bytes before publishing. Backup passphrases are cleared
after preparation; PDF is separate from this CSV-specific hardening.

`CsvCodec.encode` now quotes the complete metadata as one CSV field, preserving
multi-doctor commas and sort-JSON quotes. The decoder, clinical-row codec, columns
and schema version remain unchanged. Rebuild Android and republish before shared use.

## Standalone desktop client

The [`desktop/` source](../desktop/README.md) supersedes the Excel helper with a
CustomTkinter client targeting Windows 7 SP1 / Python 3.8.10 and Linux desktop /
Python 3.8 or later. No workbook, Excel,
VBA, COM or pywin32 is used. The owner will bundle it separately; no executable
was built. Original clinical artifacts and Android formats/versions are unchanged.

The client provides protected join/password startup, optional literal `.env`
preferences, private-code identity, patient/task/badge editors, current/archive
shift selection, explicit carry-forward, unlimited active-doctor roster, search/
filters/counts and stored sorting. Offline edits are encrypted local drafts;
publication requires fresh verified identity/membership and complete
snapshot review. Desktop can create the first admin/project and export join files;
reviewed ongoing doctor/role/destination administration is also available.

The owner-authorized desktop exception `CONNECTION_MODE=raw` accepts literal local
bot/group/topic credentials plus the existing data key via `.env` or a separate
`--env` file; see the commented raw section in `desktop/.env.example`. Mode and encryption choice are
explicit; mixed join/raw settings, duplicate keys, malformed IDs and missing or
inconsistent encryption keys fail without exposing values. Raw startup verifies
a live-registry admin through the existing Telegram code flow before opening
SQLite. Protocol authorization rechecks admin role and membership for subsequent
shared actions. No admin identity/code/password is supplied by `.env`. Normal
join-file startup and protected-file reconnect remain available; Android is
unchanged. Raw files contain plaintext secrets and must stay private/out of
bundles; token possession still permits direct Telegram access outside the client.
Dummy configuration and authorization probes, Python 3.8 grammar inspection and
whitespace checks cover this change; live Telegram/admin startup and target
Windows behavior remain unverified. Later Linux dummy GUI probes are recorded below. No build or test-suite work was run.

Desktop project creation uses `desktop_project.py` for protocol rules and
`desktop_onboarding.py` for the Arabic dialogs, with common Windows/Linux paths.
Complete raw `.env` settings expose **إنشاء مشروع جديد** before login. The first
human admin must own/administer the Telegram group and verify a private code;
challenges expire after three minutes/five attempts with a resend cooldown.
Successful verification allows thirty minutes for review; every write still
rechecks live group-admin authority.
The bot must administer the group with pin rights. Any existing pin or local
baseline/draft/receipt blocks creation. Review shows hospital/group/topic mapping,
first admin/clinical role and encryption setting, without revealing the token/key.
Every group write rechecks authority and the absence of a pin. A sealed profile
and configuration prevent stale/reused review. A persisted encrypted UNKNOWN
receipt precedes the initial doctor upload. The app publishes one permanent-admin
registry, sends/pins the current JSON index in General and verifies both before
confirming the receipt/session; no patient or shift is created automatically.
Failures do not silently delete artifacts or retry. This cannot guarantee empty
chat history or atomic concurrent creation. Existing pin recovery remains manual.

Verified admins can export Android-compatible protected `.srjoin.json` files.
The live registry/session is rechecked below the UI before preparation/write;
output includes only connection/topics/key and uses the existing authenticated
password envelope. Existing files are refused and new files are reopened for
verification; passwords are never persisted. The key is preserved exactly.
Fake-transport probes passed for plaintext/encrypted bootstrap, initial wire/index
formats, review/authority/stale-data guards, challenge limits, concurrent-pin abort,
persisted UNKNOWN/restart blocking and protected join-file round trips/overwrite
refusal. Actual Linux first-admin/review/export dialogs, admin-only visibility and
screenshots passed with dummy data and all real network blocked. Python 3.8 grammar
and whitespace checks passed. Real Windows/DPAPI execution, Android-device join
and live Telegram creation/export were not performed. No build/package or
migration/test-suite work was run; existing versions are unchanged.

Local persistence is a project-fingerprint SQLite workspace containing an AES-GCM
blob: baseline, draft, registry cache and delivery ledger. No connection secrets
are persisted. Encrypted projects derive the local key from the stable shared
key; other projects use a random local key protected by Windows DPAPI CurrentUser
or a separately entered Linux passphrase. Linux key wrapping uses PBKDF2-HMAC-
SHA256 (600000 iterations, random 16-byte salt) and AES-GCM with the project
fingerprint as authenticated data; the passphrase is never persisted. Missing
keys for existing databases, tampering and wrong passphrases fail explicitly.
No SQLite or interchange schema/version changes were needed. Draft replacement
keeps one encrypted previous copy; explicit backups/restore preserve the live
ledger and invalidate review. File-based reconnect is available before login,
validates candidates before replacing an open connection and refuses different
projects/keys/topics. A Windows named mutex or Linux flock prevents a second local
instance. Linux uses XDG_DATA_HOME or ~/.local/share and creates owner-only
key/database files. Linux setup now installs unpinned binary dependencies in
`desktop/.venv`, accepts the owner's current system Python and keeps Windows
compatibility requirements separate.

The [wire contract](../desktop/PROTOCOL.md) retains state v4/bundle v1/CSV v7,
registry/join/AES-GCM envelopes, reviewed whole-shift replacement including
deletion/roster/order, revision and task-completion stamps, three-shift history, recovery
pointers and publication journals. Telegram remains last-successful-write-wins;
checks are not atomic multi-writer transactions. Complete Arabic PDFs are generated
using ReportLab/shaping/bidi and an embedded TTF, previewed through Windows
startfile or Linux xdg-open,
and sent only after destination/content confirmation. Local PDF export also works
offline. UNKNOWN receipts are durable before mutation and block blind retries;
receipts do not update Android's local sent/audit UI. PDFs remain deliberately
readable, including temporary previews; normal interpreter exit removes previews.

Python 3.8 grammar and pinned GUI imports, Unicode bundle round trips, encrypted
SQLite reopen/tamper rejection, UNKNOWN retry blocking, receipt-preserving draft
restoration, task completion stamps, five-doctor rosters, stale-publication rejection,
receipt-before-upload and remote/concurrent order handling passed on the host
interpreter. A long dummy report generated eight PDF pages; its first page was visually inspected. Python 3.8.10 x86/x64 binary-wheel
resolution passed for the pinned dependencies (timezone backport explicitly added
because cross-target pip evaluates markers against the host). No build, package,
Python 3.8.10 execution, Windows 7/DPAPI/Tk runtime or live Telegram/device round
trip occurred. Editable Arabic/bidi/cursor behavior needs target verification.

On 2026-10-19, Linux offline checks covered shared-key/passphrase SQLite reopening,
wrong/missing/tampered key rejection without overwriting the database, private file
modes, receipt-preserving restore, cross-process flock rejection/release, XDG paths,
automatic font selection and dummy Arabic PDF generation. Simulated xdg-open
handlers covered literal filename arguments and missing/failed handlers; no real
viewer was launched. Linux shell syntax, Python 3.8 grammar, new/changed file
whitespace and local documentation links were checked. The host is Python 3.14
using existing temporary dependency imports, not a fresh pinned Linux installation.
Full graphical Linux clinical editing, actual PDF opening and live Telegram/device
operation remain unverified. Windows 7 dependencies and DPAPI behavior were not changed or
executed; no builds/packages or test-suite work were performed.

Linux Arabic display now uses `desktop_widgets.py` for presentation-only shaping
and bidi ordering of labels, choices, status, tabs, prompts and readonly reviews.
Logical paragraphs wrap before line shaping. Choice variables/callbacks retain the
original Unicode/IDs; review copying returns the complete logical source. Form/search inputs use `desktop_input.py`: original Unicode is the editing
model, shaped physical lines are the display, and caret/mouse/selection positions
map between them. Typing, clipboard, deletion, wrapping, undo/redo, numeric-field
validation and bound variables operate on logical text. Actual patient Save was
checked to retain that text without presentation glyphs. The earlier separate
readback preview was removed. Passwords and native mode retain native widgets.
TEXT_RENDERING defaults to Linux shaping / Windows native rendering; native/shaped
overrides remain. Persistence and PDF/Telegram representations are unchanged.

Actual main-window, editor and Arabic input screenshots were inspected with dummy
data on 2026-10-19. Input probes covered mixed Arabic/English/numbers, cursor/mouse
placement, selection/copy/cut/paste, Backspace/Delete, undo/redo, multiline wrapping,
diacritics/zero-width marks, numeric validation, bound-variable updates and patient
Save. Setup_Linux.sh succeeded on the owner's current system Python; dependency
consistency, shell syntax, Python 3.8 grammar and whitespace checks passed. No real
`.env` or clinical data was read and no Telegram requests were made by the probes.
Full clinical/live Telegram workflows and Windows target rendering remain
unverified.

The desktop source and launchers live under `desktop/`. `.env` is the active
local settings file; `.env.example` is the single shared commented guide for both
join and raw startup. The redundant raw template and Android README were removed;
Android commands remain in the root README.

Obsolete Excel tools/guides, duplicate protocol and completed-review docs
were removed. Private original workbooks remain in ignored `local/excel/`.
Private workbook data in `local/excel/` and runtime credentials are preserved.
Six useful historical packaging/VBA/field-mapping references are hash-verified in
ignored `local/legacy-reference/`. The cleanup command preserves them before any
deletion and stops on an archive mismatch. These are reference sources, not a
runnable package of the current desktop app; no historical dependency pins were
installed and no packaging was run.

Cleanup verification used configuration parsing, documentation-link inspection
and Git whitespace checks only. No builds or test-suite work was run.

### Desktop recovery, richer reports and administration

The desktop now retains deleted patients and their original list positions in
optional encrypted workspace recovery records, with no SQLite/interchange schema
or version changes. Current-shift restoration requires an unchanged draft and a
missing patient ID, restores complete content and uses complete-snapshot review
before publication. Recovery survives reopen/publication and encrypted draft
backup; receipts still cannot be rolled back by restoring a backup. Previously
published task completions retain their identity/time through sealed recovery
history; unpublished completions still receive verified review-time stamps.

Reports support text and PDF; the Classic seven-column RTL table and linked
portrait A5 cards now follow Android's layouts, paper/orientation choices, five
palettes and dark appearance. Paragraph output remains available. Local PDF
export can save a separate file for each supervisor, including an unassigned group. Content
and stored patient order are retained. Local text export is explicit/readable.
Delivery keeps exact-file PDF preview or complete logical-text review followed by
explicit destination confirmation. Encrypted projects send text as a protected
document; plaintext projects use UTF-16-bounded Telegram messages. Partial or
uncertain multi-part delivery retains UNKNOWN and blocks an unreviewed retry.

`desktop_doctors.py` and `desktop_admin_ui.py` implement reviewed add/edit/delete,
supervisor destinations, promotion and eligible demotion. Protocol-level checks
require current Telegram identity/membership and registry admin authority, retain
permanent-admin/rank protections, reject stale registry/patient pointers and local
draft changes, and guard published/local patient/task references and current
rosters. New doctors use the current stable ID/wire representation; no Android
PINs are transported. Registry publication records UNKNOWN before upload, retains
unrelated index fields and confirms the exact registry/pointer by readback.
Updating the registry cache does not replace the private patient draft or its
patient review baseline. Telegram concurrency remains last-successful-write-wins.

Source/local verification, repeated against the applied production modules:
Python 3.8 grammar; dummy encrypted deletion/reopen/
restore/backup and ledger preservation; fake-transport add/promote/demote and
stale/permanent-admin/non-admin rejection; text chunking and encrypted/plain
transport; long Arabic table A4/A5, linked A5 card and landscape Letter paragraph
PDF generation. An A5 table page was visually inspected. New dialog interaction,
Windows 7, live Telegram and Android round trips remain unverified. No build,
compilation, packaging, migration or test-suite work ran.

### Names-only team overview and desktop PDF parity

Both platforms expose **حفظ توزيع المرضى بالأسماء فقط (PDF)** only to admins
(Android report preview; desktop sidebar). Android observes session/current-registry
admin status for visibility and uses `AdminAuthorizer` inside the renderer and
before final export. Desktop verifies the active Telegram session, current admin
registry role and membership before rendering and saving; it requires connectivity.
Actor changes or revoked authority stop the export even if its dialog was already open. Each supervisor gets one
separate table: supervisor heading, one comma-separated resident-name row, then
one patient name per row. Residents now come from independent one-supervisor
registry membership, including residents with no assigned patient. Empty active
supervisor teams are included; no membership is inferred from patient records.
Unknown/unassigned teams remain visible and every patient name is retained in
stored relative order within its group. The export is fixed A4 portrait, with three 56.7 mm supervisor tables per row.
Each table has a supervisor header, one resident-name row, then a number column
on the left and a patient-name column on the right. Global numbering flows left
to right across tables, then down to the next row. Overflow continues on A4 pages
with repeated team headers and no dropped names. It is a local
names-only overview; ordinary clinical report review/sending remains separate.

Android `PdfAssignmentExporter` measures team rows before laying out its
three-column A4 grid, uses the existing palette/settings and saves through the existing
PDF path on IO with byte verification. Its local snapshot does not require a
shift roster; normal clinical report building still does. The action works for saved shifts as other local exports do,
retains busy/conflict guards, and has a dedicated retry action. It does not write
clinical records, sync data or change any versions.

Desktop `desktop_pdf_content.py` shares report content, task display ordering and
the exact current Android palette values with the renderer. Classic tables use
Android's seven RTL columns and proportional widths with full-row continuation;
A5 cards use a linked index, repeated patient heading and Previous/Index/Next on
all continuation pages. Paper sizes/orientation, five palettes (including pastel),
dark backgrounds/text, ward/context and separate local supervisor files are
available. Per-supervisor files are fully prepared before writing a new set;
the combined names overview always remains one file. No clinical content, tasks,
completion history or saved badges are removed from full reports. PDF delivery
still requires actual-file preview and explicit confirmation.

Verification: desktop Python 3.8 grammar; dummy grouping/resident deduplication/
unassigned-name preservation; fixed-A4 three-table grid and 200-patient overflow;
Classic output for all five palettes in light/dark; long Classic/card/paragraph
PDF generation; visual inspection of the overview, Classic and a continuation card.
Separate local supervisor-file generation retained the unassigned group and left
the original shift unchanged; card output contained resolved internal navigation links.
Direct desktop renderer probes rejected non-admin, expired-session and mid-render
revocation attempts; verified admin generation succeeded with a fake transport.
Android authority-observer/renderer/final-save wiring and retry handling were inspected; no Kotlin compile,
Gradle/build, packaging, migration or test-suite work ran. Android rendering/saving,
new desktop dialogs, Windows and live Telegram remain unverified.

Current update checks: Python 3.8 grammar and full-snapshot proposal probes
confirmed local values, deletions, roster and order are used without remote field
merging. PDF metadata/text positions verified A4 portrait, three tables per row
and sequential 1–18 numbering flowing left to right; a 200-patient overflow PDF
was generated and the six-team grid was visually inspected. Android references,
removed scheduler/settings and full-download/publication paths were inspected at
source level. No Android build/compilation, packaging, migration or test-suite work
ran; Android rendering, live Telegram and device execution remain unverified.
Date-changing carry-forward is now one explicit whole-shift action on both
platforms, separate from retrieving a published snapshot (details below).

### Desktop grouping, local access, CSV and demo

- Ward grouping is presentation-only: none, supervisor, resident, diagnosis,
  gender or admission date. Stored numbers remain visible within groups.
  The five-level sort editor uses Android's existing sort-spec fields and
  ASC/DESC directions, keeps priority patients first and saves list positions and
  compact metadata together, reapplied with current doctor names before publication
  review. Manual arrows clear the sort levels. Text/PDF/CSV
  consume that same saved order; edits reapply the active sort.
- Device PIN enrollment/reset requires a current Telegram clinician session and
  fresh membership/account authorization. Salted PBKDF2-SHA256 hashes (600,000
  rounds), doctor/account binding and persistent attempt delays live inside the
  encrypted workspace, excluded from portable backups and both CSV formats.
  Arabic/ASCII digits are accepted. Reopening an enrolled workspace locks it;
  manual locking or 1/5/15-minute inactivity hides the ward and existing dialogs,
  preserving unsaved editors for unlock. Local workspace reads/saves/exports are
  blocked while locked. The active network operation finishes before the idle
  lock engages. Locking clears Telegram authority and reviewed publication;
  PIN opens local work only. Private Telegram verification remains available for
  recovery and is still required for shared publication/admin actions.
- Patient CSV export is a single Android schema-7 document in stored order,
  retaining tasks and complete clinical fields. Doctor CSV uses Android's existing
  schema-1 columns, omits local PINs and goes through live-admin before/after
  review and the existing pointer/draft/receipt publication checks. Import matches
  existing IDs/account/name without ambiguity; it retains omitted active doctors,
  permanent/current-actor privileges, reference guards and local access records.
  Deleted-only newcomers are ignored; an old deleted row cannot delete an active
  doctor. Unsupported portable extra options are rejected explicitly. Telegram's
  registry has no username/update/delete timestamp storage: export timestamps
  describe the export, and those CSV-only fields do not add registry fields.
- `--demo` starts without reading connection settings or opening a persistent
  workspace; the regular sidebar can also open a separate demo window. Demo uses
  the real forms, groups, sorting, tasks, roster, recycle and PDF renderer with an
  in-memory workspace and a client that has no Telegram transport. Network/admin,
  backup/import and CSV paths are blocked. Reset/exit discards the session.
  Beverage patients and tree doctors are dummy data; explicitly saved PDFs carry
  a DEMO footer on every page, including card continuations/index pages.

Checks for these additions: Python 3.8 grammar and module import; disposable
source probes for stable priority/multi-level order, group conservation,
patient/doctor CSV round-trips, non-admin/ambiguous/rank import rejection,
persistent PIN throttling/account rebinding, backup exclusion and locked local
operations; and in-memory demo reset/recycle/network/backup rejection. Generated
Classic, cards and paragraph demo PDFs (1/13/3 pages) had a DEMO marker on every
page; the Classic page was visually inspected. Desktop module import hit the
sandbox's read-only user-font path and CustomTkinter reported its fallback.
No graphical desktop/display was available: lock conceal/restore, native dialogs,
large-font interaction and Windows remain unverified. No build, compilation,
packaging, migration or test-suite work ran; live Telegram is unverified.

### Team membership and workflow consolidation

The owner authorized independent teams with at most one supervisor per resident.
Doctor editing on both platforms includes that link, with an explicit unassigned
state for setup. Lower administration/registry checks require active supervisor
references and block deleting/changing a linked supervisor until residents move.
Android stores `teamSupervisor:<id>` in its existing doctor options; Telegram adds
an optional tenth `teamSupervisorId` column, and doctor CSV retains its existing
schema-1 columns using portable `extra_options`. Retrieval accepts the previous
nine-column registry as unassigned teams; it never invents team links from patient
assignments. Both clients should be updated when publishing team data. No Room
schema/migration or numbered format/toolchain version changes were needed.

Android registry retrieval replaces the portable team link while retaining local
PINs/aliases. Existing ID normalization and CSV identity matching also remap team
references. CSV review on both platforms now names additions, changes, unchanged
imported rows and omitted active doctors retained as-is; Android excludes PINs,
aliases and incidental update times when classifying changes. Its active/admin
counts cover the resulting full registry. The existing stale-registry/live-admin
publication guards remain in place. Assignments PDFs use the independent team
roster and include empty active supervisor teams; individual patient resident fields
remain separate clinical assignments and no longer define PDF team membership.

Android's optional empty-shift prompt and explicit drawer action now offer the
same whole-shift copy as desktop: all patients, unfinished tasks with deadlines,
one keep/clear responsibility choice, fresh patient IDs, no completed-task copy.
Per-patient rollover decisions/review queues are removed. Android uses the previous
local shift (download to refresh it); the clinical transaction checks source IDs,
source revisions, target date/revision and target emptiness. The new shift uses
saved manual order with priority first. Desktop has a separate start-shift action,
keeps an existing empty current-shift identity/roster, refuses local/remote nonempty
targets and rechecks patient/doctor pointers after confirmation. Neither download
path creates copied patient records. Ordinary review/publication remains required.

Desktop navigation is grouped into project/identity, local data/publication,
reports and files/recovery, with explicit local-save → review/publish → send wording.
Local report exports are labelled as draft exports that may contain unpublished
edits. The ward shows the saved sort and explains that grouping is display-only
and arrows clear sort levels. Android reuses the sort-sheet description in its
filter dialog and explains grouping versus stored/export order.

Checks: Python 3.8 grammar; disposable team wire/CSV round-trips, absent-patient
resident inclusion, empty supervisor tables, invalid/deleted team-target rejection,
CSV stable-ID remapping and protected import; bulk copying with retained deadlines,
completed-task exclusion and keep/clear assignments; and sort-description probes.
The desktop start-shift handler was exercised with disposable encrypted storage,
confirming target identity/roster retention, all-record copying, assignment clearing
and nonempty-target rejection. An assignments PDF with explicit residents but no
per-patient resident assignments and an empty supervisor table was generated and
visually inspected. Desktop GUI import again reported its read-only user-font
fallback; no interactive desktop execution was performed. Android edits
were inspected at source level only. No Gradle/build, Kotlin compilation, packaging,
migration or test-suite work ran. Updated GUI workflows, Android rendering and
live mixed-device Telegram operation remain unverified.

### Cover, reviewed sending and source freshness

Cross-team cover is allowed using the existing patient resident/supervisor fields.
There is no automatic reassignment of permanent team membership or supervisor.
Desktop resident choices identify the permanent team and explain covering; its
text/full-PDF patient output marks a known cross-team assignment. Android's form
shows the policy and a live cross-team indication. Its text/PDF report context
explains that individual cover may differ from permanent membership. Role/active
physician validation remains unchanged; no new stored clinical flag is required.

Desktop report sending now prepares the same guarded full-shift publication used
by manual review/publish. If data differ from the current remote shift, the user
reviews the entire ward replacement (including deletions, even for a supervisor
report), previews the final text/PDF, then explicitly confirms publication and
delivery. No write occurs on cancellation before final confirmation. The existing
publication ledger, current actor/pointer checks and exact PDF/text checks remain.
After successful publication, sending uses that accepted shift/pointer. Identical
accepted data reuse the remote publication, preserving existing report receipt
identity and UNKNOWN blocking rather than creating a new snapshot for each retry.
Android already publishes the matched reviewed shift before sending; its shared
confirmation now also explains that delivery failure cannot undo publication.

Android successful bundle application records per-shift download time/message ID
in existing settings in the same transaction. Both automatic empty-shift and
explicit whole-shift copy prompts display the source date and that recorded time
only when it matches the source's CSV message ID. Older/local-only copies show an
explicit unknown time; the prompt says no Telegram freshness check occurred now
and tells the user to cancel/download first if needed. Desktop copy confirmation
shows source date/count and the client-zone time its remote bundle was fetched
for this operation, retaining its final pointer recheck. No database migration,
interchange/schema version change or background synchronization was added.

Checks: Python 3.8 grammar; disposable production-handler probes with encrypted
workspace storage/fake transport confirmed cancellation causes no publication,
final confirmation precedes publication/delivery, unchanged data avoid republishing,
and an ambiguous delivery remains UNKNOWN and blocked across a retry. Cross-team
classification, text/PDF content labels and assignment/membership preservation
passed local probes. Android download metadata, scrollable confirmation and
cover-policy wiring were inspected at source level. The desktop GUI import
reported its known read-only font-install fallback. No Android build/compilation,
packaging, migration or test-suite work ran. Graphical workflows, actual Android
rendering and live Telegram remain unverified.

### Explicit report outcomes and movement restrictions

Report screens now expose publication and delivery as separate outcomes for the
last send attempt. Android records publication-in-progress before upload and
confirmed publication before rebuilding/sending. ReportSender wraps failures at
the actual Telegram delivery boundary as uncertain delivery, preserving separate
cancellation handling; render/file/validation failures before delivery remain
not-started outcomes. Supervisor batches retain the confirmed destination count
and distinguish partial success from an uncertain next delivery. Uncertain
publication/delivery removes the shortcut retry action and instructs manual
Telegram review. Successful sending marks delivery confirmed independently of
publication. Attempt-stage displays are session-local; the durable Android delivery
ledger described below protects retries across recreation and restart. No database
or interchange schema/version changes were introduced.

Desktop has the same separate last-attempt display and actionable failure text.
It recognizes confirmed publication even if subsequent local bookkeeping fails,
and confirmed delivery before receipt bookkeeping. Existing durable UNKNOWN
receipts remain authoritative across restarts and retries. Local report export
is separate and does not reset the send attempt display.

Desktop manual movement is disabled while any grouping, filter or nonempty search
is active, with a visible explanation to clear these first. The move handler also
checks the condition, so a stale callback cannot move against hidden neighbours.
Boundary arrows are disabled in the full ungrouped list. Existing stored order,
priority-first behavior and sort-level clearing remain unchanged.

Android synchronization labels now say «تعديلات غير منشورة» and «منشور على تليجرام»;
the timestamp says last synchronization with Telegram, avoiding confusion with
local encrypted backup files. Internal enum/persistence names remain unchanged.

Checks: Python 3.8 grammar and disposable production-handler/source probes passed
for group/filter/search movement rejection, report cancellation, confirmed
publication before delivery, UNKNOWN retry blocking and outcome classification
before requests/after ambiguous requests/after confirmed bookkeeping failures.
Android network-boundary wrappers, stage updates, partial delivery and retry wiring
were inspected at source level; affected import checks passed. GUI import used the
known font fallback. No Gradle/build, Kotlin compilation, packaging, migrations or
test-suite work ran; interactive Android/desktop and live Telegram remain unverified.

### Durable Android deliveries and exact reviewed PDFs

Android stores local report-delivery metadata in its existing settings repository.
A singleton ledger persists UNKNOWN before a Telegram request, then records SENT
and message IDs after acknowledgement. An unresolved shift/destination blocks all
new formats/content for that route; identical confirmed content requires explicit
one-copy permission. Preflight checks run before publication, and the send boundary
rechecks under the ledger mutex. Network/cancellation failures retain UNKNOWN;
receipt-write failures after acknowledgement retain a successful delivery outcome
and leave the durable entry blocking retries. No patient text, credentials or raw
destination IDs are stored in the ledger; route/content identifiers are hashes.
This is a device-local guard, not Telegram idempotency or cross-device coordination.
Android supervisor delivery identity also includes the supervisor ID, so distinct
reviewed subsets can share a Telegram group without being mistaken for duplicates.
Preflight and send use that same identity; an unresolved route still blocks both.

Desktop now checks UNKNOWN report receipts by shift/chat/topic independently of
format and publication pointer, before review/publication and again before the
request. New report receipts append the shift ID to local metadata. Existing
unresolved rows without that label conservatively block their destination until
resolved; their contents are retained. The existing confirmed-report key and
explicit extra-copy confirmation remain. One shared destination selector is used
by preflight and both text/PDF transport paths. No database or interchange schema
or version changes were made.

Desktop manual receipt resolution checks the live project identity/membership and
requires the original sender or a current admin below the UI. Wrong-project,
locked, expired or unauthorized sessions cannot clear the block. Automatic receipt
confirmation still relies on Telegram acknowledgement or matching pinned evidence.

The report screen exposes dated attempts and explicit confirmation after inspecting
Telegram: received, not received, or one additional copy of a confirmed report.
Only the original sender or an active local admin can resolve an attempt, checked
below the UI with expected status. An executing request cannot be resolved; process
recreation releases that in-memory execution guard while preserving UNKNOWN. Any
subsequent send still requires ordinary report review/confirmation.

General and per-supervisor Android PDF previews retain the generated private-cache
file, SHA-256, reviewed report and frozen export options/style. Send requires that
same intact file, matching current clinical content/destinations and options. It
sends those bytes without regeneration. Changed data/options, lost files or viewer
launch failure require another preview; recreation without the in-memory reviewed
artifact also requires a new preview. A supervisor batch checks every selected
preview before publication. Local save/share/export remain separate actions.

Historical assignment PDFs on both clients explicitly say the resident membership
is the current roster, rather than a historical team snapshot; the context repeats
on every page. Patient assignments still come from the selected shift.

Checks: source inspection covered durable-before-request ordering, authenticated
resolution, active-request exclusion, cancellation cleanup, all three delivery
entry points, preview/data/options/hash checks and exact-file send wiring. Desktop
production modules parse with Python 3.8 grammar. Disposable dummy-data PDF probes
verified a one-page current shift and four-page historical overflow with repeated
context; the historical Arabic page was visually inspected. Git whitespace checks
passed. No Gradle/build, Kotlin compilation, packaging, migrations or test-suite
work ran. Android recreation/concurrency/viewer behavior, interactive workflows
and live Telegram delivery remain unverified.

Additional disposable production-handler probes confirmed persistent destination
blocking across format/publication changes, rejection before review/publication,
independent shift/chat/topic availability and preservation of unresolved earlier
rows. The real client authorization path with a fake transport rejected unrelated
clinicians, expired sessions, departed membership, locked workspaces and wrong
projects; sender/admin resolution passed. Existing review/cancellation/delivery
outcome probes also passed. Python 3.8 grammar, affected Kotlin delimiter/import,
local documentation-link and Git whitespace checks passed. GUI imports used the
known read-only font fallback. Android supervisor identity was inspected at source
level; no builds, compilation, migration or test-suite work, or live Telegram ran.

### Workflow status, ward tasks and batch teams

Both ward interfaces expose the selected shift, local publication state, recorded
last download and count of unresolved report deliveries. The next action leads to
delivery review, publication review or manual download. Android publication state
is explicitly labelled device-wide, using the existing pending flag; desktop
compares the selected draft with its known publication. Neither display claims to
have checked Telegram now. Unknown download times remain explicit; a recorded time
for an earlier snapshot is labelled accordingly. Desktop records selected-shift
download metadata atomically with the local replacement, without changing shared
formats or versions. Demo status is explicitly in-memory and its network action is
disabled. Existing report review/confirmation remains required.

A ward task list on each platform shows individual unfinished tasks across all
patients, ordered overdue/deadline/priority, with patient-supervisor and
patient-resident filters. Cards retain task ownership/deadline information. Opening
a task resolves the current patient identity and uses the existing patient screen
or editor; the board does not complete tasks or silently save clinical changes.
Android reuses the PatientTasks display comparator. These are display filters and
do not change stored patient/task order or exported content.

The doctor-management screens now offer a batch team editor: supervisors and their
residents appear together, with resident membership choices and explicit review of
all changes before saving/publishing. Android checks live admin authority, the
complete expected registry and valid one-supervisor membership in the mutation
transaction, then writes the changed residents/audit/pending state together.
Desktop reuses Administration review/approval/publication and its live admin,
patient-draft and remote-registry checks. A single registry publication contains
the entire approved batch. Patient assignments, cover and task owners are retained.

Desktop manually confirmed arrival (CHECKED_DO_NOT_REPEAT) now qualifies for an
explicitly approved extra report copy. It still blocks ordinary repetition, and
UNKNOWN remains blocked independently of any extra-copy approval.

Verification: disposable production probes passed task ordering/filtering and
non-mutation, status/download persistence and next actions, multi-resident team
validation/admin checks, one-upload batch publication, stale-draft/registry
rejection and patient preservation. A dummy complete desktop handler sequence
covered download, edit, reviewed publication, report send, retrieval through the
shared Android CSV format, ambiguous delivery resolution and an approved repeat.
Actual desktop demo-window/task-filter/patient-opening/team-selection callbacks
ran with network prohibited; task/team/main Arabic screenshots were inspected.
Demo state remained in memory. ADB was reachable after its sandbox socket
restriction was lifted, but listed no connected devices. Android has source
inspection and delimiter/import checks only. Python 3.8 grammar, documentation links
and Git whitespace checks passed. No Gradle/build, compilation, packaging,
migration or test-suite work ran. Actual Android-to-desktop execution, live Telegram
and Android layout/accessibility remain unverified; dummy transport and wire
round-trips do not establish those runtime behaviors.

## Verification and limits

- Owner-supplied Android build on 2026-10-19 stopped at `compileDebugKotlin`
  because `DoctorsScreen` passed an unsupported `doctors` argument to
  `DoctorConflictReviewDialog`. Removed that argument and checked the sole call
  against its declaration; Git whitespace checks passed. No build was rerun,
  so compilation of the corrected snapshot remains unverified.
- Python 3.14.4 Linux compatibility probe on 2026-10-19 passed in an isolated
  `/tmp/shiftreport-python314-check` venv with CustomTkinter 6.0.0, Pillow 13.0.0,
  ReportLab 5.0.1, cryptography 50.0.2, Requests 2.34.2, arabic-reshaper 3.0.1 and
  python-bidi 0.6.11. All nine desktop modules imported; Arabic patient/task bundle
  round trips, AES-GCM wrong-key rejection, SQLite reopening/receipt retention,
  Linux local-passphrase rejection, actual main-window/patient-editor construction,
  logical Arabic choices and Tk callbacks passed. A dummy Arabic PDF and editor
  screenshot were visually inspected. No real `.env` or clinical data was loaded;
  Telegram requests were blocked in the probe. The environment's exact dependency
  snapshot is `/tmp/shiftreport-python314-requirements.txt`; it is temporary.
  At that snapshot, Linux setup rejected the interpreter and binary resolution
  failed on the old Pillow pin. The subsequent owner-authorized Linux change
  removed its pins and upper-version gate; actual setup and Arabic editing probes
  now pass as documented above. System Python and Windows requirements are intact.
  These checks do not establish live Telegram login/publication/delivery, complete
  clinical workflows, or a supported production Python 3.14 installation.
- Historical authorized Android build on 2026-10-07: success in 2m 55s, 43 tasks
  (21 executed, 22 up-to-date); APK metadata confirmed debug version/code above.
- Latest owner-supplied success on 2026-10-18: 2m 15s, 43 tasks (10 executed,
  33 up-to-date). Later unused-local cleanup, visual/export/login refinements,
  restored grid alias and metadata fix have source checks only. Neither build
  establishes compilation of the current working tree.
- Offline checks covered Python parsing, Unicode/protected-join crypto, CSV/bundle
  metadata, baseline/token-replacement and merge/conflict choices. Workbook ZIP/XML,
  VBA source round trips, clinical-cell preservation, reference/hash/credential
  scans and Git whitespace checks passed for the earlier package snapshot.
- Historical Excel/COM/VBA execution and current Windows 7/DPAPI/Tk execution,
  live Telegram identity/download/publication/
  recovery/report sends, Android compilation of later edits, device/provider/fold/
  keyboard/accessibility behavior and rendered PDF/Arabic extraction remain
  unverified. ADB previously listed no connected devices. No release build was run.
- Documentation cleanup validated local Markdown targets/headings, preserved README
  build instructions and deferred roadmap content, and confirmed private docs remain
  ignored. The updated Windows package passed manifest/CRC/link checks using the
  existing workbook bytes; no workbook rebuild or macro execution was performed.
- Release still uses debug signing. Production signing is the active near-term
  [roadmap](roadmap.md) item; voice/templates remain deferred. Source completion
  does not imply runtime verification.
