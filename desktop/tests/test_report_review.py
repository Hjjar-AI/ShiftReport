"""Drive production review callbacks with a headless widget/transport harness."""
import copy
from pathlib import Path
import tempfile
import unittest
from types import SimpleNamespace
from unittest.mock import Mock, patch
from desktop_report_review import install_send_review
from tests.support import client, shift
from tests.ui_support import Widget, toolkit, find, descendants


class ReportReviewTests(unittest.TestCase):
    def setUp(self):
        self.ui=toolkit();self.addCleanup(self.ui.close)
        self.directory=tempfile.TemporaryDirectory();self.addCleanup(self.directory.cleanup)
        self.window=Widget();self.area=Widget(self.window);self.actions=Widget(self.window)
        self.options_widget=Widget(self.area);self.options_widget.pack(fill='x')
        self.hidden_widget=Widget(self.area)
        self.local=shift();self.client=client();self.client._actor='cedar'
        self.owner=SimpleNamespace(client=self.client,store=SimpleNamespace(read=lambda:copy.deepcopy(self.local)),
                                   settings={},io=lambda action:action())
        self.options={'format':'pdf','layout':'cards'};self.targets=[None,'cedar'];self.result=[]
        self.delivery=SimpleNamespace(reviewed=self.local,needs_publish=False,check_targets=Mock(),
            require_unchanged=Mock(),report_day='2026-10-25',plan=SimpleNamespace(force_local=False))
        self.factory=self.enter(patch('desktop_report_review.ReportDelivery',return_value=self.delivery))
        self.open_pdf=self.enter(patch('desktop_platform.open_pdf'))
        self.confirm=self.enter(patch('desktop_report_review.messagebox.askyesno',return_value=True))
        self.error=self.enter(patch('desktop_report_review.messagebox.showerror'))
        def render(client,shift,target,*args,**kwargs):
            path=Path(self.directory.name)/('general.pdf' if target is None else target+'.pdf')
            path.write_bytes(b'%PDF-exact-'+str(target).encode());return path
        self.render=self.enter(patch('desktop_pdf.render_report',side_effect=render))
        install_send_review(self.owner,self.window,self.area,self.actions,lambda:dict(self.options),
                            lambda:list(self.targets),Mock(),self.result)

    def enter(self,manager):
        value=manager.__enter__();self.addCleanup(manager.__exit__,None,None,None);return value

    def primary(self):
        return next(widget for widget in self.actions.children if widget.layout.get('columnspan')==2 and
                    widget.cget('text')!='السابق')

    def preview_all(self):
        self.primary().invoke()
        if self.primary().cget('text')=='معاينة PDF التالي':self.primary().invoke()

    def test_options_have_one_advancing_primary_and_do_not_prepare_or_send_early(self):
        self.assertEqual('التالي — معاينة التقرير',self.primary().cget('text'))
        self.assertEqual(2,self.primary().layout['columnspan'])
        self.factory.assert_not_called();self.render.assert_not_called();self.confirm.assert_not_called()
        self.assertEqual([],self.result)

    def test_batch_requires_every_pdf_then_final_confirmation_and_returns_exact_bytes(self):
        self.primary().invoke()
        self.assertEqual('معاينة PDF التالي',self.primary().cget('text'));self.assertEqual(1,self.open_pdf.call_count)
        self.assertEqual('',self.options_widget.winfo_manager());self.assertEqual([],self.result)
        self.primary().invoke();self.assertEqual('رفع للشبكة وإرسال تقرير',self.primary().cget('text'))
        self.assertEqual(2,self.open_pdf.call_count);self.confirm.assert_not_called()
        self.primary().invoke();self.confirm.assert_called_once()
        self.assertEqual([(None,'pdf',b'%PDF-exact-None'),('cedar','pdf',b'%PDF-exact-cedar')],self.result[0]['payloads'])
        self.assertTrue(self.result[0]['exportPdf'])

    def test_failed_pdf_opener_does_not_mark_preview_open_and_retry_does_not_render_again(self):
        self.open_pdf.side_effect=OSError('synthetic opener failure');self.primary().invoke()
        self.assertEqual('معاينة PDF التالي',self.primary().cget('text'));self.assertEqual([],self.result)
        self.open_pdf.side_effect=None;self.primary().invoke()
        self.assertEqual(2,self.render.call_count);self.assertEqual([],self.result)

    def test_tampered_pdf_blocks_final_confirmation(self):
        self.preview_all();(Path(self.directory.name)/'general.pdf').write_bytes(b'%PDF-tampered')
        self.primary().invoke();self.assertEqual([],self.result);self.confirm.assert_not_called();self.error.assert_called_once()

    def test_changed_data_actor_options_or_target_each_invalidate_review(self):
        for change in ('data','actor','format','target'):
            with self.subTest(change=change):
                if self.primary().cget('text')!='التالي — معاينة التقرير':find(self.actions,'السابق').invoke()
                self.preview_all()
                if change=='data':self.local['patients'][0]['labs']+='later'
                elif change=='actor':self.client._actor+='other'
                elif change=='format':self.options['format']='text'
                else:self.targets.reverse()
                self.primary().invoke();self.assertEqual([],self.result)
        self.confirm.assert_not_called()

    def test_previous_restores_only_visible_options_and_discards_old_preview_approval(self):
        self.preview_all();find(self.actions,'السابق').invoke()
        self.assertEqual('pack',self.options_widget.winfo_manager());self.assertEqual('',self.hidden_widget.winfo_manager())
        self.assertEqual('التالي — معاينة التقرير',self.primary().cget('text'))
        self.primary().invoke();self.assertEqual('معاينة PDF التالي',self.primary().cget('text'))
        self.assertEqual(3,self.open_pdf.call_count);self.assertEqual([],self.result)

    def test_declining_final_confirmation_keeps_review_without_returning_delivery(self):
        self.preview_all();self.confirm.return_value=False;self.primary().invoke()
        self.assertEqual([],self.result);self.assertTrue(self.window.winfo_exists())

    def test_optional_copy_can_be_unchecked_and_is_captured_in_confirmed_result(self):
        self.preview_all()
        choice=next(widget for widget in descendants(self.area) if 'حفظ ملف PDF على الجهاز' in (widget.cget('text') or ''))
        choice.cget('variable').set(False);self.primary().invoke()
        self.assertFalse(self.result[0]['exportPdf'])

    def test_text_preview_needs_no_pdf_and_never_requests_a_pdf_copy(self):
        self.options['format']='text';self.targets[:]=[None]
        self.primary().invoke();self.render.assert_not_called();self.open_pdf.assert_not_called()
        self.assertEqual('رفع للشبكة وإرسال تقرير',self.primary().cget('text'))
        self.primary().invoke();self.assertEqual('text',self.result[0]['payloads'][0][1]);self.assertFalse(self.result[0]['exportPdf'])

    def test_cancel_never_returns_a_send_request(self):
        find(self.actions,'إلغاء').invoke();self.assertFalse(self.window.winfo_exists());self.assertEqual([],self.result)


if __name__=='__main__':unittest.main()
