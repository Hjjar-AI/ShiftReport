"""Exact-artifact report previews, local copies and reviewed Telegram delivery."""
import copy
import hashlib
import io
import secrets
import threading
import time
from flask import g, redirect, render_template, request, send_file, url_for
from .features import features, home, download
from .core.protocol import require, fingerprint, pointer, doctor_pointer, current_date, packed
from .core.reports import ReportAttempt, publication_review, report_text, report_destination

PDF_LOCK=threading.RLock()


def publish_plan(w,plan,expected):
    w.ready(online=True)
    require(fingerprint(w.store.read())==expected,'تغيرت البيانات بعد المراجعة؛ أعدها')
    plan.force_local=True
    w.client.approve(plan)
    key=fingerprint(dict(operation='publish',project=w.client.project_id,snapshot=plan.shift['meta']['snapshotId']))
    def before():
        w.store.receipt_start(key,'publish',w.client.config['chatId'],w.client.config.get('csvTopicId'),w.actor,snapshot=plan.shift['meta']['snapshotId'])
    message=w.client.publish(plan,before)
    w.store.commit(draft=copy.deepcopy(plan.shift),baseline=copy.deepcopy(w.client.baseline))
    w.store.receipt_finish(message)


class DeliveryOwner:
    def __init__(self,w):
        self.w=w
        self.client,self.store=w.client,w.store
        self.report_attempt=ReportAttempt()
        self.status=self
        self.plan=None
        self.raw_draft=None
        self.messages=[]

    def io(self,action):
        return action()

    def set(self,message):
        self.messages.append(message)

    def show_report_attempt(self):
        self.w.attempt=self.report_attempt

    def publish(self,notify=False):
        publish_plan(self.w,self.plan,self.raw_draft)

    def bind_artifact(self,supervisor,format_name,payload,chat,topic):
        index=self.store._receipt
        bindings=copy.deepcopy(self.store.state.get('reportArtifacts',{}))
        raw=payload if isinstance(payload,bytes) else payload.encode('utf-8')
        bindings[str(index)]=dict(sha256=hashlib.sha256(raw).hexdigest(),actor=self.w.actor,
            telegram=self.client._verified_telegram,project=self.client.project_id,chat=chat,topic=topic,
            supervisor=supervisor,format=format_name,day=self.delivery.report_day,
            pointer=list(pointer(self.delivery.snapshot['state'])),registry=list(doctor_pointer(self.delivery.snapshot['state'])))
        self.store.commit(reportArtifacts=bindings)


def artifact(w,token):
    review=w.reviews.get(token)
    require(review is not None and review['kind']=='report' and review['actor']==w.actor and review['project']==w.client.project_id,
            'معاينة التقرير غير متاحة')
    return review


def render_pdf(w,shift,target,options,assignments=False,day=None):
    from .core.pdf import render_report, render_assignments
    with PDF_LOCK:
        function=render_assignments if assignments else render_report
        path=function(w.client,shift,options=options,report_day=day) if assignments else function(w.client,shift,target,options=options,report_day=day)
        try:
            return path.read_bytes()
        finally:
            path.unlink()
            path.parent.rmdir()


