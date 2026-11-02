"""Device-local clinician PINs. Unlocking never creates Telegram authority."""
from __future__ import annotations
import copy
import hashlib
import hmac
import secrets
import time
import unicodedata
from .protocol import require, is_admin


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




