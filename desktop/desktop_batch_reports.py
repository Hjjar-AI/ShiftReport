"""One reviewed workspace for supervisor PDFs, with durable per-destination delivery."""
from desktop_widgets import messagebox
from desktop_reports import options_dialog


def send_supervisor_reports(owner):
    owner.ready()
    review=options_dialog(owner,force_pdf=True,review_send=True)
    if review is None:return
    delivery=review['delivery']
    selected=[key for key,_,_ in review['payloads']]
    outcomes = {key:'لم يبدأ' for key in selected}
    def summary():
        return '\n'.join(owner.client.doctors.get(key,{}).get('name','مشرف محفوظ')+': '+outcomes[key] for key in selected)
    def confirm_repeat(key):
        return messagebox.askyesno('نسخة إضافية',owner.client.doctors[key]['name']+' — سُلّم تقرير هذه النسخة سابقاً. هل تؤكد نسخة إضافية؟',default='no')
    def outcome(key,status):
        outcomes[key]=status
    try:
        from desktop_export_review import save_published_pdfs
        save_copy=(lambda:save_published_pdfs(owner,delivery,review['payloads'])) if review.get('exportPdf') else None
        delivery.send(review['payloads'],confirm_repeat,outcome,after_delivery=save_copy)
    except Exception:
        owner.status.set(summary()+'\nتوقف الإرسال؛ راجع سجل التسليم قبل إعادة أي وجهة.')
        messagebox.showinfo('نتائج تقارير المشرفين',summary())
        raise
    owner.status.set(summary())
    messagebox.showinfo('نتائج تقارير المشرفين',summary())
