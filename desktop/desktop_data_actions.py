"""Data actions; the Window remains the single workspace/state owner."""
from __future__ import annotations
import copy
from datetime import datetime
from pathlib import Path
import time
from tkinter import filedialog
from desktop_widgets import messagebox, simpledialog, set_readonly_text
import uuid
import desktop_widgets as ctk
from desktop_protocol import BridgeError, ZoneInfo, current_date, doctor_pointer, fingerprint, pointer, require
from desktop_model import carry_forward
from desktop_forms import dialog_actions, modal
from desktop_reports import publication_review
from desktop_sort import ordered
from desktop_cards import PatientCards
from desktop_backup import restore_dialog


def download(owner):
    owner.ready()
    owner.store.require_downloadable()
    snapshot=owner.io(owner.client.fetch); owner.io(lambda:owner.client.authorize(snapshot['doctors']))
    shifts=sorted(snapshot['bundle'],key=lambda s:s['meta']['shiftDate'],reverse=True)
    today=current_date(owner.client.zone)
    if not any(s['meta']['shiftDate']==today for s in shifts):
        shifts.insert(0,{'meta':{'schemaVersion':'7','shiftDate':today,'shiftId':str(uuid.uuid4()),'doctors':''},'patients':[]})
    choice=owner.choose('المناوبة المطلوبة',[s['meta']['shiftDate']+' — '+('مناوبة اليوم فارغة' if s['meta']['shiftDate']==today and not s['patients'] else str(len(s['patients']))+' مريض') for s in shifts])
    if choice is None:return
    if owner.store.state['draft'] is not None and not messagebox.askyesno('جلب نسخة كاملة','هل تستبدل العرض المحلي بالنسخة المنشورة الكاملة؟ تحفظ نسخة استرداد مشفرة. التعديلات المحلية المحفوظة يجب نشرها أولاً.',default='no'):return
    selected=copy.deepcopy(shifts[choice])
    previous=owner.client.baseline
    owner.client.baseline=snapshot
    try:
        owner.io(lambda:owner.store.write(owner.client,selected,downloaded=True))
    except Exception:
        owner.client.baseline=previous; raise
    owner.client.doctors=snapshot['doctors']; owner.set_doctors(); owner.plan=None
    owner.status.set('جلبت '+selected['meta']['shiftDate']+' — تحفظ التعديلات محليا حتى مراجعتها ونشرها.')


