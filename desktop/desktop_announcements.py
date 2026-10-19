"""Reviewed admin announcement updates using the existing shared index fields."""
import copy
import time
import json
from desktop_protocol import fingerprint, is_admin, require, packed


class Announcements:
    def __init__(self, client, workspace):
        self.client, self.workspace = client, workspace
        self._approval = None

    def prepare(self, text):
        require(not self.workspace.locked, 'الجلسة مقفلة')
        require(self.workspace.project == self.client.project_id, 'مساحة العمل تخص مشروعاً آخر')
        require(not any(row[0]=='announcement' for _,row in self.workspace.receipts(status='UNKNOWN')),
                'نشر إعلان سابق غير مؤكد؛ احسمه في سجل العمليات قبل المراجعة أو التكرار')
        require(isinstance(text,str) and text.strip(), 'نص الإعلان فارغ')
        require(len(text.encode('utf-16-le'))//2 <= 4096, 'الإعلان يتجاوز حد الرسالة')
        require(len(self.client.telegram.cipher.encode_text(text).encode('utf-16-le'))//2<=4096, 'النص المشفر أطول من حد الرسالة؛ اختصر الإعلان')
        snapshot = self.client.fetch()
        actor = self.client.authorize(snapshot['doctors'])
        require(is_admin(actor), 'إدارة الإعلانات متاحة للمدير فقط')
        return dict(text=text, state=copy.deepcopy(snapshot['state']), index=snapshot['index_id'], actor=actor['uid'])

    def approve(self, reviewed):
        self._approval = fingerprint(reviewed)

    def publish(self, reviewed):
        require(self._approval == fingerprint(reviewed), 'راجع الإعلان وأكد النص نفسه قبل النشر')
        self._approval = None
        require(not self.workspace.locked, 'الجلسة مقفلة')
        require(self.workspace.project == self.client.project_id, 'مساحة العمل تخص مشروعاً آخر')
        current = self.client.fetch()
        actor = self.client.authorize(current['doctors'])
        require(is_admin(actor) and actor['uid']==reviewed['actor'], 'تغيرت صلاحية المدير؛ أعد مراجعة الإعلان')
        require(current['index_id']==reviewed['index'] and fingerprint(current['state'])==fingerprint(reviewed['state']),
                'تغير فهرس المشروع؛ أعد مراجعة الإعلان')
        require(not any(row[0]=='announcement' for _,row in self.workspace.receipts(status='UNKNOWN')),
                'نشر إعلان سابق غير مؤكد؛ احسمه في سجل العمليات قبل التكرار')
        # No caller-supplied channel or parse mode; text is sent exactly as reviewed.
        text = reviewed['text']
        require(text.strip() and len(text.encode('utf-16-le'))//2<=4096, 'نص الإعلان غير صالح')
        wire = self.client.telegram.cipher.encode_text(text)
        require(len(wire.encode('utf-16-le'))//2<=4096, 'النص المشفر أطول من حد الرسالة')
        existing = current['state'].get('announcementMessageId')
        key = fingerprint(dict(operation='announcement', index=reviewed['index'], state=reviewed['state'], text=text))
        self.workspace.receipt_start(key,'announcement',self.client.config['chatId'],
                                     self.client.config.get('announcementsTopicId'),actor['uid'],
                                     snapshot=packed({k:current['state'].get(k) for k in ('announcementMessageId','announcementUpdatedAt')}))
        params = dict(chat_id=self.client.config['chatId'], text=wire)
        if existing:
            params['message_id'] = existing
            result = self.client.telegram.call('editMessageText',params)
        else:
            if self.client.config.get('announcementsTopicId'):params['message_thread_id']=self.client.config['announcementsTopicId']
            result = self.client.telegram.call('sendMessage',params)
        require(result.get('message_id'), 'لم يتأكد معرّف الإعلان؛ راجع سجل العمليات')
        latest = self.client.fetch()
        actor = self.client.authorize(latest['doctors'])
        require(is_admin(actor) and actor['uid']==reviewed['actor'], 'تغيرت الصلاحية بعد الإرسال؛ راجع سجل العمليات')
        require(latest['index_id']==reviewed['index'] and
                all(latest['state'].get(k)==reviewed['state'].get(k) for k in ('announcementMessageId','announcementUpdatedAt')),
                'نشر إعلان آخر بالتزامن؛ راجع تليجرام وسجل العمليات')
        state = copy.deepcopy(latest['state'])
        state.update(announcementMessageId=result['message_id'], announcementUpdatedAt=int(time.time()*1000))
        self.client.telegram.write_state(latest['index_id'],state)
        index, actual = self.client.telegram.state()
        require(index==latest['index_id'] and all(actual.get(k)==state[k] for k in ('announcementMessageId','announcementUpdatedAt')),
                'لم تتأكد إحالة الإعلان؛ راجع تليجرام قبل الإعادة')
        self.workspace.receipt_finish(result)
        return result


    def resolve_received(self,index,message_id):
        row = self.workspace.require_receipt_resolution(index,self.client)
        require(row[0]=='announcement' and type(message_id) is int and message_id>0, 'اختر إعلاناً غير مؤكد وأدخل معرّف رسالته')
        snapshot = self.client.fetch()
        actor = self.client.authorize(snapshot['doctors'])
        require(is_admin(actor), 'إصلاح إحالة الإعلان متاح للمدير فقط')
        require(row[7].startswith('snapshot='), 'لا توجد إحالة محفوظة؛ افحص فهرس الإعلان قبل الحسم')
        expected = json.loads(row[7][9:])
        fields = ('announcementMessageId','announcementUpdatedAt')
        latest = self.client.fetch()
        current_actor = self.client.authorize(latest['doctors'])
        require(is_admin(current_actor) and current_actor['uid']==actor['uid'], 'تغيرت صلاحية المدير')
        state = copy.deepcopy(latest['state'])
        if state.get('announcementMessageId')!=message_id and all(state.get(k)==expected.get(k) for k in fields):
            state.update(announcementMessageId=message_id,announcementUpdatedAt=int(time.time()*1000))
            self.client.telegram.write_state(latest['index_id'],state)
            index_id,actual = self.client.telegram.state()
            require(index_id==latest['index_id'] and actual.get('announcementMessageId')==message_id,
                    'لم يتأكد فهرس الإعلان؛ يبقى الإرسال محظوراً')
        # A different current reference is a newer announcement; retain it.
        self.workspace.resolve_unknown(index,False,self.client)


def administer_announcement(owner):
    import desktop_widgets as ctk
    from desktop_forms import button, entry, guarded, modal
    owner.ready()
    service = Announcements(owner.client,owner.store)
    # Check authority before opening the editor, then again in the mutation layer.
    actor = owner.io(owner.client.require_admin)
    window = modal(owner,'إدارة إعلان المشروع','740x620')
    ctk.CTkLabel(window,text='ينشر إعلاناً جديداً أو يعدل الإعلان المشار إليه في فهرس المشروع. لا يغير تثبيت فهرس المزامنة.',
                 wraplength=650,justify='right').pack(fill='x',padx=12,pady=8)
    text = entry(window,'نص الإعلان',multiline=True)
    def send():
        plan = owner.io(lambda:service.prepare(text.get('1.0','end-1c')))
        require(plan['actor']==actor['uid'],'تغير المستخدم؛ أعد فتح إدارة الإعلانات')
        if not owner.text_dialog('مراجعة الإعلان',plan['text'],True,'نشر نص الإعلان المعروض'):return
        service.approve(plan)
        owner.io(lambda:service.publish(plan))
        owner.status.set('تم نشر الإعلان وتحديث إحالته في فهرس المشروع.')
        window.destroy()
    button(window,'مراجعة ونشر الإعلان',guarded(send))
    button(window,'إغلاق',window.destroy)
    owner.root.wait_window(window)
