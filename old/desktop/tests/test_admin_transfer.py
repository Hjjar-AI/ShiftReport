"""Reviewed owner handover against synthetic transport and a real encrypted workspace."""
import copy
import tempfile
import unittest
from unittest.mock import Mock, patch
from desktop_doctors import Administration, permanent, rank
from desktop_protocol_common import BridgeError, UncertainDelivery, require
from desktop_wire import parse_doctors
from desktop_store import Workspace
from tests.support import client, shift


class PermanentAdminTransferTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.client = client()
        self.client._actor = 'cedar'
        self.client.config['doctorsTopicId'] = 4
        recipient = dict(uid='oak', name='بلوط', telegram_id=456, gender='F', clinicalRole='RESIDENT',
                         destination=None, teamSupervisorId='cedar', title='', **{'#':'20','options':'a20'})
        self.client.doctors['oak'] = recipient
        self.snapshot = dict(index_id=1, state=dict(csvFileId='ward', csvMessageId=5, doctorsFileId='registry',
            doctorsMessageId=10, doctorsUpdatedAt=100), doctors=copy.deepcopy(self.client.doctors), bundle=[shift()])
        self.client.baseline = copy.deepcopy(self.snapshot)
        self.client.fetch = Mock(side_effect=lambda:copy.deepcopy(self.snapshot))
        def authorize(doctors):
            actor = doctors.get(self.client._actor)
            require(actor is not None and actor.get('telegram_id') == 123, 'synthetic identity changed')
            return actor
        self.client.authorize = Mock(side_effect=authorize)
        self.uploaded = {}
        def upload(raw, *args):
            self.uploaded['doctors'] = parse_doctors(raw)
            return dict(message_id=30, date=1800000000, document=dict(file_id='transfer'))
        def write(index, state):
            self.snapshot.update(state=copy.deepcopy(state), doctors=copy.deepcopy(self.uploaded['doctors']))
        self.client.telegram = Mock()
        self.client.telegram.upload.side_effect = upload
        self.client.telegram.write_state.side_effect = write
        self.store = Workspace(self.directory.name, self.client)
        self.store.commit(baseline=copy.deepcopy(self.snapshot), draft=shift(), patientForm=dict(text='private draft'))
        self.service = Administration(self.client, self.store)

    def plan(self):
        result = self.service.review(self.service.fetch(), 'transfer', 'oak')
        self.service.approve(result)
        return result

    def test_review_changes_only_two_flags_and_has_no_remote_or_local_write(self):
        before = copy.deepcopy(self.store.state)
        plan = self.plan()
        expected = copy.deepcopy(self.snapshot['doctors'])
        expected['cedar']['options']='a10';expected['oak']['options']='A20'
        self.assertEqual(expected, plan['doctors'])
        self.assertEqual(before, self.store.state)
        self.client.telegram.upload.assert_not_called();self.client.telegram.write_state.assert_not_called()

    def test_success_preserves_ranks_clinical_draft_and_confirms_durable_receipt(self):
        before = copy.deepcopy(self.store.state)
        confirmed = self.service.publish(self.plan())
        self.assertFalse(permanent(confirmed['doctors']['cedar']))
        self.assertTrue(permanent(confirmed['doctors']['oak']))
        self.assertEqual((10,20), (rank(confirmed['doctors']['cedar']),rank(confirmed['doctors']['oak'])))
        reopened = Workspace(self.directory.name, self.client)
        for key in ('draft','patientForm'):self.assertEqual(before[key],reopened.state[key])
        self.assertEqual(before['baseline']['bundle'],reopened.state['baseline']['bundle'])
        self.assertEqual('SENT',reopened.state['receipts'][-1][2])
        self.assertEqual(confirmed['doctors'],reopened.state['baseline']['doctors'])
        self.client.telegram.upload.assert_called_once()

    def test_ordinary_admin_cannot_initiate_transfer(self):
        self.snapshot['doctors']['cedar']['options']='a10'
        with self.assertRaises(BridgeError):self.plan()
        self.client.telegram.upload.assert_not_called()

    def test_invalid_self_unlinked_nonadmin_and_existing_owner_recipients_rejected(self):
        original=copy.deepcopy(self.snapshot['doctors']['oak'])
        for changes in ({'telegram_id':None},{'telegram_id':123},{'#':'0','options':''},{'options':'A20'}):
            with self.subTest(changes=changes):
                self.snapshot['doctors']['oak']=dict(original,**changes)
                with self.assertRaises(BridgeError):self.plan()
        self.snapshot['doctors']['oak']=original
        with self.assertRaises(BridgeError):self.service.review(self.snapshot,'transfer','cedar')
        self.client.telegram.upload.assert_not_called()

    def test_changed_project_blocks_approved_review_before_upload(self):
        plan=self.plan();self.client.project_id='b'*64
        with self.assertRaises(BridgeError):self.service.publish(plan)
        self.client.telegram.upload.assert_not_called()

    def test_changed_registry_actor_identity_or_local_draft_blocks_review(self):
        plan=self.plan()
        self.snapshot['state']['doctorsMessageId']=11
        with self.assertRaises(BridgeError):self.service.publish(plan)
        self.snapshot['state']['doctorsMessageId']=10
        self.snapshot['doctors']['cedar']['telegram_id']=999
        with self.assertRaises(BridgeError):self.service.publish(plan)
        self.snapshot['doctors']['cedar']['telegram_id']=123
        self.store.commit(draft=dict(shift(),meta=dict(shift()['meta'],shiftDate='2026-01-02')))
        with self.assertRaises(BridgeError):self.service.publish(plan)
        self.client.telegram.upload.assert_not_called()

    def test_revoked_owner_during_upload_cannot_update_index(self):
        plan=self.plan();original=self.client.telegram.upload.side_effect
        def upload(*args):
            result=original(*args);self.snapshot['doctors']['cedar']['options']='a10'
            return result
        self.client.telegram.upload.side_effect=upload
        with self.assertRaises(BridgeError):self.service.publish(plan)
        self.client.telegram.write_state.assert_not_called()
        self.assertEqual('UNKNOWN',self.store.state['receipts'][-1][2])

    def test_project_changed_during_upload_cannot_update_index(self):
        plan=self.plan();original=self.client.telegram.upload.side_effect
        def upload(*args):
            result=original(*args);self.client.project_id='b'*64;return result
        self.client.telegram.upload.side_effect=upload
        with self.assertRaises(BridgeError):self.service.publish(plan)
        self.client.telegram.write_state.assert_not_called()

    def test_lost_index_acknowledgement_keeps_unknown_and_live_fetch_prevents_repetition(self):
        plan=self.plan();original=self.client.telegram.write_state.side_effect
        def lost(*args):
            original(*args);raise UncertainDelivery('synthetic lost acknowledgement')
        self.client.telegram.write_state.side_effect=lost
        with self.assertRaises(UncertainDelivery):self.service.publish(plan)
        self.assertTrue(permanent(self.service.fetch()['doctors']['oak']))
        self.assertEqual('UNKNOWN',Workspace(self.directory.name,self.client).state['receipts'][-1][2])
        with self.assertRaises(BridgeError):self.plan()
        self.client.telegram.upload.assert_called_once()

    def test_local_confirmation_failure_preserves_remote_handover_and_prevents_repetition(self):
        plan=self.plan();original=self.store.commit
        def commit(**changes):
            if 'baseline' in changes:raise OSError('synthetic disk full')
            return original(**changes)
        with patch.object(self.store,'commit',side_effect=commit):
            with self.assertRaisesRegex(BridgeError,'لا تكرر'):self.service.publish(plan)
        self.assertTrue(permanent(self.service.fetch()['doctors']['oak']))
        with self.assertRaises(BridgeError):self.plan()
        self.client.telegram.upload.assert_called_once()

    def test_import_cannot_grant_new_permanent_owner(self):
        incoming=copy.deepcopy(self.snapshot['doctors']['oak']);incoming['options']='A20'
        with self.assertRaises(BridgeError):self.service.review(self.snapshot,'import',values=[incoming])
        self.assertFalse(permanent(self.snapshot['doctors']['oak']))

    def test_import_preserves_existing_permanent_owner(self):
        incoming=copy.deepcopy(self.snapshot['doctors']['cedar']);incoming.update({'#':'0','options':''})
        result=self.service.review(self.snapshot,'import',values=[incoming])
        self.assertTrue(permanent(result['doctors']['cedar']))
        self.assertEqual(10,rank(result['doctors']['cedar']))
