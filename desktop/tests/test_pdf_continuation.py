"""Real PDF continuation/content/link checks; no visual or renderer parity claim."""
from pathlib import Path
import copy
import re
import shutil
import subprocess
import unittest

from desktop_pdf import render_report
from tests.support import client, shift


@unittest.skipUnless(shutil.which('pdftotext'), 'Poppler required to inspect rendered output')
class PdfContinuationTests(unittest.TestCase):
    def render(self, options):
        owner=client(); owner.demo=True
        source=shift(); row=source['patients'][0]
        markers=['LINE{:03d}'.format(index) for index in range(100)]
        row['labs']='\n'.join(marker+' نتيجة تجريبية طويلة' for marker in markers)
        row['treatmentPlan']='\n'.join('DRUG{:03d} 123 mg'.format(index) for index in range(60))
        path=render_report(owner,source,font_path=str(Path(__file__).resolve().parents[1]/'fonts/Tajawal-Regular.ttf'),
                           options=options,report_day='2026-10-27')
        self.addCleanup(shutil.rmtree,path.parent,True)
        text=subprocess.run(['pdftotext','-layout',str(path),'-'],capture_output=True,check=True,timeout=30).stdout.decode()
        pages=[page for page in text.split('\f') if page.strip()]
        self.assertGreater(len(pages),2)
        for marker in markers+['DRUG{:03d}'.format(index) for index in range(60)]:
            self.assertEqual(1,text.count(marker),'Lost or duplicated continuation marker '+marker)
        for page in pages:self.assertIn('DEMO',page)
        self.assertEqual('synthetic-shift',source['meta']['shiftId'])
        return path,pages

    def test_a5_single_long_patient_preserves_every_line_and_valid_page_fit_links(self):
        path,pages=self.render(dict(paper='A5',layout='cards',local_draft=True))
        raw=path.read_bytes()
        objects={int(number):body for number,body in re.findall(rb'(\d+)\s+0\s+obj\s*(.*?)endobj',raw,re.S)}
        page_ids={number for number,body in objects.items() if re.search(rb'/Type\s*/Page\b',body)}
        destinations=re.findall(rb'/Dest\s*\[\s*(\d+)\s+0\s+R\s*/Fit\s*\]',raw)
        self.assertTrue(destinations,'Expected index and return links')
        self.assertTrue(all(int(target) in page_ids for target in destinations))
        self.assertEqual(len(pages),len(page_ids))
        # Every annotation keeps a positive click area within the A5 sheet.
        for body in objects.values():
            if b'/Subtype /Link' not in body:continue
            rectangle=re.search(rb'/Rect\s*\[\s*([\d.]+)\s+([\d.]+)\s+([\d.]+)\s+([\d.]+)\s*\]',body)
            self.assertIsNotNone(rectangle)
            left,bottom,right,top=map(float,rectangle.groups())
            self.assertTrue(0<=left<right<=421);self.assertTrue(0<=bottom<top<=596)

    def test_a4_linear_single_long_patient_preserves_every_continuation_line(self):
        self.render(dict(paper='A4',layout='paragraphs',local_draft=True))

    def test_dated_lab_panels_keep_every_result_in_order_across_repeated_headers(self):
        from desktop_labs import apply
        owner=client();owner.demo=True
        source=shift();row=source['patients'][0]
        markers=['LAB{:03d}'.format(index) for index in range(120)]
        groups=[dict(id='one',title='PANEL_ONE '+('عنوان طويل ' * 16),date='2024-02-29',
                     results='\n'.join(marker+' = 12.5 mg/dL' for marker in markers[:90])),
                dict(id='two',title='PANEL_TWO',date=None,
                     results='\n'.join(marker+' = 140 mmol/L' for marker in markers[90:]))]
        apply(row,groups);before=copy.deepcopy(source)
        path=render_report(owner,source,font_path=str(Path(__file__).resolve().parents[1]/'fonts/Tajawal-Regular.ttf'),
                           options=dict(paper='A5',layout='cards',local_draft=True),report_day='2026-10-27')
        self.addCleanup(shutil.rmtree,path.parent,True)
        text=subprocess.run(['pdftotext','-layout',str(path),'-'],capture_output=True,check=True,timeout=30).stdout.decode()
        pages=[page for page in text.split('\f') if page.strip()]
        self.assertGreater(len(pages),2)
        for marker in markers:self.assertEqual(1,text.count(marker),'Lost or duplicated lab result '+marker)
        self.assertEqual(sorted(text.index(marker) for marker in markers),[text.index(marker) for marker in markers])
        self.assertGreater(text.count('PANEL_ONE'),1,'Expected repeated heading for continued panel')
        # Poppler may reorder numeric date components beside RTL glyph runs.
        self.assertRegex(text,r'(?:2024-02-29|29-02-2024)')
        self.assertGreater(text.index('PANEL_TWO'),text.index('LAB089'))
        for page in pages:self.assertIn('DEMO',page)
        self.assertEqual(before,source)


if __name__=='__main__':unittest.main()
