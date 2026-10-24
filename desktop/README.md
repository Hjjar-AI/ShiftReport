# ShiftReport desktop — CustomTkinter

Standalone source replacement for the Excel helper. No Excel, workbook, VBA, COM, pywin32 or Office PDF conversion is used. Targets: **Windows 7 SP1 / CPython 3.8.10** and **Linux desktop / CPython 3.8 or later**, with Tcl/Tk installed. The owner will bundle it separately; no EXE or package is built by these scripts.

## Run on Windows

1. Install CPython 3.8.10 including Tcl/Tk, pip and the Windows `py` launcher.
2. Keep all files in this folder together. Run `Setup_Windows.cmd` once, then
   `Start_Desktop.cmd`. Alternatively:

   ```bat
   py -3.8 -m pip install --only-binary=:all: -r requirements-win7.txt
   py -3.8 desktop_gui.py
   ```

3. Optionally copy `.env.example` to `.env` beside the script. Without it, startup
   prompts for the protected `.srjoin.json` file and its password. Connections,
   topics and the optional shared key normally come from that file. An optional
   admin-only raw `.env` path is described below; Android joining remains file-only.
   No credentials should be pasted into source or chat.
4. For an existing project, publish the doctor registry and current patient data
   from the current Android source first. For a new group, use desktop creation
   below. Its complete CSV metadata must be quoted; rebuild/republish older
   incorrectly quoted snapshots using Android. Protocol/schema versions stay unchanged.
5. Update the doctor list, select yourself, request a private Telegram code and
   enter it. Open the bot and press Start beforehand if it cannot deliver the code.
6. Fetch/select a shift. Choose the shift doctors; edit patients, tasks and badges
   in the app. Every patient Save and roster/order/delete action saves the local
   encrypted draft. Form changes are private until Save; Cancel requires discard
   confirmation. Existing saved drafts can be reopened/edited offline.
7. Review the complete proposed replacement snapshot, then publish the
   reviewed version. A changed draft, actor, remote pointer or registry invalidates
   the review. There is no automatic upload or automatic retry.
8. Select the general or supervisor destination, use «إرسال تقرير», inspect the
   actual PDF opened in your default PDF viewer, then confirm delivery. Local PDF
   export is also available offline. PDFs deliberately remain readable.

## Run on Linux

Use a graphical Linux session with Python 3.8 or later, Tcl/Tk, venv, xdg-utils, a
configured PDF viewer and an Arabic-capable TrueType font such as DejaVu Sans.
Keep the source folder writable for the virtual environment. Run:

```sh
cd desktop
./Setup_Linux.sh
./Start_Linux.sh
```

Setup uses the installed `python3`; select another interpreter if needed with
`PYTHON_BIN=/path/to/python ./Setup_Linux.sh`. Linux dependencies are unpinned and
pip resolves compatible binary releases for that interpreter. Setup installs or
updates only the folder's `.venv`; system Python and Windows compatibility
requirements are separate. The setup and dummy GUI/editing/storage/PDF checks
passed with the owner's current system Python. On Debian/Ubuntu, Tcl/Tk and venv
are commonly provided by `python3-tk` and `python3-venv` for that interpreter.

Joining, clinician verification, editing, review and confirmed publication/delivery
follow the same workflow above. For an unencrypted Telegram project, Linux asks
for a **separate local draft passphrase** (at least ten characters, confirmed on
first use), then asks for it when reopening/reconnecting. It wraps a random local
key using PBKDF2-HMAC-SHA256 and AES-GCM; neither passphrase is persisted. Projects
with a shared data key use that key and need no extra local passphrase.

A default PDF viewer is opened through `xdg-open`, without a shell. If opening
fails, sending stops before delivery starts. Always inspect the actual PDF before
confirming: launching a viewer alone does not establish that you reviewed it.

## Create a project from Windows or Linux

