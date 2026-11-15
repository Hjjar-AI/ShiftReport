"""Source-only checks of manual hosted workflow safeguards; no workflow execution."""

from pathlib import Path
import re
import sys

from ci_yaml import load_yaml

ROOT = Path(__file__).resolve().parents[2]
PIN = re.compile(r'[\w.-]+/[\w.-]+(?:/[\w./-]+)?@[0-9a-f]{40}')
PUBLIC = '${{ github.event.repository.private == false }}'
CONFIRMED = "${{ inputs.confirm && github.event.repository.private == false && github.ref == format('refs/heads/{0}', github.event.repository.default_branch) }}"
SIGNING_FILES = {'sign-debug.yml', 'sign-release.yml'}
PUBLICATION_CONFIRMED = CONFIRMED[:-3] + " && inputs.publication != 'artifact' }}"


def main():
    errors = []
    def need(value, message):
        if not value:
            errors.append(message)

    def steps(items, context, stack=()):
        need(isinstance(items, list) and bool(items), f'{context}: steps required')
        for step in items:
            need(all(re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]*', name) for name in step.get('env', {})),
                 f'{context}: literal valid shell environment names required')
            for variable, value in step.get('env', {}).items():
                if isinstance(value, str) and 'secrets.' in value:
                    need(context in {'sign-debug.yml/sign', 'sign-release.yml/sign'}
                         and variable.startswith('SHIFTREPORT_SIGN_')
                         and step.get('run', '').startswith('python3 .github/scripts/sign-apk.py '),
                         f'{context}: secrets restricted to isolated signing step')
            need('secrets.' not in str({key: value for key, value in step.items() if key != 'env'}),
                 f'{context}: secrets may only enter the approved signing-step environment')
            if 'run' in step:
                need('${{' not in step['run'], f'{context}: pass expressions through env')
                continue
            action = step['uses']
            inputs = step.get('with', {})
            if action.startswith('./'):
                directory = (ROOT / action).resolve()
                need(directory.is_relative_to(ROOT / '.github/actions') and directory not in stack,
                     f'{context}: local action path/recursion')
                if not directory.is_relative_to(ROOT / '.github/actions') or directory in stack:
                    continue
                local = load_yaml(directory / 'action.yml')
                need(local['runs']['using'] == 'composite', f'{context}: composite action required')
                steps(local['runs']['steps'], action, (*stack, directory))
                continue
            need(PIN.fullmatch(action), f'{context}: full commit action pin required')
            name = action.split('@')[0]
            if name == 'actions/checkout':
                need(inputs.get('persist-credentials') is False, f'{context}: persisted credentials prohibited')
            if name == 'actions/upload-artifact':
                need(type(inputs.get('retention-days')) is int and inputs['retention-days'] == 1,
                     f'{context}: one-day retention required')
                artifact_name = inputs.get('name', '')
                need(all(marker in artifact_name for marker in ('github.run_id', 'github.run_attempt', 'github.sha')),
                     f'{context}: artifact source/run/attempt identity required')
            if name == 'gradle/actions/setup-gradle':
                need(inputs.get('cache-provider') == 'basic', f'{context}: basic cache required')
                need(inputs.get('cache-read-only') == '${{ github.ref_name != github.event.repository.default_branch }}',
                     f'{context}: cache writes restricted to default branch')

    paths = sorted((ROOT / '.github/workflows').glob('*.yml'))
    numbers = [load_yaml(path)['name'].split(' ', 1)[0] for path in paths]
    need(bool(paths) and len(set(numbers)) == len(numbers) and all(re.fullmatch(r'[0-9]{2}', n) for n in numbers),
         'Present workflow display numbers must be unique two-digit values')
    for path in paths:
        data = load_yaml(path)
        name = path.name
        need('secrets.' not in str(data.get('env', {})), f'{name}: workflow environment secrets prohibited')
        need(set(data['on']) == {'workflow_dispatch'}, f'{name}: only manual dispatch allowed')
        need(data['permissions'] == {'contents': 'read'}, f'{name}: read-only workflow token required')
        need(data['concurrency']['cancel-in-progress'] is False, f'{name}: non-cancelling concurrency required')
        if name != 'repository.yml':
            confirm = data['on']['workflow_dispatch']['inputs']['confirm']
            need(confirm['type'] == 'boolean' and confirm['default'] is False,
                 f'{name}: explicit opt-in default false required')
        for input_name, spec in (data['on'].get('workflow_dispatch') or {}).get('inputs', {}).items():
            description = spec.get('description', '')
            values = spec.get('options', []) if spec.get('type') == 'choice' else ['true', 'false'] if spec.get('type') == 'boolean' else []
            need(all(f'{value} =' in description for value in values), f'{name}/{input_name}: explain each exact option')
            need(f'Default: {str(spec.get("default", "blank")).lower()}' in description,
                 f'{name}/{input_name}: describe default explicitly')
        for job_id, job in data['jobs'].items():
            need('secrets.' not in str(job.get('env', {})), f'{name}/{job_id}: job environment secrets prohibited')
            dependencies = job.get('needs', [])
            dependencies = [dependencies] if isinstance(dependencies, str) else dependencies
            need(all(dependency in data['jobs'] for dependency in dependencies),
                 f'{name}/{job_id}: unresolved job dependency')
            guard = PUBLIC if name == 'repository.yml' else CONFIRMED
            if name in SIGNING_FILES and job_id == 'publish':
                guard = PUBLICATION_CONFIRMED
            need(job['if'] == guard,
                 f'{name}: public/default-branch/confirmation guard required')
            need(job['runs-on'] == 'ubuntu-24.04', f'{name}: standard Ubuntu runner required')
            need(type(job['timeout-minutes']) is int and 0 < job['timeout-minutes'] <= 60,
                 f'{name}: bounded timeout required')
            if 'permissions' in job:
                allowed = name == 'security.yml' and job['permissions'] == {'contents': 'read', 'security-events': 'write'}
                if name in SIGNING_FILES:
                    allowed = ((job_id == 'sign' and job['permissions'] == {'contents': 'read', 'actions': 'read'})
                               or (job_id == 'publish' and job['permissions'] == {'contents': 'write', 'actions': 'read'}))
                if name == 'cleanup-evidence.yml':
                    allowed = job_id == 'cleanup' and job['permissions'] == {'contents': 'read', 'actions': 'write'}
                if name == 'cleanup-debug.yml':
                    allowed = job_id == 'cleanup' and job['permissions'] == {'contents': 'write'}
                if name == 'release.yml':
                    allowed = job_id == 'startup' and job['permissions'] == {'contents': 'read', 'actions': 'read'}
                need(allowed,
                     f'{name}: unexpected job permissions')
            if name in SIGNING_FILES and job_id == 'sign':
                need(job.get('environment') == name.removeprefix('sign-').removesuffix('.yml') + '-signing',
                     f'{name}: dedicated signing environment required')
                need(job.get('needs') == 'build' and not any(
                    step.get('uses') == './.github/actions/setup-android' for step in job['steps']),
                     f'{name}: signing isolated from build and Gradle caches')
            if name in SIGNING_FILES and job_id == 'publish':
                need(job.get('needs') == 'sign', f'{name}: publication requires verified signing')
                publication = data['on']['workflow_dispatch']['inputs']['publication']
                need(publication['default'] == 'artifact'
                     and publication['options'] == ['artifact', 'draft', 'prerelease', 'release'],
                     f'{name}: explicit publication choices with artifact default required')
            if name == 'emulator.yml':
                strategy = job.get('strategy', {})
                need(strategy.get('max-parallel') == 1 and strategy.get('fail-fast') is False
                     and strategy.get('matrix') == {'api': "${{ fromJSON(inputs.api == 'both' && '[23,35]' || inputs.api == '23' && '[23]' || inputs.api == '35' && '[35]' || 'invalid-api-selection') }}"},
                     f'{name}: explicit serial API matrix required')
                api_input = data['on']['workflow_dispatch']['inputs']['api']
                need(api_input['type'] == 'choice' and api_input['options'] == ['both', '23', '35']
                     and api_input['default'] == 'both',
                     f'{name}: explicit API selection must preserve the default full matrix')
                runners = [step for step in job['steps'] if step.get('uses', '').startswith('reactivecircus/android-emulator-runner@')]
                need(len(runners) == 1 and runners[0].get('with', {}).get('emulator-port') == 5554
                     and runners[0].get('with', {}).get('script') == 'python3 .github/scripts/run-emulator.py',
                     f'{name}: owned fixed-port capture helper required')
            if name == 'cleanup-evidence.yml':
                inputs = data['on']['workflow_dispatch']['inputs']
                need(inputs['dry_run']['type'] == 'boolean' and inputs['dry_run']['default'] is True
                     and inputs['older_than_days']['default'] == 7
                     and inputs['scope']['options'] == ['all-workflows', 'emulator'],
                     f'{name}: preview/retention/scope defaults required')
                need(job['steps'][0].get('with', {}).get('ref') == '${{ github.event.repository.default_branch }}'
                     and data['concurrency']['group'] == 'cleanup-ci-evidence',
                     f'{name}: trusted default-branch tooling and cleanup serialization required')
            if name == 'cleanup-debug.yml':
                inputs = data['on']['workflow_dispatch']['inputs']
                need(inputs['preview']['default'] is True and inputs['keep']['default'] >= 5
                     and inputs['older_than_days']['default'] == 30,
                     f'{name}: conservative preview/retention defaults required')
                need(data['concurrency']['group'] == 'shiftreport-hosted-android'
                     and job['steps'][0]['with'].get('ref') == '${{ github.event.repository.default_branch }}',
                     f'{name}: serialize delivery/cleanup and use trusted default-branch tooling')
            if name == 'release.yml' and job_id == 'startup':
                runners = [step for step in job['steps'] if step.get('uses', '').startswith('reactivecircus/android-emulator-runner@')]
                need(job.get('needs') == 'check' and len(runners) == 1
                     and runners[0].get('env', {}).get('CI_CAPTURE_KIND') == 'release'
                     and runners[0].get('with', {}).get('emulator-port') == 5554,
                     f'{name}: inspected-release dependency and owned release AVD required')
            if any(step.get('uses') == './.github/actions/setup-android' for step in job['steps']):
                need(data['concurrency']['group'] == 'shiftreport-hosted-android',
                     f'{name}: serialize hosted Android work across workflows')
            steps(job['steps'], f'{name}/{job_id}')
    for error in errors:
        print(error)
    print(f'Workflow policy: {len(paths)} manual workflows reviewed; {len(errors)} problems. Source checks only.')
    return 1 if errors else 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except Exception:
        sys.exit('Workflow policy incomplete; YAML/source details withheld. No pass claimed.')
