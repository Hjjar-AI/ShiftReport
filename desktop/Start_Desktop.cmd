@echo off
setlocal
cd /d "%~dp0"
py -3.8 "%~dp0desktop_gui.py"
if errorlevel 1 pause
