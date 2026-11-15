"""Capture startup/demo on an owned hosted AVD; no instrumentation suite invocation."""

import hashlib
import json
import os
from pathlib import Path
import re
import secrets
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


class EvidenceError(RuntimeError):
    """A reviewed diagnostic reason safe for the dummy hosted-device report."""


def failure_reason(error):
    return str(error) if isinstance(error, EvidenceError) else type(error).__name__ + ': details withheld'


def listed_users(details):
    # Legacy ADB shell (API <=23) converts LF to CRLF. Parse logical lines so
    # carriage returns cannot make a valid owner record disappear at `$`.
    users = []
    for line in details.splitlines():
        match = re.fullmatch(r'UserInfo\{(\d+):[^\r\n]*\}([^\r\n]*)', line.strip())
        if match:
            users.append({'id': match[1], 'running': match[2].strip() == 'running'})
    return users


def sole_running_owner(details):
    # Android 6 Am.java has no get-current-user. Pm.java lists every user and
    # its running state; exactly one registered/running user proves user 0.
    # https://android.googlesource.com/platform/frameworks/base/+/android-6.0.1_r1/cmds/pm/src/com/android/commands/pm/Pm.java
    users = listed_users(details)
    lines = [line.strip() for line in details.splitlines() if line.strip()]
    if len(lines) != 2 or lines[0] != 'Users:' or users != [{'id': '0', 'running': True}]:
        raise EvidenceError('Android 6 requires user 0 as the sole registered running user')
    return '0'


def owned_device():
    if (os.environ.get('GITHUB_ACTIONS') != 'true'
            or os.environ.get('RUNNER_ENVIRONMENT') != 'github-hosted'):
        raise EvidenceError('Refusing a local or non-hosted device')
    OUTPUT.mkdir(parents=True, exist_ok=True)
    observations = dict(serial=SERIAL, expectedApi=os.environ['CI_EXPECTED_API'],
                        expectedAvd=os.environ['CI_AVD_NAME'])
    try:
        observations['qemu'] = adb('shell', 'getprop', 'ro.kernel.qemu').stdout.decode().strip()
        observations['api'] = adb('shell', 'getprop', 'ro.build.version.sdk').stdout.decode().strip()
        avd = adb('emu', 'avd', 'name').stdout.decode().splitlines()
        observations['avd'] = avd[0].strip() if avd else ''
        if observations['api'] == '23':
            observations['userProof'] = 'sole-registered-running-user'
            user_text = adb('shell', 'pm', 'list', 'users').stdout.decode()
            observations['registeredUsers'] = listed_users(user_text)
            observations['userOutputLines'] = len([line for line in user_text.splitlines() if line.strip()])
            observations['legacyCarriageReturns'] = '\r' in user_text
            observations['user'] = sole_running_owner(user_text)
        else:
            observations['userProof'] = 'activity-manager-current-user'
            observations['user'] = adb('shell', 'am', 'get-current-user').stdout.decode().strip()
        if (observations['qemu'] != '1' or observations['user'] != '0'
                or observations['api'] != observations['expectedApi']
                or observations['avd'] != observations['expectedAvd']):
            raise EvidenceError('Owned AVD identity mismatch')
        observations['verified'] = True
    except Exception as error:
        observations['verified'] = False
        observations['failure'] = failure_reason(error)
        raise
    finally:
        (OUTPUT / 'ownership.json').write_text(json.dumps(observations, indent=2) + '\n')


def installation_identity(details):
    values = {}
    # Android 15 Settings.dumpPackageLPr prints appId; older releases print userId.
    # User 0 is independently proved above, so both describe that user's app UID.
    # https://android.googlesource.com/platform/frameworks/base/+/android-15.0.0_r1/services/core/java/com/android/server/pm/Settings.java
    fields = {'userId': r'(?:userId|appId)', 'codePath': 'codePath',
              'firstInstallTime': 'firstInstallTime', 'lastUpdateTime': 'lastUpdateTime'}
    for key, expression in fields.items():
        matches = set(value.strip() for value in re.findall(
            r'^[ \t]*' + expression + r'=([^\r\n]+)', details, re.M))
        if len(matches) != 1:
            raise EvidenceError('Missing/ambiguous installed package identity field: ' + key)
        values[key] = matches.pop()
    if (not values['userId'].isdigit() or not 10000 <= int(values['userId']) < 100000
            or not values['codePath'].startswith('/data/app/')):
        raise EvidenceError('Unexpected installed identity')
    return values


def identity(package=PACKAGE):
    details = adb('shell', 'dumpsys', 'package', package).stdout.decode()
    return installation_identity(details)


def screenshot(name):
    data = adb('exec-out', 'screencap', '-p').stdout
    if not data.startswith(b'\x89PNG\r\n\x1a\n'):
        raise EvidenceError('Missing/invalid PNG evidence')
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
        raise EvidenceError('Missing current resource label')
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
    raise EvidenceError('Expected startup/demo content not observed; no pass claimed')


