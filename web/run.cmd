@echo off
setlocal
cd /d "%~dp0"
if not exist ".venv\Scripts\python.exe" (
    echo Create web\.venv and install requirements.txt first; see web\README.md.
    exit /b 1
)
".venv\Scripts\python.exe" run.py %*
exit /b %errorlevel%
