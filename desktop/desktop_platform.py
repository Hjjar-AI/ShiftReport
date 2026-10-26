"""Small OS adapters; no Tk, network or clinical data access on import."""
from __future__ import annotations
import os
from pathlib import Path
import shutil
import subprocess
import sys
import threading
from desktop_protocol import BridgeError, require


def data_directory():
    if os.name == 'nt':
        return Path(os.environ.get('LOCALAPPDATA', str(Path.home()))) / 'ShiftReportDesktop'
    require(sys.platform.startswith('linux'), 'هذا التطبيق يدعم Windows وLinux')
    base = Path(os.environ.get('XDG_DATA_HOME') or (Path.home() / '.local' / 'share'))
    if not base.is_absolute():
        base = Path.home() / '.local' / 'share'
    return base / 'ShiftReportDesktop'


def open_pdf(path):
    path = Path(path).resolve()
    require(path.is_file(), 'ملف المعاينة غير موجود')
    try:
        if os.name == 'nt':
            os.startfile(str(path))
            return
        require(sys.platform.startswith('linux'), 'معاينة PDF تدعم Windows وLinux')
        opener = shutil.which('xdg-open')
        require(opener is not None, 'ثبت xdg-utils وقارئ PDF ثم أعد المعاينة')
        process = subprocess.Popen([opener, str(path)], stdin=subprocess.DEVNULL,
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        try:
            code = process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            # Some desktop handlers stay alive until the viewer closes. Reap later;
            # approval still requires the user to inspect the actual opened PDF.
            threading.Thread(target=process.wait, daemon=True).start()
            return
        require(code == 0, 'تعذر فتح قارئ PDF؛ تحقق من التطبيق الافتراضي قبل الإرسال')
    except OSError:
        raise BridgeError('تعذر فتح قارئ PDF؛ تحقق من التطبيق الافتراضي قبل الإرسال') from None


def default_pdf_fonts():
    bundled = Path(__file__).resolve().parent / 'fonts' / 'Tajawal-Regular.ttf'
    if os.name == 'nt':
        return [bundled, Path(os.environ.get('WINDIR', 'C:/Windows')) / 'Fonts' / 'arial.ttf']
    return [bundled] + [Path(path) for path in [
        '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',
        '/usr/share/fonts/truetype/noto/NotoNaskhArabic-Regular.ttf',
        '/usr/share/fonts/noto/NotoNaskhArabic-Regular.ttf',
    ]]


def acquire_instance_lock():
    """Return a release callback, held for the entire app lifetime."""
    if os.name == 'nt':
        import ctypes
        from ctypes import wintypes
        kernel = ctypes.WinDLL('kernel32', use_last_error=True)
        kernel.CreateMutexW.argtypes = [ctypes.c_void_p, wintypes.BOOL, wintypes.LPCWSTR]
        kernel.CreateMutexW.restype = wintypes.HANDLE
        kernel.CloseHandle.argtypes = [wintypes.HANDLE]
        kernel.ReleaseMutex.argtypes = [wintypes.HANDLE]
        ctypes.set_last_error(0)
        handle = kernel.CreateMutexW(None, True, 'Local\\ShiftReportStandaloneDesktop')
        if not handle or ctypes.get_last_error() == 183:
            if handle:
                kernel.CloseHandle(handle)
            raise BridgeError('التطبيق مفتوح بالفعل؛ استخدم النافذة الحالية')
        def release():
            kernel.ReleaseMutex(handle)
            kernel.CloseHandle(handle)
        return release
    require(sys.platform.startswith('linux'), 'هذا التطبيق يدعم Windows وLinux')
    import fcntl
    directory = data_directory()
    directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    descriptor = os.open(str(directory / '.instance.lock'), os.O_CREAT | os.O_RDWR, 0o600)
    stream = os.fdopen(descriptor, 'a+b')
    try:
        fcntl.flock(stream.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError:
        stream.close()
        raise BridgeError('التطبيق مفتوح بالفعل؛ استخدم النافذة الحالية') from None
    except Exception:
        stream.close()
        raise
    # Do not unlink the lock: another process could hold the old inode.
    return stream.close
