"""Device-local clinician PINs. Unlocking never creates Telegram authority."""
from __future__ import annotations
import copy
import hashlib
import hmac
import secrets
import time
import unicodedata
from desktop_protocol import require, is_admin


def normalize_pin(pin):
    return ''.join(str(unicodedata.digit(char)) if char.isdecimal() else char for char in pin)


class LocalAccess:
    def __init__(self, workspace, client):
        self.workspace,self.client=workspace,client

    def records(self):
        return self.workspace.state.get('localAccess',{}).get('pins',{})

    def eligible(self):
        return [key for key,record in self.records().items() if key in self.client.doctors and
                self.client.doctors[key].get('telegram_id')==record['telegram']]

    def enroll(self, pin, minutes=5):
        actor=self.client.require_local_identity()
        pin=normalize_pin(pin)
        # The desktop Client supplies live clinician authorization, not admin-only enrollment.
        require(pin.isascii() and pin.isdigit() and 4<=len(pin)<=12,'PIN يجب أن يكون 4–12 رقماً')
        require(minutes in {1,5,15},'مدة قفل غير صالحة')
        key=actor['uid'];salt=secrets.token_hex(16)
        digest=hashlib.pbkdf2_hmac('sha256',pin.encode('ascii'),bytes.fromhex(salt),600000).hex()
        state=copy.deepcopy(self.workspace.state.get('localAccess',{}));pins=state.setdefault('pins',{})
        pins[key]=dict(salt=salt,digest=digest,telegram=actor['telegram_id'])
        state.update(minutes=minutes,failures=0,until=0)
        self.workspace.commit(localAccess=state)
        self.workspace.local_actor = key
        return key

    def verify(self, key, pin, now=None):
        now=time.time() if now is None else now
        state=copy.deepcopy(self.workspace.state.get('localAccess',{}))
        require(now>=state.get('until',0),'محاولات كثيرة؛ انتظر قبل إعادة المحاولة')
        require(key in self.eligible(),'لم يعد PIN مرتبطاً بطبيب نشط؛ استخدم رمز تليجرام')
        record=state['pins'][key]
        pin=normalize_pin(pin)
        valid_format=pin.isascii() and pin.isdigit() and 4<=len(pin)<=12
        value=pin if valid_format else ''
        digest=hashlib.pbkdf2_hmac('sha256',value.encode('ascii'),bytes.fromhex(record['salt']),600000).hex()
        success=valid_format and hmac.compare_digest(digest,record['digest'])
        failures=0 if success else state.get('failures',0)+1
        delay=0 if failures<5 else min(900,30*2**min(5,failures-5))
        state.update(failures=failures,until=0 if success else now+delay)
        self.workspace.commit(localAccess=state)
        if success:self.workspace.local_actor = key
        return success

    def timeout(self):
        return self.workspace.state.get('localAccess',{}).get('minutes',5)*60


def configure_pin(owner):
    from desktop_widgets import simpledialog
    owner.ready()
    require(not owner.demo,'PIN غير متاح في الوضع التجريبي')
    # Revalidate membership and the current Telegram identity before enrollment/reset.
    actor=owner.io(owner.client.require_local_identity)
    pin=simpledialog.askstring('PIN محلي','اختر PIN من 4–12 رقماً لهذا الطبيب على هذا الجهاز:',show='*',parent=owner.root)
    if pin is None:return
    confirmation=simpledialog.askstring('PIN محلي','أعد PIN:',show='*',parent=owner.root)
    try:
        require(pin==confirmation,'لم يتطابق PIN')
        choice=owner.choose('القفل التلقائي',['بعد دقيقة','بعد 5 دقائق','بعد 15 دقيقة'])
        if choice is None:return
        require(owner.client._actor==actor['uid'],'تغيرت هوية الطبيب؛ أعد الإعداد')
        owner.local_actor=owner.io(lambda:owner.access.enroll(pin,[1,5,15][choice]))
    finally:pin=None;confirmation=None
    owner.last_activity=time.monotonic()
    owner.status.set('حُفظ PIN محلي مشفر. لا ينقل إلى CSV أو النسخ الاحتياطية؛ النشر يبقى برمز تليجرام.')


