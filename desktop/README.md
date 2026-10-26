# ShiftReport desktop

Standalone CustomTkinter Windows/Linux client; no Excel/VBA/COM/Office dependency. Source entry `desktop_gui.py`; all `desktop_*.py` and fonts must stay together. [Protocol](../docs/reference/telegram-protocol.md) · [Troubleshooting](../docs/guides/desktop-troubleshooting.md) · [Evidence](../docs/status/current-state.md#verification-and-limits).

## Run on Windows

Target CPython3.8.10 with Tk/pip/launcher, Windows7SP1 compatibility **unverified**. Run `Setup_Windows.cmd` once, then `Start_Desktop.cmd`, or:

   ```bat
   py -3.8 -m pip install --only-binary=:all: -r requirements-win7.txt
   py -3.8 desktop_gui.py
   ```

## Run on Linux

Graphical session, Python≥3.8/Tk/venv/xdg-utils/default PDF viewer; writable source for `.venv`:

```sh
cd desktop
./Setup_Linux.sh
./Start_Linux.sh
```

`PYTHON_BIN=/path/to/python ./Setup_Linux.sh` selects interpreter. Setup touches only this `.venv`; Linux dependencies resolve compatible binary releases, Windows pins remain separate. Debian/Ubuntu typically need matching `python3-tk`/`python3-venv`. Check existing environment before reinstalling.

## Connect and work

1. Put administrator's protected `.srjoin.json` beside source/EXE or select it; enter password interactively. Discovery does not validate connection. Publish current Android registry/CSV first for existing projects; older malformed metadata must be republished, not schema-converted.
2. Open bot→Start; select yourself in **الطبيب الحالي**, request private Telegram code and verify. First verification enrolls optional local PIN; shared actions always require fresh authority.
3. **جلب البيانات** retrieves current ward. Select roster; edit patients/tasks/badges. Save/roster/order/delete commits encrypted local draft; unfinished forms stay private, Cancel confirms discard. Saved drafts reopen offline.
4. **رفع للشبكة وإرسال تقرير** combines destination selection, complete-snapshot change review, actual PDF/text preview and final send confirmation. Changed draft/actor/pointer/registry invalidates review; no automatic upload/merge/retry. Pending publication precedes delivery; cancellation does not undo a completed publication. Viewer-launch failure stops sending.
5. Data Management retains explicit reviewed publication without sending, recovery and backup tools. Standalone clinical Export always creates a marked draft and works offline; normal PDF copies remain optional, checked by default, and save to device storage only after confirmed Telegram delivery; PDFs are readable. Names use `Report_YYYYMMDD` / `Assignments_YYYYMMDD`, supervisor names and DRAFT/DEMO suffixes; Telegram PDF titles list shift doctors.


Appearance offers Turquoise, Lapis, Rose and Poppy; legacy saved accents map to these four choices. The08:30 report day is captured for review and kept across PDF/text generation, confirmation and sending without changing stored ward identities. Retired task-time/audit computation is removed; old stored metadata remains readable.

## Create a project

New unused workspace/group only: human admin plus bot send/pin rights; create optional topics in Telegram. Set explicit raw connection in private `.env`, blank `JOIN_FILE`, exact hospital/bot/group/topics/encryption/original key; no silent key generation/conversion. Choose **إنشاء مشروع جديد**, identify first admin, private challenge, review config/authority, confirm. Encrypted UNKNOWN precedes registry upload→General index→pin→readback; no dummy ward/patients. Existing pin/draft/baseline/receipts block creation. Failure: inspect Telegram/reconnect, never delete workspace or blindly retry. No distributed-transaction claim.

Verified live admin can export fresh `.srjoin.json` with password≥10, shared connection/key only; refuses overwrite, rechecks role and reopens encrypted output. Send password separately. Live Windows/desktop creation/export remains unverified.

## Configuration and storage

| Item | Contract |
|---|---|
| Discovery | Configured adjacent `.env` takes precedence; otherwise protected joins/valid named `*.env`, chooser on ambiguity, picker if absent. `--env PATH` overrides; relative paths resolve beside selected env, not cwd/temporary EXE extraction. Preferences-only env may accompany join. |
| `.env` | Literal `CONNECTION_MODE`, `JOIN_FILE`, `TIMEZONE`, `DATA_DIR`, `PDF_FONT`, `APPEARANCE`, `TEXT_RENDERING`; quoting allowed, interpolation/inline comments/unknown or duplicate keys rejected. See [.env.example](.env.example); no passwords/codes. |
| Join/raw/reconnect | Protected join default; raw exact credentials/key requires live admin before workspace/shared actions. Pre-login reconnect file-only, validates same bot/group/topics/key and preserves drafts/receipts; requires identity verification again. BotFather revokes, local token replacement does not. |
| Local encryption | AES-GCM before SQLite, project fingerprint. Shared data key if encrypted project; otherwise random DPAPI key on Windows or PBKDF2/AES-GCM wrapped Linux key with separate ≥10-character passphrase. Never persist passphrase or silently replace keys/plaintext. |
| Workspace | Windows `%LOCALAPPDATA%\ShiftReportDesktop`; Linux `$XDG_DATA_HOME/ShiftReportDesktop` or `~/.local/share/ShiftReportDesktop`. Retain DB+Windows `.key`/original profile or Linux `.linux-key`/passphrase. Owner-only new Linux files; one instance per Windows session/Linux user. |
| PIN | Local encrypted hashes/throttle,1/5/15-minute idle lock, immediate lock/reopen lock; hidden drafts retained. Telegram-code recovery; fresh Telegram authority for shared mutations. PIN never in CSV/portable backup. |
| Recovery | Automatic `.previous.srdraft` before replacement, explicit additional backups; same project/key/fresh review/current delivery ledger retained. `.rpb` is separately labelled Android-compatible backup. Backup/another PC cannot prove delivery; never erase UNKNOWN. |
| PDF | Bundled Tajawal Regular/Bold/OFL and ReportLab/shaping/bidi; optional TTF plus matching `-Bold.ttf`. Device-local encrypted layout/paper/palette/shading (demo memory), A4/A5 portrait/light/Medium fixed; cards fix geometry/hide shading. Cancel saves nothing. Readable previews clean on normal exit, may remain after crash. |

## Clinical, reports and administration

Optional profiles/plain multiline diagnosis/treatment/follow-up/labs, explicit text/done tasks, badges/priority, valid assignments; no new audit/editor/completion stamps. Legacy metadata retained.08:30 in selected IANA zone (default Asia/Damascus) labels continuing ward; no date browser/copy-forward. New publications one ward; old multi-list Fetch chooses newest. Fetch Undo/admin reviewed prior-publication rollback remain.

Grouping is presentation-only; manual/name/admission-duration/workbook sort presets persist order, priority first. Imported custom specs remain until changed. Manual arrows require unfiltered/ungrouped list. Standalone clinical export is always a labelled local draft. Admins export names-only assignments from the sidebar button directly below upload/send: local preview then save, no upload or draft marking. Normal PDF copying is a checked-by-default optional choice in final-confirmed upload/send, applied after successful Telegram delivery; sending retains its own review/confirmation. Resident team membership independent of cover, one active supervisor/resident; move members before unsafe supervisor changes. Cross-team cover never changes membership.

| Action | Guard |
|---|---|
| Recycle/Undo | Daily idle cleanup while the app is unlocked/open removes local recycle entries deleted at least seven days ago; catches up after reopening. Existing undated entries receive a full grace week from their first sweep. Full records/tasks/badges/positions encrypted; no duplicate overwrite, validate assignments/draft; publication separate. Undo retains baseline/receipts and rejects later saves. |
| Doctors/teams/roles/routes | Full reviewed registry/live admin/pointers; protect permanent admins/ranks/references, retain stable IDs/omitted active doctors, read back exact publication. UNKNOWN before writes. |
| CSV | Complete patient CSV7 export; live-admin schema1 doctor import/export, reviewed local commit/full registry publication. Missing rows do not delete; no PINs; reject unsupported options. Username/update/delete fields are CSV-only; exported update time is export time. Team in `extra_options=teamSupervisor:<id>`. |
| Reports | Classic7-column/paragraphs/linked A5, full clinical/tasks/order, no routine/unassigned/last-edit labels. A5 geometry owned by [current state](../docs/status/current-state.md#reports-and-exports). Exact preview-byte saving for general/supervisor/names-only files, with clinical copies after confirmed delivery or explicit labelled-draft review; admin assignments use independent local preview/save. Text encrypted document or UTF-16-bounded chunks. |
| Names-only | Live admin checked below GUI/renderer/save; A4 portrait,3×56.7mm supervisor tables, independent/empty teams/unassigned, continuous numbering/headers; current-roster historical limitation. |
| Delivery | Publish then exact reviewed delivery; unchanged content may reuse publication/receipt. UNKNOWN blocks shift/destination across format/publication; live sender/admin resolves, extra copy explicitly approved. Confirmed send stays sent despite local bookkeeping failure. Device-local receipts are not exactly-once evidence. |

## Arabic display and demo

Store/edit/copy logical Unicode; shape presentation only. `TEXT_RENDERING=auto` shapes on Linux, `native` uses Tk, `shaped` forces adapter. `desktop_input.py` maps visual caret/selection/wrapping/clipboard/Undo to logical text; reviews copy originals, passwords stay native.

`python desktop_gui.py --demo` or sidebar opens memory-only beverage/tree data; no connection/clinical storage/transport. Ordinary forms/settings/reset; only explicit PDF export, DEMO every page. Reset/close discards.

## Packaging and verification

[Windows packaging](../docs/guides/windows-exe-packaging.md) owns commands/resources/architecture requirements; keep secrets/workspace out of bundles. No package created here. Historical60-case/bidirectional-file/Linux demo checks do not establish current GUI, Windows7/DPAPI/package or live Android↔desktop workflows. [Remaining checks](../docs/status/testing-handoff.md) · [Execution guide](../docs/guides/deep-testing.md).
