"""Reverse checks run after pulling the synthetic artifacts produced by CrossClientInteropTest."""
import os
from pathlib import Path
import unittest
from desktop_crypto import Cipher, open_join
from desktop_backup import decode
from desktop_wire import parse_bundle
from tests.support import client


class AndroidInteropTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        location = os.environ.get('SHIFTREPORT_ANDROID_INTEROP')
        if not location:
            raise unittest.SkipTest('Set SHIFTREPORT_ANDROID_INTEROP to the pulled Android fixture directory')
        cls.directory = Path(location)

    def test_android_encrypted_file_decrypts_to_complete_clinical_bundle(self):
        plain = Cipher(client().config).decrypt((self.directory / 'android.srdata').read_bytes(), 'file')
        parsed = parse_bundle(plain)[0]
        self.assertEqual('شاي', parsed['patients'][0]['name'])
        self.assertEqual('نتيجة ☕', parsed['patients'][0]['labs'])

    def test_android_text_envelope_decodes(self):
        self.assertEqual('شاي — نتيجة تجريبية ☕', Cipher(client().config).decode_text(
            (self.directory / 'android-text.txt').read_text()))

    def test_android_backup_decodes_with_completion_attribution(self):
        import json
        parsed = decode((self.directory / 'android.rpb').read_bytes(), 'synthetic-password')
        tasks = json.loads(parsed['patients'][0]['tasksJson'])
        self.assertEqual('cedar', next(task for task in tasks if task['done'])['completedByDoctorId'])
        self.assertEqual('نتيجة ☕', parsed['patients'][0]['labs'])

    def test_android_protected_join_decodes_without_clinical_records(self):
        value = open_join(self.directory / 'android.srjoin.json', 'synthetic-password')
        self.assertEqual(-100, value['chatId'])
        self.assertEqual(client().config['telegramDataKey'], value['telegramDataKey'])
        self.assertNotIn('patients', value)


if __name__ == '__main__': unittest.main()
