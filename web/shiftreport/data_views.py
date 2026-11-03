"""Reviewed complete-snapshot retrieval, recovery and connection management."""
import copy
import secrets
from flask import g, redirect, render_template, request, url_for
from .features import features, home, uploaded, download
from .core.protocol import require, fingerprint, packed, parse_bundle, BUNDLE, current_date
from .core.model import shift_text


def reviewed_action(kind, value, summary, action):
    key=g.workspace.review(kind,value,summary,action)
    return redirect(url_for('features.confirm_review',token=key),code=303)


@features.route('/data',methods=['GET','POST'])
def data():
    w=g.workspace
    w.ready()
    if request.method=='POST':
        action=request.form.get('action')
        shift=w.store.read()
        expected=fingerprint(shift)
        if action=='csv':
            require(not w.demo,'حفظ البيانات السريرية التجريبية مقتصر على PDF موسوم DEMO')
            from .core.csv import patient_csv
            return download(patient_csv(shift),'ShiftReport.csv','text/csv; charset=utf-8')
        if action=='doctor_csv':
            require(not w.demo,'تصدير CSV غير متاح في التجربة')
            w.ready(admin=True)
            from .core.csv import doctor_csv
            return download(doctor_csv(w.client.doctors),'doctors.csv','text/csv; charset=utf-8')
        if action=='backup':
            require(not w.demo,'النسخ الاحتياطية غير متاحة في التجربة')
            from .core.backup import encode
            password=request.form.get('password','')
            require(password==request.form.get('confirmation',''),'كلمتا المرور غير متطابقتين')
            return download(encode(shift,password),'ShiftReport.rpb')
        if action=='workspace_export':
            require(not w.demo,'ملف الاسترداد غير متاح في التجربة')
            from tempfile import TemporaryDirectory
            from pathlib import Path
            with TemporaryDirectory(prefix='ShiftReportRecovery_') as directory:
                path=Path(directory)/'workspace.srdraft'
                w.store.export(path)
                return download(path.read_bytes(),'workspace.srdraft')
        if action=='workspace_restore':
            require(not w.demo,'ملف الاسترداد غير متاح في التجربة')
            value=w.store.decode(uploaded(64*1024*1024))
            require(value.get('draft') is not None and value.get('baseline') is not None,'ملف استرداد فارغ')
            require(value['draft']['meta'].get('schemaVersion')=='8','مطلوب ملف الاسترداد الحالي')
            from .core.backup import validate_restore
            validate_restore(w.store,w.client,value['draft'],expected)
            def apply():
                w.ready()
                validate_restore(w.store,w.client,value['draft'],expected)
                w.store.commit(draft=value['draft'],recycle=value.get('recycle',[]),patientForm=None)
                w.forms.clear()
            return reviewed_action('workspace_restore',value,'استعادة مرضى القسم والسلة محلياً فقط؛ يحتفظ هذا الجهاز بهويته وإيصالاته واتصاله.\n\n'+shift_text(value['draft'],w.client.doctors,w.client.zone),apply)
        if action in ('restore','import_csv'):
            require(not w.demo,'لا يمكن إدخال بيانات حقيقية إلى التجربة')
            raw=uploaded()
            if action=='restore':
                from .core.backup import decode
                candidate=decode(raw,request.form.get('password',''))
            else:
                candidate=parse_bundle((BUNDLE+'\n').encode()+raw)[0]
            from .core.backup import validate_restore, restore
            validate_restore(w.store,w.client,candidate,expected)
            def apply():
                w.ready()
                restore(w.store,w.client,candidate,expected)
            return reviewed_action('restore',candidate,'استعادة محلية فقط؛ لا نشر ولا تغيير لإيصالات التسليم.\n\n'+shift_text(candidate,w.client.doctors,w.client.zone),apply)
        if action=='undo':
            undo=w.store.state.get('downloadUndo')
            require(undo and undo.get('before') is not None,'لا يوجد جلب للتراجع عنه')
            require(expected==undo['after'],'تغيرت البيانات منذ الجلب؛ لن تُستبدل التعديلات')
            def apply():
                require(fingerprint(w.store.read())==expected,'تغيرت البيانات؛ أعد المراجعة')
                changes=dict(draft=undo['before'],downloadUndo=None)
                if undo.get('baseline_before') is not None:
                    changes['baseline']=undo['baseline_before']
                    w.client.baseline=copy.deepcopy(undo['baseline_before'])
                    w.client.doctors=copy.deepcopy(undo['baseline_before']['doctors'])
                w.store.commit(**changes)
            return reviewed_action('undo',undo,'التراجع محلي فقط؛ لا يغيّر النسخة المنشورة.\n\n'+shift_text(undo['before'],w.client.doctors,w.client.zone),apply)
        if action in ('fetch','doctors_fetch'):
            if w.demo:
                source=copy.deepcopy(w.store.published)
                snapshot=None
            else:
                w.ready(online=True)
                snapshot=w.client.fetch()
                w.client.authorize(snapshot['doctors'])
                if action=='doctors_fetch':
                    from .core.model import validate_patient
                    for row in shift['patients']:
                        validate_patient(row,snapshot['doctors'],w.client.zone)
                    require(all(k in snapshot['doctors'] for k in filter(None,shift['meta'].get('doctors','').split(','))),'استخدم الجلب الكامل؛ أطباء المناوبة غير متاحين')
                    source=shift
                else:
                    require(len(snapshot['bundle'])==1,'لا توجد نسخة قسم منشورة')
                    source=copy.deepcopy(snapshot['bundle'][0])
            def apply():
                w.ready(online=not w.demo)
                require(fingerprint(w.store.read())==expected,'تغيرت البيانات المحلية؛ أعد مراجعة الجلب')
                undo=dict(before=shift,after=fingerprint(source),baseline_before=copy.deepcopy(w.store.state.get('baseline')))
                changes=dict(draft=source,downloadUndo=undo,patientForm=None)
                if snapshot is not None:
                    fresh=w.client.fetch()
                    w.client.authorize(fresh['doctors'])
                    require(fingerprint(fresh)==fingerprint(snapshot),'تغيرت النسخة المنشورة؛ أعد مراجعة الجلب')
                    changes['baseline']=snapshot
                    w.client.baseline=copy.deepcopy(snapshot)
                    w.client.doctors=copy.deepcopy(snapshot['doctors'])
                w.store.commit(**changes)
                w.forms.clear()
            return reviewed_action('fetch',source,'سيستبدل الجلب البيانات المحلية والتعديلات غير المنشورة؛ يحفظ تراجعاً محلياً واحداً.\n\n'+shift_text(source,(snapshot or {}).get('doctors',w.client.doctors),w.client.zone),apply)
        if action=='join_export':
            require(not w.demo,'ملف الانضمام لمشروع حقيقي فقط')
            w.ready(online=True,admin=True)
            from .core.project import export_join
            from tempfile import TemporaryDirectory
            from pathlib import Path
            with TemporaryDirectory(prefix='ShiftReportJoin_') as directory:
                path=Path(directory)/'project.srjoin.json'
                export_join(w.client,path,request.form.get('password',''))
                return download(path.read_bytes(),'project.srjoin.json','application/json')
        require(False,'عملية بيانات غير صالحة')
    return render_template('data.html',page='data')


