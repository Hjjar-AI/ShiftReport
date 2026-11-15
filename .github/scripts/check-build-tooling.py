"""Explicit hosted configuration-cache reuse and non-packaging version checks."""

import importlib.util
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]


def main():
    spec = importlib.util.spec_from_file_location('hosted_android', ROOT / '.github/scripts/hosted-android.py')
    hosted = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(hosted)
    temporary = hosted.require_hosted()
    if Path(os.environ['CI_PROJECT_CACHE']).resolve() != temporary / 'shiftreport-hosted/project-cache':
        raise ValueError('Unexpected hosted namespace')
    hosted.preflight()
    version = ROOT / 'android/gradle/app-version.properties'
    before = version.read_bytes()
    output = ROOT / 'ci-output/build-tooling'
    output.mkdir(parents=True, exist_ok=True)
    base = hosted.base_command()
    try:
        for attempt in (1, 2):
            result = subprocess.run([*base, '--configuration-cache', 'help'], cwd=ROOT / 'android',
                                    capture_output=True, text=True, timeout=600)
            (output / f'configuration-{attempt}.log').write_text(result.stdout + result.stderr)
            if result.returncode or (attempt == 2 and 'Reusing configuration cache' not in result.stdout):
                raise ValueError('Configuration-cache execution/reuse not established')
        result = subprocess.run([*base, '--no-configuration-cache', '--dry-run', ':app:assembleDebug'],
                                cwd=ROOT / 'android', capture_output=True, text=True, timeout=600)
        (output / 'packaging-dry-run.log').write_text(result.stdout + result.stderr)
        if result.returncode:
            raise ValueError('Packaging dry-run failed')
    finally:
        if version.read_bytes() != before:
            raise ValueError('Non-packaging operation changed stored version; preserve and investigate')
    text = ('Configuration-cache reuse observed in two help invocations; debug packaging dry-run succeeded; stored version unchanged.\n'
            'No actual packaging, dependency-cache performance, Windows or concurrent reservation/locking acceptance.\n')
    (output / 'results.md').write_text(text)
    with Path(os.environ['GITHUB_STEP_SUMMARY']).open('a') as stream:
        stream.write(text)


if __name__ == '__main__':
    main()
