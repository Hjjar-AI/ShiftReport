# Android troubleshooting

[Behavior/evidence](../../status/current-state.md) · [Build/cache/signing](build.md) · [Remaining checks](../../status/testing-handoff.md).

Use an installed development app under [AGENTS permissions](../../../AGENTS.md); these recipes grant no build/test/migration/destructive-device/reset authority. Keep captures local/redacted. Clean Logcat does not prove success.

## Doctor registry needs attention

«جلب سجل الأطباء» retrieves; «نشر سجل الأطباء» reviews/publishes doctors/teams/permissions/destinations. «عرض التعديلات» excludes local PIN/unchanged saves. Unrelated edits need not block patient publication; publish/correct invalid unpublished roster/assignment references first through the admin shortcut. Fetch invalidates previews. Publication replaces the complete reviewed shift, last-write-wins; check destination/UNKNOWN before retry.

## Build, cache and signing failures

Use [offline verification](build.md#cached-verification-without-packaging), [account/cache isolation](build.md#what-is-shared-and-what-is-private), [APK paths](build.md#find-the-apk), [version reservation](build.md#automatic-app-patch-and-version-code-increments) or [signing](build.md#shared-debug-signing). Inspect the failing path/task; preserve caches/pins.

## Existing top-level functions become unresolved

2 November owner `assembleDebug`: six changed UI files failed despite existing declarations. Read-only inspection of launcher-selected `~/.gradle/shiftreport-project-cache/build-output/app` found 82-byte `app_debug.kotlin_module` indexing only `ReportPreviewScreenKt`, with component classes still present; transformed/resource copies matched. This suggests incomplete incremental metadata, cause unproven. Checkout-local `app/build` was older, not selected.

With explicit approval/build preflight, try one production-only non-incremental compile without packaging/version reservation:

```bash
./gradlew --offline -Pshiftreport.autoVersion=false -Pkotlin.incremental=false :app:compileDebugKotlin > /tmp/shiftreport-metadata-compile.log 2>&1
```

Recovery unrun. Preserve failure/generated state; do not delete caches or rewrite valid imports. A compile cannot establish runtime behavior.

## KSP cannot access FilterOutputStream.out

For `java.io`/`InaccessibleObjectException`, retain `kotlin.daemon.jvmargs=--add-opens=java.base/java.io=ALL-UNNAMED` in `android/gradle.properties` on the **Kotlin daemon** running KSP. Preserve toolchain/caches. [Arguments](https://kotlinlang.org/docs/gradle-compilation-and-caches.html#setting-kotlin-daemon-s-jvm-arguments); earlier owner build passed, not newer-source proof.

## KSP lookup cache cannot be read

`NegativeArraySizeException` in `IOUtil.readString`/`LookupSymbolExternalizer`/`FileToSymbolsMap`: capture trace/path; cause unproven. With approval/no writers, quarantine only the affected account's generated `app/kspCaches/debug` to a unique sibling. Preserve dependencies/app data/others' output. Earlier targeted repair passed an owner build. [Tracking](https://kotlinlang.org/docs/ksp-incremental.html).

## Capture one failure

Use installed platform-tools and an unlocked/authorized USB-debugging phone; check Windows drivers/Linux udev. From repo root, capture below, reproduce once, record exact Arabic action/error, stop Ctrl+C; preserve prior logs. CSV errors may appear only in a snackbar. Verify ignored/redacted captures with `git check-ignore -v local-debug/reproduction.txt`.

App-only: `adb shell pidof com.hos.rushdpatients`, then `adb logcat --pid=THE_PID -v threadtime`; refresh PID after restart. No hierarchy dumps during instrumentation. Wait for settled native search results; select the exact file. Picker timeout alone is not import failure.

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

Evidence collection must not clear data, uninstall, delete databases, force-publish or restore backups. SQLCipher copies stay encrypted/private. Bug reports/UI dumps may expose staff/patients; never publish tokens/PIN/passphrases/raw records.

## Current files

| File/data | Required representation |
|---|---|
| Patient CSV | Schema 8, 24 named columns/quoted shift metadata; explicit task JSON and ordered lab-group JSON |
| Telegram patient snapshot | Bundle 1 referenced by pinned sync-state v4; not an XLSX document |
| Doctor spreadsheet | Schema 1, UTF-8 with/without BOM, exact header below |
| Join | Password-encrypted `.srjoin.json`; connection/topics/key inside ciphertext |
| Backup | Password-encrypted `.rpb` |
| Encrypted Telegram data | `SRDATA1` files / `SRMSG1:` text; readable PDFs are intentional |

Excel Save as CSV cannot replace app metadata/bundle encoding; old XLSX-pinning workflows are unsupported. [Protocol](../../reference/telegram-protocol.md).

## Joining and empty exports

Protected join/password only; zero bytes invalid. Android 10+ prefers verified JSON in `Downloads/ShiftReport`, optionally share/picker; older Android primarily shares. Record save result; retry a fresh local export. Original empty-provider cause unknown. Doctor/backup encrypted staging retains random IDs/counts through recreation; cancel/verification failure cleans owned output. Expired admin needs new login/export.

Wrong keys/passwords/tampering fail; recover the correct key. Pre-login reconnect preserves state and rejects changed project/key/topics. BotFather revokes tokens. Enrolled admin may use PIN; erased devices need join/enrollment.

## Doctor CSV import

Check selected file/provider timestamp; use an untouched same-app registry export as control. Import separates preparation/review/commit; “saved locally, sync failed” needs sync diagnosis, not repeated mutation. Registry/actor changes can invalidate review.

Required header:

```text
schema_version,id,first_name,last_name,full_name,gender,clinical_role,supervisor_group_chat_id,telegram_id,telegram_username,custom_title,rank,is_permanent_admin,extra_options,updated_at,deleted_at
```

### Local file validation

Read-only; prints counts, never doctor records:

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

`blank_required_count` includes optional name parts; only schema/ID blanks are required-field errors.

Inspect encoding/size/first bytes without printing records:

```bash
file -bi doctors-import.local.csv
wc -c doctors-import.local.csv
xxd -l 16 doctors-import.local.csv
```

UTF-8 BOM is optional; UTF-16 is unsupported.

### Spreadsheet pitfalls

CSV UTF-8 (BOM optional), exact header/schema1; no ANSI/UTF16/XLSX. Preserve numeric IDs (negative group IDs, no scientific notation/separators/`.0`), CSV quoting and unique active admin ranks. Blank Telegram IDs allowed; doctor IDs required; name parts optional. Gender `M/MALE/F/FEMALE`; roles `RESIDENT/SUPERVISOR`.

## Tasks and reports

Tasks are explicit text/done records; pending excludes completed. Stored ownership/deadlines/stamps remain storage-only. No parsed notes/overdue counters/minute refresh/rollover; 08:30 labels the continuing ward.

Use dummy DEMO PDFs: Arabic/Latin, long text/tasks/badges, order/continuations/headings/footer. Android A5 cards always place two clinical columns below demographics/badges. In Acrobat/browser inspect search/copy/selection/index/name/Previous/Next/print. A5≈420×595pt; `pdfinfo`/`pdftotext -enc UTF-8` cannot establish viewer behavior. [Remaining checks](../../status/testing-handoff.md).

## Appearance and navigation

Inspect appearance/accent independence, neutral diagnosis/shared severity, field focus/error/disabled, large Arabic wrapping, 48dp targets, IME/fold/dock clearance. Bulk expansion immediate, individual brief. Filtered/pinned demo rounds advance only after successful Save-next, never invalid/discard/no-successor. [Limits](../../status/current-state.md#verification-and-limits).

## Report a problem

Record app/source/OS/device/viewer, steps, expected/actual/error, file/provider facts, local-save/remote-send and redacted timestamps. Separate static/build/device/live evidence; keep logs private/ignored, without credentials/clinical records.
