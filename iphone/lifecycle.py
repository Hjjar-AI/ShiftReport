"""Pyto's bundled Rubicon adapter for app activity; no background execution grant."""

from rubicon.objc import NSObject, ObjCClass, objc_method
from rubicon.objc.runtime import SEL


class ShiftReportIphoneActivityObserver(NSObject):
    @objc_method
    def shiftReportInactive_(self, notification) -> None:
        callback = self.on_inactive
        if callback is not None:
            callback()

    @objc_method
    def shiftReportActive_(self, notification) -> None:
        callback = self.on_active
        if callback is not None:
            callback()


class ActivityObserver:
    """Create/close on UIKit's thread; retain the observer until unregistering."""

    def __init__(self, on_inactive, on_active):
        self.center = ObjCClass("NSNotificationCenter").defaultCenter
        self.observer = ShiftReportIphoneActivityObserver.alloc().init()
        self.observer.on_inactive = on_inactive
        self.observer.on_active = on_active
        try:
            self.center.addObserver(self.observer, selector=SEL("shiftReportInactive:"),
                                    name="UIApplicationWillResignActiveNotification", object=None)
            self.center.addObserver(self.observer, selector=SEL("shiftReportActive:"),
                                    name="UIApplicationDidBecomeActiveNotification", object=None)
        except Exception:
            self.close()
            raise

    @staticmethod
    def is_active():
        return int(ObjCClass("UIApplication").sharedApplication.applicationState) == 0

    def close(self):
        if self.observer is not None:
            self.center.removeObserver(self.observer)
            self.observer.on_inactive = None
            self.observer.on_active = None
            self.observer = None
