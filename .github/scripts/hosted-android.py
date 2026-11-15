#!/usr/bin/env python3
"""Explicit, version-preserving hosted Android operations; never a local build shortcut."""

import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
ANDROID = ROOT / 'android'
OPERATIONS = {
    'compile-debug': [':app:compileDebugSources'],
    'compile-release': [':app:compileReleaseSources'],
    'jvm': [':app:testDebugUnitTest'],
    'all': [':app:compileDebugSources', ':app:compileReleaseSources', ':app:testDebugUnitTest'],
    'lint': ['-I', str(ROOT / '.github/production-lint.init.gradle'), ':app:lintDebug'],
    'debug-apk': [':app:assembleDebug'],
    'instrumentation-apk': [':app:assembleDebugAndroidTest'],
    'release-apk': [':app:assembleRelease'],
    'dependencies': ['-I', str(ROOT / '.github/dependency-inventory.init.gradle'), ':app:ciDependencyInventory'],
    'codeql': ['--no-build-cache', '--rerun-tasks', ':app:compileDebugSources'],
}


def require_hosted():
    if (os.environ.get('GITHUB_ACTIONS') != 'true' or sys.platform != 'linux'
            or os.environ.get('RUNNER_ENVIRONMENT') != 'github-hosted'):
        raise ValueError('Explicit GitHub-hosted Linux job required')
    return Path(os.environ['RUNNER_TEMP']).resolve()


def prepare():
    temporary = require_hosted()
    wrapper = (ANDROID / 'gradle/wrapper/gradle-wrapper.properties').read_text()
    app = (ANDROID / 'app/build.gradle.kts').read_text()
    if ('gradle-8.7-bin.zip' not in wrapper
            or not re.search(r'^distributionSha256Sum=[0-9a-f]{64}$', wrapper, re.M)
            or not re.search(r'compileSdk\s*=\s*34\b', app)):
        raise ValueError('Review hosted setup after a toolchain pin change')
    mode = subprocess.run(['git', 'ls-files', '-s', '--', 'android/gradlew'], cwd=ROOT,
                          capture_output=True, text=True, check=True).stdout
    if not mode.startswith('100755 ') or not os.access(ANDROID / 'gradlew', os.X_OK):
        raise ValueError('Tracked wrapper must be executable')
    namespace = temporary / 'shiftreport-hosted'
    namespace.mkdir(exist_ok=True)
    with Path(os.environ['GITHUB_ENV']).open('a', encoding='utf-8') as stream:
        stream.write(f'CI_PROJECT_CACHE={namespace / "project-cache"}\n')
        stream.write(f'CI_APP_BUILD={namespace / "build/app"}\n')
        stream.write('ANDROID_SHARED_DISABLE=1\n')
        stream.write('SHIFTREPORT_LIVE_BOOTSTRAP_CONFIG=\n')


def preflight():
    require_hosted()
    memory = Path('/proc/meminfo').read_text()
    available = int(re.search(r'^MemAvailable:\s+(\d+)', memory, re.M)[1])
    swap_free = int(re.search(r'^SwapFree:\s+(\d+)', memory, re.M)[1])
    if available < 4 * 1024 * 1024:
        raise ValueError('Less than 4 GiB available RAM for the 2 GiB Gradle heap plus compiler/native overhead')
    for entry in Path('/proc').iterdir():
        if not entry.name.isdecimal():
            continue
        try:
            command = (entry / 'cmdline').read_bytes().replace(b'\0', b' ')
        except FileNotFoundError:
            continue
        except PermissionError as error:
            raise ValueError('Process visibility is insufficient for build preflight') from error
        if any(marker in command for marker in (
            b'org.gradle.launcher.GradleMain', b'GradleWrapperMain', b'GradleWorkerMain',
            b'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', b'com.intellij.idea.Main',
        )):
            raise ValueError('Competing build/compiler/IDE observed; defer this operation')
    print(f'Hosted preflight: RAM available {available // 1024} MiB; swap free {swap_free // 1024} MiB; heap 2048 MiB; one worker.')


