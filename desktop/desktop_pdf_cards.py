"""A5 canvas layout matching Android PdfPatientCardsRenderer, in PDF points."""
from __future__ import annotations
from datetime import date
from io import BytesIO
from unicodedata import bidirectional
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfgen import canvas as pdfcanvas
from reportlab.lib.colors import HexColor
from desktop_protocol import require
from desktop_model import DIAGNOSIS, GENDER, badges_decode
from desktop_pdf_content import palette, roster_label
from desktop_tasks import read_tasks, ordered_for_display, status_label, metadata, overdue


WIDTH, HEIGHT = 420, 595
SIDE = 5 * 72 / 25.4
TOP = 20 * 72 / 25.4
BOTTOM = 10 * 72 / 25.4
PADDING, GAP = 8, 6
LEFT, RIGHT = SIDE + PADDING, WIDTH - SIDE - PADDING


class _Paint:
    def __init__(self, size, color, bold=False):
        self.size, self.color = size, color
        self.font = 'ArabicBold' if bold else 'Arabic'
        # Android Paint.top/bottom use the same bundled face's full glyph bounds.
        bounds = pdfmetrics.getFont(self.font).face.bbox
        self.above = bounds[3] * size / 1000
        self.below = -bounds[1] * size / 1000
        self.height = self.above + self.below


