# Repository instructions for coding agents

These instructions apply to the entire repository. Before work, check for additional `AGENTS.md` files in the affected directories and read the relevant work/plan documents. Explicit user authorization and preferences from the current session remain applicable.

## Scope restrictions

- Do not inspect, review, edit, or create migration files unless the user explicitly requests migration work.
- Do not inspect or review test-suite files unless the user explicitly requests test work.
- Do not run Gradle, Android builds, compilation tasks, or packaging tasks unless the user explicitly allows it. Small Python scripts and simple tools for source inspection, development, and debugging are allowed; they do not authorize builds or test-suite work.
- Do not change versions unless explicitly requested, including database or interchange schema versions. Necessary schema changes must be covered by the user's explicit authorization. Do not change the Gradle, Android Gradle Plugin, Kotlin, KSP, Compose, compile SDK, target SDK, or minimum SDK versions unless explicitly requested.
- The owner usually starts a fresh project after edits. Do not add legacy conversion or migration work by assumption; migration work still requires an explicit request.
- When available usage reporting shows the five-hour allowance has 20% or less remaining, finish the steps already underway and stop, preserving the remaining allowance.
- Preserve the current logo and the About/credits page unless the user explicitly asks to change them.

## Security and clinical-safety invariants

- Never hardcode or commit bot tokens, chat IDs, signing keys, PINs, passphrases, or hospital credentials.
- `legacy-hardcoded-credentials.local.properties`, `GITHUB-GUIDE.local.md`, signing files, and other `*.local.properties` files are intentionally local and ignored.
- Do not replace encryption with encoding, obfuscation, dummy lines, or reversible presentation tricks.
- Do not silently overwrite stale patient or shift data. Preserve expected-revision checks and explicit conflict handling.
- Keep admin authorization below the UI/navigation layer for every privileged mutation.
- Never present Telegram pinned state as an atomic multi-writer database. Preserve the accepted retrieve/edit/publish and last-write-wins scope documented in `designAndArchitecture.md` and `currentState.md`; do not introduce a required collaboration backend.
- Joining is file-only through a password-protected `.srjoin` file. Connection settings, topic IDs, and the optional shared data key come from that file; manual connection entry belongs only to project creation.
- Optional Telegram-data encryption is chosen when creating a project. Preserve authenticated encryption, the shared key in encrypted device settings/protected join files, explicit decryption failures, and the deliberate readable-PDF exception. Replacing a bot token must not silently replace the project data key.
- Do not mix one hospital/project's existing clinical database with another project's credentials. Same-project reconnect must validate candidate credentials before changing the live token and preserve local clinical state, pending edits, topics, and the data key. Cross-project/key/topic changes are blocked; do not bypass this protection or clear clinical records implicitly.
- Report sending must retain review/confirmation. Do not turn the ward FAB into an accidental one-tap clinical publication action.

## Architecture conventions

- UI is Jetpack Compose under `ui/`; screens delegate state and mutations to Hilt ViewModels.
- Domain rules belong under `domain/`, persistence behind repositories under `data/repository/`, Telegram access under `network/telegram/`, and synchronization orchestration under `sync/`.
- Keep blocking database, file, cryptographic, and network work off the main thread.
- Prefer one source of truth for shared rules: report readiness is shared through `domain/report/ReportReadiness.kt`, and task counts, deadlines, validation, and carry-forward rules through `domain/task/PatientTasks.kt`.
- Tasks are explicit structured records, never inferred from follow-up text. Overdue is a subset of pending. Preserve task persistence, synchronization, backup/report output, expected-revision review, repository-stamped completion identity/time, and unfinished-task rollover. New tasks start unassigned; assigning a doctor is optional. No user acknowledgment workflow is required.
- Keep patient editing full-screen with clinical sections first and demographic/personal fields last; new-patient entry starts with demographic/personal fields.
- Preserve RTL layout, Arabic user-facing text, accessibility labels, semantic states, and 48dp interaction targets.
- Keep demo patients in memory only; demo mode must not write to Room or synchronize.

## Work tracking

- `workRemains.md` contains only unfinished implementation work and the explicitly deferred voice/template proposals. Do not restore removed verification-only or edge-case backlog items. Record verification evidence and limitations in `currentState.md` and `workDone.md`.
- Move genuinely completed items to `workDone.md`; do not duplicate them in both files.
- Update `currentState.md` when architecture, security boundaries, persistence, synchronization, onboarding, or major navigation changes.
- Keep design/architecture rationale in `designAndArchitecture.md`, implementation details in `currentState.md`, and diagnostic procedures in `debugging.md`. Keep documentation links and historical/current-state distinctions accurate.
- Keep README build instructions intact unless the user explicitly asks to change them.

## Safe verification

- Always run `git diff --check` after edits.
- Use source-level inspection and narrowly scoped static checks by default.
- If a build is explicitly authorized, do not change toolchain versions merely to make it pass.
- Preserve unrelated user changes in the working tree and avoid destructive Git commands.
