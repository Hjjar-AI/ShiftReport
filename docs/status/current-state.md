# Current implementation

Updated 1 November 2026. [Architecture](../reference/architecture.md) · [Protocol](../reference/telegram-protocol.md) · [Remaining checks](testing-handoff.md) · [Roadmap](roadmap.md).

## Data and synchronization

Arabic offline-first Android/desktop handover: Compose/Hilt with encrypted Room; Python/CustomTkinter with encrypted SQLite. Current formats only: protected join, registry/index and single-ward 23-column CSV bundle. Existing fields/rows remain. Android database 7 relaxes unique ward-date and historical doctor-name indexes; IDs remain identity and Telegram accounts stay unique.

Manual Fetch reads the current Telegram pin and completely replaces local patients/doctors, including pending edits. It validates the final pair and saves one encrypted pre-Fetch Undo, restoring data and selection **locally only**. Doctor-only Fetch validates retained clinical references. No merge or older-message gate. Last successful index write wins; Telegram supplies no atomic multi-writer guarantee. Publication requires review, final confirmation and live authority checks.

One continuous ward; explicit IDs and stored dates survive. 08:30 changes displayed/report day only. No rollover, dated browsing or deadline. Clinical saves use last-save-wins with existence/deletion/ward/assignment checks. Tasks are explicit text/done records; current hidden metadata remains.

## Security and recovery

Join uses password-protected `.srjoin.json`. Encryption is chosen at creation; reconnect validates project/key/topics and preserves records. Missing keys preserve files for recovery. PDFs are readable; secrets stay out of logs, saved UI and exports.

Telegram login uses a private six-digit code, memory-only salted hash, five-minute expiry and five attempts; no shared update-queue polling. Offline PIN entry requires device enrollment for the current linked identity/hash. Live admin/identity and privileged-account guards remain below UI. PIN cooldown is 5/15/60 minutes; default auto-lock is five minutes.

## Clinical write safety

Drafts retain all form/unfinished task text. Unreadable drafts block autosave until recovery. Save and draft deletion commit together; late autosaves cannot recreate discarded drafts. Assignment roles are checked transactionally. Fetch/Undo preserve assignment dates. Backup restore never publishes; verified exported files survive later bookkeeping failure/cancellation. Deleted patients remain recoverable for at least seven days.

## Reports and exports

Select doctors/destinations → review exact previews → confirm publication/delivery. No roster cap or one-tap send. Actor, project, destination, content and PDF bytes are rechecked. UNKNOWN remains durable; extra-copy approval binds the exact artifact/topic/linked actor. Save/Share copy reviewed bytes without rerendering; optional local copies follow all confirmed deliveries. Standalone Export is previewed DRAFT; admin assignments have a separate local route. PDFs support A4/A5 portrait/light, captured dates and index links; default A5 cards.

## Shared Android presentation

Arabic/123 digits, RTL, labelled wrapping targets and shared spacing/typography. Add starts with demographics; Edit with clinical fields/tasks. Save/Cancel remain keyboard-safe. Card controls live in Ward View Options. Appearance defaults to system; accent is independent. Reduced-motion/bulk expansion is immediate. Splash keeps its five-second deadline during holds and dismisses an expired hold on release/recreation. Original emblem/About remain.

Demo uses production offline forms/renderers with memory-only dummy data; every dummy PDF page says DEMO. Desktop retains its encrypted local and reviewed Telegram workflows.

## Verification and limits

1 November: production/JVM/device sources compiled; 246 JVM methods and 88 phone methods passed (latest result per method across focused runs; reruns counted once, no skips). Phone: PPA-LX2/Android 10, isolated verification packages and dummy/intercepted transport. Checks include encrypted 6→7 row-preserving migration, coupled replacement/local Undo, account reassignment, publication ordering, enrollment/privileges, drafts, backups/preferences, exact-copy guards and splash. Static source/resource and Git whitespace checks passed.

The isolated verification app also cold-launched into the dummy ward; its activity remained resumed and the process crash buffer was empty. UI hierarchy inspection passed; screenshot capture returned no bytes. Only verification packages were installed/opened by this run; no normal-package install/uninstall or clinical-data command was issued. The normal package seen earlier is absent from the final inventory. No live Telegram sends were performed.

Evidence: `local/verification/android-review-fixes-2026-11-01/`. These focused checks do not establish live Telegram/two-device integration, full-suite acceptance, renderer parity, physical printing, Windows packaging or release signing. Historical results apply to their recorded sources. Commands remain in the [build](../guides/android-build.md) and [testing](../guides/android-testing.md) guides.