def start_shift(owner):
    owner.ready()
    expected=fingerprint(owner.store.state.get('draft'))
    snapshot=owner.io(owner.client.fetch)
    owner.io(lambda:owner.client.authorize(snapshot['doctors']))
    today=current_date(owner.client.zone)
    require(not any(s['meta']['shiftDate']==today and s['patients'] for s in snapshot['bundle']),
            'مناوبة اليوم المنشورة تحتوي مرضى؛ اجلبها بدلاً من نسخ السابقة')
    local=owner.store.state['draft']
    require(local is None or local['meta']['shiftDate']!=today or not local['patients'],
            'مناوبة اليوم المحلية تحتوي مرضى؛ لن تُستبدل بنسخ السابقة')
    previous=[s for s in snapshot['bundle'] if s['meta']['shiftDate']<today]
    require(previous,'لا توجد مناوبة سابقة لنسخها')
    source=max(previous,key=lambda s:s['meta']['shiftDate'])
    checked_at=datetime.now(ZoneInfo(owner.client.zone)).strftime('%Y-%m-%d %H:%M %Z')
    source_info='المصدر: مناوبة '+source['meta']['shiftDate']+' من حزمة تليجرام.\nتم جلب الحزمة لهذه العملية في '+checked_at+'\nعدد المرضى: '+str(len(source['patients']))
    require(source['patients'],'المناوبة السابقة فارغة؛ لا يوجد مرضى لنسخهم')
    keep=owner.choose('بدء مناوبة جديدة — نسخ جميع المرضى والمهام غير المكتملة من '+source['meta']['shiftDate'],
                     ['نسخ الكل وإزالة مسؤوليات الأطباء ومالكي المهام','نسخ الكل والاحتفاظ بالمسؤوليات'])
    if keep is None:return
    if not messagebox.askyesno('تأكيد بدء مناوبة جديدة',
            source_info+'\nستُنشأ سجلات جديدة لجميع مرضى المناوبة السابقة مع المهام غير المكتملة فقط.\nهذا ليس جلباً لبيانات اليوم. تحفظ محلياً ثم تراجع وتنشر.\nهل تتابع؟',default='no'):return
    recovery_path=None
    if owner.store.has_pending_changes():
        if not messagebox.askyesno('تعديلات محلية غير منشورة',
                'توجد تعديلات محلية لم تُنشر. لن تُنقل إلى المناوبة الجديدة المنسوخة من تليجرام.\n'
                'يجب حفظها في ملف استرداد مشفر مستقل قبل المتابعة. إلغاء يحافظ على العرض الحالي.\n'
                'هل تريد حفظ ملف الاسترداد والمتابعة؟',default='no'):return
        recovery_path=filedialog.asksaveasfilename(title='حفظ التعديلات غير المنشورة قبل بدء المناوبة — اختر ملفاً جديداً',
                defaultextension='.srdraft',filetypes=[('Desktop recovery','*.srdraft')])
        if not recovery_path:return
    selected=carry_forward(source,owner.client.zone,bool(keep))
    actor=owner.io(lambda:owner.client.authorize(snapshot['doctors']))
    existing=next((s for s in snapshot['bundle'] if s['meta']['shiftDate']==today),None)
    if existing:selected['meta']=copy.deepcopy(existing['meta'])
    elif local and local['meta']['shiftDate']==today:selected['meta']=copy.deepcopy(local['meta'])
    selected['meta']['sortSpec']='{"levels":[]}'
    ordered(selected,snapshot['doctors'],owner.client.zone)
    latest=owner.io(owner.client.fetch)
    owner.io(lambda:owner.client.authorize(latest['doctors']))
    require(pointer(latest['state'])==pointer(snapshot['state']) and
            doctor_pointer(latest['state'])==doctor_pointer(snapshot['state']),
            'وصلت نسخة جديدة أثناء التأكيد؛ أعد بدء المناوبة')
    at=str(int(time.time()*1000))
    for row in selected['patients']:
        row.update(lastEditedByDoctorId=actor['uid'],lastEditedBy=actor['name'],updatedAt=at,revision='1')
    old=owner.client.baseline;owner.client.baseline=snapshot
    try:owner.io(lambda:owner.store.start_shift(owner.client,selected,expected,recovery_path))
    except Exception:
        owner.client.baseline=old;raise
    owner.client.doctors=snapshot['doctors'];owner.set_doctors();owner.plan=None;owner.raw_draft=None
    owner.status.set('نُسخت المناوبة كاملة محلياً؛ حدد أطباء المناوبة ثم راجع وانشر. المهام المكتملة بقيت في السابقة.')


def undo_download(owner):
    owner.ready(False)
    expected=fingerprint(owner.store.read())
    undo=owner.store.state.get('downloadUndo')
    require(undo and undo.get('before'),'لا يوجد تنزيل محلي للتراجع عنه')
    require(expected==undo['after'],'تغيرت البيانات منذ التنزيل؛ التراجع محظور لحماية التعديلات')
    if not messagebox.askyesno('التراجع عن آخر تنزيل',
            'استعادة العرض المحلي قبل آخر تنزيل (مناوبة '+undo['before']['meta']['shiftDate']+')؟\n'
            'لا يغير تليجرام أو سجل التسليم. يلزم مراجعة ونشر أي تغييرات لاحقاً.',default='no'):return
    owner.io(lambda:owner.store.undo_download(owner.client,expected))
    owner.plan=None;owner.raw_draft=None;owner.cards=PatientCards()
    owner.status.set('استُعيد العرض المحلي قبل التنزيل؛ بقيت آخر نسخة منشورة معروفة وسجلات التسليم محفوظة.')


