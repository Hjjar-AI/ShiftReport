# Deep regression checks

[Android suite](android-testing.md) · [Current results](../status/current-state.md#verification-and-limits)

Use synthetic records and isolated storage. Automated success describes the
specific scenarios exercised; it does not establish complete branch coverage,
physical biometric success, or distributed transactions over Telegram.

## Coverage by failure boundary

| Area | Automated checks | Limits that require separate evidence |
|---|---|---|
| Destructive empty start | Review invalidation; live/published authority; upload, cancellation, missing receipt and pinned-write failures; racing edits; audit-trigger rollback; account/settings/receipt preservation | Power loss during every network/local boundary is not simulated by exception injection |
| Process termination | Dedicated debug service dies before/after a real Room transaction; Python child exits before/after a SQLite commit | These are transaction durability checks, not complete process-death coverage of publication/restore/Undo |
| Long clinical sequences | Save, task completion, CSV/report/PDF content, backup, download Undo, restore, stale edit rejection and a subsequent valid edit | Real multi-device sequences remain separate from local pipeline assertions |
| Transaction consistency | SQLite-trigger failures verify patient revisions, audit and pending state roll back together; failed reset audit preserves clinical/settings state | Simulated disk-full errors do not reproduce every filesystem/device failure |
| Production workflows | Ward patient actions, login ViewModel and settings backup operations cover busy/error/cancellation, debounced drafts, repeated actions and saved metadata | These do not replace a complete navigation end-to-end test |
| Doctor import | Production ViewModel reads a ContentProvider, previews 33 rows and commits through the real repository; denied, malformed, oversized and cancelled paths | Optional owner CSV stays private; a provider URI does not reproduce every system-picker/provider combination |
| Desktop | Real encrypted SQLite, backup/join/cipher, PIN lockout/identity, receipt durability, authority/membership, reviewed publication and transport failure suites | Windows DPAPI, native GUI events and hardware behavior need Windows/device execution |
| Cross-client files | Production Python creates fixtures consumed by Android cipher/join/backup; Android emits files consumed by Python | Current-format fixtures only; no schema conversion work is implied |
| Input robustness | Deterministic generated Arabic/Unicode/quote/newline cases, duplicate/missing columns, negative revisions, unclosed quotes, incomplete tasks and bundle-delimiter rejection | Not an exhaustive fuzzing campaign or formal resource-exhaustion proof |
| Privacy | Password/byte cleanup, opaque saved export IDs, encrypted workspace/staging, temporary-file cleanup and transport errors that omit credentials | Physical screenshot/recents behavior, background clipboard restrictions and every log path require additional device review |
| Telegram | Offline intercepted failure suites plus an explicitly invoked synthetic live harness using two independent clients | Harness edits its own synthetic index, not the project's pinned index; physical second-device UI and BotFather revocation are not exercised |
| PDF | Clinical content preservation plus native rasterization of classic/A5 pages | Rasterization alone does not prove glyph/clipping correctness for every content/options combination |
| Patient scrolling | Real 200-patient grid, collapsed/expanded large-font interaction, screenshot and frame-duration artifacts | Debug instrumentation timings are not a release performance SLA or a before/after speedup measurement |
| Accessibility/appearance | RTL and large-font controls plus retained screenshots for review | Full TalkBack, physical foldables, multiple OS/device versions and cosmetic approval remain device/manual checks |
| Mutation sensitivity | Temporary Python source copies deliberately break deadline, lock and encryption-purpose rules; selected regressions must fail | Three selected mutants do not measure mutation coverage of the whole application |

## Desktop execution

From the repository root, using the installed interpreter and dependencies:

```sh
PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=desktop python3 -m unittest discover -s desktop/tests -t desktop -v
```

`test_android_interop` explicitly skips without Android-generated artifacts. After
the device interop suite, pull `verification-artifacts/interop` from the debug
app's external files directory into ignored local verification storage, then set
`SHIFTREPORT_ANDROID_INTEROP` to that directory and rerun the desktop suite. Check
that all four reverse-interop cases run; a skipped class is not interoperability
verification.

Synthetic desktop fixtures can be regenerated without network access:

```sh
PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=desktop python3 desktop/tests/generate_interop_fixtures.py
```

Only those synthetic fixtures belong in Git. The owner's original CSV and runtime
bot configuration remain ignored and must not be copied into committed fixtures.

## Explicit live transport check

The owner must identify a disposable bot/group before invoking this command. The
configuration is a protected local JSON file with `botToken`, `chatId` and optional
topic IDs. The harness creates synthetic messages/documents, records their IDs,
and never pins, unpins, deletes or replaces an existing project index. It never
retries an uncertain send automatically. Inspect the group and retained report
before repeating a failed/uncertain run.

```sh
PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=desktop python3 desktop/tests/live_telegram.py \
  --config local/verification/telegram-test-config.local.json \
  --report local/verification/deep-tests-2026-10-23/live-telegram.json
```

## Device/manual checks

Record device/OS, exact source snapshot and observed result alongside retained
artifacts. Do not mark these complete from source inspection alone:

- Exercise real biometric success/cancel/failure and background/resume locking.
- Check screenshot/recents protection and clipboard expiry while focused and after
  backgrounding; a replacement user clip must survive cleanup of an old code.
- Navigate patients/editor/report/settings using TalkBack; inspect large-font/IME
  placement, split-screen and fold positions on hardware that supports them.
- Review rasterized PDF pages for Arabic shaping, missing glyphs, table/task
  clipping and page transitions; inspect both first/last and content-heavy pages.
- Review patient-scroll timings against a named device/build baseline; debug
  instrumentation cannot establish release responsiveness by itself.
- Test actual token revocation through BotFather only with explicit owner action;
  saving a new local token does not revoke the old token.