def main():
    owned_device()
    kind = os.environ.get('CI_CAPTURE_KIND', 'debug')
    if kind not in ('debug', 'release'):
        raise EvidenceError('Unknown capture build type')
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
            raise EvidenceError('Existing package found; refusing replacement or data clearing')
        paths = list((ROOT / f'ci-output/{kind}-apk').glob('*.apk'))
        if len(paths) != 1:
            raise EvidenceError('Expected one fresh application APK')
        apk = paths[0]
        tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/34.0.0'
        if kind == 'release':
            source_hash = hashlib.sha256(apk.read_bytes()).hexdigest()
            inspection = json.loads((ROOT / 'ci-output/release-apk/inspection.json').read_text())
            if inspection['apkSha256'] != source_hash or inspection['sourceCommit'] != os.environ['GITHUB_SHA']:
                raise EvidenceError('Inspected release provenance mismatch')
            report['unsignedSourceSha256'] = source_hash
            # This throwaway signer is used only on this fresh hosted AVD. It is
            # never exported as a production distribution or a permanent identity.
            signing_workspace = tempfile.TemporaryDirectory(prefix='shiftreport-release-smoke-', dir=os.environ['RUNNER_TEMP'])
            work = Path(signing_workspace.name)
            work.chmod(0o700)
            key = work / 'throwaway.keystore'
            signed = work / 'release-smoke.apk'
            environment = dict(os.environ, CI_SMOKE_PASSWORD=secrets.token_urlsafe(32))
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
                raise EvidenceError('Unexpected application package')
            if executable == 'aapt' and ('application-debuggable' in result) != (kind == 'debug'):
                raise EvidenceError('Unexpected packaged build type')
        installation_started = True
        adb('install', '-t', str(apk), timeout=90)
        installed = identity()
        report['installation'] = installed
        installed_path = adb('shell', 'pm', 'path', PACKAGE).stdout.decode().strip()
        if not installed_path.startswith('package:/data/app/') or '\n' in installed_path:
            raise EvidenceError('Unexpected installed APK path')
        copy = Path(os.environ['RUNNER_TEMP']) / 'shiftreport-installed.apk'
        adb('pull', installed_path.removeprefix('package:'), str(copy))
        if hashlib.sha256(copy.read_bytes()).hexdigest() != report['apkSha256']:
            raise EvidenceError('Installed APK differs from verified artifact')
        report['installedBytesMatch'] = True
        report['phase'] = 'disable network radios'
        adb('shell', 'svc', 'wifi', 'disable')
        adb('shell', 'svc', 'data', 'disable')
        report['network'] = {
            name: adb('shell', 'settings', 'get', 'global', name).stdout.decode().strip()
            for name in ('wifi_on', 'mobile_data')}
        if any(value != '0' for value in report['network'].values()):
            raise EvidenceError('Network-radio disablement was not confirmed')
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
            raise EvidenceError('Demo entry has no usable visible bounds')
        left, top, right, bottom = map(int, bounds.groups())
        if right <= left or bottom <= top:
            raise EvidenceError('Empty demo entry bounds')
        # One explicit navigation through the real visible control; never retry a tap.
        adb('shell', 'input', 'tap', str((left + right) // 2), str((top + bottom) // 2))
        report['phase'] = 'demo'
        raw, _ = wait_for(labels('ui_demo_navigation_001'), contains=True)
        (OUTPUT / 'demo.xml').write_bytes(raw)
        screenshot('demo')
        report['captures'].append('demo')
        adb('shell', 'settings', 'put', 'system', 'font_scale', '1.3')
        if adb('shell', 'settings', 'get', 'system', 'font_scale').stdout.strip() != b'1.3':
            raise EvidenceError('Large-text setting did not apply')
        wait_for(labels('ui_demo_navigation_001'), contains=True)
        screenshot('demo-large-text')
        report['captures'].append('demo-large-text')
        if kind == 'release':
            pid = adb('shell', 'pidof', PACKAGE).stdout.strip()
            if not re.fullmatch(rb'\d+', pid):
                raise EvidenceError('Expected one release application process')
            for _ in range(3):
                time.sleep(10)
                if adb('shell', 'pidof', PACKAGE).stdout.strip() != pid:
                    raise EvidenceError('Minified release process exited/restarted during observation')
            report['releaseProcessSurvivalSeconds'] = 30
        report['phase'] = 'capture completed'
    except Exception as error:
        report['failure'] = failure_reason(error)
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
                    raise EvidenceError('Empty recording')
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
                    raise EvidenceError('Installation identity changed; refusing cleanup')
                adb('shell', 'am', 'force-stop', PACKAGE)
                adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
                adb('uninstall', PACKAGE)
                if adb('shell', 'pm', 'path', PACKAGE, check=False).stdout.strip():
                    raise EvidenceError('Owned package cleanup not confirmed')
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
        raise EvidenceError('Evidence/cleanup incomplete; inspect the retained report')


if __name__ == '__main__':
    main()
