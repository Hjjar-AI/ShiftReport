# Desktop troubleshooting

[Setup/workflow](../../desktop/README.md) · [Protocol](../reference/telegram-protocol.md) · [Results/limits](../status/current-state.md#verification-and-limits)

| Symptom | Check / action |
|---|---|
| Startup | Keep all modules together. Windows:CPython3.8.10/Tk/pip/launcher and pinned requirements. Linux:Python≥3.8/Tk/venv/graphical session, Setup_Linux.sh then Start_Linux.sh; select interpreter with PYTHON_BIN. Check existing `.venv` before installing; launchers retain errors. |
| Configuration | Discover protected joins/configured `.env` beside the source/EXE, or choose among adjacent valid `*.env`/joins. `--env PATH` overrides automatic environment selection; paths resolve beside that file. Invalid auto-discovered envs produce a generic notice and manual join remains available. Literal settings only; [example](../../desktop/.env.example). Join/password default; explicit raw mode needs exact credentials/key, blank JOIN_FILE and live admin before workspace access. No password/env expansion. |
| Offline/validation | Protected join opens saved drafts; edits require Save. Optional clinical fields may be blank; invalid supplied values stay in editor. |
| Decryption | Preserve DB plus Windows `.key`/original profile or Linux `.linux-key`/local passphrase. Join password differs. Never delete/regenerate keys to bypass errors; restore same project/key only. |
| Reconnect | Protected file available before login; candidate must validate live. Other project/key/topics blocked; BotFather revokes token. |
| Login/admin | Published linked doctor, bot Start, private code3min/5attempts; session30min/live mutation checks. Reviewed doctor administration is available on both clients. |
| Create | New empty workspace/pin, complete raw settings, human group admin/private code and bot pin rights. Existing pin blocks desktop creation; no history deletion. |
| Creation UNKNOWN | Inspect registry/index in Telegram, reconnect after confirming correct pin. Never repeat blindly/delete workspace to bypass receipt. |
| Join export | Verified live admin, fresh `.srjoin.json` filename,password≥10; refuse overwrite/changed role. |
| CSV/review | Republish old malformed quoted metadata with current Android; no XLSX pin/schema changes. Zone/08:30 labels the continuous ward day; no archive browser. Changed pointers invalidate review; restore retains ledger, not approval. |
| PDF | Bundled Tajawal Regular/Bold/OFL; inspect unintended PDF_FONT override. Linux xdg-utils/default viewer required; opener failure stops sending. Open exact file before confirming. Readable previews clear on normal exit; crashes may leave them. |
| UNKNOWN delivery | Inspect ledger **and actual destination** before resolving/retrying. Journal match may confirm; absence cannot prove failure. Disk failure after confirmed send does not undo it; extra copy needs approval. Receipts are device-local, not restored draft/Android audit evidence. |
| Arabic | TEXT_RENDERING=auto/shaped on Linux; native only if Tk handles bidi. Restart; logical Unicode/caret/clipboard/Save preserved. Passwords native; readonly copy returns full original text. |
| Linux instance lock | Default XDG_DATA_HOME/ShiftReportDesktop or ~/.local/share/ShiftReportDesktop, even with custom DATA_DIR. Empty file may remain; process exit releases lock. Never remove a held lock to run another instance; use normal local filesystem. |

[Remaining checks](../status/testing-handoff.md): Windows7/DPAPI/packages and complete live GUI acceptance; wheel resolution is not target certification.