class _Cards:
    def __init__(self, target, client, shift, patients, options, now, total):
        from desktop_pdf import visual
        def directed(value):
            # Match Android FIRSTSTRONG_LTR, including Latin-first DEMO footers.
            direction = 'L'
            for character in str(value):
                kind = bidirectional(character)
                if kind in {'L', 'R', 'AL'}:
                    direction = 'L' if kind == 'L' else 'R'
                    break
            return visual(value, direction)
        self.visual = directed
        self.client, self.shift, self.patients = client, shift, patients
        self.now, self.total = now, total
        self.now_ms = int(now.timestamp() * 1000)
        self.colors = palette(options)
        title = self.colors['text'] if options['dark'] else self.colors['title']
        self.body = _Paint(11.5, self.colors['text'])
        self.heading = _Paint(12, title, True)
        self.name = _Paint(15, '#FFFFFF', True)
        self.nav = _Paint(12, self.colors['text'], True)
        self.footer = _Paint(8.5, self.colors['text'])
        self.urgent = _Paint(12, '#FFB4AB' if options['dark'] else '#8B1E1E', True)
        self.canvas = pdfcanvas.Canvas(target, pagesize=(WIDTH, HEIGHT))
        self.canvas.setTitle('DEMO — ShiftReport' if getattr(client, 'demo', False) else 'ShiftReport')
        self.canvas.setAuthor('ShiftReport')
        self.page = 0

    def wrap(self, value, paint, width):
        lines, current = [], ''
        def fits(text):
            return pdfmetrics.stringWidth(self.visual(text), paint.font, paint.size) <= width
        for index, paragraph in enumerate(str(value).replace('\r\n', '\n').replace('\r', '\n').split('\n')):
            if index:
                lines.append(current)
                current = ''
            for word in filter(None, paragraph.split(' ')):
                candidate = current + ' ' + word if current else word
                if fits(candidate):
                    current = candidate
                    continue
                if current:
                    lines.append(current)
                    current = ''
                while not fits(word) and word:
                    cut = 1
                    while cut < len(word) and fits(word[:cut + 1]):
                        cut += 1
                    lines.append(word[:cut])
                    word = word[cut:]
                current = word
        if current:
            lines.append(current)
        return lines or ['']

    def draw(self, text, paint, top, right=RIGHT, center=None, underline=False):
        c = self.canvas
        c.setFont(paint.font, paint.size)
        c.setFillColor(HexColor(paint.color))
        value = self.visual(text)
        baseline = HEIGHT - top - paint.above
        if center is None:
            c.drawRightString(right, baseline, value)
        else:
            c.drawCentredString(center, baseline, value)
            if underline:
                half = pdfmetrics.stringWidth(value, paint.font, paint.size) / 2
                c.setStrokeColor(HexColor(paint.color))
                c.setLineWidth(.5)
                c.line(center - half, baseline - 1, center + half, baseline - 1)

    def link(self, key, rect):
        left, top, right, bottom = rect
        self.canvas.linkRect('', key, (left, HEIGHT - bottom, right, HEIGHT - top), thickness=0)

    def new_page(self, index=False):
        if self.page:
            self.canvas.showPage()
        self.page += 1
        c = self.canvas
        c.setFillColor(HexColor(self.colors['page']))
        c.rect(0, 0, WIDTH, HEIGHT, stroke=0, fill=1)
        c.setStrokeColor(HexColor(self.colors['border']))
        c.setLineWidth(.5)
        page_top = 10 * 72 / 25.4 if index else TOP
        if index:
            title = _Paint(15, self.heading.color, True)
            self.draw('فهرس المرضى', title, (page_top - title.height) / 2)
        c.roundRect(SIDE, BOTTOM, WIDTH - 2 * SIDE, HEIGHT - page_top - BOTTOM, 4, stroke=1, fill=1)
        self.y = page_top + PADDING
        card_bottom = HEIGHT - BOTTOM
        self.bottom = card_bottom - PADDING
        if not index:
            self.bottom -= self.nav.height + 2 * PADDING + GAP
        label = ['DEMO — بيانات تجريبية'] if getattr(self.client, 'demo', False) else []
        label += ['ShiftReport', '{}/{}'.format(self.page, self.total)]
        offset = self.now.strftime('%z')
        zone = 'GMT' + offset[:3] + ':' + offset[3:]
        generated = 'أُنشئ في: ' + self.now.strftime('%Y-%m-%d %H:%M ') + zone
        label = ' · '.join(label + [generated])
        measured = pdfmetrics.stringWidth(self.visual(label), self.footer.font, self.footer.size)
        footer = _Paint(self.footer.size * min(1, (WIDTH - 2 * SIDE) / max(1, measured)), self.footer.color)
        footer_top = HEIGHT - BOTTOM / 2 - footer.height / 2
        self.draw(label, footer, footer_top, center=WIDTH / 2)

    def line(self, value, paint):
        self.draw(value, paint, self.y)
        self.y += paint.height

    def index_text(self, value, paint):
        for line in self.wrap(value, paint, RIGHT - LEFT):
            if self.y + paint.height > self.bottom:
                self.new_page(index=True)
            self.line(line, paint)
        self.y += 3

    def header(self, index):
        lines = self.wrap(self.patients[index]['name'], self.name, RIGHT - LEFT)
        height = len(lines) * self.name.height + 2 * PADDING
        require(self.y + height + GAP + 2 * self.heading.height <= self.bottom,
                'اسم المريض أطول من مساحة البطاقة')
        top = self.y
        c = self.canvas
        c.setFillColor(HexColor(self.colors['title']))
        c.rect(SIDE, HEIGHT - top - height, WIDTH - 2 * SIDE, height, stroke=0, fill=1)
        for line in lines:
            self.draw(line, self.name, self.y, center=WIDTH / 2)
            self.y += self.name.height
        self.y = top + height + GAP
        self.link('entry-' + str(index), (SIDE, top, WIDTH - SIDE, top + height))
        nav_bottom = HEIGHT - BOTTOM - PADDING
        nav_top = nav_bottom - self.nav.height - 2 * PADDING
        cell = (RIGHT - LEFT) / 3
        targets = [('السابق', index - 1 if index else None), ('الفهرس', index),
                   ('التالي', index + 1 if index + 1 < len(self.patients) else None)]
        for column, (label, target) in enumerate(targets):
            right = RIGHT - column * cell
            active = target is not None
            self.draw(label if active else '—', self.nav, nav_top + PADDING,
                      center=right - cell / 2, underline=active)
            if active:
                key = ('entry-' if column == 1 else 'patient-') + str(target)
                self.link(key, (right - cell, nav_top, right, nav_bottom))

    def content(self, row):
        personal = ('[أولوية] ' if row.get('isPriority') == 'Y' else '') + row['name']
        birth = row.get('birthDate')
        if birth:
            personal += '\n{} • {} سنة'.format(birth[:4], max(0, self.now.year - int(birth[:4])))
        personal += ' • ' + GENDER.get(row.get('gender', 'M'), 'ذكر')
        if row.get('hasCompanion') == 'Y':
            personal += '\n(مرافق)'
        admit = row.get('admitNum', '') + '\n---\n'
        if row.get('admitDate'):
            admission = date.fromisoformat(row['admitDate'])
            admit += 'يوم {}\n{}'.format(max(0, (self.now.date() - admission).days), admission)
        doctors = self.client.doctors
        team = '\n---\n'.join(doctors.get(row[key], {}).get('name') or 'طبيب محفوظ' for key in
                                ['responsibleSupervisorId', 'responsibleResidentId'] if row.get(key))
        diagnosis = [DIAGNOSIS[row.get('diagnosisType', 'PSYCHIATRIC')]]
        diagnosis += ['⚠ ' + text + (' · عالية' if priority == 'high' else '')
                      for text, priority in badges_decode(row.get('warningDetails', ''))]
        if row.get('initialDiagnosis'):
            diagnosis.append(row['initialDiagnosis'])
        sections = [('التشخيص / التنبيهات', '\n'.join(diagnosis), False),
                    ('الخطة العلاجية', row.get('treatmentPlan', ''), False), ('المتابعة', row.get('followUp', ''), False)]
        for task in ordered_for_display(read_tasks(row), self.now_ms):
            details = metadata(task, doctors, self.client.zone, include_unassigned=False, include_routine_priority=False)
            urgent = overdue(task, self.now_ms) or not task.get('done') and task.get('priority') == 'HIGH'
            sections.append(('المهام · ' + status_label(task, self.now_ms),
                             '\n'.join(filter(None, [task['description'], details])), urgent))
        sections.append(('التحاليل', row.get('labs', ''), False))
        return personal, admit, team, sections

    def render(self):
        self.new_page(index=True)
        index_link = _Paint(14, self.heading.color, True)
        self.index_text('{} · {} مريض · {} حالة نفسية · {} مرافق'.format(self.shift['meta']['shiftDate'], len(self.patients),
            sum(p.get('diagnosisType', 'PSYCHIATRIC') in {'PSYCHIATRIC', 'DUAL'} for p in self.patients),
            sum(p.get('hasCompanion') == 'Y' for p in self.patients)), self.body)
        doctors = [self.client.doctors[key] for key in self.shift['meta'].get('doctors', '').split(',') if key in self.client.doctors]
        if doctors:
            self.index_text(roster_label(doctors) + ': ' + ' / '.join(d['name'] for d in doctors), self.body)
        index_gap, tile_padding = 8, 2
        tile_width = (RIGHT - LEFT - 2 * index_gap) / 3
        for start in range(0, len(self.patients), 3):
            row = self.patients[start:start + 3]
            lines = [self.wrap('{} · {}'.format(start + col + 1, patient['name']), index_link,
                               tile_width - 2 * tile_padding) for col, patient in enumerate(row)]
            height = max(map(len, lines)) * index_link.height + 2 * tile_padding
            if self.y + height > self.bottom:
                self.new_page(index=True)
            require(self.y + height <= self.bottom, 'اسم المريض أطول من مساحة صفحة الفهرس')
            top = self.y
            for col, wrapped in enumerate(lines):
                end = RIGHT - col * (tile_width + index_gap)
                self.canvas.bookmarkPage('entry-' + str(start + col), fit='Fit')
                self.canvas.setStrokeColor(HexColor(self.colors['border']))
                self.canvas.setLineWidth(.5)
                self.canvas.roundRect(end - tile_width, HEIGHT - top - height, tile_width, height, 3, stroke=1, fill=0)
                for n, line in enumerate(wrapped):
                    self.draw(line, index_link, top + tile_padding + n * index_link.height, right=end - tile_padding)
                self.link('patient-' + str(start + col), (end - tile_width, top, end, top + height))
            self.y = top + height + 3
        for index, row in enumerate(self.patients):
            self.new_page()
            self.canvas.bookmarkPage('patient-' + str(index), fit='Fit')
            self.header(index)
            personal, admit, team, sections = self.content(row)
            gap = 10
            column = (RIGHT - LEFT - 2 * gap) / 3
            intro_columns = [('الفريق المسؤول', team, LEFT + column),
                             ('القبول', admit, LEFT + 2 * column + gap),
                             ('بيانات المريض', personal, RIGHT)]
            def intro_headings():
                require(self.y + self.heading.height + GAP + self.body.height <= self.bottom,
                        'اسم المريض أطول من مساحة البطاقة')
                for title, _, end in intro_columns:
                    self.draw(title, self.heading, self.y, right=end)
                self.y += self.heading.height + GAP
            intro_headings()
            intro_lines = [self.wrap(value, self.body, column) for _, value, _ in intro_columns]
            for n in range(max(map(len, intro_lines))):
                if self.y + self.body.height > self.bottom:
                    self.new_page(); self.header(index); intro_headings()
                for col, lines in enumerate(intro_lines):
                    if n < len(lines):
                        self.draw(lines[n], self.body, self.y, right=intro_columns[col][2])
                self.y += self.body.height
            self.y += GAP
            for title, value, urgent in sections:
                if not value.strip():
                    continue
                paint = self.urgent if urgent else self.heading
                if self.y + GAP + self.heading.height + self.body.height > self.bottom:
                    self.new_page(); self.header(index)
                self.canvas.setStrokeColor(HexColor(self.colors['rule']))
                self.canvas.setLineWidth(self.colors['rule_width'])
                self.canvas.line(LEFT, HEIGHT - self.y, RIGHT, HEIGHT - self.y)
                self.y += GAP
                self.line(title, paint)
                for line in self.wrap(value, self.body, RIGHT - LEFT):
                    if self.y + self.body.height > self.bottom:
                        self.new_page(); self.header(index); self.line(title + ' (تابع)', paint)
                    self.line(line, self.body)
                self.y += GAP
        self.canvas.save()
        return self.page


def render_cards(client, shift, supervisor, font_path, options, now):
    from desktop_pdf import register_font, temporary_pdf
    register_font(font_path)
    patients = [p for p in shift['patients'] if supervisor is None or p.get('responsibleSupervisorId') == supervisor]
    require(patients, 'لا يوجد مرضى في التقرير المختار')
    total = _Cards(BytesIO(), client, shift, patients, options, now, 0).render()
    path = temporary_pdf()
    require(_Cards(str(path), client, shift, patients, options, now, total).render() == total,
            'تغير عدد صفحات التقرير؛ أعد التصدير')
    return path
