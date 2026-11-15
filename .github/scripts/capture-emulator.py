"""Capture startup/demo on an owned hosted AVD; no instrumentation suite invocation."""

import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / 'ci-output/emulator'
SERIAL = 'emulator-5554'
PACKAGE = 'com.hos.rushdpatients'


def adb(*arguments, check=True, timeout=30):
    return subprocess.run(['adb', '-s', SERIAL, *arguments], capture_output=True,
                          timeout=timeout, check=check)


def owned_device():
    if (os.environ.get('GITHUB_ACTIONS') != 'true'
            or os.environ.get('RUNNER_ENVIRONMENT') != 'github-hosted'):
        raise RuntimeError('Refusing a local or non-hosted device')
    if (adb('shell', 'getprop', 'ro.kernel.qemu').stdout.strip() != b'1'
            or adb('shell', 'am', 'get-current-user').stdout.strip() != b'0'
            or adb('shell', 'getprop', 'ro.build.version.sdk').stdout.decode().strip() != os.environ['CI_EXPECTED_API']
            or adb('emu', 'avd', 'name').stdout.decode().splitlines()[0].strip() != os.environ['CI_AVD_NAME']):
        raise RuntimeError('Owned AVD identity mismatch')


def identity(package=PACKAGE):
    details = adb('shell', 'dumpsys', 'package', package).stdout.decode()
    values = {}
    for key in ('userId', 'codePath', 'firstInstallTime', 'lastUpdateTime'):
        matches = set(re.findall(r'^\s*' + key + r'=([^\r\n]+)', details, re.M))
        if len(matches) != 1:
            raise RuntimeError('Missing/ambiguous installed package identity')
        values[key] = matches.pop().strip()
    if not values['userId'].isdigit() or int(values['userId']) < 10000 or not values['codePath'].startswith('/data/app/'):
        raise RuntimeError('Unexpected installed identity')
    return values


def screenshot(name):
    data = adb('exec-out', 'screencap', '-p').stdout
    if not data.startswith(b'\x89PNG\r\n\x1a\n'):
        raise RuntimeError('Missing/invalid PNG evidence')
    (OUTPUT / f'{name}.png').write_bytes(data)


def hierarchy():
    # A fresh, owned AVD contains no account/server configuration or credentials.
    adb('shell', 'uiautomator', 'dump', '/sdcard/shiftreport-ci-window.xml')
    raw = adb('exec-out', 'cat', '/sdcard/shiftreport-ci-window.xml').stdout
    return raw, ET.fromstring(raw)


def labels(key):
    found = set()
    for path in (ROOT / 'android/app/src/main/res').glob('values*/strings*.xml'):
        for node in ET.parse(path).getroot().findall('string'):
            if node.get('name') == key:
                found.add(''.join(node.itertext()).strip('"'))
    if not found:
        raise RuntimeError('Missing current resource label')
    return found


def wait_for(label_values, contains=False):
    deadline = time.monotonic() + 45
    while time.monotonic() < deadline:
        raw, tree = hierarchy()
        for node in tree.iter('node'):
            text = node.get('text', '')
            if node.get('package') == PACKAGE and any(
                    (label in text if contains else label == text) for label in label_values):
                return raw, node
        time.sleep(1)
    raise RuntimeError('Expected startup/demo content not observed; no pass claimed')


