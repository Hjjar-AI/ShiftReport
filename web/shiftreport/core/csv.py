"""Portable Android CSV codecs; registry changes use Administration review/publication."""
from __future__ import annotations
import copy
import csv
import io
import re
from datetime import datetime, timezone
from pathlib import Path
from .protocol import encode_bundle, require
from .doctors import profile, rank, permanent, encode_doctors

DOCTOR_COLUMNS = ('schema_version,id,first_name,last_name,full_name,gender,clinical_role,'
                  'supervisor_group_chat_id,telegram_id,telegram_username,custom_title,rank,'
                  'is_permanent_admin,extra_options,updated_at,deleted_at').split(',')


def patient_csv(shift):
    # A single CsvCodec document, without the Telegram bundle envelope.
    value=copy.deepcopy(shift)
    defaults=dict(sortSpec='{"levels":[]}',shiftRevision='0',snapshotId='',baseSnapshotId='',deviceId='')
    for key,default in defaults.items():value['meta'].setdefault(key,default)
    return encode_bundle([value]).split(b'\n',1)[1]


def doctor_csv(doctors):
    stream=io.StringIO(newline='');writer=csv.writer(stream,lineterminator='\r\n')
    writer.writerow(DOCTOR_COLUMNS)
    for key,doctor in doctors.items():
        name=doctor['name'];plain=name[2:].strip() if name.startswith('د.') else name
        parts=['',''] if re.fullmatch(r'د\. بلا اسم [0-9a-fA-F]{8}',name) else plain.split(' ',1)
        writer.writerow(['1',key,parts[0],parts[1] if len(parts)>1 else '',name,doctor['gender'],
                         doctor['clinicalRole'],doctor.get('destination') or '',doctor.get('telegram_id') or '',
                         '',doctor.get('title',''),rank(doctor),str(permanent(doctor)).lower(),
                         'teamSupervisor:'+doctor['teamSupervisorId'] if doctor.get('teamSupervisorId') else '',
                         datetime.now(timezone.utc).isoformat(),''])
    return stream.getvalue().encode('utf-8-sig')


def decode_doctors(raw):
    require(len(raw)<=4*1024*1024,'ملف الأطباء كبير جداً')
    try:
        reader=csv.DictReader(io.StringIO(raw.decode('utf-8-sig'),newline=''))
        require(reader.fieldnames is not None and set(DOCTOR_COLUMNS).issubset(reader.fieldnames) and
                len(set(reader.fieldnames))==len(reader.fieldnames),'أعمدة CSV الأطباء ناقصة أو مكررة')
        rows=[]
        for source in reader:
            require(None not in source and all(source.get(key) is not None for key in DOCTOR_COLUMNS),'سطر CSV غير مكتمل')
            require(source['schema_version']=='1','مطلوب CSV الأطباء الحالي')
            identity=source['id'].strip();require(identity and not any(c in identity for c in ',|@\r\n"'),'معرف طبيب غير صالح')
            gender={'M':'M','MALE':'M','F':'F','FEMALE':'F'}.get(source['gender'].strip().upper())
            values=dict(first=source['first_name'],last=source['last_name'],title=source['custom_title'],
                        gender=gender,clinicalRole=source['clinical_role'].strip().upper(),
                        telegram_id=source['telegram_id'],destination=source['supervisor_group_chat_id'])
            candidate=profile(values, {'uid':identity})
            candidate['uid']=identity
            full=source['full_name'].strip()
            require(not full or full==candidate['name'],'الاسم الكامل لا يطابق الاسم والكنية')
            number=int(source['rank'] or '0');flag=source['is_permanent_admin'].strip().lower()
            require(flag in {'true','1','yes','false','0','no',''},'قيمة مدير دائم غير صالحة')
            is_permanent=flag in {'true','1','yes'}
            require(0<=number<=2147483647 and (not is_permanent or number>0),'رتبة مدير غير صالحة')
            # PIN/syncAlias are intentionally never accepted from a portable file.
            extras=[item.strip() for item in source['extra_options'].split(';') if item.strip()]
            require(all(item.startswith(('pin:','syncAlias:','teamSupervisor:')) for item in extras),
                    'خيارات إضافية غير مدعومة في سجل تليجرام؛ لن تُحذف ضمنياً')
            teams=[item[len('teamSupervisor:'):] for item in extras if item.startswith('teamSupervisor:')]
            require(len(teams)<=1 and all(teams),'أكثر من مشرف لفريق المقيم أو معرّف مشرف فارغ')
            candidate['teamSupervisorId']=teams[0] if teams else ''
            candidate.update({'#':str(number),'options':('A' if is_permanent else 'a')+str(number) if number else ''})
            candidate['_deleted']=bool(source['deleted_at'].strip())
            for field in ('updated_at','deleted_at'):
                if source[field].strip():datetime.fromisoformat(source[field].strip().replace('Z','+00:00'))
            rows.append(candidate)
            require(len(rows)<=500,'الحد الأقصى 500 طبيب')
        require(rows and len({row['uid'] for row in rows})==len(rows),'ملف فارغ أو معرّفات أطباء مكررة')
        return rows
    except (ValueError,TypeError,UnicodeError,csv.Error):
        require(False,'ملف CSV الأطباء غير صالح')


