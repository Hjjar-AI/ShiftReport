"""Office-free paginated Arabic PDF. Logical clinical text is never reshaped in storage."""
from __future__ import annotations
from datetime import datetime
import atexit
import shutil
from pathlib import Path
import tempfile
import arabic_reshaper
from bidi.algorithm import get_display
from reportlab.lib.colors import HexColor, white
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import Flowable, SimpleDocTemplate, Spacer, Table, TableStyle, PageBreak
from desktop_protocol import ZoneInfo, require
from desktop_platform import default_pdf_fonts
from desktop_model import task_counts


def visual(text):
    return get_display(arabic_reshaper.reshape(str(text)), base_dir='R')


class RTLText(Flowable):
    def __init__(self, text='', size=11, lines=None, link=None, anchor=None, color=None):
        Flowable.__init__(self)
        self.text, self.size, self.lines = str(text), size, lines
        self.leading = size * 1.65
        self.link, self.anchor, self.color = link, anchor, color

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
        return [RTLText(size=self.size, lines=self.lines[:count], link=self.link, anchor=self.anchor, color=self.color),
                RTLText(size=self.size, lines=self.lines[count:], link=self.link, color=self.color)]

    def draw(self):
        if self.anchor:
            self.canv.bookmarkHorizontal(self.anchor, 0, self.height)
        if self.link:
            self.canv.linkRect('', self.link, (0, 0, self.width, self.height), relative=1, thickness=0)
        self.canv.setFillColor(self.color or HexColor('#111111'))
        self.canv.setFont('Arabic', self.size)
        for index, line in enumerate(self.lines):
            self.canv.drawRightString(self.width, self.height - (index+1)*self.leading + 3, visual(line))


def register_font(font_path=None):
    candidates = [Path(font_path)] if font_path else default_pdf_fonts()
    font = next((p for p in candidates if p.is_file()), None)
    require(font is not None, 'اختر خط TTF يدعم العربية في PDF_FONT داخل .env')
    pdfmetrics.registerFont(TTFont('Arabic', str(font)))


def temporary_pdf():
    directory = Path(tempfile.mkdtemp(prefix='ShiftReportPreview_'))
    atexit.register(shutil.rmtree, str(directory), ignore_errors=True)
    return directory / 'report.pdf'


class ReportTable(Table):
    def split(self, available_width, available_height):
        parts = super().split(available_width, available_height)
        # Start the remainder on a new page rather than repeating its header in
        # the small space still available below the first fragment.
        if len(parts)>1:return [parts[0], PageBreak()] + parts[1:]
        return parts


def styled_table(data, widths, colors, repeat=0, backgrounds=None):
    table = ReportTable(data, colWidths=widths, repeatRows=repeat, splitInRow=1)
    commands = [('VALIGN',(0,0),(-1,-1),'TOP'), ('GRID',(0,0),(-1,-1),.5,HexColor(colors['border'])),
                ('LEFTPADDING',(0,0),(-1,-1),3), ('RIGHTPADDING',(0,0),(-1,-1),3),
                ('TOPPADDING',(0,0),(-1,-1),3), ('BOTTOMPADDING',(0,0),(-1,-1),3)]
    for index, color in enumerate(backgrounds or []):
        commands.append(('BACKGROUND',(0,index),(-1,index),HexColor(color)))
    table.setStyle(TableStyle(commands))
    return table


class ReportDocument(SimpleDocTemplate):
    """Resolve the active card at page end so continuation pages retain navigation."""
    def afterFlowable(self, flowable):
        marker = getattr(flowable, 'patient_index', None)
        if marker is not None:self.patient_index = marker

    def afterPage(self):
        index = getattr(self, 'patient_index', None)
        if not getattr(self, 'cards', False) or index is None:return
        patient = self.patients[index]
        name = RTLText(patient['name'], 15, link='entry-'+str(index), color=white)
        name.wrap(self.width-8, 100000)
        self.canv.setFillColor(HexColor(self.colors['title']))
        self.canv.rect(self.leftMargin, self.pagesize[1]-self.topMargin+4, self.width, self.topMargin-14, fill=1, stroke=0)
        name.drawOn(self.canv, self.leftMargin+4, self.pagesize[1]-18-name.height)
        labels = [('السابق','patient-'+str(index-1)) if index>0 else ('—',None),
                  ('الفهرس','entry-'+str(index)),
                  ('التالي','patient-'+str(index+1)) if index+1<len(self.patients) else ('—',None)]
        # Logical previous/index/next reads from the right, as on Android.
        for column,(label,target) in enumerate(labels):
            link = RTLText(label, 10, link=target, color=HexColor(self.colors['text']))
            link.wrap(self.width/3, 30)
            link.drawOn(self.canv, self.leftMargin+self.width-(column+1)*self.width/3, 27)


