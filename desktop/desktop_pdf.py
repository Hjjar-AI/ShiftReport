"""Office-free paginated Arabic PDF. Logical clinical text is never reshaped in storage."""
from __future__ import annotations
from datetime import datetime
import atexit
import shutil
from pathlib import Path
import tempfile
from desktop_report_labels import report_filename
import arabic_reshaper
from bidi.algorithm import get_display
from reportlab.lib.colors import HexColor, white
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import Flowable, SimpleDocTemplate, Spacer, Table, TableStyle, PageBreak
from reportlab.platypus import HRFlowable
from desktop_protocol import ZoneInfo, require
from desktop_platform import default_pdf_fonts
from desktop_model import patient_name, pending_task_count


END_TIMESTAMP_GAP = 30  # Points between final report content and its creation date.


_reshaper = arabic_reshaper.ArabicReshaper(configuration={'use_unshaped_instead_of_isolated': True})


def visual(text, direction='R'):
    # Tajawal maps isolated letters through base Unicode rather than presentation forms.
    return get_display(_reshaper.reshape(str(text)), base_dir=direction)


class RTLText(Flowable):
    def __init__(self, text='', size=11, lines=None, link=None, anchor=None, color=None, align='right', bold=False):
        Flowable.__init__(self)
        self.text, self.size, self.lines = str(text), size, lines
        self.leading = size * 1.65
        self.link, self.anchor, self.color = link, anchor, color
        self.align = align
        self.bold = bold
        self.font = 'ArabicBold' if bold else 'Arabic'

    def wrap(self, available_width, available_height):
        self.width = available_width
        if self.lines is None:
            self.lines = []
            for paragraph in self.text.replace('\r\n', '\n').replace('\r', '\n').split('\n'):
                current = ''
                # Character wrapping also handles long identifiers/URLs without truncation.
                for character in paragraph:
                    candidate = current + character
                    if current and pdfmetrics.stringWidth(visual(candidate), self.font, self.size) > available_width:
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
        return [RTLText(size=self.size, lines=self.lines[:count], link=self.link, anchor=self.anchor, color=self.color, align=self.align, bold=self.bold),
                RTLText(size=self.size, lines=self.lines[count:], link=self.link, color=self.color, align=self.align, bold=self.bold)]

    def draw(self):
        if self.anchor:
            if self.anchor.startswith('entry-'):
                # Return to the containing index page at page-fit, never to the row.
                self.canv.bookmarkPage(self.anchor,fit='Fit')
            else:self.canv.bookmarkHorizontal(self.anchor, 0, self.height)
        if self.link:
            self.canv.linkRect('', self.link, (0, 0, self.width, self.height), relative=1, thickness=0)
        self.canv.setFillColor(self.color or HexColor('#111111'))
        self.canv.setFont(self.font, self.size)
        for index, line in enumerate(self.lines):
            if self.align == 'center':
                self.canv.drawCentredString(self.width/2, self.height - (index+1)*self.leading + 3, visual(line))
            else:
                self.canv.drawRightString(self.width, self.height - (index+1)*self.leading + 3, visual(line))


def register_font(font_path=None):
    candidates = [Path(font_path)] if font_path else default_pdf_fonts()
    font = next((p for p in candidates if p.is_file()), None)
    require(font is not None, 'اختر خط TTF يدعم العربية في PDF_FONT داخل .env')
    pdfmetrics.registerFont(TTFont('Arabic', str(font)))
    bold_stem = font.stem.replace('Regular', 'Bold') if 'Regular' in font.stem else font.stem+'-Bold'
    bold = font.with_name(bold_stem + font.suffix)
    pdfmetrics.registerFont(TTFont('ArabicBold', str(bold if bold != font and bold.is_file() else font)))


def temporary_pdf(display_name='report.pdf'):
    directory = Path(tempfile.mkdtemp(prefix='ShiftReportPreview_'))
    atexit.register(shutil.rmtree, str(directory), ignore_errors=True)
    return directory / display_name


class EndTimestamp(Flowable):
    """Draw once after final content, inside space reserved above the page footer."""
    def __init__(self,label):
        Flowable.__init__(self)
        self.label=label

    def wrap(self,width,height):
        self.label.wrap(width,100000)
        self.width=width
        return width,0

    def draw(self):
        self.label.drawOn(self.canv,0,-END_TIMESTAMP_GAP-self.label.height)


