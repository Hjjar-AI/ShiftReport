"""Dependency/resource inventory, without installations or workspace access."""

import importlib
import platform
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
WEB = ROOT / "web"
REQUIRED = (
    ("Flask", "flask", ("Flask",)),
    ("Werkzeug", "werkzeug", ()),
    ("Jinja2", "jinja2", ()),
    ("waitress", "waitress", ("create_server",)),
    ("cryptography", "cryptography.hazmat.primitives.ciphers.aead", ("AESGCM",)),
    ("cryptography", "cryptography.hazmat.primitives.kdf.pbkdf2", ("PBKDF2HMAC",)),
    ("cffi", "cffi", ()),
    ("requests", "requests", ()),
    ("certifi", "certifi", ()),
    ("reportlab", "reportlab.pdfbase.ttfonts", ("TTFont",)),
    ("Pillow", "PIL.Image", ()),
    ("arabic-reshaper", "arabic_reshaper", ("ArabicReshaper",)),
    ("python-bidi", "bidi.algorithm", ("get_display",)),
    (None, "sqlite3", ("connect",)),
    (None, "fcntl", ("flock", "LOCK_EX", "LOCK_NB")),
    (None, "rubicon.objc", ("NSObject", "ObjCClass", "objc_method")),
    (None, "rubicon.objc.runtime", ("SEL",)),
    (None, "file_system", ("quick_look", "save_as", "share_files", "FilePickerCancellation")),
)
RESOURCES = (
    "web/shiftreport/__init__.py",
    "web/shiftreport/templates/base.html",
    "web/shiftreport/static/app.css",
    "web/shiftreport/static/app.js",
    "web/shiftreport/static/appearance.js",
    "web/shiftreport/static/department_emblem.png",
    "web/shiftreport/static/fonts/tajawal_regular.ttf",
    "web/shiftreport/static/fonts/tajawal_bold.ttf",
    "web/shiftreport/static/fonts/OFL.txt",
    "samples/data/patients.csv",
    "iphone/templates/base.html",
    "iphone/static/iphone.css",
    "iphone/static/iphone.js",
    "iphone/static/artifacts.js",
    "iphone/templates/report_review.html",
    "iphone/templates/artifact_operation.html",
)


class StartupError(Exception):
    """Only deliberately safe, user-readable startup messages belong here."""


def shared_source():
    expected = WEB / "shiftreport" / "__init__.py"
    loaded = sys.modules.get("shiftreport")
    loaded_file = getattr(loaded, "__file__", None)
    if loaded is not None and (not loaded_file or Path(loaded_file).resolve() != expected):
        raise StartupError("حُمّلت نسخة أخرى من ShiftReport؛ أعد تشغيل Pyto قبل فتح هذه النسخة.")
    if not expected.is_file():
        raise StartupError("مجلد web المشترك مفقود؛ احتفظ بترتيب مجلدات المشروع.")
    if str(WEB) not in sys.path:
        sys.path.insert(0, str(WEB))


def inventory():
    """Importability is recorded separately from crypto/PDF/device acceptance."""
    try:
        from importlib.metadata import PackageNotFoundError, version
    except ImportError:
        version = None

    packages = []
    for distribution, module_name, attributes in REQUIRED:
        item = dict(module=module_name, available=False, version=None)
        try:
            module = importlib.import_module(module_name)
            item["available"] = all(hasattr(module, name) for name in attributes)
        except Exception as error:
            item["error_type"] = type(error).__name__
        if distribution and version is not None:
            try:
                item["version"] = version(distribution)
            except PackageNotFoundError:
                pass  # Bundled native libraries may lack wheel metadata.
        packages.append(item)

    ui = {}
    try:
        native = importlib.import_module("pyto_ui")
        dispatch = importlib.import_module("mainthread")
        ui["dispatch"] = callable(getattr(dispatch, "run_sync", None))
        ui["async_dispatch"] = callable(getattr(dispatch, "run_async", None))
        for name in ("View", "WebView", "Label", "Button", "Alert", "Color", "show_view"):
            ui[name] = hasattr(native, name)
        for name in ("load_url", "evaluate_js", "url", "did_start_loading", "did_finish_loading", "did_fail_loading"):
            ui["WebView." + name] = hasattr(native.WebView, name)
        for name in ("hidden", "did_appear", "did_disappear"):
            ui["View." + name] = hasattr(native.View, name)
        ui["sheet"] = hasattr(native.PresentationMode, "SHEET")
    except Exception as error:
        ui["available"] = False
        ui["error_type"] = type(error).__name__

    try:
        from zoneinfo import ZoneInfo
        ZoneInfo("Asia/Damascus")
        timezone_available = True
    except Exception:
        timezone_available = False
    try:
        import certifi
        certificates_available = Path(certifi.where()).is_file()
    except Exception:
        certificates_available = False
    return dict(
        python=platform.python_version(), platform=sys.platform,
        pyto_release="Record the installed app release and edition separately.",
        packages=packages, ui=ui, damascus_timezone=timezone_available,
        https_certificates=certificates_available,
        resources={name: (ROOT / name).is_file() for name in RESOURCES},
        limits="Inventory only; no crypto operation, PDF rendering, server or device acceptance.",
    )


def require_runtime():
    if sys.version_info < (3, 10):
        raise StartupError("يتطلب مشغّل iPhone بيئة Pyto ذات Python 3.10 أو أحدث.")
    report = inventory()
    if (not all(item["available"] for item in report["packages"])
            or not report["ui"] or not all(value is True for value in report["ui"].values())
            or not all(report["resources"].values()) or not report["damascus_timezone"]
            or not report["https_certificates"]):
        raise StartupError("مكتبات أو واجهات Pyto أو ملفات مشتركة مطلوبة غير متاحة. شغّل iphone/runtime_report.py؛ لم تُثبّت أو تُستبدل أي مكتبة.")
    return report
