"""Disposable in-memory workspace; no Telegram transport or clinical persistence."""
from __future__ import annotations
import copy
import uuid
from desktop_protocol import current_date, require
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
        for index,name in enumerate(['أرز','بلوط','صنوبر','زيتون','صفصاف','نخيل']):
            role='RESIDENT' if index<3 else 'SUPERVISOR'
            key='demo-doctor-'+str(index);self.doctors[key]=dict(uid=key,name='د. '+name,gender='M' if index%2==0 else 'F',clinicalRole=role,
                                                       telegram_id=None,destination=None,title='',options='',**{'#':'0'})

    def blocked(self,*args,**kwargs):
        require(False,'الوضع التجريبي محلي في الذاكرة؛ الاتصال والنشر والإدارة غير متاحة')
    fetch=begin_login=check_login=authorize=require_admin=require_local_identity=plan=prepare=approve=publish=send_pdf=send_text=blocked

    def require_local_admin(self):
        # Dummy-only local PDF authority; never enroll or contact a real account.
        return next(doctor for doctor in self.doctors.values() if doctor['clinicalRole']=='SUPERVISOR')


class DemoWorkspace(Workspace):
    def __init__(self, client, appearance=None):
        self.zone=client.zone;self.project='demo';self.locked=False;self._receipt=None
        self.client=client;self.local_actor=None
        self.state={'project':'demo','baseline':None,'draft':None,'receipts':[]}
        self.reset(client)
        if appearance:self.state['appearance']=copy.deepcopy(appearance)

    def commit(self, **changes):
        # Retain old records without creating new audit/completion evidence.
        changes.pop("clinicalActivity", None)
        changes.pop("localCompletions", None)
        self.state=dict(copy.deepcopy(self.state),**copy.deepcopy(changes))

    def reset(self,client):
        supervisors=[key for key,d in client.doctors.items() if d['clinicalRole']=='SUPERVISOR']
        residents=[key for key,d in client.doctors.items() if d['clinicalRole']=='RESIDENT']
        for index,key in enumerate(residents):client.doctors[key]['teamSupervisorId']=supervisors[index%len(supervisors)]
        shift=dict(meta=dict(schemaVersion='8',shiftDate=current_date(client.zone),shiftId=str(uuid.uuid4()),
                             doctors=','.join(client.doctors),sortSpec='{"levels":[]}'),patients=[])
        from desktop_demo_sample import sample_shift
        for index,sample in enumerate(sample_shift()['patients']):
            row=copy.deepcopy(sample)
            row.update(externalId='demo-patient-'+str(index),
                       responsibleSupervisorId=supervisors[index%3],responsibleResidentId=residents[index%3])
            shift['patients'].append(row)
        from desktop_sort import ordered
        ordered(shift,client.doctors,client.zone)
        cosmetic={key:value for key,value in self.state.get('appearance',{}).items() if key in {'mode','accent'}}
        self.state={'project':'demo','baseline':None,'draft':shift,'receipts':[],'appearance':cosmetic}
        self.published=copy.deepcopy(shift)

    def blocked(self,*args,**kwargs):require(False,'البيانات التجريبية تبقى في الذاكرة؛ يمكن حفظ PDF تجريبي فقط')
    export=import_draft=write=receipt_start=receipt_finish=blocked


def simulate_transfer(owner,kind):
    """Use dummy snapshots only; the DemoClient's network methods stay blocked."""
    from desktop_widgets import messagebox
    from desktop_model import shift_text
    owner.ready(False)
    require(owner.demo and isinstance(owner.store,DemoWorkspace),'المحاكاة متاحة للعرض التجريبي فقط')
    source=owner.store.read()
    if kind=='fetch':
        if not messagebox.askyesno('DEMO — جلب البيانات','استبدال البيانات التجريبية بآخر نسخة راجعتها في الذاكرة؟',default='no'):return
        owner.store.commit(draft=copy.deepcopy(owner.store.published))
    else:
        if not owner.text_dialog('DEMO — مراجعة البيانات',shift_text(source,owner.client.doctors,owner.client.zone),approve=True):return
        if not messagebox.askyesno('DEMO — تأكيد المحاكاة','تأكيد النسخة المعروضة في الذاكرة؟ لا يوجد اتصال أو إرسال إلى تليجرام.',default='no'):return
        owner.store.published=copy.deepcopy(source)
        if kind=='report':
            # The ordinary export route still requires review of an explicitly
            # DEMO/DRAFT-marked PDF and copies its exact bytes if requested.
            from desktop_export_review import run_export
            run_export(owner)
    owner.notify_success('DEMO — تمت المحاكاة في الذاكرة فقط.')
    owner.render()
