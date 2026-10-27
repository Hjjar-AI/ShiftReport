"""Doctor-facing callback/layout contracts; actual Tk display acceptance is separate."""
import copy
import json
import unittest
from unittest.mock import Mock, patch
from desktop_protocol_common import BridgeError
from desktop_workflow import set_task_done
from tests.ui_support import toolkit, Widget, Pages, descendants, find


class DoctorWorkspaceTests(unittest.TestCase):
    def setUp(self):
        self.ui=toolkit();self.addCleanup(self.ui.close)
        from desktop_gui import Window
        self.root=Widget();self.owner=Window(self.root,{},demo=True)
        self.addCleanup(self.owner.close)

    def test_roster_and_tasks_are_direct_navigation_and_destination_is_inside_report_review(self):
        for title in ('أطباء المناوبة','مهام الجناح','ملخص الجناح','مركز المزامنة'):
            control=find(self.root,title)
            self.assertEqual('pack',control.winfo_manager())
            self.assertIs(control.master,self.owner.settings_button.master)
        self.assertEqual('',self.owner.supervisor.winfo_manager())
        self.assertEqual('pack',self.owner.assignments_button.winfo_manager())
        first_doctor=next(iter(self.owner.client.doctors.values()))['name']
        self.assertNotIn(first_doctor,self.owner.metrics.cget('text'))
        self.owner.show_workflow_details=True;self.owner.render()
        self.assertIn(first_doctor,self.owner.metrics.cget('text'))

    def test_account_header_opens_account_without_changing_clinical_data(self):
        before=self.owner.store.read();find(self.root,'الحساب').invoke()
        self.assertEqual('window',self.owner.security_window.winfo_manager())
        self.assertEqual(before,self.owner.store.read())

    def test_about_is_available_in_demo_and_retains_original_credits(self):
        before=self.owner.store.read()
        from desktop_intro import SUPERVISOR,CONTRIBUTORS,ORIGINAL_AUTHOR
        def inspect(window):
            text='\n'.join(widget.cget('text') or '' for widget in descendants(window))
            for credit in (SUPERVISOR,*CONTRIBUTORS,ORIGINAL_AUTHOR):self.assertIn(credit,text)
            find(window,'إغلاق').invoke()
        self.root.on_wait=inspect
        with patch('desktop_intro.set_emblem'):find(self.root,'حول التطبيق').invoke()
        self.assertEqual(before,self.owner.store.read())

    def test_patient_cards_show_direct_actions_without_expansion_or_move_controls(self):
        frame=next(value[1] for key,value in self.owner.patient_widgets.items() if key[0]=='patient')
        primary=next(child for child in frame.children if any(grandchild.cget('text')=='تحرير' for grandchild in child.children))
        self.assertEqual(['تحرير','تثبيت للعرض','حذف'],[child.cget('text') for child in primary.children if child.winfo_manager()=='grid'])
        self.assertFalse(any(widget.cget('text')=='الملف الكامل' for widget in descendants(frame)))
        self.assertEqual({0,1,2},{child.layout['column'] for child in primary.children if child.winfo_manager()=='grid'})
        self.assertFalse(any(widget.cget('text') in {'طي','توسيع','توسيع الكل','طي الكل','نقل لأعلى','نقل لأسفل','المزيد'} for widget in descendants(self.root)))
        row=self.owner.store.read()['patients'][0]
        with patch.object(self.owner,'edit') as edit:
            find(frame,'تحرير').invoke()
            edit.assert_called_once_with(row)
        text='\n'.join(widget.cget('text') or '' for widget in descendants(frame))
        for field in ('initialDiagnosis','treatmentPlan','followUp','labs'):self.assertIn(row[field],text)
        for task in json.loads(row['tasksJson']):self.assertIn(task['description'],text)

    def test_pinning_changes_only_presentation_and_keeps_full_content(self):
        before=self.owner.store.read();key=before['patients'][-1]['externalId']
        self.owner.toggle_card(key);self.assertIn(key,self.owner.cards.pinned)
        self.assertEqual(before,self.owner.store.read())
        self.owner.toggle_card(key);self.assertEqual(set(),self.owner.cards.pinned)

    def test_direct_delete_cancel_keeps_patient_and_requires_named_confirmation(self):
        before=self.owner.store.read();row=before['patients'][0]
        frame=self.owner.patient_widgets[('patient',row['externalId'])][1]
        with patch('desktop_patient_actions.messagebox.askyesno',return_value=False) as confirm,patch.object(self.owner.store,'delete_patient',wraps=self.owner.store.delete_patient) as remove:
            find(frame,'حذف').invoke()
            confirm.assert_called_once();remove.assert_not_called()
            self.assertIn(row['name'],confirm.call_args.args[1])
            self.assertEqual('no',confirm.call_args.kwargs['default'])
            self.assertIs(self.root,confirm.call_args.kwargs['parent'])
        self.assertEqual(before,self.owner.store.read())

    def test_direct_delete_confirm_recycles_complete_record_and_allows_restore(self):
        before=self.owner.store.read();row=before['patients'][0]
        frame=self.owner.patient_widgets[('patient',row['externalId'])][1]
        with patch('desktop_patient_actions.messagebox.askyesno',return_value=True) as confirm:
            find(frame,'حذف').invoke();confirm.assert_called_once()
        self.assertNotIn(row['externalId'],[item['externalId'] for item in self.owner.store.read()['patients']])
        self.assertEqual(row,self.owner.store.state['recycle'][0]['patient'])
        self.assertNotIn(('patient',row['externalId']),self.owner.patient_widgets)
        self.owner.store.restore_patient(row['externalId'],None)
        self.assertEqual(before,self.owner.store.read())

    def test_pinning_stays_display_only_and_saved_sort_remains_available(self):
        before=self.owner.store.read();key=before['patients'][-1]['externalId']
        frame=self.owner.patient_widgets[('patient',key)][1]
        find(frame,'تثبيت للعرض').invoke()
        self.assertIn(key,self.owner.cards.pinned);self.assertEqual(before,self.owner.store.read())
        self.assertFalse(hasattr(self.owner,'move'))
        with patch('desktop_patient_actions.messagebox.askyesno',return_value=True):self.owner.sort_patients('name')
        saved=self.owner.store.read()
        expected=sorted(before['patients'],key=lambda row:(row['isPriority']!='Y',row['name'].lower(),row['externalId']))
        self.assertEqual([row['externalId'] for row in expected],[row['externalId'] for row in saved['patients']])
        self.assertEqual('NAME',json.loads(saved['meta']['sortSpec'])['levels'][0]['field'])
        self.assertEqual(key,self.owner.patient_display(saved)[0][2]['externalId'])
        find(self.owner.patient_widgets[('patient',key)][1],'إلغاء التثبيت').invoke()
        self.assertEqual([row['externalId'] for row in expected],[row['externalId'] for _,_,row in self.owner.patient_display(self.owner.store.read())])

    def test_search_debounce_waits_for_last_query_and_cancels_on_destroy(self):
        from desktop_ui_events import debounce
        from desktop_layout import build_ward_controls
        from types import SimpleNamespace
        with patch('desktop_layout.debounce',debounce),patch('desktop_ui_events.tk.Misc.bind') as binding:
            build_ward_controls(self.owner)
        self.owner.render=Mock()
        previous=set(self.root.timers)
        for query in ('ق','قه','قهوة'):self.owner.search.set(query)
        pending=set(self.root.timers)-previous
        self.assertEqual(1,len(pending));token=pending.pop()
        self.assertEqual(300,self.root.delays[token]);self.owner.render.assert_not_called()
        self.root.timers.pop(token)()
        self.owner.render.assert_called_once()
        self.assertEqual('قهوة',self.owner.search.get())
        self.owner.search.set('شاي')
        binding.call_args.args[2](SimpleNamespace(widget=self.root))
        self.assertEqual(previous,set(self.root.timers))
        self.owner.render.assert_called_once()

    def test_edit_opens_clinical_first_and_preserves_multiline_content_on_save(self):
        row=self.owner.store.read()['patients'][0]
        def interact(window):
            pages=next(widget for widget in descendants(window) if isinstance(widget,Pages))
            self.assertEqual('السريرية',pages.get())
            find(window,'حفظ المريض').invoke()
        self.root.on_wait=interact
        self.owner.edit(row)
        saved=self.owner.store.read()['patients'][0]
        for field in ('initialDiagnosis','treatmentPlan','followUp','labs','name','tasksJson'):
            self.assertEqual(json.loads(row[field]),json.loads(saved[field])) if field=='tasksJson' else self.assertEqual(row[field],saved[field])

    def test_add_opens_demographics_first_and_only_explicit_save_inserts_patient(self):
        before=self.owner.store.read()
        def interact(window):
            pages=next(widget for widget in descendants(window) if isinstance(widget,Pages))
            self.assertEqual('البيانات الشخصية',pages.get())
            self.assertEqual(before,self.owner.store.read())
            find(window,'حفظ المريض').invoke()
        self.root.on_wait=interact;self.owner.add_patient()
        self.assertEqual(len(before['patients'])+1,len(self.owner.store.read()['patients']))

    def test_last_patient_editor_does_not_offer_save_next(self):
        row=self.owner.store.read()['patients'][-1]
        def interact(window):
            self.assertFalse(any(widget.cget('text')=='حفظ والتالي' for widget in descendants(window)))
            window.destroy()
        self.root.on_wait=interact;self.owner.edit(row)

    def test_task_completion_and_undo_preserve_other_patients_and_unrelated_clinical_edits(self):
        before=self.owner.store.read();row=before['patients'][0];task=json.loads(row['tasksJson'])[0]
        scope=(self.owner.client.project_id,before['meta']['shiftId'],row['externalId'],task['id'])
        set_task_done(self.owner,*scope,True,False)
        changed=self.owner.store.read();changed['patients'][0]['labs']='new clinical result'
        self.owner.store.commit(draft=changed)
        set_task_done(self.owner,*scope,False,True)
        after=self.owner.store.read()
        self.assertFalse(json.loads(after['patients'][0]['tasksJson'])[0]['done'])
        self.assertEqual('new clinical result',after['patients'][0]['labs'])
        from desktop_model import patient_edit_content
        self.assertEqual([patient_edit_content(patient) for patient in before['patients'][1:]],
                         [patient_edit_content(patient) for patient in after['patients'][1:]])

    def test_task_stale_status_deleted_patient_and_wrong_scope_are_rejected(self):
        draft=self.owner.store.read();row=draft['patients'][0];task=json.loads(row['tasksJson'])[0]
        scope=(self.owner.client.project_id,draft['meta']['shiftId'],row['externalId'],task['id'])
        before=self.owner.store.read()
        for args in ((scope,True),(('other',*scope[1:]),False),((scope[0],'other',*scope[2:]),False),
                     ((scope[0],scope[1],'missing',scope[3]),False),((*scope[:3],'missing'),False)):
            with self.subTest(args=args),self.assertRaises(BridgeError):set_task_done(self.owner,*args[0],True,args[1])
        self.assertEqual(before,self.owner.store.read())

    def test_locked_session_cannot_complete_task(self):
        draft=self.owner.store.read();row=draft['patients'][0];task=json.loads(row['tasksJson'])[0]
        self.owner.locked=True
        with self.assertRaises(BridgeError):set_task_done(self.owner,self.owner.client.project_id,draft['meta']['shiftId'],row['externalId'],task['id'],True,False)
        self.owner.locked=False

    def test_grouped_task_board_completes_with_two_second_undo_and_refresh_cancels_timer(self):
        from desktop_workflow import task_board
        before=self.owner.store.read()
        def interact(window):
            controls=[widget for widget in descendants(window) if widget.cget('text')=='إتمام المهمة']
            self.assertEqual(6,len(controls))
            controls[0].invoke()
            self.assertTrue(json.loads(self.owner.store.read()['patients'][0]['tasksJson'])[0]['done'])
            self.assertEqual([2000],list(window.delays.values()))
            find(window,'تراجع عن الإتمام').invoke()
            self.assertFalse(json.loads(self.owner.store.read()['patients'][0]['tasksJson'])[0]['done'])
            self.assertEqual({},window.timers)
            window.destroy()
        self.root.on_wait=interact;task_board(self.owner)
        self.assertEqual(before['patients'][0]['labs'],self.owner.store.read()['patients'][0]['labs'])

    def test_autosave_failure_warns_inside_patient_form_and_retains_typed_text_for_retry(self):
        row=self.owner.store.read()['patients'][0]
        def interact(window):
            field=next(widget for widget in descendants(window) if widget.content==row['treatmentPlan'])
            field.delete('1.0','end');field.insert('1.0','retained multiline\nnew treatment')
            autosave=next(iter(window.timers.values()))
            with patch.object(self.owner.store,'save_patient_form',side_effect=OSError('synthetic disk failure')):
                autosave()
            warning='تعذر حفظ مسودة هذا المريض تلقائياً؛ أبق النافذة مفتوحة وأعد الحفظ.'
            self.assertTrue(find(window,warning).winfo_exists())
            self.assertEqual('retained multiline\nnew treatment',field.get('1.0','end-1c'))
            find(window,'حفظ المريض').invoke()
        self.root.on_wait=interact;self.owner.edit(row)
        self.assertEqual('retained multiline\nnew treatment',self.owner.store.read()['patients'][0]['treatmentPlan'])


if __name__=='__main__':unittest.main()