def main():
    owned_device()
    kind = os.environ.get('CI_CAPTURE_KIND', 'debug')
    if kind not in ('debug', 'release'):
        raise RuntimeError('Unknown capture build type')
    OUTPUT.mkdir(parents=True, exist_ok=True)
    report = dict(api=os.environ['CI_EXPECTED_API'], avd=os.environ['CI_AVD_NAME'], serial=SERIAL,
                  package=PACKAGE, sourceCommit=os.environ['GITHUB_SHA'], run=os.environ['GITHUB_RUN_ID'],
                  attempt=os.environ['GITHUB_RUN_ATTEMPT'], kind=kind, phase='prepare', captures=[], cleanupErrors=[])
    installed = None
    installation_started = False
    recorder = None
    recorder_log = None
    signing_workspace = None
    remote = f'/sdcard/shiftreport-ci-{os.environ["GITHUB_RUN_ID"]}-{os.environ["GITHUB_RUN_ATTEMPT"]}.mp4'
    try:
        probe = adb('shell', 'pm', 'path', PACKAGE, check=False)
        if probe.returncode not in (0, 1) or probe.stdout.strip() or probe.stderr.strip():
            raise RuntimeError('Existing package found; refusing replacement or data clearing')
        paths = list((ROOT / f'ci-output/{kind}-apk').glob('*.apk'))
        if len(paths) != 1:
            raise RuntimeError('Expected one fresh application APK')
        apk = paths[0]
        tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/34.0.0'
        if kind == 'release':
            source_hash = hashlib.sha256(apk.read_bytes()).hexdigest()
            inspection = json.loads((ROOT / 'ci-output/release-apk/inspection.json').read_text())
            if inspection['apkSha256'] != source_hash or inspection['sourceCommit'] != os.environ['GITHUB_SHA']:
                raise RuntimeError('Inspected release provenance mismatch')
            report['unsignedSourceSha256'] = source_hash
            # This throwaway signer is used only on this fresh hosted AVD. It is
            # never exported as a production distribution or a permanent identity.
            signing_workspace = tempfile.TemporaryDirectory(prefix='shiftreport-release-smoke-', dir=os.environ['RUNNER_TEMP'])
            work = Path(signing_workspace.name)
            work.chmod(0o700)
            key = work / 'throwaway.keystore'
            signed = work / 'release-smoke.apk'
            environment = dict(os.environ, CI_SMOKE_PASSWORD='throwaway-hosted-smoke-only')
            subprocess.run(['keytool', '-genkeypair', '-keystore', str(key), '-alias', 'smoke',
                            '-storepass:env', 'CI_SMOKE_PASSWORD', '-keypass:env', 'CI_SMOKE_PASSWORD',
                            '-keyalg', 'RSA', '-keysize', '2048', '-validity', '2', '-dname', 'CN=ShiftReport CI smoke'],
                           env=environment, capture_output=True, check=True, timeout=60)
            key.chmod(0o600)
            subprocess.run([str(tools / 'apksigner'), 'sign', '--ks', str(key), '--ks-key-alias', 'smoke',
                            '--ks-pass', 'env:CI_SMOKE_PASSWORD', '--key-pass', 'env:CI_SMOKE_PASSWORD',
                            '--v4-signing-enabled', 'false', '--out', str(signed), str(apk)],
                           env=environment, capture_output=True, check=True, timeout=60)
            apk = signed
        report['apkSha256'] = hashlib.sha256(apk.read_bytes()).hexdigest()
        for executable, arguments in (
                ('aapt', ['dump', 'badging', str(apk)]),
                ('apksigner', ['verify', '--print-certs', str(apk)])):
            result = subprocess.run([str(tools / executable), *arguments], capture_output=True,
                                    text=True, check=True, timeout=30).stdout
            (OUTPUT / f'apk-{executable}.txt').write_text(result)
            if executable == 'aapt' and not result.startswith(f"package: name='{PACKAGE}' "):
                raise RuntimeError('Unexpected application package')
            if executable == 'aapt' and ('application-debuggable' in result) != (kind == 'debug'):
                raise RuntimeError('Unexpected packaged build type')
        installation_started = True
        adb('install', '-t', str(apk), timeout=90)
        installed = identity()
        report['installation'] = installed
        installed_path = adb('shell', 'pm', 'path', PACKAGE).stdout.decode().strip()
        if not installed_path.startswith('package:/data/app/') or '\n' in installed_path:
            raise RuntimeError('Unexpected installed APK path')
        copy = Path(os.environ['RUNNER_TEMP']) / 'shiftreport-installed.apk'
        adb('pull', installed_path.removeprefix('package:'), str(copy))
        if hashlib.sha256(copy.read_bytes()).hexdigest() != report['apkSha256']:
            raise RuntimeError('Installed APK differs from verified artifact')
        report['installedBytesMatch'] = True
        report['phase'] = 'disable network radios'
        adb('shell', 'svc', 'wifi', 'disable')
        adb('shell', 'svc', 'data', 'disable')
        report['network'] = {
            name: adb('shell', 'settings', 'get', 'global', name).stdout.decode().strip()
            for name in ('wifi_on', 'mobile_data')}
        if any(value != '0' for value in report['network'].values()):
            raise RuntimeError('Network-radio disablement was not confirmed')
        recorder_log = (OUTPUT / 'recorder.log').open('wb')
        recorder = subprocess.Popen(['adb', '-s', SERIAL, 'shell', 'screenrecord', '--size', '360x640',
                                     '--bit-rate', '1000000', '--time-limit', '45', remote],
                                    stdout=recorder_log, stderr=subprocess.STDOUT)
        report['phase'] = 'startup'
        launch = adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
        (OUTPUT / 'launch.txt').write_bytes(launch.stdout + launch.stderr)
        raw, node = wait_for(labels('ui_project_setup_screen_038'))
        (OUTPUT / 'startup.xml').write_bytes(raw)
        screenshot('startup')
        report['captures'].append('startup')
        bounds = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.get('bounds', ''))
        if not bounds or node.get('enabled') != 'true':
            raise RuntimeError('Demo entry has no usable visible bounds')
        left, top, right, bottom = map(int, bounds.groups())
        if right <= left or bottom <= top:
            raise RuntimeError('Empty demo entry bounds')
        # One explicit navigation through the real visible control; never retry a tap.
        adb('shell', 'input', 'tap', str((left + right) // 2), str((top + bottom) // 2))
        report['phase'] = 'demo'
        raw, _ = wait_for(labels('ui_demo_ward_screen_005'), contains=True)
        (OUTPUT / 'demo.xml').write_bytes(raw)
        screenshot('demo')
        report['captures'].append('demo')
        adb('shell', 'settings', 'put', 'system', 'font_scale', '1.3')
        if adb('shell', 'settings', 'get', 'system', 'font_scale').stdout.strip() != b'1.3':
            raise RuntimeError('Large-text setting did not apply')
        wait_for(labels('ui_demo_ward_screen_005'), contains=True)
        screenshot('demo-large-text')
        report['captures'].append('demo-large-text')
        if kind == 'release':
            pid = adb('shell', 'pidof', PACKAGE).stdout.strip()
            if not re.fullmatch(rb'\d+', pid):
                raise RuntimeError('Expected one release application process')
            for _ in range(3):
                time.sleep(10)
                if adb('shell', 'pidof', PACKAGE).stdout.strip() != pid:
                    raise RuntimeError('Minified release process exited/restarted during observation')
            report['releaseProcessSurvivalSeconds'] = 30
        report['phase'] = 'capture completed'
    except Exception as error:
        report['failure'] = type(error).__name__ + ': capture incomplete; inspect dummy-device diagnostics'
        try:
            screenshot('failure')
            log = adb('logcat', '-d', '-t', '1500', check=False)
            (OUTPUT / 'failure-logcat.txt').write_bytes(log.stdout + log.stderr)
        except Exception:
            report['cleanupErrors'].append('Failure capture unavailable')
        raise
    finally:
        if recorder is not None:
            try:
                owned_device()
                for pid in adb('shell', 'pidof', 'screenrecord', check=False).stdout.decode().split():
                    if pid.isdigit():
                        command = adb('shell', 'cat', f'/proc/{pid}/cmdline', check=False).stdout
                        if b'screenrecord' in command and remote.encode() in command:
                            adb('shell', 'kill', '-INT', pid)
                recorder.wait(timeout=10)
                adb('pull', remote, str(OUTPUT / 'startup-demo.mp4'))
                if (OUTPUT / 'startup-demo.mp4').stat().st_size == 0:
                    raise RuntimeError('Empty recording')
                adb('shell', 'rm', '-f', remote)
            except Exception:
                report['cleanupErrors'].append('Recording missing/incomplete or recorder cleanup failed')
            finally:
                if recorder.poll() is None:
                    recorder.terminate()
                    try:
                        recorder.wait(timeout=5)
                    except subprocess.TimeoutExpired:
                        recorder.kill()
                        recorder.wait(timeout=5)
                recorder_log.close()
        if installed is not None:
            try:
                owned_device()
                if identity() != installed:
                    raise RuntimeError('Installation identity changed; refusing cleanup')
                adb('shell', 'am', 'force-stop', PACKAGE)
                adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
                adb('uninstall', PACKAGE)
                if adb('shell', 'pm', 'path', PACKAGE, check=False).stdout.strip():
                    raise RuntimeError('Owned package cleanup not confirmed')
                report['ownedPackageCleanup'] = 'absent'
            except Exception:
                report['cleanupErrors'].append('Owned package cleanup unconfirmed')
        elif installation_started:
            report['cleanupErrors'].append('Installation outcome/identity unconfirmed; no uninstall attempted')
        if signing_workspace is not None:
            try:
                signing_workspace.cleanup()
            except OSError:
                report['cleanupErrors'].append('Throwaway signing workspace cleanup unconfirmed')
        (OUTPUT / 'provenance.json').write_text(json.dumps(report, indent=2) + '\n')
        text = ('## Emulator visual capture\n\n' + f'{kind} build; API {report["api"]}; last phase: {report["phase"]}.\n'
                + f'Captured: {", ".join(report["captures"]) or "none"}. Cleanup/capture limitations: {len(report["cleanupErrors"])}.\n\n'
                'Startup/demo evidence only. No instrumentation suite, functional-regression, accessibility or human visual acceptance claim.\n'
                'Network radios were requested off; this is not packet-level network isolation proof.\n')
        (OUTPUT / 'results.md').write_text(text)
        with Path(os.environ['GITHUB_STEP_SUMMARY']).open('a') as summary:
            summary.write(text)
    if report['cleanupErrors']:
        raise RuntimeError('Evidence/cleanup incomplete; inspect the retained report')


if __name__ == '__main__':
    main()