def import_registry(service, snapshot, incoming, actor):
    doctors=copy.deepcopy(snapshot['doctors']);touched=set();aliases={};candidates={}
    for source in incoming:
        candidate=copy.deepcopy(source);deleted=candidate.pop('_deleted',False)
        matches=[key for key,old in doctors.items() if key==candidate['uid'] or
                 candidate.get('telegram_id') and old.get('telegram_id')==candidate['telegram_id'] or
                 old['name'].casefold()==candidate['name'].casefold()]
        require(len(matches)<=1,'صف مستورد يطابق أكثر من طبيب؛ صحح الملف')
        key=matches[0] if matches else candidate['uid'];old=doctors.get(key)
        require(key not in touched,'أكثر من صف يطابق الطبيب نفسه');touched.add(key)
        if deleted and old is None:continue
        aliases[source['uid']]=key
        candidate['uid']=key
        candidates[key]=candidate
        if old and (permanent(old) or key==service.client._actor):
            candidate.update({'#':old['#'],'options':old['options']})
        if old and old['clinicalRole']!=candidate['clinicalRole']:service._references(key,snapshot)
        changed=old is None and rank(candidate)>0 or old is not None and (rank(old),permanent(old))!=(rank(candidate),permanent(candidate))
        if changed and not permanent(actor):
            require((not old or not permanent(old) and rank(old)<rank(actor)) and
                    not permanent(candidate) and rank(candidate)<rank(actor),'لا يمكن استيراد صلاحيات مساوية للمنفذ أو أعلى منه')
        doctors[key]=candidate
    for key,candidate in candidates.items():
        supervisor=candidate.get('teamSupervisorId') or ''
        doctors[key]['teamSupervisorId']=aliases.get(supervisor,supervisor)
    encode_doctors(doctors)
    return doctors


def import_summary(before, after, incoming):
    from .model import doctor_label
    incoming_keys=set()
    for row in incoming:
        incoming_keys.update(key for key,doctor in after.items() if key==row['uid'] or
                             row.get('telegram_id') and doctor.get('telegram_id')==row['telegram_id'] or
                             doctor['name'].casefold()==row['name'].casefold())
    groups=[('إضافات', [key for key in after if key not in before]),
            ('تعديلات', [key for key in after if key in before and after[key]!=before[key]]),
            ('صفوف مستوردة دون تغيير', [key for key in after if key in incoming_keys and key in before and after[key]==before[key]]),
            ('سجلات غائبة عن الملف محفوظة كما هي', [key for key in before if key not in incoming_keys])]
    return '\n\n'.join(label+' ('+str(len(keys))+'):\n'+('\n'.join(doctor_label(after,key) for key in keys) or 'لا يوجد') for label,keys in groups)


