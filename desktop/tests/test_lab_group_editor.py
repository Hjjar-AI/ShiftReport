"""Headless editor callbacks: preserve unsaved data through group/date operations."""
import copy
import unittest
from unittest.mock import patch

from desktop_lab_editor import lab_groups_editor
from desktop_labs import apply
from tests.support import lab_group_shift as grouped_shift, lab_groups as panels
from tests.ui_support import Widget, descendants, find, toolkit


class LabGroupEditorTests(unittest.TestCase):
    def setUp(self):
        harness=toolkit();harness.__enter__();self.addCleanup(harness.__exit__,None,None,None)
        self.root=Widget();self.source=grouped_shift()['patients'][0];self.before=copy.deepcopy(self.source)
        self.read=lab_groups_editor(self.root,self.source)

    def fields(self,label):
        result=[]
        for item in descendants(self.root):
            if item.cget('text')==label:
                siblings=item.master.children
                result.append(siblings[siblings.index(item)+1])
        return result

    def edit(self,widget,value):widget.delete(0,'end');widget.insert(0,value)

    def test_reorder_preserves_unsubmitted_title_results_and_dates_by_group_identity(self):
        self.edit(self.fields('عنوان المجموعة — اختياري')[0],'عنوان غير محفوظ')
        self.edit(self.fields('النتائج')[0],'نتيجة\nغير محفوظة')
        find(self.root,'نقل المجموعة لأعلى').invoke()
        groups=self.read()
        self.assertEqual(['renal','cbc'],[g['id'] for g in groups])
        self.assertEqual('عنوان غير محفوظ',groups[1]['title'])
        self.assertEqual('نتيجة\nغير محفوظة',groups[1]['results'])
        self.assertEqual('2024-02-29',groups[1]['date'])
        self.assertEqual(self.before,self.source)

    def test_date_callback_changes_only_its_group_and_survives_later_reorder(self):
        with patch('desktop_lab_editor.select_date') as picker:
            find(self.root,'تاريخ التحاليل: اختياري').invoke()
            picker.call_args.args[2]('2026-11-02')
        self.assertEqual('2024-02-29',self.read()[0]['date'])
        self.assertEqual('2026-11-02',self.read()[1]['date'])
        find(self.root,'نقل المجموعة لأعلى').invoke()
        self.assertEqual(('renal','2026-11-02'),(self.read()[0]['id'],self.read()[0]['date']))
        self.assertEqual(self.before,self.source)

    def test_clearing_or_cancelling_date_never_clears_results(self):
        with patch('desktop_lab_editor.select_date') as picker:
            find(self.root,'تاريخ التحاليل: 2024-02-29').invoke()
            self.assertEqual(panels(),self.read())  # Opening/cancelling alone is not an edit.
            picker.call_args.args[2](None)
        self.assertIsNone(self.read()[0]['date'])
        self.assertEqual(panels()[0]['results'],self.read()[0]['results'])
        self.assertEqual(self.before,self.source)

    def test_add_keeps_blank_unfinished_group_and_existing_unsaved_results(self):
        self.edit(self.fields('النتائج')[0],'pending clinical text')
        with patch('desktop_lab_editor.uuid.uuid4',return_value='new-group'):
            find(self.root,'إضافة مجموعة تحاليل').invoke()
        groups=self.read()
        self.assertEqual('pending clinical text',groups[0]['results'])
        self.assertEqual(dict(id='new-group',title='',date=None,results=''),groups[-1])
        self.assertEqual(self.before,self.source)

    def test_remove_requires_confirmation_and_final_removal_cannot_revive_old_results(self):
        with patch('desktop_lab_editor.messagebox.askyesno',return_value=False) as confirm:
            find(self.root,'حذف المجموعة').invoke()
            confirm.assert_called_once();self.assertEqual(panels(),self.read())
        with patch('desktop_lab_editor.messagebox.askyesno',return_value=True):
            find(self.root,'حذف المجموعة').invoke()
            self.assertEqual([panels()[1]],self.read())
            find(self.root,'حذف المجموعة').invoke()
        self.assertEqual([],self.read())
        captured=copy.deepcopy(self.source);apply(captured,self.read())
        self.assertEqual('',captured['labs']);self.assertEqual('[]',captured['labGroupsJson'])
        self.assertEqual(self.before,self.source)
