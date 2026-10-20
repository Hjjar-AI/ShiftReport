#!/usr/bin/env python3
"""Reserve a unique patch/version-code pair before a POSIX packaging invocation."""
import fcntl
import os
from pathlib import Path
import re
import sys
import tempfile
import time


def build_date(timestamp=None):
    """Build-start calendar date in the project's Asia/Damascus timezone."""
    previous_timezone = os.environ.get('TZ')
    try:
        os.environ['TZ'] = 'Asia/Damascus'
        time.tzset()
        instant = time.time() if timestamp is None else timestamp
        return time.strftime('%Y%m%d', time.localtime(instant))
    finally:
        if previous_timezone is None:
            os.environ.pop('TZ', None)
        else:
            os.environ['TZ'] = previous_timezone
        time.tzset()


def reserve(path):
    path = Path(path).resolve()
    # Lock the stable parent directory, not a file that atomic replacement changes.
    # A read-only directory descriptor also works across accounts sharing the source.
    directory = os.open(str(path.parent), os.O_RDONLY)
    try:
        fcntl.flock(directory, fcntl.LOCK_EX)
        values = {}
        for line in path.read_text(encoding='utf-8').splitlines():
            if line.strip() and not line.lstrip().startswith('#'):
                key, value = line.split('=', 1)
                values[key.strip()] = value.strip()
        code = int(values['versionCode']) + 1
        match = re.fullmatch(r'(\d+)\.(\d+)\.(\d+)(?:\.\d{8})?', values['versionName'])
        if not match or not 1 < code <= 2_100_000_000:
            raise ValueError('Invalid app version counter; repair it explicitly rather than resetting it.')
        major, minor, patch = match.groups()
        name = '{}.{}.{}.{}'.format(major, minor, int(patch) + 1, build_date())
        descriptor, temporary = tempfile.mkstemp(prefix='.app-version-', dir=str(path.parent))
        try:
            with os.fdopen(descriptor, 'w', encoding='utf-8') as output:
                # Normal source-file permissions; replacement uses parent-directory rights.
                os.fchmod(output.fileno(), 0o644)
                output.write('# Last reserved app version. Keep this file when moving between PCs.\n')
                output.write('versionCode={}\nversionName={}\n'.format(code, name))
                output.flush()
                os.fsync(output.fileno())
            os.replace(temporary, str(path))
            os.fsync(directory)
        finally:
            if os.path.exists(temporary):
                os.unlink(temporary)
        return code, name
    finally:
        os.close(directory)


if __name__ == '__main__':
    try:
        if len(sys.argv) != 2:
            raise ValueError('Expected the project app-version.properties path.')
        version_code, version_name = reserve(sys.argv[1])
        print('{} {}'.format(version_code, version_name))
    except (OSError, ValueError, KeyError) as error:
        raise SystemExit('Could not reserve app version: {}'.format(error))
