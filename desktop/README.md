# ShiftReport desktop — CustomTkinter

Standalone source replacement for the Excel helper. No Excel, workbook, VBA, COM,
pywin32 or Office PDF conversion is used. Targets: **Windows 7 SP1 / CPython
3.8.10** and **Linux desktop / CPython 3.8 or later**, with Tcl/Tk installed. The owner will bundle it separately; no EXE or
package is built by these scripts.

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
4. Publish the doctor registry and current patient data from the current Android
   source first. Its complete CSV metadata must be quoted; rebuild/republish older
   incorrectly quoted snapshots using Android. Protocol/schema versions stay unchanged.
5. Update the doctor list, select yourself, request a private Telegram code and
   enter it. Open the bot and press Start beforehand if it cannot deliver the code.
6. Fetch/select a shift. Choose the shift doctors; edit patients, tasks and badges
   in the app. Every patient Save and roster/order/delete action saves the local
   encrypted draft. Form changes are private until Save; Cancel requires discard
   confirmation. Existing saved drafts can be reopened/edited offline.
7. Review every conflict and the complete proposed snapshot, then publish the
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

## Features and boundaries

- Patient demographics, clinical notes/labs, optional resident/supervisor, priority,
  structured tasks with optional owners/deadlines/completion, and multiple badges.
- Search, priority/pending/overdue filters, per-patient/ward counts, explicit deletion,
  stored manual/alphabetical/admission ordering, unlimited active-doctor roster.
- Current 08:30 shift boundary in the selected IANA zone (default Asia/Damascus).
  Archived shifts are viewable/exportable. Only current shifts are editable or
  publishable/sendable. A new shift can explicitly carry patients and unfinished
  tasks, preserving deadlines and optionally clearing assignments.
- Existing Android state v4, bundle v1, CSV v7, doctor wire registry, join envelope
  and authenticated encryption. Telegram remains retrieve/edit/publish with
  last-successful-write-wins; pointer checks are not a distributed transaction.
- Live identity/membership and pointer checks below the GUI. Completion identity,
  time, revisions and publication metadata are stamped during reviewed preparation.
- Doctor/role/destination administration, project creation, announcements and
  Android's local audit UI remain in Android. This replaces the shared Excel
  client; it is not a second implementation of every Android administration screen.
- The app never opens/converts old workbooks. Private clinical artifacts remain
  preserved in ignored local storage.

## Arabic display

Labels, buttons, choices, status messages, tabs and readonly reviews use a
presentation-only Arabic shaping/bidi adapter on Linux. Logical paragraphs wrap
before each line is shaped, so wrapped clinical reviews keep the correct reading
order. Choice values, doctor IDs and callbacks remain logical Unicode. The review
copy action copies the complete original text, not presentation glyphs.

In Linux auto/shaped mode, patient/task/badge and search fields use
`desktop_input.py`. The editor keeps the original Unicode as its model and renders
shaped visual lines inside the field, with mapped caret and selection positions.
Typing, mouse placement/selection, copy/cut/paste, deletion, undo/redo and wrapping
operate on logical text. `get()`, bound variables and patient Save return original
Unicode, never presentation glyphs. The separate readback preview is no longer
needed. Password fields and native-rendering mode retain native controls.

TEXT_RENDERING accepts `auto`, `native` or `shaped`. Auto enables the Linux adapter
and leaves Windows rendering unchanged. Use native on a Tk build that already
handles bidi layout, or shaped to explicitly enable the adapter. PDF rendering
continues through its separate existing renderer.

## Configuration and local files

`.env` supports literal `CONNECTION_MODE`, `JOIN_FILE`, `TIMEZONE`, `DATA_DIR`,
`PDF_FONT`, `APPEARANCE` and `TEXT_RENDERING`. Quoted values are accepted; shell
expansion, interpolation and inline comments are not. Relative paths are based
on this folder. Unknown or duplicate keys fail explicitly. Pass another settings
file with `--env PATH` if needed.

