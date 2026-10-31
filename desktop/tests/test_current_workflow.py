"""Current continuous-ward behavior, using disposable encrypted workspaces."""
import copy
import json
import tempfile
import unittest
from types import SimpleNamespace
from unittest.mock import Mock, patch

from desktop_protocol_common import BridgeError, fingerprint
from desktop_store import Workspace
from desktop_reports import publication_content, readiness_warnings, prepare_delivery
from desktop_publication import plan, prepare
from desktop_model import doctor_label
from desktop_data_actions import download
from tests.support import client, shift


class CurrentWorkflowTests(unittest.TestCase):
    def setUp(self):
        self.directory=tempfile.TemporaryDirectory();self.addCleanup(self.directory.cleanup)
        self.client=client();self.store=Workspace(self.directory.name,self.client)
        self.original=shift()
        self.store.commit(draft=self.original,baseline=dict(bundle=[copy.deepcopy(self.original)],doctors=self.client.doctors))

    def test_legacy_bookkeeping_does_not_create_pending_work_or_mutate_stored_fields(self):
        draft=self.store.read();row=draft['patients'][0]
        row.update(revision='99',updatedAt='1',lastEditedBy='legacy',lastEditedByDoctorId='other')
        tasks=json.loads(row['tasksJson'])
        tasks[0].update(ownerDoctorId='legacy',ownerName='قديم',priority='LOW',dueShiftDate='2020-01-01',
                        dueAtEpochMillis=1,completedByName='قديم',completedByDoctorId='legacy',completedAtEpochMillis=2)
        row['tasksJson']=json.dumps(tasks)
        self.store.commit(draft=draft)
        self.assertFalse(self.store.has_pending_changes())
        self.assertEqual(draft,self.store.read())

    def test_clinical_assignment_task_and_roster_changes_each_remain_pending(self):
        for field,value in [('initialDiagnosis','new diagnosis'),('followUp','new follow-up'),
                            ('assignmentChangedOn','2026-10-26'),('responsibleSupervisorId','cedar')]:
            draft=copy.deepcopy(self.original);draft['patients'][0][field]=value
            with self.subTest(field=field):
                self.store.commit(draft=draft);self.assertTrue(self.store.has_pending_changes())
        for change in ('task text','done','add','remove','roster','sort'):
            draft=copy.deepcopy(self.original);tasks=json.loads(draft['patients'][0]['tasksJson'])
            if change=='task text':tasks[0]['description']='changed'
            elif change=='done':tasks[0]['done']=True
            elif change=='add':tasks.append(dict(id='new',description='new',done=False))
            elif change=='remove':tasks.pop()
            elif change=='roster':draft['meta']['doctors']=''
            else:draft['meta']['sortSpec']='{"levels":[{"field":"NAME","direction":"ASC"}]}'
            draft['patients'][0]['tasksJson']=json.dumps(tasks)
            with self.subTest(change=change):
                self.store.commit(draft=draft);self.assertTrue(self.store.has_pending_changes())

    def test_reorder_and_deletion_are_pending_but_edit_then_revert_is_clean(self):
        source=copy.deepcopy(self.original);second=copy.deepcopy(source['patients'][0]);second['externalId']='coffee'
        source['patients'].append(second)
        self.store.commit(draft=source,baseline=dict(bundle=[copy.deepcopy(source)]))
        changed=copy.deepcopy(source);changed['patients'].reverse();self.store.commit(draft=changed)
        self.assertTrue(self.store.has_pending_changes())
        self.store.commit(draft=source);self.assertFalse(self.store.has_pending_changes())
        changed['patients'].pop();self.store.commit(draft=changed);self.assertTrue(self.store.has_pending_changes())

    def test_empty_roster_free_ward_allows_fetch_despite_sort_without_baseline(self):
        draft=self.store.read();draft['patients']=[];draft['meta'].update(doctors='',sortSpec='{"levels":[{"field":"NAME","direction":"ASC"}]}')
        self.store.commit(draft=draft,baseline=None)
        self.assertFalse(self.store.has_pending_changes());self.store.require_downloadable()
        draft['meta']['doctors']='cedar';self.store.commit(draft=draft)
        self.assertTrue(self.store.has_pending_changes())

    def test_missing_baseline_with_patients_remains_pending(self):
        self.store.commit(baseline=None);self.assertTrue(self.store.has_pending_changes())

    def test_explicit_fetch_replacement_has_exact_undo_and_retains_receipts(self):
        local=self.store.read();local['patients'][0]['labs']='pending local';self.store.commit(draft=local)
        self.store.receipt_start('unresolved','report',-100,3,'cedar',report_shift=local['meta']['shiftId'])
        receipts=copy.deepcopy(self.store.state['receipts'])
        incoming=shift();incoming['meta'].update(shiftId='new-current',shiftDate='2026-10-25')
        self.client.baseline=dict(bundle=[incoming],doctors=self.client.doctors)
        self.store.write(self.client,incoming,downloaded=True,expected_replacement=fingerprint(local))
        self.assertEqual(incoming,self.store.read())
        self.store.undo_download(self.client,fingerprint(incoming))
        self.assertEqual(local,self.store.read());self.assertEqual(receipts,self.store.state['receipts'])
        with self.assertRaises(BridgeError):self.store.check_report_delivery(local['meta']['shiftId'],-100,3)

    def test_replacement_rejects_edit_after_approval_without_writing_backup_or_state(self):
        approved=fingerprint(self.store.read());changed=self.store.read();changed['patients'][0]['labs']='later'
        self.store.commit(draft=changed);before=copy.deepcopy(self.store.state)
        with self.assertRaises(BridgeError):self.store.write(self.client,shift(),downloaded=True,expected_replacement=approved)
        self.assertEqual(before,self.store.state)
        self.assertFalse(self.store.path.with_suffix('.previous.srdraft').exists())

    def test_readiness_counts_a_patient_once_and_includes_follow_up(self):
        row=copy.deepcopy(self.original['patients'][0]);row.update(responsibleSupervisorId='cedar',responsibleResidentId='resident')
        self.assertEqual([],readiness_warnings([row]))
        row['followUp']='  ';notes=readiness_warnings([row]);self.assertEqual(1,len(notes));self.assertTrue(notes[0].startswith('1 '))
        row.update(initialDiagnosis='',treatmentPlan='',responsibleResidentId='')
        self.assertEqual(1,len(readiness_warnings([row])));self.assertTrue(readiness_warnings([row])[0].startswith('1 '))

    def test_ordinary_doctor_names_are_short_but_duplicate_and_blank_names_are_distinct(self):
        doctors={'a':dict(name='أرز'),'b':dict(name='صنوبر')}
        self.assertEqual('أرز',doctor_label(doctors,'a'))
        doctors['b']['name']='أرز';self.assertNotEqual(doctor_label(doctors,'a'),doctor_label(doctors,'b'))
        doctors['a']['name']=doctors['b']['name']=''
        self.assertNotEqual(doctor_label(doctors,'a'),doctor_label(doctors,'b'))
        self.assertIn('طبيب بلا اسم',doctor_label(doctors,'a'))

    def test_continuous_ward_review_uses_newest_remote_without_date_gate_or_patient_id_collision(self):
        older=shift();newest=shift();newest['meta'].update(shiftId='remote-newest',shiftDate='2026-10-25',snapshotId='latest-snapshot')
        self.client.baseline=dict(bundle=[older])
        self.client.fetch=lambda:dict(state={},bundle=[newest],doctors=self.client.doctors)
        self.client.authorize=lambda doctors:doctors['cedar'];self.client.device='dummy';self.client._actor='cedar'
        result=plan(self.client,self.original)
        self.assertTrue(result.force_required)
        prepared=prepare(self.client,result)
        self.assertEqual('latest-snapshot',prepared.shift['meta']['baseSnapshotId'])
        self.assertEqual('2026-01-01',prepared.shift['meta']['shiftDate'])
        self.assertEqual('synthetic-shift',prepared.shift['meta']['shiftId'])

    def test_delivery_requires_publication_when_current_remote_ward_differs(self):
        older=shift();newest=shift();newest['meta'].update(shiftId='other',shiftDate='2026-10-25')
        prepared=SimpleNamespace(bundle=[newest],shift=older)
        self.client.plan=lambda local:prepared;self.client.prepare=lambda value:value
        _,reviewed,needed=prepare_delivery(self.client,older)
        self.assertTrue(needed);self.assertEqual(older,reviewed)

    def owner(self,choice):
        self.store.commit(baseline=None)
        return SimpleNamespace(ready=Mock(),store=self.store,choose=Mock(return_value=choice),plan='stale',
                               publish=Mock(),client=self.client,io=lambda action:action())

    def test_pending_fetch_publish_opens_fresh_review_without_self_download(self):
        owner=self.owner(0);self.client.fetch=Mock(side_effect=AssertionError('must not self-fetch'))
        download(owner);owner.publish.assert_called_once();self.assertIsNone(owner.plan)
        self.client.fetch.assert_not_called()

    def test_pending_fetch_cancel_preserves_every_local_value_without_network(self):
        for choice in (None,2):
            owner=self.owner(choice);before=copy.deepcopy(self.store.state);self.client.fetch=Mock()
            download(owner);self.client.fetch.assert_not_called();owner.publish.assert_not_called()
            self.assertEqual(before,self.store.state)


if __name__=='__main__':unittest.main()
