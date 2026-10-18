"""Office-free paginated Arabic PDF. Logical clinical text is never reshaped in storage."""
from __future__ import annotations
from datetime import datetime
import json
import atexit
import shutil
from pathlib import Path
import tempfile
import arabic_reshaper
from bidi.algorithm import get_display
from reportlab.lib.pagesizes import A4
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import Flowable, SimpleDocTemplate, Spacer
from desktop_protocol import ZoneInfo, require
from desktop_platform import default_pdf_fonts
from desktop_model import DIAGNOSIS, GENDER, badges_decode, task_counts


def visual(text):
    return get_display(arabic_reshaper.reshape(str(text)), base_dir='R')


class RTLText(Flowable):
    def __init__(self, text='', size=11, lines=None):
        Flowable.__init__(self)
        self.text, self.size, self.lines = str(text), size, lines
        self.leading = size * 1.65

    def wrap(self, available_width, available_height):
        self.width = available_width
        if self.lines is None:
            self.lines = []
            for paragraph in self.text.replace('\r\n', '\n').replace('\r', '\n').split('\n'):
                current = ''
                # Character wrapping also handles long identifiers/URLs without truncation.
                for character in paragraph:
                    candidate = current + character
                    if current and pdfmetrics.stringWidth(visual(candidate), 'Arabic', self.size) > available_width:
                        # Prefer a word boundary; preserve all whitespace and remaining characters.
                        boundary = current.rfind(' ')
                        if boundary > len(current)//2:
                            self.lines.append(current[:boundary+1])
                            current = current[boundary+1:] + character
                        else:
                            self.lines.append(current); current = character
                    else:
                        current = candidate
                self.lines.append(current)
        self.height = max(1, len(self.lines)) * self.leading
        return self.width, self.height

    def split(self, width, height):
        count = int(height // self.leading)
        if count < 1 or count >= len(self.lines):
            return []
        return [RTLText(size=self.size, lines=self.lines[:count]), RTLText(size=self.size, lines=self.lines[count:])]

    def draw(self):
        self.canv.setFont('Arabic', self.size)
        for index, line in enumerate(self.lines):
            self.canv.drawRightString(self.width, self.height - (index+1)*self.leading + 3, visual(line))


def render_report(client, shift, supervisor=None, font_path=None):
    candidates = [Path(font_path)] if font_path else default_pdf_fonts()
    font = next((p for p in candidates if p.is_file()), None)
    require(font is not None, 'اختر خط TTF يدعم العربية في PDF_FONT داخل .env')
    pdfmetrics.registerFont(TTFont('Arabic', str(font)))
    patients = [p for p in shift['patients'] if not supervisor or p.get('responsibleSupervisorId') == supervisor]
    require(patients, 'لا يوجد مرضى في التقرير المختار')
    doctors = client.doctors
    now = datetime.now(ZoneInfo(client.zone))
    story = []
    def line(label, value, size=11):
        if value != '' and value is not None:
            story.append(RTLText(label + ': ' + str(value), size))
    line('المشفى', client.config['hospitalName'], 15)
    line('المناوبة', shift['meta']['shiftDate'])
    line('أطباء المناوبة', ' / '.join(doctors.get(d, {}).get('name', 'طبيب محفوظ') for d in shift['meta'].get('doctors', '').split(',') if d))
    line('عدد المرضى', len(patients))
    line('التوزيع', 'ذكور: {} · إناث: {} · مرافقون: {}'.format(sum(p['gender']=='M' for p in patients), sum(p['gender']=='F' for p in patients), sum(p['hasCompanion']=='Y' for p in patients)))
    line('الحالات', ' · '.join('{}: {}'.format(label, sum(p['diagnosisType']==code for p in patients)) for code, label in DIAGNOSIS.items()))
    pending, overdue = task_counts(patients)
    if pending:
        line('المهام', 'معلقة: {} · المتأخرة منها: {}'.format(pending, overdue))
    for i, row in enumerate(patients, 1):
        story.append(Spacer(1, 14))
        line(str(i), row['name'], 14)
        line('العمر', '{} سنة'.format(now.year-int(row['birthDate'][:4])))
        line('القبول', row['admitNum'] + ' · ' + row['admitDate'])
        line('أيام القبول', max(0, (now.date()-datetime.fromisoformat(row['admitDate']).date()).days))
        line('الجنس / المرافق', GENDER[row['gender']] + ' / ' + ('يوجد مرافق' if row['hasCompanion']=='Y' else 'دون مرافق'))
        line('الحالة', DIAGNOSIS[row['diagnosisType']])
        for label, key in [('التشخيص','initialDiagnosis'), ('العلاج','treatmentPlan'), ('المتابعة','followUp'), ('التحاليل','labs')]:
            line(label, row.get(key, ''))
        for label, key in [('المقيم','responsibleResidentId'), ('المشرف','responsibleSupervisorId')]:
            if row.get(key):
                line(label, doctors.get(row[key], {}).get('name', 'طبيب محفوظ'))
        if row.get('isPriority') == 'Y':
            line('الأولوية', 'مريض ذو أولوية')
        for text, priority in badges_decode(row.get('warningDetails', '')):
            line('شارة', text + (' · ' + {'low':'منخفضة','medium':'متوسطة','high':'عالية'}[priority] if priority != 'none' else ''))
        for task in json.loads(row.get('tasksJson') or '[]'):
            status = 'مكتملة' if task.get('done') else 'متأخرة' if task.get('dueAtEpochMillis') and task['dueAtEpochMillis'] <= now.timestamp()*1000 else 'معلقة'
            details = status + ': ' + task['description']
            if task.get('priority') == 'HIGH':
                details += ' · أولوية عالية'
            if task.get('ownerDoctorId'):
                details += ' · ' + doctors.get(task['ownerDoctorId'], {}).get('name', task.get('ownerName') or 'طبيب محفوظ')
            for key, label in [('dueAtEpochMillis', 'الموعد'), ('completedAtEpochMillis', 'أتمت في')]:
                if task.get(key):
                    details += ' · ' + label + ' ' + datetime.fromtimestamp(task[key]/1000, ZoneInfo(client.zone)).strftime('%Y-%m-%d %H:%M')
            if task.get('dueShiftDate'):
                details += ' · نهاية مناوبة ' + task['dueShiftDate']
            if task.get('done') and task.get('completedByName'):
                details += ' · أتمها ' + task['completedByName']
            line('المهام', details)
    directory = Path(tempfile.mkdtemp(prefix='ShiftReportPreview_'))
    atexit.register(shutil.rmtree, str(directory), ignore_errors=True)
    path = directory / 'report.pdf'
    def footer(canvas, document):
        canvas.setFont('Arabic', 9)
        canvas.drawRightString(A4[0]-36, 20, visual('{} · {} · صفحة {}'.format(client.config['hospitalName'], shift['meta']['shiftDate'], document.page)))
    SimpleDocTemplate(str(path), pagesize=A4, rightMargin=36, leftMargin=36, topMargin=32, bottomMargin=38,
                      title='ShiftReport', author='ShiftReport').build(story, onFirstPage=footer, onLaterPages=footer)
    return path
