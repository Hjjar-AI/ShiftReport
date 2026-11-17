#!/usr/bin/env python3
"""Read-only production-source integrity checks; no tests, Gradle or network."""

import ast
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tomllib
from urllib.parse import unquote, urlsplit
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]
EXCLUDED_PARTS = frozenset((
    'test', 'tests', 'androidTest', 'sharedTest', 'testFixtures', 'migrations',
    'schemas', '.git', '.gradle', '.kotlin', 'build', 'node_modules',
))
PRIVATE_DIRECTORIES = frozenset(('local', 'ci-output', '.signing', 'signing', 'release-files', 'exports', 'reports', 'backups', 'keys'))
PRIVATE_NAMES = frozenset((
    'local.properties', 'server.properties', 'keystore.properties',
    'release-keystore.properties', 'release.properties', 'secrets.properties',
    'id_rsa', 'id_ed25519', 'id_ecdsa',
))
PRIVATE_SUFFIXES = frozenset((
    '.jks', '.keystore', '.p12', '.pfx', '.pem', '.key', '.p8', '.apk', '.aab',
    '.idsig', '.hprof', '.db', '.sqlite', '.sqlite3', '.rushdbackup', '.rushdbak', '.rpb', '.srjoin', '.srdraft',
))
BINARY_SUFFIXES = frozenset((
    '.jar', '.png', '.jpg', '.jpeg', '.webp', '.gif', '.ico', '.ttf', '.otf',
    '.woff', '.woff2', '.zip', '.pdf',
))
UI_SOURCE = 'android/app/src/main/java/com/hos/rushdpatients/ui/'
UI_TOKEN_RULES = {
    'dp': re.compile(r'\b[0-9]+(?:\.[0-9]+)?\.dp\b'),
    'color': re.compile(r'\bColor\s*\(\s*0x[0-9a-fA-F]+'),
    'opacity': re.compile(r'\balpha\s*=\s*(?:if\s*\([^)]*\)\s*)?(?:[0-9]+(?:\.[0-9]+)?|\.[0-9]+)f?\b'),
    'delay': re.compile(r'\bdelay\(\s*[0-9][0-9_]*L?\s*\)'),
}
# Exact existing transport pacing, not a presentation delay. A second occurrence
# or a different value still fails; do not exempt entire files or directories.
UI_TOKEN_EXCEPTIONS = {
    ('login/LoginViewModel.kt', 'delay', 'delay(1000)'): 1,
}


def ui_token_problems(name, text):
    if not name.startswith(UI_SOURCE) or not name.endswith('.kt'):
        return []
    relative = name[len(UI_SOURCE):]
    if relative.startswith('theme/'):
        return []
    problems = []
    seen = {}
    for rule, pattern in UI_TOKEN_RULES.items():
        for match in pattern.finditer(text):
            key = (relative, rule, match.group())
            seen[key] = seen.get(key, 0) + 1
            if seen[key] <= UI_TOKEN_EXCEPTIONS.get(key, 0):
                continue
            line = text.count('\n', 0, match.start()) + 1
            problems.append(f'{name}:{line}: hardcoded UI {rule}; use its theme owner')
    return problems


SECRET_PATTERNS = {
    'private-key': re.compile(r'-----BEGIN (?:RSA |EC |DSA |OPENSSH |ENCRYPTED )?PRIVATE KEY-----'),
    'github-token': re.compile(r'\b(?:gh[pousr]_[A-Za-z0-9]{36,255}|github_pat_[A-Za-z0-9_]{50,255})\b'),
    'telegram-token': re.compile(r'\b[0-9]{6,16}:[A-Za-z0-9_-]{30,}\b'),
    'aws-access-key': re.compile(r'\b(?:AKIA|ASIA)[A-Z0-9]{16}\b'),
    'openai-key': re.compile(r'\bsk-(?:proj-|svcacct-)?[A-Za-z0-9_-]{40,}\b'),
    'google-api-key': re.compile(r'\bAIza[A-Za-z0-9_-]{35}\b'),
    'slack-token': re.compile(r'\bxox[baprs]-[A-Za-z0-9-]{20,}\b|\bxapp-[A-Za-z0-9-]{20,}\b'),
}


def excluded(path):
    return bool(set(path.parts) & EXCLUDED_PARTS) or any(re.match(r'migrat', part, re.I) for part in path.parts)


def private(path):
    return (bool(set(path.parts) & PRIVATE_DIRECTORIES)
            or path.name in PRIVATE_NAMES
            or path.suffix.lower() in PRIVATE_SUFFIXES
            or path.name.endswith(('.local.properties', '.srjoin.json'))
            or (path.name.startswith('.env') and path.name != '.env.example'))


def markdown_body(text):
    return re.sub(r'(?ms)^(```|~~~).*?^\1[^\n]*$', '', text)


def anchors(text):
    counts = {}
    values = set(re.findall(r'<a\s+(?:name|id)=["\']([^"\']+)', text))
    for heading in re.findall(r'^#{1,6}\s+(.+?)(?:\s+#+)?$', markdown_body(text), re.M):
        slug = re.sub(r'[^\w\- ]', '', heading.lower().replace('`', '')).replace(' ', '-')
        count = counts.get(slug, 0)
        values.add(slug + (f'-{count}' if count else ''))
        counts[slug] = count + 1
    return values


