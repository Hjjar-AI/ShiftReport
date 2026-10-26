# Initial setup

24 October2026 VBA snapshot: retains date gates/completion stamps described below; do not infer parity with newer Android/desktop behavior. No Excel runtime certification.

## Files and private credentials

| Path | Purpose |
|---|---|
| `generate_workbook.py` | Python 3.8+ standard-library generator; no Telegram calls |
| `vba/*.bas` | Native VBA modules to import; no embedded credentials |
| `ThisWorkbook.txt` | Event code to paste into the workbook's document module |
| `fonts/` | Tajawal Regular/Bold and OFL licence; install fonts in Windows |
| `private/original.xlsm` | Untouched private source workbook copy |
| `private/legacy-credentials.local.json` | Original Settings credentials, reference only; never used by new VBA |
| `private/app-connection.local.json` | Private protected-join location/status; no guessed credentials |
| `credentials.example.md` | Public placeholder examples |
| `output/` | Ignored generated workbooks, manifest and adjacent VBA |

Private/output/join/local credentials stay ignored. Owner must supply current admin-exported `.srjoin.json` with original project key; no fixture substitutes. Masked interactive password is never saved to cells/configuration.

## Generate and import

From this directory:

```bash
python generate_workbook.py
```

Linux: `python3`; Windows without Python: use generated workbook+source. Other outputs:

```bash
python generate_workbook.py --source "private/original.xlsm" --format xlsm --output "output/MyWard.xlsm"
python generate_workbook.py --blank --format xlsx --output "output/Blank.xlsx"
```

Outputs never overwrite. Generated `.xlsm` is OOXML **without executable VBA** until manual import; no old VBA/control package copied.

1. Install `fonts/Tajawal-Regular.ttf` and `fonts/Tajawal-Bold.ttf`; close/reopen Excel if it was running.
2. Open the **generated** workbook, not the original containing old Telegram macros.
3. Press **Alt+F11**. Select the generated workbook's project. Use **File → Import File** for **all eleven** `vba/*.bas` files. No references/add-ins or “Trust access to the VBA project object model” setting are required.
4. Double-click **ThisWorkbook** in that project. Paste the contents of `ThisWorkbook.txt` into its code window. Do not import it as a separate class.
5. Save as **Excel Macro-Enabled Workbook (`.xlsm`)**. If starting from `.xlsx`, this Save As step is mandatory.
6. Return to Excel; press **Alt+F8**, run **Module1.InitializeWorkbook**. `AppHome` receives the Arabic action buttons. Only enable macros for this reviewed local file; no global security-setting change is required.

For actual legacy BIFF `.xls`, run the generator on Windows with installed Excel:

```bash
python generate_workbook.py --blank --format xls --output "output/Blank.xls"
```

Explicit conversion uses temporary macro-free workbook/installed Excel via `cscript.exe`; no Python third-party dependency. Import VBA/event code afterward; real `.xls` retains macros. Conversion unexecuted.

## Connect and work

1. In `AppHome`, open the app's protected join file. Candidate credentials/bot/group are validated before replacement; the workbook binds to bot/group/topics/key. Same-project token replacement retains clinical state. Original Settings tokens/groups are not read by the new transport.
2. Prove clinician identity. Select the published UUID from `AppDoctors`; start the bot privately, then enter its six-digit private code. Code validity is three minutes/five attempts; session is thirty minutes. No `getUpdates` is used.
3. Choose **Retrieve** for the published complete shift. Saved/unpublished changes block replacement. A local recovery copy is required before a destructive retrieve. Only the current Damascus shift (08:30 boundary, UTC+03) can be edited/published/sent; archives are for viewing.
4. For the original spreadsheet rows, use **Import original rows** only into an empty current remote shift. Set the explicit B1 date, select the full roster in `AppDoctors` D=`Y`, and resolve doctor names to live identities. Original ages estimate birth years: review T. Column O's auditor becomes a labelled follow-up note, since the app has no separate auditor field.
5. Edit `Data`; add tasks/badges using its hidden Q patient UUID. Copy the ID from Q by temporarily unhiding the column; never invent/change an existing ID. New patient rows leave Q blank. T is birth year, R is priority Y/N; C and P are calculated. M/N accept the exact doctor label or UUID from `AppDoctors`.
6. `AppTasks`: leave B blank for a new task, E optional doctor UUID, F deadline `YYYY-MM-DD HH:MM` in Damascus time, G optional due-shift date, H=`Y/N`. Save stamps completion actor/time; the locked completion columns are not inputs. Delete task/badge rows explicitly when deleting their patient. Notes never become tasks.
7. Save locally, then **Review/publish** the full replacement. This includes removals, roster, stored order, tasks and badges. A changed remote baseline blocks stale replacement; preserve the workbook and reconcile with a separately retrieved copy. There is no automatic merge or force-publish shortcut.
8. Choose PDF style in `AppHome` B20: `CARDS`, `CLASSIC` or `PARAGRAPHS`. General/supervisor reports use the live registry destination, not legacy cells. Review the exact generated file, approve publication if needed, then give final Send confirmation. Cancelling delivery does not undo already confirmed publication.

