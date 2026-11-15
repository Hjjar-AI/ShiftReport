"""Run separately selected visual capture/offline instrumentation on an owned AVD."""

import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / 'ci-output/instrumentation'


def load_capture():
    spec = importlib.util.spec_from_file_location('capture', ROOT / '.github/scripts/capture-emulator.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def instrument(capture):
    capture.owned_device()
    OUTPUT.mkdir(parents=True, exist_ok=True)
    selection = json.loads((ROOT / '.github/instrumentation-selection.json').read_text())
    allowed = {
        'com.hos.rushdpatients.config.ProjectDataCipherTest',
        'com.hos.rushdpatients.config.ProjectProvisioningManagerTest',
        'com.hos.rushdpatients.domain.auth.PasswordHasherTest',
        'com.hos.rushdpatients.ui.demo.DemoWorkspaceInteractionTest',
        'com.hos.rushdpatients.ui.demo.DemoWorkflowDeviceTest',
        'com.hos.rushdpatients.util.Pbkdf2CompatibilityTest',
        'com.hos.rushdpatients.ui.report.ReportSelectionInteractionTest',
    }
    if not isinstance(selection, dict) or set(selection) != allowed:
        raise ValueError('Only the reviewed offline demo/crypto/report-selection classes are selectable')
    if any(not isinstance(methods, list) or not methods or len(set(methods)) != len(methods)
           or any(not isinstance(method, str) or not re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]*', method)
                  for method in methods) for methods in selection.values()):
        raise ValueError('A nonempty explicit method inventory is required')
    expected = {(cls, method) for cls, methods in selection.items() for method in methods}
    tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/34.0.0'
    analyzer = Path(os.environ['ANDROID_HOME']) / 'cmdline-tools/latest/bin/apkanalyzer'
    installed = {}
    report = dict(source=os.environ['GITHUB_SHA'], run=os.environ['GITHUB_RUN_ID'],
                  attempt=os.environ['GITHUB_RUN_ATTEMPT'], api=os.environ['CI_EXPECTED_API'],
                  expected=len(expected), results=[], cleanupErrors=[])
    started = None
    try:
        application = list((ROOT / 'ci-output/debug-apk').glob('*.apk'))
        tests = list((ROOT / 'ci-output/instrumentation-apk').glob('*.apk'))
        if len(application) != 1 or len(tests) != 1:
            raise ValueError('One fresh application and instrumentation APK required')
        manifest = subprocess.run([str(analyzer), 'manifest', 'print', str(tests[0])],
                                  capture_output=True, text=True, check=True, timeout=60).stdout
        tree = ET.fromstring(manifest)
        ns = '{http://schemas.android.com/apk/res/android}'
        runner = tree.find('instrumentation')
        test_package = tree.get('package')
        if (test_package != capture.PACKAGE + '.test' or runner is None
                or runner.get(ns + 'targetPackage') != capture.PACKAGE
                or runner.get(ns + 'name') != 'androidx.test.runner.AndroidJUnitRunner'):
            raise ValueError('Unexpected instrumentation identity/target/runner')
        certificates = []
        report['artifacts'] = []
        for apk, package in ((application[0], capture.PACKAGE), (tests[0], test_package)):
            if apk.is_symlink():
                raise ValueError('APK symlink refused')
            manifest_sum = (apk.parent / 'SHA256SUMS.txt').read_text().splitlines()
            digest = hashlib.sha256(apk.read_bytes()).hexdigest()
            if manifest_sum != [f'{digest}  {apk.name}']:
                raise ValueError('Build checksum mismatch')
            badging = subprocess.run([str(tools / 'aapt'), 'dump', 'badging', str(apk)],
                                     capture_output=True, text=True, check=True, timeout=30).stdout
            if not badging.startswith(f"package: name='{package}' "):
                raise ValueError('Unexpected APK identity')
            inspection = subprocess.run([str(tools / 'apksigner'), 'verify', '--print-certs', str(apk)],
                                        capture_output=True, text=True, check=True, timeout=30).stdout
            fingerprints = re.findall(r'certificate SHA-256 digest: ([a-fA-F0-9]+)', inspection)
            if len(fingerprints) != 1:
                raise ValueError('Ambiguous APK signer')
            certificates.append(fingerprints)
            report['artifacts'].append(dict(package=package, sha256=digest, certificate=fingerprints[0]))
        if certificates[0] != certificates[1]:
            raise ValueError('App/instrumentation signer mismatch')
        for apk, package in ((application[0], capture.PACKAGE), (tests[0], test_package)):
            probe = capture.adb('shell', 'pm', 'path', package, check=False)
            if probe.returncode not in (0, 1) or probe.stdout.strip() or probe.stderr.strip():
                raise ValueError('Existing package or ambiguous probe; refusing replacement')
            started = package
            capture.adb('install', '-t', str(apk), timeout=90)
            installed[package] = capture.identity(package)
            path = capture.adb('shell', 'pm', 'path', package).stdout.decode().strip()
            if not path.startswith('package:/data/app/') or '\n' in path:
                raise ValueError('Unexpected installed APK path')
            copy = Path(os.environ['RUNNER_TEMP']) / (package + '.installed.apk')
            capture.adb('pull', path.removeprefix('package:'), str(copy))
            if hashlib.sha256(copy.read_bytes()).digest() != hashlib.sha256(apk.read_bytes()).digest():
                raise ValueError('Installed APK bytes differ')
            started = None
        capture.adb('shell', 'svc', 'wifi', 'disable')
        capture.adb('shell', 'svc', 'data', 'disable')
        for key in ('wifi_on', 'mobile_data'):
            if capture.adb('shell', 'settings', 'get', 'global', key).stdout.strip() != b'0':
                raise ValueError('Network radios not confirmed off')
        # No UiAutomator process competes with this instrumentation invocation.
        result = capture.adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
                             ','.join(f'{cls}#{method}' for cls, methods in selection.items() for method in methods), test_package + '/' + runner.get(ns + 'name'),
                             check=False, timeout=1800)
        raw = (result.stdout + result.stderr).decode('utf-8', errors='replace')
        (OUTPUT / 'instrumentation.txt').write_text(raw)
        current = {}
        outcomes = {}
        for line in raw.splitlines():
            if line.startswith('INSTRUMENTATION_STATUS: '):
                key, sep, value = line.removeprefix('INSTRUMENTATION_STATUS: ').partition('=')
                if sep:
                    current[key] = value
            elif line.startswith('INSTRUMENTATION_STATUS_CODE: '):
                code = int(line.split(': ', 1)[1])
                if code in (0, -1, -2, -3, -4) and 'class' in current and 'test' in current:
                    case = (current['class'], current['test'])
                    if case in outcomes:
                        raise ValueError('Duplicate instrumentation result')
                    outcomes[case] = code
                current = {}
        report['results'] = [dict(className=c, method=m, status=code) for (c, m), code in sorted(outcomes.items())]
        if (result.returncode or set(outcomes) != expected or any(code != 0 for code in outcomes.values())
                or 'INSTRUMENTATION_CODE: -1' not in raw or 'INSTRUMENTATION_FAILED' in raw):
            raise ValueError('Instrumentation failed/skipped/incomplete; no pass claimed')
    except Exception:
        report['failure'] = 'Offline instrumentation incomplete; inspect retained dummy-device results.'
        # Screenshot/logcat do not use UiAutomator while instrumentation may still be active.
        try:
            (OUTPUT / 'failure.png').write_bytes(capture.adb('exec-out', 'screencap', '-p').stdout)
            (OUTPUT / 'failure-logcat.txt').write_bytes(capture.adb('logcat', '-d', '-t', '1000', check=False).stdout)
        except Exception:
            report['cleanupErrors'].append('Failure capture unavailable')
        raise
    finally:
        if started:
            report['cleanupErrors'].append('Ambiguous install outcome; unowned uninstall refused')
        for package, installation in reversed(list(installed.items())):
            try:
                capture.owned_device()
                if capture.identity(package) != installation:
                    raise ValueError('Install identity changed')
                capture.adb('shell', 'am', 'force-stop', package)
                capture.adb('uninstall', package)
                if capture.adb('shell', 'pm', 'path', package, check=False).stdout.strip():
                    raise ValueError('Owned package absence unconfirmed')
            except Exception:
                report['cleanupErrors'].append('Owned package cleanup unconfirmed: ' + package)
        (OUTPUT / 'results.json').write_text(json.dumps(report, indent=2) + '\n')
        text = (f'## Offline instrumentation\n\nAPI {report["api"]}; expected {len(expected)} cases; '
                f'observed {len(report["results"])}; cleanup limitations {len(report["cleanupErrors"])}.\n'
                'See per-case statuses; a missing/skipped case is not a pass. No live Telegram, physical device or human acceptance.\n')
        (OUTPUT / 'results.md').write_text(text)
        with Path(os.environ['GITHUB_STEP_SUMMARY']).open('a') as stream:
            stream.write(text)
    if report['cleanupErrors']:
        raise ValueError('Instrumentation cleanup incomplete')


def main():
    capture = load_capture()
    mode = os.environ['CI_EMULATOR_OPERATION']
    if mode not in ('capture', 'instrumentation', 'both'):
        raise ValueError('Unknown emulator operation')
    failures = []
    if mode in ('capture', 'both'):
        try:
            capture.main()
        except Exception:
            failures.append('capture')
    if mode in ('instrumentation', 'both'):
        try:
            instrument(capture)
        except Exception:
            failures.append('instrumentation')
    if failures:
        raise ValueError('Incomplete selected phases: ' + ', '.join(failures))


if __name__ == '__main__':
    try:
        main()
    except Exception:
        sys.exit('Emulator operation incomplete; inspect retained phase evidence. No pass claimed.')
