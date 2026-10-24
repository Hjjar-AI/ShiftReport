package com.hos.rushdpatients.sync

/** Fetch must preserve local edits; presentation offers reviewed publication rather than a retry loop. */
class LocalPatientChangesPendingException : IllegalStateException(
    "توجد تعديلات مرضى محفوظة على هذا الجهاز ولم تُنشر. افتح مراجعة التقرير وانشر بيانات المرضى، ثم ارجع لجلب أحدث نسخة. لم تُحذف تعديلاتك."
)
