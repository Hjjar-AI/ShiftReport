# Bundle the Flask client as a Windows EXE

This guide packages the local Flask/Jinja client in `web/`. Use `web/` as the source root: `shiftreport/` is its Python package, while `run.py` starts the local server and opens the browser. The native CustomTkinter client has a separate [Windows packaging guide](../docs/guides/desktop/windows-exe.md); its `desktop_gui.py`, Tk assets and configuration commands do not apply here.

**Status, 3 November 2026:** documentation and a proposed packaging entry point only. The entry-point code below is not installed in the repository. These commands have not been executed on Windows, and no Flask EXE, installer or signed release is claimed. Application behavior and remaining acceptance work are recorded in [current state](../docs/status/current-state.md#local-flask-client) and the [single verification handoff](../docs/status/testing-handoff.md#local-flask-client).

## Contents

1. [What the executable does](#1-what-the-executable-does)
2. [Choose output and Windows target](#2-choose-output-and-windows-target)
3. [Prepare a clean source copy](#3-prepare-a-clean-source-copy)
4. [Check tools and workstation capacity](#4-check-tools-and-workstation-capacity)
5. [Create an isolated packaging environment](#5-create-an-isolated-packaging-environment)
6. [Add the packaging entry point](#6-add-the-packaging-entry-point)
7. [Build the folder bundle](#7-build-the-folder-bundle)
8. [Launch, select storage and stop](#8-launch-select-storage-and-stop)
9. [Build a single EXE](#9-build-a-single-exe)
10. [Inspect and distribute the result](#10-inspect-and-distribute-the-result)
11. [Update or move an installation](#11-update-or-move-an-installation)
12. [Optional icon, shortcut and installer](#12-optional-icon-shortcut-and-installer)
13. [Reproducibility and offline dependencies](#13-reproducibility-and-offline-dependencies)
14. [Troubleshooting](#14-troubleshooting)
15. [References and verification limits](#15-references-and-verification-limits)

## 1. What the executable does

The launch sequence remains the existing application sequence:

```text
ShiftReportWeb.exe
    -> choose persistent data directory and acquire its instance lock
    -> create the Flask application
    -> start Waitress on 127.0.0.1:8765
    -> open the default browser
    -> serve HTML, CSS, JavaScript, fonts and PDFs locally
```

The browser remains the user interface. Packaging does not introduce a native window, embed a browser, add a shared server or create a Windows service. Waitress already [supports Windows](https://flask.palletsprojects.com/en/stable/deploying/waitress/). Keep the application's existing `127.0.0.1` binding and Host/origin/CSRF checks.

Recipients need a compatible Windows installation and browser. They do not need a separate Python installation or `pip`; PyInstaller bundles the interpreter and dependencies. Initial dependency acquisition requires internet or a prepared wheelhouse. The local demo can operate offline; Telegram operations still require connectivity and the existing identity/review/confirmation workflow.

Closing the browser tab leaves the server running. The console provides the current shutdown mechanism: **Ctrl+C**. For that reason, both recipes below deliberately keep a console. Hiding it requires a separately implemented, reviewable lifecycle/shutdown interface.

## 2. Choose output and Windows target

| Choice | Result | Distribution | Practical use |
|---|---|---|---|
| `--onedir` | A folder containing an EXE, Python libraries and resources | Send the entire folder, usually zipped | Start here; resources and missing dependencies are easier to inspect |
| `--onefile` | One EXE that extracts its support files at startup | Send the EXE plus applicable notices | Convenient after the folder build has been accepted; extraction adds startup work |
| Installer | A separate setup program around an accepted bundle | Installer plus required notices | Optional deployment work; not produced by these recipes |

Both modes need a separate writable data directory. A single EXE does not mean that saved patient records are embedded in it. PyInstaller describes its [folder/file runtime behavior](https://pyinstaller.org/en/v5.13.2/operating-mode.html) and [resource locations](https://pyinstaller.org/en/v5.13.2/runtime-information.html).

This recipe follows the existing repository packaging precedent: **CPython 3.8.10**, the unchanged `web/requirements.txt`, and **PyInstaller 5.13.2** in a separate build environment. It does not upgrade application dependencies or change app/database/interchange versions.

Python 3.8 is [end of life](https://www.python.org/downloads/release/python-3810/); these are compatibility pins, not a claim of current upstream maintenance. A newer runtime/dependency target requires its own owner-approved compatibility work. PyInstaller 5.13.2 [documents Windows 8+](https://pyinstaller.org/en/v5.13.2/requirements.html); this guide does not establish Windows 7 compatibility or acceptance on any particular Windows release.

Build on **Windows**, using the interpreter architecture intended for recipients. A Linux build produces a Linux executable, even if it is renamed `.exe`. WSL/Linux output is not a Windows build. For ordinary x64 PCs, use x64 Python; x64 output does not run on x86 Windows. ARM64 and other architectures need separate dependency and target validation.

All `bat` blocks below are for **Command Prompt (`cmd.exe`)**. A `^` continues a command and must be the final character on its line, without trailing spaces. Blocks labelled `powershell` run in PowerShell instead. Change the example source path to your actual Windows location; `/mnt/Common/...` is the Linux workspace path.

## 3. Prepare a clean source copy

Use a separate Windows working copy containing the current public application source and the one fictional sample file. Keep its structure:

```text
C:\ShiftReport\
    samples\
        data\
            patients.csv
    web\
        requirements.txt
        run.py
        run.cmd
        run.sh
        bundle.md
        shiftreport\
            __init__.py
            instance.py
            demo.py
            workspace.py
            features.py
            views.py
            clinical_views.py
            data_views.py
            report_views.py
            core\
                ...all current Python modules...
            templates\
                ...all current templates...
            static\
                app.css
                app.js
                appearance.js
                department_emblem.png
                fonts\
                    tajawal_regular.ttf
                    tajawal_bold.ttf
                    OFL.txt
```

Copy all current package modules and templates, including files added after this guide; the tree illustrates ownership rather than a fixed Python-file allowlist. `old/desktop/`, Android sources and Node tooling are not runtime dependencies of this Flask client.

Do not copy the working `web/instance/` directory, `.venv/`, caches, `.env` files, protected joins, private verification folders, signing material, patient data, exports or backups into the distribution. A Linux virtual environment cannot serve as the Windows build environment. Ignore rules do not filter PyInstaller resources: avoid `--add-data ".;."` or collecting the whole repository.

The recipes explicitly include only package templates/static assets and the fictional sample CSV. Imports supply application code; dependency hooks supply required libraries. Keep Tajawal's `OFL.txt` with its fonts and preserve the emblem's white background.

Open Command Prompt and check the copy:

```bat
set "SR_WEB_SOURCE=C:\ShiftReport\web"
cd /d "%SR_WEB_SOURCE%"
dir run.py
dir requirements.txt
dir shiftreport\templates
dir shiftreport\static\fonts
dir ..\samples\data\patients.csv
```

Stop if a required path is missing. These commands list source assets, not private workspace contents.

## 4. Check tools and workstation capacity

Before **each** build, inspect work across projects/accounts: active Gradle/Java/Kotlin builds, IDE compilation, MSBuild/C/C++ compilers, other packaging/Python jobs, and available RAM/pagefile capacity. Compare available memory with the active tools' configured heaps. An idle IDE/daemon or cached memory alone does not establish pressure.

For a read-only overview, run these in a separate PowerShell window. They print process identity and memory, not full command lines that might contain secrets:

```powershell
$srNames = '^(java|javaw|gradle|kotlinc|studio64|idea64|devenv|MSBuild|VBCSCompiler|dotnet|cl|clang|clang-cl|cmake|ninja|python|pythonw|pyinstaller|node|Code)\.exe$'
Get-CimInstance Win32_Process -ErrorAction Stop |
    Where-Object { $_.Name -match $srNames } |
    ForEach-Object {
        $srProcess = $_
        $srOwner = Invoke-CimMethod -InputObject $srProcess -MethodName GetOwner -ErrorAction Stop
        [pscustomobject]@{
            Name = $srProcess.Name
            PID = $srProcess.ProcessId
            Session = $srProcess.SessionId
            Owner = if ($srOwner.ReturnValue -eq 0) { $srOwner.Domain + '\' + $srOwner.User } else { 'UNKNOWN' }
            WorkingSetMB = [math]::Round([double]$srProcess.WorkingSetSize / 1MB)
        }
    } | Format-Table -AutoSize

Get-CimInstance Win32_OperatingSystem -ErrorAction Stop |
    Select-Object @{n='FreeRAM_MB';e={[math]::Round($_.FreePhysicalMemory / 1024)}},
                  @{n='FreeVirtual_MB';e={[math]::Round($_.FreeVirtualMemory / 1024)}},
                  @{n='TotalVirtual_MB';e={[math]::Round($_.TotalVirtualMemorySize / 1024)}}

Get-CimInstance Win32_PageFileUsage -ErrorAction Stop |
    Select-Object Name, AllocatedBaseSize, CurrentUsage, PeakUsage
```

This is a snapshot, not automatic proof that every process is idle. Check activity in Task Manager and the relevant IDE/build owner; inspect heap settings privately. If activity, memory pressure or incomplete cross-account visibility prevents a reliable decision, defer the build. Do not terminate another person's work, stop their daemons or clear shared caches. Keep build commands below for a suitable window.

Check the installed interpreter in Command Prompt:

```bat
py -0p
py -3.8 -c "import sys, struct; print(sys.version); print(str(struct.calcsize('P') * 8) + '-bit'); assert sys.version_info[:3] == (3, 8, 10), 'Use CPython 3.8.10 for this recipe'"
ver
```

If the launcher is unavailable, use the full path to the intended `python.exe`. Do not substitute an unrelated Python selected by `PATH`. If software installation is needed, arrange it once with owner approval. Existing `web/.venv` and `old/desktop/.venv` can help inventory installed tools; do not modify or reuse a running application's environment for packaging.

## 5. Create an isolated packaging environment

The source remains under `C:\ShiftReport\web`. The build environment, work files, specs, logs and output go under a separate per-user directory. Choose a **fresh** build-root name for each candidate so previous evidence/output survives. Re-establish these variables whenever opening a new Command Prompt:

```bat
set "SR_WEB_SOURCE=C:\ShiftReport\web"
set "SR_WEB_BUILD=%LOCALAPPDATA%\ShiftReportWebBuild\candidate-01"
set "SR_WEB_PY=%SR_WEB_BUILD%\venv\Scripts\python.exe"
cd /d "%SR_WEB_SOURCE%"
```

Run installation only when authorized. Stop after any failure; do not paste subsequent blocks until it is resolved:

```bat
py -3.8 -m venv "%SR_WEB_BUILD%\venv"
"%SR_WEB_PY%" -m pip install --only-binary=:all: -r requirements.txt
"%SR_WEB_PY%" -m pip install --only-binary=:all: "pyinstaller==5.13.2"
"%SR_WEB_PY%" -m pip check
"%SR_WEB_PY%" -m PyInstaller --version
"%SR_WEB_PY%" -c "import sys, struct; print(sys.version); print(str(struct.calcsize('P') * 8) + '-bit')"
```

`--only-binary=:all:` makes missing Windows wheels fail explicitly instead of unexpectedly compiling dependencies. Preserve the pinned application requirements if resolution fails; inspect interpreter/architecture and the actual pip error. Packaging dependencies resolved by pip are recorded in section 13. The examples do not silently upgrade pip, Python or the app dependency set.

You can inspect the existing source launcher's supported arguments without starting a server:

```bat
"%SR_WEB_PY%" run.py --help
```

## 6. Add the packaging entry point

Two current path assumptions require adaptation:

| Existing source behavior | Why freezing needs attention | Adaptation in the wrapper |
|---|---|---|
| `run.py` defaults to `instance/` beside its source file | In one-file mode that is an extraction directory; app data must survive shutdown/update | Supply `%LOCALAPPDATA%\ShiftReportWeb` unless the user explicitly passes `--data-dir` |
| `demo.py` locates the sample at `parents[2]/samples/...` | The frozen package is rooted differently from `web/shiftreport/` | Point its sample constant to the explicitly bundled `samples/` directory |

Templates, static files and PDF fonts can keep their package-relative lookup when resources are copied to matching destinations. PyInstaller explains the distinction between [`__file__`, extraction paths and `sys.executable`](https://pyinstaller.org/en/v5.13.2/runtime-information.html). `sys.executable` locates the launched EXE; it does not locate one-file extracted resources.

In the **Windows source copy**, create `web\bundle_windows.py` with the following complete contents. This is a packaging adapter around the existing `run.main()`; it preserves the existing Waitress binding, browser launch, port validation and data-directory lock:

```python
"""Windows packaging entry point; keep normal run.py behavior when unfrozen."""

import os
import sys
from pathlib import Path

from run import main as run_main


def main():
    if getattr(sys, "frozen", False):
        bundle_root = Path(__file__).resolve().parent

        # Match --add-data's destination, not the source repository's depth.
        from shiftreport import demo

        demo.SAMPLE = (
            bundle_root / "samples" / "data" / "patients.csv"
        )

        # Explicit command-line storage selection always wins.
        has_data_dir = any(
            arg == "--data-dir" or arg.startswith("--data-dir=")
            for arg in sys.argv[1:]
        )
        if not has_data_dir:
            local_app_data = os.environ.get("LOCALAPPDATA")
            if not local_app_data or not Path(local_app_data).is_absolute():
                raise SystemExit(
                    "LOCALAPPDATA is unavailable. Pass an absolute --data-dir."
                )
            data_dir = Path(local_app_data) / "ShiftReportWeb"
            sys.argv[1:1] = ["--data-dir", str(data_dir)]

    run_main()


if __name__ == "__main__":
    main()
```

Save as UTF-8 and confirm it is `bundle_windows.py`, not `bundle_windows.py.txt`:

```bat
dir bundle_windows.py
"%SR_WEB_PY%" bundle_windows.py --help
```

Running this wrapper from Python delegates to the ordinary source launcher and retains its existing source data default. Only the frozen EXE selects the new default automatically. Therefore, real data previously held in `web/instance/` is **not** automatically moved or discovered. Section 11 explains storage continuity without converting any formats.

The wrapper does not add encryption, authentication, publication behavior or an automatic existing-instance browser opener. A second launch against the same data directory retains the existing lock/error behavior.

## 7. Build the folder bundle

**Scope:** package the Flask client and its dependencies/assets into a Windows console folder bundle; write build output only under `%SR_WEB_BUILD%`. No app startup, installer, live Telegram operation, tests or app-version change is part of this command. Complete section 4 immediately before building and obtain the required build authorization.

From `%SR_WEB_SOURCE%`, run this complete block. It keeps a full local log and uses distinct work/spec/output paths. No `--clean`, shared-cache deletion or overwrite-confirmation bypass is requested:

```bat
"%SR_WEB_PY%" -m PyInstaller ^
  --name ShiftReportWeb ^
  --onedir ^
  --console ^
  --noupx ^
  --distpath "%SR_WEB_BUILD%\dist" ^
  --workpath "%SR_WEB_BUILD%\work-folder" ^
  --specpath "%SR_WEB_BUILD%\spec-folder" ^
  --paths "%SR_WEB_SOURCE%" ^
  --collect-submodules shiftreport ^
  --collect-all arabic_reshaper ^
  --collect-all certifi ^
  --collect-all tzdata ^
  --add-data "%SR_WEB_SOURCE%\shiftreport\templates;shiftreport\templates" ^
  --add-data "%SR_WEB_SOURCE%\shiftreport\static;shiftreport\static" ^
  --add-data "%SR_WEB_SOURCE%\..\samples\data\patients.csv;samples/data" ^
  "%SR_WEB_SOURCE%\bundle_windows.py" > "%SR_WEB_BUILD%\build-folder.log" 2>&1
set "SR_WEB_BUILD_EXIT=%ERRORLEVEL%"
echo Packaging exit code: %SR_WEB_BUILD_EXIT%
```

The terminal can be quiet while output is redirected. Await process completion. A nonzero exit means failure; preserve the log and inspect its first substantive error. Do not claim success just because an EXE exists from an earlier attempt.

```bat
type "%SR_WEB_BUILD%\build-folder.log"
dir "%SR_WEB_BUILD%\dist\ShiftReportWeb"
```

Expected entry point after a successful build:

```text
%LOCALAPPDATA%\ShiftReportWebBuild\candidate-01\dist\ShiftReportWeb\ShiftReportWeb.exe
```

| Option | Purpose |
|---|---|
| `--console` | Exposes startup errors and provides Ctrl+C shutdown |
| `--noupx` | Avoids relying on an external UPX installation |
| `--collect-submodules shiftreport` | Includes the application's current package modules, including deferred imports |
| `--collect-all arabic_reshaper` | Includes the shaping package and its resource/configuration data |
| `--collect-all certifi` | Includes the CA bundle used by HTTPS dependencies |
| `--collect-all tzdata` | Includes timezone data needed by Windows/ZoneInfo |
| Explicit `--add-data` entries | Preserve Flask/font resource layout and include only the fictional sample CSV |
| Separate paths and redirected output | Preserve diagnosable output without writing generated artifacts into source |

PyInstaller's standard hooks handle dependencies such as cryptography, Pillow and ReportLab; their actual completeness still needs target acceptance. Do not add private data to resolve a missing dependency.

The Windows source/destination separator in this pinned PyInstaller version is **`;`**. These commands start from the `.py` entry point. Building an old `.spec` can retain outdated resources/options; most choices are [encoded in the spec](https://pyinstaller.org/en/v5.13.2/spec-files.html).

Resource source paths are absolute because the generated spec lives outside the source tree. Destinations stay relative to the bundle root. Keep those two sides distinct when editing the recipe.

## 8. Launch, select storage and stop

Once runtime work is authorized, use a new, **disposable dummy-only** data directory first. This overrides the EXE default and avoids opening existing clinical workspaces:

```bat
"%SR_WEB_BUILD%\dist\ShiftReportWeb\ShiftReportWeb.exe" ^
  --data-dir "%LOCALAPPDATA%\ShiftReportWebDummy\candidate-01" ^
  --port 8875
```

The default browser should open `http://127.0.0.1:8875/` and the initial workspace uses the fictional demo. There is no `--demo` argument in this Flask launcher; that flag belongs to the native desktop client. Do not join/create a real project as part of a packaging launch.

For an intentionally accepted installation, normal launch is:

```bat
"%SR_WEB_BUILD%\dist\ShiftReportWeb\ShiftReportWeb.exe"
```

That opens `http://127.0.0.1:8765/` and uses `%LOCALAPPDATA%\ShiftReportWeb`. To open the browser yourself:

```bat
"%SR_WEB_BUILD%\dist\ShiftReportWeb\ShiftReportWeb.exe" ^
  --no-browser ^
  --data-dir "%LOCALAPPDATA%\ShiftReportWebDummy\candidate-01" ^
  --port 8875
```

Command-line options are unchanged:

| Argument | Meaning |
|---|---|
| `--port 8875` | Select a local port; allowed range is 1024–65535 |
| `--no-browser` | Start the server without launching the default browser |
| `--data-dir "C:\absolute\directory"` | Select the persistent instance root; prefer an absolute, user-writable path |
| `--help` | Display arguments and exit |

Close editing/review work deliberately, then press **Ctrl+C in the server console** and await its exit before replacing files or restarting. Closing the terminal window is not the documented graceful shutdown path. Browser refresh reloads pages, not Python code; code changes require a deliberate server restart. Reviews and sessions held in server memory do not survive shutdown.

Locks and ports are independent. A different port does not permit a second process to use a locked data directory. A different data directory does not free an occupied port. The lock file can remain on disk after exit: its existence alone does not mean the OS lock is held, so deleting it is not a repair procedure.

Use a browser on the same PC. No inbound firewall rule, port forwarding or LAN address is needed for the loopback-only design. The app's peer/Host guards intentionally reject external access.

## 9. Build a single EXE

Proceed after the folder candidate has been accepted and a single-file build is authorized. Repeat the capacity preflight. The packaging wrapper in section 6 is required: it keeps persistent storage outside the extraction directory and finds the sample inside it.

```bat
cd /d "%SR_WEB_SOURCE%"
"%SR_WEB_PY%" -m PyInstaller ^
  --name ShiftReportWebSingle ^
  --onefile ^
  --console ^
  --noupx ^
  --distpath "%SR_WEB_BUILD%\dist" ^
  --workpath "%SR_WEB_BUILD%\work-single" ^
  --specpath "%SR_WEB_BUILD%\spec-single" ^
  --paths "%SR_WEB_SOURCE%" ^
  --collect-submodules shiftreport ^
  --collect-all arabic_reshaper ^
  --collect-all certifi ^
  --collect-all tzdata ^
  --add-data "%SR_WEB_SOURCE%\shiftreport\templates;shiftreport\templates" ^
  --add-data "%SR_WEB_SOURCE%\shiftreport\static;shiftreport\static" ^
  --add-data "%SR_WEB_SOURCE%\..\samples\data\patients.csv;samples/data" ^
  "%SR_WEB_SOURCE%\bundle_windows.py" > "%SR_WEB_BUILD%\build-single.log" 2>&1
set "SR_WEB_BUILD_EXIT=%ERRORLEVEL%"
echo Packaging exit code: %SR_WEB_BUILD_EXIT%
```

Expected artifact:

```text
%LOCALAPPDATA%\ShiftReportWebBuild\candidate-01\dist\ShiftReportWebSingle.exe
```

Authorized dummy launch, after stopping any process using the same dummy directory:

```bat
"%SR_WEB_BUILD%\dist\ShiftReportWebSingle.exe" ^
  --data-dir "%LOCALAPPDATA%\ShiftReportWebDummy\candidate-single-01" ^
  --port 8876
```

Resources are unpacked to a temporary `_MEI...` directory. Treat that directory as implementation-owned, temporary support data, never the clinical workspace. Do not configure a fixed shared extraction directory or run the EXE as administrator. Extraction/antivirus scanning can delay the browser opening; a quiet startup is not by itself a failed import. Inspect the settled process and console before retrying.

The single EXE still uses external persistent data, downloaded files and any explicitly saved PDFs. Those do not become part of the executable. An accepted folder build does not establish acceptance of the single-file artifact.

## 10. Inspect and distribute the result

A successful PyInstaller exit proves packaging completed. It does not prove Windows runtime behavior, key protection, Arabic rendering, PDF output or clinical workflow acceptance. Use the existing [Flask verification handoff](../docs/status/testing-handoff.md#local-flask-client) for the remaining cases and record the actual target OS, architecture, dummy-data scope and observed results there. Keep source review, package creation and runtime results distinct; this guide adds no second remaining-check list.

Inspect the selected distribution before sharing. Its resources should match the explicit include list; do not distribute a build-root archive, source checkout or `instance/` tree. Keep private runtime configuration, connections and records separate. Built-in fonts and applicable third-party license notices remain part of distribution; retaining only the program's EXE does not waive notice requirements.

For a folder build, distribute the **entire `dist\ShiftReportWeb` folder**, with all DLLs, Python support files, resources and notices. Users must extract it before launching. Create a fresh ZIP after inspection:

```bat
powershell -NoProfile -Command "Compress-Archive -LiteralPath ($env:SR_WEB_BUILD + '\dist\ShiftReportWeb') -DestinationPath ($env:SR_WEB_BUILD + '\ShiftReportWeb-Windows-candidate-01.zip')"
certutil -hashfile "%SR_WEB_BUILD%\ShiftReportWeb-Windows-candidate-01.zip" SHA256
```

For single-file output, send `ShiftReportWebSingle.exe` with applicable notices. If providing the bundled font license separately, copy it into a clean release folder beside the EXE. Hash the final artifact after any authorized signing:

```bat
certutil -hashfile "%SR_WEB_BUILD%\dist\ShiftReportWebSingle.exe" SHA256
```

A hash detects changed bytes when compared through a trusted channel; it is not a digital signature. Protected `.srjoin.json` files and their passwords must be delivered separately through the normal protected-join process, never embedded in the app or its ZIP.

Recipient instructions can be brief: extract the whole folder, double-click the EXE, keep its console open while working, use the browser on that PC, and press Ctrl+C when finished. Python installation is not part of recipient setup.

## 11. Update or move an installation

| Item | Location with this recipe | Update rule |
|---|---|---|
| Application code/resources | Distributed folder or single EXE | Replace the complete accepted artifact while stopped |
| Default instance root | `%LOCALAPPDATA%\ShiftReportWeb` | Preserve across app updates |
| Clinical workspace files | `workspaces/` beneath the selected instance root | Preserve; use normal encrypted backup/recovery workflows |
| Instance lock | `.server.lock` beneath the selected instance root | Let the OS lock coordinate processes; do not delete to bypass a running server |
| Build environment/spec/logs | `%LOCALAPPDATA%\ShiftReportWebBuild\candidate-01` | Private build material, not recipient storage |
| Dummy-only launch data | Explicit `%LOCALAPPDATA%\ShiftReportWebDummy\...` | Keep separate from real projects |
| Source-launcher default | `web/instance/` | Existing behavior; not automatically relocated by packaging |

Before replacing an installed artifact, finish active work, make a normal encrypted recovery export where appropriate, retain the protected join/key-recovery material, stop the server and preserve its data root. Replace the entire folder build rather than mixing DLLs/resources from different candidates. Restart using the same intended `--data-dir` or the stable packaged default.

For an existing Windows source installation, you can explicitly keep its existing instance root:

```bat
"C:\Apps\ShiftReportWeb\ShiftReportWeb.exe" ^
  --data-dir "C:\ShiftReport\web\instance"
```

Do this only after the source process using that root has exited. If the EXE opens an empty demo instead of a known project, confirm the selected directory and normal protected reconnection; do not reset/delete files to make the old project disappear. No automatic relocation, reader conversion or schema change is performed by the wrapper.

Moving an EXE is different from moving an encrypted workspace. Windows-local protected keys can be bound to the Windows profile, particularly for locally protected state in projects without a Telegram data key. Retain original profile/key files and use the application's reviewed backup/recovery and protected-join workflow when transferring work. Copying a database alone is not a recovery plan; do not generate replacement keys over unreadable files.

Do not uninstall by deleting `%LOCALAPPDATA%\ShiftReportWeb`. Removing the executable and removing saved clinical work are separate owner decisions. No uninstall/deletion command is supplied here.

## 12. Optional icon, shortcut and installer

The existing emblem is `shiftreport\static\department_emblem.png`; preserving it in the web assets is already covered. A Windows EXE icon is a separate resource. If an approved `.ico` is available in the Windows copy, add this option to the chosen build command, keeping all other arguments:

```bat
  --icon "C:\ShiftReport\branding\ShiftReport.ico" ^
```

Use the approved artwork and preserve the original emblem/white field. The recipe does not generate or alter branding. Changing the icon rebuilds the artifact and needs the same preflight/authorization as any other build.

A desktop shortcut can target the accepted EXE. Put arguments after the closing quote around the executable, for example:

```text
Target: "C:\Apps\ShiftReportWeb\ShiftReportWeb.exe" --port 8765
Start in: C:\Apps\ShiftReportWeb
```

An explicit absolute `--data-dir` can also be placed in that target. The default should remain the user's writable data directory; do not point it inside `Program Files`, the ZIP or an extraction folder.

PyInstaller produces neither an installer nor a signature. An installer/signing task needs a separately selected deployment tool, organizational certificate/identity and explicit distribution scope. Its data-retention behavior must preserve the existing instance root. This guide supplies no certificate passwords, private-key export, automatic signing or deployment commands.

Do not replace `--console` with `--windowed` merely to hide the terminal: current shutdown and diagnostics depend on it. A hidden process without a user-accessible shutdown control would require further implementation.

## 13. Reproducibility and offline dependencies

Record the exact source snapshot and dependency environment privately. From a Git working copy, these are read-only commands; otherwise record the identity of the supplied source copy manually:

```bat
git rev-parse HEAD > "%SR_WEB_BUILD%\source-commit.txt"
git status --short > "%SR_WEB_BUILD%\source-status.txt"
"%SR_WEB_PY%" -m pip freeze > "%SR_WEB_BUILD%\packaging-environment.txt"
"%SR_WEB_PY%" -m PyInstaller --version > "%SR_WEB_BUILD%\pyinstaller-version.txt"
"%SR_WEB_PY%" -c "import sys, struct; print(sys.version); print(str(struct.calcsize('P') * 8) + '-bit')" > "%SR_WEB_BUILD%\python-target.txt"
ver > "%SR_WEB_BUILD%\windows-version.txt"
```

Keep the packaging wrapper and exact command with the receipt. A commit ID alone does not describe uncommitted source changes. Inspect local receipts before sharing; full build logs can contain local paths and should not become public clinical/support exports. No credentials or full process command lines belong in receipts.

For approved offline installation, prepare a wheelhouse **on a matching Windows/Python/architecture environment**. Include the full frozen packaging environment, not just application requirements, so PyInstaller's own dependencies are available:

```bat
"%SR_WEB_PY%" -m pip download ^
  --only-binary=:all: ^
  --dest "%SR_WEB_BUILD%\wheelhouse" ^
  -r "%SR_WEB_BUILD%\packaging-environment.txt"
```

On the offline Windows builder, install the same CPython first. Copy the clean source, wrapper, wheelhouse and `packaging-environment.txt`. In a fresh build root, after setting `SR_WEB_SOURCE`, `SR_WEB_BUILD` and `SR_WEB_PY` as in section 5:

```bat
py -3.8 -m venv "%SR_WEB_BUILD%\venv"
"%SR_WEB_PY%" -m pip install ^
  --no-index ^
  --only-binary=:all: ^
  --find-links "D:\ShiftReportBuildInputs\wheelhouse" ^
  -r "D:\ShiftReportBuildInputs\packaging-environment.txt"
"%SR_WEB_PY%" -m pip check
```

Use your actual media path. If a wheel is missing, resolve that on the connected matching builder and preserve the failure evidence; do not fall back to random versions. An environment freeze improves traceability but does not guarantee byte-identical EXEs or current dependency maintenance. Run the selected packaging command only after its preflight and authorization.

## 14. Troubleshooting

Stop at the first failing step and keep its original log. Use the console build and dummy-only storage for diagnosis. Do not delete saved workspaces, downgrade encryption, bypass identity/review guards or disable HTTPS certificate checking to make a bundle appear functional.

| Symptom | Check / action |
|---|---|
| `py` is not recognized | Check installed Python/launcher; use the exact interpreter path if necessary |
| `py -3.8` selects the wrong patch/bitness | Inspect `py -0p` and the version assertion; select the intended interpreter explicitly |
| No matching wheel / installation tries a compiler | Confirm Python 3.8.10, Windows architecture and the exact failing pin; keep requirements unchanged while diagnosing |
| PyInstaller error only visible in a log | Read `build-folder.log` or `build-single.log`; the packaging terminal is redirected intentionally |
| `bundle_windows.py` not found | Create the exact filename from section 6 and run from the selected `web/` root |
| EXE cannot find the fictional CSV | Include the exact sample resource and build the wrapper, which adjusts the frozen sample path |
| `TemplateNotFound` / missing styles or emblem | Check both `--add-data` destinations and the complete distributed folder; do not flatten `shiftreport/` resources |
| Missing Arabic glyphs / wrong PDF font | Confirm both Tajawal files and their license exist under bundled `shiftreport/static/fonts`; preserve case/spelling |
| Arabic shaping resource/import failure | Confirm `--collect-all arabic_reshaper` and inspect the actual console error before adding narrower missing resources |
| `ZoneInfoNotFoundError` / Damascus timezone error | Confirm `--collect-all tzdata` and compatible backports for Python 3.8 from the unchanged requirements |
| HTTPS certificate failure | Check bundled certifi data, clock, proxy/network policy; keep certificate verification enabled |
| Missing DLL / not a valid Win32 application | Confirm Windows/interpreter/recipient architecture and complete folder distribution; do not download arbitrary DLLs |
| Port is unavailable | Confirm which local process owns it; stop your own server normally or select an unused port without disturbing others |
| Data directory is already in use | Open the existing local address or stop that server normally; a new port cannot bypass its instance lock |
| App opens as demo / expected local state absent | Confirm `--data-dir` and protected reconnection; packaged and source defaults differ |
| State vanishes after single-file exit | Confirm the wrapper was used and the selected data root is outside temporary extraction paths |
| Access denied saving state | Select an absolute per-user writable data directory; do not elevate the clinical app to work around it |
| Browser does not open | Read the printed local URL and open it manually; verify default-browser setup and that Waitress started |
| Closing the tab leaves a process | Expected current behavior; press Ctrl+C in the server console |
| Code change has no effect | Finish work and restart the relevant server; browser refresh cannot reload Python code |
| Antivirus/SmartScreen intervenes | Preserve the detection and verify source/artifact under organizational policy; signing is separate and no bypass is prescribed |
| Works on builder but fails on recipient PC | Confirm full extraction, architecture, permissions and clean-machine acceptance; retain both environments' details |

Read-only local port lookup, in Command Prompt:

```bat
netstat -ano -p tcp | findstr ":8765"
```

Identify the displayed PID in Task Manager; do not use a blanket `taskkill` command. A matching line can be a connection rather than the listening server: inspect its state and local address.

PyInstaller warnings normally appear at these paths:

```text
%SR_WEB_BUILD%\work-folder\ShiftReportWeb\warn-ShiftReportWeb.txt
%SR_WEB_BUILD%\work-single\ShiftReportWebSingle\warn-ShiftReportWebSingle.txt
```

Optional/platform imports can produce warnings without an application failure. Investigate the actual failing import first. If a confirmed dynamically loaded module is absent, add the narrow `--hidden-import package.module` to the original command; do not invent a module name or collect unrelated/private trees. Retain the failed evidence until a resolving artifact passes its authorized checks.

For an authorized dummy diagnostic launch with local output capture:

```bat
"%SR_WEB_BUILD%\dist\ShiftReportWeb\ShiftReportWeb.exe" ^
  --no-browser ^
  --data-dir "%LOCALAPPDATA%\ShiftReportWebDummy\diagnostic-01" ^
  --port 8877 > "%SR_WEB_BUILD%\dummy-startup.log" 2>&1
```

This command stays running until Ctrl+C; inspect the log from a second window and browse to `http://127.0.0.1:8877/` yourself. Python output redirected to a file can be buffered, so an initially empty log does not prove failure. Do not turn on Flask debug mode or capture real credentials/request bodies to obtain more logging. See PyInstaller's [diagnostic guidance](https://pyinstaller.org/en/v5.13.2/when-things-go-wrong.html).

## 15. References and verification limits

| Reference | Purpose |
|---|---|
| [Flask client README](README.md) | Existing setup, workflow, source ownership and data boundaries |
| [Native Windows EXE guide](../docs/guides/desktop/windows-exe.md) | Related Python/PyInstaller precedent for the separate Tk client |
| [Current implementation/evidence](../docs/status/current-state.md#local-flask-client) | What has actually been implemented and observed |
| [Flask handoff](../docs/status/testing-handoff.md#local-flask-client) | Sole remaining acceptance list; execute only with required approval |
| [PyInstaller 5.13.2 usage](https://pyinstaller.org/en/v5.13.2/usage.html) | CLI options and Windows data syntax |
| [Runtime paths](https://pyinstaller.org/en/v5.13.2/runtime-information.html) | Frozen resources versus persistent external files |
| [Spec-file behavior](https://pyinstaller.org/en/v5.13.2/spec-files.html) | Encoded settings and resource collection |

This documentation task does not install software, create the wrapper, compile/package the application, launch Windows processes, run tests, access migrations, publish Telegram content or certify a target platform. Those actions remain separate from writing the guide. The folder and single-file commands are reviewable recipes pending actual Windows execution and acceptance.