1. Add your bot as a group administrator with permission to pin messages. Your personal Telegram account must also own/administer the group. Enable topics if you use topic IDs, create those topics in Telegram and copy their exact IDs; desktop creation does not create the topics themselves. Use a new group; it refuses every existing pinned item and never removes pins/messages to make room.
2. Fill `.env` using its commented guide: choose `CONNECTION_MODE=raw`, leave `JOIN_FILE` blank, and provide the hospital, bot, group and exact topic settings. Encryption follows the configured setting/key; no key is silently generated, replaced or used to convert an existing project.
3. Start the desktop client. If connection reports no index, choose **إنشاء مشروع جديد**. Enter your full name, personal Telegram user ID, gender and clinical role. Open your bot and press Start beforehand.
4. Request and enter the private creation code. The app checks your group-admin status and the bot's pin permission. On Linux, set/unlock the separate local draft passphrase if the Telegram project is unencrypted.
5. Review the group, hospital, topic mapping, first administrator and encryption setting, then explicitly approve. The app uploads the first doctor registry, sends/pins the current JSON index in General and reads both back for confirmation. It creates no dummy patients or shift automatically.
6. Choose **جلب / اختيار مناوبة** to start your first shift. Use **تصدير ملف انضمام محمي** to save an Android-compatible `.srjoin.json` for other devices; enter/confirm a password and send that password separately. The export includes the connection and any shared data key, without patient/user records.

The creation action locks once a project is open. Existing local drafts, baselines or operation receipts block bootstrap. A persisted encrypted UNKNOWN receipt is written before the first registry upload. If creation fails after it starts, inspect Telegram and reconnect once the correct index is pinned; do not blindly repeat creation or delete the workspace. Uploaded files/messages are not silently removed. Read/edit/publish and concurrent creation still have Telegram's last-successful-write-wins limitation, not a distributed transaction.

Join-file export is visible to a verified admin and rechecks the live registry below the UI. It refuses existing output files and verifies the encrypted file by reopening it before reporting success. Windows uses its existing DPAPI path; Linux uses its existing shared-key/passphrase path. The new dialogs and protocol were checked on Linux with a fake Telegram transport; actual Windows runtime and live creation/export remain unverified.

## Features and boundaries

