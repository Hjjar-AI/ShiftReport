@echo off
setlocal
py -3.8 -c "import sys, tkinter; assert sys.version_info[:3] == (3,8,10), 'Install CPython 3.8.10 including Tcl/Tk and pip'"
if errorlevel 1 goto failure
py -3.8 -m pip install --only-binary=:all: -r "%~dp0requirements-win7.txt"
if errorlevel 1 goto failure
echo Ready. Run Start_Desktop.cmd. Excel is not required.
pause
exit /b 0
:failure
echo Setup did not finish. Check Python 3.8.10, Tcl/Tk, internet access and the displayed error.
pause
exit /b 1
