# Agent rules

Repository-wide. Read applicable nested rules, current plan/status and relevant source first. Session owner instructions prevail; preserve unrelated edits.

## Authorization and execution

- “Continue”/“do P…” means finish unfinished source/roadmap work; after interruption inspect and resume. Reuse existing approval; ask only for missing scope/authority. No speculative redesign/backend/legacy conversion/edge-case backlog or delegation without owner request.
- **Migrations:** no inspection/review/creation/change without explicit authorization. **Tests:** no inspection/review/run/change without explicit test authorization; creation needs explicit creation approval. Build approval does not approve test work; fixture consolidation must preserve authority/failure coverage.
- **Gradle/build/compile/package:** describe commands/scope and obtain explicit approval unless already authorized. Supplied failures authorize source fixes, not reruns. No app/database/interchange/toolchain/SDK version changes, staging/commits or destructive Git without request.
- Before **every** build: inspect active builds/IDE/compilers across projects/accounts and available RAM/swap against heaps. Idle daemons/cache alone are not pressure. Defer if active/pressured/visibility unreliable; report why/manual command. Never kill others' work or clear their caches.
- Preflight installed Java, pinned Gradle, SDK/tools and durable dependencies; check `desktop/.venv` before declaring dependencies missing. Install once into an authorized durable environment. Prefer narrow offline debug compile/resources, `-Pshiftreport.autoVersion=false`; no assumed packaging/export/distribution. Keep full logs local; no repeat successful unchanged checks.
- Native UI: wait for settled results, inspect target, then tap; respect screenshot scaling. Timeout ≠ import failure. No competing UiAutomator during instrumentation. Physical interaction needs owner availability; honor skips. Authorized live/device work uses disposable projects/dummy data; remove only owned temporary files.
- Report outcome, actual checks and limits concisely; source ≠ compile ≠ runtime. Separate inventory/cases/passes/skips; overlapping reruns are not distinct coverage. Batch narrow reads, avoid repeated history. If visible five-hour allowance reaches ≤20%, finish current step and stop; never invent usage.

## Security and data

- No credentials/token/chat IDs/keys/PIN/passphrases in source, logs, saved UI state, clinical exports or draft/conflict summaries. Runtime configuration owns connections; `docs/local/`, signing files and `*.local.properties` remain private/ignored.
- Preserve authenticated encryption, explicit decryption errors/protected keys; encoding is not encryption. Encryption chosen at creation; protected join/settings carry the shared key. PDFs deliberately readable; token replacement preserves key.
- Join only via password-protected `.srjoin.json`; manual group/topics at creation. Pre-login reconnect validates candidates before replacement, preserves records/pending/topics/key and blocks cross-project/key/topic substitution. Manual same-bot token replacement needs live admin; BotFather revokes, local replacement does not. Redistribute protected join files; never implicitly clear records.
- Live identity/admin and protected registry checks stay below UI. Clinical patient/shift saves: last-save-wins, no revision/conflict prompt; preserve existence/shift/deletion/assignment checks. No new audit/editor/completion stamps; legacy fields/rows remain compatible without migration.
- Telegram: retrieve/edit/review/publish complete snapshots, last-successful-write-wins; no atomic multi-writer/backend claim. Sending requires review/final confirmation, never one-tap ward FAB publication. Preserve durable UNKNOWN, exact artifact/destination checks and explicit extra-copy approval.

## Source ownership

- Android: `android/app/src/main/java/com/hos/rushdpatients/`; desktop: `desktop/`; docs: `docs/`; private originals: `local/excel/` and `excel/private/`.
- Compose `ui/` delegates to Hilt ViewModels; dedicated navigation/dashboard/review/field files. `domain/` rules, `data/repository/` persistence, `network/telegram/` transport, `sync/` orchestration. `DoctorSyncCoordinator` owns doctor sync; patient/doctor writes share singleton `RemoteSyncStateStore`/mutex.
- DB/file/crypto/network work off-main; cancellation separate. Shared-data changes cover model/persistence/sync/backup/conflicts/counts/filters/text/PDF; schema authorization still required.
- `domain/report/ReportReadiness.kt` and `domain/task/PatientTasks.kt` own rules. Tasks are explicit text/done records, never parsed notes. Preserve storage/sync/backup/report and legacy ownership/deadline/priority/stamp fields; no corresponding controls, new attribution/time, overdue/unassigned counters, revision gating or acknowledgment workflow.
- Demo: memory-only clinical/settings, no Room/sync; reuse real offline forms/cards, beverage patients/tree doctors. Explicit dummy PDF allowed with DEMO on **every page**.