@features.route('/reports',methods=['GET','POST'])
def reports():
    w=g.workspace
    w.ready()
    if request.method=='POST':
        action=request.form.get('action','export')
        assignments=action=='assignments'
        selected=request.form.getlist('doctor')
        require((selected or assignments) and len(set(selected))==len(selected) and all(k in w.client.doctors for k in selected),'اختر أطباء المناوبة أولاً')
        shift=w.store.read()
        shift['meta']['doctors']=','.join(selected)
        w.store.commit(draft=shift)
        targets=[None if key=='general' else key for key in request.form.getlist('target')]
        require(targets and len(set(targets))==len(targets),'اختر وجهة واحدة على الأقل')
        require(all(t is None or t in w.client.doctors and w.client.doctors[t]['clinicalRole']=='SUPERVISOR' for t in targets),'وجهة غير صالحة')
        format_name=request.form.get('format','pdf')
        require(format_name in ('pdf','text'),'صيغة غير صالحة')
        if assignments:
            w.ready(admin=True)
            targets=[None]
        sending=action=='send' and not w.demo
        require(action in ('export','send','assignments'),'عملية تقرير غير صالحة')
        if sending:
            w.ready(online=True)
            from .core.delivery import ReportDelivery
            owner=DeliveryOwner(w)
            delivery=ReportDelivery(owner,shift)
            owner.delivery=delivery
            # UNKNOWN does not prevent reviewing the exact retained artifact; it blocks sending.
            reviewed=delivery.reviewed
            day=delivery.report_day
        else:
            owner=delivery=None
            reviewed=copy.deepcopy(shift)
            day=current_date(w.client.zone)
        from .core.pdf_content import normalize_options
        options=normalize_options(dict(layout=request.form.get('layout','cards'),app_theme=request.form.get('app_theme','VANILLA'),local_draft=not sending and not assignments))
        w.print_accent=options['app_theme']
        payloads=[]
        for target in targets:
            raw=render_pdf(w,reviewed,target,options,assignments,day) if format_name=='pdf' or assignments else report_text(w.client,reviewed,target,day)
            payloads.append(dict(target=target,format='pdf' if assignments else format_name,raw=raw,
                                 sha=hashlib.sha256(raw if isinstance(raw,bytes) else raw.encode()).hexdigest(),viewed=False))
        source=fingerprint(w.store.read())
        summary=publication_review(delivery.plan,w.client.zone) if delivery and delivery.needs_publish else 'راجع التقرير كاملاً قبل الحفظ أو التأكيد النهائي.'
        token=w.review('report',dict(payloads=payloads,delivery=delivery,owner=owner,source=source,options=options,
            day=day,sending=sending,assignments=assignments,simulation=action=='send' and w.demo,
            doctors=fingerprint(w.client.doctors)),summary,None)
        return redirect(url_for('features.report_review',token=token),code=303)
    return render_template('reports.html',doctors=w.client.doctors,selected=w.store.read()['meta'].get('doctors','').split(','),page='reports')


@features.route('/reports/review/<token>',methods=['GET','POST'])
def report_review(token):
    w=g.workspace
    w.ready()
    review=artifact(w,token)
    data=review['value']
    if request.method=='POST':
        require(not review['used'],'انتهت محاولة هذه المراجعة؛ افحص سجل التسليم قبل أي إعادة')
        require(request.form.get('confirm')=='1','الموافقة النهائية مطلوبة')
        require(all(p['viewed'] for p in data['payloads']),'افتح كل ملف من المعاينات أولاً')
        require(fingerprint(w.store.read())==data['source'],'تغير المحتوى؛ أعد المعاينة')
        require(fingerprint(w.client.doctors)==data['doctors'] and w.print_accent==data['options']['app_theme'],'تغير الأطباء أو لون التقرير؛ أعد المعاينة')
        if data['sending']:
            w.ready(online=True)
            # Bind resolved destination and exact bytes to the explicitly reviewed artifact.
            delivery=data['delivery']
            targets=[p['target'] for p in data['payloads']]
            delivery.check_targets(targets)
            for p in data['payloads']:
                if any(row[2] in ('SENT','CHECKED_DO_NOT_REPEAT') for _,row in w.store.receipts() if row[0]=='report'):
                    require(request.form.get('repeat')=='1','توجد إيصالات سابقة؛ وافق صراحة على نسخة إضافية بعد المراجعة')
            review['used']=True
            try:
                delivery.plan.force_local=True
                delivery.send([(p['target'],p['format'],p['raw']) for p in data['payloads']],
                              lambda target:request.form.get('repeat')=='1')
            except Exception:
                data['owner'].report_attempt.failed(w.client.baseline)
                data['source']=fingerprint(w.store.read())
                raise
            data['source']=fingerprint(w.store.read())
            w.notice='أكد تليجرام التسليم. الحفظ المحلي ينسخ ملفات المعاينة نفسها.'
        elif data['simulation']:
            w.store.published=copy.deepcopy(w.store.read())
            review['used']=True
            w.notice='DEMO — تمت محاكاة النشر في الذاكرة فقط.'
        return redirect(url_for('features.report_review',token=token),code=303)
    receipts=[dict(index=i,status=row[2]) for i,row in w.store.receipts() if row[0]=='report' and row[2]=='UNKNOWN']
    return render_template('report_review.html',review=review,data=data,token=token,receipts=receipts,page='reports')