ROOT_FILES = frozenset(('README.md', 'AGENTS.md', 'SECURITY.md', '.gitignore', '.gitattributes', '.editorconfig'))


def selected(path):
    return (path.as_posix() in ROOT_FILES or path.parts[0] in ('android', '.github', '.githooks')
            or (path.parts[0] == 'docs' and not path.as_posix().startswith('docs/guides/desktop/')))


def main():
    names = subprocess.run(
        ['git', 'ls-files', '--cached', '--others', '--exclude-standard', '-z'],
        cwd=ROOT, capture_output=True, check=True,
    ).stdout.decode('utf-8').split('\0')
    problems = []
    files = links = external_links = excluded_links = 0
    texts = {}
    for name in sorted(set(filter(None, names))):
        relative = Path(name)
        # Skip suite/migration/generated contents even when tracked.
        if not selected(relative) or excluded(relative):
            continue
        if private(relative):
            problems.append(f'{name}: private/generated file must not be tracked (contents not read)')
            continue
        path = ROOT / relative
        if path.is_symlink() or not path.resolve().is_relative_to(ROOT):
            problems.append(f'{name}: source symlink is not inspected; review its target explicitly')
            continue
        if not path.is_file() or path.suffix.lower() in BINARY_SUFFIXES:
            continue
        raw = path.read_bytes()
        if b'\0' in raw:
            continue
        try:
            text = raw.decode('utf-8')
        except UnicodeDecodeError:
            problems.append(f'{name}: non-UTF-8 source; classify a binary format explicitly if appropriate')
            continue
        files += 1
        texts[path] = text
        problems.extend(ui_token_problems(name, text))
        for rule, pattern in SECRET_PATTERNS.items():
            for match in pattern.finditer(text):
                line = text.count('\n', 0, match.start()) + 1
                problems.append(f'{name}:{line}: possible {rule} (value redacted)')
        try:
            if path.suffix == '.json':
                json.loads(text)
            elif path.suffix == '.toml':
                tomllib.loads(text)
            elif path.suffix == '.xml':
                ET.fromstring(text)
            elif path.suffix == '.py':
                ast.parse(text, filename=name)
        except (ValueError, ET.ParseError, SyntaxError):
            problems.append(f'{name}: malformed {path.suffix} source/configuration')

    for path, text in texts.items():
        if path.suffix != '.md':
            continue
        body = markdown_body(text)
        destinations = re.findall(r'\]\((<[^>]+>|[^)]+)\)', body)
        destinations += re.findall(r'^\s*\[[^\]]+\]:\s*(\S+)', body, re.M)
        for destination in destinations:
            destination = re.split(r'\s+["\']', destination.strip(), maxsplit=1)[0].strip('<>')
            url = urlsplit(destination)
            if url.scheme or url.netloc:
                continue
            relative = unquote(url.path)
            # Absolute workstation paths and sibling-repository links are external
            # dependencies, not files available in a single-repository CI checkout.
            target = (path.parent / relative).resolve() if relative else path
            if relative.startswith('/') or not target.is_relative_to(ROOT):
                external_links += 1
                continue
            local = target.relative_to(ROOT)
            if not selected(local) or excluded(local) or private(local):
                excluded_links += 1
                continue
            links += 1
            if not target.exists():
                problems.append(f'{path.relative_to(ROOT)}: missing local link {relative}')
            elif url.fragment and target.suffix == '.md':
                target_text = texts.get(target)
                # Do not read ignored/private/unselected targets via a Markdown link.
                if target_text is None:
                    excluded_links += 1
                elif unquote(url.fragment) not in anchors(target_text):
                    problems.append(f'{path.relative_to(ROOT)}: missing local anchor {relative}#{url.fragment}')

    properties = dict(re.findall(r'^([^#\s=]+)=(.*)$', texts[ROOT / 'android/gradle/wrapper/gradle-wrapper.properties'], re.M))
    if not re.fullmatch(r'[0-9a-f]{64}', properties.get('distributionSha256Sum', '')):
        problems.append('gradle/wrapper/gradle-wrapper.properties: missing SHA-256 distribution pin')
    for name, marker in (('android/gradlew', 'gradle/next_app_version.py'),):
        if marker not in texts[ROOT / name]:
            problems.append(f'{name}: missing existing version-preparation integration')

    report = '\n'.join([
        '## Repository integrity', '',
        f'Text files inspected: {files}; repository-local links checked: {links}.',
        f'External/workstation links not verified: {external_links}; excluded links: {excluded_links}.',
        f'Problems: {len(problems)}', '', *[f'- {problem}' for problem in problems], '',
        'Scope: Android production, shared docs and repository configuration in the working tree. Tests, migrations, schemas,',
        'generated contents, private file contents and binary assets are excluded.',
        'Selected credential formats only; no arbitrary-password or Git-history guarantee.',
        'No Gradle, compilation, tests, YAML/action lint, network, or external link verification.',
    ]) + '\n'
    print(report)
    if summary := os.environ.get('GITHUB_STEP_SUMMARY'):
        with Path(summary).open('a', encoding='utf-8') as stream:
            stream.write(report)
    return 1 if problems else 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (OSError, ValueError, KeyError, subprocess.SubprocessError):
        # Parser errors can embed source contents; do not leak them in public logs.
        sys.exit('Repository check incomplete; source details withheld. No pass claimed.')
