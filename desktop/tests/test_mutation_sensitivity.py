"""Small targeted mutation check: only temporary source copies are modified."""
from pathlib import Path
import os
import subprocess
import sys
import tempfile
import unittest


class MutationSensitivityTests(unittest.TestCase):
    def test_selected_safety_assertions_detect_broken_production_guards(self):
        desktop = Path(__file__).resolve().parents[1]
        mutants = [
            ('desktop_tasks.py', 'due <= now', 'due < now',
             'tests.test_safety.WireAndTaskTests.test_task_counts_partition_across_deterministic_matrix'),
            ('desktop_store.py', "require(not self.locked, 'الجلسة مقفلة')", "require(True, 'الجلسة مقفلة')",
             'tests.test_safety.WorkspaceTests.test_read_returns_independent_copy_and_lock_blocks_access'),
            ('desktop_crypto.py', "MAGIC + purpose.encode('ascii')", "MAGIC + b'file'",
             'tests.test_safety.CryptoTests.test_purpose_confusion_and_wrong_key_rejected'),
        ]
        for filename, before, after, testcase in mutants:
            with self.subTest(mutant=filename), tempfile.TemporaryDirectory() as directory:
                source = (desktop / filename).read_text()
                self.assertIn(before, source)
                (Path(directory) / filename).write_text(source.replace(before, after))
                environment = dict(os.environ, PYTHONPATH=os.pathsep.join([directory, str(desktop)]),
                                   PYTHONDONTWRITEBYTECODE='1')
                result = subprocess.run([sys.executable, '-m', 'unittest', testcase], cwd=directory,
                                        env=environment, capture_output=True, timeout=30)
                output = result.stderr.decode()
                self.assertNotEqual(0, result.returncode, 'Mutated safeguard escaped detection')
                self.assertIn('AssertionError', output, output)
                self.assertNotIn('ModuleNotFoundError', output, output)


if __name__ == '__main__': unittest.main()
