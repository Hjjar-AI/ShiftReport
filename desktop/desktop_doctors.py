"""Reviewed registry administration using the existing Android wire format."""
from __future__ import annotations
import copy
import hashlib
import json
import re
import time
import uuid
from desktop_protocol import doctor_pointer, fingerprint, is_admin, parse_doctors, pointer, require

HEADER = '#,uid,name,gender,id,title,options,clinicalRole,supervisorGroupChatId,teamSupervisorId'


def rank(doctor):
    return int(doctor.get('#') or doctor.get('options', '')[1:] or 0)


def permanent(doctor):
    return is_admin(doctor) and doctor.get('options', '').startswith('A')


def encode_doctors(doctors):
    from desktop_teams import validate_teams
    validate_teams(doctors)
    require(0 < len(doctors) <= 500, 'عدد الأطباء يجب أن يكون بين 1 و500')
    rows, names, accounts, ranks = [], set(), set(), set()
    for key, doctor in doctors.items():
        values = [str(doctor.get(field, '')) for field in HEADER.split(',')]
        require(key == doctor['uid'] and all(not any(c in value for c in ',|@\n\r"') for value in values),
                'بيانات الطبيب تحتوي رموزاً لا يدعمها السجل')
        require(doctor['gender'] in {'M', 'F'} and doctor['name'].strip(), 'اسم أو جنس غير صالح')
        require(doctor['name'].casefold() not in names, 'اسم الطبيب موجود مسبقاً')
        names.add(doctor['name'].casefold())
        identity = doctor.get('telegram_id')
        require(identity is None or 0 < identity < 2**52 and identity not in accounts, 'حساب تليجرام غير صالح أو مكرر')
        if identity is not None: accounts.add(identity)
        number = rank(doctor)
        require(0 <= number <= 2147483647 and (not number or is_admin(doctor) and number not in ranks),
                'رتبة مدير غير صالحة أو مكررة')
        require(number or not doctor.get('options'), 'خيارات مدير غير صالحة')
        if number: ranks.add(number)
        values[4] = str(identity) if identity else ''
        values[8] = str(doctor['destination']) if doctor.get('destination') else ''
        rows.append(','.join(values))
    raw = (HEADER+'|'+ '|'.join(rows)+'@'+str(max(ranks or {0}))).encode('utf-8')
    parse_doctors(raw)
    return raw


def profile(values, existing=None):
    """No device PIN is transported in the shared registry."""
    first, last = (str(values.get(key, '')).strip() for key in ('first', 'last'))
    title = str(values.get('title', '')).strip()
    forbidden = set('\\"\'=,|@+&\n\r')
    require(not any(c in forbidden for c in first+last+title) and len(title) <= 16,
            'الاسم والكنية واللقب لا تقبل رموزاً ممنوعة؛ اللقب حتى 16 محرفاً')
    require(values.get('gender') in {'M', 'F'} and values.get('clinicalRole') in {'RESIDENT', 'SUPERVISOR'},
            'الجنس أو التصنيف السريري غير صالح')
    identity, destination = str(values.get('telegram_id', '')).strip(), str(values.get('destination', '')).strip()
    require(not identity or re.fullmatch(r'[0-9]+', identity) and 0 < int(identity) < 2**52,
            'معرّف حساب تليجرام يجب أن يكون رقماً موجباً')
    require(not destination or values['clinicalRole'] == 'SUPERVISOR' and
            re.fullmatch(r'-[0-9]+', destination) and -(2**63) <= int(destination) < 0,
            'مجموعة التقارير متاحة للمشرف ويجب أن يكون معرّفها سالباً')
    unnamed = not first and not last
    generated_id = (existing or {}).get('uid') or str(uuid.uuid4())
    name = 'د. بلا اسم '+generated_id[:8] if unnamed else 'د. '+' '.join(filter(None,[first,last]))
    key = str(uuid.UUID(bytes=hashlib.md5(('shift-report-doctor|'+re.sub(r'\s+', ' ', name).lower()).encode()).digest(), version=3))
    result = copy.deepcopy(existing) if existing else dict(uid=generated_id if unnamed else key, **{'#':'0', 'options':''})
    result.update(name=name, gender=values['gender'], title=title, clinicalRole=values['clinicalRole'],
                  telegram_id=int(identity) if identity else None, destination=int(destination) if destination else None)
    result['id'] = identity
    result['supervisorGroupChatId'] = destination
    result['teamSupervisorId'] = str(values.get('teamSupervisorId', (existing or {}).get('teamSupervisorId','')) or '').strip() if values['clinicalRole']=='RESIDENT' else ''
    return result


