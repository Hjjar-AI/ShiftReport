"""Data actions; the Window remains the single workspace/state owner."""
from __future__ import annotations
import copy
from pathlib import Path
import time
from tkinter import filedialog
from desktop_widgets import messagebox, simpledialog, set_readonly_text
import desktop_widgets as ctk
from desktop_protocol import BridgeError, fingerprint, require
from desktop_forms import dialog_actions, modal
from desktop_reports import publication_review
from desktop_cards import PatientCards
from desktop_backup import restore_dialog


def download(owner):
    owner.ready()
    owner.store.require_downloadable()
    snapshot=owner.io(owner.client.fetch); owner.io(lambda:owner.client.authorize(snapshot['doctors']))
    shifts=sorted(snapshot['bundle'],key=lambda s:s['meta']['shiftDate'],reverse=True)
    require(shifts,'لا توجد بيانات مرضى منشورة')
    if owner.store.state['draft'] is not None and not messagebox.askyesno('جلب نسخة كاملة','هل تستبدل العرض المحلي بالنسخة المنشورة الكاملة؟ تحفظ نسخة استرداد مشفرة. التعديلات المحلية المحفوظة يجب نشرها أولاً.',default='no'):return
    selected=copy.deepcopy(shifts[0])
    previous=owner.client.baseline
    owner.client.baseline=snapshot
    try:
        owner.io(lambda:owner.store.write(owner.client,selected,downloaded=True))
    except Exception:
        owner.client.baseline=previous; raise
    owner.client.doctors=snapshot['doctors']; owner.set_doctors(); owner.plan=None
    owner.status.set('جلبت '+selected['meta']['shiftDate']+' — تحفظ التعديلات محليا حتى مراجعتها ونشرها.')


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
    plan=owner.io(lambda:owner.client.prepare(plan))
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
    owner.io(lambda:owner.store.resolve_unknown(index,confirmation,owner.client))

