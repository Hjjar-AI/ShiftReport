# Desktop, interop and live checks

[Results](../status/current-state.md#verification-and-limits) · [Remaining checks](../status/testing-handoff.md) · [Android commands](android-testing.md) · [Device procedures](device-coverage.md)

Coverage: encrypted storage/join/backup,PIN/authority,revisions/receipts,transport faults,file interop,process-kill and seven selected mutants. Not exhaustive branch/mutation/fuzz/hardware coverage or atomic Telegram. Isolated synthetic data; [AGENTS.md](../../AGENTS.md).

## Desktop execution

From repository root, use the existing project venv (or an explicitly selected compatible environment):

```sh
PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=desktop desktop/.venv/bin/python -m unittest discover -s desktop/tests -t desktop -v
```

`test_android_interop` skips without Android artifacts. Run the device interop probe, pull external app files `verification-artifacts/interop/` to ignored local storage, set `SHIFTREPORT_ANDROID_INTEROP` to that folder and rerun. All four reverse checks must execute to claim bidirectional file compatibility. These current-format fixtures already passed; this is not live cross-client GUI evidence.

Regenerate only synthetic committed fixtures, without network access:

```sh
PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=desktop desktop/.venv/bin/python desktop/tests/generate_interop_fixtures.py
```

## Explicit live transport check

Use an owner-designated disposable bot/group and a protected local JSON with `botToken`, `chatId` and optional topic IDs. The harness creates synthetic messages/files using its own index; it never pins/unpins/deletes/replaces the project index and never retries an uncertain send automatically. Inspect the retained receipt and group before repeating an incomplete run.

```sh
PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=desktop desktop/.venv/bin/python desktop/tests/live_telegram.py \
  --config local/verification/telegram-test-config.local.json \
  --report local/verification/live-telegram-result.json
```

### Android bootstrap pin override

`LiveBootstrapOverrideTest` skips unless `SHIFTREPORT_LIVE_BOOTSTRAP_CONFIG` points to a protected JSON containing `botToken`, `chatId`, `adminTelegramId`. This probe mutates pins in the designated disposable group: it creates synthetic pins, rejects a changed pin, seeds/downloads a reviewed registry, unpins only its created messages and checks restoration of the original active pin. Messages/files remain. The private `live-bootstrap-receipt.txt` beside the config records cleanup; inspect it before repeating an uncertain run. No installed clinical database is used.

After explicit build/live-test permission and resource checks, from `android/` using the `srgradle` helper in [Android tests](android-testing.md#run-deliberately):

```sh
SHIFTREPORT_LIVE_BOOTSTRAP_CONFIG="/absolute/path/to/protected-live-config.local.json" \
  srgradle :app:testDebugUnitTest \
  --tests 'com.hos.rushdpatients.domain.auth.LiveBootstrapOverrideTest'
```

Live harness/seeder successes already exist; they do not establish desktop live creation/export workflows, two physical phones or BotFather revocation. Record new evidence in Current state and retain private receipts; never copy real credentials, patient files or the original private CSV into committed fixtures.