def render_report(client, shift, supervisor=None, font_path=None, options=None):
    from desktop_pdf_content import normalize_options, palette, PAPERS, context_lines, patient_content
    options = normalize_options(options)
    colors = palette(options)
    cards = options['layout']=='cards'
    page_size = PAPERS['A5'] if cards else PAPERS[options['paper']]
    if not cards and options['orientation']=='landscape':page_size=page_size[::-1]
    margin = 9 if cards else 8.5
    width = page_size[0]-2*margin
    register_font(font_path)
    patients=[p for p in shift['patients'] if supervisor is None or p.get('responsibleSupervisorId')==supervisor]
    require(patients,'لا يوجد مرضى في التقرير المختار')
    now=datetime.now(ZoneInfo(client.zone))
    text_color=HexColor(colors['text'])
    heading_color=text_color if options['dark'] else HexColor(colors['title'])
    def text(value,size=11,color=None,**kwargs):return RTLText(value,size,color=color or text_color,**kwargs)
    context=context_lines(client,shift,options,now)
    roster=' / '.join(client.doctors.get(key,{}).get('name','طبيب محفوظ') for key in shift['meta'].get('doctors','').split(',') if key)
    pending,overdue=task_counts(patients)
    summary='عدد المرضى: {} · حالات نفسية: {} · المرافقون: {} · مهام معلقة: {} (متأخرة: {})'.format(
        len(patients),sum(p['diagnosisType'] in {'PSYCHIATRIC','DUAL'} for p in patients),
        sum(p['hasCompanion']=='Y' for p in patients),pending,overdue)
    story=[]
    if cards:
        story.append(text('فهرس المرضى',14,heading_color,anchor='index'))
        story.extend(text(line) for line in context)
        story.extend([text(summary),text('أطباء المناوبة: '+roster),
                      text('اضغط اسم المريض لفتح بطاقته؛ اضغط اسمه في البطاقة للعودة إلى الفهرس.')])
        for index,patient in enumerate(patients):
            story.append(text('{} · {}'.format(index+1,patient['name']),12,heading_color,
                              link='patient-'+str(index),anchor='entry-'+str(index)))
        for index,patient in enumerate(patients):
            story.append(PageBreak())
            marker=text('',1,anchor='patient-'+str(index));marker.patient_index=index
            story.append(marker)
            content=patient_content(patient,client.doctors,client.zone,now)
            sections=[('بيانات المريض',content['personal']),('القبول',content['admit']),('الفريق المسؤول',content['team']),
                      ('التشخيص / التنبيهات',content['diagnosis']),('الخطة العلاجية',content['treatment']),('المتابعة',content['followUp'])]
            sections += [('المهام',task) for task in content['tasks']]
            sections += [('التحاليل',content['labs'])]
            for title,value in sections:
                if not value:continue
                urgent=title=='المهام' and (value.startswith('متأخرة') or not value.startswith('مكتملة') and 'أولوية عالية' in value)
                color=HexColor('#FFB4AB' if options['dark'] else '#8B1E1E') if urgent else heading_color
                title_text=text(title,12,color);title_text.keepWithNext=True
                story.extend([title_text,text(value,12),Spacer(1,3)])
    elif options['layout']=='table':
        story.append(styled_table([[text('\n'.join(context+['تقرير مناوبة: '+roster]),14,white)]],[width],colors,
                                  backgrounds=[colors['title']]))
        story.append(styled_table([[text(summary,10)]],[width],colors,backgrounds=[colors['summary']]))
        headers=['#','المريض','التشخيص / التنبيهات','رقم القبول الحالي\nالتاريخ / الأيام','الاختصاصي / المقيم','الخطة العلاجية','المتابعة / التحاليل']
        data=[[[text(label,9.5,white)] for label in reversed(headers)]]
        backgrounds=[colors['header']]
        group_ids=list(dict.fromkeys(p.get('responsibleSupervisorId') for p in patients))
        for index,patient in enumerate(patients,1):
            content=patient_content(patient,client.doctors,client.zone,now)
            cells=[str(index),content['personal'],content['diagnosis'],content['admit'],content['team'],content['treatment'],content['notes']]
            data.append([[text(value,9)] for value in reversed(cells)])
            group=group_ids.index(patient.get('responsibleSupervisorId'))
            backgrounds.append(colors['groups'][group%len(colors['groups'])])
        weights=list(reversed([4,16,13,12,15,30,34]))
        story.append(styled_table(data,[width*w/sum(weights) for w in weights],colors,repeat=1,backgrounds=backgrounds))
        story.append(styled_table([[text(summary,10)]],[width],colors,backgrounds=[colors['summary']]))
    else:
        story.extend(text(line) for line in context+[summary,'أطباء المناوبة: '+roster])
        for index,patient in enumerate(patients,1):
            content=patient_content(patient,client.doctors,client.zone,now)
            story.extend([Spacer(1,12),text(str(index)+': '+patient['name'],14,heading_color)])
            for key,label in [('personal','بيانات المريض'),('admit','القبول'),('team','الفريق المسؤول'),('diagnosis','التشخيص / التنبيهات'),('treatment','العلاج'),('notes','المتابعة / المهام / التحاليل')]:
                if content[key]:story.append(text(label+': '+content[key]))
    path=temporary_pdf()
    top_margin=18
    if cards:
        # Reserve enough header space for the longest patient name, including continuations.
        heights=[]
        for patient in patients:
            name=text(patient['name'],15);name.wrap(width-8,100000);heights.append(name.height)
        top_margin=max(heights)+27
        require(top_margin+100<page_size[1], 'اسم المريض أطول من مساحة بطاقة A5؛ استخدم الجدول لحفظ كامل المحتوى')
    document=ReportDocument(str(path),pagesize=page_size,leftMargin=margin,rightMargin=margin,
                            topMargin=top_margin,bottomMargin=52 if cards else 35,title='ShiftReport',author='ShiftReport')
    document.cards,document.patients,document.colors=cards,patients,colors
    def page_background(canvas,doc):
        canvas.setFillColor(HexColor(colors['cream'] if cards else colors['page']))
        canvas.rect(0,0,page_size[0],page_size[1],fill=1,stroke=0)
        footer=text('{} · {} · صفحة {}'.format(client.config['hospitalName'],shift['meta']['shiftDate'],doc.page),8)
        footer.wrap(width,30);footer.drawOn(canvas,margin,10)
    document.build(story,onFirstPage=page_background,onLaterPages=page_background)
    return path


