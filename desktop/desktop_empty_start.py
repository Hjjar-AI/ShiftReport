"""Explicit permanent-superadmin empty seed; preserve accounts and Telegram history."""
import copy
import uuid
from desktop_protocol import BridgeError, current_date, doctor_pointer, encode_bundle, fingerprint, require
from desktop_doctors import permanent

CLINICAL_KEYS = ('baseline', 'draft', 'recycle', 'restored', 'localCompletions',
                 'patientForm', 'downloadUndo', 'downloads')


def local_signature(store):
    return fingerprint({key:store.state.get(key) for key in CLINICAL_KEYS})


class EmptyStart:
    def __init__(self, client, store):
        self.client, self.store = client, store
        self.source = client.fetch_registry()
        actor = client.authorize(self.source['doctors'])
        require(permanent(actor), 'البداية الفارغة متاحة للمدير الدائم فقط')
        require(not store.locked, 'الجلسة مقفلة')
        self.actor_id = client._actor
        self.local = local_signature(store)
        self.used = False
        self.shift = dict(meta=dict(schemaVersion='7', shiftId=str(uuid.uuid4()),
            shiftDate=current_date(client.zone), doctors=self.actor_id, shiftRevision='1',
            snapshotId=str(uuid.uuid4()), baseSnapshotId='', deviceId=client.device,
            sortSpec='{"levels":[]}'), patients=[])

    def authorize(self, source):
        actor = self.client.authorize(source['doctors'])
        require(self.client._actor == self.actor_id and permanent(actor), 'تغير المستخدم أو صلاحية المدير الدائم')
        require(doctor_pointer(source['state']) == doctor_pointer(self.source['state']), 'تغير سجل الأطباء؛ أعد المراجعة')
        require(not self.store.locked and local_signature(self.store) == self.local,
                'تغيرت البيانات المحلية؛ لم تُمسح تعديلاتك. أعد مراجعة البداية الفارغة')
        return actor

    def publish(self):
        require(not self.used, 'أعد مراجعة البداية الفارغة قبل محاولة جديدة')
        self.authorize(self.client.fetch_registry())
        raw = encode_bundle([self.shift])
        self.used = True
        receipt = fingerprint(dict(operation='empty_start', project=self.client.project_id,
                                   snapshot=self.shift['meta']['snapshotId']))
        self.store.receipt_start(receipt, 'empty_start', self.client.config['chatId'],
            self.client.config.get('csvTopicId'), self.actor_id, snapshot=self.shift['meta']['snapshotId'])
        uploaded = self.client.telegram.upload(raw, 'ShiftReport_empty.csv',
            self.client.config.get('csvTopicId'), 'بداية فارغة — '+self.shift['meta']['shiftDate'])
        current = self.client.fetch_registry()
        self.authorize(current)
        index_id, state = current['index_id'], copy.deepcopy(current['state'])
        at = int(uploaded['date'])*1000
        state.update(csvFileId=uploaded['document']['file_id'], csvMessageId=uploaded['message_id'],
            csvDate=self.shift['meta']['shiftDate'], csvUpdatedAt=at,
            previousCsvFileId=None, previousCsvMessageId=None, previousCsvDate=None, previousCsvUpdatedAt=0)
        state['publicationJournal'] = [dict(snapshotId=self.shift['meta']['snapshotId'], parentSnapshotId=None,
            fileId=state['csvFileId'], messageId=state['csvMessageId'], at=at,
            deviceId=self.client.device, forced=True)]
        committed = self.client.telegram.write_state(index_id, state)
        baseline = dict(index_id=index_id,state=committed,doctors=current['doctors'],bundle=[self.shift])
        try:
            # Re-check local changes after transport; never discard work that appeared in flight.
            self.authorize(current)
            self.store.commit(baseline=baseline, draft=copy.deepcopy(self.shift), recycle=[], restored=[],
                localCompletions=[], patientForm=None, downloadUndo=None, downloads={})
            self.client.baseline = copy.deepcopy(baseline)
            self.client.doctors = copy.deepcopy(current['doctors'])
            self.store.receipt_finish(uploaded)
        except Exception:
            raise BridgeError('تليجرام أكد البداية الفارغة؛ تعذر تحديث العرض المحلي. لا تعد العملية؛ راجع سجل العمليات والنسخة الحالية') from None


def empty_start_dialog(owner):
    owner.ready()
    require(not owner.demo, 'البداية الفارغة غير متاحة في العرض التجريبي')
    plan = owner.io(lambda:EmptyStart(owner.client,owner.store))
    local = owner.store.state.get('draft') or {}
    text = ('سيُمسح: المرضى والمناوبات المخزنة على هذا الجهاز، بما فيها السلة ومسودة الإدخال. وستُنشر بداية مرضى فارغة للفريق.\n'
            'مرضى المناوبة المعروضة: {}\n'
            'يشمل المسح تعديلات المرضى غير المنشورة؛ لا يلزم نشرها أو جلب بيانات المرضى أولاً.\n'
            'سيبقى: سجل الأطباء والحسابات وإعدادات المشروع والاتصال والتشفير وسجل التسليم. لا تُحذف رسائل أو ملفات تليجرام.\n'
            'على الأجهزة الأخرى جلب البداية الجديدة قبل العمل؛ نشر نسخة قديمة قد يعيد المرضى. لا يُرسل تقرير.\n'
            'احفظ نسخة احتياطية أولاً إذا أردت الاحتفاظ بها خارج سجل تليجرام.').format(len(local.get('patients',[])))
    if not owner.text_dialog('مسح المرضى والمناوبات؟',text,True,'مسح المرضى والمناوبات'):return
    owner.io(plan.publish)
    owner.plan=None;owner.raw_draft=None
    from desktop_cards import PatientCards
    owner.cards=PatientCards()
    owner.set_doctors()
    owner.notify_success('تم نشر البداية الفارغة؛ بقي الأطباء والاتصال وسجل تليجرام. على الأجهزة الأخرى جلب البيانات قبل العمل.')
