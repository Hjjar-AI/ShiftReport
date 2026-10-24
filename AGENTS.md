# Agent rules

Repository-wide; read nested `AGENTS.md`, relevant source and current work documents first. Current-session owner instructions/authorization take precedence.

## Owner and scope

- “Continue”/“do P…” means implement actual unfinished roadmap/source work. After interruption inspect the tree and resume missing steps; preserve unrelated edits. Ask only if intent remains unclear.
- Keep changes practical and focused on this Telegram app; no speculative redesign, backend, legacy conversion or edge-case backlog. No delegation without explicit owner request.
- Continue authorized reversible work; reuse approval for the same session/scope. Keep updates/results concise: outcome, checks, material limits. Read narrow ranges, batch independent lookups, keep full tool output local; avoid repeated history/checks.
- Never inspect/review/edit/create migration files without explicit migration authorization. Fresh projects are normal; do not invent conversion work.
- **Before builds/compilation/extensive tests:** describe commands/scope and obtain explicit approval unless already authorized for that scope. Editing/continuing alone is insufficient; supplied build failures authorize source fixes, not another build.
- **Tests:** inspection/review/execution/modification require explicit test authorization; creating test files requires explicit approval for creation. Running existing tests or approving builds does not authorize test creation/modification. Consolidate shared fixtures without weakening actor/authority or failure coverage.
- Do not change app/database/interchange/toolchain versions without explicit authorization: Gradle, AGP, Kotlin, KSP, Compose or SDK levels included. Preserve logo, white-field emblem and About/credits.
- If available five-hour usage reporting reaches ≤20%, finish underway steps and stop. Never stage/commit or use destructive Git commands unless requested.

## Build and device discipline

- Before **every** compile/build check visible compiler/IDE/build activity across projects/accounts and available RAM/swap pressure against configured heaps. Idle daemons/cached RAM alone are not pressure. Defer if active, pressured or visibility unreliable; report reason/manual command. Never stop others' builds, kill their processes or clear their caches.
- Preflight installed Java, pinned Gradle, SDK/build tools and durable dependencies. Use narrow offline debug compile first; prefer compile/resources over APK packaging. Set `-Pshiftreport.autoVersion=false`; do not export/distribute by assumption. Keep local full logs and concise actionable results; do not rebuild a successful unchanged scope.
- Check the existing project environment (`desktop/.venv`) before declaring dependencies absent. Install missing prerequisites once in an authorized durable environment; no repeated temporary toolchain downloads or unrequested version changes.
- Native UI: wait for navigation/search results to settle, inspect the actual target, then tap. A timeout is not an import failure; collect the visible error/result. Respect screenshot/device coordinate scaling. Do not run competing UiAutomator sessions during instrumentation.
- Physical interaction needs owner availability; honor requested skips (fingerprint success was skipped this run). Use isolated dummy data/disposable projects for authorized device/live checks; remove only temporary files created for that check.
- Distinguish source inventory, executed cases, passes and skips; overlapping reruns are not additional distinct coverage. Builds apply only to their source snapshot; static checks do not establish compilation, accessibility or device behavior.

## Security and clinical invariants

- Never hardcode/commit credentials, bot tokens, chat IDs, signing keys, PINs or passphrases; exclude them from logs, saved UI state, clinical exports and draft/conflict summaries. `docs/local/github-guide.local.md`, signing files and `*.local.properties` stay private/ignored; runtime project configuration owns connection credentials.
- Preserve authenticated encryption, explicit decryption errors and protected keys; encoding/obfuscation is not encryption. Telegram encryption is chosen at creation; protected settings/join files carry its shared key. Readable PDFs are deliberate. Token replacement must preserve that key.
- Join is password-protected `.srjoin.json` only; manual group/topics belong to creation. Manual same-bot token replacement requires live admin authority. Reconnect remains accessible before login, validates candidates before replacing live credentials, and retains clinical data, pending edits, topics and key. Block cross-project/key/topic substitution; never clear records implicitly. BotFather revokes tokens; local replacement does not. Redistribute new protected join files.
- Preserve expected-revision/conflict checks and admin authorization below UI for privileged mutations. Never silently overwrite stale patient/shift data.
- Telegram remains retrieve/edit/review/publish, complete snapshots, last-successful-write-wins; no atomic multi-writer claim or required collaboration backend. Preserve [architecture](docs/reference/architecture.md) and [current scope](docs/status/current-state.md).
- Sending requires review/final confirmation; the ward FAB must not publish clinical data in one tap. Retain durable UNKNOWN handling, exact-artifact/destination checks and explicit approval for extra copies.

