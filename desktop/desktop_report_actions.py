"""Report actions; the Window remains the single workspace/state owner."""
from __future__ import annotations
import sys
from pathlib import Path
from tkinter import filedialog
from desktop_widgets import messagebox
from desktop_protocol import BridgeError, require, current_date
from desktop_model import doctor_label
from desktop_reports import options_dialog, ReportAttempt
from desktop_batch_reports import send_supervisor_reports


BASE = Path(sys.executable).resolve().parent if getattr(sys, 'frozen', False) else Path(__file__).resolve().parent


def export_assignments(owner):
    from desktop_export_review import run_export
    return run_export(owner, assignments=True)


def show_report_attempt(owner):
    if owner.report_attempt:owner.report_summary.set(owner.report_attempt.summary())


def report_batch(owner):
    owner.report_attempt=ReportAttempt();owner.show_report_attempt()
    try:return send_supervisor_reports(owner)
    except Exception:
        owner.report_attempt.failed(owner.client.baseline if owner.client else None)
        owner.show_report_attempt()
        raise


def report(owner,export_only=False,separate=False):
    if export_only:return owner._report(export_only=True,separate=separate)
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


def _report(owner,export_only=False,separate=False):
    owner.ready(not export_only);local=owner.store.read(owner.client)
    if not export_only:
        review=options_dialog(owner,review_send=True)
        if review is None:return
        def confirm_repeat(supervisor):
            return messagebox.askyesno('نسخة إضافية','هذا التقرير سلم سابقاً إلى الوجهة نفسها. هل تؤكد إرسال نسخة إضافية؟',default='no')
        from desktop_export_review import save_published_pdfs
        save_copy=(lambda:save_published_pdfs(owner,review['delivery'],review['payloads'])) if review.get('exportPdf') else None
        sent=review['delivery'].send(review['payloads'],confirm_repeat,after_delivery=save_copy)
        if sent:owner.notify_success('تم تسليم التقرير إلى الوجهة التي راجعتها.')
        return
    from desktop_export_review import run_export
    return run_export(owner,separate=separate)


def selected_supervisor(owner):
    value=owner.supervisor.get()
    if value=='التقرير العام':return None
    key=next((key for key in owner.destinations[1:] if doctor_label(owner.client.doctors,key)==value),None)
    require(key is not None,'اختر وجهة التقرير');return key

