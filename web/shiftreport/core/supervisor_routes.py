"""Current Android supervisor routing; independent of general-report topics."""
from .protocol_common import require


def validate_route(doctor):
    group = doctor.get('destination')
    topic = doctor.get('supervisorTopicId')
    supervisor = doctor.get('clinicalRole') == 'SUPERVISOR'
    require(group is None or supervisor and type(group) is int and -(2**63) <= group < 0,
            'معرّف مجموعة المشرف يجب أن يكون عدداً سالباً ولمشرف فقط')
    require(topic is None or supervisor and type(topic) is int and 0 < topic < 2**63,
            'معرّف موضوع المشرف يجب أن يكون عدداً موجباً واحداً ولمشرف فقط')
    return doctor


def resolve_route(config, doctor):
    validate_route(doctor)
    group, topic = doctor.get('destination'), doctor.get('supervisorTopicId')
    require(doctor.get('clinicalRole') == 'SUPERVISOR' and (group is not None or topic is not None),
            'وجهة المشرف غير متاحة؛ حدّد مجموعة أو موضوعاً أولاً')
    group = group if group is not None else config.get('residentsChatId') or config.get('chatId')
    require(type(group) is int and -(2**63) <= group < 0, 'مجموعة المقيمين غير صالحة')
    return group, topic
