"""Open this script in Pyto, or select it in a foreground Run Script shortcut."""

import sys
from pathlib import Path


def main():
    # Resolve imports from the script location, independent of Shortcut cwd.
    root = str(Path(__file__).resolve().parent.parent)
    if root not in sys.path:
        sys.path.insert(0, root)
    from iphone.runtime import StartupError
    from iphone.host import open_host
    from iphone.pyto_host import present, show_message

    host = None
    claimed = False
    try:
        host = open_host()
        claimed = host.presentation.acquire(blocking=False)
        if not claimed:
            print("تقرير المناوبة مفتوح بالفعل؛ عد إلى نافذته داخل Pyto.")
            return
        present(host)
    except StartupError as error:
        show_message(str(error))
    except KeyboardInterrupt:
        print("أُغلق العرض. يجري انتظار العمليات المحلية قبل إيقاف الجلسة التجريبية.")
    except Exception:
        # Native/transport exception text can contain private URLs or paths.
        show_message("تعذّر فتح العرض. شغّل iphone/runtime_report.py لمعلومات التوافق.")
    finally:
        if host is not None and claimed:
            try:
                if not host.stop():
                    show_message("توجد عملية محلية لم تنتهِ؛ بقي الخادم مملوكاً لهذه الجلسة. أعد فتح المشغّل دون إعادة أي عملية تلقائياً.")
            except Exception:
                show_message("تعذّر تأكيد إغلاق الجلسة. لا تفترض نجاح عملية سابقة؛ لم يبدأ خادم بديل.")
            finally:
                host.presentation.release()


if __name__ == "__main__":
    main()
