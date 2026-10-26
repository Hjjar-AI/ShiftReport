# Examples only — not working credentials

Connect from Excel using a password-protected `project.srjoin.json` exported by a current app administrator. Its password is entered in the Windows masked prompt and is never written to cells.

| Item | Example |
|---|---|
| Protected join file | `C:\ShiftReportExcel\private\project.srjoin.json` |
| Join password | `<enter interactively; do not save>` |
| Legacy bot token | `<legacy-token-reference-only>` |
| Legacy group ID | `<legacy-group-reference-only>` |

Real files belong in `private/`, which is ignored by Git. Legacy settings are reference material and are never read by the new VBA transport. Never paste connection credentials into patient/report sheets.
