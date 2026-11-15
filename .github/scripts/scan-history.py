#!/usr/bin/env python3
"""Manual hosted production-history scan; raw findings never become public output."""

import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import sys
import tarfile
import tempfile
import urllib.request

ROOT = Path(__file__).resolve().parents[2]


def main():
    if (os.environ.get('GITHUB_ACTIONS') != 'true' or platform.system() != 'Linux'
            or platform.machine() != 'x86_64'):
        raise ValueError('Hosted Linux x86_64 required')
    if subprocess.run(['git', 'rev-parse', '--is-shallow-repository'], cwd=ROOT,
                      capture_output=True, text=True, check=True).stdout.strip() != 'false':
        raise ValueError('Full available Git history required')
    pin = json.loads((ROOT / '.github/ci-tools.json').read_text())['gitleaks']
    if not re_full_pin(pin):
        raise ValueError('Invalid scanner pin')
    with tempfile.TemporaryDirectory(prefix='shiftreport-history-', dir=os.environ['RUNNER_TEMP']) as temporary:
        work = Path(temporary)
        work.chmod(0o700)
        url = f'https://github.com/gitleaks/gitleaks/releases/download/v{pin["version"]}/gitleaks_{pin["version"]}_linux_x64.tar.gz'
        with urllib.request.urlopen(url, timeout=60) as response:
            archive = response.read(30 * 1024 * 1024 + 1)
        if len(archive) > 30 * 1024 * 1024 or hashlib.sha256(archive).hexdigest() != pin['sha256']:
            raise ValueError('Scanner checksum/size mismatch')
        path = work / 'scanner.tar.gz'
        path.write_bytes(archive)
        with tarfile.open(path) as bundle:
            entry = bundle.getmember('gitleaks')
            if not entry.isfile() or entry.size > 100 * 1024 * 1024:
                raise ValueError('Invalid scanner entry')
            with bundle.extractfile(entry) as source:
                executable = work / 'gitleaks'
                executable.write_bytes(source.read())
        executable.chmod(0o700)
        excluded = ('test', 'tests', 'androidTest', 'sharedTest', 'testFixtures', 'migrations', 'schemas')
        log_options = '--all -- android .github .githooks docs README.md AGENTS.md SECURITY.md .gitignore .gitattributes .editorconfig ' + ' '.join(f':(exclude,glob)**/{name}/**' for name in excluded)
        log_options += ' :(exclude,glob)docs/local/** :(exclude,glob)docs/guides/desktop/** :(exclude,glob)android/**/.signing/** :(exclude,glob)android/**/*.local.properties'
        report = work / 'findings.json'
        report.touch(mode=0o600)
        with (work / 'scanner.log').open('xb') as log:
            os.chmod(log.name, 0o600)
            result = subprocess.run([str(executable), 'git', str(ROOT), f'--log-opts={log_options}',
                                     '--redact=100', '--report-format=json', '--report-path', str(report),
                                     '--no-banner'], cwd=work, stdout=log, stderr=log, timeout=600)
        if result.returncode not in (0, 1) or not report.stat().st_size:
            raise ValueError('Incomplete history scan')
        findings = json.loads(report.read_text())
        if not isinstance(findings, list) or bool(findings) != (result.returncode == 1):
            raise ValueError('Invalid/inconsistent scanner report')
        text = (f'## Production history secrets\n\nFindings requiring private review: {len(findings)}. Values and locations withheld.\n'
                'Scope: locally fetched Git refs, excluding suites, migrations and schemas. Not a complete all-files/history guarantee.\n'
                'Raw report/logs are ephemeral and are not uploaded; rerun privately with explicit scope to investigate findings.\n')
        print(text)
        with Path(os.environ['GITHUB_STEP_SUMMARY']).open('a') as summary:
            summary.write(text)
        return 1 if findings else 0


def re_full_pin(pin):
    import re
    return (re.fullmatch(r'\d+\.\d+\.\d+', pin['version'])
            and re.fullmatch(r'[a-f0-9]{64}', pin['sha256']))


if __name__ == '__main__':
    try:
        sys.exit(main())
    except Exception:
        sys.exit('History scan failed/incomplete; private diagnostics withheld. No pass claimed.')
