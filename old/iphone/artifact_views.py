"""CSRF-protected native requests; browser routes never receive PDF bytes/paths."""


STATES = {
    'queued': 'بانتظار واجهة الملفات الأصلية؛ لم تبدأ العملية بعد.',
    'running': 'جارٍ فتح نافذة الملف الأصلية؛ لا تُعد العملية.',
    'review': 'أُرسل الملف إلى المعاينة. بعد إغلاق نافذتها، أكّد هل ظهر PDF وراجعته كاملاً.',
    'reviewed': 'أكدت ظهور المعاينة ومراجعتها. الحفظ ينسخ بايتاتها نفسها.',
    'saved': 'حُفظت نسخة منفصلة وتطابقت بايتاتها مع الملف المعاين.',
    'share_requested': 'طُلبت نافذة المشاركة. هذا لا يؤكد الحفظ أو وصول الملف إلى جهة أخرى.',
    'uncertain': 'لم يمكن تأكيد بايتات النسخة المصدّرة. افحص Files؛ لم تُعد العملية.',
    'failed_open': 'تعذّر تأكيد العملية الأصلية. أغلق أي نافذة ملف ظهرت ثم تابع؛ لا تفترض نجاحها.',
    'failed': 'تعذّر إكمال العملية. لم تُعتمد معاينة ولم تبدأ إعادة تلقائية.',
    'cancelled': 'أُلغيت العملية؛ لم تُعتمد معاينة.',
    'closed': 'أكدت إغلاق النافذة الأصلية. هذا لا يثبت تسليم الملف أو نجاح التصدير.',
}


def register_routes(native, operations):
    from flask import g, jsonify, redirect, render_template, request, url_for
    from shiftreport.core.protocol import require

    @native.post('/artifact/start')
    def artifact_start():
        try:
            index = int(request.form.get('index', ''))
        except ValueError:
            require(False, 'رقم ملف غير صالح')
        key = operations.start(g.workspace, request.form.get('review', ''), index, request.form.get('mode', ''))
        return redirect(url_for('iphone.artifact_operation', key=key), code=303)

    @native.route('/artifact/<key>', methods=['GET','POST'])
    def artifact_operation(key):
        operation = operations.get(g.workspace, key)
        if request.method == 'POST':
            action = request.form.get('action')
            if action in ('reviewed', 'not_viewed'):
                require(request.form.get('closed') == '1', 'أغلق نافذة المعاينة الأصلية أولاً')
                operations.acknowledge(g.workspace, key, viewed=action == 'reviewed')
            elif action == 'closed':
                require(request.form.get('closed') == '1', 'أغلق النافذة الأصلية أولاً')
                operations.closed(g.workspace, key)
            elif action == 'cancel':
                operations.cancel(g.workspace, key)
            else:
                require(False, 'عملية ملف غير صالحة')
            return redirect(url_for('iphone.artifact_operation', key=key), code=303)
        return render_template('artifact_operation.html', operation=operation,
                               key=key, message=STATES[operation.state], page='reports')

    @native.get('/artifact/<key>/status')
    def artifact_status(key):
        operation = operations.get(g.workspace, key)
        return jsonify(state=operation.state, message=STATES[operation.state])