## Source ownership

- Android root `android/`; source below `app/src/main/java/com/hos/rushdpatients/`. Desktop `desktop/`; shared docs `docs/`; private Excel originals `local/excel/`.
- Compose `ui/` delegates state/mutations to Hilt ViewModels; keep navigation/dashboard/reviews/fields in dedicated files. `domain/` owns rules, `data/repository/` persistence, `network/telegram/` transport, `sync/` orchestration. `DoctorSyncCoordinator` owns doctor sync; patient/doctor writes share singleton `RemoteSyncStateStore` and mutex.
- Database/file/crypto/network work stays off main; handle coroutine cancellation separately from ordinary errors. Shared-data changes must cover model/persistence/sync/backup/conflicts/counts/filters/text/PDF consistently; schema authorization still applies.
- Use `domain/report/ReportReadiness.kt` and `domain/task/PatientTasks.kt` as rule owners. Tasks are explicit records, never parsed from follow-up; overdue ⊂ pending. Preserve persistence/sync/backup/report, expected revisions, repository-stamped completion actor/time and unfinished rollover. New tasks unassigned; optional ownership never blocks Save/readiness. No acknowledgment workflow.
- Demo clinical/settings state stays in memory: no Room or sync. Reuse real offline forms/cards. Explicit dummy PDF export is allowed with DEMO on **every page**; beverage patients/tree doctors.

## Product rules

- Arabic, first-layout RTL, labelled ≥48dp targets, wrapping/semantics at large fonts and fold-safe placement. Compact navigation/drawer; hospital/app and user/shift in a short two-column header. Activity Center is admin-only everywhere; retain «إرسال تقرير» and reviewed report flow.
- Frequent actions stay accessible; group secondary actions, avoid crowding/gaps, keep details in compact expandable sections. Sorting updates stored positions and agrees across exports.
- Full-screen editing: clinical/tasks first, demographics last; creation: demographics first. Labs/per-field tools retain one draft, fixed keyboard-safe Save/Cancel. No restored global entry mode. Numeric admission/PIN keyboards, «أو» between birth year/age, diagnosis-specific hints, task-time picker.
- Shared typography/shapes/`UiSpacing`; appearance independent of accent, severity through `patientBadgeColors`, diagnosis never error-colored. Brief individual expansion; immediate bulk/reduced motion. Keep individual/top-bar expand controls, card task counts and dashboard totals; collapsed zero/routine omissions never hide expanded clinical content.
- Report preview starts with actionable shift-doctor selection, even with no roster. No fixed roster cap; preserve active-doctor/stale-edit checks. Uncommon PDF controls stay Advanced. Omit routine/unassigned labels and patient last-edit metadata without losing tasks/content.
- Android/desktop cards retain shared A5 geometry/fonts/sections and full-page index/patient navigation so headers remain visible. Check matching fixtures before claiming parity; disclose renderer differences rather than claiming pixel identity.
- Splash: five-second timer continues while held; expired hold dismisses on release, quick tap skips, recreation retains deadline. Preserve branding/credits.

## Documentation and evidence

- Keep Markdown extremely compact: one topic/home, concise tables/rules, no duplicated chronology or stale pending claims. Preserve working commands, required safety/design details and private source-bearing originals; do not rewrite embedded VBA as prose.
- [Roadmap](docs/status/roadmap.md): unfinished implementation only, plus **Far future** voice and **Far far future** specialty templates with technology/design notes. No verification-only/edge-case backlog. Completed work → [changelog](docs/status/changelog.md); unselected proposals → [decisions](docs/decisions/android-ui-options.md). Removing a proposal is a scope decision.
- [Current state](docs/status/current-state.md): current behavior, security/persistence/sync/onboarding/navigation and exact evidence/limits. [Testing handoff](docs/status/testing-handoff.md): single remaining-check/issue list. Architecture owns rationale; troubleshooting owns diagnostics. Keep README build commands intact.
- Retain current useful summaries/results, necessary XML/receipts, reusable scripts/guides and selected current screenshots/PDFs. Delete redundant older success logs, generated noise and old visuals; remove failed-run evidence only once a later applicable pass proves resolution. Preserve unresolved failures, unique live receipts, private configuration and originals. Record pruning/replacement evidence locally; never delete unrelated phone/private files.
- Always `git diff --check`; also `git diff --cached --check` when staged changes exist. Report only checks actually performed and material limits; no repeated successful builds/tests without changed source, failure or unresolved concern.