@features.route('/sync',methods=['GET','POST'])
def sync():
    w=g.workspace
    w.ready()
    if request.method=='POST':
        action=request.form.get('action')
        if action=='publish':
            local=w.store.read()
            expected=fingerprint(local)
            if w.demo:
                def apply():
                    require(fingerprint(w.store.read())==expected,'تغيرت البيانات؛ أعد المراجعة')
                    w.store.published=copy.deepcopy(local)
                return reviewed_action('publish',local,'DEMO — محاكاة في الذاكرة فقط.\n\n'+shift_text(local,w.client.doctors,w.client.zone),apply)
            w.ready(online=True)
            plan=w.client.prepare(w.client.plan(local))
            from .core.reports import publication_review
            from .report_views import publish_plan
            def apply():
                publish_plan(w,plan,expected)
            return reviewed_action('publish',plan,publication_review(plan,w.client.zone),apply)
        if action=='rollback':
            require(not w.demo,'التراجع عن النشر يتطلب مشروعاً حقيقياً')
            w.ready(online=True,admin=True)
            snapshot=w.client.fetch()
            file_id=snapshot['state'].get('previousCsvFileId')
            require(file_id,'لا توجد نسخة نشر سابقة')
            shifts=parse_bundle(w.client.telegram.download(file_id))
            require(len(shifts)==1,'نسخة سابقة غير صالحة')
            expected=fingerprint(w.store.read())
            plan=w.client.prepare(w.client.plan(shifts[0]))
            from .core.reports import publication_review
            from .report_views import publish_plan
            def apply():
                w.ready(online=True,admin=True)
                publish_plan(w,plan,expected)
            return reviewed_action('rollback',plan,'إعادة نشر إداري للنسخة السابقة؛ سيصبح النشر الأخير الناجح هو المعتمد.\n\n'+publication_review(plan,w.client.zone),apply)
        if action=='empty':
            require(not w.demo,'البداية الفارغة الإدارية لمشروع حقيقي فقط')
            w.ready(online=True,admin=True)
            from .core.empty_start import EmptyStart
            plan=EmptyStart(w.client,w.store)
            return reviewed_action('empty',plan,'مسح المرضى المحليين ومسوداتهم والسلة ونشر قسم فارغ. يبقى الأطباء والاتصال والإيصالات. احفظ نسخة احتياطية أولاً.',plan.publish)
        if action=='destination':
            require(not w.demo,'الوجهة الحقيقية غير متاحة في التجربة')
            w.ready(online=True,admin=True)
            from .core.destinations import parse_destination, save_destination
            group,topic=parse_destination(request.form.get('group',''),request.form.get('topic',''))
            expected=fingerprint(w.client.config)
            return reviewed_action('destination',None,'تغيير وجهة تقارير المقيمين. أعد توزيع ملف انضمام محمي بعد الحفظ.',lambda:save_destination(w.client,w.store,expected,group,topic))
        if action=='resolve':
            require(not w.demo,'لا توجد إيصالات شبكة حقيقية في التجربة')
            index=int(request.form.get('index','-1'))
            row=w.store.require_receipt_resolution(index,w.client)
            not_sent=request.form.get('resolution')=='not_sent'
            def apply():
                require(w.store.state['receipts'][index]==row,'تغير الإيصال؛ أعد المراجعة')
                w.store.resolve_unknown(index,not_sent,w.client)
            return reviewed_action('receipt',row,'تأكيد فحص الوجهة: '+('لم تصل النسخة السابقة.' if not_sent else 'وصلت النسخة السابقة؛ لا تُكرّر.'),apply)
        require(False,'عملية مزامنة غير صالحة')
    receipts=[dict(index=i,operation=row[0],status=row[2],at=row[6],can_copy=str(i) in w.store.state.get('reportArtifacts',{})) for i,row in w.store.receipts()]
    journal=(w.store.state.get('baseline') or {}).get('state',{}).get('publicationJournal',[])
    return render_template('sync.html',receipts=receipts,journal=journal,local_pending=w.pending_publication(),page='sync')
