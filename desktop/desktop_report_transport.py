"""Report transport using the existing Client session and reviewed snapshot."""
from __future__ import annotations
from desktop_protocol_common import require, current_date, pointer, doctor_pointer


def send_pdf(owner, raw, shift, supervisor, reviewed_pointer, reviewed_registry, before_send):
    from desktop_reports import report_destination
    require(raw.startswith(b'%PDF-'), 'مطلوب PDF معاين')
    current = owner.fetch()
    owner.authorize(current['doctors'])
    require(pointer(current['state']) == reviewed_pointer and doctor_pointer(current['state']) == reviewed_registry, 'تغيرت نسخة المرضى أو الأطباء؛ أعد المعاينة')
    require(shift['meta']['shiftDate'] == current_date(owner.zone), 'لا ترسل تقرير مناوبة محفوظة')
    chat, topic = report_destination(owner, current['doctors'], supervisor)
    before_send(chat, topic)
    return owner.telegram.upload(raw, 'ShiftReport_' + shift['meta']['shiftDate'] + '.pdf', topic,
                                owner.config['hospitalName'] + ' — ' + shift['meta']['shiftDate'], chat, pdf=True)


def send_text(owner, text, shift, supervisor, reviewed_pointer, reviewed_registry, before_send):
    from desktop_reports import report_destination, report_text, text_chunks
    def destination():
        current = owner.fetch()
        owner.authorize(current['doctors'])
        require(pointer(current['state']) == reviewed_pointer and doctor_pointer(current['state']) == reviewed_registry,
                'تغيرت نسخة المرضى أو الأطباء؛ أعد المعاينة')
        require(shift['meta']['shiftDate'] == current_date(owner.zone), 'لا ترسل تقرير مناوبة محفوظة')
        owner.doctors = current['doctors']
        require(text == report_text(owner, shift, supervisor), 'تغير النص بعد المعاينة')
        return report_destination(owner, current['doctors'], supervisor)
    chat, topic = destination()
    before_send(chat, topic)  # One UNKNOWN receipt covers all parts, including partial delivery.
    if owner.config.get('telegramDataKey'):
        return owner.telegram.upload(text.encode('utf-8'), 'ShiftReport_'+shift['meta']['shiftDate']+'.txt', topic, chat=chat)
    parts = text_chunks(text)
    result = None
    for index, part in enumerate(parts, 1):
        destination()  # A mid-send failure retains UNKNOWN; never silently retry a partial report.
        params = dict(chat_id=chat, text='({}/{})\n'.format(index, len(parts))+part, disable_notification='true')
        if topic: params['message_thread_id'] = topic
        result = owner.telegram.call('sendMessage', params)
        require(result.get('message_id'), 'لم يرجع تليجرام معرّف جزء التقرير؛ افحص التسليم قبل الإعادة')
    return result

