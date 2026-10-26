# Android troubleshooting

[Current state and verification limits](../status/current-state.md) · [Windows/Linux desktop troubleshooting](desktop-troubleshooting.md) · [Windows EXE packaging guide](windows-exe-packaging.md)

Use an already installed development app. These recipes do not authorize builds, compilation, test-suite work, migrations, destructive device actions or data resets; follow [AGENTS.md](../../AGENTS.md). Keep clinical captures local and redacted. A clean Logcat is not proof that an operation succeeded.

## Doctor registry needs attention

«جلب سجل الأطباء» retrieves; «نشر سجل الأطباء» reviews/publishes local doctors/teams/permissions/destinations. «عرض التعديلات» shows portable changes, excluding local PIN/unchanged saves. Unrelated registry edits need not block patient publication; unpublished invalid roster/assignment references must be published/corrected first (admin registry shortcut). Fetch invalidates previews; review again. Publication replaces the complete reviewed local shift, last-write-wins; check destination/UNKNOWN before retry.

## Build, cache and signing failures

Use the [build/cache/signing guide](android-build.md) for [offline verification](android-build.md#cached-verification-without-packaging), [account isolation and read-only snapshots](android-build.md#what-is-shared-and-what-is-private), [APK locations](android-build.md#find-the-apk), [version reservation](android-build.md#automatic-app-patch-and-version-code-increments) and [signature mismatch](android-build.md#shared-debug-signing). Inspect the failing path/task before changing settings; preserve caches and pins.

## KSP cannot access FilterOutputStream.out

For unopened `java.io`/`InaccessibleObjectException`, retain `kotlin.daemon.jvmargs=--add-opens=java.base/java.io=ALL-UNNAMED` in `android/gradle.properties` on the **Kotlin daemon** where KSP runs. No toolchain/cache change. [Arguments](https://kotlinlang.org/docs/gradle-compilation-and-caches.html#setting-kotlin-daemon-s-jvm-arguments); earlier owner build passed, not newer-source proof.

## KSP lookup cache cannot be read

`NegativeArraySizeException` in `IOUtil.readString`/`LookupSymbolExternalizer`/`FileToSymbolsMap`: capture trace/output path; cause unproven. With owner approval/no writers, quarantine only affected account’s generated `app/kspCaches/debug` to unique sibling, preserving diagnosis. Never clear dependencies/app data/others’ output. Earlier targeted repair passed an owner build. [Tracking](https://kotlinlang.org/docs/ksp-incremental.html).

## Capture one failure

Use installed platform-tools, USB debugging/unlocked authorized phone; check Windows driver/Linux udev. From repo root capture below, reproduce once, record exact Arabic action/error, stop Ctrl+C; do not clear prior logs. CSV errors may exist only in snackbar. Keep captures ignored/redacted (`git check-ignore -v local-debug/reproduction.txt`). App-only capture: `adb shell pidof com.hos.rushdpatients`, then `adb logcat --pid=THE_PID -v threadtime`; refresh PID after restart. Do **not** run hierarchy dumps alongside instrumentation. Wait for native search results to settle before selecting exact file; picker timeout alone is not import failure.

```bash
adb devices -l
adb shell dumpsys package com.hos.rushdpatients
mkdir -p local-debug
adb logcat -v threadtime > local-debug/reproduction.txt
```

```bash
adb exec-out screencap -p > local-debug/error.png
adb shell uiautomator dump /sdcard/shiftreport-window.xml
adb pull /sdcard/shiftreport-window.xml local-debug/window.xml
adb shell rm /sdcard/shiftreport-window.xml
rg -n -i 'Rushd|AndroidRuntime|FATAL EXCEPTION|ANR|SecurityException|IOException|CSV|telegram|timeout|denied' local-debug/reproduction.txt
```

## Choose the right diagnostic

| Symptom | What to check |
|---|---|
| Device unauthorized/offline | Unlock/authorize, reconnect cable; inspect `adb devices -l`. Use `adb -s SERIAL …` with multiple devices. |
| Crash or freeze | Capture the full reproduction; `adb logcat -b crash -d` and `adb shell dumpsys activity lastanr`. |
| Unexpected restart/navigation | Record rotation, backgrounding, lock state and active form; `adb shell dumpsys activity activities`. |
| File picker/write failure | Provider, selected filename, nonzero bytes and exact error; retry local Downloads. Never infer success from file existence alone. |
| Network/sync failure | Connectivity, bot membership/admin/pin rights, project topics/key, exact sync state and pending/conflict indicator. Connectivity alone does not mean data is current. |
| Background work delayed | `adb shell dumpsys jobscheduler` and `adb shell dumpsys deviceidle`; record battery restrictions before changing them. |
| Memory/jank | `adb shell dumpsys meminfo com.hos.rushdpatients` and `adb shell dumpsys gfxinfo com.hos.rushdpatients`. |

Do not clear app data, uninstall, delete its database, force-publish, or restore a backup merely to collect evidence. SQLCipher files are private clinical data; copying them does not produce a readable SQLite database. Bug reports and UI dumps may contain staff/patient information. No token, PIN, passphrase or raw record belongs in a public issue.

## Current files

| File/data | Required representation |
|---|---|
| Patient CSV | Schema 7, exact named columns and quoted shift metadata; tasks are explicit JSON |
| Telegram patient snapshot | Bundle 1 referenced by pinned sync-state v4; not an XLSX document |
| Doctor spreadsheet | Schema 1, UTF-8 with/without BOM, exact header below |
| Join | Password-encrypted `.srjoin.json`; connection/topics/key inside ciphertext |
| Backup | Password-encrypted `.rpb` |
| Encrypted Telegram data | `SRDATA1` files / `SRMSG1:` text; readable PDFs are intentional |

Ordinary Excel Save as CSV is not a substitute for app metadata or current bundle encoding. Do not open both clients against an old XLSX-pinning workflow. See [the protocol](../reference/telegram-protocol.md).

## Joining and empty exports

Protected join/password only; zero bytes invalid. Android10+ prefers verified JSON in `Downloads/ShiftReport`, optional share/picker; older Android primarily shares. Record save result/retry fresh local export; original empty-provider cause unknown. Doctor/backup encrypted staging keeps random IDs/counts across recreation; cancel/verification failure cleans owned output, expired admin needs new login/export.

Wrong keys/passwords/tampering fail; recover correct key rather than replacing it. Pre-login same-project file reconnect preserves state and blocks changed project/key/topics; same-bot token revocation belongs to BotFather. Existing enrolled admin may use PIN; erased devices require join/enrollment.

## Doctor CSV import

Check the exact selected file/provider timestamp. Export an untouched registry from the same installed app as a control. Import has separate preparation, review and commit phases; “saved locally, sync failed” means investigate sync rather than repeat the mutation. Full registry/actor changes can invalidate the reviewed plan.

Required header:

```text
schema_version,id,first_name,last_name,full_name,gender,clinical_role,supervisor_group_chat_id,telegram_id,telegram_username,custom_title,rank,is_permanent_admin,extra_options,updated_at,deleted_at
```

### Local file validation

This check does not modify the file and does not print doctor data:

```bash
python3 - <<'PY'
import csv
from collections import Counter
from pathlib import Path

path = Path("doctors-import.local.csv")
required = [
    "schema_version", "id", "first_name", "last_name", "full_name", "gender",
    "clinical_role", "supervisor_group_chat_id", "telegram_id",
    "telegram_username", "custom_title", "rank", "is_permanent_admin",
    "extra_options", "updated_at", "deleted_at",
]
with path.open(encoding="utf-8-sig", newline="") as stream:
    reader = csv.DictReader(stream)
    rows = list(reader)

print("header_exact:", reader.fieldnames == required)
print("row_count:", len(rows))
for field in ("id", "full_name"):
    values = [(row.get(field) or "").strip().casefold() for row in rows]
    counts = Counter(value for value in values if value)
    print(field + "_duplicate_count:", sum(count > 1 for count in counts.values()))
telegram = [(row.get("telegram_id") or "").strip() for row in rows]
counts = Counter(value for value in telegram if value)
print("telegram_duplicate_count:", sum(count > 1 for count in counts.values()))
print("blank_required_count:", sum(
    not (row.get(field) or "").strip()
    for row in rows for field in ("schema_version", "id", "first_name", "last_name")
))
print("rows_with_extra_columns:", sum(None in row for row in rows))
PY
```

The legacy `blank_required_count` diagnostic includes name parts, now optional; only schema/ID blanks are required-field errors.

Also check encoding and the first bytes without printing the records:

```bash
file -bi doctors-import.local.csv
wc -c doctors-import.local.csv
xxd -l 16 doctors-import.local.csv
```

UTF-8 with or without a UTF-8 BOM is accepted. A UTF-16 spreadsheet export is not.

### Spreadsheet pitfalls

CSV UTF-8 (BOM optional), exact header/schema1; no ANSI/UTF16/XLSX. Preserve numeric IDs (negative group IDs, no scientific notation/separators/`.0`), CSV quoting and unique active admin ranks. Blank Telegram IDs allowed; doctor IDs required; name parts optional. Gender `M/MALE/F/FEMALE`; roles `RESIDENT/SUPERVISOR`.

## Tasks and reports

Tasks are explicit text/done records, never parsed notes; pending counts exclude completed tasks. Legacy ownership/deadlines/stamps remain storage-only. No overdue counters, minute refresh or rollover;08:30 labels the continuing ward day.

Use dummy DEMO PDF: mixed Arabic/Latin,long content/tasks,badges,order/continuations/headings/footer. Acrobat/browser: search/copy/selection,index/name/Previous/Next,print. A5≈420×595pt; `pdfinfo`/`pdftotext -enc UTF-8` do not establish viewer behavior. [Remaining](../status/testing-handoff.md).

## Appearance and navigation

Check independent appearance/accent and neutral diagnosis/shared severity; field focus/error/disabled,large Arabic wrapping,48dp,IME-safe actions,fold-safe regions and last-row dock clearance. Bulk immediate/individual brief. In filtered/pinned demo rounds, only successful Save-next advances; invalid/discard/no-successor must not. See [actual limits](../status/current-state.md#verification-and-limits).

## Report a problem

Record app/source/OS/device/viewer,steps,expected/actual,error,file/provider facts,local-save versus remote-send and redacted timestamps. Distinguish static/build/device/live evidence. Keep logs/reports ignored/private; no credentials/clinical records.
