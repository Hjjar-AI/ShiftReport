#!/usr/bin/env python3
"""Sign this run's APK in an isolated hosted job; never print private diagnostics."""

import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]


def command(arguments, environment=None):
    result = subprocess.run(arguments, cwd=ROOT, env=environment, capture_output=True,
                            text=True, timeout=180, check=False)
    if result.returncode:
        raise ValueError('Signing/verification command failed')
    return result.stdout


def main(kind):
    if kind not in ('debug', 'demo', 'release') or os.environ.get('GITHUB_ACTIONS') != 'true':
        raise ValueError('Explicit hosted signing operation required')
    if os.environ['GITHUB_REF'] != 'refs/heads/' + os.environ['CI_DEFAULT_BRANCH']:
        raise ValueError('Default branch required')
    required = ('KEYSTORE_BASE64', 'KEY_ALIAS', 'STORE_PASSWORD', 'KEY_PASSWORD', 'CERT_SHA256')
    if any(not os.environ.get('SHIFTREPORT_SIGN_' + name) for name in required):
        raise ValueError('Missing signing configuration')
    expected = os.environ['SHIFTREPORT_SIGN_CERT_SHA256'].replace(':', '').strip().lower()
    if not re.fullmatch(r'[a-f0-9]{64}', expected):
        raise ValueError('Invalid public certificate fingerprint')
    source = ROOT / ('ci-output/signing-input-demo' if kind == 'demo' else 'ci-output/signing-input')
    packages = list(source.glob('*.apk'))
    if len(packages) != 1 or packages[0].is_symlink():
        raise ValueError('Expected exactly one APK from this run')
    package = packages[0]
    # Bind this job's input bytes to the build job checksum, then verify against
    # source version properties and packaged identity rather than trusting filenames.
    sums = (source / 'SHA256SUMS.txt').read_text().splitlines()
    digest = hashlib.sha256(package.read_bytes()).hexdigest()
    if sums != [f'{digest}  {package.name}']:
        raise ValueError('Build artifact checksum mismatch')
    version = dict(re.findall(r'^([^#\s=]+)=(.*)$', (ROOT / 'android/gradle/app-version.properties').read_text(), re.M))
    code, name = version['versionCode'], version['versionName']
    packaged_name = name + ('-debug' if kind == 'debug' else '')
    application_id = 'com.hos.rushdpatients.demo' if kind == 'demo' else 'com.hos.rushdpatients'
    if not code.isdecimal() or not re.fullmatch(r'\d+\.\d+\.\d+(?:\.\d{8})?', name):
        raise ValueError('Invalid stored version')
    tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/34.0.0'
    public_environment = {key: value for key, value in os.environ.items() if not key.startswith('SHIFTREPORT_SIGN_')}

    def inspect(apk):
        badging = command([str(tools / 'aapt'), 'dump', 'badging', str(apk)], public_environment)
        if not badging.startswith(f"package: name='{application_id}' "):
            raise ValueError('Unexpected package')
        if (f"versionCode='{code}'" not in badging or f"versionName='{packaged_name}'" not in badging
                or "sdkVersion:'23'" not in badging or "targetSdkVersion:'34'" not in badging):
            raise ValueError('Packaged version/SDK differs from source')
        if ('application-debuggable' in badging) != (kind == 'debug'):
            raise ValueError('Unexpected build type')
        if kind == 'demo' and (
                "launchable-activity: name='com.hos.rushdpatients.DemoActivity'" not in badging
                or re.search(r"^uses-permission(?:-sdk-\d+)?: name='android\.permission\.(?:INTERNET|ACCESS_NETWORK_STATE)'", badging, re.M)):
            raise ValueError('Unexpected demo launcher or network permission')
        command([str(tools / 'zipalign'), '-c', '4', str(apk)], public_environment)

    inspect(package)
    destination = ROOT / f'ci-output/signed-{kind}'
    if destination.exists():
        raise ValueError('Preserve existing signed output; do not overwrite')
    with tempfile.TemporaryDirectory(prefix='shiftreport-sign-', dir=os.environ['RUNNER_TEMP']) as temporary:
        work = Path(temporary)
        work.chmod(0o700)
        key = work / 'signing.keystore'
        with key.open('xb') as stream:
            os.chmod(key, 0o600)
            stream.write(base64.b64decode(''.join(os.environ['SHIFTREPORT_SIGN_KEYSTORE_BASE64'].split()), validate=True))
        signed = work / f'ShiftReport-{name}-code-{code}-{kind}.apk'
        command([str(tools / 'apksigner'), 'sign', '--ks', str(key),
                 '--ks-key-alias', os.environ['SHIFTREPORT_SIGN_KEY_ALIAS'],
                 '--ks-pass', 'env:SHIFTREPORT_SIGN_STORE_PASSWORD',
                 '--key-pass', 'env:SHIFTREPORT_SIGN_KEY_PASSWORD',
                 '--v4-signing-enabled', 'false', '--out', str(signed), str(package)])
        inspection = command([str(tools / 'apksigner'), 'verify', '--print-certs', str(signed)], public_environment)
        fingerprints = [value.lower() for value in re.findall(r'certificate SHA-256 digest: ([a-fA-F0-9]+)', inspection)]
        if fingerprints != [expected]:
            raise ValueError('Expected exactly the configured certificate')
        inspect(signed)
        destination.mkdir()
        shutil.copyfile(signed, destination / signed.name)
    if kind == 'release':
        mapping = source / 'mapping/mapping.txt'
        if not mapping.is_file() or mapping.is_symlink():
            raise ValueError('Missing release shrinker mapping')
        shutil.copyfile(mapping, destination / 'mapping.txt')
    receipt = dict(kind=kind, package=application_id, versionCode=int(code), versionName=name,
                   certificateSha256=expected, apk=signed.name, sourceCommit=os.environ['GITHUB_SHA'],
                   workflowRun=os.environ['GITHUB_RUN_ID'], workflowAttempt=os.environ['GITHUB_RUN_ATTEMPT'])
    (destination / 'signing-info.json').write_text(json.dumps(receipt, indent=2) + '\n')
    public_files = sorted(destination.iterdir())
    (destination / 'SHA256SUMS.txt').write_text(''.join(
        f'{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}\n' for path in public_files))
    with Path(os.environ['GITHUB_STEP_SUMMARY']).open('a') as stream:
        stream.write(f'## Signed {kind} APK\n\nPackage/version/SDK/alignment/signature checks passed for `{name}` (code {code}).\n'
                     f'Certificate SHA-256: `{expected}`. No device installation/update acceptance is implied.\n')


if __name__ == '__main__':
    try:
        main(sys.argv[1])
    except Exception:
        sys.exit('Signing incomplete. Check environment secrets, expected certificate and build artifact. Private diagnostics withheld.')
