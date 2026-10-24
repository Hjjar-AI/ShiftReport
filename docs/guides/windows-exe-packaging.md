# Package the desktop client as a Windows EXE

This guide packages `desktop/desktop_gui.py` and its dependencies with PyInstaller.
Run these commands **on Windows**, in **Command Prompt (`cmd.exe`)**. The `^` line
continuations below are for Command Prompt, not PowerShell.

The recommended result is a folder containing `ShiftReport.exe` and its support
files. Recipients do not need Python installed. This is a portable application
bundle; it does not create an installer, desktop shortcut or signed release.

Related: [desktop usage](../../desktop/README.md),
[desktop troubleshooting](desktop-troubleshooting.md),
[current verification limits](../status/current-state.md#verification-and-limits).

## 1. Choose the Windows and Python target

The repository's Windows dependency set is
[`requirements-win7.txt`](../../desktop/requirements-win7.txt), targeting **CPython
3.8.10**, including Tcl/Tk and pip. This guide uses **PyInstaller 5.13.2** in a
separate packaging environment. It does not change application dependencies.

Check your installed interpreters:

```bat
py -0p
py -3.8 -c "import sys, struct, tkinter; print(sys.version); print(str(struct.calcsize('P') * 8) + '-bit'); print('Tk', tkinter.TkVersion); assert sys.version_info[:3] == (3, 8, 10), 'Use CPython 3.8.10 for this dependency set'"
```

If `py -3.8` is unavailable, install Python 3.8.10 with the Python launcher,
Tcl/Tk, pip and venv components. Another installed Python version can remain.
A newer interpreter requires a separately checked dependency/tooling combination;
do not point this guide's pinned Windows requirements at it by assumption.

Use a Python installation matching the intended architecture. A 64-bit bundle
cannot run on 32-bit Windows. Prepare separate environments/output folders when
producing different architectures, and inspect the printed bitness each time.

**Windows 7 is a compatibility target, not a verified EXE result.** PyInstaller
5.13.2's official requirements list Windows 8 or newer. Packaging successfully
on newer Windows does not establish that the result runs on Windows 7. Check
startup, Arabic editing, encryption/storage and PDF output on every intended
Windows/architecture combination. See the
[version-specific requirements](https://pyinstaller.org/en/v5.13.2/requirements.html).

## 2. Prepare the application source

Copy the current `desktop` source folder to Windows. For example:

```text
C:\ShiftReport\desktop\
    desktop_gui.py
    desktop_*.py
    requirements-win7.txt
    fonts\
        Tajawal-Regular.ttf
        Tajawal-Bold.ttf
        OFL.txt
```

Keep **all** the desktop Python modules together; `desktop_gui.py` imports them.
Use the latest complete source snapshot, including newly added modules.
The `desktop_*.py` entry above describes the files; it is not a file to create.

Open Command Prompt and enter your actual source directory:

```bat
cd /d "C:\ShiftReport\desktop"
dir desktop_gui.py
dir fonts
```

Keep `.env`, protected join files, signing material and clinical workspace/export
files out of the distributable. Do not use `--add-data ".;."` or collect the whole
source directory: that could include private files. Only the fonts directory is
explicitly added by the commands below.

## 3. Create an isolated packaging environment

Keep the environment and generated artifacts outside the source checkout:

```bat
set "SR_BUILD_ROOT=%LOCALAPPDATA%\ShiftReportBuild"
py -3.8 -m venv "%SR_BUILD_ROOT%\py38"
set "SR_BUILD_PY=%SR_BUILD_ROOT%\py38\Scripts\python.exe"
"%SR_BUILD_PY%" -m pip install --only-binary=:all: -r requirements-win7.txt
"%SR_BUILD_PY%" -m pip install "pyinstaller==5.13.2"
"%SR_BUILD_PY%" -m pip check
"%SR_BUILD_PY%" -m PyInstaller --version
```

Stop if a command fails and resolve that error before continuing. Activation is
unnecessary because each command names the environment's Python explicitly.
`Setup_Windows.cmd` installs into the selected Python installation; the isolated
commands above are sufficient for packaging and do not require running that script.

These variables exist only in the current Command Prompt. In a new prompt, set
`SR_BUILD_ROOT` and `SR_BUILD_PY` again and return to the source directory.

Check imports and launch the source in demo mode:

```bat
"%SR_BUILD_PY%" -c "import tkinter, customtkinter, certifi, tzdata, cryptography, reportlab, arabic_reshaper, bidi; print('Required imports OK')"
"%SR_BUILD_PY%" desktop_gui.py --demo
```

Close the demo before packaging. Demo data stays in memory and does not connect
to Telegram. Explicit PDF export writes a labelled dummy report if you choose it.

## 4. Package the recommended folder bundle

Run this from the `desktop` source directory:

```bat
"%SR_BUILD_PY%" -m PyInstaller ^
  --name ShiftReport ^
  --onedir ^
  --windowed ^
  --clean ^
  --noupx ^
  --distpath "%SR_BUILD_ROOT%\dist" ^
  --workpath "%SR_BUILD_ROOT%\work" ^
  --specpath "%SR_BUILD_ROOT%\spec" ^
  --collect-all customtkinter ^
  --collect-all certifi ^
  --collect-all tzdata ^
  --add-data "fonts;fonts" ^
  desktop_gui.py
```

Paste the whole block. Each `^` must be the final character on its line, with no
spaces after it. If output already exists, PyInstaller may ask before replacing
it; preserve any earlier bundle you still need first.

The options collect CustomTkinter resources, certificate/timezone data and the
project's Arabic fonts. `--windowed` hides the console. The EXE is:

```text
%LOCALAPPDATA%\ShiftReportBuild\dist\ShiftReport\ShiftReport.exe
```

The generated `.spec` and working files are kept under `spec` and `work`.
The `.spec` records packaging choices; changing flags on a command that builds
an existing spec does not necessarily override those choices. The commands in
this guide consistently build from `desktop_gui.py`.
See [PyInstaller options](https://www.pyinstaller.org/en/v5.13.2/usage.html).

CustomTkinter's own [packaging guidance](https://customtkinter.tomschimansky.com/documentation/packaging/)
recommends `--onedir` and explicitly including its resource directory. This recipe
uses `--collect-all customtkinter` to collect that package's resources. The result
still requires checking on Windows.

## 5. Check the packaged application

Start with fabricated demo data:

```bat
"%SR_BUILD_ROOT%\dist\ShiftReport\ShiftReport.exe" --demo
```

Check these application paths:

- Arabic names/text, right-to-left fields, search and readable wrapping.
- Patient editing, tasks, save/cancel, card expansion and the full patient record.
- Data/settings windows and common dialog footers.
- Local demo PDF export in table, card and names-only assignment layouts where
  available; check Arabic glyphs, the creation date, continuation pages and DEMO
  labels. Open the result in the target computer's PDF reader.
- Startup from a shortcut or another working directory.

Copy the entire output folder to a clean Windows account/computer without Python,
then repeat the relevant checks. A successful launch on the packaging computer
can miss accidental dependencies on its local installation.

For project onboarding, close the demo and launch normally:

```bat
"%SR_BUILD_ROOT%\dist\ShiftReport\ShiftReport.exe"
```

Without an adjacent `.env`, the application prompts for a protected
`.srjoin.json` file and its password. Supply these separately through the existing
join workflow. Validate real delivery only against an explicitly chosen project
and destination, retaining preview and final confirmation. A local/demo check is
not evidence that live Telegram publication or delivery works.

## 6. Share the folder bundle

Distribute **the whole `dist\ShiftReport` folder**, including its support files
and the bundled font license. Copying only `ShiftReport.exe` from a folder bundle
will leave required files behind. Recipients extract the folder before running it.

An optional PowerShell ZIP command, invoked from the same Command Prompt, is:

```bat
powershell -NoProfile -Command "Compress-Archive -Path ($env:SR_BUILD_ROOT + '\dist\ShiftReport') -DestinationPath ($env:SR_BUILD_ROOT + '\ShiftReport-Windows.zip')"
```

Use a new ZIP filename if it already exists. Share that ZIP rather than the build
source/environment. A protected join file and its password are distributed
separately; they are not application resources.

Packaging does not sign the EXE. If Windows or endpoint protection blocks it,
inspect the exact message and follow the organization's distribution policy.
Do not disable protection or assume a packaging success establishes trust.

## 7. Optional single-file experiment

This single-file path is experimental. The main recommendation remains the folder
bundle. The earlier short chat answer
suggested replacing `--onedir` with `--onefile`; this is a PyInstaller capability,
but **not a verified CustomTkinter packaging result for this project**.
CustomTkinter's published guidance recommends folder output.

If you want to evaluate a single EXE, use a distinct output name:

```bat
"%SR_BUILD_PY%" -m PyInstaller ^
  --name ShiftReportSingle ^
  --onefile ^
  --windowed ^
  --clean ^
  --noupx ^
  --distpath "%SR_BUILD_ROOT%\dist" ^
  --workpath "%SR_BUILD_ROOT%\work" ^
  --specpath "%SR_BUILD_ROOT%\spec" ^
  --collect-all customtkinter ^
  --collect-all certifi ^
  --collect-all tzdata ^
  --add-data "fonts;fonts" ^
  desktop_gui.py
```

The candidate is `%SR_BUILD_ROOT%\dist\ShiftReportSingle.exe`. Repeat the complete
packaged-app checks. Retain the working folder bundle if fonts/themes or another
runtime resource fails in single-file mode.

Single-file output extracts runtime resources to a temporary directory during
startup, which can make startup slower. Run as an ordinary user; this app's local
workspace does not require elevating the EXE. See
[PyInstaller's runtime model](https://pyinstaller.org/en/v5.13.2/operating-mode.html).

## 8. Understand configuration, fonts and patient storage

The source distinguishes two locations:

| Item | Location/behavior |
|---|---|
| Optional `.env` | Beside the EXE; not embedded |
| Relative `JOIN_FILE`, `DATA_DIR` or explicit `PDF_FONT` in `.env` | Resolved relative to the EXE directory |
| Default Arabic PDF fonts | Bundled `fonts/Tajawal-Regular.ttf` and `Tajawal-Bold.ttf` |
| Default Windows clinical workspace | `%LOCALAPPDATA%\ShiftReportDesktop` |
| Generated packaging files | `%LOCALAPPDATA%\ShiftReportBuild` in this guide |

Leave `PDF_FONT` unset to use the bundled Tajawal fonts. An explicit relative
`PDF_FONT` override expects a matching external file beside the EXE path; in
single-file mode it does not refer to the temporary bundled-resource directory.
For implementation context see
[resource path behavior](https://www.pyinstaller.org/en/v5.13.2/runtime-information.html)
and [`desktop_platform.py`](../../desktop/desktop_platform.py).

A portable application bundle is not a portable clinical database. Existing
Windows workspaces can depend on their original user profile and DPAPI-protected
key. Preserve workspace/key files, and use the app's reviewed encrypted backup
workflow for deliberate transfers. Do not copy patient records into `dist` or
remove workspace files to troubleshoot startup. Packaging does not alter Telegram
credentials, revoke a bot token or change the project data key.

## 9. Update a previously distributed bundle

1. Close all running copies of ShiftReport.
2. Keep a reviewed encrypted backup of clinical work through the application.
3. Copy the latest complete desktop source to the packaging computer and rerun
   the dependency check and packaging command using the chosen target environment.
4. Repeat the demo and target-machine checks. Record the source snapshot,
   Python version/bitness, Windows version and packaging/dependency versions.
5. Replace the complete application folder. Keep private external configuration
   and protected join material separate from the replacement.
6. Preserve `%LOCALAPPDATA%\ShiftReportDesktop` and any configured external
   workspace. Replacing application binaries is not a data reset.

To record the environment for your own packaging evidence:

```bat
"%SR_BUILD_PY%" -m pip freeze > "%SR_BUILD_ROOT%\packaging-environment.txt"
"%SR_BUILD_PY%" -c "import sys, struct; print(sys.version); print(struct.calcsize('P') * 8)"
ver
```

Do not add real credentials, patient exports or clinical screenshots to that record.

## 10. Diagnose packaging problems

Use dummy data for diagnostic reproduction. Logs and screenshots from a real
session may contain clinical details; keep them private and redact before sharing.

| Symptom | Next check |
|---|---|
| `py` or `py -3.8` unavailable | Inspect installed Python/launcher components and install the required interpreter. |
| Dependency installation fails or tries building a package | Check Python 3.8.10 and architecture, then read the first pip error. Preserve the supplied requirements instead of changing versions at random. |
| EXE fails after copying | Confirm the entire folder bundle was copied and extracted. |
| Missing theme/font or `customtkinter` data | Confirm `--collect-all customtkinter` was used and rebuild from the Python entry point. |
| Arabic PDFs use a fallback font or have missing glyphs | Verify both Tajawal files and `OFL.txt` were included; remove an unintended `PDF_FONT` override from private runtime settings. |
| Missing timezone data | Confirm `--collect-all tzdata`. |
| HTTPS/certificate error | Confirm `--collect-all certifi`, the computer clock and its network/proxy settings. Preserve certificate verification. |
| “Application already open” | Close the existing normal app instance before opening another. |
| Missing DLL or “not a valid Win32 application” | Check architecture and target Windows compatibility; do not download arbitrary replacement DLLs. |
| Blank/brief startup error with no console | Produce the console diagnostic bundle below and run it with `--demo`. |

For a diagnostic bundle, rerun the folder command from section 4 with
`--name ShiftReportDebug` and **replace `--windowed` with `--console`**, leaving
all resource options and output-path options in place. Then run:

```bat
"%SR_BUILD_ROOT%\dist\ShiftReportDebug\ShiftReportDebug.exe" --demo > "%SR_BUILD_ROOT%\demo-startup.txt" 2>&1
```

PyInstaller also writes warnings to
`%SR_BUILD_ROOT%\work\ShiftReportDebug\warn-ShiftReportDebug.txt`.
Inspect the actual startup exception before treating optional/platform-specific
missing-module warnings as failures. See the official
[diagnostic guide](https://pyinstaller.org/en/v5.13.2/when-things-go-wrong.html).

## Verification status of this guide

Commands are reference procedures, not a verified Windows build. No EXE/installer
or Windows 7/DPAPI/Tk certification is established. Keep target-runtime/package
checks in [Remaining checks](../status/testing-handoff.md); observed results belong
in [Current state](../status/current-state.md#verification-and-limits).
