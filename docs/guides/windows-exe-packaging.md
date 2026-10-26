# Package the desktop client as a Windows EXE

Windows **cmd.exe** recipes (`^` continuation, not PowerShell), entry `desktop_gui.py`. Prefer a complete portable folder bundle; recipients need no Python. No installer/shortcut/signature is produced.

Related: [desktop usage](../../desktop/README.md), [desktop troubleshooting](desktop-troubleshooting.md), [current verification limits](../status/current-state.md#verification-and-limits).

## 1. Choose the Windows and Python target

Use [requirements-win7.txt](../../desktop/requirements-win7.txt), CPython **3.8.10** with Tk/pip/venv/launcher, and separate PyInstaller **5.13.2** packaging environment. Match interpreter/output architecture; x64 cannot run on x86. Windows 7 is unverified: [PyInstaller 5.13.2 requires Windows 8+](https://pyinstaller.org/en/v5.13.2/requirements.html). Check each target OS/architecture; newer Python needs separately validated dependencies.

```bat
py -0p
py -3.8 -c "import sys, struct, tkinter; print(sys.version); print(str(struct.calcsize('P') * 8) + '-bit'); print('Tk', tkinter.TkVersion); assert sys.version_info[:3] == (3, 8, 10), 'Use CPython 3.8.10 for this dependency set'"
```

## 2. Prepare the application source

Copy the complete current `desktop/` source, including every `desktop_*.py` module and `fonts/` with OFL license. Use your actual path below. Exclude `.env`, join files, signing material and clinical data; never collect the entire source directory as resources.

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

```bat
cd /d "C:\ShiftReport\desktop"
dir desktop_gui.py
dir fonts
```

## 3. Create an isolated packaging environment

Run in the source directory; keep environment/output outside it. Commands name the venv Python, so activation and `Setup_Windows.cmd` are unnecessary. Stop on failure. Re-set these variables in each new cmd prompt. Check imports/demo, then close it; demo is memory-only except explicit labelled PDF export.

```bat
set "SR_BUILD_ROOT=%LOCALAPPDATA%\ShiftReportBuild"
py -3.8 -m venv "%SR_BUILD_ROOT%\py38"
set "SR_BUILD_PY=%SR_BUILD_ROOT%\py38\Scripts\python.exe"
"%SR_BUILD_PY%" -m pip install --only-binary=:all: -r requirements-win7.txt
"%SR_BUILD_PY%" -m pip install "pyinstaller==5.13.2"
"%SR_BUILD_PY%" -m pip check
"%SR_BUILD_PY%" -m PyInstaller --version
```

```bat
"%SR_BUILD_PY%" -c "import tkinter, customtkinter, certifi, tzdata, cryptography, reportlab, arabic_reshaper, bidi; print('Required imports OK')"
"%SR_BUILD_PY%" desktop_gui.py --demo
```

## 4. Package the recommended folder bundle

Paste the complete cmd block; `^` must end its line without trailing spaces. Preserve older output before replacement. Collections include CustomTkinter, CA/timezone data and Tajawal. Output: `%LOCALAPPDATA%\ShiftReportBuild\dist\ShiftReport\ShiftReport.exe`; work/spec remain separate. Build from `desktop_gui.py`; existing spec files can override command choices. [Options](https://www.pyinstaller.org/en/v5.13.2/usage.html) · [CustomTkinter folder guidance](https://customtkinter.tomschimansky.com/documentation/packaging/).

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

```text
%LOCALAPPDATA%\ShiftReportBuild\dist\ShiftReport\ShiftReport.exe
```

## 5. Check the packaged application

Run demo on each target OS/architecture and a clean account without Python: Arabic/RTL, editing/Save/Cancel, settings, PDF pagination/DEMO/viewer, shortcuts and other working directories. Normal launch: protected join/password or adjacent configuration. Live sends require designated project/review/confirmation; demo proves neither onboarding nor Telegram.

```bat
"%SR_BUILD_ROOT%\dist\ShiftReport\ShiftReport.exe" --demo
```

```bat
"%SR_BUILD_ROOT%\dist\ShiftReport\ShiftReport.exe"
```

## 6. Share the folder bundle

Share the **whole extracted `dist\ShiftReport` folder**, support files and font license. Optional ZIP command below; choose a fresh filename. Distribute protected join/password separately. Packaging creates no installer, shortcut or signature; inspect protection errors under organizational policy without disabling protection.

```bat
powershell -NoProfile -Command "Compress-Archive -Path ($env:SR_BUILD_ROOT + '\dist\ShiftReport') -DestinationPath ($env:SR_BUILD_ROOT + '\ShiftReport-Windows.zip')"
```

## 7. Optional single-file experiment

**Experimental/unverified for this app**; folder output remains recommended by CustomTkinter. Use the distinct name below, repeat all target checks and retain the working folder bundle. One-file startup extracts resources temporarily and may be slower; run as an ordinary user. [Runtime model](https://pyinstaller.org/en/v5.13.2/operating-mode.html).

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

## 8. Understand configuration, fonts and patient storage

Leave `PDF_FONT` unset for bundled Tajawal; an override is external/EXE-relative, including one-file mode. [Resource behavior](https://www.pyinstaller.org/en/v5.13.2/runtime-information.html) · [Platform adapter](../../desktop/desktop_platform.py). A portable app is not a portable database: retain original DPAPI profile/key/workspace and use reviewed encrypted backups for transfers. Never put records in `dist` or delete storage to repair startup. Packaging changes no credentials, revocation or data key.

| Item | Location/behavior |
|---|---|
| Optional `.env` | Beside the EXE; not embedded |
| Relative `JOIN_FILE`, `DATA_DIR` or explicit `PDF_FONT` in `.env` | Resolved relative to the EXE directory |
| Default Arabic PDF fonts | Bundled `fonts/Tajawal-Regular.ttf` and `Tajawal-Bold.ttf` |
| Default Windows clinical workspace | `%LOCALAPPDATA%\ShiftReportDesktop` |
| Generated packaging files | `%LOCALAPPDATA%\ShiftReportBuild` in this guide |

## 9. Update a previously distributed bundle

Update: close→encrypted backup→package current source→target checks→replace whole app folder. Preserve private config/workspace. Record source/Python/bitness/Windows/dependencies, no secrets:

```bat
"%SR_BUILD_PY%" -m pip freeze > "%SR_BUILD_ROOT%\packaging-environment.txt"
"%SR_BUILD_PY%" -c "import sys, struct; print(sys.version); print(struct.calcsize('P') * 8)"
ver
```

## 10. Diagnose packaging problems

Reproduce with dummy data; keep real captures private/redacted. For console diagnostics rerun section 4 as `ShiftReportDebug`, replacing `--windowed` with `--console`; retain resource/path options. Inspect the actual exception and `%SR_BUILD_ROOT%\work\ShiftReportDebug\warn-ShiftReportDebug.txt`; optional/platform imports are not automatically failures. [Diagnostics](https://pyinstaller.org/en/v5.13.2/when-things-go-wrong.html).

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

```bat
"%SR_BUILD_ROOT%\dist\ShiftReportDebug\ShiftReportDebug.exe" --demo > "%SR_BUILD_ROOT%\demo-startup.txt" 2>&1
```

## Verification status of this guide

Reference commands only; no verified Windows EXE/installer, Windows 7, DPAPI or Tk certification. [Remaining checks](../status/testing-handoff.md) · [Observed results](../status/current-state.md#verification-and-limits).
