import copy
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch
from desktop_protocol_common import BridgeError
from desktop_publication import Review, approve, publish, plan, prepare
from desktop_wire import parse_bundle
from tests.support import client, shift


class PublicationLifecycleTests(unittest.TestCase):
    def setUp(self):
        self.state = dict(csvFileId='old', csvMessageId=10, csvUpdatedAt=1,
                          doctorsFileId='doctors', doctorsMessageId=20, doctorsUpdatedAt=30)
        self.remote = dict(index_id=1, state=copy.deepcopy(self.state), doctors=client().doctors, bundle=[])
        self.owner = SimpleNamespace(_actor='cedar', zone='Asia/Damascus', device='synthetic-device',
            config={}, fetch=Mock(side_effect=lambda: copy.deepcopy(self.remote)), authorize=Mock(),
            telegram=Mock(), baseline=None)
        self.owner.telegram.state.side_effect = lambda: (1, copy.deepcopy(self.state))
        self.owner.telegram.upload.return_value = dict(message_id=11, date=1800000000, document=dict(file_id='uploaded'))
        self.owner.telegram.write_state.side_effect = lambda _, state: copy.deepcopy(state)
        self.plan = Review(('old', 10), ('doctors', 20, 30), shift(), [], copy.deepcopy(self.remote), actor_id='cedar')

    def test_unapproved_or_changed_review_cannot_upload(self):
        with self.assertRaises(BridgeError): publish(self.owner, self.plan, Mock())
        approve(self.owner, self.plan)
        self.plan.shift['patients'][0]['labs'] = 'changed after review'
        with self.assertRaises(BridgeError): publish(self.owner, self.plan, Mock())
        self.owner.telegram.upload.assert_not_called()

    def test_actor_change_invalidates_approval(self):
        approve(self.owner, self.plan); self.owner._actor = 'other'
        with self.assertRaises(BridgeError): publish(self.owner, self.plan, Mock())
        self.owner.fetch.assert_not_called()

    def test_registry_change_blocks_even_explicit_force_local(self):
        self.plan.force_local = True; approve(self.owner, self.plan)
        self.remote['state']['doctorsMessageId'] = 21
        with self.assertRaises(BridgeError): publish(self.owner, self.plan, Mock())
        self.owner.telegram.upload.assert_not_called()

    def test_new_patient_pointer_requires_another_review(self):
        approve(self.owner, self.plan); self.remote['state']['csvMessageId'] = 12
        with self.assertRaises(BridgeError): publish(self.owner, self.plan, Mock())
        self.owner.telegram.upload.assert_not_called()

    def test_failure_to_persist_unknown_prevents_transport(self):
        approve(self.owner, self.plan)
        before = Mock(side_effect=OSError('synthetic storage failure'))
        with self.assertRaises(OSError): publish(self.owner, self.plan, before)
        self.owner.telegram.upload.assert_not_called()

    def test_racing_registry_update_during_upload_prevents_index_write(self):
        approve(self.owner, self.plan)
        def upload(*args):
            self.state['doctorsMessageId'] = 21
            return dict(message_id=11, date=1800000000, document=dict(file_id='uploaded'))
        self.owner.telegram.upload.side_effect = upload
        with self.assertRaises(BridgeError): publish(self.owner, self.plan, Mock())
        self.owner.telegram.write_state.assert_not_called()
        self.assertIsNone(self.owner.baseline)

    def test_authority_loss_after_upload_prevents_index_write(self):
        approve(self.owner, self.plan)
        self.owner.authorize.side_effect = [self.remote['doctors']['cedar'], BridgeError('synthetic demotion')]
        with self.assertRaises(BridgeError): publish(self.owner, self.plan, Mock())
        self.owner.telegram.upload.assert_called_once()
        self.owner.telegram.write_state.assert_not_called()

    def test_unconfirmed_index_cannot_advance_local_baseline(self):
        approve(self.owner, self.plan)
        self.owner.telegram.write_state.side_effect = BridgeError('synthetic pin failure')
        with self.assertRaises(BridgeError): publish(self.owner, self.plan, Mock())
        self.assertIsNone(self.owner.baseline)

    def test_success_uploads_exact_review_and_stamps_baseline_after_index_confirmation(self):
        approve(self.owner, self.plan)
        events = []
        def upload(raw, *args):
            self.assertEqual(['UNKNOWN'], events)
            parsed = parse_bundle(raw)[0]
            self.assertEqual(self.plan.shift['patients'][0]['labs'], parsed['patients'][0]['labs'])
            events.append('uploaded')
            return dict(message_id=11, date=1800000000, document=dict(file_id='uploaded'))
        self.owner.telegram.upload.side_effect = upload
        publish(self.owner, self.plan, lambda: events.append('UNKNOWN'))
        self.assertEqual(['UNKNOWN', 'uploaded'], events)
        self.assertEqual(11, self.owner.baseline['state']['csvMessageId'])
        self.assertEqual('old', self.owner.baseline['state']['previousCsvFileId'])

    def test_later_report_day_preserves_reviewed_ward_identity_and_allows_upload(self):
        approve(self.owner, self.plan)
        before=copy.deepcopy(self.plan.shift)
        with patch('desktop_protocol_common.current_date', return_value='2026-01-02'):
            publish(self.owner, self.plan, Mock())
        self.owner.telegram.upload.assert_called_once()
        self.assertEqual(before,self.owner.baseline['bundle'][0])


if __name__ == '__main__': unittest.main()
