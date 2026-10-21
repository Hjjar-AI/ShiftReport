"""Report actions; the Window remains the single workspace/state owner."""
from __future__ import annotations
import sys
from pathlib import Path
from tkinter import filedialog
from desktop_widgets import messagebox
from desktop_protocol import BridgeError, require
from desktop_model import doctor_label
from desktop_reports import options_dialog, report_text, export_supervisor_pdfs, ReportAttempt
from desktop_batch_reports import send_supervisor_reports


BASE = Path(sys.executable).resolve().parent if getattr(sys, 'frozen', False) else Path(__file__).resolve().parent


def export_assignments(owner):
    owner.ready()
    actor_id=owner.io(owner.client.require_admin)['uid']
    local=owner.store.read()
    options=options_dialog(owner,export_only=True,assignments=True)
    if options is None:return
    font=owner.settings.get('PDF_FONT')
    if font and not Path(font).is_absolute():font=str(BASE/font)
    destination=filedialog.asksaveasfilename(title='توزيع المرضى بالأسماء فقط',defaultextension='.pdf',filetypes=[('PDF','*.pdf')])
    if not destination:return
    from desktop_pdf import render_assignments
    def save():
        path=render_assignments(owner.client,local,font_path=font,options=options)
        require(owner.client.require_admin()['uid']==actor_id,'تغيّر المستخدم؛ أعد تصدير توزيع المرضى')
        raw=path.read_bytes()
        Path(destination).write_bytes(raw)
        require(Path(destination).read_bytes()==raw,'تعذر التحقق من حفظ PDF كاملاً')
    owner.io(save)
    owner.status.set('حُفظ توزيع المرضى: A4 عمودي، ثلاثة جداول في كل صف وترقيم متصل.')


def show_report_attempt(owner):
    if owner.report_attempt:owner.report_summary.set(owner.report_attempt.summary())


def report_batch(owner):
    owner.report_attempt=ReportAttempt();owner.show_report_attempt()
    try:return send_supervisor_reports(owner)
    except Exception:
        owner.report_attempt.failed(owner.client.baseline if owner.client else None)
        owner.show_report_attempt()
        raise


def report(owner,export_only=False):
    if export_only:return owner._report(export_only=True)
    owner.report_attempt=ReportAttempt();owner.show_report_attempt()
    try:
        return owner._report()
    except Exception as error:
        owner.report_attempt.failed(owner.client.baseline if owner.client else None)
        owner.show_report_attempt()
        explanation=owner.report_attempt.summary()+'\n'+owner.report_attempt.next_action()
        owner.status.set(explanation)
        detail=str(error) if isinstance(error,BridgeError) else 'تعذر إتمام العملية المحلية أو الاتصال.'
        raise BridgeError(explanation+'\n'+detail) from None
    finally:owner.show_report_attempt()


def _report(owner,export_only=False):
    owner.ready(not export_only);local=owner.store.read(owner.client)
    if not export_only:
        review=options_dialog(owner,review_send=True)
        if review is None:return
        def confirm_repeat(supervisor):
            return messagebox.askyesno('نسخة إضافية','هذا التقرير سلم سابقاً إلى الوجهة نفسها. هل تؤكد إرسال نسخة إضافية؟',default='no')
        sent=review['delivery'].send(review['payloads'],confirm_repeat)
        if sent:owner.notify_success('تم تسليم التقرير إلى الوجهة التي راجعتها.')
        return
    options=options_dialog(owner,export_only=True)
    if options is None:return
    local=owner.store.read(owner.client)
    if owner.demo:require(export_only and options['format']=='pdf','في الوضع التجريبي يسمح بحفظ PDF محلي فقط')
    supervisor=owner.selected_supervisor()
    font=owner.settings.get('PDF_FONT')
    if font and not Path(font).is_absolute():font=str(BASE/font)
    from desktop_pdf import render_report
    if export_only:
        if options['format']=='pdf' and options['separate']:
            export_supervisor_pdfs(owner,local,supervisor,font,options)
            return
        if options['format']=='text':
            raw=report_text(owner.client,local,supervisor).encode('utf-8')
            extension='.txt'
        else:
            path=owner.io(lambda:render_report(owner.client,local,supervisor,font,options))
            raw=owner.io(path.read_bytes);extension='.pdf'
        destination=filedialog.asksaveasfilename(title='حفظ تقرير المسودة المحلية — قد يتضمن تعديلات غير منشورة',defaultextension=extension,filetypes=[('Report','*'+extension)])
        if destination:owner.io(lambda:Path(destination).write_bytes(raw))
        return


def selected_supervisor(owner):
    value=owner.supervisor.get()
    if value=='التقرير العام':return None
    key=next((key for key in owner.destinations[1:] if doctor_label(owner.client.doctors,key)==value),None)
    require(key is not None,'اختر وجهة التقرير');return key

