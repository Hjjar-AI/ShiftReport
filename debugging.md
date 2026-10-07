# ShiftReport debugging guide

This guide covers device connection, ADB, Logcat, crashes, freezes, file imports,
network and Telegram failures, file-only joining, encryption, structured tasks, appearance, background synchronization, storage, and safe evidence
collection. Commands assume the Android application ID is
`com.hos.rushdpatients`.

Current implementation and verification limitations are recorded in [currentState.md](currentState.md). Follow [AGENTS.md](AGENTS.md): these diagnostic examples do not authorize an agent to run builds, compilation, packaging, test suites, migrations, or destructive device commands. Small source-inspection/debugging scripts are allowed. Use an already installed, authorized development app for device captures.

> Clinical privacy: logs, screenshots, UI dumps, bug reports, and app databases can
> contain patient or staff information. Keep captures local, redact them before
> sharing, and never commit them. Never paste a bot token, PIN, signing secret,
> Telegram ID, patient name, or clinical note into a public issue.

## Table of contents

- [Fastest workflow](#fastest-workflow)
- [Current formats and import behavior](#current-formats-and-import-behavior)
- [Project joining, encryption, and task diagnostics](#project-joining-encryption-and-task-diagnostics)
- [Appearance and card behavior](#appearance-and-card-behavior)
- [ADB setup](#adb-setup)
- [Connect and identify a device](#connect-and-identify-a-device)
- [Capture a focused Logcat session](#capture-a-focused-logcat-session)
- [Debug the doctors CSV import](#debug-the-doctors-csv-import)
- [Capture an error that only appears in the UI](#capture-an-error-that-only-appears-in-the-ui)
- [Crash and ANR investigation](#crash-and-anr-investigation)
- [App state and lifecycle](#app-state-and-lifecycle)
- [File picker and storage access](#file-picker-and-storage-access)
- [Network and Telegram diagnostics](#network-and-telegram-diagnostics)
- [Background work and synchronization](#background-work-and-synchronization)
- [Database and app storage inspection](#database-and-app-storage-inspection)
- [Performance and memory](#performance-and-memory)
- [Screenshots, recordings, and bug reports](#screenshots-recordings-and-bug-reports)
- [Common ADB problems](#common-adb-problems)
- [Commands to avoid](#commands-to-avoid)
- [Evidence checklist](#evidence-checklist)
- [Issue report template](#issue-report-template)

## Fastest workflow

For a reported doctors CSV failure, connect the phone, open the app, and run this
from the project root:

```bash
mkdir -p local-debug
adb devices -l
adb logcat -c
adb shell am force-stop com.hos.rushdpatients
adb shell am start -n com.hos.rushdpatients/.MainActivity
adb logcat -v threadtime > local-debug/doctors-import-logcat.txt
```

Leave the last command running, reproduce the import once, wait until the error is
visible, and stop capture with `Ctrl+C`. In another terminal, capture the visible
screen and accessibility/UI hierarchy while the snackbar is still displayed:

```bash
adb exec-out screencap -p > local-debug/doctors-import-error.png
adb shell uiautomator dump /sdcard/shiftreport-window.xml
adb pull /sdcard/shiftreport-window.xml local-debug/shiftreport-window.xml
adb shell rm /sdcard/shiftreport-window.xml
```

Then extract likely failures without printing the entire log:

```bash
rg -n -i "Rushd|AndroidRuntime|FATAL EXCEPTION|ANR|SecurityException|IOException|CSV|import|document|telegram|timeout|denied" local-debug/doctors-import-logcat.txt
```

`local-debug/` should remain local. Before committing anything, verify it is ignored:

```bash
git check-ignore -v local-debug/doctors-import-logcat.txt
```

If it is not ignored, do not add or commit the capture.

## Current formats and import behavior

| Data | Current representation |
|---|---|
| Local clinical database | SQLCipher-encrypted Room, schema 5; tasks stored in the patient row |
| Patient snapshots/backups | Patient CSV schema 7, including `tasksJson`; encrypted backups wrap this content |
| Doctor spreadsheet import/export | Doctor-registry CSV schema 1, exact 16-column header shown below |
| Project joining | Password-protected `.srjoin.json` file containing connection settings, topics, and any shared data key |
| Encrypted Telegram documents | Generic `project-data.srdata` filename; the app decrypts downloaded records before parsing |
| Encrypted Telegram text | `SRMSG1:` envelope used for application-published text/pinned state |
| PDF reports | Readable documents by design, including in encrypted projects |

An earlier local doctor-CSV inspection was a historical sample, not proof that a currently selected device file is valid. Do not assume its row count or contents remain unchanged. Use an untouched export from the current app as a format reference, and inspect only file facts needed for the reported failure.

Doctor import validates the full proposed merge and rechecks authorization and the reviewed registry at commit. A failed preparation/confirmation does not establish that any rows were saved. If the UI explicitly reports that import succeeded locally but Telegram synchronization failed, the local import committed; investigate synchronization rather than repeating the import.

## Project joining, encryption, and task diagnostics

### File-only joining and export

- Joining requires the administrator's protected file and its passphrase. Connection values and topic IDs are imported automatically; there is no manual joining form.
- New exports use `.srjoin.json` / `application/json`. Its visible JSON contains only a format label and encrypted payload, never readable connection credentials. Import also recognizes existing binary join files.
- Export stages ciphertext in private non-backed-up storage before the picker and restores its random file ID through `SavedStateHandle`. Saved bytes are read back before success; cancelled/failed exports clear the staged payload and attempt to remove failed destinations. If the session expired, sign in as an administrator and create a new export. Android providers/process recreation still need device validation; source checks are not a build.
- If a file is still empty, use the error shown in Settings and retry saving to local Downloads. An empty file is not a valid join file; never paste credentials into it manually.
- A wrong passphrase or damaged join file should display an import error. Keep the passphrase, bot token, and project key out of logs, screenshots, and issue reports.

### Optional Telegram encryption

- Record whether encryption was selected when the project was created and whether the joining device used that project's protected file. Existing projects without a key retain their original storage mode.
- Ciphertext on Telegram is expected in encrypted projects. Text reports are protected documents; PDFs and their captions remain readable, and Telegram metadata remains visible.
- Wrong/missing data keys, altered ciphertext, or plaintext clinical records in an encrypted project cause explicit errors before parsing/import. Recover the correct project file; generating a new random key will not decrypt existing records.
- Bot-token replacement and data-key replacement are separate operations. Administrators revoke/regenerate the same bot token through BotFather, apply it in Settings → Security and privacy → Reconnect project, and export a fresh join file. Other devices reconnect by that file in Settings or login. The app verifies candidates before saving and preserves the local database/key. Different-project/key/topic files are deliberately rejected. A failed probe leaves the live token unchanged; confirm network access and bot admin/pinning permissions.
- Plain local previews and explicit local exports retain their existing formats. Never print decrypted records or key material to demonstrate that decryption worked.

### Structured tasks and counts

- Tasks are edited in **Patient → Edit → مهام المريض** and commit when the patient is saved. New tasks start unassigned and can be saved without an owner; assigning a doctor is optional. Closing/canceling the form does not publish draft task changes.
- Collapsed cards show that patient's nonzero pending and overdue counts; zero-count chips are omitted. Dashboard metrics are ward totals. Overdue is included in pending, so the counts must not be added together.
- Only unfinished tasks with a reached deadline are overdue; tasks without a deadline are not overdue. Counts refresh through the existing minute timer. A shift-end deadline uses 08:30 local time on the following calendar day.
- Completion actor/time is assigned by the repository at save. Reopening clears completion metadata. No user acknowledgment is required.
- My patients includes patients with unfinished tasks assigned to the signed-in doctor. Dashboard filters cover pending, overdue, and unassigned tasks.
- Rollover carries unfinished tasks with their original deadlines; completed tasks remain in the source shift. Reassignment clears task ownership. Copying a patient starts with no tasks.
- Task records travel in snapshots/backups and appear in text/PDF reports. A stale patient save or conflicting task-list publication requires the existing explicit review. Narrative follow-up lines and checkbox text are never counted as structured tasks.

### Important observability limitation

`DoctorsViewModel` currently catches CSV read/import exceptions and places the error
message in a snackbar. It does not currently send that exception to the app's
`Rushd` Logcat tag. Therefore a clean Logcat does **not** prove the import succeeded.
Capture the snackbar with a screenshot or UI dump. Logcat is still useful for
uncaught crashes, document-provider errors, permission failures, Room failures, and
network/system messages.

## Appearance and card behavior

- Settings → App interface separates System/Light/Dark appearance from the accent palette. Teal is the default, with distinct green/blue/gold/purple alternatives applied across controls and headings. A saved accent does not force light/dark mode; wallpaper colors are disabled by default. Inspect `AppAppearance`, `AppThemePreset`, and `ThemeViewModel` when diagnosing preferences.
- Badge editing and cards share `patientBadgeColors`: high is red, medium is amber, and low/unclassified badges are neutral. Diagnosis categories are neutral, not error states.
- Collapsed cards keep admission/identity, clinician labels, a two-line diagnosis summary, warning/unclassified badges, and nonzero task counts. Low-level badges show an additional-badge count. Expand for full text, all badges, completed tasks, and last-edit information; collapsed summaries do not remove stored data.
- Individual expansion/collapse uses 160/130ms transitions. Bulk expansion is deliberately immediate. Report readiness remains on the FAB; Activity has its own primary tab.
- General and Advanced patient editing both expose labs using one draft. Report preview has a top roster selector even with no doctors selected; there is no fixed maximum, and at least one active doctor is required. The latest form/roster/accent changes have not been rebuilt.
- PDF orientation, paper, colors, dark output, and per-supervisor output are under Settings → Report → Advanced PDF options. These controls are hidden until expanded, not removed.
- The missing Material opt-ins in `PatientTaskEditor.kt` are fixed; the owner supplied a successful debug build on 2026-10-07. The remaining deprecated-API note names generated `RushdApplication_MembersInjector.java`; do not edit generated output to suppress it. Build success and static accent contrast checks do not establish rendered readability or smoothness.

## ADB setup

### On the Android device

1. Open **Settings → About phone**.
2. Tap **Build number** seven times to enable Developer options.
3. Open **Developer options** and enable **USB debugging**.
4. Connect the device with a data-capable USB cable.
5. Unlock the device and accept the computer's RSA debugging prompt.

Use a dedicated development device when possible. Disable USB debugging after the
investigation on a clinical device.

### On Linux

Check that ADB is installed:

```bash
adb version
```

If the phone is visible to USB but ADB says `no permissions`, install the platform's
Android udev rules, ensure the current user belongs to the appropriate device group,
then reconnect and reauthorize the phone. Useful checks are:

```bash
lsusb
id
adb kill-server
adb start-server
adb devices -l
```

Do not run the app or the entire development workflow as root merely to bypass a udev
configuration problem.

### On Windows

Install current Android platform-tools and, if necessary, the device manufacturer's
USB driver. Run commands from PowerShell or Command Prompt in the platform-tools
directory. Replace shell utilities such as `rg` with an available equivalent.

## Connect and identify a device

List devices:

```bash
adb devices -l
```

Expected states:

- `device`: connected and authorized;
- `unauthorized`: unlock the phone and accept the RSA prompt;
- `offline`: reconnect, toggle USB debugging, and restart the ADB server;
- no row: check cable, USB mode, drivers/udev rules, and `lsusb`.

With multiple devices, select one explicitly:

```bash
adb -s DEVICE_SERIAL shell getprop ro.product.model
adb -s DEVICE_SERIAL logcat -v threadtime
```

Record basic device facts:

```bash
adb shell getprop ro.product.manufacturer
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell wm size
adb shell wm density
```

Confirm the installed package and version:

```bash
adb shell pm path com.hos.rushdpatients
adb shell dumpsys package com.hos.rushdpatients | rg "versionName|versionCode|firstInstallTime|lastUpdateTime"
```

If `pm path` returns nothing, the package is not installed for the active Android
user/profile.

## Capture a focused Logcat session

### Full reproduction capture

This is the safest first capture because filtering too early can hide the cause:

```bash
mkdir -p local-debug
adb logcat -c
adb logcat -v threadtime > local-debug/shiftreport-logcat.txt
```

Reproduce exactly one failure, then stop with `Ctrl+C`.

### Only the running app process

Start the app first, then obtain its process ID:

```bash
adb shell am start -n com.hos.rushdpatients/.MainActivity
adb shell pidof -s com.hos.rushdpatients
```

If a PID is returned:

```bash
APP_PID=$(adb shell pidof -s com.hos.rushdpatients | tr -d '\r')
adb logcat --pid="$APP_PID" -v threadtime
```

The PID changes after a crash, force-stop, or process restart. A PID-filtered capture
will stop following the correct process after such a restart; use a full capture for
crash/relaunch investigations.

### Useful tag and severity filters

The app's explicit logging tag is `Rushd`:

```bash
adb logcat -v color Rushd:V AndroidRuntime:E ActivityManager:I '*:S'
```

Show warnings and errors from all tags:

```bash
adb logcat -v threadtime '*:W'
```

Search a saved log:

```bash
rg -n -i "FATAL EXCEPTION|AndroidRuntime|ANR|SecurityException|SQLite|Room|IOException|timeout|denied|Rushd" local-debug/shiftreport-logcat.txt
```

Do not filter only for the word `error`; many useful Java/Kotlin stack traces do not
contain that exact word.

## Debug the doctors CSV import

### 1. Confirm the exact file selected

Android's picker may expose a downloaded copy rather than the project file. Verify
the selected filename and modified time in the picker. If uncertain, export a fresh
doctor registry from the same installed app and try importing that untouched export.
An untouched app export is the best format-control sample.

The required header is:

```text
schema_version,id,first_name,last_name,full_name,gender,clinical_role,supervisor_group_chat_id,telegram_id,telegram_username,custom_title,rank,is_permanent_admin,extra_options,updated_at,deleted_at
```

The simple legacy header `name,id,role` is not accepted by the current importer.

### 2. Capture the preparation/read phase

The first phase reads and validates the file, then displays the import-review dialog.
If that dialog never appears, capture the snackbar immediately. Likely messages
include:

- missing columns;
- unsupported schema version;
- invalid name, title, gender, role, Telegram ID, admin rank, or timestamp;
- duplicate IDs, names, Telegram IDs, or admin ranks;
- file too large, empty, inaccessible, or malformed quoting.

Run:

```bash
adb logcat -c
adb logcat -v threadtime > local-debug/doctors-read-phase.txt
```

Open **Doctors → Import CSV**, select the file once, capture the visible snackbar,
then stop Logcat.

### 3. Capture the confirmation/merge phase

If the review dialog appears, parsing succeeded. Start a new capture before pressing
**Import and synchronize**:

```bash
adb logcat -c
adb logcat -v threadtime > local-debug/doctors-merge-phase.txt
```

Failure here usually means administrator authorization, an ambiguous match with the
local registry, a final duplicate after merging, a database write error, or Telegram
upload trouble.

### 4. Interpret the UI result

- Review dialog never appears: read or format validation failed.
- Review dialog appears but confirmation fails: merge, authorization, database, or
  synchronization stage failed.
- Message says imported locally but Telegram sync failed: the local rows were saved;
  investigate connectivity/project configuration instead of repeatedly importing.
- Doctor count/list changes despite an error about Telegram: local import worked.

### 5. Validate a CSV on the development computer

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

### 6. Spreadsheet pitfalls

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

## Capture an error that only appears in the UI

Take a screenshot while the error is visible:

```bash
mkdir -p local-debug
adb exec-out screencap -p > local-debug/error-screen.png
```

Dump the UI hierarchy:

```bash
adb shell uiautomator dump /sdcard/shiftreport-window.xml
adb pull /sdcard/shiftreport-window.xml local-debug/shiftreport-window.xml
adb shell rm /sdcard/shiftreport-window.xml
```

Search locally for Arabic error text or snackbar nodes:

```bash
rg -n "تعذر|فشل|غير صالح|مكرر|مطلوب|text=" local-debug/shiftreport-window.xml
```

Compose semantics are not guaranteed to expose every transient snackbar to
UIAutomator. If the text is absent, use a screenshot or screen recording.

## Crash and ANR investigation

### Reproduce a crash

```bash
adb logcat -c
adb shell am force-stop com.hos.rushdpatients
adb shell am start -n com.hos.rushdpatients/.MainActivity
adb logcat -v threadtime AndroidRuntime:E ActivityManager:I '*:S'
```

Look for:

- `FATAL EXCEPTION`;
- the first `Caused by:` line;
- the deepest application frame beginning with `com.hos.rushdpatients`;
- the operation immediately before the exception.

### Check for an ANR or frozen activity

```bash
adb shell dumpsys activity processes | rg -n -i "com.hos.rushdpatients|ANR|not responding"
adb shell dumpsys activity top | rg -n -i "com.hos.rushdpatients|mResumedActivity|mFocusedApp"
adb shell dumpsys window | rg -n -i "mCurrentFocus|mFocusedApp|com.hos.rushdpatients"
```

On a debuggable build, request Java stacks from the running process:

```bash
APP_PID=$(adb shell pidof -s com.hos.rushdpatients | tr -d '\r')
adb shell kill -3 "$APP_PID"
adb logcat -d -v threadtime > local-debug/thread-dump-logcat.txt
```

`kill -3` requests a thread dump; it does not terminate a normal Android runtime
process. Confirm the PID is non-empty and belongs to this package before using it.

## App state and lifecycle

Start the launcher activity:

```bash
adb shell am start -W -n com.hos.rushdpatients/.MainActivity
```

Force-stop and reopen without deleting data:

```bash
adb shell am force-stop com.hos.rushdpatients
adb shell am start -n com.hos.rushdpatients/.MainActivity
```

Check the current activity and process:

```bash
adb shell pidof -s com.hos.rushdpatients
adb shell dumpsys activity activities | rg -n "com.hos.rushdpatients|mResumedActivity"
```

Inspect package configuration:

```bash
adb shell dumpsys package com.hos.rushdpatients > local-debug/package-state.txt
```

Inspect recent process-exit reasons on supported Android versions:

```bash
adb shell dumpsys activity exit-info com.hos.rushdpatients
```

## File picker and storage access

Doctor export prepares UTF-8/BOM bytes before `CreateDocument`, then verifies the saved contents by reading them back. If the export session is lost after a process restart, the app reports a retry instruction; an empty or mismatched file must not be reported as successful. Picker cancellation clears the pending snapshot. These changes passed source checks and the authorized 2026-10-07 debug build; document-provider/device behavior remains unverified.

The doctors importer uses Android's Storage Access Framework through `OpenDocument`.
It reads a content URI supplied by the chosen document provider; it does not require
broad storage permission.

Useful provider-related searches during reproduction:

```bash
adb logcat -v threadtime | rg -i "DocumentsUI|DocumentsProvider|ContentResolver|FileNotFoundException|SecurityException|permission denial|com.hos.rushdpatients"
```

Inspect currently granted URI permissions when supported by the device:

```bash
adb shell dumpsys activity uri-permissions | rg -n -C 3 "com.hos.rushdpatients"
```

If a cloud document provider is involved, first download the CSV locally on the
phone and select the local copy. This separates provider/network trouble from CSV
parsing trouble.

Do not assume that a file path visible on the development computer exists on the
phone. Android imports the file selected on the device.

## Network and Telegram diagnostics

Confirm Android sees a network:

```bash
adb shell dumpsys connectivity | rg -n -i "NetworkAgentInfo|VALIDATED|INTERNET|WIFI|MOBILE"
adb shell settings get global airplane_mode_on
```

Check DNS/network reachability from the device where shell utilities allow it:

```bash
adb shell ping -c 3 api.telegram.org
```

Some devices disable `ping`; failure of this command alone does not prove the app
has no internet access.

Capture relevant network failures:

```bash
adb logcat -v threadtime | rg -i "UnknownHostException|ConnectException|SocketTimeoutException|SSLHandshakeException|Telegram|HTTP 4|HTTP 5|Rushd"
```

Interpret common Telegram conditions:

- `401`: bot token is invalid or revoked;
- `403`: bot lacks access, was blocked, or lacks the required chat/topic rights;
- `400 chat not found`: configured chat ID is wrong or inaccessible;
- `400 message thread not found`: configured topic ID is wrong;
- `429`: rate limited; respect the retry delay;
- timeout/DNS/SSL errors: device connectivity, clock, DNS, proxy, firewall, or TLS
  interception problem.

Never print or share the bot-token URL. Search logs for exception classes and status
codes, not credentials.

## Background work and synchronization

Inspect scheduled jobs:

```bash
adb shell dumpsys jobscheduler | rg -n -C 5 "com.hos.rushdpatients"
```

Inspect WorkManager-related Logcat output:

```bash
adb logcat -v threadtime | rg -i "WorkManager|WM-|AutoSyncWorker|com.hos.rushdpatients|Rushd"
```

Check battery restrictions that may delay background work:

```bash
adb shell dumpsys deviceidle | rg -n -i "mState|mLightState|whitelist"
adb shell dumpsys battery | rg -n "level|status|plugged"
```

Do not force background synchronization against production Telegram chats merely as
a diagnostic unless sending/uploading is explicitly intended.

## Database and app storage inspection

Only a debuggable build normally permits `run-as`:

```bash
adb shell run-as com.hos.rushdpatients pwd
adb shell run-as com.hos.rushdpatients ls -la
adb shell run-as com.hos.rushdpatients find databases -maxdepth 1 -type f -print
```

These commands list paths only. Do not copy or commit the database: it may contain
clinical data, local authentication material, and synchronization state.

Check app storage size without reading its contents:

```bash
adb shell dumpsys diskstats
adb shell dumpsys package com.hos.rushdpatients | rg -n -i "dataDir|credentialProtectedDataDir|deviceProtectedDataDir"
```

If database inspection becomes necessary, use a disposable test dataset and obtain
explicit authorization first. Do not edit Room files directly.

## Performance and memory

Show memory use:

```bash
adb shell dumpsys meminfo com.hos.rushdpatients
```

Show CPU use for a short interactive observation:

```bash
adb shell top -b -n 1 | rg "com.hos.rushdpatients|PID"
```

Measure activity launch time:

```bash
adb shell am force-stop com.hos.rushdpatients
adb shell am start -W -n com.hos.rushdpatients/.MainActivity
```

Inspect rendering summaries where supported:

```bash
adb shell dumpsys gfxinfo com.hos.rushdpatients
```

Reset only graphics timing counters, not app data:

```bash
adb shell dumpsys gfxinfo com.hos.rushdpatients reset
```

## Screenshots, recordings, and bug reports

Screenshot:

```bash
adb exec-out screencap -p > local-debug/shiftreport-screen.png
```

Short screen recording:

```bash
adb shell screenrecord --time-limit 30 /sdcard/shiftreport-debug.mp4
adb pull /sdcard/shiftreport-debug.mp4 local-debug/shiftreport-debug.mp4
adb shell rm /sdcard/shiftreport-debug.mp4
```

Full Android bug report:

```bash
adb bugreport local-debug/shiftreport-bugreport.zip
```

A bug report is broad and may contain device identifiers, installed-package metadata,
notifications, logs, network information, and clinical UI remnants. Use it only when
narrow Logcat/UI captures are insufficient, store it securely, and never commit it.

## Common ADB problems

### `unauthorized`

Unlock the phone, accept the RSA prompt, then run:

```bash
adb reconnect
adb devices -l
```

If no prompt appears, revoke USB debugging authorizations in Developer options,
reconnect, and authorize again.

### `offline`

```bash
adb reconnect offline
adb kill-server
adb start-server
adb devices -l
```

Also reconnect the cable and switch USB mode to file transfer if the device requires
it.

### `no permissions` on Linux

This is normally a host udev/group issue. Confirm the USB vendor with `lsusb`, install
appropriate Android udev rules, reconnect, and log out/in after group changes.

### `ADB server didn't ACK` or cannot bind port 5037

Check for another ADB server, container/sandbox restriction, or a process occupying
the port:

```bash
ps aux | rg "[a]db"
ss -ltnp | rg ":5037"
adb kill-server
adb start-server
```

If commands are being run inside a restricted container or coding sandbox, run ADB
from the normal host terminal instead. A sandbox may prevent ADB from opening its
local server socket even when the phone and project are configured correctly.

### More than one device/emulator

Use the serial shown by `adb devices -l`:

```bash
adb -s DEVICE_SERIAL shell pidof -s com.hos.rushdpatients
```

### App PID is empty

The app is not running in that profile, has crashed, or was force-stopped. Launch it
and query again:

```bash
adb shell am start -n com.hos.rushdpatients/.MainActivity
adb shell pidof -s com.hos.rushdpatients
```

## Commands to avoid

Do not use these during ordinary diagnosis:

```text
adb shell pm clear com.hos.rushdpatients
adb uninstall com.hos.rushdpatients
adb shell rm -rf ...
```

`pm clear` deletes the app's databases, preferences, local credentials, drafts, and
synchronization state. Uninstalling can also remove app data. Neither is necessary
for capturing CSV import evidence. If clean-state testing is explicitly needed, use a
separate test device/profile and preserve authorized backups first.

Also avoid changing system time, certificate stores, battery policy, network policy,
or Telegram configuration while collecting the initial reproduction; changing many
variables makes the result difficult to interpret.

## Evidence checklist

For a useful and privacy-safe report, collect:

- exact screen and action where the problem occurs;
- whether the import-review dialog appeared;
- exact snackbar/error wording, redacted if it contains personal data;
- whether the doctor list changed afterward;
- app `versionName` and `versionCode`;
- Android version, manufacturer, and model;
- whether the selected file was a fresh app export or spreadsheet-edited copy;
- file byte size, encoding, header match, and row count—not its staff records;
- focused Logcat covering one reproduction;
- screenshot or UI dump when the failure is UI-only;
- whether internet was available and whether other Telegram operations worked;
- whether the failure reproduces after force-stop/reopen without clearing data;
- for joining/encryption failures, which stage failed and whether encryption is enabled, without including keys or passphrases;
- for task-count issues, whether the patient was saved, which list/card/dashboard was shown, and the task status/deadline using a disposable sample.

## Issue report template

```text
Title:

App version/version code:
Android version and device:
Build type (debug/release, if known):

Operation:
Doctors CSV read phase / confirmation-merge phase / Telegram sync phase

Steps:
1.
2.
3.

Expected result:

Actual result:

Did the review dialog appear? yes/no
Did the local doctor list change? yes/no/unknown
Exact visible error (redacted):

CSV facts:
- Fresh app export or edited file:
- Encoding:
- Header exact: yes/no
- Row count:
- Duplicate-count checks:

Connectivity:
- Internet validated: yes/no
- Other Telegram actions work: yes/no/not tested

Attachments (kept private and redacted):
- focused Logcat
- screenshot or UI dump
- package/device version output
```
