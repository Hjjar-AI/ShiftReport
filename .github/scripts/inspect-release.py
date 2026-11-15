"""Inspect an already-built release against current ShiftReport source; never sign it."""

import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import struct
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
ANDROID = '{http://schemas.android.com/apk/res/android}'


MACHINES = {'armeabi-v7a': 40, 'arm64-v8a': 183, 'x86': 3, 'x86_64': 62}


def inspect_elf(data, abi):
    if len(data) < 64 or data[:4] != b'\x7fELF' or data[5] != 1 or data[4] not in (1, 2):
        raise ValueError('Unsupported or invalid native ELF')
    if struct.unpack_from('<H', data, 18)[0] != MACHINES[abi]:
        raise ValueError('Native machine does not match APK ABI directory')
    wide = data[4] == 2
    if wide != (abi in ('arm64-v8a', 'x86_64')):
        raise ValueError('Native ELF bitness does not match APK ABI directory')
    offset = struct.unpack_from('<Q' if wide else '<I', data, 32 if wide else 28)[0]
    size, count = struct.unpack_from('<HH', data, 54 if wide else 42)
    if size < (56 if wide else 32) or count == 0:
        raise ValueError('Invalid ELF program header table')
    alignments = []
    for index in range(count):
        start = offset + index * size
        if start + size > len(data):
            raise ValueError('Truncated ELF program header')
        if struct.unpack_from('<I', data, start)[0] == 1:  # PT_LOAD
            alignment = struct.unpack_from('<Q' if wide else '<I', data, start + (48 if wide else 28))[0]
            alignments.append(alignment)
    if not alignments or (wide and any(value < 16384 for value in alignments)):
        raise ValueError('64-bit native load segments must support 16 KB pages')
    return alignments


def main(apk):
    if os.environ.get('GITHUB_ACTIONS') != 'true':
        raise ValueError('Explicit hosted operation required')
    tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/34.0.0'
    analyzer = Path(os.environ['ANDROID_HOME']) / 'cmdline-tools/latest/bin/apkanalyzer'
    output = ROOT / 'ci-output/release-apk'
    output.mkdir(parents=True, exist_ok=True)
    def command(arguments):
        return subprocess.run(arguments, capture_output=True, text=True, check=True, timeout=60).stdout
    badging = command([str(tools / 'aapt'), 'dump', 'badging', str(apk)])
    manifest_text = command([str(analyzer), 'manifest', 'print', str(apk)])
    manifest = ET.fromstring(manifest_text)
    source = ET.parse(ROOT / 'android/app/src/main/AndroidManifest.xml').getroot()
    version = dict(re.findall(r'^([^#\s=]+)=(.*)$', (ROOT / 'android/gradle/app-version.properties').read_text(), re.M))
    if (manifest.get('package') != 'com.hos.rushdpatients'
            or manifest.get(ANDROID + 'versionCode') != version['versionCode']
            or manifest.get(ANDROID + 'versionName') != version['versionName']
            or 'application-debuggable' in badging):
        raise ValueError('Unexpected release identity/version/debuggable state')
    sdk = manifest.find('uses-sdk')
    if sdk is None or sdk.get(ANDROID + 'minSdkVersion') != '23' or sdk.get(ANDROID + 'targetSdkVersion') != '34':
        raise ValueError('Unexpected packaged SDKs')
    app = manifest.find('application')
    source_app = source.find('application')
    if app is None or source_app is None or app.get(ANDROID + 'debuggable', 'false') != 'false':
        raise ValueError('Missing/non-release application')
    for attribute in ('allowBackup', 'supportsRtl'):
        if app.get(ANDROID + attribute) != source_app.get(ANDROID + attribute):
            raise ValueError('Packaged policy differs from current source: ' + attribute)
    if app.get(ANDROID + 'usesCleartextTraffic', 'false') != 'false':
        raise ValueError('Unexpected cleartext network permission')
    permissions = {node.get(ANDROID + 'name') for node in manifest if node.tag.startswith('uses-permission')}
    expected = {node.get(ANDROID + 'name') for node in source if node.tag.startswith('uses-permission')}
    receiver = 'com.hos.rushdpatients.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'
    framework_permissions = {
        'android.permission.WAKE_LOCK', 'android.permission.RECEIVE_BOOT_COMPLETED',
        'android.permission.FOREGROUND_SERVICE',
    }
    if not expected <= permissions or permissions - expected - framework_permissions - {receiver}:
        raise ValueError('Unexpected merged permission set; review actual shipping graph')
    if receiver in permissions:
        declaration = next((node for node in manifest.findall('permission') if node.get(ANDROID + 'name') == receiver), None)
        protection = declaration.get(ANDROID + 'protectionLevel', '') if declaration is not None else ''
        try:
            signature_only = protection == 'signature' or int(protection, 0) == 2
        except ValueError:
            signature_only = False
        if not signature_only:
            raise ValueError('Generated receiver permission must be signature-protected')
    with zipfile.ZipFile(apk) as package:
        if package.testzip() is not None:
            raise ValueError('Corrupt APK entry')
        if 'classes.dex' not in package.namelist() or 'resources.arsc' not in package.namelist():
            raise ValueError('Missing compiled classes/resources')
        native = [name for name in package.namelist() if name.startswith('lib/') and name.endswith('.so')]
        native_alignment = {}
        for name in native:
            abi = name.split('/')[1]
            if abi not in MACHINES:
                raise ValueError('Unexpected native ABI')
            native_alignment[name] = inspect_elf(package.read(name), abi)
        for abi in MACHINES:
            if f'lib/{abi}/libsqlcipher.so' not in native:
                raise ValueError('SQLCipher native runtime missing: ' + abi)
        assets = ROOT / 'android/app/src/main/assets'
        checked = 0
        for path in assets.rglob('*'):
            if path.is_file():
                if path.is_symlink() or package.read('assets/' + path.relative_to(assets).as_posix()) != path.read_bytes():
                    raise ValueError('Packaged public asset differs from source')
                checked += 1
    subprocess.run([str(tools / 'zipalign'), '-c', '4', str(apk)], capture_output=True, check=True, timeout=30)
    receipt = dict(package=manifest.get('package'), versionCode=int(version['versionCode']),
                   versionName=version['versionName'], apkSha256=hashlib.sha256(apk.read_bytes()).hexdigest(),
                   sourceCommit=os.environ['GITHUB_SHA'], publicAssetsChecked=checked,
                   permissions=sorted(permissions), nativeLibraries=native, nativeLoadAlignments=native_alignment,
                   signature='not established: this is the unsigned inspection build')
    (output / 'inspection.json').write_text(json.dumps(receipt, indent=2) + '\n')
    (output / 'manifest.xml').write_text(manifest_text)
    (output / 'badging.txt').write_text(badging)
    print('Release source/package/resource/permission/alignment inspection completed; no production signature or device pass claimed.')


if __name__ == '__main__':
    main(Path(sys.argv[1]))
