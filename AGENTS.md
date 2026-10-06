# Repository instructions for coding agents

These instructions apply to the entire repository.

## Scope restrictions

- Do not inspect, review, edit, or create migration files unless the user explicitly requests migration work.
- Do not inspect or review test-suite files unless the user explicitly requests test work.
- Do not run Gradle, Android builds, compilation tasks, or packaging tasks unless the user explicitly allows it.
- Do not change the Gradle, Android Gradle Plugin, Kotlin, KSP, Compose, compile SDK, target SDK, or minimum SDK versions unless explicitly requested.
- Preserve the current logo and the About/credits page unless the user explicitly asks to change them.

## Security and clinical-safety invariants

- Never hardcode or commit bot tokens, chat IDs, signing keys, PINs, passphrases, or hospital credentials.
- `legacy-hardcoded-credentials.local.properties`, `GITHUB-GUIDE.local.md`, signing files, and other `*.local.properties` files are intentionally local and ignored.
- Do not replace encryption with encoding, obfuscation, dummy lines, or reversible presentation tricks.
- Do not silently overwrite stale patient or shift data. Preserve expected-revision checks and explicit conflict handling.
- Keep admin authorization below the UI/navigation layer for every privileged mutation.
- Never present Telegram pinned state as an atomic multi-writer database. Preserve the limitation documented in `currentState.md` and `workRemains.md`.
- Do not mix one hospital/project's existing clinical database with another project's credentials. A future reconnect/switch workflow must explicitly protect or clear local clinical state.
- Report sending must retain review/confirmation. Do not turn the ward FAB into an accidental one-tap clinical publication action.

## Architecture conventions

- UI is Jetpack Compose under `ui/`; screens delegate state and mutations to Hilt ViewModels.
- Domain rules belong under `domain/`, persistence behind repositories under `data/repository/`, Telegram access under `network/telegram/`, and synchronization orchestration under `sync/`.
- Keep blocking database, file, cryptographic, and network work off the main thread.
- Prefer one source of truth for shared rules. For example, report readiness is shared through `domain/report/ReportReadiness.kt`.
- Preserve RTL layout, Arabic user-facing text, accessibility labels, semantic states, and 48dp interaction targets.
- Keep demo patients in memory only; demo mode must not write to Room or synchronize.

## Work tracking

- `workRemains.md` contains only unfinished work.
- Move genuinely completed items to `workDone.md`; do not duplicate them in both files.
- Update `currentState.md` when architecture, security boundaries, persistence, synchronization, onboarding, or major navigation changes.
- Keep README build instructions intact unless the user explicitly asks to change them.

## Safe verification

- Always run `git diff --check` after edits.
- Use source-level inspection and narrowly scoped static checks by default.
- If a build is explicitly authorized, do not change toolchain versions merely to make it pass.
- Preserve unrelated user changes in the working tree and avoid destructive Git commands.
