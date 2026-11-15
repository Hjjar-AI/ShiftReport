"""Explicit, preview-first cleanup of releases created by the debug publisher."""
from datetime import datetime, timedelta, timezone
import json
import os
import re
import subprocess
import time

MARKER = re.compile(r'<!-- shiftreport-debug-distribution:v1 run=([0-9]+) attempt=([0-9]+) source=([0-9a-f]{40}) -->')


def api(path, method='GET'):
    args = ['gh', 'api', path]
    if method != 'GET':
        args += ['--method', method]
    response = subprocess.run(args, capture_output=True, text=True, timeout=60)
    if response.returncode:
        raise ValueError('GitHub operation failed; no automatic mutation retry')
    return json.loads(response.stdout) if response.stdout.strip() else None


def identity(release):
    return (release['id'], release['tag_name'], release['updated_at'], release['body'],
            release['author']['id'], release['draft'], release['prerelease'], release['target_commitish'], release['published_at'],
            release.get('immutable', False),
            sorted((a['id'], a['name'], a['size'], a['updated_at']) for a in release['assets']))


def main():
    if os.environ.get('GITHUB_ACTIONS') != 'true' or os.environ.get('CI_CONFIRM') != 'true':
        raise ValueError('An explicitly confirmed hosted dispatch is required')
    repository = os.environ['GITHUB_REPOSITORY']
    if os.environ['GITHUB_REF'] != 'refs/heads/' + os.environ['CI_DEFAULT_BRANCH']:
        raise ValueError('Default branch required')
    days, keep = int(os.environ.get('CI_DAYS', '30')), int(os.environ.get('CI_KEEP', '5'))
    if not 1 <= days <= 365 or keep < 5:
        raise ValueError('Days must be 1–365 and keep must be at least five')
    preview = os.environ.get('CI_PREVIEW', 'true')
    if preview not in ('true', 'false'):
        raise ValueError('Explicit preview selection required')
    repo = api(f'repos/{repository}')
    if (repo['private'] or str(repo['id']) != os.environ['GITHUB_REPOSITORY_ID']
            or repo['full_name'].casefold() != repository.casefold()
            or repo['default_branch'] != os.environ['CI_DEFAULT_BRANCH']):
        raise ValueError('Unexpected repository/default branch')
    releases = []
    for page in range(1, 101):
        batch = api(f'repos/{repository}/releases?per_page=100&page={page}')
        releases += batch
        if len(batch) < 100:
            break
    else:
        raise ValueError('Release pagination incomplete; no deletion')
    owned = []
    for release in releases:
        marker = MARKER.search(release.get('body') or '')
        if (marker and release['tag_name'] == f'debug-{marker[1]}-{marker[2]}'
                and release['author']['login'] == 'github-actions[bot]' and not release['draft']
                and not release.get('immutable', False)):
            owned.append((release, marker))
    owned.sort(key=lambda item: item[0]['published_at'], reverse=True)
    retained = {item[0]['id'] for item in owned[:keep]}
    cutoff = datetime.now(timezone.utc) - timedelta(days=days)
    errors = []
    for release, marker in owned:
        if release['id'] in retained or any(datetime.fromisoformat(release[field].replace('Z', '+00:00')) >= cutoff for field in ('published_at', 'updated_at')):
            continue
        path = f'repos/{repository}/releases/{release["id"]}'
        tag_path = f'repos/{repository}/git/refs/tags/{release["tag_name"]}'
        try:
            # Recheck the release, attached assets and lightweight tag before mutation.
            fresh = api(path)
            tag = api(tag_path)
            if identity(fresh) != identity(release) or tag['object']['sha'] != marker[3] or tag['object']['type'] != 'commit':
                raise ValueError('Identity changed; retained')
            print(('Would delete: ' if preview == 'true' else 'Deleting: ') + release['tag_name'])
            if preview == 'true':
                continue
            api(path, 'DELETE')
            # Confirm actual absence; an API error or permission failure is not absence.
            for delay in (0, 1, 2, 4, 8):
                time.sleep(delay)
                observation = subprocess.run(['gh', 'api', '--include', path], capture_output=True, text=True, timeout=30)
                if re.search(r'^HTTP/\S+ 404\b', observation.stdout, re.M):
                    break
            else:
                raise ValueError('Release deletion not confirmed; tag retained')
            if api(tag_path)['object'] != tag['object']:
                raise ValueError('Tag identity changed; retained')
            api(tag_path, 'DELETE')
            confirmation = subprocess.run(['gh', 'api', '--include', tag_path], capture_output=True, text=True, timeout=30)
            if not re.search(r'^HTTP/\S+ 404\b', confirmation.stdout, re.M):
                raise ValueError('Tag deletion not confirmed')
        except (ValueError, KeyError, subprocess.SubprocessError):
            errors.append(release['tag_name'])
    print(f'Cleanup preview={preview}; unconfirmed/retained failures={len(errors)}.')
    if errors:
        raise ValueError('Some eligible releases/tags could not be confirmed cleaned: ' + ', '.join(errors))


if __name__ == '__main__':
    main()