Excel's own Save/Ctrl+S retains cell edits offline. The clinical Save action currently requires a verified live registry/session before it stamps records; it is not an offline PIN client. Registry administration, project creation, rollover and backup import remain app operations; this workbook does not manufacture those privileges or alter the app schema.

## Delivery and recovery

Before a publication/report upload, VBA flushes an `UNKNOWN` receipt under `%LOCALAPPDATA%\ShiftReportExcel\<project-digest>.receipts.json`. A per-project Windows file lock serializes this client's receipt writes across Excel processes. `AppReceipts` displays the ledger; editing that sheet cannot change the authoritative file. Receipts contain hashed destinations and operation IDs, without bot tokens, raw chat IDs or clinical content.

UNKNOWN blocks shift/destination retry: inspect actual destination/pin, then sender/live admin **Resolve**. Journal absence proves no failure; confirmed copies need explicit approval. Receipts are local, not cross-device exactly-once.

Preserve recovery workbooks and the ledger when troubleshooting. PDFs are readable by design. Owned temporary previews are deleted best-effort at workbook close; a crash or open viewer may leave them behind. Sign-out clears in-memory connection/session state and leaves clinical cells intact.

## Windows 7 and PDF limits

- Windows 7 **SP1**, Excel **2010+** with VBA7; pointer declarations cover both 32-bit and 64-bit Office. The generator can run elsewhere, or in a Windows 7-compatible Python 3.8 installation. Excel use itself requires no Python, PowerShell, pywin32 or desktop app.
- WinHTTP is configured for TLS 1.2 with certificate verification and no redirects/proxy inheritance. Windows must already have functioning TLS 1.2 support and trusted certificate roots. No registry, OS or certificate settings are changed automatically. See Microsoft's [WinHTTP TLS configuration](https://learn.microsoft.com/en-us/windows-server/networking/configure-secure-protocol-options-winhttp).
- AES-GCM and PBKDF2 use Windows CNG; PBKDF2-HMAC-SHA256/210000 matches the protected join format. Microsoft documents [PBKDF2 support from Windows 7](https://learn.microsoft.com/en-us/windows/win32/api/bcrypt/nf-bcrypt-bcryptderivekeypbkdf2) and [VBA7 pointer compatibility](https://learn.microsoft.com/en-us/office/client-developer/shared/compatibility-between-the-32-bit-and-64-bit-versions-of-office).
- PDF export needs working Excel fixed-format support and an installed printer/page setup accepting the selected paper size, plus a default PDF viewer for sending. Cards use A5, 15 mm sides/20 mm top/10 mm bottom, Tajawal and full clinical/tasks/badges. Classic uses the app's seven-column proportions; paragraphs preserve the same content. Long fields split into continuation rows/pages instead of clipping to Excel's row-height limit.
- Excel pagination, Arabic shaping and hyperlink export differ from Android/ReportLab. Workbook index/Previous/Next links target patient page tops, but survival as active **PDF** links and exact rendered geometry remain unverified. There is no pixel-parity claim, and custom app palettes/options are not reproduced in full.

Generation and source/XML inspection do not establish VBA compilation, Windows 7 execution, PDF readability/navigation or live interoperability. No original credentials/groups were contacted during this work.
