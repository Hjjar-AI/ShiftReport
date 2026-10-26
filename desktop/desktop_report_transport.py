"""Report transport using the existing Client session and reviewed snapshot."""
from __future__ import annotations
from desktop_protocol_common import require, current_date, pointer, doctor_pointer, UncertainDelivery
from desktop_report_labels import report_filename, report_caption


def send_pdf(owner, raw, shift, supervisor, reviewed_pointer, reviewed_registry, before_send, report_day=None):
    report_day = report_day or current_date(owner.zone)
    from desktop_reports import report_destination
    require(raw.startswith(b'%PDF-'), 'مطلوب PDF معاين')
    current = owner.fetch()
    owner.authorize(current['doctors'])
    require(pointer(current['state']) == reviewed_pointer and doctor_pointer(current['state']) == reviewed_registry, 'تغيرت نسخة المرضى أو الأطباء؛ أعد المعاينة')
    chat, topic = report_destination(owner, current['doctors'], supervisor)
    from desktop_reports import text_chunks
    titles=text_chunks(report_caption(shift,current['doctors'],report_day,supervisor),limit=1024)
    before_send(chat, topic)
    result=owner.telegram.upload(raw, report_filename(report_day,supervisor,
        current['doctors'].get(supervisor,{}).get('name')), topic,
        titles[0], chat, pdf=True)
    try:
        for title in titles[1:]:
            params=dict(chat_id=chat,text=title,reply_to_message_id=result['message_id'],
                        allow_sending_without_reply='true',disable_notification='true')
            if topic:params['message_thread_id']=topic
            message=owner.telegram.call('sendMessage',params)
            require(message.get('message_id'),'لم يتأكد وصول تكملة أسماء أطباء المناوبة')
    except Exception:
        raise UncertainDelivery('وصل ملف التقرير؛ لم يتأكد اكتمال عنوان الأطباء. افحص تليجرام قبل إعادة الإرسال') from None
    return result


def send_text(owner, text, shift, supervisor, reviewed_pointer, reviewed_registry, before_send, report_day=None):
    report_day = report_day or current_date(owner.zone)
    from desktop_reports import report_destination, report_text, text_chunks
    def destination():
        current = owner.fetch()
        owner.authorize(current['doctors'])
        require(pointer(current['state']) == reviewed_pointer and doctor_pointer(current['state']) == reviewed_registry,
                'تغيرت نسخة المرضى أو الأطباء؛ أعد المعاينة')
        owner.doctors = current['doctors']
        require(text == report_text(owner, shift, supervisor, report_day), 'تغير النص بعد المعاينة')
        return report_destination(owner, current['doctors'], supervisor)
    chat, topic = destination()
    before_send(chat, topic)  # One UNKNOWN receipt covers all parts, including partial delivery.
    if owner.config.get('telegramDataKey'):
        return owner.telegram.upload(text.encode('utf-8'), report_filename(report_day,supervisor,owner.doctors.get(supervisor,{}).get('name'),extension='.txt'), topic, chat=chat)
    parts = text_chunks(text)
    result = None
    for index, part in enumerate(parts, 1):
        destination()  # A mid-send failure retains UNKNOWN; never silently retry a partial report.
        params = dict(chat_id=chat, text='({}/{})\n'.format(index, len(parts))+part, disable_notification='true')
        if topic: params['message_thread_id'] = topic
        result = owner.telegram.call('sendMessage', params)
        require(result.get('message_id'), 'لم يرجع تليجرام معرّف جزء التقرير؛ افحص التسليم قبل الإعادة')
    return result