class ReportTable(Table):
    def split(self, available_width, available_height):
        parts = super().split(available_width, available_height)
        # Start the remainder on a new page rather than repeating its header in
        # the small space still available below the first fragment.
        if len(parts)>1:return [parts[0], PageBreak()] + parts[1:]
        return parts


def styled_table(data, widths, colors, repeat=0, backgrounds=None, guides=False, padding=3, row_heights=None):
    table = ReportTable(data, colWidths=widths, repeatRows=repeat, splitInRow=1, rowHeights=row_heights)
    commands = [('VALIGN',(0,0),(-1,-1),'TOP'), ('LINEBELOW',(0,0),(-1,-1),colors['rule_width'],HexColor(colors['rule'])),
                ('LEFTPADDING',(0,0),(-1,-1),padding), ('RIGHTPADDING',(0,0),(-1,-1),padding),
                ('TOPPADDING',(0,0),(-1,-1),padding), ('BOTTOMPADDING',(0,0),(-1,-1),padding)]
    if guides:
        commands.append(('LINEAFTER',(0,0),(-2,-1),.25,HexColor('#EBECEE')))
    for index, color in enumerate(backgrounds or []):
        commands.append(('BACKGROUND',(0,index),(-1,index),HexColor(color)))
    table.setStyle(TableStyle(commands))
    return table


def render_report(client, shift, supervisor=None, font_path=None, options=None, report_day=None):
    return _render_report(client, shift, supervisor, font_path, options,
                          datetime.now(ZoneInfo(client.zone)), report_day)


def _render_report(client, shift, supervisor, font_path, options, now, report_day=None):
    from desktop_protocol import current_date
    report_day=report_day or current_date(client.zone, now)
    from desktop_pdf_content import normalize_options, palette, PAPERS, context_lines, patient_content, row_backgrounds, roster_label, created_at
    options = normalize_options(options)
    colors = palette(options)
    if options['layout']=='cards':
        from desktop_pdf_cards import render_cards
        return render_cards(client, shift, supervisor, font_path, options, now, report_day)
    page_size = PAPERS[options['paper']]
    margin = 8.5
    width = page_size[0]-2*margin
    register_font(font_path)
    patients=[p for p in shift['patients'] if supervisor is None or p.get('responsibleSupervisorId')==supervisor]
    require(patients,'لا يوجد مرضى في التقرير المختار')
    text_color=HexColor(colors['text'])
    heading_color=HexColor(colors['title'])
    def text(value,size=11,color=None,**kwargs):return RTLText(value,size,color=color or text_color,**kwargs)
    context=context_lines(client,shift,options,now,report_day=report_day)
    roster=' / '.join(client.doctors.get(key,{}).get('name','طبيب محفوظ') for key in shift['meta'].get('doctors','').split(',') if key)
    roster_heading=roster_label([client.doctors.get(key,{}) for key in shift['meta'].get('doctors','').split(',') if key])
    pending=pending_task_count(patients)
    summary='إجمالي المرضى: {} (حالات نفسية: {}) • المرافقون: {} • مهام معلقة: {}'.format(
        len(patients),sum(p['diagnosisType'] in {'PSYCHIATRIC','DUAL'} for p in patients),
        sum(p['hasCompanion']=='Y' for p in patients),pending)
    story=[]
    if options['layout']=='table':
        heading=styled_table([[text(roster_heading+': '+roster,11,white,bold=True,align='center')],
                              [text(' • '.join(context),8.5,white,align='center')]],[width],colors,
                             backgrounds=[colors['title'],colors['title']])
        heading.setStyle(TableStyle([('TOPPADDING',(0,0),(-1,-1),1.5),('BOTTOMPADDING',(0,0),(-1,-1),1.5),
                                     ('LINEBELOW',(0,0),(-1,0),0,HexColor(colors['title']))]))
        story.append(heading)
        story.append(styled_table([[text(summary,10)]],[width],colors,backgrounds=[colors['summary']]))
        headers=['#','المريض','التشخيص / التنبيهات','رقم القبول الحالي\nالتاريخ / الأيام','الاختصاصي / المقيم','الخطة العلاجية','المتابعة / التحاليل']
        data=[[[text(label,9.5,white,bold=True,align='center')] for label in reversed(headers)]]
        backgrounds=[colors['header']]
        stripes=row_backgrounds(patients,colors)
        for index,patient in enumerate(patients,1):
            content=patient_content(patient,client.doctors,client.zone,now,report_day=report_day)
            cells=[str(index),content['personal'],content['diagnosis'],content['admit'],content['team'],content['treatment'],content['notes']]
            data.append([[text(value,9)] for value in reversed(cells)])
            backgrounds.append(stripes[index-1])
        weights=list(reversed([4,16,13,12,15,30,34]))
        story.append(styled_table(data,[width*w/sum(weights) for w in weights],colors,repeat=1,backgrounds=backgrounds,guides=True))
        story.append(styled_table([[text(summary,10)]],[width],colors,backgrounds=[colors['summary']]))
    else:
        story.extend([text('تقرير المناوبة',16,heading_color,bold=True,align='center'),text(' • '.join(context),9),
                      text(roster_heading+': '+roster,11),text(summary,10)])
        for index,patient in enumerate(patients,1):
            content=patient_content(patient,client.doctors,client.zone,now,report_day=report_day)
            divider=HRFlowable(width='100%',thickness=colors['rule_width'],color=HexColor(colors['rule']),spaceBefore=8,spaceAfter=6)
            divider.keepWithNext=True
            story.extend([divider,text(str(index)+': '+patient_name(patient),14,heading_color,bold=True)])
            for key,label in [('personal','بيانات المريض'),('admit','القبول'),('team','الفريق المسؤول'),('diagnosis','التشخيص / التنبيهات'),('treatment','العلاج'),('notes','المتابعة / المهام / التحاليل')]:
                if content[key]:story.append(text(label+': '+content[key]))
    timestamp=text(created_at(now),8.5)
    timestamp.wrap(width,100000)
    story.append(EndTimestamp(timestamp))
    path=temporary_pdf(report_filename(report_day,supervisor,client.doctors.get(supervisor,{}).get('name'),
        draft=options['local_draft'],demo=bool(getattr(client,'demo',False))))
    document=SimpleDocTemplate(str(path),pagesize=page_size,leftMargin=margin,rightMargin=margin,
                              topMargin=18,bottomMargin=35+timestamp.height+END_TIMESTAMP_GAP,
                              title='DEMO — ShiftReport' if getattr(client,'demo',False) else 'ShiftReport',author='ShiftReport')
    def page_background(canvas,doc):
        canvas.setFillColor(HexColor(colors['page']))
        canvas.rect(0,0,page_size[0],page_size[1],fill=1,stroke=0)
        footer=text('{}{}{} • صفحة {}'.format('DEMO — ' if getattr(client,'demo',False) else '',
            'مسودة محلية • لم يُعتمد نشرها • ' if options['local_draft'] else '',report_day,doc.page),8)
        footer.wrap(width,30);footer.drawOn(canvas,margin,10)
    document.build(story,onFirstPage=page_background,onLaterPages=page_background)
    return path