def export_draft(owner):
    owner.ready(False); owner.store.read()
    path=filedialog.asksaveasfilename(title='استرداد سطح المكتب فقط — للمشاركة استخدم .rpb',defaultextension='.srdraft',filetypes=[('Desktop recovery','*.srdraft')])
    if path:owner.io(lambda:owner.store.export(path,replace=Path(path).exists()))


def import_draft(owner):
    owner.ready(False)
    require(not owner.demo,'لا يمكن استيراد بيانات حقيقية إلى العرض التجريبي')
    path=filedialog.askopenfilename(title='استعادة نسخة للمشروع نفسه',initialdir=str(owner.store.directory),filetypes=[('Encrypted backups','*.rpb *.srdraft')])
    if path and Path(path).suffix.lower()=='.rpb':
        if restore_dialog(owner,path):
            owner.plan=None;owner.raw_draft=None
            owner.status.set('استُعيدت المناوبة محلياً؛ راجعها قبل النشر. بقي سجل التسليم محفوظاً.')
        return
    if path and messagebox.askyesno('استعادة','هل تستبدل المسودة المحلية؟ سجل التسليم الحالي يبقى محفوظا.',default='no'):
        owner.io(lambda:owner.store.import_draft(path,owner.client));owner.plan=None;owner.set_doctors()
        owner.status.set('استعدت المسودة؛ أعد المراجعة مع تليجرام قبل النشر.')


def text_dialog(owner,title,text,approve=False,approval_label='راجعت النسخة المعروضة وأوافق على النشر',publication_plan=None):
    window=modal(owner,title,'920x680')
    controls=ctk.CTkFrame(window);controls.pack(side='bottom',fill='x')
    widget=ctk.CTkTextbox(window,wrap='word',font=('Arial',15));widget.pack(fill='both',expand=True,padx=8,pady=8)
    copy_original=set_readonly_text(widget,text)
    if publication_plan is not None:
        ctk.CTkLabel(controls,text='التأكيد ينشر النسخة المحلية الكاملة مكان بيانات المناوبة المنشورة، بما فيها الحذف والترتيب وأطباء المناوبة. آخر نشر ناجح هو المعتمد.',
            wraplength=820,justify='right').pack(fill='x',padx=8,pady=4)
    accepted=[]
    def accept():
        if publication_plan is not None:publication_plan.force_local=True
        accepted.append(True);window.destroy()
    dialog_actions(controls,primary=(approval_label,accept) if approve else None,
        cancel=('إلغاء' if approve else 'إغلاق',window.destroy),secondary=[('نسخ النص الكامل',copy_original)])
    owner.root.wait_window(window);return bool(accepted)


def show_review(owner,text,publication_plan=None):
    return owner.text_dialog('مراجعة قبل النشر',text,approve=True,publication_plan=publication_plan)


def review(owner):
    owner.ready();owner.plan=None
    local=owner.store.read(owner.client);draft_hash=fingerprint(local)
    plan=owner.io(lambda:owner.client.plan(local))
    plan=owner.io(lambda:owner.client.prepare(plan,owner.store.restored_completions(),saved_patients=local['patients']))
    require(fingerprint(owner.store.read(owner.client))==draft_hash,'تغير المسودة أثناء المراجعة؛ أعدها')
    if owner.show_review(publication_review(plan,owner.client.zone),publication_plan=plan):
        owner.client.approve(plan);owner.plan=plan;owner.raw_draft=draft_hash
        owner.status.set('المراجعة جاهزة. اضغط نشر لاستبدال بيانات المناوبة بالنسخة التي راجعتها.')