def base_command():
    temporary = require_hosted()
    namespace = temporary / 'shiftreport-hosted'
    if (Path(os.environ['CI_PROJECT_CACHE']).resolve() != namespace / 'project-cache'
            or Path(os.environ['CI_APP_BUILD']).resolve() != namespace / 'build/app'):
        raise ValueError('Unexpected hosted output namespace')
    return [str(ANDROID / 'gradlew'), '--project-cache-dir', str(namespace / 'project-cache'),
            '--no-daemon', '--no-parallel', '--max-workers=1', '--console=plain',
            '-Pshiftreport.autoVersion=false', f'-Pshiftreport.buildRoot={namespace / "build"}',
            f'-Pshiftreport.apkExportDir={namespace / "exports"}',
            '-Pkotlin.compiler.execution.strategy=in-process',
            '-I', str(ROOT / '.github/hosted.init.gradle')]


def run(operation):
    command = base_command()
    preflight()
    directory = ROOT / 'ci-output' / operation
    directory.mkdir(parents=True, exist_ok=True)
    version = ANDROID / 'gradle/app-version.properties'
    before = version.read_bytes()
    build = Path(os.environ['CI_APP_BUILD'])
    environment = {key: value for key, value in os.environ.items()
                   if not key.startswith(('SHIFTREPORT_SIGN_', 'SHIFTREPORT_LIVE_'))}
    try:
        with (directory / 'gradle.log').open('w', encoding='utf-8') as stream:
            result = subprocess.run([*command, '--no-configuration-cache', *OPERATIONS[operation]],
                                    cwd=ANDROID, env=environment, stdout=stream,
                                    stderr=subprocess.STDOUT, timeout=3000)
    finally:
        for name in ('reports', 'test-results'):
            if name == 'test-results' and operation not in ('jvm', 'all'):
                continue
            if (build / name).is_dir():
                shutil.copytree(build / name, directory / name, dirs_exist_ok=True)
        if version.read_bytes() != before:
            raise ValueError('Stored version changed unexpectedly; preserve evidence and investigate')
    if result.returncode:
        print(f'Operation {operation} failed; see retained gradle.log.', file=sys.stderr)
        return result.returncode
    if operation in ('jvm', 'all'):
        import xml.etree.ElementTree as ET
        results = sorted((build / 'test-results/testDebugUnitTest').glob('TEST-*.xml'))
        if not results or sum(int(ET.parse(path).getroot().get('tests', '0')) for path in results) == 0:
            raise ValueError('Missing/empty JVM results; no pass claimed')
    if operation in ('debug-apk', 'release-apk', 'instrumentation-apk'):
        variant = 'release' if operation == 'release-apk' else 'debug'
        location = 'outputs/apk/androidTest/debug' if operation == 'instrumentation-apk' else f'outputs/apk/{variant}'
        packages = sorted((build / location).glob('*.apk'))
        if len(packages) != 1:
            raise ValueError('Expected exactly one fresh universal APK')
        package = packages[0]
        if variant == 'release':
            subprocess.run([sys.executable, str(ROOT / '.github/scripts/inspect-release.py'), str(package)], cwd=ROOT, check=True)
        shutil.copy2(package, directory / package.name)
        checksum = hashlib.sha256(package.read_bytes()).hexdigest()
        (directory / 'SHA256SUMS.txt').write_text(f'{checksum}  {package.name}\n')
        (directory / 'build-info.json').write_text(json.dumps(dict(
            operation=operation, sourceCommit=os.environ['GITHUB_SHA'], run=os.environ['GITHUB_RUN_ID'],
            attempt=os.environ['GITHUB_RUN_ATTEMPT'], apk=package.name, sha256=checksum,
            versionProperties=before.decode().strip()), indent=2) + '\n')
        if variant == 'release' and (build / 'outputs/mapping/release').is_dir():
            shutil.copytree(build / 'outputs/mapping/release', directory / 'mapping', dirs_exist_ok=True)
    text = (f'## {operation}\n\nGradle completed; stored version unchanged.\n\n'
            'Results establish this selected operation only; no physical device, production signing, publication or live Telegram acceptance.\n')
    if operation == 'debug-apk':
        text += 'Preview uses an ephemeral runner signer and cannot update an existing differently signed installation.\n'
    if operation == 'release-apk':
        text += 'Unsigned release inspection artifact; not a production distribution.\n'
    (directory / 'result.md').write_text(text)
    with Path(os.environ['GITHUB_STEP_SUMMARY']).open('a') as stream:
        stream.write(text)
    return 0


if __name__ == '__main__':
    try:
        selected = sys.argv[1]
        if selected == 'prepare':
            prepare()
        elif selected in OPERATIONS:
            sys.exit(run(selected))
        else:
            raise ValueError('Unknown hosted operation')
    except Exception:
        sys.exit('Hosted operation incomplete; see scoped diagnostics. No pass claimed.')