def render_assignments(client,shift,font_path=None,options=None):
    """One portrait page, separate supervisor tables, a resident-name row and patient names only."""
    actor_id=client.require_admin()['uid']
    from reportlab.pdfgen import canvas as pdfcanvas
    from desktop_pdf_content import normalize_options,palette,PAPERS,context_lines,assignment_groups
    options=normalize_options(options);colors=palette(options)
    groups=assignment_groups(shift,client.doctors)
    require(groups,'لا يوجد مرضى لتصدير توزيعهم')
    register_font(font_path)
    width,minimum_height=PAPERS[options['paper']]
    margin,gap=18,14
    content_width=width-2*margin
    now=datetime.now(ZoneInfo(client.zone))
    def text(value,color=None):return RTLText(value,11,color=HexColor(color or colors['text']))
    tables=[styled_table([[text(line)] for line in ['توزيع المرضى حسب المشرف — الأسماء فقط']+
                         context_lines(client,shift,options,now)],[content_width],colors)]
    for index,group in enumerate(groups):
        rows=[[text('المشرف: '+group['supervisor'],'#FFFFFF')],
              [text('المقيمون: '+('، '.join(group['residents']) or 'لم يُحدد مقيم'))]]
        rows += [[text(name)] for name in group['patients']]
        backgrounds=[colors['title'],colors['groups'][index%len(colors['groups'])]]
        backgrounds += [colors['page'] if i%2==0 else colors['zebra'] for i in range(len(group['patients']))]
        tables.append(styled_table(rows,[content_width],colors,backgrounds=backgrounds))
    measured=[table.wrap(content_width,100000000)[1] for table in tables]
    height=max(minimum_height,width+1,2*margin+sum(measured)+gap*(len(tables)-1))
    path=temporary_pdf()
    canvas=pdfcanvas.Canvas(str(path),pagesize=(width,height))
    canvas.setTitle('ShiftReport — توزيع المرضى بالأسماء فقط')
    canvas.setFillColor(HexColor(colors['page']));canvas.rect(0,0,width,height,fill=1,stroke=0)
    top=height-margin
    for table,table_height in zip(tables,measured):
        table.drawOn(canvas,margin,top-table_height)
        top-=table_height+gap
    require(client.require_admin()['uid']==actor_id,'تغيّر المستخدم؛ أعد تصدير توزيع المرضى')
    canvas.showPage();canvas.save()
    return path
