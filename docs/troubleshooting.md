# Android troubleshooting

[Current state and verification limits](current-state.md) ·
[Windows/Linux desktop troubleshooting](../desktop/TROUBLESHOOTING.md) ·
[Windows EXE packaging guide](windows-exe-packaging.md)

Use an already installed development app. These recipes do not authorize builds,
compilation, test-suite work, migrations, destructive device actions or data resets;
follow [AGENTS.md](../AGENTS.md). Keep clinical captures local and redacted.
A clean Logcat is not proof that an operation succeeded.

## Reuse the workstation Gradle cache

For the encountered failures, causes and final fixes, see
[Shared-workstation build issues](android-shared-workstation-build-fixes.md).

The POSIX `android/gradlew` launcher automatically uses
`/opt/android-shared/.gradle` when its root and existing daemon directories are
readable, writable and traversable, and
`GRADLE_USER_HOME` is unset or points to the normal `$HOME/.gradle` location.
This also handles development environments that pre-export the ordinary home cache.
The shared cache contains the project's Gradle 8.7 distribution. A genuinely custom
`GRADLE_USER_HOME` takes precedence; other machines
keep Gradle's normal per-user cache. The wrapper version and distribution checksum
are unchanged. This setting applies to the shell launcher; Android Studio has its
own Gradle user-home setting.

A writable cache root can still contain another account's private `daemon/8.7`
directory. This caused an early `java.io.IOException: Permission denied` after
version reservation on the shared workstation. The launcher now checks daemon
access before automatic shared-cache selection and otherwise keeps the personal
cache. Explicit custom user-home settings still take precedence. If the same
failure remains, include `--stacktrace` in the next owner-run build and share its
failing path; the short error alone cannot identify every possible permission issue.

The POSIX launcher also defaults the project cache to
`$HOME/.gradle/shiftreport-project-cache`, isolating configuration-cache permissions
between accounts sharing this checkout. An explicit `--project-cache-dir` is
preserved. Use the ordinary `./gradlew assembleDebug` command from `android/`;
this documentation does not authorize the agent to run a build.

The launcher also supplies `shiftreport.buildRoot` so generated resources, Kotlin
output and APKs use account-local directories rather than another account's
`app/build`. Its default debug APK is
`$HOME/.gradle/shiftreport-project-cache/build-output/app/outputs/apk/debug/app-debug.apk`.
An explicit `-Pshiftreport.buildRoot=/your/path` overrides that output root.
Android Studio and callers without the property retain standard output directories.
Old shared build directories are left untouched; they are no longer used by the
POSIX launcher's default build.

Successful assemble tasks also export versioned APK copies into
`Desktop/ShiftReport/<variant>/`, for example
`ShiftReport-v1.1.1.20261007-debug.apk`. The shell uses the current user's XDG Desktop
location when available; otherwise it uses `$HOME/Desktop`. Use
`-Pshiftreport.apkExportDir=/your/folder` to change that export root. These names
separate output files, while Android app IDs and installation behavior remain unchanged.

