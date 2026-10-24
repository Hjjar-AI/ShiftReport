#!/usr/bin/env python3
"""Create an offline Excel workbook and adjacent VBA source (Python 3.8+, stdlib).

No macros execute, no VBA is compiled, and no Telegram connection is made.
The generated XLSM is an OOXML macro-enabled container; import the adjacent BAS
modules and paste ThisWorkbook.txt in Excel before using its buttons.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timedelta, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET
from zipfile import ZipFile, ZIP_DEFLATED

BASE = Path(__file__).resolve().parent
S = 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'
R = 'http://schemas.openxmlformats.org/officeDocument/2006/relationships'
P = 'http://schemas.openxmlformats.org/package/2006/relationships'
C = 'http://schemas.openxmlformats.org/package/2006/content-types'
ET.register_namespace('', S)
ET.register_namespace('r', R)
TOKEN = re.compile(r'\b\d{6,}:[A-Za-z0-9_-]{20,}\b')
NEW_SHEETS = ['AppHome', 'AppDoctors', 'AppTasks', 'AppBadges', 'AppReview',
              'AppReceipts', 'AppHelp', 'AppState', 'AppPdf', 'AppMeasure']
LABELS = {
    'connect': 'فتح ملف المشروع المحمي', 'login': 'إثبات هوية الطبيب',
    'retrieve': 'جلب بيانات المناوبة', 'save': 'حفظ محلي',
    'publish': 'مراجعة ونشر البيانات', 'send': 'إرسال تقرير',
    'supervisorpdf': 'تقرير مشرف محدد', 'export': 'حفظ PDF محلي',
    'sort': 'ترتيب المرضى', 'receipts': 'سجل التسليم',
    'resolve': 'حسم عملية غير مؤكدة', 'initial': 'استيراد الصفوف الأصلية',
    'logout': 'تسجيل الخروج', 'birth': 'سنة الميلاد', 'admission': 'القبول',
    'diagnosis': 'التشخيص', 'treatment': 'العلاج والحقن المديدة',
    'followup': 'المتابعة', 'labs': 'التحاليل', 'tasks': 'المهام',
    'done': 'مكتملة', 'pending': 'معلقة', 'high': 'أولوية عالية',
    'due': 'الموعد', 'index': 'الفهرس', 'previous': 'السابق', 'next': 'التالي',
    'continued': 'يتبع', 'roster': 'أطباء المناوبة', 'companion': 'يوجد مرافق',
    'badges': 'الشارات', 'supervisor': 'المشرف', 'resident': 'المقيم', 'doctors': 'الأطباء',
    'patient': 'المريض', 'days': 'يوم',
    'male': 'ذكر', 'female': 'أنثى', 'psychiatric': 'نفسي',
    'addiction': 'إدمان', 'dual': 'نفسي + إدمان',
    'patients': 'المرضى', 'psychiatriccount': 'نفسي وإدمان مشترك',
    'companions': 'المرافقون', 'pendingcount': 'مهام معلقة', 'overduecount': 'منها متأخرة',
}
DATA_HEADERS = ['#', 'الاسم', 'العمر', 'الجنس', 'رقم القبول', 'تاريخ القبول',
                'الحالة المرضية', 'مرافق', 'التشخيص', 'العلاج والحقن المديدة',
                'المتابعة', 'التحاليل', 'المشرف', 'المقيم', 'الطبيب المدقق (ملاحظة)',
                'أيام القبول', 'Patient UUID', 'أولوية Y/N', '', 'سنة الميلاد']


def tag(name):
    return '{' + S + '}' + name


def xml(root):
    return ET.tostring(root, encoding='utf-8', xml_declaration=True)


def col(number):
    result = ''
    while number:
        number, digit = divmod(number - 1, 26)
        result = chr(65 + digit) + result
    return result


def rows(root):
    found = root.find(tag('sheetData'))
    if found is None:
        found = ET.SubElement(root, tag('sheetData'))
    return found


def cell(root, address):
    number = int(re.search(r'\d+$', address).group())
    data = rows(root)
    row = next((r for r in data if int(r.get('r')) == number), None)
    if row is None:
        row = ET.SubElement(data, tag('row'), r=str(number))
    result = next((c for c in row if c.get('r') == address), None)
    if result is None:
        result = ET.SubElement(row, tag('c'), r=address)
    return result


def set_cell(root, address, value='', style=None, formula=None):
    target = cell(root, address)
    for child in list(target):
        target.remove(child)
    target.attrib.pop('t', None)
    if style is not None:
        target.set('s', str(style))
    if formula is not None:
        ET.SubElement(target, tag('f')).text = formula
    else:
        target.set('t', 'inlineStr')
        node = ET.SubElement(ET.SubElement(target, tag('is')), tag('t'))
        node.set('{http://www.w3.org/XML/1998/namespace}space', 'preserve')
        node.text = str(value)
    return target


def text_of(target):
    if target is None:
        return ''
    if target.get('t') == 'inlineStr':
        return ''.join(n.text or '' for n in target.iter(tag('t')))
    value = target.find(tag('v'))
    return value.text or '' if value is not None else ''


def remove(root, *names):
    for child in list(root):
        if child.tag.rsplit('}', 1)[-1] in names:
            root.remove(child)


def strip_old_relationship_markup(root):
    """Remove nested legacy controls and their references, including mc fallbacks."""
    discarded = {'controls', 'control', 'oleObjects', 'legacyDrawing', 'legacyDrawingHF',
                 'drawing', 'extLst', 'tableParts', 'AlternateContent', 'hyperlinks'}
    for parent in list(root.iter()):
        for child in list(parent):
            local = child.tag.rsplit('}', 1)[-1]
            if local in discarded or not child.tag.startswith('{' + S + '}'):
                parent.remove(child)
        for attribute in list(parent.attrib):
            if attribute.startswith('{') and not attribute.startswith('{http://www.w3.org/XML/1998/namespace}'):
                del parent.attrib[attribute]


def ordered_sheet(root):
    #SpreadsheetML requires worksheet children in schema order.
    order = 'sheetPr dimension sheetViews sheetFormatPr cols sheetData sheetCalcPr sheetProtection protectedRanges scenarios autoFilter sortState dataConsolidate customSheetViews mergeCells phoneticPr conditionalFormatting dataValidations hyperlinks printOptions pageMargins pageSetup headerFooter rowBreaks colBreaks customProperties cellWatches ignoredErrors smartTags drawing legacyDrawing legacyDrawingHF picture oleObjects controls webPublishItems tableParts extLst'.split()
    positions = {tag(name): i for i, name in enumerate(order)}
    root[:] = sorted(root, key=lambda n: positions.get(n.tag, 999))
    data = root.find(tag('sheetData'))
    if data is not None:
        data[:] = sorted(data, key=lambda n: int(n.get('r', '0')))
        for row in data:
            def index(c):
                value = 0
                for ch in re.match('[A-Z]+', c.get('r')).group():
                    value = value * 26 + ord(ch) - 64
                return value
            row[:] = sorted(row, key=index)


def make_sheet(code_name=None):
    root = ET.Element(tag('worksheet'))
    if code_name:
        ET.SubElement(root, tag('sheetPr'), codeName=code_name)
    views = ET.SubElement(root, tag('sheetViews'))
    ET.SubElement(views, tag('sheetView'), workbookViewId='0', rightToLeft='1', showGridLines='0')
    ET.SubElement(root, tag('sheetFormatPr'), defaultRowHeight='24')
    ET.SubElement(root, tag('sheetData'))
    return root


def columns(root, widths, hidden=()):
    remove(root, 'cols')
    node = ET.SubElement(root, tag('cols'))
    for i, width in enumerate(widths, 1):
        attrs = dict(min=str(i), max=str(i), width=str(width), customWidth='1')
        if i in hidden:
            attrs['hidden'] = '1'
        ET.SubElement(node, tag('col'), attrs)


def merge(root, address):
    node = root.find(tag('mergeCells'))
    if node is None:
        node = ET.SubElement(root, tag('mergeCells'))
    ET.SubElement(node, tag('mergeCell'), ref=address)
    node.set('count', str(len(node)))


def row_height(root, row, height):
    cell(root, 'A' + str(row))
    node = next(r for r in rows(root) if r.get('r') == str(row))
    node.set('ht', str(height))
    node.set('customHeight', '1')


def validation(root, address, choices):
    node = root.find(tag('dataValidations'))
    if node is None:
        node = ET.SubElement(root, tag('dataValidations'))
    item = ET.SubElement(node, tag('dataValidation'), type='list', allowBlank='1',
                         showErrorMessage='1', errorTitle='قيمة غير صالحة',
                         error='اختر إحدى القيم المعروضة.', sqref=address)
    ET.SubElement(item, tag('formula1')).text = '"' + choices + '"'
    node.set('count', str(len(node)))


def new_styles(existing=None):
    if existing is None:
        existing = ET.Element(tag('styleSheet'))
        fonts = ET.SubElement(existing, tag('fonts'), count='1')
        font = ET.SubElement(fonts, tag('font'))
        ET.SubElement(font, tag('sz'), val='11')
        ET.SubElement(font, tag('name'), val='Tajawal')
        fills = ET.SubElement(existing, tag('fills'), count='2')
        for kind in ['none', 'gray125']:
            ET.SubElement(ET.SubElement(fills, tag('fill')), tag('patternFill'), patternType=kind)
        borders = ET.SubElement(existing, tag('borders'), count='1')
        ET.SubElement(borders, tag('border'))
        master = ET.SubElement(existing, tag('cellStyleXfs'), count='1')
        ET.SubElement(master, tag('xf'), numFmtId='0', fontId='0', fillId='0', borderId='0')
        xfs = ET.SubElement(existing, tag('cellXfs'), count='1')
        ET.SubElement(xfs, tag('xf'), numFmtId='0', fontId='0', fillId='0', borderId='0', xfId='0')
        styles = ET.SubElement(existing, tag('cellStyles'), count='1')
        ET.SubElement(styles, tag('cellStyle'), name='Normal', xfId='0', builtinId='0')
    fonts = existing.find(tag('fonts'))
    for font in fonts:
        name = font.find(tag('name'))
        if name is not None:
            name.set('val', 'Tajawal')
    font_ids = []
    for size, bold, color in [(12, False, 'FF263D4A'), (12, True, 'FF234C61'), (20, True, 'FF234C61'), (11, False, 'FF5E7582')]:
        font_ids.append(len(fonts))
        node = ET.SubElement(fonts, tag('font'))
        ET.SubElement(node, tag('sz'), val=str(size))
        ET.SubElement(node, tag('name'), val='Tajawal')
        ET.SubElement(node, tag('color'), rgb=color)
        if bold:
            ET.SubElement(node, tag('b'))
    fonts.set('count', str(len(fonts)))
    fills = existing.find(tag('fills'))
    fill_ids = []
    for color in ['FFFFFFFF', 'FFF1F7FA', 'FFE3EFF5', 'FFF8FAFC']:
        fill_ids.append(len(fills))
        node = ET.SubElement(ET.SubElement(fills, tag('fill')), tag('patternFill'), patternType='solid')
        ET.SubElement(node, tag('fgColor'), rgb=color)
        ET.SubElement(node, tag('bgColor'), indexed='64')
    fills.set('count', str(len(fills)))
    xfs = existing.find(tag('cellXfs'))
    ids = {}
    for name, font, fill, number in [('body', 0, 0, 49), ('stripe', 0, 3, 49),
                                      ('header', 1, 2, 49), ('title', 2, 0, 49),
                                      ('hint', 3, 1, 49), ('calculated', 3, 1, 0)]:
        ids[name] = len(xfs)
        node = ET.SubElement(xfs, tag('xf'), numFmtId=str(number), fontId=str(font_ids[font]),
                             fillId=str(fill_ids[fill]), borderId='0', xfId='0',
                             applyFont='1', applyFill='1', applyAlignment='1', applyNumberFormat='1')
        ET.SubElement(node, tag('alignment'), horizontal='right', vertical='top', wrapText='1', readingOrder='2')
    xfs.set('count', str(len(xfs)))
    return existing, ids


def read_source(source):
    if source is None:
        return {}, {}, None
    if source.stat().st_size > 32 * 1024 * 1024:
        raise ValueError('Source workbook exceeds 32 MiB')
    with ZipFile(source) as z:
        if sum(i.file_size for i in z.infolist()) > 64 * 1024 * 1024:
            raise ValueError('Source workbook expands beyond 64 MiB')
        parts = {name: z.read(name) for name in z.namelist()}
    book = ET.fromstring(parts['xl/workbook.xml'])
    relationships = ET.fromstring(parts['xl/_rels/workbook.xml.rels'])
    targets = {x.get('Id'): x.get('Target') for x in relationships}
    strings = []
    if 'xl/sharedStrings.xml' in parts:
        strings = [''.join(t.text or '' for t in x.iter(tag('t')))
                   for x in ET.fromstring(parts['xl/sharedStrings.xml'])]
    sheets = {}
    for entry in book.find(tag('sheets')):
        target = targets[entry.get('{' + R + '}id')]
        path = target.lstrip('/') if target.startswith('/') else 'xl/' + target
        root = ET.fromstring(parts[path])
        for c in root.findall('.//' + tag('c')):
            if c.get('t') == 's':
                value = strings[int(c.find(tag('v')).text)]
                for child in list(c):
                    c.remove(child)
                c.set('t', 'inlineStr')
                node = ET.SubElement(ET.SubElement(c, tag('is')), tag('t'))
                node.set('{http://www.w3.org/XML/1998/namespace}space', 'preserve')
                node.text = value
        sheets[entry.get('name')] = root
    return parts, sheets, book


def prepare(source, output, kind, blank):
    parts, sheets, old_book = read_source(source)
    source_styles = ET.fromstring(parts['xl/styles.xml']) if 'xl/styles.xml' in parts else None
    styles, style = new_styles(source_styles)
    strip_old_relationship_markup(styles)
    for name in ['Data', 'Report', 'Settings', 'Lists', 'Backup', 'Sheet1']:
        sheets.setdefault(name, make_sheet())
    for name in NEW_SHEETS:
        sheets[name] = make_sheet()
    today = (datetime.now(timezone.utc) - timedelta(hours=5, minutes=30)).date().isoformat()
    #Strip old controls/macros/connection cells, but retain clinical input and pictures.
    for name, root in sheets.items():
        strip_old_relationship_markup(root)
        remove(root, 'sheetProtection', 'controls', 'oleObjects', 'legacyDrawing', 'legacyDrawingHF',
               'extLst', 'dataValidations', 'drawing', 'tableParts')
        for c in root.findall('.//' + tag('c')):
            text = text_of(c)
            if TOKEN.search(text) or (name == 'Settings' and re.fullmatch(r'B([1-9]|1[0-9]|20)', c.get('r'))):
                for child in list(c):
                    c.remove(child)
                c.attrib.pop('t', None)
        views = root.find(tag('sheetViews'))
        if views is None:
            views = ET.SubElement(root, tag('sheetViews'))
        if not len(views):
            ET.SubElement(views, tag('sheetView'), workbookViewId='0')
        for view in views:
            view.set('rightToLeft', '1')
            view.set('showGridLines', '0')
            view.attrib.pop('tabSelected', None)
    if blank:
        for name in ['Data', 'Report', 'Backup', 'Lists']:
            sheets[name] = make_sheet()
    data = sheets['Data']
    date_cell = next((c for c in data.findall('.//' + tag('c')) if c.get('r') == 'B1'), None)
    original_date = text_of(date_cell)
    if original_date and re.fullmatch(r'\d+(\.\d+)?', original_date):
        original_date = (datetime(1899, 12, 30) + timedelta(days=float(original_date))).date().isoformat()
    if not re.fullmatch(r'\d{4}-\d{2}-\d{2}', original_date or ''):
        original_date = today
    set_cell(data, 'B1', original_date, style['body'])
    set_cell(data, 'A1', 'تاريخ المناوبة', style['header'])
    set_cell(data, 'H1', 'كامل أطباء المناوبة: AppDoctors', style['hint'])
    for i, header in enumerate(DATA_HEADERS, 1):
        set_cell(data, col(i) + '5', header, style['header'])
    for name in ['Data', 'Backup']:
        root = sheets[name]
        set_cell(root, 'B1', original_date, style['body'])
        for address, formula in {
            'B2': 'COUNTA(B6:B1000)', 'D2': 'COUNTIFS(G6:G1000,"نفسي",B6:B1000,"<>")+COUNTIFS(G6:G1000,"PSYCHIATRIC",B6:B1000,"<>")',
            'F2': 'COUNTIFS(G6:G1000,"إدمان",B6:B1000,"<>")+COUNTIFS(G6:G1000,"نفسي + إدمان",B6:B1000,"<>")+COUNTIFS(G6:G1000,"ADDICTION",B6:B1000,"<>")+COUNTIFS(G6:G1000,"DUAL",B6:B1000,"<>")',
            'B3': 'COUNTIFS(H6:H1000,"نعم",B6:B1000,"<>")+COUNTIFS(H6:H1000,"Y",B6:B1000,"<>")',
        }.items():
            set_cell(root, address, style=style['calculated'], formula=formula)
        for c in list(root.findall('.//' + tag('c'))):
            address = c.get('r')
            row = int(re.search(r'\d+$', address).group())
            letter = re.match('[A-Z]+', address).group()
            if row >= 6:
                c.set('s', str(style['stripe' if row % 2 else 'body']))
                if letter == 'P' and (c.find(tag('f')) is not None or text_of(c)):
                    explicit = 'DATE(LEFT($B$1,4),MID($B$1,6,2),RIGHT($B$1,2))'
                    set_cell(root, address, style=style['calculated'], formula='IF(F{0}="","",{1}-IF(ISNUMBER(F{0}),F{0},DATE(LEFT(F{0},4),MID(F{0},6,2),RIGHT(F{0},2)))+1)'.format(row, explicit))
                if letter == 'B' and text_of(c):
                    row_height(root, row, 76)
    columns(data, [5, 24, 7, 10, 12, 15, 19, 8, 31, 42, 40, 34, 30, 30, 26, 10, 38, 12, 4, 14], hidden=(17, 19))
    row_height(data, 5, 36)
    remove(data, 'autoFilter')
    ET.SubElement(data, tag('autoFilter'), ref='A5:T1000')
    for view in data.find(tag('sheetViews')):
        remove(view, 'pane', 'selection')
        ET.SubElement(view, tag('pane'), ySplit='5', topLeftCell='A6', activePane='bottomLeft', state='frozen')
    validation(data, 'D6:D1000', 'M,F,ذكر,أنثى')
    validation(data, 'G6:G1000', 'PSYCHIATRIC,ADDICTION,DUAL,نفسي,إدمان,نفسي + إدمان')
    validation(data, 'H6:H1000 R6:R1000', 'Y,N,نعم,لا')
    for address, value in {'D40': 'سنة', 'D41': 'يوجد مرافق', 'D42': 'نعم', 'D43': 'لا',
                           'D50': 'أنثى', 'D51': 'ذكر', 'D52': 'نفسي',
                           'D53': 'نفسي + إدمان', 'D54': 'إدمان'}.items():
        set_cell(sheets['Settings'], address, value)
    layouts = {
        'AppDoctors': (['Doctor UUID', 'الطبيب', 'الدور', 'مناوب Y/N', 'الاسم مع الهوية', 'مشرف الفريق UUID'], [38, 28, 18, 15, 55, 38]),
        'AppTasks': (['Patient UUID', 'Task UUID (فارغ للجديد)', 'المهمة', 'LOW/NORMAL/HIGH', 'مسؤول UUID (اختياري)', 'الموعد بتوقيت دمشق YYYY-MM-DD HH:MM', 'مناوبة الموعد YYYY-MM-DD', 'مكتملة Y/N', 'CompletedBy UUID', 'أتمها', 'CompletedAt UTC ms'], [38, 38, 55, 20, 38, 29, 23, 16, 38, 28, 24]),
        'AppBadges': (['Patient UUID', 'نص الشارة', 'none/low/medium/high'], [38, 65, 26]),
        'AppReceipts': (['Receipt UUID', 'العملية', 'الحالة', 'UTC ms', 'Sender UUID', 'Shift UUID', 'Message ID', 'Snapshot UUID', 'Destination digest'], [38, 18, 18, 22, 38, 38, 18, 38, 70]),
        'AppState': (['Key', 'Chunk', 'Local app state (no connection secrets)'], [25, 12, 100]),
    }
    for name, (headers, widths) in layouts.items():
        root = sheets[name]
        for i, title in enumerate(headers, 1):
            set_cell(root, col(i) + '1', title, style['header'])
        columns(root, widths, hidden=(9, 11) if name == 'AppTasks' else ())
        row_height(root, 1, 44)
        view = root.find(tag('sheetViews'))[0]
        ET.SubElement(view, tag('pane'), ySplit='1', topLeftCell='A2', activePane='bottomLeft', state='frozen')
    validation(sheets['AppDoctors'], 'D2:D10000', 'Y,N')
    validation(sheets['AppTasks'], 'D2:D10000', 'LOW,NORMAL,HIGH')
    validation(sheets['AppTasks'], 'H2:H10000', 'Y,N')
    validation(sheets['AppBadges'], 'C2:C10000', 'none,low,medium,high')
    home = sheets['AppHome']
    columns(home, [12] * 10)
    merge(home, 'A1:J2')
    set_cell(home, 'A1', 'تقرير المناوبة — Excel', style['title'])
    merge(home, 'A3:J5')
    set_cell(home, 'A3', 'افتح ملف المشروع المحمي ← أثبت الهوية ← اجلب المناوبة ← عدّل واحفظ محلياً ← راجع وانشر ← عاين PDF وأكّد الإرسال.\nالأزرار تظهر بعد استيراد وحدات VBA وتشغيل InitializeWorkbook. لا توجد اتصالات أثناء توليد هذا الملف.', style['hint'])
    for row, text in [(25, 'Data: البيانات السريرية. سنة الميلاد في T، والأولوية في R. هوية المريض محفوظة في Q المخفي.'),
                      (27, 'AppDoctors: اختر كامل أطباء المناوبة بوضع Y في D. لا يوجد حد لثلاثة أو خمسة أطباء.'),
                      (29, 'AppTasks وAppBadges: مهام وشارات صريحة مرتبطة بهوية المريض، دون استخراج مهام من الملاحظات.'),
                      (31, 'ملف XLSM هذا جاهز لاستيراد VBA، ولا يتضمن كود VBA تنفيذيّاً قبل الاستيراد. راجع initiaitaion.md.'),
                      (33, 'PDF مقروء عمداً. ملف Excel يحتوي بيانات سريرية مقروءة؛ احفظه محلياً وبصلاحيات مناسبة.')]:
        merge(home, 'A{0}:J{0}'.format(row))
        set_cell(home, 'A' + str(row), text, style['hint'])
        row_height(home, row, 40)
    set_cell(home, 'A20', 'نمط PDF', style['header'])
    set_cell(home, 'B20', 'CARDS', style['body'])
    merge(home, 'C20:J21')
    set_cell(home, 'C20', 'CARDS: بطاقات A5 مع فهرس • CLASSIC: جدول A4 • PARAGRAPHS: فقرات A4. تُراجع النسخة النهائية قبل الإرسال.', style['hint'])
    validation(home, 'B20', 'CARDS,CLASSIC,PARAGRAPHS')
    help_sheet = sheets['AppHelp']
    columns(help_sheet, [25, 100])
    set_cell(help_sheet, 'B1', 'دليل العمل', style['title'])
    instructions = [
        '1. ثبّت خطوط Tajawal من fonts. استورد جميع ملفات BAS والصق ThisWorkbook.txt في كائن المصنف.',
        '2. احفظ بصيغة XLSM. شغّل Module1.InitializeWorkbook من Alt+F8، ثم استخدم صفحة AppHome.',
        '3. استخدم ملف srjoin.json محمياً مُصدراً من التطبيق الحالي. الإعدادات القديمة مرجع خاص فقط.',
        '4. بعد الجلب: لا تغيّر تاريخ B1 أو هويات Q. لبدء اليوم الجديد اجلب المناوبة الحالية؛ لا تبدّل تاريخ سجل قديم.',
        '5. الصفوف الأصلية تستورد صراحة إلى مناوبة حالية فارغة فقط. الأعمار تقدّر سنة الميلاد وتحتاج مراجعة.',
        '6. المهام: A هوية المريض، B فارغ للمهمة الجديدة، E مسؤول اختياري، F موعد YYYY-MM-DD HH:MM بتوقيت دمشق.',
        '7. عند حذف مريض، احذف صفوف مهامه وشاراته صراحة. تحفظ نسخة استرداد قبل الحذف/الجلب.',
        '8. النشر يستبدل النسخة المشتركة كاملة بعد المراجعة. الإرسال يتطلب معاينة PDF وتأكيداً نهائياً.',
        '9. UNKNOWN يعني أن النتيجة غير مؤكدة. افحص تليجرام قبل الحسم؛ لا تعِد الإرسال تلقائياً.',
        '10. محرّك PDF في Excel مختلف. إعداد A5 والهوامش والخطوط مشتركة؛ التطابق البصري وروابط PDF يحتاجان تحققاً فعلياً.',
    ]
    for row, text in enumerate(instructions, 3):
        set_cell(help_sheet, 'B' + str(row), text, style['body'])
        row_height(help_sheet, row, 42)
    for row, (key, value) in enumerate(LABELS.items(), 30):
        set_cell(help_sheet, 'A' + str(row), key)
        set_cell(help_sheet, 'B' + str(row), value)
        next(r for r in rows(help_sheet) if r.get('r') == str(row)).set('hidden', '1')
    #Create a fresh package: no compiled old VBA, old control actions, external links,
    #shared-string residue, connection files or cached patient formula results.
    package = {}
    if 'xl/theme/theme1.xml' in parts:
        package['xl/theme/theme1.xml'] = parts['xl/theme/theme1.xml']
    package['xl/styles.xml'] = xml(styles)
    book = ET.Element(tag('workbook'))
    ET.SubElement(book, tag('workbookPr'), date1904='0')
    views = ET.SubElement(book, tag('bookViews'))
    ET.SubElement(views, tag('workbookView'), activeTab='0')
    all_sheets = ET.SubElement(book, tag('sheets'))
    relationships = ET.Element('{' + P + '}Relationships')
    order = ['AppHome', 'Data', 'AppDoctors', 'AppTasks', 'AppBadges', 'AppReview', 'AppReceipts', 'AppHelp', 'AppPdf', 'Report', 'Settings', 'Lists', 'Backup', 'Sheet1', 'AppState', 'AppMeasure']
    types = ET.Element('{' + C + '}Types')
    ET.SubElement(types, '{' + C + '}Default', Extension='rels', ContentType='application/vnd.openxmlformats-package.relationships+xml')
    ET.SubElement(types, '{' + C + '}Default', Extension='xml', ContentType='application/xml')
    content = 'application/vnd.ms-excel.sheet.macroEnabled.main+xml' if kind == 'xlsm' else 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml'
    ET.SubElement(types, '{' + C + '}Override', PartName='/xl/workbook.xml', ContentType=content)
    ET.SubElement(types, '{' + C + '}Override', PartName='/xl/styles.xml', ContentType='application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml')
    for i, name in enumerate(order, 1):
        root = sheets[name]
        #Old sheet formulas can reference only the six retained original sheet names.
        #Preserve pictures as original private source; no old drawing/control package is carried.
        for c in root.findall('.//' + tag('c')):
            if c.find(tag('f')) is not None:
                for cached in list(c.findall(tag('v'))):
                    c.remove(cached)
        ordered_sheet(root)
        path = 'xl/worksheets/sheet' + str(i) + '.xml'
        package[path] = xml(root)
        attrs = dict(name=name, sheetId=str(i))
        attrs['{' + R + '}id'] = 'rId' + str(i)
        if name in ['AppState', 'AppMeasure']:
            attrs['state'] = 'veryHidden'
        elif name in ['Report', 'Settings', 'Lists', 'Backup', 'Sheet1']:
            attrs['state'] = 'hidden'
        ET.SubElement(all_sheets, tag('sheet'), attrs)
        ET.SubElement(relationships, '{' + P + '}Relationship', Id='rId' + str(i), Type=R + '/worksheet', Target='worksheets/sheet' + str(i) + '.xml')
        ET.SubElement(types, '{' + C + '}Override', PartName='/' + path, ContentType='application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml')
    ET.SubElement(book, tag('calcPr'), calcMode='auto', fullCalcOnLoad='1', forceFullCalc='1')
    ET.SubElement(relationships, '{' + P + '}Relationship', Id='rStyles', Type=R + '/styles', Target='styles.xml')
    if 'xl/theme/theme1.xml' in package:
        ET.SubElement(relationships, '{' + P + '}Relationship', Id='rTheme', Type=R + '/theme', Target='theme/theme1.xml')
        ET.SubElement(types, '{' + C + '}Override', PartName='/xl/theme/theme1.xml', ContentType='application/vnd.openxmlformats-officedocument.theme+xml')
    root_rels = ET.Element('{' + P + '}Relationships')
    ET.SubElement(root_rels, '{' + P + '}Relationship', Id='rId1', Type=R + '/officeDocument', Target='xl/workbook.xml')
    package['xl/workbook.xml'] = xml(book)
    package['xl/_rels/workbook.xml.rels'] = xml(relationships)
    package['_rels/.rels'] = xml(root_rels)
    package['[Content_Types].xml'] = xml(types)
    output.parent.mkdir(parents=True, exist_ok=True)
    if output.exists():
        raise ValueError('Output exists; choose a new filename rather than overwriting clinical work')
    with ZipFile(output, 'x', ZIP_DEFLATED) as z:
        for name, content in package.items():
            z.writestr(name, content)
    adjacent = output.parent / 'vba'
    adjacent.mkdir(exist_ok=True)
    for path in sorted((BASE / 'vba').glob('*.bas')):
        shutil.copy2(str(path), str(adjacent / path.name))
    shutil.copy2(str(BASE / 'ThisWorkbook.txt'), str(output.parent / 'ThisWorkbook.txt'))
    manifest = {'format': kind, 'vbaEmbedded': False, 'vbaImportRequired': True,
                'vba_sha256': {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((BASE / 'vba').glob('*.bas'))},
                'document_events_sha256': hashlib.sha256((BASE / 'ThisWorkbook.txt').read_bytes()).hexdigest(),
                'generator_sha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                'source_sha256': hashlib.sha256(source.read_bytes()).hexdigest() if source else None,
                'workbook_sha256': hashlib.sha256(output.read_bytes()).hexdigest(),
                'originalCredentialsUsed': False, 'networkCalls': 0,
                'sheets': order, 'limits': 'OOXML/source checks only; Excel/VBA/Win7/PDF/live execution unverified.'}
    output.with_suffix('.manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    return output


def legacy_xls(source, output, blank):
    """Produce actual BIFF through installed Excel, never by renaming an XLSX.

    This path runs only when a Windows owner explicitly selects --format xls.
    The intermediary is generated here with no VBA or external links.
    """
    if os.name != 'nt':
        raise ValueError('Real .xls output requires installed Excel on Windows; use xlsm/xlsx here')
    if output.exists():
        raise ValueError('Choose a new output filename')
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='ShiftReportExcel-') as temporary:
        directory = Path(temporary)
        intermediate = directory / 'generated.xlsx'
        prepare(source, intermediate, 'xlsx', blank)
        def vb_string(value):
            return '"' + str(value).replace('"', '""') + '"'
        script = '\r\n'.join([
            'Option Explicit', 'Dim app, book, errorCode',
            'On Error Resume Next', 'Set app = CreateObject("Excel.Application")',
            'If Err.Number <> 0 Then WScript.Quit 2',
            'app.Visible = False', 'app.DisplayAlerts = False', 'app.EnableEvents = False',
            'app.AutomationSecurity = 3',
            'Set book = app.Workbooks.Open(' + vb_string(intermediate) + ', 0, False)',
            'If Err.Number <> 0 Then app.Quit: WScript.Quit 3',
            'book.CheckCompatibility = False',
            'book.SaveAs ' + vb_string(output.resolve()) + ', 56',
            'errorCode = Err.Number', 'book.Close False', 'app.Quit',
            'If errorCode <> 0 Then WScript.Quit 4', 'WScript.Quit 0',
        ])
        conversion = directory / 'convert.vbs'
        conversion.write_text(script, encoding='utf-16')
        result = subprocess.run(['cscript.exe', '//NoLogo', str(conversion)],
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
        if result.returncode or not output.exists():
            raise ValueError('Installed Excel did not complete the BIFF conversion')
        adjacent = output.parent / 'vba'
        adjacent.mkdir(exist_ok=True)
        for path in sorted((BASE / 'vba').glob('*.bas')):
            shutil.copy2(str(path), str(adjacent / path.name))
        shutil.copy2(str(BASE / 'ThisWorkbook.txt'), str(output.parent / 'ThisWorkbook.txt'))
        manifest = json.loads(intermediate.with_suffix('.manifest.json').read_text(encoding='utf-8'))
        manifest.update(format='xls', workbook_sha256=hashlib.sha256(output.read_bytes()).hexdigest(),
                        limits='Converted by installed Excel; native VBA import/runtime and live transport remain unverified.')
        output.with_suffix('.manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, help='Optional private original workbook; no macros execute')
    parser.add_argument('--blank', action='store_true', help='Start with blank clinical/legacy-list sheets')
    parser.add_argument('--format', choices=['xlsm', 'xlsx', 'xls'], default='xlsm',
                        help='xls requires Windows with installed Excel; xlsm/xlsx need only Python')
    parser.add_argument('--output', type=Path, help='New output filename; existing files are never overwritten')
    args = parser.parse_args()
    source = args.source
    if source is None and not args.blank and (BASE / 'private' / 'original.xlsm').exists():
        source = BASE / 'private' / 'original.xlsm'
    output = args.output or BASE / 'output' / ('ShiftReport-Excel.' + args.format)
    if output.suffix.lower() != '.' + args.format:
        parser.error('Output extension must match --format; legacy BIFF .xls is not renamed OOXML')
    try:
        result = legacy_xls(source, output, args.blank) if args.format == 'xls' else prepare(source, output, args.format, args.blank)
    except Exception as error:
        print('Generation stopped (' + type(error).__name__ + '). Preserve the original; check source and choose an unused output filename.')
        return 1
    print('Created ' + str(result))
    print('Import the adjacent VBA modules and ThisWorkbook.txt in Excel. No macros or network calls were executed.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
