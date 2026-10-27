"""Recovery/isolation boundary regressions; disposable encrypted workspaces, no network."""
import copy
import json
from pathlib import Path
import sqlite3
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

from desktop_backup import encode as backup_encode, decode as backup_decode
from desktop_export_review import ExportReview, save_published_pdfs
from desktop_protocol_common import BridgeError, fingerprint
from desktop_reports import report_text, text_chunks
from desktop_store import Workspace
from desktop_wire import encode_bundle, parse_bundle
from tests.support import client, shift


class WorkspaceBoundaryTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.client = client()
        self.store = Workspace(self.directory.name, self.client)
        self.original = shift()
        self.store.commit(draft=self.original, baseline=dict(bundle=[copy.deepcopy(self.original)], doctors=self.client.doctors))

    def reopen(self):
        return Workspace(self.directory.name, client())

    def test_retention_cutoff_is_inclusive_and_does_not_touch_other_recovery_state(self):
        now = 1800000000000
        cutoff = now - 7 * 24 * 60 * 60 * 1000
        entries = [dict(shiftId='synthetic-shift', patient=dict(externalId=name), deletedAtEpochMillis=when)
                   for name, when in [('older', cutoff-1), ('exact', cutoff), ('recent', cutoff+1)]]
        self.store.commit(recycle=entries, patientForm=dict(shiftId='synthetic-shift', text='draft'),
                          downloadUndo=dict(before=copy.deepcopy(self.original), after='captured'))
        self.store.receipt_start('uncertain', 'report', -100, 3, 'cedar', report_shift='synthetic-shift')
        before = copy.deepcopy(self.store.state)
        self.assertEqual(2, self.store.purge_recycle_bin(now))
        expected = copy.deepcopy(before); expected['recycle'] = entries[2:]
        self.assertEqual(expected, self.reopen().state)
        self.assertEqual(0, self.store.purge_recycle_bin(now))

    def test_failed_retention_commit_keeps_recycle_and_drafts_on_disk_and_can_retry(self):
        self.store.commit(recycle=[dict(patient=dict(externalId='expired'), deletedAtEpochMillis=1)],
                          patientForm=dict(text='retained'))
        before = copy.deepcopy(self.store.state)
        with patch('desktop_store.sqlite3.connect', side_effect=sqlite3.OperationalError('synthetic full disk')):
            with self.assertRaises(sqlite3.OperationalError): self.store.purge_recycle_bin(1800000000000)
        self.assertEqual(before, self.store.state); self.assertEqual(before, self.reopen().state)
        self.assertEqual(1, self.store.purge_recycle_bin(1800000000000))

    def test_legacy_undated_recycle_entries_get_a_full_grace_week_once(self):
        now = 1800000000000
        self.store.commit(recycle=[dict(patient=dict(externalId='legacy'))])
        self.assertEqual(0, self.store.purge_recycle_bin(now))
        self.assertEqual(now, self.reopen().state['recycle'][0]['deletedAtEpochMillis'])
        self.assertEqual(0, self.store.purge_recycle_bin(now + 7*24*60*60*1000 - 1))
        self.assertEqual(1, self.store.purge_recycle_bin(now + 7*24*60*60*1000))

    def test_restore_before_sweep_survives_and_restore_after_sweep_cannot_resurrect(self):
        with patch('time.time', return_value=1000):
            self.store.delete_patient('tea', fingerprint(self.store.read()))
        self.store.restore_patient('tea', fingerprint(self.store.read()))
        self.store.purge_recycle_bin(1800000000000)
        self.assertEqual(self.original, self.store.read())
        with patch('time.time', return_value=1000):
            self.store.delete_patient('tea', fingerprint(self.store.read()))
        self.store.purge_recycle_bin(1800000000000)
        before = copy.deepcopy(self.store.state)
        with self.assertRaises(BridgeError): self.store.restore_patient('tea', fingerprint(self.store.read()))
        self.assertEqual(before, self.reopen().state)

    def test_fetch_undo_rejects_later_task_assignment_and_order_edits_without_consuming_ticket(self):
        incoming = shift(); second = copy.deepcopy(incoming['patients'][0]); second['externalId'] = 'coffee'
        incoming['patients'].append(second)
        self.client.baseline = dict(bundle=[incoming], doctors=self.client.doctors)
        self.store.write(self.client, incoming, downloaded=True)
        ticket = copy.deepcopy(self.store.state['downloadUndo'])
        for change in ('task', 'assignment', 'order'):
            changed = copy.deepcopy(incoming)
            if change == 'task':
                tasks = json.loads(changed['patients'][0]['tasksJson']); tasks[0]['done'] = True
                changed['patients'][0]['tasksJson'] = json.dumps(tasks)
            elif change == 'assignment': changed['patients'][0]['responsibleSupervisorId'] = 'cedar'
            else: changed['patients'].reverse()
            with self.subTest(change=change):
                self.store.commit(draft=changed); before = copy.deepcopy(self.store.state)
                with self.assertRaises(BridgeError): self.store.undo_download(self.client, fingerprint(incoming))
                self.assertEqual(before, self.reopen().state)
                self.assertEqual(ticket, self.store.state['downloadUndo'])

    def test_authenticated_wrong_project_import_preserves_all_local_state_and_creates_no_backup(self):
        other = Workspace(self.directory.name, client(project='b'*64))  # Deliberately the same encryption key.
        other.commit(draft=shift(), baseline=dict(bundle=[shift()], doctors=self.client.doctors))
        foreign = Path(self.directory.name) / 'foreign.srdraft'; other.export(foreign)
        self.store.commit(patientForm=dict(text='local unfinished input'))
        self.store.receipt_start('unknown', 'report', -100, 3, 'cedar', report_shift='synthetic-shift')
        before = copy.deepcopy(self.store.state)
        with self.assertRaises(BridgeError): self.store.import_draft(foreign, self.client)
        self.assertEqual(before, self.reopen().state)
        self.assertFalse(self.store.path.with_suffix('.previous.srdraft').exists())

    def test_two_projects_keep_drafts_receipts_and_export_settings_separate_after_reopen(self):
        other_client = client(project='b'*64)
        other = Workspace(self.directory.name, other_client); other.commit(draft=shift())
        self.store.commit(patientForm=dict(text='tea draft'), reportPreferences=dict(paper='A5'))
        self.store.receipt_start('unknown', 'report', -100, 3, 'cedar', report_shift='synthetic-shift')
        other.commit(patientForm=dict(text='coffee draft'), reportPreferences=dict(paper='A4'))
        for expected, store in [('tea draft', self.reopen()), ('coffee draft', Workspace(self.directory.name, client(project='b'*64)))]:
            self.assertEqual(expected, store.patient_form_draft()['text'])
        self.assertEqual([], other.receipts()); self.assertEqual(1, len(self.reopen().receipts()))
        self.assertEqual(dict(paper='A4'), other.report_preferences())
        self.assertEqual(dict(paper='A5'), self.reopen().report_preferences())
        with self.assertRaises(BridgeError): self.store.undo_download(other_client, fingerprint(self.store.read()))

    def test_unicode_survives_csv_backup_encrypted_reopen_and_text_report_without_changing_task_ids(self):
        text = 'شَاي 123 Na=140 💊 ☕ "quote", [x] \\ path\nمتابعة\nآخر سطر'
        source = self.store.read(); row = source['patients'][0]
        for field in ('initialDiagnosis', 'treatmentPlan', 'followUp', 'labs'): row[field] = text
        tasks = json.loads(row['tasksJson']); tasks[0]['description'] = text; row['tasksJson'] = json.dumps(tasks, ensure_ascii=False)
        row['assignmentChangedOn'] = '2026-10-27'
        decoded = parse_bundle(encode_bundle([source]))[0]
        restored = backup_decode(backup_encode(decoded, 'synthetic-password'), 'synthetic-password')
        self.store.commit(draft=restored)
        reopened = self.reopen().read(); actual = reopened['patients'][0]
        for field in ('initialDiagnosis', 'treatmentPlan', 'followUp', 'labs'): self.assertEqual(text, actual[field])
        self.assertEqual(tasks, json.loads(actual['tasksJson']))
        self.assertEqual('2026-10-27', actual['assignmentChangedOn'])
        report = report_text(self.client, reopened, report_day='2026-10-27')
        self.assertIn(text, report)
        self.assertEqual(report, ''.join(text_chunks(report, 37)))
        self.assertTrue(all(len(part.encode('utf-16-le'))//2 <= 37 for part in text_chunks(report, 37)))

    def test_assignment_changes_and_unchanged_clinical_saves_use_actual_day_and_survive_backup(self):
        self.client._actor = 'cedar'
        self.client.doctors['oak'] = dict(self.client.doctors['cedar'], uid='oak', name='بلوط')
        with patch('desktop_clinical.current_date', return_value='2026-10-27'):
            for supervisor in ('cedar', 'oak', 'cedar', '', 'cedar'):
                candidate = self.store.read(); candidate['patients'][0]['responsibleSupervisorId'] = supervisor
                self.store.save_clinical_draft(self.client, candidate, fingerprint(self.store.read()))
                self.assertEqual('2026-10-27', self.store.read()['patients'][0]['assignmentChangedOn'])
        with patch('desktop_clinical.current_date', return_value='2026-10-28'):
            candidate = self.store.read(); candidate['patients'][0].update(labs='later result', assignmentChangedOn='1999-01-01')
            self.store.save_clinical_draft(self.client, candidate, fingerprint(self.store.read()))
        final = self.reopen().read()
        self.assertEqual('2026-10-27', final['patients'][0]['assignmentChangedOn'])
        self.assertEqual('2026-10-27', backup_decode(backup_encode(final, 'synthetic-password'), 'synthetic-password')['patients'][0]['assignmentChangedOn'])


class LocalExportBoundaryTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(); self.addCleanup(self.directory.cleanup)
        self.client = client(); self.client._actor = 'cedar'
        self.local = shift()
        self.owner = SimpleNamespace(client=self.client, store=SimpleNamespace(read=lambda:copy.deepcopy(self.local)),
            ready=Mock(), io=lambda action:action(), status=Mock(), demo=False, selected_supervisor=lambda:None)
        self.review = ExportReview(self.owner, dict(format='text'))
        self.review.preview(True, None)
        for item in self.review.prepared['files']: item['opened'] = True

    def test_cancelled_chooser_keeps_exact_review_and_writes_nothing(self):
        before = copy.deepcopy(self.review.prepared)
        with patch('desktop_export_review.filedialog.asksaveasfilename', return_value=''):
            self.review.save(True)
        self.assertEqual(before, self.review.prepared)
        self.assertEqual([], list(Path(self.directory.name).iterdir())); self.owner.status.set.assert_not_called()

    def test_permission_denied_save_keeps_review_and_allows_local_retry(self):
        target = Path(self.directory.name)/'draft.txt'
        raw = self.review.prepared['files'][0]['raw']
        with patch('desktop_export_review.filedialog.asksaveasfilename', return_value=str(target)):
            with patch.object(Path, 'open', side_effect=PermissionError('synthetic denied')):
                with self.assertRaises(PermissionError): self.review.save(True)
            self.owner.status.set.assert_not_called()
            self.review.save(True)
        self.assertEqual(raw, target.read_bytes())

    def test_actor_project_and_lock_changes_during_chooser_prevent_write(self):
        target = Path(self.directory.name)/'draft.txt'
        for change in ('actor', 'project', 'lock'):
            with self.subTest(change=change):
                self.client._actor='cedar'; self.client.project_id='a'*64; self.owner.ready.side_effect=None
                self.review.preview(True,None)
                self.review.prepared['files'][0]['opened']=True
                def choose(**kwargs):
                    if change=='actor': self.client._actor='oak'
                    elif change=='project': self.client.project_id='b'*64
                    else: self.owner.ready.side_effect=BridgeError('synthetic locked')
                    return str(target)
                with patch('desktop_export_review.filedialog.asksaveasfilename', side_effect=choose):
                    with self.assertRaises(BridgeError): self.review.save(True)
                self.assertFalse(target.exists())

    def test_optional_confirmed_copy_cancellation_and_failure_keep_confirmed_outcome_and_exact_bytes(self):
        self.owner.report_attempt=SimpleNamespace(publication='CONFIRMED', delivery='CONFIRMED')
        delivery=SimpleNamespace(report_day='2026-10-27')
        target=Path(self.directory.name)/'confirmed.pdf'
        payload=[(None,'pdf',b'%PDF exact reviewed bytes')]
        with patch('desktop_export_review.filedialog.asksaveasfilename', return_value=''):
            save_published_pdfs(self.owner,delivery,payload)
        self.assertFalse(target.exists())
        with patch('desktop_export_review.filedialog.asksaveasfilename',return_value=str(target)), \
             patch.object(Path,'open',side_effect=PermissionError('synthetic denied')):
            with self.assertRaises(PermissionError):save_published_pdfs(self.owner,delivery,payload)
        self.assertEqual('CONFIRMED',self.owner.report_attempt.delivery)
        with patch('desktop_export_review.filedialog.asksaveasfilename',return_value=str(target)):
            save_published_pdfs(self.owner,delivery,payload)
        self.assertEqual(payload[0][2],target.read_bytes())


if __name__ == '__main__': unittest.main()
