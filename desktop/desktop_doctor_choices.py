"""Presentation groups for searchable doctor choices; eligibility stays with callers."""
from desktop_model import doctor_label


def doctor_groups(doctors, keys=None):
    groups = {}
    candidates = list(doctors) if keys is None else list(keys)
    def group(key):
        doctor = doctors.get(key, {})
        if key not in doctors:return 'اختيار محفوظ غير متاح'
        if doctor.get('clinicalRole') == 'SUPERVISOR':return 'المشرفون'
        supervisor = doctor.get('teamSupervisorId')
        return 'فريق ' + doctor_label(doctors, supervisor) if supervisor else 'المقيمون · دون فريق'
    for key in sorted(candidates, key=lambda key: (doctors.get(key, {}).get('clinicalRole') != 'SUPERVISOR', group(key), doctor_label(doctors, key))):
        groups.setdefault(group(key), []).append(key)
    return groups
