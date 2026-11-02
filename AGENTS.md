# Agent rules

Repository-wide; owner instructions prevail. Read nested rules, plan/status and relevant source first; verify suggestions and preserve unrelated edits.

## Authorization and execution

- Finish authorized work; inspect/resume after interruptions. “Continue”/“do P…” includes unfinished source/roadmap work. Reuse approval; ask only for missing scope/authority. No speculative redesign/backend/legacy conversion/edge-case backlog/delegation without owner request.
- **Migrations:** explicit approval before inspection/review/creation/change. **Tests:** explicit test approval before inspection/review/run/change; creation needs creation approval. Build approval excludes tests; fixture consolidation preserves authority/failure coverage.
- **Gradle/build/compile/package:** describe commands/scope; obtain explicit approval unless authorized. Supplied failures authorize fixes, not reruns. No version changes (app/database/interchange/toolchain/SDK), staging/commits or destructive Git without request.
- **Every build:** inspect cross-project/account builds/IDEs/compilers and available RAM/swap against heaps. Idle daemons/cache alone are not pressure. Defer for activity/pressure/unreliable visibility; report reason/manual command. Never kill others' work or clear their caches.
- Preflight installed Java/pinned Gradle/SDK/tools/durable dependencies; check `desktop/.venv` before declaring missing dependencies. Install once with approval. Prefer narrow offline debug compile/resources, `-Pshiftreport.autoVersion=false`; no assumed packaging/export/distribution. Keep full logs local; no unchanged successful reruns.
- Native UI: await settled results, inspect, tap; respect screenshot scaling. Timeout ≠ import failure. No competing UiAutomator during instrumentation. Physical interaction needs owner availability; honor skips. Live/device work uses disposable projects/dummy data; delete only owned temporary files.
- Report outcome/checks/limits: source ≠ compile ≠ runtime. Separate inventory/cases/passes/skips; overlapping reruns add no unique coverage. Batch narrow reads. At visible five-hour allowance ≤20%, finish this step and stop; never invent usage.

## Security and data

- No credentials/token/chat IDs/keys/PIN/passphrases in source/logs/saved UI state/clinical exports/draft or conflict summaries. Runtime configuration owns connections; `docs/local/`, signing files, `*.local.properties` stay private/ignored.
- Preserve authenticated encryption, explicit decryption errors/protected keys; encoding is not encryption. Choose at creation; protected join/settings carry shared keys. PDFs stay readable; token replacement preserves keys.
- Join only via password-protected `.srjoin.json`; manual group/topics at creation. Validate pre-login reconnect before replacement; preserve records/pending/topics/key, reject project/key/topic substitution. Same-bot token replacement needs live admin; BotFather revokes, replacement does not. Redistribute protected joins; never implicitly clear records.
- Live identity/admin/protected-registry guards stay below UI. Patient/shift saves: last-save-wins, no revision/conflict prompts; retain existence/shift/deletion/assignment checks. No new audit/editor/completion stamps.
- Telegram: retrieve/edit/review/publish complete snapshots, last-successful-write-wins; no atomic multi-writer/backend claims. Require review/final confirmation, never one-tap ward FAB publication. Preserve active-doctor/exact artifact/destination guards, durable UNKNOWN and explicit extra-copy approval.

## Source ownership

- Android: `android/app/src/main/java/com/hos/rushdpatients/`; desktop: `desktop/`; docs: `docs/`; private originals: `local/excel/` and `excel/private/`.
- Compose `ui/` delegates to Hilt ViewModels; dedicated navigation/dashboard/review/fields. `domain/`: rules; `data/repository/`: persistence; `network/telegram/`: transport; `sync/`: orchestration. `DoctorSyncCoordinator` owns doctor sync; patient/doctor writes share singleton `RemoteSyncStateStore`/mutex.
- Reuse components for repeated behavior/semantics; keep substantial feature sections local. Avoid pass-through wrappers. Existing owners retain drafts/state/launchers/validation/review guards.
- DB/file/crypto/network off-main; cancellation separate. Shared-data changes cover model/persistence/sync/backup/conflicts/counts/filters/text/PDF.
- No initialized old projects: current formats only, no old-app readers/conversions or receipt/theme/identity aliases. Preserve database/wire schema fields/rows and OS/platform support unless authorized.
- `domain/report/ReportReadiness.kt` and `domain/task/PatientTasks.kt` own rules. Tasks are explicit text/done records, never parsed notes. Preserve storage/sync/backup/report and stored ownership/deadline/priority/stamps; no corresponding controls, new attribution/time, overdue/unassigned counters, revision gating or acknowledgment workflow.
- Demo: real offline UI, sample-CSV food/drink patients/tree doctors. Clinical/session settings memory-only, no Room/sync; isolated accent/appearance may persist. Explicit dummy PDFs need DEMO on **every page**.