def publish(owner, notify=True):
    owner.ready()
    if owner.plan is None:
        owner.review()
        if owner.plan is None:return
    require(fingerprint(owner.store.read(owner.client))==owner.raw_draft,'تغيرت المسودة؛ أعد مراجعتها')
    plan=owner.plan
    key=fingerprint({'operation':'publish','project':owner.client.project_id,'draft':owner.raw_draft,'remote':plan.baseline_pointer})
    result=owner.io(lambda:owner.client.publish(plan,lambda:owner.store.receipt_start(key,'publish',owner.client.config['chatId'],owner.client.config.get('csvTopicId'),owner.client._actor,snapshot=plan.shift['meta']['snapshotId'])))
    owner.plan=None
    owner.status.set('تليجرام أكد نشر النسخة. لا تعد العملية إذا فشل حفظ العرض المحلي لاحقا.')
    try:
        owner.io(lambda:owner.store.receipt_finish(result))
        owner.io(lambda:owner.store.write(owner.client,plan.shift,backup=False))
    except Exception:
        raise BridgeError('تم النشر على تليجرام؛ تعذر تحديث المسودة محليا. لا تعد الإرسال. اجلب النسخة الحالية بعد حفظ مسودتك') from None
    if notify:messagebox.showinfo('تم النشر','نشر مؤكد؛ بقيت بيانات الأطباء والإعلانات والاستعادة في الفهرس.')


def resolve(owner):
    owner.ready(False)
    records=owner.store.receipts(status='UNKNOWN')
    if not records:
        owner.text_dialog('سجل العمليات','\n'.join(' | '.join(row[:8]) for _,row in owner.store.receipts()) or 'لا توجد عمليات');return
    choice=owner.choose('اختر عملية واحدة للمراجعة',[str(index)+' | '+row[0]+' | '+row[6]+' | '+row[4]+' | '+row[1][:12] for index,row in records])
    if choice is None:return
    index,row=records[choice]
    if row[0]=='doctors' and row[7].startswith('snapshot='):
        owner.ready()
        snapshot=owner.io(owner.client.fetch)
        owner.io(lambda:owner.client.authorize(snapshot['doctors']))
        if fingerprint(snapshot['doctors'])==row[7][9:]:
            owner.io(lambda:owner.store.confirm_indexed_receipt(index,snapshot['state']['doctorsMessageId']))
            messagebox.showinfo('تأكيد سجل الأطباء','السجل المثبت الحالي يطابق العملية؛ لا تكررها.')
            return
    if row[0] in {'publish','empty_start'} and row[7].startswith('snapshot='):
        owner.ready()
        state=owner.io(owner.client.fetch)['state']
        match=next((entry for entry in state.get('publicationJournal',[]) if entry['snapshotId']==row[7][9:]),None)
        if match:
            owner.io(lambda:owner.store.confirm_indexed_receipt(index,match['messageId']))
            messagebox.showinfo('تأكيد النشر','سجل الفهرس المثبت يؤكد نشر هذه النسخة؛ لا تكررها.');return
    confirmation=messagebox.askyesnocancel('تحقق يدوي','الحسم للمرسل أو لمدير نشط ويتطلب التحقق من الهوية عبر تليجرام.\nافحص العملية المختارة على تليجرام أولا. في النشر تحقق من الفهرس والمرضى، وليس مجرد وجود ملف في موضوع CSV. غيابها عن السجل الحالي ليس إثبات عدم وصولها.\nنعم: تؤكد أنها لم تصل وتسمح بمحاولتها.\nلا: وصلت أو لا تريد تكرارها.\nإلغاء: إبقاء الحظر.',default='cancel')
    if confirmation is None:return
    if row[0]=='announcement' and not confirmation:
        value=simpledialog.askstring('رسالة الإعلان','أدخل معرّف رسالة الإعلان الذي تحققت منه في تليجرام. لا تُستبدل إحالة أحدث.',parent=owner.root)
        if value is None:return
        require(value.strip().isdecimal() and int(value)>0,'أدخل معرّف رسالة موجباً')
        from desktop_announcements import Announcements
        owner.io(lambda:Announcements(owner.client,owner.store).resolve_received(index,int(value)))
    else:owner.io(lambda:owner.store.resolve_unknown(index,confirmation,owner.client))