def lock_screen(owner):
    import tkinter as tk
    import desktop_widgets as ctk
    from desktop_forms import button
    from desktop_model import doctor_label
    from desktop_widgets import simpledialog
    if owner.locked or owner.demo or owner.access is None or not owner.access.records():return
    owner.locked=True;owner.store.locked=True;owner.store.local_actor=None
    owner.plan=None;owner.raw_draft=None
    owner.client._actor=None;owner.client._expires=0;owner.client._nonce=None;owner.client._verified_telegram=None
    previous_grab=owner.root.grab_current()
    hidden=[]
    # Preserve unsaved editors while hiding every clinical window during the lock.
    def conceal(widget):
        for child in list(widget.winfo_children()):
            if isinstance(child,tk.Toplevel):
                hidden.append((child,child.state()))
                child.withdraw()
            conceal(child)
    conceal(owner.root)
    if previous_grab:previous_grab.grab_release()
    owner.root.withdraw()
    window=ctk.CTkToplevel(owner.root);window.title('ShiftReport — مقفل');window.geometry('620x440')
    window.protocol('WM_DELETE_WINDOW',lambda:None)
    window.grab_set()
    ctk.CTkLabel(window,text='الجلسة مقفلة — التعديلات والنوافذ المفتوحة محفوظة\nاختر الطبيب ثم أدخل PIN أو رمز تليجرام.',justify='right').pack(fill='x',padx=15,pady=18)
    keys=[key for key,doctor in owner.client.doctors.items() if doctor.get('telegram_id')]
    labels=[doctor_label(owner.client.doctors,key) for key in keys]
    selector=ctk.CTkComboBox(window,values=labels or ['استخدم تليجرام'],state='readonly',justify='right')
    selector.pack(fill='x',padx=15,pady=5)
    pin=ctk.CTkEntry(window,show='*',justify='right');pin.pack(fill='x',padx=15,pady=8)
    active=False
    feedback=ctk.CTkLabel(window,text='',justify='right',wraplength=570)
    feedback.pack(fill='x',padx=15,pady=4)
    def attempt(action):
        def run():
            nonlocal active
            if active or owner.closed:return
            active=True
            try:action()
            except Exception as error:
                from desktop_protocol import BridgeError
                if window.winfo_exists():feedback.configure(text=str(error) if isinstance(error,BridgeError) else 'تعذر فتح الجلسة؛ تحقق من الاتصال وحاول مجدداً')
            finally:active=False
        return run
    def unlock(actor):
        owner.local_actor=actor;owner.locked=False;owner.store.locked=False
        owner.last_activity=time.monotonic();pin.delete(0,'end');window.destroy()
        owner.actor.set(doctor_label(owner.client.doctors,actor))
        owner.status.set('فُتحت الجلسة: '+owner.client.doctors[actor]['name']+' — النشر يتطلب هوية تليجرام متحققة.')
        owner.root.deiconify()
        for child,state in hidden:
            if child.winfo_exists() and getattr(child,'admin_only',False) and not is_admin(owner.client.doctors.get(actor,{})):
                child.destroy()
                continue
            if child.winfo_exists() and state!='withdrawn':
                if state=='iconic':child.iconify()
                else:child.deiconify()
        if previous_grab and previous_grab.winfo_exists():previous_grab.grab_set()
        owner.render()
    def check():
        key=next((key for key,label in zip(keys,labels) if label==selector.get()),None)
        secret=pin.get();pin.delete(0,'end')
        try:valid=owner.io(lambda:owner.access.verify(key,secret))
        finally:secret=None
        require(valid,'PIN غير صحيح')
        unlock(key)
    def telegram():
        key=next((key for key,label in zip(keys,labels) if label==selector.get()),None)
        require(key is not None,'لا يوجد طبيب مرتبط؛ أعد فتح البرنامج بملف الانضمام الصحيح')
        owner.io(lambda:owner.client.begin_login(key))
        code=simpledialog.askstring('رمز تليجرام','الرمز المرسل إلى حساب الطبيب:',show='*',parent=window)
        if code is None:return
        try:actor=owner.io(lambda:owner.client.check_login(code))
        finally:code=None
        require(actor,'رمز غير صحيح');unlock(actor['uid'])
    button(window,'فتح بـ PIN',attempt(check));button(window,'فتح برمز تليجرام / استعادة الوصول',attempt(telegram))
    def quit_locked():
        if owner.io_depth:return
        window.destroy();owner.busy=False;owner.close()
    button(window,'إغلاق البرنامج',quit_locked)
    owner.root.wait_window(window)
