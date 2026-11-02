#!/bin/sh
set -eu
cd "$(dirname "$0")"
if [ ! -x .venv/bin/python ]; then
    echo 'Create web/.venv and install requirements.txt first; see web/README.md.' >&2
    exit 1
fi
exec .venv/bin/python run.py "$@"
