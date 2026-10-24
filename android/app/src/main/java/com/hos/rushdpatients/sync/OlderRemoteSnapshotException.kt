package com.hos.rushdpatients.sync

class OlderRemoteSnapshotException : IllegalStateException(
    "النسخة المنشورة أقدم من آخر نسخة معروفة؛ يمكن للمدير تأكيد الاستبدال مع الاحتفاظ بالتراجع المحلي"
)
