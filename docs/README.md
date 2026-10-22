# ShiftReport documentation

Start with the [project README](../README.md) for capabilities and build commands.

| Need | Document |
|---|---|
| What is implemented, and what is verified? | [Current state](current-state.md) |
| Why these architecture choices? | [Architecture](architecture.md) |
| What remains? | [Roadmap](roadmap.md) |
| Choose optional UI work / answer pending questions | [UI decision register](decisions/android-ui-options.md) |
| Maintain Android shared UI | [UI work guide](../android/chick.md), [Design catalog](design-catalog.md) |
| What was completed? | [Changelog](changelog.md) |
| Diagnose an Android or data problem | [Troubleshooting](troubleshooting.md) |
| Configure Telegram, topics and joining | [Telegram setup — العربية](guides/telegram-setup.md) |
| Use the Windows/Linux desktop client | [Desktop guide](../desktop/README.md) |
| Diagnose the desktop client | [Desktop troubleshooting](../desktop/TROUBLESHOOTING.md) |
| Maintain the shared wire format | [Telegram protocol](../desktop/PROTOCOL.md) |
| Review outcomes and unresolved source findings | [Consolidated Android review](reviews/android-review.md) |
| Cached Android verification / ordinary APK builds | [Workstation build guide](android-shared-workstation-build-fixes.md) |
| Package the Windows client | [EXE guide](windows-exe-packaging.md) |
| Contributor restrictions | [AGENTS.md](../AGENTS.md) |

Keep one home for each topic: current behavior in Current state, rationale in
Architecture, unfinished implementation in Roadmap, and completed summaries in
Changelog. The consolidated review separates completed dispositions from retained source
findings; it is not a second roadmap. Source completion and runtime verification are separate.

Private machine-specific material lives in ignored `docs/local/`; its Git/GitHub
reference is intentionally excluded from shared documentation and packages.