@features.get('/reports/artifact/<token>/<int:index>')
def report_artifact(token,index):
    w=g.workspace
    w.ready()
    review=artifact(w,token)
    data=review['value']
    require(0<=index<len(data['payloads']),'الملف غير موجود')
    payload=data['payloads'][index]
    if request.args.get('save')=='1':
        require(payload['viewed'],'افتح الملف قبل حفظه')
        require(fingerprint(w.store.read())==data['source'] and fingerprint(w.client.doctors)==data['doctors'], 'تغير المحتوى؛ أعد المعاينة')
        require(w.print_accent==data['options']['app_theme'],'تغير لون التطبيق؛ أعد المعاينة')
    else:
        payload['viewed']=True
    if data['assignments']:
        w.ready(admin=True)
    if request.args.get('save')=='1' and data['sending']:
        require(review['used'] and data['owner'] is not None and data['owner'].report_attempt.delivery=='CONFIRMED','حفظ النسخة العادية متاح بعد تأكيد التسليم؛ للتصدير المحلي اختر مسودة')
    raw=payload['raw']
    name=('DEMO-' if w.demo else '')+'ShiftReport-'+data['day']+('.pdf' if payload['format']=='pdf' else '.txt')
    return send_file(io.BytesIO(raw if isinstance(raw,bytes) else raw.encode('utf-8')),mimetype='application/pdf' if payload['format']=='pdf' else 'text/plain; charset=utf-8',
                     as_attachment=request.args.get('save')=='1',download_name=name)


