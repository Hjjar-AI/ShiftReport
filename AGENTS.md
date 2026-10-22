# Repository instructions for coding agents

These instructions apply to the entire repository. Before work, check for additional `AGENTS.md` files in the affected directories and read the relevant work/plan documents. Explicit user authorization and preferences from the current session remain applicable.

## Working with the owner

- Treat “continue” and “do P…” as requests to implement the relevant unfinished work. Check the current roadmap and source first; priority labels may have been removed or completed. Ask only when the intended item cannot be determined.
- After an interruption, inspect the working tree and affected source to identify what actually finished. Resume missing steps without discarding edits or repeating completed work.
- Favor practical improvements to the current Telegram app. Do not expand a focused request into a redesign, backend replacement, legacy-support project, or speculative edge-case backlog. Preserve existing safety checks while keeping scope small.
- Continue authorized, reversible work without repeated confirmation. Keep updates and final responses concise; explain what changed, what was checked, and any material limitation.
- Do not delegate to additional agents unless the owner explicitly requests it.
- Minimize chat/token overhead: read relevant source ranges and document sections, batch independent lookups, keep full build/tool logs in local files and report concise outcomes. Avoid rereading long history or repeating successful checks without a changed source/failure.

## Scope restrictions

- Do not inspect, review, edit, or create migration files unless the user explicitly requests migration work.
- Test files may be created only after asking the owner and receiving explicit approval for that test work; reuse approval already given in the current session. Test-suite inspection, review, modification and execution also require explicit test authorization. Build permission alone does not authorize test work.
- Builds/compilation are permitted when useful for the authorized change. Start with a narrow offline debug compile using installed system tools or the existing pinned Gradle distribution and durable caches. Keep full logs in a local file and report only actionable results; avoid repeated builds after a successful relevant check.
- Before every build/compile attempt, check for other active builds or compiler work, including IDE builds and other projects/accounts where visible, and inspect available RAM and swap pressure against the configured build/compiler heap needs. An idle Gradle daemon alone is not an active build; cached memory alone does not establish RAM pressure. Do not start another build while one is active or memory is under pressure. Continue independent work and recheck after a short wait, or finish source work and clearly tell the owner verification was skipped, why, and which command to run manually. If resource availability cannot be checked reliably, report that limitation and leave the build to the owner. Never stop another build, kill its processes or clear its caches to make room.
- Preflight Java, the pinned Gradle distribution, Android SDK/build tools and cached dependencies. Do not repeatedly download toolchains/dependencies into temporary directories for verification. If a prerequisite is missing, identify the exact component and ask the owner to install it once in the durable system/shared cache; continue independent source work.
- Prefer compile/resource checks over APK packaging. Verification builds must disable automatic version reservation (`-Pshiftreport.autoVersion=false`); do not change versions or export/distribute artifacts by assumption. A supplied build log authorizes fixing its source failures and a focused cached verification build under these rules.
- Do not change versions unless explicitly requested, including database or interchange schema versions. Necessary schema changes must be covered by the user's explicit authorization. Do not change the Gradle, Android Gradle Plugin, Kotlin, KSP, Compose, compile SDK, target SDK, or minimum SDK versions unless explicitly requested.
- The owner usually starts a fresh project after edits. Do not add legacy conversion or migration work by assumption; migration work still requires an explicit request.
- When available usage reporting shows the five-hour allowance has 20% or less remaining, finish the steps already underway and stop, preserving the remaining allowance.
- Preserve the current logo and the About/credits page unless the user explicitly asks to change them.

## Security and clinical-safety invariants

- Never hardcode or commit bot tokens, chat IDs, signing keys, PINs, passphrases, or hospital credentials.
- `docs/local/github-guide.local.md`, signing files, and `*.local.properties` files are intentionally local and ignored. The obsolete legacy Telegram properties file was removed; connection credentials belong in runtime project configuration.
- Do not replace encryption with encoding, obfuscation, dummy lines, or reversible presentation tricks.
- Do not silently overwrite stale patient or shift data. Preserve expected-revision checks and explicit conflict handling.
- Keep admin authorization below the UI/navigation layer for every privileged mutation.
- Never present Telegram pinned state as an atomic multi-writer database. Preserve the accepted retrieve/edit/publish and last-write-wins scope documented in `docs/architecture.md` and `docs/current-state.md`; do not introduce a required collaboration backend.
- Joining is file-only through a password-protected `.srjoin.json` file. Connection settings, topic IDs, and the optional shared data key come from that file. Manual group/topic entry belongs only to project creation; manual replacement of the same bot's token requires live admin authorization.
- Optional Telegram-data encryption is chosen when creating a project. Preserve authenticated encryption, the shared key in encrypted device settings/protected join files, explicit decryption failures, and the deliberate readable-PDF exception. Replacing a bot token must not silently replace the project data key.
- Do not mix one hospital/project's existing clinical database with another project's credentials. Same-project reconnect must validate candidate credentials before changing the live token and preserve local clinical state, pending edits, topics, and the data key. Cross-project/key/topic changes are blocked; do not bypass this protection or clear clinical records implicitly.
- Keep file-based reconnect accessible before login. Token revocation belongs to BotFather; do not claim that saving a replacement locally revokes the old token. Updated credentials are distributed through new protected join files.
- Report sending must retain review/confirmation. Do not turn the ward FAB into an accidental one-tap clinical publication action.

## Architecture conventions

- The Android project root is `android/` (open it in Android Studio and run Gradle there); Android source-layer paths below are relative to `android/app/src/main/java/com/hos/rushdpatients/`. The Python PC client is in `desktop/`; shared documentation remains in `docs/`. Private local Excel artifacts are ignored under `local/excel/`.

