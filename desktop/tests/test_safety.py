"""Exercise production Python boundaries without GUI, real credentials, or Telegram."""
import base64
import copy
import json
import os
from pathlib import Path
import sqlite3
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from desktop_crypto import Cipher, open_join
from desktop_protocol_common import BridgeError, MAGIC, BUNDLE, fingerprint
from desktop_store import Workspace
from desktop_access import LocalAccess, normalize_pin
from desktop_backup import encode as backup_encode, decode as backup_decode
from desktop_wire import encode_bundle, parse_bundle
import desktop_tasks as tasks
from tests.support import KEY, client, shift, task


class CryptoTests(unittest.TestCase):
    def setUp(self):
        self.cipher = Cipher(client().config)

    def test_encrypted_file_round_trip_and_fresh_nonce(self):
        plain = 'شاي, "☕"\nنتيجة'.encode()
        first, second = self.cipher.encrypt(plain, 'file'), self.cipher.encrypt(plain, 'file')
        self.assertNotEqual(first, second)
        self.assertNotIn(plain, first)
        self.assertEqual(plain, self.cipher.decrypt(first, 'file'))

    def test_every_envelope_region_is_authenticated(self):
        raw = self.cipher.encrypt(b'synthetic clinical content', 'file')
        for index in (0, len(MAGIC), len(MAGIC) + 12, len(raw) - 1):
            with self.subTest(index=index):
                changed = bytearray(raw); changed[index] ^= 1
                with self.assertRaises(BridgeError): self.cipher.decrypt(bytes(changed), 'file')

    def test_purpose_confusion_and_wrong_key_rejected(self):
        raw = self.cipher.encrypt(b'data', 'file')
        with self.assertRaises(BridgeError): self.cipher.decrypt(raw, 'text')
        with self.assertRaises(BridgeError): Cipher(client(key=bytes(reversed(KEY))).config).decrypt(raw, 'file')

    def test_plaintext_downgrade_rejected_in_encrypted_project(self):
        with self.assertRaises(BridgeError): self.cipher.decrypt(b'plain', 'file')
        with self.assertRaises(BridgeError): self.cipher.decode_text('plain')

    def test_unencrypted_project_does_not_accept_encrypted_payload(self):
        plain = Cipher({})
        self.assertEqual(b'data', plain.encrypt(b'data', 'file'))
        with self.assertRaises(BridgeError): plain.decrypt(self.cipher.encrypt(b'data', 'file'), 'file')

    def test_text_envelope_round_trip_and_invalid_base64(self):
        text = 'مراجعة عربية\n☕' * 100
        self.assertEqual(text, self.cipher.decode_text(self.cipher.encode_text(text)))
        with self.assertRaises(BridgeError): self.cipher.decode_text('SRMSG1:!!!!')

    def test_truncation_never_yields_partial_plaintext(self):
        raw = self.cipher.encrypt(b'clinical content', 'file')
        for end in range(len(raw)):
            with self.subTest(end=end), self.assertRaises(BridgeError): self.cipher.decrypt(raw[:end], 'file')

    def test_join_round_trip_wrong_password_and_tampering(self):
        import hashlib
        config = dict(version=1, botToken='synthetic:fixture', chatId=-100,
                      encryptTelegram=True, telegramDataKey=base64.b64encode(KEY).decode())
        salt, nonce, password = bytes(range(16)), bytes(range(12)), 'synthetic-passphrase'
        key = hashlib.pbkdf2_hmac('sha256', password.encode(), salt, 210000, 32)
        raw = b'RUSHDJOIN1' + salt + nonce + AESGCM(key).encrypt(nonce, json.dumps(config).encode(), None)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'synthetic.srjoin.json'
            path.write_text(json.dumps(dict(format='ShiftReportJoin', encryptedPayload=base64.b64encode(raw).decode())))
            self.assertEqual(config, open_join(path, password))
            with self.assertRaises(BridgeError): open_join(path, 'wrong-password')
            path.write_text('{broken')
            with self.assertRaises(BridgeError): open_join(path, password)