The POSIX launcher now increments the app patch/version code before normal debug/
release packaging. Failed attempts consume a number; help/dry runs do not. Keep
`android/gradle/app-version.properties` when sharing the project with another PC.
IDE/direct Gradle and Windows launchers do not reserve new versions. See the
[automatic versioning details](android-shared-workstation-build-fixes.md#automatic-app-patch-and-version-code-increments).
The export task uses built-in regex naming to avoid the script-object capture
reported during configuration-cache storage. New version properties can cause
normal cache invalidation; that is not a cache-storage failure.




## Capture one failure

Install Android platform-tools using your normal SDK setup; enable USB debugging,
unlock the phone and accept its computer authorization prompt. On Windows, install
the manufacturer's USB driver if needed; on Linux check USB/udev permissions.
From the project root:

```bash
adb devices -l
adb shell dumpsys package com.hos.rushdpatients
mkdir -p local-debug
adb logcat -v threadtime > local-debug/reproduction.txt
```

Reproduce once, record the exact Arabic message and action, then stop capture with
Ctrl+C. Avoid clearing existing logs before collecting a failure. Capture the screen
or UI hierarchy if the error is only a short-lived snackbar:

```bash
adb exec-out screencap -p > local-debug/error.png
adb shell uiautomator dump /sdcard/shiftreport-window.xml
adb pull /sdcard/shiftreport-window.xml local-debug/window.xml
adb shell rm /sdcard/shiftreport-window.xml
rg -n -i 'Rushd|AndroidRuntime|FATAL EXCEPTION|ANR|SecurityException|IOException|CSV|telegram|timeout|denied' local-debug/reproduction.txt
```

`DoctorsViewModel` displays CSV exceptions in a snackbar rather than always logging
them under `Rushd`. Save the visible error as well as Logcat. Verify captures are
ignored with `git check-ignore -v local-debug/reproduction.txt` before sharing.

For an app-only stream, obtain its running PID with
`adb shell pidof com.hos.rushdpatients`, then use `adb logcat --pid=THE_PID -v threadtime`.
A restarted process has a different PID.

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

Do not clear app data, uninstall, delete its database, force-publish, or restore a
backup merely to collect evidence. SQLCipher files are private clinical data;
copying them does not produce a readable SQLite database. Bug reports and UI dumps
may contain staff/patient information. No token, PIN, passphrase or raw record belongs
in a public issue.

## Current files

| File/data | Required representation |
|---|---|
| Patient CSV | Schema 7, exact named columns and quoted shift metadata; tasks are explicit JSON |
| Telegram patient snapshot | Bundle 1 referenced by pinned sync-state v4; not an XLSX document |
| Doctor spreadsheet | Schema 1, UTF-8 with/without BOM, exact header below |
| Join | Password-encrypted `.srjoin.json`; connection/topics/key inside ciphertext |
| Backup | Password-encrypted `.rpb` |
| Encrypted Telegram data | `SRDATA1` files / `SRMSG1:` text; readable PDFs are intentional |

Ordinary Excel Save as CSV is not a substitute for app metadata or current bundle
encoding. Do not open both clients against an old XLSX-pinning workflow.
See [the protocol](../desktop/PROTOCOL.md).

## Joining and empty exports

Join requires the administrator's protected file/passphrase; connection/topic entry
is only for Create. A zero-byte file is invalid. New Android 10+ exports primarily
save verified JSON directly to `Downloads/ShiftReport`; sharing and optional picker
remain. On older Android sharing is primary. Record the displayed save result and
retry a fresh local export; the owner's original empty-provider cause is unconfirmed.

Doctor/backup exports stage encrypted bytes before the picker. Only random IDs/counts
survive recreation; cancellation or failed verification cleans new staging/output.
An expired admin session requires sign-in and a new export. Never manually paste
credentials or fake metadata into a join file.

Wrong key/password/tampered data must fail explicitly. Recover the correct join
file/key rather than generating a new key. Same-bot token replacement is through
BotFather and protected reconnect; different project/key/topics are blocked to
protect existing clinical data. Reconnect is available before login. An enrolled
same-device administrator may return using the existing PIN; erased devices still
need joining and first enrollment.

## Doctor CSV import

Check the exact selected file/provider timestamp. Export an untouched registry from
the same installed app as a control. Import has separate preparation, review and
commit phases; “saved locally, sync failed” means investigate sync rather than
repeat the mutation. Full registry/actor changes can invalidate the reviewed plan.

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

Also check encoding and the first bytes without printing the records:

```bash
file -bi doctors-import.local.csv
wc -c doctors-import.local.csv
xxd -l 16 doctors-import.local.csv
```

UTF-8 with or without a UTF-8 BOM is accepted. A UTF-16 spreadsheet export is not.

### Spreadsheet pitfalls

- Export as **CSV UTF-8**, not legacy ANSI, UTF-16, XLS, or XLSX.
- Keep the exact header names.
- Do not let a spreadsheet convert long Telegram IDs to scientific notation.
- Do not introduce thousands separators or decimal `.0` suffixes into numeric IDs.
- Preserve negative supervisor group chat IDs.
- Quote fields containing commas, quotes, or line breaks according to CSV rules.
- Do not create duplicate active administrator ranks.
- Blank Telegram IDs are allowed; blank doctor IDs and name parts are not.
- Valid `gender` values are `M`, `MALE`, `F`, or `FEMALE`.
- Valid `clinical_role` values are `RESIDENT` and `SUPERVISOR`.
- `schema_version` must currently be `1`.


## Tasks and reports

Pending includes overdue; never add the two counts together. Cards count one
patient, dashboards the ward. Only unfinished records with reached deadlines are
overdue. The minute timer refreshes counts; shift-end means 08:30 next calendar day.
New tasks may remain unassigned. Completion is stamped on patient save; rollover
keeps unfinished tasks/deadlines and copies start without tasks. Narrative notes
are never inferred as task records.

Use a **demo** PDF to check Arabic names, mixed Arabic/Latin text, long paragraphs,
tasks/badges, and multiple pages without exposing clinical data. Open the same file
in Acrobat and a browser; select/copy/search text and try index/name/Previous/Next
links. Check ordering, every demo-page label, headings/footers and continuation pages.
A5 cards should report approximately 420×595pt in `pdfinfo`; `pdftotext -enc UTF-8`
checks extraction but does not establish viewer behavior. Actual rendering, selection,
links and printing remain unverified for the current source.

## Appearance and navigation

Appearance and accent are independent; badge severity is shared and diagnoses are
neutral. Check field-label focus/error/disabled states, large Arabic font wrapping,
48dp targets, keyboard-safe form actions and fold-safe regions. Transparent bottom
navigation gutters require content behind the pill, with end clearance for the last
row. Bulk expansion is immediate; individual expansion animates briefly.

For Save and next, use a filtered/pinned demo ward: successful save advances in
rounds order; invalid/stale save, discard or a missing successor must not advance.
These device/rendering checks have not been performed here.

## Report a problem

Include app version, Android/Windows version, device/viewer, action sequence,
expected/actual result, exact displayed error, selected file facts, local-save versus
remote-send status, and redacted capture timestamps. Identify source-only evidence
separately from build/device/live-Telegram verification. Keep logs and reports in
ignored local storage; never include secrets or clinical records.
