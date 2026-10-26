"""Explicit, shared one-supervisor membership for residents."""
from desktop_protocol import require


def validate_teams(doctors):
    for key,doctor in doctors.items():
        supervisor=doctor.get('teamSupervisorId') or ''
        require(not supervisor or doctor['clinicalRole']=='RESIDENT' and supervisor!=key and
                supervisor in doctors and doctors[supervisor]['clinicalRole']=='SUPERVISOR',
                'اختر مشرفاً نشطاً لكل مقيم مرتبط بفريق؛ لا يمكن حذف مشرف فريق أو تغيير تصنيفه قبل نقل المقيمين')
    return doctors


def resident_ids(doctors, supervisor):
    return [key for key,doctor in doctors.items() if doctor['clinicalRole']=='RESIDENT' and
            supervisor and doctor.get('teamSupervisorId')==supervisor]


def is_cross_team_cover(patient, doctors):
    resident=doctors.get(patient.get('responsibleResidentId'),{})
    own=resident.get('teamSupervisorId')
    target=patient.get('responsibleSupervisorId')
    return bool(own and target and own!=target)
