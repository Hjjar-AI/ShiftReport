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
TITLE_PADDING, NAV_PADDING, NAV_INSET = 3, 3, 3
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
        self.dark = options['dark']
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
        c.setFillColor(HexColor(self.colors['page']))
        c.roundRect(SIDE, BOTTOM, WIDTH - 2 * SIDE, HEIGHT - page_top - BOTTOM, 4, stroke=1, fill=1)
        self.y = page_top + PADDING
        card_bottom = HEIGHT - BOTTOM
        self.bottom = card_bottom - PADDING
        if not index:
            self.bottom = card_bottom - NAV_INSET - self.nav.height - 2 * NAV_PADDING - GAP
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
        height = len(lines) * self.name.height + 2 * TITLE_PADDING
        require(self.y + height + GAP + 2 * self.heading.height <= self.bottom,
                'اسم المريض أطول من مساحة البطاقة')
        top = self.y
        c = self.canvas
        c.setFillColor(HexColor(self.colors['title']))
        c.rect(SIDE, HEIGHT - top - height, WIDTH - 2 * SIDE, height, stroke=0, fill=1)
        self.y += TITLE_PADDING
        for line in lines:
            self.draw(line, self.name, self.y, center=WIDTH / 2)
            self.y += self.name.height
        self.y = top + height + GAP
        self.link('entry-' + str(index), (SIDE, top, WIDTH - SIDE, top + height))
        nav_bottom = HEIGHT - BOTTOM - NAV_INSET
        nav_top = nav_bottom - self.nav.height - 2 * NAV_PADDING
        cell = (RIGHT - LEFT) / 3
        targets = [('السابق', index - 1 if index else None), ('الفهرس', index),
                   ('التالي', index + 1 if index + 1 < len(self.patients) else None)]
        for column, (label, target) in enumerate(targets):
            right = RIGHT - column * cell
            active = target is not None
            self.draw(label if active else '—', self.nav, nav_top + NAV_PADDING,
                      center=right - cell / 2, underline=active)
            if active:
                key = ('entry-' if column == 1 else 'patient-') + str(target)
                self.link(key, (right - cell, nav_top, right, nav_bottom))

    def patient_badges(self, index, row):
        paint = _Paint(11.5, self.body.color, True)
        x_padding, y_padding, badge_gap = 6, 4, 6
        badges = [(text, priority) for text, priority in badges_decode(row.get('warningDetails', '')) if text.strip()]
        maximum_width = (RIGHT - LEFT - badge_gap) / 2 if len(badges) > 1 else RIGHT - LEFT
        def draw_row(cells):
            count = max(len(cell[1]) for cell in cells)
            if self.y + count * paint.height + 2 * y_padding > self.bottom:
                self.new_page(); self.header(index)
            offset = 0
            while offset < count:
                capacity = int((self.bottom - self.y - 2 * y_padding) / paint.height)
                if capacity < 1:
                    self.new_page(); self.header(index)
                    capacity = int((self.bottom - self.y - 2 * y_padding) / paint.height)
                require(capacity > 0, 'لا توجد مساحة لعرض شارة المريض')
                take = min(capacity, count - offset)
                height = take * paint.height + 2 * y_padding
                end = RIGHT
                for width, lines, high in cells:
                    chunk = lines[offset:offset + take]
                    if chunk:
                        foreground = self.urgent.color if high else self.body.color
                        background = ('#4D2525' if self.dark else '#FDE8E8') if high else self.colors['summary']
                        self.canvas.setFillColor(HexColor(background))
                        self.canvas.setStrokeColor(HexColor(foreground if high else self.colors['border']))
                        self.canvas.setLineWidth(.5)
                        self.canvas.roundRect(end - width, HEIGHT - self.y - height, width, height, 5, stroke=1, fill=1)
                        text_paint = _Paint(11.5, foreground, True)
                        for n, line in enumerate(chunk):
                            self.draw(line, text_paint, self.y + (height - len(chunk) * paint.height) / 2 + n * paint.height, right=end - x_padding)
                    end -= width + badge_gap
                self.y += height + 4
                offset += take
                if offset < count:
                    self.new_page(); self.header(index)
        cells, used = [], 0
        for value, priority in badges:
            if not value.strip():
                continue
            high = priority == 'high'
            label = value + (' · عالية' if high else '')
            measured = max(pdfmetrics.stringWidth(self.visual(line), paint.font, paint.size) for line in label.split('\n'))
            width = min(maximum_width, measured + 2 * x_padding)
            if cells and used + badge_gap + width > RIGHT - LEFT + .01:
                draw_row(cells); cells, used = [], 0
            cells.append((width, self.wrap(label, paint, width - 2 * x_padding), high))
            used += (badge_gap if len(cells) > 1 else 0) + width
        if cells:
            draw_row(cells)

    def content(self, row):
        personal = '[أولوية]' if row.get('isPriority') == 'Y' else ''
        birth = row.get('birthDate')
        if birth:
            personal += ('\n' if personal else '') + '{} • {} سنة'.format(birth[:4], max(0, self.now.year - int(birth[:4])))
        personal += (' • ' if personal else '') + GENDER.get(row.get('gender', 'M'), 'ذكر')
        if row.get('hasCompanion') == 'Y':
            personal += '\n(مرافق)'
        admit = row.get('admitNum', '') + '\n---\n'
        if row.get('admitDate'):
            admission = date.fromisoformat(row['admitDate'])
            admit += 'يوم {}\n{}'.format(max(0, (self.now.date() - admission).days), admission)
        doctors = self.client.doctors
        team = '\n---\n'.join(doctors.get(row[key], {}).get('name') or 'طبيب محفوظ' for key in
                                ['responsibleSupervisorId', 'responsibleResidentId'] if row.get(key))
        sections = [('التشخيص · ' + DIAGNOSIS[row.get('diagnosisType', 'PSYCHIATRIC')], row.get('initialDiagnosis', ''), False, False, True),
                    ('الخطة العلاجية', row.get('treatmentPlan', ''), False, False, False),
                    ('المتابعة', row.get('followUp', ''), False, False, False)]
        tasks = ordered_for_display(read_tasks(row), self.now_ms)
        groups = {}
        for task in tasks:
            groups.setdefault(status_label(task, self.now_ms), []).append(task)
        task_lines = []
        for status, group in groups.items():
            task_lines.append('{} ({})'.format(status, len(group)))
            for task in group:
                details = metadata(task, doctors, self.client.zone, include_unassigned=False, include_routine_priority=False)
                task_lines.append('• ' + ' — '.join(filter(None, [task['description'], details])))
        urgent = any(overdue(task, self.now_ms) or not task.get('done') and task.get('priority') == 'HIGH' for task in tasks)
        sections.append(('المهام ({})'.format(len(tasks)), '\n'.join(task_lines), urgent, True, False))
        sections.append(('التحاليل', row.get('labs', ''), False, False, False))
        return personal, admit, team, sections

    def render(self):
        self.new_page(index=True)
        self.index_text('{} · {} مريض · {} حالة نفسية · {} مرافق'.format(self.shift['meta']['shiftDate'], len(self.patients),
            sum(p.get('diagnosisType', 'PSYCHIATRIC') in {'PSYCHIATRIC', 'DUAL'} for p in self.patients),
            sum(p.get('hasCompanion') == 'Y' for p in self.patients)), self.body)
        doctors = [self.client.doctors[key] for key in self.shift['meta'].get('doctors', '').split(',') if key in self.client.doctors]
        if doctors:
            self.index_text(roster_label(doctors) + ': ' + ' / '.join(d['name'] for d in doctors), self.body)
        index_gap, tile_padding, height = 8, 2, 28
        tile_width = (RIGHT - LEFT - 2 * index_gap) / 3
        for start in range(0, len(self.patients), 3):
            row = self.patients[start:start + 3]
            fitted = []
            for col, patient in enumerate(row):
                value = '{} · {}'.format(start + col + 1, patient['name'])
                paint = _Paint(14, self.heading.color, True)
                available_width = tile_width - 2 * tile_padding
                available_height = height - 2 * tile_padding
                def fits(paint):
                    return len(self.wrap(value, paint, available_width)) * paint.height <= available_height
                if not fits(paint):
                    low, high = 1, 14
                    for _ in range(18):
                        candidate = _Paint((low + high) / 2, self.heading.color, True)
                        if fits(candidate):
                            low = candidate.size
                        else:
                            high = candidate.size
                    paint = _Paint(low, self.heading.color, True)
                require(fits(paint), 'اسم المريض أطول من مساحة خلية الفهرس')
                fitted.append((paint, self.wrap(value, paint, available_width)))
            if self.y + height > self.bottom:
                self.new_page(index=True)
            require(self.y + height <= self.bottom, 'اسم المريض أطول من مساحة صفحة الفهرس')
            top = self.y
            for col, (index_link, wrapped) in enumerate(fitted):
                end = RIGHT - col * (tile_width + index_gap)
                self.canvas.bookmarkPage('entry-' + str(start + col), fit='Fit')
                self.canvas.setStrokeColor(HexColor(self.colors['border']))
                self.canvas.setLineWidth(.5)
                self.canvas.roundRect(end - tile_width, HEIGHT - top - height, tile_width, height, 3, stroke=1, fill=0)
                self.canvas.saveState()
                clip = self.canvas.beginPath()
                clip.rect(end - tile_width + tile_padding, HEIGHT - top - height + tile_padding,
                          tile_width - 2 * tile_padding, height - 2 * tile_padding)
                self.canvas.clipPath(clip, stroke=0, fill=0)
                text_top = top + (height - len(wrapped) * index_link.height) / 2
                for n, line in enumerate(wrapped):
                    self.draw(line, index_link, text_top + n * index_link.height, right=end - tile_padding)
                self.canvas.restoreState()
                self.link('patient-' + str(start + col), (end - tile_width, top, end, top + height))
            self.y = top + height + 1
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
            self.patient_badges(index, row)
            for title, value, urgent, keep_together, always_show in sections:
                if not always_show and not value.strip():
                    continue
                paint = self.urgent if urgent else self.heading
                lines = self.wrap(value, self.body, RIGHT - LEFT) if value.strip() else []
                needed = GAP + self.heading.height + len(lines) * self.body.height + GAP
                fresh_top = TOP + PADDING + len(self.wrap(row['name'], self.name, RIGHT - LEFT)) * self.name.height + 2 * TITLE_PADDING + GAP
                move_group = keep_together and needed <= self.bottom - fresh_top and self.y + needed > self.bottom
                if move_group or self.y + GAP + self.heading.height + (self.body.height if lines else 0) > self.bottom:
                    self.new_page(); self.header(index)
                self.canvas.setStrokeColor(HexColor(self.colors['rule']))
                self.canvas.setLineWidth(self.colors['rule_width'])
                self.canvas.line(LEFT, HEIGHT - self.y, RIGHT, HEIGHT - self.y)
                self.y += GAP
                self.line(title, paint)
                for line in lines:
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
