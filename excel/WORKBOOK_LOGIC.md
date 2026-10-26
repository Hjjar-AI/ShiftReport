# Workbook logic

Original: six sheets, only `Data` visible; ten extracted VBA components. The document modules and Module3 were empty. Module1 owned the clinical workflow; Module2 offered manual external-link removal.

| Original page/code | Actual role | Replacement |
|---|---|---|
| `Data` A:P | Patient fields; B1 `TODAY()`; D1:F1 three roster slots | Explicit date, full 1000-row count ranges, birth year T, priority R, stable UUID Q; complete roster in AppDoctors |
| `Report` | Copies Data into a printable general/supervisor table | Native A5 cards, seven-column Classic and paragraphs via AppPdf |
| `Settings` | Multiple tokens/groups in B; Arabic labels in D | B1:B20 removed from generated copy; useful Arabic labels retained; protected join owns credentials |
| `Lists` | Validation choices and five supervisor names | Retained privately; live AppDoctors identities own assignments and unlimited supervisor choice |
| `Backup` | Last local cell-copy snapshot | Original preserved; complete recovery workbook before retrieve/deletion |
| `Sheet1` | Empty hidden page | Retained hidden |
| `SaveData` | Copies A:P to an XLSX, uploads it and pins the document | Complete reviewed CSV7 bundle → encrypted upload → update existing JSON v4 pin → readback |
| `LoadData` | Finds a pinned document, downloads XLSX and overwrites rows | Read/decrypt JSON index → resolve file ID → download/decrypt/validate bundle → guarded local replacement |
| General/five doctor PDF buttons | Builds Excel report, posts to Settings destinations | Reviewed exact PDF + live registry route + publication before delivery + durable receipts |
| `SortPatients` | Gender/category/date using temporary R:S keys | Sorts the complete A:T row without overwriting patient priority/IDs; row order is reviewed/published |

The old pinned-XLSX protocol cannot communicate with the current app. Current format owners remain [Telegram protocol](../docs/reference/telegram-protocol.md), [patient CSV](../android/app/src/main/java/com/hos/rushdpatients/sync/CsvCodec.kt), [encryption](../android/app/src/main/java/com/hos/rushdpatients/config/ProjectDataCipher.kt) and [desktop wire](../desktop/desktop_wire.py).

| New VBA module | Owns |
|---|---|
| Module1 | Original button entry names, reentry guard, light Arabic dashboard buttons |
| SRJson / SRBytes | Canonical JSON, UTF-8, byte envelopes, IDs and dates |
| SRCrypto / SRGzip | Windows CNG AES-GCM/PBKDF2/SHA-256 and native gzip/DEFLATE |
| SRTelegram | Protected join, WinHTTP, index/file transport, clinician challenge and live membership |
| SRWire | CSV bundle, complete patient fields, doctor registry and validation |
| SRWorkbook | Clinical grid/full roster/tasks/badges, local stamps and app-state preservation |
| SRWorkflows | Retrieve, explicit original-row import, save, review/publish and final report confirmation |
| SRReceipts | Flushed local receipts, process lock, sender/admin resolution and duplicate-copy approval |
| SRPdf | Tajawal cards/Classic/paragraphs, full clinical content and continuation handling |

Tasks/badges remain explicit records. Optional task ownership never blocks saving; completion uses the verified saver. Patients retain app identity, revisions, edit stamps and metadata. Existing sort metadata stays until row order changes. No connection secret is written to app-state, review or report sheets.