## Product invariants

- Arabic/123 digits, first-layout RTL, labelled ≥48dp targets, large-font wrapping/semantics and fold safety. Shared typography/shapes/`UiSpacing`; accent independent of appearance/severity (`patientBadgeColors`); diagnosis never error-colored.
- Compact drawer/navigation; short two-column hospital/app and user/shift header. Frequent actions accessible, secondary actions grouped, details compact/expandable. Clinical Activity/history/background audit retired; admin publication history in Sync Center, receipts beside reports. Combined action: «رفع للشبكة وإرسال تقرير».
- Continuous ward:08:30 labels displayed/report day only; no rollover/new ward/date edit lock/publication deadline. Preserve stored dates/IDs and reviewed captured date. No dated-list browsing/copying/local previous-publication viewing. Publish current ward only; read old multi-list CSV and fetch newest. Preserve stored rows, Fetch Undo and reviewed admin rollback of prior publication.
- Appearance: theme/text size/normal-or-comfortable spacing/reduced motion; navigation visible, Add compact, header expansion explicit. Card controls only Ward View Options. Individual expansion brief; bulk/reduced motion immediate. Keep top/individual expand, task counts/dashboard totals; collapsed omissions never hide expanded content.
- PDF: A4/A5 only (legacy A3/Letter→A4), portrait/light, Medium separators/contrast; ignore retired saved overrides. Uncommon controls Advanced; supervisor split is explicit local export, no persistent switch. Android/desktop A5 geometry/fonts/sections and full-page index/patient links preserve headers; matching fixtures required for parity claims, disclose renderer differences.
- Announcements retired; legacy topic/index fields compatibility only. Local exports under Export; ordinary Save/Share copies previewed PDF without rerender. Start empty stays collapsed under Advanced administration. Sort presets manual/name/admission-duration/workbook; old custom definitions readable, stored order agrees with exports.
- Fullscreen Edit: clinical/tasks first, demographics last; Add: demographics first. Plain multiline diagnosis/treatment/follow-up/labs, retained drafts including unfinished inline task text; fixed keyboard-safe Save/Cancel. Explicit Save/Save and next only with successor; no persistent/global entry mode. Numeric admission/PIN keyboards, «أو» between birth year/age, diagnosis-specific hints. Doctor custom titles compatibility storage only, no editor/display override.
- Report starts with actionable shift-doctor selection even with empty roster; no roster cap. Preserve active-doctor/exact reviewed artifact/destination checks. Omit routine/unassigned/last-edit labels without losing content/tasks.
- Preserve logo, white-field emblem, About/credits. Splash deadline5seconds continues during hold; expired hold dismisses on release, quick tap skips, recreation retains deadline.

## Documentation and evidence

- One compact home/topic; no duplicated chronology or stale pending claims. Preserve commands, safety/design details and private source-bearing originals/VBA. [Index](docs/README.md) maps owners.
- [Roadmap](docs/status/roadmap.md): unfinished implementation only plus **Far future** voice/**Far far future** specialty templates with design/technology notes; no verification/edge-case backlog. Completed→[changelog](docs/status/changelog.md), unselected→[decisions](docs/decisions/android-ui-options.md); dropping proposals needs a scope decision.
- [Current state](docs/status/current-state.md): behavior/security/persistence/sync/navigation and exact evidence/limits. [Handoff](docs/status/testing-handoff.md): sole remaining-check list. Architecture owns rationale; troubleshooting diagnostics; README build commands stay intact. Plans contain remaining work, logs completed work/limits.
- Retain useful results/XML/receipts/scripts/guides/current visuals. Prune redundant successes/generated noise/superseded visuals; failed evidence only after an applicable resolving pass. Preserve unresolved failures, unique live receipts/configs/originals; record pruning/replacement proof locally. Never delete unrelated private/phone files.
- Always `git diff --check`; also `git diff --cached --check` if staged changes exist. No unperformed verification claims.
