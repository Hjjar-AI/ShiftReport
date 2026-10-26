"""Shared reviewed delivery lifecycle for one report or a supervisor batch.

Callers own previews and final confirmation. This coordinator preserves exact
payloads, publishes at most once and records uncertainty before each transport.
"""
import copy
from desktop_protocol import BridgeError, doctor_pointer, fingerprint, pointer, require, current_date
from desktop_reports import prepare_delivery, report_content, report_destination


class ReportDelivery:
    def __init__(self, owner, local):
        self.owner = owner
        self.report_day = current_date(owner.client.zone)
        self.source_hash = fingerprint(local)
        self.plan, self.reviewed, self.needs_publish = owner.io(
            lambda: prepare_delivery(owner.client, local))
        self.require_unchanged()
        self.snapshot = self.plan.remote
        owner.client.doctors = copy.deepcopy(self.snapshot['doctors'])
        owner.report_attempt.publication = 'NOT_STARTED' if self.needs_publish else 'CONFIRMED'
        owner.show_report_attempt()

    def require_unchanged(self):
        require(fingerprint(self.owner.store.read()) == self.source_hash, 'تغيرت المسودة؛ أعد مراجعة التقارير')

    def check_targets(self, supervisors):
        for supervisor in supervisors:
            chat, topic = report_destination(self.owner.client, self.snapshot['doctors'], supervisor)
            self.owner.io(lambda: self.owner.store.check_report_delivery(self.reviewed['meta']['shiftId'], chat, topic))

    def send(self, payloads, confirm_repeat, outcome=lambda supervisor, status: None, after_delivery=None):
        """Payloads are (supervisor, format, exact reviewed bytes/text), in send order."""
        owner = self.owner
        payloads = tuple(payloads)
        require(payloads and all(format_name in {'pdf', 'text'} for _, format_name, _ in payloads),
                'اختر تقارير بصيغة صالحة قبل النشر')
        self.require_unchanged()
        self.check_targets([supervisor for supervisor, _, _ in payloads])
        if self.needs_publish:
            owner.report_attempt.publication = 'IN_PROGRESS'
            owner.report_attempt.snapshot = self.plan.shift['meta']['snapshotId']
            owner.show_report_attempt()
            owner.client.approve(self.plan)
            owner.plan, owner.raw_draft = self.plan, self.source_hash
            owner.publish(notify=False)
            owner.report_attempt.publication = 'CONFIRMED'
            owner.show_report_attempt()
            self.snapshot = owner.client.baseline
            self.reviewed = owner.store.read()
            require(fingerprint(report_content(self.reviewed)) == fingerprint(report_content(self.plan.shift)),
                    'تغيرت البيانات المنشورة؛ أعد معاينة التقارير')
        sent = 0
        for supervisor, format_name, payload in payloads:
            identity = dict(operation='report', project=owner.client.project_id,
                            pointer=pointer(self.snapshot['state']), shift=self.reviewed['meta']['shiftId'],
                            supervisor=supervisor)
            # Preserve receipt identity for existing PDF workspaces.
            if format_name == 'text': identity['format'] = 'text'
            key = fingerprint(identity)
            if owner.store.receipts(status='UNKNOWN', key=key):
                owner.report_attempt.delivery = 'UNCERTAIN'
                owner.show_report_attempt()
            require(not owner.store.receipts(status='UNKNOWN', key=key),
                    'نتيجة إرسال سابقة غير مؤكدة؛ افحص سجل العمليات قبل الإعادة')
            chat, topic = report_destination(owner.client, self.snapshot['doctors'], supervisor)
            owner.io(lambda: owner.store.check_report_delivery(self.reviewed['meta']['shiftId'], chat, topic))
            repeat = False
            if any(row[2] in {'SENT', 'CHECKED_DO_NOT_REPEAT'} for _, row in owner.store.receipts(key=key)):
                repeat = confirm_repeat(supervisor)
                if not repeat:
                    outcome(supervisor, 'تُرك دون إعادة')
                    continue

            def before(chat, topic):
                owner.store.receipt_start(key, 'report', chat, topic, owner.client._actor,
                                          repeat=repeat, report_shift=self.reviewed['meta']['shiftId'])
                outcome(supervisor, 'غير مؤكد')
                owner.report_attempt.delivery = 'IN_PROGRESS'
                owner.show_report_attempt()

            send = owner.client.send_pdf if format_name == 'pdf' else owner.client.send_text
            result = owner.io(lambda: send(payload, self.reviewed, supervisor, pointer(self.snapshot['state']),
                                          doctor_pointer(self.snapshot['state']), before, report_day=self.report_day))
            owner.report_attempt.delivery = 'CONFIRMED'
            owner.show_report_attempt()
            outcome(supervisor, 'تليجرام أكد التسليم')
            owner.status.set('تليجرام أكد تسليم التقرير. لا تعاود الإرسال إذا تعذر حفظ سجل المسودة.')
            try:
                owner.io(lambda: owner.store.receipt_finish(result))
            except Exception:
                raise BridgeError('تم تسليم التقرير؛ تعذر حفظ إيصال المسودة. لا تعد إرسال التقرير') from None
            sent += 1
        if after_delivery is not None and sent == len(payloads):
            try:after_delivery()
            except Exception:
                raise BridgeError('تم تأكيد إرسال التقارير إلى تليجرام، لكن لم يكتمل الحفظ المحلي. لا تُعد الإرسال بسبب تعذر الحفظ') from None
        return sent
