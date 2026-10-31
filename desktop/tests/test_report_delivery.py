"""Real delivery coordinator and encrypted ledger; all network calls are doubles."""
import copy
import tempfile
import unittest
from types import SimpleNamespace
from unittest.mock import Mock, patch
from desktop_delivery import ReportDelivery
from desktop_reports import ReportAttempt
from desktop_protocol_common import BridgeError, UncertainDelivery
from desktop_store import Workspace
from tests.support import client, shift


class ReportDeliveryTests(unittest.TestCase):
    def setUp(self):
        self.directory=tempfile.TemporaryDirectory();self.addCleanup(self.directory.cleanup)
        self.client=client();self.client._actor='cedar'
        self.store=Workspace(self.directory.name,self.client);self.local=shift();self.store.commit(draft=self.local)
        self.state=dict(csvFileId='csv',csvMessageId=10,doctorsFileId='doctors',doctorsMessageId=20,doctorsUpdatedAt=30)
        self.remote=dict(state=self.state,doctors=copy.deepcopy(self.client.doctors),bundle=[copy.deepcopy(self.local)])
        self.plan=SimpleNamespace(remote=self.remote,shift=self.local,force_local=False)
        self.owner=SimpleNamespace(client=self.client,store=self.store,io=lambda action:action(),
                                   report_attempt=ReportAttempt(),show_report_attempt=Mock(),status=Mock(),publish=Mock())
        def send(payload,reviewed,supervisor,pointer,registry,before,report_day):
            from desktop_reports import report_destination
            before(*report_destination(self.client,self.client.doctors,supervisor))
            return dict(message_id=99)
        self.client.send_pdf=Mock(side_effect=send);self.client.send_text=Mock(side_effect=send)
        self.client.approve=Mock()

    def delivery(self,needs_publish=False):
        with patch('desktop_delivery.prepare_delivery',return_value=(self.plan,copy.deepcopy(self.local),needs_publish)), \
             patch('desktop_delivery.current_date',return_value='2026-10-25'):
            return ReportDelivery(self.owner,self.local)

    def test_preparation_does_not_publish_or_send_and_captures_report_day(self):
        delivery=self.delivery(True)
        self.owner.publish.assert_not_called();self.client.send_pdf.assert_not_called()
        self.assertEqual('2026-10-25',delivery.report_day)
        from desktop_reports import report_destination
        for group,topic in ((0,0),(-300,0),(-300,7)):
            self.client.config.update(residentsChatId=group,residentsTopicId=topic)
            self.assertEqual((group or -100,(topic or None) if group else 3),report_destination(self.client,self.client.doctors))
            self.assertEqual((-200,None),report_destination(self.client,self.client.doctors,'cedar'))
        with self.assertRaises(BridgeError):delivery.require_connection()
        self.client.send_pdf.assert_not_called()

    def test_exact_reviewed_bytes_and_captured_day_reach_transport_after_durable_unknown(self):
        delivery=self.delivery();raw=b'%PDF-exact-preview'
        def send(payload,reviewed,supervisor,pointer,registry,before,report_day):
            self.assertEqual(raw,payload);self.assertEqual('2026-10-25',report_day)
            before(-100,3)
            reopened=Workspace(self.directory.name,client())
            self.assertEqual('UNKNOWN',reopened.receipts()[0][1][2])
            return dict(message_id=77)
        self.client.send_pdf.side_effect=send
        self.assertEqual(1,delivery.send([(None,'pdf',raw)],Mock()))
        self.assertEqual('SENT',self.store.receipts()[0][1][2])

    def test_local_change_after_preview_blocks_publication_and_delivery(self):
        delivery=self.delivery(True);changed=self.store.read();changed['patients'][0]['labs']='later'
        self.store.commit(draft=changed)
        with self.assertRaises(BridgeError):delivery.send([(None,'pdf',b'%PDF')],Mock())
        self.owner.publish.assert_not_called();self.client.send_pdf.assert_not_called()

    def test_unknown_destination_blocks_new_content_and_other_format(self):
        self.store.receipt_start('old-key','report',-100,3,'cedar',report_shift=self.local['meta']['shiftId'])
        delivery=self.delivery()
        with self.assertRaises(BridgeError):delivery.send([(None,'text','different content')],Mock())
        self.client.send_text.assert_not_called()

    def test_uncertain_send_keeps_original_durable_block_and_skips_local_copy(self):
        delivery=self.delivery();copy_pdf=Mock()
        def send(*args,**kwargs):
            args[5](-100,3);raise UncertainDelivery('synthetic connection loss')
        self.client.send_pdf.side_effect=send
        with self.assertRaises(UncertainDelivery):delivery.send([(None,'pdf',b'%PDF')],Mock(),after_delivery=copy_pdf)
        self.assertEqual('UNKNOWN',Workspace(self.directory.name,client()).receipts()[0][1][2])
        copy_pdf.assert_not_called()

    def test_partial_batch_keeps_confirmed_first_and_unknown_second_without_local_copy(self):
        delivery=self.delivery();copy_pdf=Mock();sent=[]
        def send(*args,**kwargs):
            target=args[2];args[5](-100 if target is None else -200,3 if target is None else None)
            sent.append(target)
            if target:raise UncertainDelivery('synthetic partial send')
            return dict(message_id=1)
        self.client.send_pdf.side_effect=send
        with self.assertRaises(UncertainDelivery):delivery.send([(None,'pdf',b'%PDF1'),('cedar','pdf',b'%PDF2')],Mock(),after_delivery=copy_pdf)
        self.assertEqual([None,'cedar'],sent)
        self.assertEqual(['SENT','UNKNOWN'],[row[2] for _,row in self.store.receipts()]);copy_pdf.assert_not_called()

    def test_optional_copy_runs_once_only_after_every_destination_is_confirmed(self):
        delivery=self.delivery();events=[]
        def copy_pdf():
            self.assertEqual(['SENT','SENT'],[row[2] for _,row in self.store.receipts()]);events.append('copy')
        self.assertEqual(2,delivery.send([(None,'pdf',b'%PDF1'),('cedar','pdf',b'%PDF2')],Mock(),after_delivery=copy_pdf))
        self.assertEqual(['copy'],events)

    def test_copy_failure_keeps_confirmed_delivery_and_never_repeats_transport(self):
        delivery=self.delivery()
        with self.assertRaisesRegex(BridgeError,'لا تُعد الإرسال'):
            delivery.send([(None,'pdf',b'%PDF')],Mock(),after_delivery=Mock(side_effect=OSError('synthetic disk full')))
        self.assertEqual('CONFIRMED',self.owner.report_attempt.delivery)
        self.assertEqual('SENT',self.store.receipts()[0][1][2]);self.client.send_pdf.assert_called_once()

    def test_receipt_storage_failure_after_send_reports_success_without_resending(self):
        delivery=self.delivery()
        with patch.object(self.store,'receipt_finish',side_effect=OSError('synthetic disk full')):
            with self.assertRaisesRegex(BridgeError,'لا تعد إرسال'):delivery.send([(None,'pdf',b'%PDF')],Mock())
        self.assertEqual('CONFIRMED',self.owner.report_attempt.delivery);self.client.send_pdf.assert_called_once()
        self.assertEqual('UNKNOWN',Workspace(self.directory.name,client()).receipts()[0][1][2])

    def test_repeat_requires_explicit_extra_copy_approval_and_decline_skips_copy(self):
        delivery=self.delivery();payload=[(None,'pdf',b'%PDF')]
        delivery.send(payload,Mock());confirm=Mock(return_value=False);copy_pdf=Mock()
        self.assertEqual(0,delivery.send(payload,confirm,after_delivery=copy_pdf))
        confirm.assert_called_once_with(None);copy_pdf.assert_not_called();self.client.send_pdf.assert_called_once()
        self.assertEqual(1,delivery.send(payload,Mock(return_value=True)))
        self.assertEqual(['SENT','SENT'],[row[2] for _,row in self.store.receipts()])

    def test_missing_supervisor_destination_blocks_before_publication(self):
        delivery=self.delivery(True);delivery.snapshot['doctors']['cedar']['destination']=None
        with self.assertRaises(BridgeError):delivery.send([('cedar','pdf',b'%PDF')],Mock())
        self.owner.publish.assert_not_called();self.client.send_pdf.assert_not_called()

    def test_partial_batch_restart_blocks_all_transport_until_explicit_unknown_resolution(self):
        payloads=[(None,'pdf',b'%PDF1'),('cedar','pdf',b'%PDF2')]
        def partial(*args,**kwargs):
            target=args[2];args[5](-100 if target is None else -200,3 if target is None else None)
            if target:raise UncertainDelivery('synthetic response loss')
            return dict(message_id=1)
        self.client.send_pdf.side_effect=partial
        with self.assertRaises(UncertainDelivery):self.delivery().send(payloads,Mock())
        reopened=Workspace(self.directory.name,client())
        self.owner.store=reopened;self.store=reopened
        self.client.send_pdf.reset_mock()
        with self.assertRaises(BridgeError):self.delivery().send(payloads,Mock())
        self.client.send_pdf.assert_not_called()
        self.assertEqual(['SENT','UNKNOWN'],[row[2] for _,row in reopened.receipts()])
        reopened.resolve_unknown(1,True,self.client)
        def send(*args,**kwargs):
            self.assertEqual('cedar',args[2]);args[5](-200,None);return dict(message_id=2)
        self.client.send_pdf.side_effect=send
        decline=Mock(return_value=False);copy_pdf=Mock()
        self.assertEqual(1,self.delivery().send(payloads,decline,after_delivery=copy_pdf))
        decline.assert_called_once_with(None)
        self.client.send_pdf.assert_called_once();copy_pdf.assert_not_called()
        self.assertEqual('SENT',reopened.receipts()[0][1][2])
        self.assertEqual('SENT',reopened.receipts()[-1][1][2])
        self.owner.publish.assert_not_called()


if __name__=='__main__':unittest.main()