def render_assignments(client,shift,font_path=None,options=None,report_day=None):
    """Admin-only A5 grid, matching Android's three RTL team columns."""
    actor_id=client.require_local_admin()['uid']
    from reportlab.pdfgen import canvas as pdfcanvas
    from desktop_pdf_content import normalize_options,palette,assignment_groups,created_at
    options=normalize_options(options);colors=palette(options)
    groups=assignment_groups(shift,client.doctors)
    require(groups,'لا يوجد مرضى لتصدير توزيعهم')
    register_font(font_path)
    width,height=420,595
    margin,top,bottom=10,8,14
    column_gap,gap,padding,number_width=3,6,2,16
    content_width=width-2*margin
    table_width=(content_width-2*column_gap)/3
    def text(value,size=10,color=None,align='right',bold=False,lines=None):
        return RTLText(value,size,lines=lines,color=HexColor(color or colors['text']),align=align,bold=bold)
    from desktop_protocol import current_date
    report_day=report_day or current_date(client.zone)
    context_title=text('توزيع المرضى حسب المشرف',13,colors['title'],align='center',bold=True)
    timestamp=text(created_at(datetime.now(ZoneInfo(client.zone))),7)
    legend=text('المظلّل: مريض جديد أو تغيّر المشرف المسؤول',7.5)
    for item in (context_title,timestamp,legend):item.wrap(content_width,100000)
    table_top=height-top-context_title.height-legend.height-2
    available=table_top-bottom-4-timestamp.height
    subheader='#'+''.join('{:02X}'.format(int(int(colors['title'][i:i+2],16)*.12+255*.88)) for i in (1,3,5))

    def team_table(group,rows,filler=0):
        data=[[text(group['supervisor'],11,'#FFFFFF',align='center',bold=True),''],
              [text('، '.join(group['residents']) or 'لم يُحدد مقيم في الفريق',9.5,align='center',bold=True),'']]
        data += [[text(name,11,bold=True),text(str(number),11,align='center',bold=True,lines=[str(number)])]
                 for number,name,highlighted in rows]
        backgrounds=[colors['title'],subheader]+[colors['page'] if i%2==0 else colors['zebra'] for i in range(len(rows))]
        if filler>.01:
            data.append(['','']);backgrounds.append(colors['page'] if len(rows)%2==0 else colors['zebra'])
        heights=[None]*(len(data)-1)+[filler] if filler>.01 else None
        table=styled_table(data,[table_width-number_width,number_width],colors,backgrounds=backgrounds,
                           padding=padding,row_heights=heights)
        table.setStyle(TableStyle([('SPAN',(0,0),(1,0)),('SPAN',(0,1),(1,1))]+
            [('BACKGROUND',(0,i+2),(0,i+2),HexColor('#FFF4B8')) for i,(_,_,highlighted) in enumerate(rows) if highlighted]))
        table.wrap(table_width,1000000)
        return table

    # Split each team independently, then place its fragments in RTL bands.
    fragments=[];number=1
    for group in groups:
        rows=[(number+i,name,group['assignmentDates'][i]==report_day) for i,name in enumerate(group['patients'])]
        number+=len(rows)
        measured=team_table(group,rows)
        header=sum(measured._rowHeights[:2])
        require(header<=available,'أسماء الفريق أطول من مساحة جدول A5')
        offset=0
        if not rows:fragments.append((group,[]))
        while offset<len(rows):
            end=offset;used=header
            while end<len(rows) and used+measured._rowHeights[end+2]<=available:
                used+=measured._rowHeights[end+2];end+=1
            require(end>offset,'اسم المريض أو أسماء الفريق أطول من مساحة جدول A5؛ راجع الأسماء قبل التصدير')
            fragments.append((group,rows[offset:end]));offset=end
    bands=[]
    for start in range(0,len(fragments),3):
        band=fragments[start:start+3]
        tables=[team_table(group,rows) for group,rows in band]
        band_height=max(table._height for table in tables)
        bands.append([(team_table(group,rows,band_height-table._height),band_height)
                      for (group,rows),table in zip(band,tables)])
    pages=[];page_rows=[];used=0
    for band in bands:
        required=band[0][1]+(gap if page_rows else 0)
        if page_rows and used+required>available:
            pages.append(page_rows);page_rows=[];used=0;required=band[0][1]
        page_rows.append(band);used+=required
    if page_rows:pages.append(page_rows)
    path=temporary_pdf(report_filename(report_day,assignments=True,draft=options['local_draft'],demo=bool(getattr(client,'demo',False))))
    canvas=pdfcanvas.Canvas(str(path),pagesize=(width,height))
    canvas.setTitle('ShiftReport — توزيع المرضى بالأسماء فقط')
    for page,page_rows in enumerate(pages,1):
        if page>1:canvas.showPage()
        canvas.setFillColor(HexColor(colors['page']));canvas.rect(0,0,width,height,fill=1,stroke=0)
        y=height-top-context_title.height;context_title.drawOn(canvas,margin,y)
        y-=legend.height;legend.drawOn(canvas,margin,y)
        y-=2
        for band in page_rows:
            band_height=band[0][1]
            band_width=len(band)*table_width+(len(band)-1)*column_gap
            band_left=(width-band_width)/2
            for column,(table,_) in enumerate(band):
                table.drawOn(canvas,band_left+band_width-table_width-column*(table_width+column_gap),y-band_height)
            y-=band_height+gap
        if page==len(pages):timestamp.drawOn(canvas,margin,y+gap-4-timestamp.height)
        canvas.setFont('Arabic',6.5);canvas.setFillColor(HexColor(colors['text']))
        canvas.drawCentredString(width/2,6,visual(('DEMO — ' if getattr(client,'demo',False) else '')+
            ('مسودة محلية • لم يُعتمد نشرها • ' if options['local_draft'] else '')+str(page)))
    require(client.require_local_admin()['uid']==actor_id,'تغيّر المستخدم؛ أعد تصدير توزيع المرضى')
    canvas.showPage();canvas.save()
    return path
