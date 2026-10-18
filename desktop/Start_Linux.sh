#!/bin/sh
set -eu
shiftreport_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ ! -x "$shiftreport_dir/.venv/bin/python" ]; then
    printf '%s\n' 'Run ./Setup_Linux.sh first.' >&2
    exit 1
fi
exec "$shiftreport_dir/.venv/bin/python" "$shiftreport_dir/desktop_gui.py" "$@"
