"""Pyto presentation/activity owner; no page-to-Python message bridge is exposed."""

import json
import queue
import threading
import time
from urllib.parse import urlsplit

from .launch_gate import AUTHORIZE, BOOTSTRAP


_dispatch_guard = threading.Lock()


def show_message(message):
    """Use only safe application messages; missing native APIs fall back to console."""
    print(message)
    try:
        import pyto_ui as ui
        alert = ui.Alert("تقرير المناوبة", message)
        alert.add_action("حسناً")
        # Pyto's blocking alert API belongs on the calling script thread.
        alert.show()
    except Exception:
        pass


class SessionScreen:
    def __init__(self, host, ui, dispatch):
        self.host, self.ui, self.dispatch = host, ui, dispatch
        self.events = queue.Queue()
        self.stopped = threading.Event()
        self.guard = threading.RLock()
        self.epoch = 0
        self.active = False
        self.activity = None
        self.browser = None
        self.shield = None

    def sync(self, callback):
        # The published Pyto helper uses shared return/exception slots.
        with _dispatch_guard:
            return self.dispatch.run_sync(callback)

    def build(self):
        from .lifecycle import ActivityObserver

        ui = self.ui
        self.root = ui.View()
        self.root.title = "تقرير المناوبة • DEMO"
        self.status = ui.Label("جارٍ فتح التجربة الوهمية…")
        self.status.number_of_lines = 0
        self.back = ui.Button(title="رجوع")
        self.check = ui.Button(title="تحقق")
        self.browser = ui.WebView()
        # Cover the native surface without changing the WebView's visibility
        # state, which would otherwise interfere with page-session revalidation.
        self.shield = ui.View()
        self.shield.background_color = ui.Color.rgb(0.93, 0.95, 0.93, 1)
        self.shield.opaque = True
        for child in (self.status, self.back, self.check, self.browser, self.shield):
            self.root.add_subview(child)

        def layout(view):
            width, height = view.width, view.height
            bar = 128
            self.status.frame = (8, 8, max(0, width-16), 56)
            self.back.frame = (8, 72, max(48, (width-24)/2), 48)
            self.check.frame = (16+(width-24)/2, 72, max(48, (width-24)/2), 48)
            self.browser.frame = (0, bar, width, max(0, height-bar))
            self.shield.frame = self.browser.frame

        self.root.layout = layout
        self.back.action = lambda sender: self.events.put("back")
        self.check.action = lambda sender: self.events.put("check")
        self.browser.did_start_loading = lambda sender: self.navigation()
        self.browser.did_finish_loading = lambda sender: self.events.put("check")
        self.browser.did_fail_loading = lambda sender, error: self.loading_failed()
        self.root.did_appear = lambda sender: self.resume() if ActivityObserver.is_active() else self.pause()
        self.root.did_disappear = lambda sender: self.pause()
        self.activity = ActivityObserver(self.pause, self.resume)
        self.active = ActivityObserver.is_active()
        self.browser.load_url(self.host.origin + BOOTSTRAP)
        return self.root

    def pause(self):
        # App notifications arrive on UIKit's thread: hide before asynchronous I/O.
        with self.guard:
            self.epoch += 1
            self.active = False
        self.shield.hidden = False
        self.status.text = "المحتوى مخفي؛ يلزم التحقق عند العودة."
        self.events.put("cover")

    def resume(self):
        with self.guard:
            self.epoch += 1
            self.active = True
        self.events.put("check")

    def navigation(self):
        with self.guard:
            self.epoch += 1
        self.shield.hidden = False
        self.status.text = "جارٍ تحميل الصفحة المحلية…"

    def loading_failed(self):
        with self.guard:
            self.epoch += 1
        self.shield.hidden = False
        self.status.text = "تعذّر تحميل الصفحة. تحقق من الجلسة؛ لا تفترض نجاح الحفظ."

    def _current(self, epoch):
        with self.guard:
            return self.active and self.epoch == epoch and not self.stopped.is_set()

    def _update(self, message, epoch, reveal=False):
        def update():
            if self._current(epoch):
                self.status.text = message
                self.shield.hidden = reveal
        self.dispatch.run_async(update)

    def _url(self):
        return self.sync(lambda: self.browser.url)

    def _owned(self, url):
        if not isinstance(url, str):
            return False
        parsed, expected = urlsplit(url), urlsplit(self.host.origin)
        return parsed.scheme == expected.scheme and parsed.netloc == expected.netloc

    def _bootstrap(self, epoch):
        # Fixed JavaScript only, and only after native readiness/URL checks.
        script = """(() => {
            if (location.href !== %s) return;
            fetch(%s, {method: 'POST', credentials: 'same-origin',
                headers: {'X-ShiftReport-Launch': %s}, redirect: 'error'})
            .then(response => {
                if (response.status !== 204) throw new Error('launch');
                location.replace('/');
            }).catch(() => {
                document.querySelector('[role="status"]').textContent =
                    'تعذّر فتح الجلسة؛ لم تُعد أي عملية سريرية.';
            });
        })();""" % (json.dumps(self.host.origin + BOOTSTRAP), json.dumps(AUTHORIZE), json.dumps(self.host.gate.token))
        self.browser.evaluate_js(script)
        deadline = time.monotonic() + 4
        while self._current(epoch) and time.monotonic() < deadline:
            if self._url() != self.host.origin + BOOTSTRAP:
                return  # The new document's loading callback revalidates it.
            self.stopped.wait(0.1)
        self._update("تعذّر فتح الجلسة ضمن المهلة. استخدم تحقق أو أعد فتح المشغّل.", epoch)

    def _check(self):
        with self.guard:
            epoch = self.epoch
        if not self._current(epoch):
            return
        self._update("جارٍ التحقق من الخادم والجلسة…", epoch)
        if not self.host.ready(timeout=2):
            self._update("تعذّر تأكيد الخادم القائم؛ لم يُنشأ بديل أو تُعد أي عملية.", epoch)
            return
        url = self._url()
        if not self._current(epoch):
            return
        if not self._owned(url):
            self._update("هذا العنوان خارج الجلسة المحلية. استخدم رجوع للعودة دون إرسال بيانات إليه.", epoch)
            return
        if url == self.host.origin + BOOTSTRAP:
            self._bootstrap(epoch)
            return
        self.browser.evaluate_js("if (window.shiftreportIphone) { void window.shiftreportIphone.resume(true); }")
        deadline = time.monotonic() + 5
        while self._current(epoch) and time.monotonic() < deadline:
            result = self.browser.evaluate_js("window.shiftreportIphone ? window.shiftreportIphone.state : 'missing';")
            if result == "ready":
                self._update("تجربة وهمية • التغييرات في ذاكرة الجلسة فقط", epoch, reveal=True)
                return
            if result in ("lost", "missing"):
                break
            self.stopped.wait(0.1)
        self._update("تعذّر تأكيد جلسة الصفحة. بقي المحتوى مخفياً ولم تُعد أي عملية.", epoch)

    def work(self):
        # evaluate_js/readiness never block UIKit's thread or overlap each other.
        while not self.stopped.is_set():
            try:
                event = self.events.get(timeout=0.25)
            except queue.Empty:
                continue
            try:
                if event == "check":
                    self._check()
                elif event == "cover" and self._owned(self._url()):
                    self.browser.evaluate_js("window.shiftreportIphone && window.shiftreportIphone.cover();")
                elif event == "back":
                    def back():
                        if not self.stopped.is_set() and self.browser.can_go_back:
                            self.browser.go_back()
                    self.dispatch.run_async(back)
            except Exception:
                with self.guard:
                    epoch = self.epoch
                self._update("تعذّر إكمال التحقق. احتُفظ بالصفحة دون إعادة تحميل أو حفظ تلقائي.", epoch)

    def close(self):
        self.stopped.set()
        if self.activity is not None:
            self.activity.close()
        if self.shield is not None:
            self.shield.hidden = False


def present(host):
    import mainthread
    import pyto_ui as ui

    screen = SessionScreen(host, ui, mainthread)
    worker = None
    try:
        root = screen.sync(screen.build)
        worker = threading.Thread(target=screen.work, name="ShiftReport-iPhone-UI", daemon=True)
        worker.start()
        # Pyto's blocking presentation API runs on this script thread.
        ui.show_view(root, ui.PresentationMode.SHEET)
    finally:
        screen.stopped.set()
        screen.sync(screen.close)
        if worker is not None:
            worker.join(timeout=2)
