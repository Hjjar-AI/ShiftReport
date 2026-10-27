"""Opt-in real Tk geometry checks. Use a disposable display and memory-only demo."""
import os
import time
from types import SimpleNamespace
import unittest


@unittest.skipUnless(os.environ.get('SHIFTREPORT_TK_SIMULATION')=='1',
                     'Set SHIFTREPORT_TK_SIMULATION=1 on an isolated display for real Tk checks')
class TkSimulationTests(unittest.TestCase):
    def setUp(self):
        import desktop_widgets as ctk
        from desktop_gui import Window
        self.ctk=ctk;ctk.set_widget_scaling(1);ctk.set_appearance_mode('Light')
        self.root=ctk.CTk()
        try:self.owner=Window(self.root,{},demo=True)
        except Exception:self.root.destroy();raise
        self.addCleanup(self.close_display_root)
        self.root.geometry('1150x820+0+0');self.errors=[]
        self.root.report_callback_exception=lambda kind,error,tb:self.errors.append(error)
        self.settle()

    def settle(self):
        deadline=time.monotonic()+.4
        while time.monotonic()<deadline:self.root.update();time.sleep(.015)
        self.assertEqual([],self.errors)

    def close_display_root(self):
        # This process owns this Tcl interpreter; prevent library timers from
        # referring to a destroyed root when the next isolated case starts.
        for token in self.root.tk.call('after','info'):self.root.tk.call('after','cancel',token)
        self.owner.close()

    def test_collapsed_view_options_leave_space_for_patients(self):
        self.assertLess(self.owner.view_controls.winfo_height(),10)
        self.assertGreater(self.owner.patients.winfo_height(),350)
        self.owner.view_controls.toggle();self.settle()
        self.assertGreater(self.owner.view_controls.winfo_height(),100)
        self.owner.view_controls.toggle();self.settle()
        self.assertLess(self.owner.view_controls.winfo_height(),10)

    def test_large_text_keeps_retired_workflow_action_hidden_and_search_full_width(self):
        self.ctk.set_widget_scaling(1.3);self.root.geometry('760x650+0+0');self.settle()
        self.owner.resize_content(SimpleNamespace(width=self.owner.content.winfo_width()));self.settle()
        self.assertEqual('',self.owner.workflow_action.winfo_manager())
        self.assertEqual('',self.owner.workflow_details.winfo_manager())
        self.assertGreater(self.owner.patients.winfo_height(),150)
        self.assertGreater(self.owner.search_box.winfo_width(),self.owner.content.winfo_width()-45)
        self.assertGreater(self.owner.add_patient_button.winfo_y(),self.owner.search_box.winfo_y())
        self.assertLessEqual(self.owner.metrics._label.winfo_reqwidth(),self.owner.metrics.winfo_width()+2)

    def test_long_button_label_keeps_all_text_and_grows_with_real_font_metrics(self):
        text='اسم طبيب طويل يحتاج إلى أكثر من سطر دون إخفاء الاسم أو تصغير الخط'
        control=self.ctk.CTkButton(self.owner.content,text=text,width=150,height=48)
        control.pack();self.settle()
        self.assertEqual(text,control.logical_text)
        self.assertGreater(control.winfo_height(),48)
        self.assertLessEqual(control._text_label.winfo_reqwidth(),control.winfo_width())
        control.destroy()

    def test_report_primary_stays_full_width_with_secondary_actions_below_after_scaling(self):
        from desktop_report_review import install_send_review
        window=self.ctk.CTkToplevel(self.root);window.geometry('740x680')
        area=self.ctk.CTkFrame(window);area.pack(fill='both',expand=True)
        actions=self.ctk.CTkFrame(window);actions.pack(fill='x')
        install_send_review(self.owner,window,area,actions,lambda:{'format':'pdf'},lambda:[None],lambda options:None,[])
        self.ctk.set_widget_scaling(1.3);self.settle()
        controls={child.logical_text:child for child in actions.winfo_children() if isinstance(child,self.ctk.CTkButton)}
        self.assertEqual(2,int(controls['التالي — معاينة التقرير'].grid_info()['columnspan']))
        self.assertEqual(0,int(controls['التالي — معاينة التقرير'].grid_info()['row']))
        self.assertEqual(1,int(controls['السابق'].grid_info()['row']))
        self.assertEqual(1,int(controls['إلغاء'].grid_info()['row']))
        window.destroy()

    def test_splash_loads_original_emblem_on_white_field_and_skip_is_single_use(self):
        from desktop_intro import LaunchIntro
        from unittest.mock import Mock
        window=self.ctk.CTkToplevel(self.root);ready=Mock()
        intro=LaunchIntro(window,ready);self.settle()
        try:
            self.assertIsNotNone(intro.logo.cget('image'))
            self.assertEqual('#FFFFFF',intro.logo.cget('fg_color'))
            intro.finish();intro.finish();ready.assert_called_once()
            self.assertFalse(intro.frame.winfo_exists())
        finally:window.destroy()

    def test_light_clinical_fields_have_visible_edges_and_all_card_sections_are_present(self):
        from desktop_appearance import LIGHT_FIELD,LIGHT_BORDER
        def all_children(widget):
            for child in widget.winfo_children():
                yield child
                yield from all_children(child)
        labels='\n'.join(getattr(widget,'logical_text','') for widget in all_children(self.owner.patients))
        patient=self.owner.store.read()['patients'][0]
        for field in ('initialDiagnosis','treatmentPlan','followUp','labs'):self.assertIn(patient[field],labels)
        failures=[]
        def inspect():
            window=next(child for child in self.root.winfo_children() if isinstance(child,self.ctk.CTkToplevel) and child.title()=='تحرير المريض')
            try:
                import customtkinter as native
                fields=[widget for widget in all_children(window) if isinstance(widget,native.CTkTextbox)]
                self.assertTrue(fields)
                for field in fields:
                    self.assertEqual(LIGHT_FIELD,field.cget('fg_color')[0])
                    self.assertEqual(LIGHT_BORDER,field.cget('border_color')[0])
                    self.assertEqual(2,field.cget('border_width'))
            except Exception as error:failures.append(error)
            finally:
                next(widget for widget in all_children(window) if isinstance(widget,self.ctk.CTkButton) and widget.logical_text=='حفظ المريض').invoke()
        self.root.after(150,inspect);self.owner.edit(patient)
        self.assertEqual([],failures)

    def test_arabic_editor_wheel_scrolls_over_fields_and_hands_off_at_text_boundaries(self):
        self.check_editor_wheel(shaped=True)

    def test_native_editor_wheel_scrolls_over_fields_and_hands_off_at_text_boundaries(self):
        self.check_editor_wheel(shaped=False)

    def check_editor_wheel(self,shaped):
        from unittest.mock import patch
        import traceback
        import customtkinter as native
        def report(kind,error,tb):
            traceback.print_exception(kind,error,tb)
            self.errors.append(error)
        self.root.report_callback_exception=report
        patient=self.owner.store.read()['patients'][0]
        failures=[]
        def descendants(widget):
            for child in widget.winfo_children():
                yield child
                yield from descendants(child)
        def inspect():
            window=next(child for child in self.root.winfo_children()
                        if isinstance(child,self.ctk.CTkToplevel) and child.title()=='تحرير المريض')
            try:
                fields=[child for child in descendants(window) if isinstance(child,native.CTkTextbox)]
                field=fields[0];text=field._textbox
                area=field.master
                while not hasattr(area,'_parent_canvas'):area=area.master
                canvas=area._parent_canvas
                original=field.get('1.0','end-1c')
                field.delete('1.0','end');field.insert('1.0','short')
                self.settle()
                for sequence,options in (('<Button-5>',{}),('<MouseWheel>',{'delta':-120})):
                    canvas.yview_moveto(0);self.settle()
                    text.event_generate(sequence,**options);self.settle()
                    self.assertGreater(canvas.yview()[0],0,'Short field trapped the wheel')
                field.delete('1.0','end');field.insert('1.0','\n'.join('line '+str(i) for i in range(80)))
                self.settle();canvas.yview_moveto(0);text.yview_moveto(0);self.settle()
                text.event_generate('<Button-5>');self.settle()
                self.assertGreater(text.yview()[0],0)
                self.assertEqual(0,canvas.yview()[0],'Form scrolled while text could scroll')
                text.yview_moveto(1);self.settle()
                text.event_generate('<Button-5>');self.settle()
                self.assertGreater(canvas.yview()[0],0,'Bottom of long text trapped the wheel')
                text.yview_moveto(0);self.settle()
                before=canvas.yview()[0]
                text.event_generate('<Button-4>');self.settle()
                self.assertLess(canvas.yview()[0],before,'Top of long text trapped the wheel')
                field.delete('1.0','end');field.insert('1.0',original)
                from desktop_sections import SectionPages
                tabs=next(child for child in descendants(window) if isinstance(child,SectionPages))
                tabs.set('البيانات الشخصية');self.settle()
                demographic_area=next(child for child in descendants(tabs.tab('البيانات الشخصية'))
                                      if hasattr(child,'_parent_canvas'))
                name=next(child for child in demographic_area.winfo_children()
                          if isinstance(child,(native.CTkTextbox,native.CTkEntry)))
                target=getattr(name,'_textbox',None) or name._entry
                demographic_area._parent_canvas.yview_moveto(0);self.settle()
                target.event_generate('<Button-5>');self.settle()
                self.assertGreater(demographic_area._parent_canvas.yview()[0],0,
                                   'Single-line field trapped the wheel')
            except Exception as error:failures.append(error)
            finally:
                next(child for child in descendants(window) if isinstance(child,self.ctk.CTkButton)
                     and child.logical_text=='حفظ المريض').invoke()
        with patch('desktop_widgets._VISUAL',shaped):
            self.root.after(150,inspect);self.owner.edit(patient)
            self.settle()
            self.assertEqual([],failures)


if __name__=='__main__':unittest.main()