@features.route('/reports/copy/<token>/<int:receipt_index>',methods=['GET','POST'])
def report_copy(token,receipt_index):
    w=g.workspace
    require(not w.demo,'لا يوجد إرسال في التجربة')
    w.ready(online=True)
    review=artifact(w,token)
    data=review['value']
    require(data['sending'],'المراجعة لا تخص إرسالاً')
    receipt=w.store.require_receipt_resolution(receipt_index,w.client)
    binding=w.store.state.get('reportArtifacts',{}).get(str(receipt_index))
    require(binding and binding['project']==w.client.project_id and binding['actor']==w.actor and binding['telegram']==w.client._verified_telegram,'النسخة الإضافية تحتاج المرسل الأصلي وهوية الملف نفسها')
    payload=next((p for p in data['payloads'] if p['sha']==binding['sha256'] and p['target']==binding['supervisor'] and p['format']==binding['format']),None)
    require(payload is not None,'الملف الأصلي غير متاح؛ لا يمكن اعتماد PDF أُعيد توليده')
    require(tuple(report_destination(w.client,w.client.doctors,payload['target']))==(binding['chat'],binding['topic']),'تغيرت الوجهة؛ النسخة الإضافية محظورة')
    if request.method=='POST':
        require(request.form.get('confirm')=='1','الموافقة الصريحة على نسخة إضافية مطلوبة')
        require(payload['viewed'],'افتح الملف الأصلي قبل الموافقة على النسخة الإضافية')
        require(w.store.state['receipts'][receipt_index]==receipt,'تغير الإيصال الأصلي')
        permit=secrets.token_hex(24)
        permissions=copy.deepcopy(w.store.state.get('reportCopyPermits',{}))
        permissions[permit]=dict(binding=binding,receipt=receipt_index,used=False)
        w.store.commit(reportCopyPermits=permissions)
        copy_index=[None]
        def before(chat,topic):
            require((chat,topic)==(binding['chat'],binding['topic']),'تغيرت وجهة النسخة')
            permits=copy.deepcopy(w.store.state['reportCopyPermits'])
            require(not permits[permit]['used'],'استُخدم إذن النسخة')
            permits[permit]['used']=True
            rows=copy.deepcopy(w.store.state['receipts'])
            require(rows[receipt_index][2]=='UNKNOWN','حُسم الإيصال أثناء العملية')
            rows.append(['report',fingerprint(dict(copy=permit,sha=binding['sha256'])),'UNKNOWN','',str(chat),str(topic or ''),time.strftime('%Y-%m-%dT%H:%M:%S'),'نسخة إضافية مصرح بها',w.actor,receipt[9]])
            copy_index[0]=len(rows)-1
            w.store.commit(receipts=rows,reportCopyPermits=permits)
        send=w.client.send_pdf if payload['format']=='pdf' else w.client.send_text
        result=send(payload['raw'],data['delivery'].reviewed,payload['target'],tuple(binding['pointer']),tuple(binding['registry']),before,report_day=binding['day'])
        w.store.confirm_indexed_receipt(copy_index[0],result['message_id'])
        w.notice='أكد تليجرام النسخة الإضافية؛ بقي الإيصال الأصلي غير محسوم حتى تفحصه.'
        return redirect(url_for('features.sync'),code=303)
    return render_template('copy_review.html',token=token,index=receipt_index,
                           artifact_index=data['payloads'].index(payload),page='reports')

@features.route('/receipts/<int:receipt_index>/artifact',methods=['GET','POST'])
def recover_artifact(receipt_index):
    from .features import uploaded
    from types import SimpleNamespace
    w=g.workspace
    require(not w.demo,'استرداد ملف الإرسال لمشروع حقيقي فقط')
    w.ready(online=True)
    w.store.require_receipt_resolution(receipt_index,w.client)
    binding=w.store.state.get('reportArtifacts',{}).get(str(receipt_index))
    require(binding and binding['actor']==w.actor and binding['telegram']==w.client._verified_telegram,
            'يتطلب الملف هوية المرسل الأصلي')
    if request.method=='POST':
        raw=uploaded()
        require(hashlib.sha256(raw).hexdigest()==binding['sha256'],'الملف ليس نفس البايتات الأصلية؛ لن تُقبل نسخة أعيد توليدها')
        snapshot=w.client.fetch()
        w.client.authorize(snapshot['doctors'])
        require(list(pointer(snapshot['state']))==binding['pointer'] and list(doctor_pointer(snapshot['state']))==binding['registry'],'تغيرت النسخة المنشورة؛ افحص الإيصال قبل أي إرسال')
        require(len(snapshot['bundle'])==1,'القسم الأصلي غير متاح')
        value=raw if binding['format']=='pdf' else raw.decode('utf-8')
        payload=dict(target=binding['supervisor'],format=binding['format'],raw=value,sha=binding['sha256'],viewed=False)
        key=w.review('report',dict(payloads=[payload],sending=True,assignments=False,simulation=False,day=binding['day'],
            delivery=SimpleNamespace(reviewed=snapshot['bundle'][0]),owner=None,source=fingerprint(w.store.read()),
            options=dict(app_theme=w.print_accent),doctors=fingerprint(w.client.doctors)),
            'استُعيد الملف الأصلي بفحص بصمته. النسخة الإضافية تحتاج موافقة مستقلة ولا تحسم الإيصال الأصلي.',None)
        w.reviews[key]['used']=True
        return redirect(url_for('features.report_copy',token=key,receipt_index=receipt_index),code=303)
    return render_template('recover_artifact.html',page='reports')