- UI is Jetpack Compose under `ui/`; screens delegate state and mutations to Hilt ViewModels.
- Keep ward navigation, dashboard, review dialogs, and reusable patient fields in their dedicated files. Doctor synchronization belongs in `DoctorSyncCoordinator`; patient/doctor pinned-state updates share the singleton `RemoteSyncStateStore` and its mutex.
- Domain rules belong under `domain/`, persistence behind repositories under `data/repository/`, Telegram access under `network/telegram/`, and synchronization orchestration under `sync/`.
- Keep blocking database, file, cryptographic, and network work off the main thread.
- Keep credentials and passphrases out of logs, UI saved state, clinical exports, and draft/conflict summaries. Handle coroutine cancellation separately from ordinary failures.
- When changing shared patient data, follow its existing model, persistence, sync/backup, conflict-review, count/filter, and text/PDF paths. Keep the representation consistent; schema changes still need explicit authorization.
- Prefer one source of truth for shared rules: report readiness is shared through `domain/report/ReportReadiness.kt`, and task counts, deadlines, validation, and carry-forward rules through `domain/task/PatientTasks.kt`.
- Tasks are explicit structured records, never inferred from follow-up text. Overdue is a subset of pending. Preserve task persistence, synchronization, backup/report output, expected-revision review, repository-stamped completion identity/time, and unfinished-task rollover. New tasks start unassigned; assigning a doctor is optional. No user acknowledgment workflow is required.
- Keep demo clinical data/settings in memory only; demo mode must not write to Room or synchronize. Reuse real offline forms/cards where practical. The owner permits explicit local PDF export of dummy demo data; label every exported page as a demo. Use hot-beverage patient names and tree-named doctors.

## Product and interface preferences

- Keep Activity Center visible only to admins across drawer, tabs, dashboard, and supporting panes. Keep the report action labelled «إرسال تقرير» and bottom navigation compact while preserving 48dp targets and report review. Patient sorting must update stored positions and agree across exported formats.
- Keep drawer rows compact with 48dp minimum targets, and arrange hospital/app and user/shift details in a short two-column header.
- Optimize frequent actions for quick access. Group secondary drawer/settings actions logically; avoid crowding forms or adding wide vertical gaps. Preserve useful details through compact sections and optional expansion.
- Keep patient forms RTL from their first native/Compose layout. Use numeric keyboards for admission numbers and numeric PINs, «أو» between birth year and age, type-specific diagnosis placeholders, and a picker for task times. PDF output omits unassigned/routine-priority labels and patient last-edit metadata without dropping tasks or clinical content.
- Keep patient editing full-screen with clinical/task sections first and demographic/personal fields last; new-patient entry starts with demographic/personal fields. Any simple/advanced field tools must expose labs and preserve the same draft, with fixed keyboard-safe Save/Cancel controls; do not restore a removed global entry mode by assumption.
- Use shared theme typography, shapes, and `UiSpacing`; keep appearance independent of accent and badge severity mapping shared through `patientBadgeColors`. Diagnosis categories must not use error colors. Keep individual card expansion brief and bulk expansion immediate; retain uncommon PDF options under Advanced. Collapsed summaries may omit zero counts and routine metadata, but full saved clinical content must remain available when expanded.
- Keep per-patient task counts on cards and ward totals on the dashboard. Optional task ownership must not become a required save or report-readiness condition.
- Preserve top-bar expand/collapse-all and individual card expansion, plus an actionable shift-doctor selector as the first report-preview section, available before the roster is set. Do not restore a fixed maximum roster size in the picker, sync, or reports; retain active-doctor validation and stale-edit protection.
- Preserve Arabic user-facing text, RTL layout, labelled 48dp targets, readable wrapping at large font sizes, semantic states, and adaptive/fold-safe placement.
- Keep the splash responsive: keep the five-second timer running while held for reading, defer dismissal until release if the timer has expired, and allow a quick tap to skip, including across Activity recreation. Preserve the white-field emblem and existing About/credit content unless explicitly asked to change them.

## Work tracking

- `docs/roadmap.md` contains only unfinished implementation work and the explicitly deferred voice/template proposals. Do not restore removed verification-only or edge-case backlog items. Record verification evidence and limitations in `docs/current-state.md` and `docs/changelog.md`.
- Move genuinely completed items to `docs/changelog.md`; do not duplicate them in both files.
- Keep history short: consolidate superseded chronological entries into feature summaries; retain current security/behavior and verification limits. Preserve and improve task-oriented guides and their working commands. Store unselected proposals/questions in `docs/decisions/android-ui-options.md`, separate from the implementation roadmap. Removing a proposal is a scope decision, not implementation.
- Update `docs/current-state.md` when architecture, security boundaries, persistence, synchronization, onboarding, or major navigation changes.
- Keep design/architecture rationale in `docs/architecture.md`, implementation details in `docs/current-state.md`, and diagnostic procedures in `docs/troubleshooting.md`. Keep documentation links and historical/current-state distinctions accurate.
- Keep README build instructions intact unless the user explicitly asks to change them.
- Keep voice dictation in “Far future” and specialty templates in “Far far future”, with their technology/design notes. Do not implement deferred proposals unless requested.

## Safe verification

- Always run `git diff --check` after edits.
- If changes are staged, also run `git diff --cached --check`; an empty unstaged diff is not verification of staged edits. Do not stage or commit changes unless requested.
- Use source-level inspection and narrowly scoped static checks by default.
- State the verification actually performed. An earlier successful build applies only to its source snapshot; static checks do not establish compilation, device behavior, or rendered accessibility. Keep these limitations in the status docs rather than creating verification-only roadmap items.
- Do not change toolchain versions to make a build pass. Test-suite inspection/work and migration work still require explicit requests; build permission does not authorize either.
- Preserve unrelated user changes in the working tree and avoid destructive Git commands.
