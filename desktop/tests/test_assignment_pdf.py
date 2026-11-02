"""Real dummy PDF output checks using installed Poppler; no Android parity claim."""
import copy
from pathlib import Path
import shutil
import subprocess
import unittest
import xml.etree.ElementTree as ET

from desktop_demo import DemoClient, DemoWorkspace
from desktop_pdf import render_assignments
from desktop_protocol_common import BridgeError


@unittest.skipUnless(shutil.which('pdftotext'),'Poppler pdftotext is required for PDF output inspection')
class AssignmentPdfTests(unittest.TestCase):
    def setUp(self):
        self.client=DemoClient();self.store=DemoWorkspace(self.client);self.local=self.store.read()
        self.font=str(Path(__file__).resolve().parents[1]/'fonts/Tajawal-Regular.ttf')

    def render(self,source):
        path=render_assignments(self.client,source,self.font,report_day='2026-10-26')
        self.addCleanup(shutil.rmtree,path.parent,True)
        xml=subprocess.run(['pdftotext','-bbox',str(path),'-'],capture_output=True,check=True).stdout
        pages=ET.fromstring(xml).findall('.//{http://www.w3.org/1999/xhtml}page')
        return path,pages

    def words(self,page):return page.findall('.//{http://www.w3.org/1999/xhtml}word')

    def assert_a5_geometry(self,page):
        self.assertAlmostEqual(420,float(page.attrib['width']),delta=1)
        self.assertAlmostEqual(595,float(page.attrib['height']),delta=1)
        words=self.words(page)
        self.assertLess(min(float(word.attrib['yMin']) for word in words),30,'Compact title should stay at the top')
        for word in words:
            self.assertGreaterEqual(float(word.attrib['xMin']),0)
            self.assertLessEqual(float(word.attrib['xMax']),420)
            self.assertGreaterEqual(float(word.attrib['yMin']),0)
            self.assertLessEqual(float(word.attrib['yMax']),595)

    def test_short_a5_sheet_has_top_header_timestamp_and_demo_mark(self):
        source=copy.deepcopy(self.local);source['patients']=source['patients'][:6]
        path,pages=self.render(source)
        self.assertTrue(path.name.endswith('_DEMO.pdf'));self.assertEqual(1,len(pages))
        self.assert_a5_geometry(pages[0]);self.assertIn('DEMO',' '.join(word.text or '' for word in self.words(pages[0])))

    def test_long_a5_sheet_retains_all_patients_with_repeated_headers_and_demo_footer(self):
        source=copy.deepcopy(self.local);template=source['patients'][0]
        source['patients']=[]
        for index in range(110):
            row=copy.deepcopy(template);row.update(externalId='dummy-'+str(index),name='PAT{:03d}'.format(index),assignmentChangedOn='2026-10-26')
            source['patients'].append(row)
        before=copy.deepcopy(source)
        _,pages=self.render(source);self.assertGreater(len(pages),1)
        all_text=' '.join(word.text or '' for page in pages for word in self.words(page))
        for index in range(110):self.assertEqual(1,all_text.count('PAT{:03d}'.format(index)))
        for page in pages:
            self.assert_a5_geometry(page);self.assertIn('DEMO',' '.join(word.text or '' for word in self.words(page)))
        self.assertEqual(before,source)

    def test_empty_roster_is_allowed_without_changing_stored_identity_or_assignments(self):
        source=copy.deepcopy(self.local);source['meta']['doctors']='';before=copy.deepcopy(source)
        _,pages=self.render(source)
        self.assertEqual(before,source);self.assertTrue(pages)

    def test_admin_authority_is_checked_before_and_after_rendering(self):
        original=self.client.require_local_admin
        calls=[]
        def changed_actor():
            value=copy.deepcopy(original());calls.append(1)
            if len(calls)>1:value['uid']='another'
            return value
        self.client.require_local_admin=changed_actor
        with self.assertRaises(BridgeError):render_assignments(self.client,self.local,self.font,report_day='2026-10-26')
        self.assertEqual(2,len(calls))


if __name__=='__main__':unittest.main()
