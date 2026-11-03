"""A5 cards using Android content/nominal geometry; native renderer metrics can differ."""
from __future__ import annotations
from datetime import date
from io import BytesIO
from functools import lru_cache
from unicodedata import bidirectional
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfgen import canvas as pdfcanvas
from reportlab.lib.colors import HexColor
from .protocol import require
from .model import patient_name, DIAGNOSIS, GENDER, badges_decode
from .pdf_content import palette, roster_lines
from .labs import read_groups, heading as lab_heading
from .tasks import read_tasks, ordered_for_display, status_label


WIDTH, HEIGHT = 420, 595
SIDE = 4 * 72 / 25.4
TOP = 12 * 72 / 25.4
BOTTOM = 8 * 72 / 25.4
PADDING, GAP = 5, 4
TITLE_PADDING, NAV_PADDING, NAV_INSET = 2, 2, 2
LEFT, RIGHT = SIDE + PADDING, WIDTH - SIDE - PADDING
TREATMENT_TITLE = 'الخطة العلاجية'
TREATMENT_COLUMN_GAP = 8


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
    def __init__(self, target, client, shift, patients, options, now, total, report_day=None, visual_cache=None):
        from .protocol import current_date
        self.report_day=date.fromisoformat(report_day or current_date(client.zone,now))
        from .pdf import visual
        def directed(value):
            # Match Android FIRSTSTRONG_LTR, including Latin-first DEMO footers.
            direction = 'L'
            for character in str(value):
                kind = bidirectional(character)
                if kind in {'L', 'R', 'AL'}:
                    direction = 'L' if kind == 'L' else 'R'
                    break
            return visual(value, direction)
        self.visual = visual_cache or lru_cache(maxsize=8192)(directed)
        self.client, self.shift, self.patients = client, shift, patients
        self.now, self.total = now, total
        self.local_draft = options.get('local_draft', False)
        self.colors = palette(options)
        title = self.colors['title']
        self.panel_fill = '#' + ''.join('{:02X}'.format(int(int(title[i:i+2], 16)*.12+255*.88)) for i in (1,3,5))
        self.body = _Paint(12.5, self.colors['text'])
        self.heading = _Paint(13, title, True)
        self.name = _Paint(16, '#FFFFFF', True)
        self.nav = _Paint(12, self.colors['text'], True)
        self.footer = _Paint(8.5, self.colors['text'])
        self.urgent = _Paint(13, '#8B1E1E', True)
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

    def draw(self, text, paint, top, right=RIGHT, center=None, underline=False, left=None):
        c = self.canvas
        c.setFont(paint.font, paint.size)
        c.setFillColor(HexColor(paint.color))
        value = self.visual(text)
        baseline = HEIGHT - top - paint.above
        if left is not None:
            c.drawString(left,baseline,value)
        elif center is None:
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
            column_width=(RIGHT-LEFT-GAP)/2
            title_lines=self.wrap('فهرس المرضى',title,column_width)
            date_lines=self.wrap(self.report_day.isoformat(),title,column_width)
            header_height=max(len(title_lines),len(date_lines))*title.height
            page_top=max(page_top,header_height+2*TITLE_PADDING)
            header_top=(page_top-header_height)/2
            for n,line in enumerate(title_lines):
                self.draw(line,title,header_top+n*title.height)
            for n,line in enumerate(date_lines):
                self.draw(line,title,header_top+n*title.height,left=LEFT)
        c.setFillColor(HexColor(self.colors['page']))
        c.roundRect(SIDE, BOTTOM, WIDTH - 2 * SIDE, HEIGHT - page_top - BOTTOM, 4, stroke=1, fill=1)
        self.y = page_top + PADDING
        card_bottom = HEIGHT - BOTTOM
        self.bottom = card_bottom - PADDING
        if not index:
            self.bottom = card_bottom - NAV_INSET - self.nav.height - 2 * NAV_PADDING - GAP
        label = ['DEMO — بيانات تجريبية'] if getattr(self.client, 'demo', False) else []
        if self.local_draft:label.append('مسودة محلية • لم يُعتمد نشرها')
        label += ['ShiftReport', '{}/{}'.format(self.page, self.total)]
        offset = self.now.strftime('%z')
        zone = 'GMT' + offset[:3] + ':' + offset[3:]
        generated = 'أُنشئ في: ' + self.now.strftime('%Y-%m-%d %H:%M ') + zone
        label = ' • '.join(label + [generated])
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
        self.y += 2

    def header(self, index):
        lines = self.wrap(patient_name(self.patients[index]), self.name, RIGHT - LEFT)
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
        paint = _Paint(self.body.size, self.body.color, True)
        x_padding, y_padding, badge_gap = 4, 2, 4
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
                        background = '#FDE8E8' if high else self.colors['summary']
                        self.canvas.setFillColor(HexColor(background))
                        self.canvas.setStrokeColor(HexColor(foreground if high else self.colors['border']))
                        self.canvas.setLineWidth(.5)
                        self.canvas.roundRect(end - width, HEIGHT - self.y - height, width, height, 5, stroke=1, fill=1)
                        text_paint = _Paint(self.body.size, foreground, True)
                        for n, line in enumerate(chunk):
                            self.draw(line, text_paint, self.y + (height - len(chunk) * paint.height) / 2 + n * paint.height, right=end - x_padding)
                    end -= width + badge_gap
                self.y += height + 3
                offset += take
                if offset < count:
                    self.new_page(); self.header(index)
        cells, used = [], 0
        for value, priority in badges:
            if not value.strip():
                continue
            high = priority == 'high'
            label = value + (' • عالية' if high else '')
            measured = max(pdfmetrics.stringWidth(self.visual(line), paint.font, paint.size) for line in label.split('\n'))
            width = min(maximum_width, measured + 2 * x_padding)
            if cells and used + badge_gap + width > RIGHT - LEFT + .01:
                draw_row(cells); cells, used = [], 0
            cells.append((width, self.wrap(label, paint, width - 2 * x_padding), high))
            used += (badge_gap if len(cells) > 1 else 0) + width
        if cells:
            draw_row(cells)

    def panel_columns(self, index, row, sections):
        width = (RIGHT - LEFT - TREATMENT_COLUMN_GAP) / 2
        panel_gap = 3
        column_y = [self.y, self.y]
        column = 0
        for title, value, urgent, keep_together, always_show in sections:
            lines = self.wrap(value, self.body, width) if value.strip() else []
            offset, initial = 0, True
            while initial or offset < len(lines):
                labels = self.wrap(title + (' (تابع)' if offset else ''), self.heading, width)
                label_height = len(labels) * self.heading.height
                minimum = panel_gap + label_height + (self.body.height if offset < len(lines) else 0)
                fresh_top = TOP + PADDING + len(self.wrap(patient_name(row), self.name, RIGHT - LEFT)) * self.name.height + 2 * TITLE_PADDING + GAP
                full_height = panel_gap + label_height + (len(lines) - offset) * self.body.height + panel_gap
                required = full_height if initial and keep_together and full_height <= self.bottom - fresh_top else minimum
                # Source order follows the right column down, then the left, then the next page.
                while column_y[column] + required > self.bottom:
                    if column == 0:
                        column = 1
                    else:
                        self.new_page(); self.header(index)
                        column_y = [self.y, self.y]
                        column = 0
                        require(self.y + required <= self.bottom, 'عنوان القسم أطول من مساحة البطاقة')
                top = column_y[column] + panel_gap
                require(top + label_height + (self.body.height if offset < len(lines) else 0) <= self.bottom,
                        'عنوان القسم أطول من مساحة البطاقة')
                end = RIGHT - column * (width + TREATMENT_COLUMN_GAP)
                self.canvas.setStrokeColor(HexColor(self.colors['rule']))
                self.canvas.setLineWidth(.5)
                self.canvas.line(end - width + 2, HEIGHT - column_y[column], end - 2, HEIGHT - column_y[column])
                self.canvas.setFillColor(HexColor(self.panel_fill))
                self.canvas.rect(end - width, HEIGHT - top - label_height, width, label_height, stroke=0, fill=1)
                for n, label in enumerate(labels):
                    self.draw(label, self.heading, top + n * self.heading.height, right=end)
                capacity = max(0, int((self.bottom - top - label_height) / self.body.height))
                take = min(capacity, len(lines) - offset)
                require(offset == len(lines) or take > 0, 'لا توجد مساحة لعرض المحتوى')
                for n in range(take):
                    self.draw(lines[offset + n], self.body, top + label_height + n * self.body.height, right=end)
                offset += take
                column_y[column] = top + label_height + take * self.body.height + panel_gap
                initial = False
        self.y = max(column_y)

    def content(self, row):
        personal = GENDER.get(row.get('gender', 'M'), 'ذكر')
        birth = row.get('birthDate')
        if birth:
            birthday = date.fromisoformat(birth) if len(birth) > 4 else date(int(birth), 1, 1)
            age = max(0, self.report_day.year - birthday.year - ((self.report_day.month, self.report_day.day) < (birthday.month, birthday.day)))
            personal += '\n{} • {} سنة'.format(birth[:4], age)
        if row.get('isPriority') == 'Y':
            personal += '\n[أولوية]'
        if row.get('hasCompanion') == 'Y':
            personal += '\n(مرافق)'
        admit = ''
        if row.get('admitDate'):
            admission = date.fromisoformat(row['admitDate'])
            admit += 'يوم {}\n{}'.format(max(0, (self.report_day - admission).days), admission)
        doctors = self.client.doctors
        team = '\n'.join(doctors.get(row[key], {}).get('name') or 'طبيب محفوظ' for key in
                                ['responsibleSupervisorId', 'responsibleResidentId'] if row.get(key))
        sections = [('التشخيص • ' + DIAGNOSIS[row.get('diagnosisType', 'PSYCHIATRIC')], row.get('initialDiagnosis', ''), False, False, True),
                    (TREATMENT_TITLE, row.get('treatmentPlan', ''), False, False, False),
                    ('المتابعة', row.get('followUp', ''), False, False, False)]
        tasks = ordered_for_display(read_tasks(row))
        groups = {}
        for task in tasks:
            groups.setdefault(status_label(task), []).append(task)
        task_lines = []
        for status, group in groups.items():
            task_lines.append('{} ({})'.format(status, len(group)))
            for task in group:
                task_lines.append('• ' + task['description'])
        for group in read_groups(row):
            label = ' • '.join(value for value in ('التحاليل', lab_heading(group)) if value)
            sections.append((label, group['results'], False, True, bool(lab_heading(group))))
        sections.append(('المهام ({})'.format(len(tasks)), '\n'.join(task_lines), False, True, False))
        return personal, admit, team, sections

    def render(self):
        self.new_page(index=True)
        self.index_text('المرضى: {} (حالات نفسية: {}) • المرافقون: {}'.format(len(self.patients),
            sum(p.get('diagnosisType', 'PSYCHIATRIC') in {'PSYCHIATRIC', 'DUAL'} for p in self.patients),
            sum(p.get('hasCompanion') == 'Y' for p in self.patients)),self.heading)
        doctors = [self.client.doctors[key] for key in self.shift['meta'].get('doctors', '').split(',') if key in self.client.doctors]
        if doctors:
            for line in roster_lines(doctors):
                self.index_text(line,_Paint(11.5,self.body.color))
        index_gap, tile_padding, height = 5, 2, 28
        tile_width = (RIGHT - LEFT - 2 * index_gap) / 3
        first = 0
        while first < len(self.patients):
            if self.y + height > self.bottom:
                self.new_page(index=True)
            capacity = int((self.bottom - self.y + 1) / (height + 1))
            require(capacity > 0, 'اسم المريض أطول من مساحة صفحة الفهرس')
            count = min(len(self.patients) - first, capacity * 3)
            rows = (count + 2) // 3
            grid_top = self.y
            # Fill the rightmost column downward, then move left.
            for offset in range(count):
                patient = self.patients[first + offset]
                value = '{} • {}'.format(first + offset + 1, patient_name(patient))
                paint = _Paint(15, self.heading.color, True)
                available_width = tile_width - 2 * tile_padding
                available_height = height - 2 * tile_padding
                def fits(paint):
                    return len(self.wrap(value, paint, available_width)) * paint.height <= available_height
                if not fits(paint):
                    low, high = 1, 15
                    for _ in range(18):
                        candidate = _Paint((low + high) / 2, self.heading.color, True)
                        if fits(candidate):
                            low = candidate.size
                        else:
                            high = candidate.size
                    paint = _Paint(low, self.heading.color, True)
                require(fits(paint), 'اسم المريض أطول من مساحة خلية الفهرس')
                wrapped = self.wrap(value, paint, available_width)
                col, row = divmod(offset, rows)
                top = grid_top + row * (height + 1)
                end = RIGHT - col * (tile_width + index_gap)
                self.canvas.bookmarkPage('entry-' + str(first + offset), fit='Fit')
                self.canvas.setStrokeColor(HexColor(self.colors['border']))
                self.canvas.setLineWidth(.5)
                if col==1:
                    self.canvas.setFillColor(HexColor(self.panel_fill))
                    self.canvas.roundRect(end-tile_width,HEIGHT-top-height,tile_width,height,3,stroke=0,fill=1)
                self.canvas.roundRect(end - tile_width, HEIGHT - top - height, tile_width, height, 3, stroke=1, fill=0)
                self.canvas.saveState()
                clip = self.canvas.beginPath()
                clip.rect(end - tile_width + tile_padding, HEIGHT - top - height + tile_padding,
                          tile_width - 2 * tile_padding, height - 2 * tile_padding)
                self.canvas.clipPath(clip, stroke=0, fill=0)
                text_top = top + (height - len(wrapped) * paint.height) / 2
                for n, line in enumerate(wrapped):
                    self.draw(line, paint, text_top + n * paint.height, right=end - tile_padding)
                self.canvas.restoreState()
                self.link('patient-' + str(first + offset), (end - tile_width, top, end, top + height))
            first += count
            self.y = grid_top + rows * (height + 1)
        for index, row in enumerate(self.patients):
            self.new_page()
            self.canvas.bookmarkPage('patient-' + str(index), fit='Fit')
            self.header(index)
            personal, admit, team, sections = self.content(row)
            gap = 6
            column = (RIGHT - LEFT - 2 * gap) / 3
            intro_columns = [('الفريق المسؤول', team, LEFT + column),
                             ('القبول', admit, LEFT + 2 * column + gap),
                             ('بيانات المريض', personal, RIGHT)]
            admission_paint = _Paint(self.heading.size, self.body.color, True)
            admission_number = row.get('admitNum', '')
            admission_offset = pdfmetrics.stringWidth(self.visual('القبول'), self.heading.font, self.heading.size) + pdfmetrics.stringWidth(' ', self.heading.font, self.heading.size)
            admission_inline = bool(admission_number.strip()) and '\n' not in admission_number and '\r' not in admission_number and admission_offset + pdfmetrics.stringWidth(self.visual(admission_number), admission_paint.font, admission_paint.size) <= column
            intro_titles = [self.wrap(title, self.heading, column) for title, _, _ in intro_columns]
            if admission_number.strip() and not admission_inline:
                intro_titles[1] = ['القبول'] + self.wrap(admission_number, admission_paint, column)
            intro_title_height = max(map(len, intro_titles)) * self.heading.height
            def intro_headings():
                require(self.y + intro_title_height + GAP + self.body.height <= self.bottom,
                        'اسم المريض أطول من مساحة البطاقة')
                for col, labels in enumerate(intro_titles):
                    for n, label in enumerate(labels):
                        paint = admission_paint if col == 1 and n > 0 else self.heading
                        self.draw(label, paint, self.y + n * self.heading.height, right=intro_columns[col][2])
                if admission_inline:
                    self.draw(admission_number, admission_paint, self.y, right=intro_columns[1][2] - admission_offset)
                self.y += intro_title_height + GAP
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
            visible = [section for section in sections if section[4] or section[1].strip()]
            self.panel_columns(index, row, visible)
        self.canvas.save()
        return self.page


def render_cards(client, shift, supervisor, font_path, options, now, report_day=None):
    from .pdf import register_font, temporary_pdf
    register_font(font_path)
    patients = [p for p in shift['patients'] if supervisor is None or p.get('responsibleSupervisorId') == supervisor]
    require(patients, 'لا يوجد مرضى في التقرير المختار')
    counting = _Cards(BytesIO(), client, shift, patients, options, now, 0, report_day)
    from .report_labels import report_filename
    from .protocol import current_date
    try:
        total = counting.render()
        path = temporary_pdf(report_filename(report_day or current_date(client.zone,now),supervisor,client.doctors.get(supervisor,{}).get('name'),
            draft=options.get('local_draft',False),demo=bool(getattr(client,'demo',False))))
        require(_Cards(str(path), client, shift, patients, options, now, total, report_day,
                       visual_cache=counting.visual).render() == total,
                'تغير عدد صفحات التقرير؛ أعد التصدير')
        return path
    finally:
        # Reuse Arabic shaping only inside this report's two rendering passes.
        # Do not keep patient text in a process-global cache after the report.
        counting.visual.cache_clear()