- Full patient demographics/clinical notes/labs, badges/priority, optional resident/supervisor and structured optional-owner tasks/deadlines/completion; ward/patient counts, search/filter/grouping and stored sorting. New-position/priority-first entry rejects stale drafts; admission dates accept ISO/day-month-year and Arabic digits.
- Dashboard/roster-first report setup shares Android readiness, without required task ownership or roster cap. Current shift: 08:30 in selected IANA zone (default Asia/Damascus); archives view/export only. Reviewed rollover preserves unfinished deadlines with keep/clear assignments.
- Manual complete-snapshot retrieve/edit/review/publish, last-successful-write-wins; live membership/pointer checks below GUI. Local Save stamps editor/completion; publication preserves identity. No workbook conversion.
- Shared [wire/security contract](../docs/reference/telegram-protocol.md); [results](../docs/status/current-state.md#verification-and-limits) and [remaining checks](../docs/status/testing-handoff.md).

## Desktop recovery, reports and doctor administration

| Action | Behavior / guard |
|---|---|
| Recycle/Undo download | Encrypted full records/tasks/badges/positions and sealed completion history survive reopen/backups. Restore never overwrites IDs; recheck draft/assignments and review full publication. Undo retains current baseline/receipts and rejects later saves. |
| PDF/text | Classic seven-column RTL, paragraphs, linked A5 cards; full saved content/tasks/badges/order. Cards: Android geometry, 20mm top/10mm bottom/15mm sides, repeated continuation headings and full-page navigation. Advanced paper/orientation/five palettes/dark/separate supervisor files. Hospital/ward omitted; assignment overview also omits shift date. Open exact PDF before confirming; encrypted text uses protected documents, otherwise UTF-16-bounded messages. Local exports are readable. |
| Names-only PDF | Verified live admin only, checked in renderer/before saving. Fixed A4 portrait, three 56.7mm supervisor tables/row; independent resident membership (including no-patient residents), empty teams, unassigned group, continuous numbering and continuation headers. Keeps every name; independent of separate-file setting. |
| Doctors/teams/roles/destinations | Review full registry; permanent admins protected, demotion limited by rank. Reject unsafe patient/task/roster references; reassign/publish first. Stable IDs/omitted active doctors retained; no device PIN export. |
| Registry publication | Live actor/admin, draft and patient/doctor pointers rechecked; preserve patient baseline and unrelated index fields, read back exact new registry. UNKNOWN before upload; never auto-retry. |
| Delivery | Review pending full shift + exact report, final confirmation, publish then deliver. Cancel publishes nothing; unchanged content reuses publication/receipt. Shift/destination UNKNOWN blocks format/publication changes and requires live sender/admin resolution; extra confirmed copy needs approval. Publication and delivery outcomes stay separate; disk failure after confirmed send does not undo delivery. |

## Arabic display

Store/edit/copy logical Unicode; shape only presentation/PDF. Linux `TEXT_RENDERING=auto` enables shaping/bidi; `native` uses Tk, `shaped` forces adapter. `desktop_input.py` maps visual caret/selection/wrapping to logical typing, mouse, clipboard, undo and Save. Reviews wrap before shaping and copy full original text; IDs/callbacks remain logical. Passwords/native mode retain native controls; no duplicate readback.

## Configuration and local files

| Setting/storage | Contract |
|---|---|
| `.env` | Literal `CONNECTION_MODE`, `JOIN_FILE`, `TIMEZONE`, `DATA_DIR`, `PDF_FONT`, `APPEARANCE`, `TEXT_RENDERING`; quoted values accepted, expansion/interpolation/inline comments rejected; unknown/duplicate keys fail. Paths relative to source/EXE. Alternate file: `--env PATH`. |
| Join/raw | [.env.example](.env.example) documents both. Default protected join prompts for password. Explicit raw mode: exact hospital/token/group/topics/encryption/original Base64 key, blank JOIN_FILE; no key generation/substitution/fallback. Raw startup verifies live registry admin before opening drafts and rechecks shared actions. Join supports offline reopening. Never store passwords/codes in env. |
| Reconnect | Protected file before login; validate live, preserve drafts/receipts/key, reject other project/bot/group/topics/key, repeat clinician verification. No manual token endpoint; BotFather revokes. Private plaintext raw files stay ignored/outside bundles. Extracted tokens bypass client gates. |
| Workspace | AES-GCM before SQLite; fingerprint per project. Windows `%LOCALAPPDATA%\ShiftReportDesktop`; Linux `$XDG_DATA_HOME/ShiftReportDesktop` or `~/.local/share/ShiftReportDesktop`. No raw config/passwords persisted. Shared key protects encrypted projects; otherwise random key wrapped by DPAPI or Linux passphrase. Retain Windows `.key`/original profile or Linux `.linux-key`/passphrase; DB alone is insufficient. No silent replacement/plaintext fallback. New Linux files owner-only. One instance per Windows session/Linux user. |
| Recovery | Automatic encrypted `.previous.srdraft` before replacement; explicit backups for more copies. Same project/key only, fresh review, current delivery ledger retained. Draft backup/another PC never proves delivery; do not erase workspace to retry UNKNOWN. Android-compatible `.rpb` is separately labelled. |
| PDF preferences | Encrypted device-local (demo memory): layout/paper/orientation/palette/appearance/supervisor files; advanced separator/contrast/shading and Balanced/Clear/Print presets. Cancel saves nothing; cards fix paper/margins and hide shading. |
| Fonts/previews | ReportLab + shaping/bidi + bundled Tajawal Regular/Bold/OFL. Optional Arabic TTF override uses matching `-Bold.ttf` sibling if present. Full clinical/task/badge pagination; routine/unassigned/last-edit labels omitted. Temporary readable previews removed on normal exit; crashes can leave them. |

## Bundling later

Use this folder as the source entry, with `desktop_gui.py` as the entry point.
Preserve CustomTkinter data/fonts/themes, certifi CA data and tzdata, together with
the desktop `fonts/` directory and its license. Build for each intended machine
architecture using Python 3.8.10; x64 executables cannot run on 32-bit Windows.
No bundler dependency or packaging task is included. Keep secrets and clinical
workspace files out of any bundle.

## Verification

Linux storage/crypto/transport/PIN/CSV/demo/PDF and selected Arabic GUI probes passed on their recorded snapshots. The later deep run passed 60 cases, including four reverse Android file checks; seven selected mutants were detected and synthetic live transport checks passed separately. These do not establish the latest full desktop GUI, Windows 7/DPAPI/packaged runtime or end-to-end Android/desktop live workflows. [Exact results](../docs/status/current-state.md#verification-and-limits) · [Remaining checks](../docs/status/testing-handoff.md) · [Desktop/interop/live commands](../docs/guides/deep-testing.md).

Synchronization uses full downloads and reviewed full-shift replacement, including local deletions, order and roster. Remote clinical fields/additions are not merged into the proposed snapshot. Pointer/authority checks and UNKNOWN receipts remain; last successful publication wins. Local stale-edit review remains separate from remote synchronization. Neither platform schedules background synchronization.

## Grouping, local PIN, CSV and demonstration

- Use the grouping selector above the ward for supervisor/resident/diagnosis/gender/ admission-date groups. «ترتيب متعدد المستويات» saves up to five ordered criteria with ascending/descending directions. Priority patients stay first. Manual arrows return to manual order. Stored patient numbers and report/CSV order agree.
- After Telegram verification, «إعداد PIN والقفل التلقائي» enrolls a clinician PIN on this computer and selects a 1/5/15-minute idle timeout. «قفل الشاشة» locks immediately. Reopening an enrolled workspace also locks it. Hidden editors retain unsaved input. PIN unlocks local work; reverify Telegram for publishing/admin actions. Telegram-code unlock is available if the PIN is forgotten. PIN hashes and persistent attempt limits stay inside local encrypted storage and are excluded from portable backups and CSV files.
- «أدوات CSV» exports the selected shift's complete Android-format patient CSV, or, for a live verified admin, exports/imports schema-1 doctor CSV. Import reviews before/after and publishes through the existing guarded registry flow. Missing rows do not delete doctors. Import does not carry device PINs. Unsupported extra options are rejected. Telegram stores no doctor username/update/delete timestamp fields; those CSV-only fields are not persisted, and exported update times are the export time.
- Start with `python desktop_gui.py --demo` (or open the independent demo from the sidebar). Connection settings and real clinical storage are not opened by `--demo`. Edit beverage patients/tree doctors using ordinary local forms; reset or close to discard the session. Only explicit local PDF export is available, with DEMO on every page. Demo has no Telegram transport.

Runtime limits are consolidated in [Remaining checks](../docs/status/testing-handoff.md).

## Consolidated workflows

- Resident team membership is independent of patient cover; one active supervisor per resident. Move linked residents before supervisor deletion/role change. Cross-team cover never moves membership and is labelled in editor/report context. Teams travel in Telegram `teamSupervisorId` and schema-1 CSV `extra_options=teamSupervisor:<id>`; use updated clients.
- Fetch/select shift only retrieves. Separate «بدء مناوبة اليوم من السابقة» copies all previous patients and unfinished deadlines into an empty today shift after review, offers keep/clear assignments and rechecks pointers. Saves locally; publish separately. Source/download timestamps never promise current remote freshness; cancel/fetch first if needed.
- Sidebar groups identity/project, local/publication, reports and recovery. Sort context stays visible; grouping is presentation. Manual arrows require no grouping/filter/search and clear sort levels. Local reports include unpublished edits; sending reviews/publishes them first. CSV review shows additions/changes/unchanged/retained omissions before full-registry publication.
- Status shows selected shift, known publication/download and unresolved deliveries with next manual action, without background Telegram checks. «مهام الجناح» filters pending tasks and opens reviewed patient editing; «إدارة الفرق معاً» reviews bulk memberships without changing assignments.
