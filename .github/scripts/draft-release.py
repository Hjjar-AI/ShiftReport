"""Create the explicitly selected Release from this run's signed bundle; never overwrite."""

import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys


def main(kind):
    mode = os.environ['CI_PUBLICATION_MODE']
    if mode not in ('draft', 'prerelease', 'release'):
        raise ValueError('Explicit publication mode required')
    if kind not in ('debug', 'release') or os.environ.get('GITHUB_ACTIONS') != 'true':
        raise ValueError('Hosted draft operation required')
    if os.environ['GITHUB_REF'] != 'refs/heads/' + os.environ['CI_DEFAULT_BRANCH']:
        raise ValueError('Default branch required')
    root = Path(f'ci-output/signed-{kind}')
    receipt = json.loads((root / 'signing-info.json').read_text())
    if (receipt['kind'] != kind or receipt['sourceCommit'] != os.environ['GITHUB_SHA']
            or receipt['workflowRun'] != os.environ['GITHUB_RUN_ID']
            or receipt['workflowAttempt'] != os.environ['GITHUB_RUN_ATTEMPT']
            or receipt['package'] != 'com.hos.rushdpatients'):
        raise ValueError('Signed receipt provenance mismatch')
    version = receipt['versionName']
    if not re.fullmatch(r'\d+\.\d+\.\d+(?:\.\d{8})?', version) or type(receipt['versionCode']) is not int:
        raise ValueError('Invalid version')
    files = []
    for line in (root / 'SHA256SUMS.txt').read_text().splitlines():
        digest, name = line.split('  ', 1)
        if Path(name).name != name or not re.fullmatch(r'[a-f0-9]{64}', digest):
            raise ValueError('Invalid manifest path/hash')
        path = root / name
        if path.is_symlink() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            raise ValueError('Signed bundle changed')
        files.append(path)
    if set(path.name for path in files) != {receipt['apk'], 'signing-info.json'} | ({'mapping.txt'} if kind == 'release' else set()):
        raise ValueError('Unexpected signed asset set')
    tag = (f'android-v{version}-code-{receipt["versionCode"]}' if kind == 'release'
           else f'debug-{os.environ["GITHUB_RUN_ID"]}-{os.environ["GITHUB_RUN_ATTEMPT"]}')
    repository = os.environ['GITHUB_REPOSITORY']
    # List successfully before writing; a network/auth failure is never treated
    # as evidence that the intended draft/tag is absent.
    def pages(endpoint):
        response = subprocess.run(['gh', 'api', '--paginate', '--slurp', endpoint],
                                  capture_output=True, text=True, check=True)
        return [item for page in json.loads(response.stdout) for item in page]
    if any(item['tag_name'] == tag for item in pages(f'repos/{repository}/releases?per_page=100')):
        raise ValueError('Release/draft already exists; preserve it')
    if any(item['ref'] == 'refs/tags/' + tag for item in pages(f'repos/{repository}/git/matching-refs/tags/{tag}')):
        raise ValueError('Tag already exists; preserve it')
    notes = root / 'draft-notes.md'
    notes.write_text(f'ShiftReport {kind}: {version} (code {receipt["versionCode"]}).\n\n'
                     f'Source: {receipt["sourceCommit"]}\nCertificate SHA-256: {receipt["certificateSha256"]}\n\n'
                     'Review checksums, signer/update continuity and the intended ShiftReport installation update before publishing.\n'
                     'No device, accessibility or live API acceptance is implied.\n')
    if kind == 'debug':
        with notes.open('a') as stream:
            stream.write(f'\n<!-- shiftreport-debug-distribution:v1 run={os.environ["GITHUB_RUN_ID"]} '
                         f'attempt={os.environ["GITHUB_RUN_ATTEMPT"]} source={os.environ["GITHUB_SHA"]} -->\n')
    command = ['gh', 'release', 'create', tag, *map(str, files), str(root / 'SHA256SUMS.txt'),
               '--target', os.environ['GITHUB_SHA'], '--title', f'ShiftReport {version} ({kind}, code {receipt["versionCode"]})',
               '--notes-file', str(notes)]
    command.append('--latest' if kind == 'release' and mode == 'release' else '--latest=false')
    if mode == 'draft':
        command.append('--draft')
    if mode == 'prerelease' or (mode == 'draft' and kind == 'debug'):
        command.append('--prerelease')
    # Creation fails if already present; no edit/upload/clobber/delete path.
    subprocess.run(command, check=True)


if __name__ == '__main__':
    try:
        main(sys.argv[1])
    except Exception:
        sys.exit('Release creation incomplete; inspect GitHub before any retry. No automatic replay or overwrite.')