## Product invariants

- Arabic/123 digits, first-layout RTL, labelled ≥48dp targets, large-font wrapping/semantics, fold safety. Shared typography/shapes/`UiSpacing`; accent independent of appearance/severity (`patientBadgeColors`); diagnosis never error-colored.
- Compact drawer/two-column hospital-app/user-shift header; accessible frequent actions, grouped secondary actions, compact/expandable details. Clinical Activity/history/background audit retired; admin publication history in Sync Center, receipts beside reports. Combined: «رفع للشبكة وإرسال تقرير».
- Continuous ward: 08:30 labels displayed/report day only; no rollover/new ward/date edit lock/publication deadline or dated-list browsing/copying/local previous-publication viewing. Preserve rows/dates/IDs, reviewed captured date, Fetch Undo and reviewed admin rollback. Publish/fetch only the current single-ward CSV bundle.
- Appearance: theme/text size/reduced motion, shared normal-or-comfortable spacing; visible navigation, compact Add, explicit header expansion. Card controls only Ward View Options. Individual expansion brief; bulk/reduced motion immediate. Keep top/individual expand, task counts/dashboard totals/full expanded content.
- PDF: A4/A5, portrait/light, Medium separators/contrast; unsupported saved sizes default/reject; ignore retired overrides. Android A5 cards: always two clinical columns below full-width demographics/badges, right→left, tasks last; no switch. Uncommon controls in Advanced; supervisor split explicit/local, never persistent. Preserve Android/desktop A5 geometry/fonts/sections and full-page index/patient links/headers; parity needs matching fixtures/disclosed renderer differences.
- Announcements/obsolete topic/index fields stay removed. Local exports under Export; Save/Share copies previewed PDF without rerender. Start empty collapsed in Advanced administration. Sort presets manual/name/admission-duration/workbook; stored custom definitions readable, stored order matches exports.
- Fullscreen Edit: clinical/tasks first, demographics last; Add: demographics first. Plain multiline diagnosis/treatment/follow-up; repeatable labs with optional title/date and multiline results. Retain drafts/unfinished inline tasks, fixed keyboard-safe Save/Cancel. Explicit Save; Save and next only with successor; no persistent/global entry mode. Numeric admission/PIN keyboards, «أو» between birth year/age, diagnosis hints. Doctor custom titles storage-only, no editor/display override.
- Reports start with actionable shift-doctor selection, even empty roster; no cap. Omit routine/unassigned/last-edit labels without losing content/tasks.
- Preserve logo, white-field emblem, About/credits. Three-second splash deadline continues during hold; expired hold dismisses on release, quick tap skips, recreation retains deadline.

## Documentation and evidence

- One compact home/topic; no duplicate chronology/stale pending claims. Preserve commands/safety/design/private originals/VBA; [Index](docs/README.md) maps owners.
- [Roadmap](docs/status/roadmap.md): unfinished implementation plus **Far future** voice/**Far far future** specialty templates with design/technology notes; no verification/edge-case backlog. Completed→[changelog](docs/status/changelog.md), unselected→[decisions](docs/decisions/android-ui-options.md); dropping proposals needs a scope decision.
- [Current state](docs/status/current-state.md): behavior/security/persistence/sync/navigation/evidence/limits; [Handoff](docs/status/testing-handoff.md): sole remaining-check list. Architecture: rationale; troubleshooting: diagnostics; README: intact build commands. Plans: remaining work; logs: completion/limits.
- Retain useful results/XML/receipts/scripts/guides/current visuals, unresolved failures/unique live receipts/configs/originals. Prune redundant successes/generated noise/superseded visuals; failed evidence only after a resolving pass. Record replacement/pruning proof locally; never delete unrelated private/phone files.
- Always `git diff --check`; also `git diff --cached --check` when staged. Claim only performed verification.
