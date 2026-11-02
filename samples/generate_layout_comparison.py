#!/usr/bin/env python3
"""Render disposable A5 layout comparisons from the fictional sample, without app/network access."""
from datetime import datetime
from pathlib import Path
from types import SimpleNamespace
import json
import shutil
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'desktop'))
from desktop_pdf_cards import render_cards
from desktop_pdf_content import normalize_options
from desktop_protocol import ZoneInfo
from desktop_wire import BUNDLE, parse_bundle


# Show a filled follow-up on the first card; retain two empty follow-ups for omission examples.
SELECTED_PATIENT_INDICES = (26, 0, 9, 17, 36, 49)

def generate():
    source = ROOT / 'samples/synthetic-patients-50-expanded.csv'
    shift = parse_bundle((BUNDLE + '\n' + source.read_text(encoding='utf-8-sig')).encode())[0]
    shift['patients'] = [shift['patients'][i] for i in SELECTED_PATIENT_INDICES]
    doctors = {
        'demo-supervisor': dict(name='د. الأرز الشامي', clinicalRole='SUPERVISOR', gender='M'),
        'demo-resident': dict(name='د. الصنوبر', clinicalRole='RESIDENT', gender='M'),
    }
    for row in shift['patients']:
        row.update(responsibleSupervisorId='demo-supervisor', responsibleResidentId='demo-resident')
    shift['meta']['doctors'] = ','.join(doctors)
    client = SimpleNamespace(demo=True, doctors=doctors, zone='Asia/Damascus')
    now = datetime.now(ZoneInfo(client.zone))
    output = ROOT / 'samples/layout-comparison'
    output.mkdir(exist_ok=True)
    layouts = [('a5-current-stacked.pdf', False, False),
               ('a5-lab-groups-two-columns.pdf', True, False),
               ('a5-all-sections-two-columns.pdf', True, True)]
    for name, labs, sections in layouts:
        options = normalize_options(dict(two_column_labs=labs, two_column_sections=sections))
        temporary = render_cards(client, shift, None, ROOT / 'desktop/fonts/Tajawal-Regular.ttf',
                                 options, now, report_day=shift['meta']['shiftDate'])
        shutil.copy2(temporary, output / name)
        print(output / name)
    (output / 'README.md').write_text(
        '# A5 layout comparison\n\n'
        'The same six fictional sample patients in three layouts, with a filled follow-up on the first card '
        '(four filled/two empty follow-ups total): current stacked panels, lab groups '
        'in two columns, and all clinical sections in two columns. Demographics remain full width; '
        'each section uses its own content height. Read down the right column, then down the left, '
        'then continue on the next page. Diagnosis, treatment, follow-up, ordered lab groups and finally tasks '
        'retain their source order, with independent thin inset separators. No nested '
        'treatment/lab columns in the full two-column layout. Every page is marked DEMO.\n\n'
        'Preview presentation includes current Android nominal body/heading/name sizes (12.5/13/16pt), '
        'normal-text-colored admission number beside its heading, no dashed team/admission separators, heavier dots and '
        '12% theme-tinted section headings. '
        'Generated with the production desktop ReportLab renderer and bundled Tajawal font. '
        'Android uses Android Canvas/Paint and requires separate compilation/native PDF acceptance; '
        'these files do not establish pixel or pagination parity. No network/app/database access.\n\n'
        'Regenerate: `PYTHONDONTWRITEBYTECODE=1 desktop/.venv/bin/python samples/generate_layout_comparison.py`.\n',
        encoding='utf-8')
    (output / 'source-patients.json').write_text(json.dumps(
        [dict(id=p['externalId'], name=p['name']) for p in shift['patients']], ensure_ascii=False, indent=2)+'\n')


if __name__ == '__main__':
    generate()
