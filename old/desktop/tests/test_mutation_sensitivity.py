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
            ('desktop_tasks.py', "return [task for task in tasks if not task.get('done')]", "return list(tasks)",
             'tests.test_safety.WireAndTaskTests.test_task_counts_partition_across_deterministic_matrix'),
            ('desktop_store.py', "require(not self.locked, 'الجلسة مقفلة')", "require(True, 'الجلسة مقفلة')",
             'tests.test_safety.WorkspaceTests.test_read_returns_independent_copy_and_lock_blocks_access'),
            ('desktop_crypto.py', "MAGIC + purpose.encode('ascii')", "MAGIC + b'file'",
             'tests.test_safety.CryptoTests.test_purpose_confusion_and_wrong_key_rejected'),
            ('desktop_publication.py', 'require(plan.actor_id == owner._actor,', 'require(True,',
             'tests.test_publication_lifecycle.PublicationLifecycleTests.test_actor_change_invalidates_approval'),
            ('desktop_publication.py', "doctor_pointer(current['state']) == plan.registry_pointer", 'True',
             'tests.test_publication_lifecycle.PublicationLifecycleTests.test_registry_change_blocks_even_explicit_force_local'),
            ('desktop_publication.py', 'before_upload()  # Persist', 'pass  # Persist',
             'tests.test_publication_lifecycle.PublicationLifecycleTests.test_failure_to_persist_unknown_prevents_transport'),
            ('desktop_workspace_receipts.py', "actor.get('uid') == rows[index][8] or is_admin(actor)", 'True',
             'tests.test_safety.WorkspaceTests.test_other_clinician_cannot_resolve_sender_receipt'),
        ]
        for filename, before, after, testcase in mutants:
            with self.subTest(mutant=filename, guard=before), tempfile.TemporaryDirectory() as directory:
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