The single [.env.example](.env.example) guide covers both connection methods.
For occasional admin use, copy it to `.env.raw`, change `CONNECTION_MODE` to `raw`,
uncomment/fill the raw fields, then run `python desktop_gui.py --env .env.raw` (use
`py -3.8` on Windows or your Linux venv's Python). `CONNECTION_MODE=raw` explicitly
enables `HOSPITAL_NAME`, `BOT_TOKEN`, `CHAT_ID`, `REPORTS_TOPIC_ID`, `CSV_TOPIC_ID`,
`DOCTORS_TOPIC_ID`, optional `ANNOUNCEMENTS_TOPIC_ID`, `ENCRYPT_TELEGRAM` and
`TELEGRAM_DATA_KEY`. Leave `JOIN_FILE` blank in raw mode. Use the existing project's
exact topics and encryption setting; encrypted projects require the original
Base64 data key. No key is generated or silently substituted. Missing/invalid
credentials fail without falling back to another connection.

Raw startup requires an online current-registry admin verified by a private
Telegram code **before opening the local workspace**. Subsequent shared actions
recheck the admin role and membership below the UI. Join-file startup retains
ordinary member login and offline draft reopening. Raw mode does not create a
project or doctor registry; publish the registry from Android first. Reconnecting
an already open project still uses a protected join file and rejects project/key/
topic changes. Join passwords, login codes and local unlock passphrases are never
read from `.env`.

A filled raw settings file contains plaintext secrets. `.env` and `.env.*` are
ignored by Git; keep them private and exclude them from bundles. These client
checks do not restrict someone who extracts the bot token and uses Telegram
directly. Revoke a suspected token through BotFather.

The default workspace is `%LOCALAPPDATA%\ShiftReportDesktop` on Windows and
`$XDG_DATA_HOME/ShiftReportDesktop` on Linux (otherwise
`~/.local/share/ShiftReportDesktop`), with a separate project-fingerprint database. Clinical data, baseline, doctor cache and receipts
are AES-GCM encrypted before entering SQLite. No raw connection config/password
is persisted. Encrypted projects derive the local key from the shared key;
unencrypted projects use a random local key protected by Windows DPAPI or the
Linux passphrase. On Windows, keep the `.key` file and original Windows user
profile. On Linux, keep the `.linux-key` file and remember the local passphrase.
Copying the database alone is insufficient; losing the protection key/passphrase
prevents opening its drafts. No automatic key replacement or plaintext fallback
is performed. New Linux key/database files are owner-only. A Windows named mutex
or Linux file lock permits one app instance per Windows session / Linux user.

Replacing a draft automatically saves one encrypted `.previous.srdraft` recovery
copy. Use the explicit backup action for additional copies. Restore accepts the
same project/key only, preserves the current delivery ledger and requires fresh
remote review. These are desktop drafts, not Android backup files. The ledger is
local to this workspace; backups/other PCs do not establish whether a report was
sent. Do not delete the workspace to retry an uncertain operation.

Protected-file reconnect is accessible before clinician login. While an existing
project is open it rejects different bots/groups/topics/keys and validates the
candidate against Telegram before replacing the live client. Drafts and receipts
are retained; clinician verification must be repeated. Revocation is in BotFather.
There is no manual token-entry endpoint.

PDF generation uses ReportLab, Arabic shaping/bidi and an embedded TTF. The default
is Windows `Fonts\arial.ttf` or a common Linux DejaVu Sans/Noto Naskh Arabic
path; `PDF_FONT` can select another Arabic-capable TrueType font. Reports paginate full clinical content/tasks/badges and omit patient last-edit,
routine-priority and unassigned labels. Preview files are temporary and removed on
normal interpreter exit; a crash can leave a readable PDF in the system temp folder.

## Bundling later

Use this folder as the source entry, with `desktop_gui.py` as the entry point.
Preserve CustomTkinter data/fonts/themes, certifi CA data and tzdata, together with
the chosen Arabic font if not using Windows Arial. Build for each intended machine
architecture using Python 3.8.10; x64 executables cannot run on 32-bit Windows.
No bundler dependency or packaging task is included. Keep secrets and clinical
workspace files out of any bundle.

## Verification

On 2026-10-18, Python 3.8 grammar/import inspection and offline Unicode CSV/bundle,
encrypted SQLite reopening/tamper, UNKNOWN-retry, receipt-preserving restoration, task completion stamps, five-doctor
rosters, stale-publication rejection, receipt-before-upload and remote/concurrent
order checks passed. A long dummy Arabic report generated eight PDF pages and its first
page was visually inspected. Python 3.8 x86/x64 wheel resolution is recorded in
[Current state](../docs/current-state.md). The checks used the host Python, not
Python 3.8.10, and did not exercise a real project.

On 2026-10-19, Linux checks passed for both shared-key and passphrase-protected
SQLite reopening, wrong/missing/tampered key rejection, owner-only key/database
permissions, delivery-ledger-preserving restore, cross-process lock/release, XDG
paths and default font discovery. A dummy Arabic PDF was generated with the Linux
default font; simulated PDF openers verified argument handling and missing/failed
handler errors without launching a real viewer. Linux launchers passed shell syntax
checks; all desktop modules passed Python 3.8 grammar inspection and whitespace checks.
The host is Python 3.14 with temporary dependency imports from the prior checks,
so these probes do not establish a fresh pinned Python 3.8–3.11 installation.

Arabic presentation checks on 2026-10-19 constructed the actual Linux main window
and dummy single/multiline editors, verified logical selection/status/copy values,
and inspected screenshots of labels, choices, reviews and live readbacks. Original
editable Unicode remained unchanged. Full clinical editing workflows, Windows
7/DPAPI execution, target fonts and
PDF viewer, live Telegram login/publication/delivery and Android-device round trips
remain unverified. Wheel availability is not Windows 7 certification. The pinned
legacy dependencies are compatibility choices, not a current-support claim.
See [protocol](PROTOCOL.md) and
[desktop troubleshooting](TROUBLESHOOTING.md).

The subsequent Linux update installed unpinned dependencies through Setup_Linux.sh
on the owner's system Python. Actual form/search editing, visual cursor/mouse
mapping, mixed Arabic/English text, clipboard, deletion, undo/redo, numeric
validation and patient Save passed dummy GUI checks; screenshots confirmed Arabic
inside the fields. Native-rendering/password exclusions and encrypted storage/PDF
checks also passed. This does not establish full clinical or live Telegram workflows.
