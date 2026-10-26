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


if __name__=='__main__':unittest.main()
