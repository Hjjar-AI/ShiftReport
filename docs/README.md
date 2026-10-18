# ShiftReport documentation

Start with the [project README](../README.md) for capabilities and build commands.

| Need | Document |
|---|---|
| What is implemented, and what is verified? | [Current state](current-state.md) |
| Why these architecture choices? | [Architecture](architecture.md) |
| What remains? | [Roadmap](roadmap.md) |
| What was completed? | [Changelog](changelog.md) |
| Diagnose an Android or data problem | [Troubleshooting](troubleshooting.md) |
| Configure Telegram, topics and joining | [Telegram setup — العربية](guides/telegram-setup.md) |
| Use the Windows/Linux desktop client | [Desktop guide](../desktop/README.md) |
| Diagnose the desktop client | [Desktop troubleshooting](../desktop/TROUBLESHOOTING.md) |
| Maintain the shared wire format | [Telegram protocol](../desktop/PROTOCOL.md) |
| Review unresolved historical findings | [Data-integrity review](reviews/data-integrity.md) |
| Contributor restrictions | [AGENTS.md](../AGENTS.md) |

Keep one home for each topic: current behavior in Current state, rationale in
Architecture, unfinished implementation in Roadmap, and completed summaries in
Changelog. Reviews describe their original source snapshot; they are not a second
roadmap. Source completion and runtime verification are separate.

Private machine-specific material lives in ignored `docs/local/`; its Git/GitHub
reference is intentionally excluded from shared documentation and packages.
