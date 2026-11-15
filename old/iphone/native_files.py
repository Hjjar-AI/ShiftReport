"""Pyto file helpers; return alone is never evidence of review or external delivery."""

import hashlib
import os
import tempfile
from pathlib import Path


class NativePdfCopy:
    def __init__(self, raw, name):
        if Path(name).name != name or not name.endswith('.pdf'):
            raise ValueError('Invalid owned PDF name')
        self.directory = Path(tempfile.mkdtemp(prefix='ShiftReport-iPhone-PDF-'))
        self.path = self.directory / name
        try:
            descriptor = os.open(str(self.path), os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(descriptor, 'wb') as output:
                output.write(raw)
            if self.digest() != hashlib.sha256(raw).hexdigest():
                raise ValueError('Owned PDF differs from reviewed bytes')
        except BaseException:
            self.close()
            raise

    def digest(self):
        return hashlib.sha256(self.path.read_bytes()).hexdigest()

    def close(self):
        # Only these two exact owned paths; never an exported destination.
        self.path.unlink(missing_ok=True)
        self.directory.rmdir()


def perform(operations, key, operation):
    import file_system

    lease = None
    invoked = False
    try:
        with operation.workspace.lock:
            raw, name = operations.payload(operation.workspace, operation)
        lease = NativePdfCopy(raw, name)
        # Recheck identity/source/hash immediately before the native side effect.
        with operation.workspace.lock:
            current, _ = operations.payload(operation.workspace, operation)
            if hashlib.sha256(current).hexdigest() != lease.digest():
                raise ValueError('Artifact changed before native presentation')
        invoked = True
        if operation.mode == 'preview':
            file_system.quick_look(str(lease.path))
            operations.finish(key, 'review', lease)
        elif operation.mode == 'save':
            exported = Path(file_system.save_as(str(lease.path)))
            # The documented return is a separate copy; verify without disclosing its path.
            if exported.resolve() == lease.path.resolve() or hashlib.sha256(exported.read_bytes()).hexdigest() != lease.digest():
                operations.finish(key, 'uncertain', lease)
            else:
                try:
                    lease.close()
                except OSError:
                    operations.finish(key, 'saved', lease)
                else:
                    operations.finish(key, 'saved')
        else:
            file_system.share_files(str(lease.path))
            # No completion result is documented. Retain this immutable copy;
            # future callback-based cleanup must establish the consumer's lifetime.
            operations.finish(key, 'share_requested', lease)
    except file_system.FilePickerCancellation:
        operations.finish(key, 'cancelled' if operation.mode == 'save' else 'failed_open',
                          None if operation.mode == 'save' else lease)
        if lease is not None and operation.mode == 'save':
            lease.close()
    except Exception:
        # Native error text and Files paths never enter logs or browser status.
        operations.finish(key, 'failed_open' if invoked else 'failed', lease if invoked else None)
        if lease is not None and not invoked:
            lease.close()