class Administration:
    def __init__(self, client, workspace):
        self.client, self.workspace = client, workspace

    def fetch(self):
        snapshot = self.client.fetch()
        require(is_admin(self.client.authorize(snapshot['doctors'])), 'إدارة الأطباء متاحة للمدير فقط')
        return snapshot

    def _references(self, key, snapshot, roster=False):
        from desktop_protocol import current_date
        shifts = snapshot['bundle'] + ([self.workspace.read()] if self.workspace.state['draft'] else [])
        for shift in shifts:
            if roster:
                require(key not in shift['meta'].get('doctors', '').split(','), 'أزل الطبيب من أطباء المناوبة الحالية أولاً')
            for patient in shift['patients']:
                tasks = json.loads(patient.get('tasksJson') or '[]')
                require(key not in (patient.get('responsibleResidentId'), patient.get('responsibleSupervisorId')),
                        'أعد إسناد المرضى والمهام المعلقة وانشرها قبل حذف الطبيب أو تغيير تصنيفه')

    def propose(self, snapshot, action, key=None, values=None):
        actor = self.client.authorize(snapshot['doctors'])
        require(is_admin(actor), 'إدارة الأطباء متاحة للمدير فقط')
        doctors = copy.deepcopy(snapshot['doctors'])
        old = doctors.get(key)
        require(action in {'add', 'import', 'teams'} or old is not None, 'الطبيب لم يعد في السجل')
        if action == 'teams':
            require(isinstance(values,dict) and values, 'اختر تغييرات الفرق أولاً')
            for resident,supervisor in values.items():
                require(resident in doctors and doctors[resident]['clinicalRole']=='RESIDENT', 'المقيم غير متاح؛ أعد مراجعة الفرق')
                doctors[resident]['teamSupervisorId']=supervisor or ''
            encode_doctors(doctors)
            return doctors
        if action == 'import':
            from desktop_csv import import_registry
            return import_registry(self, snapshot, values or [], actor)
        if action in {'add', 'edit'}:
            candidate = profile(values or {}, old)
            if old and old['clinicalRole'] != candidate['clinicalRole']:
                self._references(key, snapshot)
            if action == 'add':
                require(candidate['uid'] not in doctors, 'يوجد سجل بهذا المعرّف؛ استخدم تحرير الطبيب')
            doctors[candidate['uid']] = candidate
        elif action == 'delete':
            require(not is_admin(old), 'أزل صلاحية المدير قبل حذف الطبيب')
            self._references(key, snapshot, roster=True)
            del doctors[key]
        elif action == 'promote':
            require(not is_admin(old), 'الطبيب مدير بالفعل')
            number = max(rank(d) for d in doctors.values())+1
            old.update({'#':str(number), 'options':'a'+str(number), 'title':'المديرة' if old['gender']=='F' else 'المدير'})
        elif action == 'demote':
            require(is_admin(old) and not permanent(old), 'لا يمكن إزالة صلاحية المدير الدائم')
            require(permanent(actor) or rank(actor) > rank(old), 'لا يمكنك إزالة مدير أعلى رتبة أو مساوي الرتبة')
            old.update({'#':'0', 'options':'', 'title':''})
        else:
            require(False, 'عملية إدارة غير مدعومة')
        encode_doctors(doctors)
        return doctors

    def review(self, snapshot, action, key=None, values=None):
        doctors = self.propose(snapshot, action, key, values)
        return dict(snapshot=copy.deepcopy(snapshot), action=action, key=key, values=copy.deepcopy(values),
                    doctors=doctors, actor=self.client._actor, draft=fingerprint(self.workspace.state['draft']))

    def approve(self, plan):
        plan['approval'] = fingerprint({k:v for k,v in plan.items() if k != 'approval'})

    def publish(self, plan):
        require(plan.get('approval') == fingerprint({k:v for k,v in plan.items() if k != 'approval'}) and
                plan['actor'] == self.client._actor, 'تغيرت مراجعة الأطباء أو هوية المنفذ؛ أعدها')
        require(plan['draft'] == fingerprint(self.workspace.state['draft']), 'تغيرت المسودة المحلية؛ أعد مراجعة الأطباء')
        current = self.fetch()
        expected = plan['snapshot']
        def unchanged(snapshot):
            return (snapshot['index_id'] == expected['index_id'] and
                    pointer(snapshot['state']) == pointer(expected['state']) and
                    doctor_pointer(snapshot['state']) == doctor_pointer(expected['state']) and
                    fingerprint(snapshot['doctors']) == fingerprint(expected['doctors']))
        require(unchanged(current), 'وصلت بيانات أحدث؛ أعد مراجعة الأطباء')
        proposed = self.propose(current, plan['action'], plan['key'], plan['values'])
        require(proposed == plan['doctors'], 'تغير اقتراح الأطباء؛ أعد المراجعة')
        raw = encode_doctors(proposed)
        digest = fingerprint(parse_doctors(raw))
        receipt_key = fingerprint(dict(operation='doctors', registry=doctor_pointer(current['state']), body=digest))
        self.workspace.receipt_start(receipt_key, 'doctors', self.client.config['chatId'],
                                     self.client.config.get('doctorsTopicId'), self.client._actor, snapshot=digest)
        uploaded = self.client.telegram.upload(raw, 'doctors_registry.csv', self.client.config.get('doctorsTopicId'), 'سجل الأطباء')
        latest = self.fetch()  # Recheck role, linkage and both pointers before changing the index.
        require(unchanged(latest), 'وصل نشر أثناء رفع الأطباء؛ لم يُحدّث الفهرس. اجلب وراجع قبل الإعادة')
        state = copy.deepcopy(latest['state'])
        state.update(doctorsFileId=uploaded['document']['file_id'], doctorsMessageId=uploaded['message_id'],
                     doctorsUpdatedAt=int(uploaded['date'])*1000)
        self.client.telegram.write_state(latest['index_id'], state)
        confirmed = self.client.fetch()
        require(confirmed['index_id'] == latest['index_id'] and doctor_pointer(confirmed['state']) == doctor_pointer(state) and
                fingerprint(confirmed['doctors']) == digest, 'لم يتأكد نشر سجل الأطباء؛ افحص تليجرام قبل الإعادة')
        # Update only the registry cache/cursor; retain the patient baseline and private draft.
        baseline = copy.deepcopy(self.client.baseline)
        if baseline is not None:
            baseline['doctors'] = confirmed['doctors']
            for field in ('doctorsFileId', 'doctorsMessageId', 'doctorsUpdatedAt'):
                baseline['state'][field] = state[field]
        try:
            self.workspace.commit(baseline=baseline)
            self.workspace.receipt_finish(uploaded)
        except Exception:
            require(False, 'نُشر سجل الأطباء؛ تعذر حفظ التأكيد محلياً. لا تكرر؛ أعد الاتصال وافحص سجل العمليات')
        self.client.baseline, self.client.doctors = baseline, confirmed['doctors']
        return confirmed