class WireAndTaskTests(unittest.TestCase):
    def test_complete_clinical_bundle_round_trip(self):
        source = shift()
        decoded = parse_bundle(encode_bundle([source]))[0]
        self.assertEqual(source['meta'], decoded['meta'])
        for key, value in source['patients'][0].items():
            actual = decoded['patients'][0][key]
            self.assertEqual(json.loads(value), json.loads(actual)) if key == 'tasksJson' else self.assertEqual(value, actual)

    def test_duplicate_patient_and_shift_ids_rejected(self):
        source = shift(); source['patients'] *= 2
        with self.assertRaises(BridgeError): parse_bundle(encode_bundle([source]))
        source = shift()
        with self.assertRaises(BridgeError): parse_bundle(encode_bundle([source, source]))

    def test_incomplete_completion_rejects_entire_bundle(self):
        source = shift(); invalid = task(True); invalid['completedByDoctorId'] = None
        source['patients'][0]['tasksJson'] = json.dumps([invalid])
        with self.assertRaises(BridgeError): parse_bundle(encode_bundle([source]))

    def test_generated_text_preserves_quotes_unicode_and_newlines(self):
        for value in ['عربي', '☕', '"', ',', '\n', '\r\n', '\u200fنص', 'نتيجة,' * 1000]:
            source = shift()
            for field in ('initialDiagnosis', 'treatmentPlan', 'labs', 'followUp'): source['patients'][0][field] = value
            decoded = parse_bundle(encode_bundle([source]))[0]
            for field in ('initialDiagnosis', 'treatmentPlan', 'labs', 'followUp'): self.assertEqual(value, decoded['patients'][0][field])

    def test_truncated_bundle_and_invalid_utf8_rejected(self):
        for raw in (b'', b'\xff', BUNDLE.encode(), b'# wrong\n', encode_bundle([shift()])[:100]):
            with self.subTest(raw=raw[:20]), self.assertRaises(BridgeError): parse_bundle(raw)

    def test_task_counts_partition_across_deterministic_matrix(self):
        matrix = []
        for done in (False, True):
            for owner in (None, 'cedar'):
                for due in (None, 99, 100, 101):
                    for priority in ('LOW', 'NORMAL', 'HIGH'):
                        value = task(done, str(len(matrix))); value.update(ownerDoctorId=owner, dueAtEpochMillis=due,
                            dueShiftDate=None, priority=priority); matrix.append(value)
        tasks.validate(matrix)
        self.assertEqual((24, 0, 0), tasks.counts(matrix, 100))
        ordered = tasks.ordered_for_display(matrix, 100)
        self.assertEqual(ordered, tasks.ordered_for_display(ordered, 100))
        self.assertEqual({t['id'] for t in matrix}, {t['id'] for t in ordered})

    def test_rollover_drops_completed_and_clears_owners_without_mutating_source(self):
        value = task(); value.update(ownerDoctorId='cedar', ownerName='أرز')
        before = copy.deepcopy(value)
        carried = tasks.carry_forward([value, task(True, 'done')], True)
        self.assertEqual(1, len(carried)); self.assertIsNone(carried[0]['ownerDoctorId'])
        self.assertIsNone(carried[0]['ownerName']); self.assertEqual(before, value)
        self.assertEqual(carried, tasks.carry_forward(carried, True))

    def test_missing_attribution_components_and_duplicate_tasks_rejected(self):
        for field in tasks.COMPLETION:
            value = task(True); value[field] = None
            with self.subTest(field=field), self.assertRaises(BridgeError): tasks.validate([value])
        with self.assertRaises(BridgeError): tasks.validate([task(), task()])

    def test_invalid_task_types_and_identity_rejected_legacy_dates_ignored(self):
        for change in [dict(dueAtEpochMillis=True), dict(done='false'),
                       dict(priority='UNKNOWN'), dict(id=' '), dict(description=' ')]:
            value = task(); value.update(change)
            with self.subTest(change=change), self.assertRaises(BridgeError): tasks.validate([value])
        legacy = task(); legacy.update(dueAtEpochMillis=-1, dueShiftDate='2025-02-29')
        tasks.validate([legacy])
        self.assertEqual((1, 0, 0), tasks.counts([legacy], 100))


class WorkspaceTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.client = client()
        self.store = Workspace(self.directory.name, self.client)
        self.store.commit(draft=shift())

    def test_reopen_preserves_content_without_plaintext_on_disk(self):
        self.store.commit(patientForm={'note': 'synthetic-private-draft'})
        raw = self.store.path.read_bytes()
        self.assertNotIn('synthetic-private-draft'.encode(), raw)
        self.assertNotIn('شاي'.encode(), raw)
        reopened = Workspace(self.directory.name, client())
        self.assertEqual(self.store.state, reopened.state)

    def test_wrong_key_and_modified_ciphertext_rejected(self):
        with self.assertRaises(BridgeError): Workspace(self.directory.name, client(key=bytes(reversed(KEY))))
        raw = bytearray(self.store.encode(self.store.state)); raw[-1] ^= 1
        with self.assertRaises(BridgeError): self.store.decode(bytes(raw))

    def test_cross_project_envelope_cannot_be_rebound(self):
        other = Workspace(self.directory.name, client(project='b' * 64))
        with self.assertRaises(BridgeError): other.decode(self.store.encode(self.store.state))

    def test_failed_sql_write_keeps_memory_and_durable_state_unchanged(self):
        before = copy.deepcopy(self.store.state)
        with patch('desktop_store.sqlite3.connect', side_effect=sqlite3.OperationalError('synthetic disk full')):
            with self.assertRaises(sqlite3.OperationalError): self.store.commit(patientForm={'note': 'unsaved'})
        self.assertEqual(before, self.store.state)
        self.assertEqual(before, Workspace(self.directory.name, client()).state)

    def test_read_returns_independent_copy_and_lock_blocks_access(self):
        value = self.store.read(); value['patients'].clear()
        self.assertEqual(1, len(self.store.read()['patients']))
        self.store.locked = True
        with self.assertRaises(BridgeError): self.store.read()

    def test_pending_changes_block_download_without_mutation(self):
        before = copy.deepcopy(self.store.state)
        self.assertTrue(self.store.has_pending_changes())
        with self.assertRaises(BridgeError): self.store.write(self.client, shift(), downloaded=True)
        self.assertEqual(before, self.store.state)

    def test_unknown_receipt_survives_reopen_and_blocks_changed_content_same_destination(self):
        self.store.receipt_start('key', 'report', -100, 3, 'cedar', report_shift='synthetic-shift')
        reopened = Workspace(self.directory.name, client())
        with self.assertRaises(BridgeError): reopened.check_report_delivery('synthetic-shift', -100, 3)
        reopened.check_report_delivery('other-shift', -100, 3)
        reopened.check_report_delivery('synthetic-shift', -200, 3)

    def test_failed_receipt_confirmation_retains_durable_unknown(self):
        self.store.receipt_start('key', 'report', -100, 3, 'cedar', report_shift='synthetic-shift')
        with patch('desktop_store.sqlite3.connect', side_effect=sqlite3.OperationalError('synthetic failure')):
            with self.assertRaises(sqlite3.OperationalError): self.store.receipt_finish({'message_id': 12})
        self.assertEqual('UNKNOWN', Workspace(self.directory.name, client()).receipts()[0][1][2])

    def test_other_clinician_cannot_resolve_sender_receipt(self):
        self.store.receipt_start('key', 'report', -100, 3, 'cedar', report_shift='synthetic-shift')
        self.client.require_local_identity = lambda: dict(uid='other', **{'#': '0', 'options': ''})
        with self.assertRaises(BridgeError): self.store.resolve_unknown(0, True, self.client)
        self.assertEqual('UNKNOWN', self.store.receipts()[0][1][2])

    def test_explicit_not_sent_resolution_allows_retry_but_confirmed_receipt_blocks_it(self):
        self.store.receipt_start('key', 'report', -100, 3, 'cedar', report_shift='synthetic-shift')
        self.store.resolve_unknown(0, True, self.client)
        self.store.receipt_start('key', 'report', -100, 3, 'cedar', report_shift='synthetic-shift')
        self.store.receipt_finish({'message_id': 12})
        with self.assertRaises(BridgeError): self.store.receipt_start('key', 'report', -100, 3, 'cedar', report_shift='synthetic-shift')

    def test_backup_round_trip_tampering_and_wrong_password(self):
        raw = backup_encode(shift(), 'synthetic-password')
        value = backup_decode(raw, 'synthetic-password')
        self.assertEqual(shift()['meta'], value['meta'])
        self.assertEqual(shift()['patients'][0]['labs'], value['patients'][0]['labs'])
        for payload, password in [(raw, 'wrong-password'), (raw[:-1], 'synthetic-password'),
                                  (raw[:-1] + bytes([raw[-1] ^ 1]), 'synthetic-password')]:
            with self.assertRaises(BridgeError): backup_decode(payload, password)

    def test_pin_enrollment_is_hashed_and_unicode_digits_unlock(self):
        access = LocalAccess(self.store, self.client)
        access.enroll('١٢٣٤')
        record = access.records()['cedar']
        self.assertNotIn('1234', record.values()); self.assertNotIn('١٢٣٤', record.values())
        self.assertTrue(access.verify('cedar', '1234', now=100))

    def test_pin_lockout_persists_and_identity_replacement_invalidates_pin(self):
        access = LocalAccess(self.store, self.client); access.enroll('1234')
        for _ in range(5): self.assertFalse(access.verify('cedar', '9999', now=100))
        reopened = Workspace(self.directory.name, self.client)
        access = LocalAccess(reopened, self.client)
        with self.assertRaises(BridgeError): access.verify('cedar', '1234', now=101)
        self.assertTrue(access.verify('cedar', '1234', now=130))
        self.client.doctors['cedar']['telegram_id'] = 999
        with self.assertRaises(BridgeError): access.verify('cedar', '1234', now=200)

    def test_actual_process_exit_before_and_after_sql_commit(self):
        # This child owns only this test's temporary workspace. Never kills the application/owner process.
        script = '''
import os, sqlite3, sys
from desktop_store import Workspace
from tests.support import client
store=Workspace(sys.argv[1], client())
original=sqlite3.connect
class CrashConnection(sqlite3.Connection):
    def __exit__(self,*args):
        if sys.argv[2]=='before': os._exit(31)
        result=super().__exit__(*args)
        os._exit(32)
sqlite3.connect=lambda *a,**k: original(*a,**dict(k,factory=CrashConnection))
store.commit(patientForm={'note':'committed-after-crash'})
'''
        desktop = str(Path(__file__).resolve().parents[1])
        env = dict(os.environ, PYTHONPATH=desktop, PYTHONDONTWRITEBYTECODE='1')
        for mode, code in [('before', 31), ('after', 32)]:
            completed = subprocess.run([sys.executable, '-c', script, self.directory.name, mode],
                                       env=env, timeout=15, capture_output=True)
            self.assertEqual(code, completed.returncode, completed.stderr.decode())
            reopened = Workspace(self.directory.name, client())
            self.assertEqual(shift(), reopened.read())
            self.assertEqual(None if mode=='before' else {'note':'committed-after-crash'}, reopened.state.get('patientForm'))


if __name__ == '__main__': unittest.main()
