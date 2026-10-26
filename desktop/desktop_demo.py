"""Disposable in-memory workspace; no Telegram transport or persistent settings."""
from __future__ import annotations
import copy
import uuid
from desktop_model import new_patient
from desktop_protocol import current_date, packed, require
from desktop_store import Workspace


class DemoClient:
    demo=True
    admin_only=False
    _actor=None
    _expires=0
    baseline=None
    project_id='demo'
    zone='Asia/Damascus'
    config={'hospitalName':'بيانات تجريبية','telegramDataKey':''}

    def __init__(self):
        self.doctors={}
        for name,role in [('د. الأرز الشامي','SUPERVISOR'),('د. الزيتون الأخضر','SUPERVISOR'),
                          ('د. الصنوبر الطويل','RESIDENT'),('د. السرو الدائم','RESIDENT')]:
            key=str(uuid.uuid4());self.doctors[key]=dict(uid=key,name=name,gender='M',clinicalRole=role,
                                                       telegram_id=None,destination=None,title='',options='',**{'#':'0'})

    def blocked(self,*args,**kwargs):
        require(False,'الوضع التجريبي محلي في الذاكرة؛ الاتصال والنشر والإدارة غير متاحة')
    fetch=begin_login=check_login=authorize=require_admin=require_local_identity=plan=prepare=approve=publish=send_pdf=send_text=blocked


class DemoWorkspace(Workspace):
    def __init__(self, client):
        self.zone=client.zone;self.project='demo';self.locked=False;self._receipt=None
        self.client=client;self.local_actor=None
        self.state={'project':'demo','baseline':None,'draft':None,'receipts':[]}
        self.reset(client)

    def commit(self, **changes):
        # Retain old records without creating new audit/completion evidence.
        changes.pop("clinicalActivity", None)
        changes.pop("localCompletions", None)
        self.state=dict(copy.deepcopy(self.state),**copy.deepcopy(changes))

    def reset(self,client):
        supervisors=[key for key,d in client.doctors.items() if d['clinicalRole']=='SUPERVISOR']
        residents=[key for key,d in client.doctors.items() if d['clinicalRole']=='RESIDENT']
        for index,key in enumerate(residents):client.doctors[key]['teamSupervisorId']=supervisors[index%len(supervisors)]
        shift=dict(meta=dict(schemaVersion='7',shiftDate=current_date(client.zone),shiftId=str(uuid.uuid4()),
                             doctors=','.join(client.doctors),sortSpec='{"levels":[]}'),patients=[])
        for i,name in enumerate(['شاي بالنعناع','قهوة عربية','كاكاو ساخن','يانسون دافئ','زنجبيل بالعسل','قرفة بالحليب']):
            row=new_patient(client.zone)
            row.update(name=name,admitNum=str(i+1),birthDate='1990',gender='F' if i%2 else 'M',
                       initialDiagnosis='مثال توضيحي للتشخيص',treatmentPlan='مثال توضيحي لخطة العلاج',
                       followUp='متابعة تجريبية',labs='تحاليل تجريبية',responsibleSupervisorId=supervisors[i%2],
                       responsibleResidentId=residents[i%2],isPriority='Y' if i==0 else 'N')
            row['tasksJson']=packed([dict(id=str(uuid.uuid4()),description='مهمة تجريبية',done=False,priority='NORMAL',
                                         ownerDoctorId=None,ownerName=None,dueAtEpochMillis=None,dueShiftDate=None,
                                         completedByDoctorId=None,completedByName=None,completedAtEpochMillis=None)])
            shift['patients'].append(row)
        self.state={'project':'demo','baseline':None,'draft':shift,'receipts':[]}

    def blocked(self,*args,**kwargs):require(False,'البيانات التجريبية تبقى في الذاكرة؛ يمكن حفظ PDF تجريبي فقط')
    export=import_draft=write=receipt_start=receipt_finish=blocked
