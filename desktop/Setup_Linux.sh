#!/bin/sh
set -eu
shiftreport_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
shiftreport_python=${PYTHON_BIN:-python3}
"$shiftreport_python" - <<'PY'
import sys
if sys.version_info[:2] < (3, 8):
    raise SystemExit('Python 3.8 or later is required. Set PYTHON_BIN to a compatible interpreter.')
try:
    import tkinter
    import venv
except ImportError:
    raise SystemExit('Install Tcl/Tk and venv for the selected Python (e.g. python3-tk and python3-venv on Debian/Ubuntu).')
PY
"$shiftreport_python" -m venv "$shiftreport_dir/.venv"
"$shiftreport_dir/.venv/bin/python" -m pip install --upgrade --only-binary=:all: -r "$shiftreport_dir/requirements-linux.txt"
printf '%s\n' 'Ready. Run ./Start_Linux.sh in a graphical desktop session.' 'PDF preview needs xdg-utils, a default PDF viewer and an Arabic-capable TrueType font (e.g. DejaVu Sans).'
