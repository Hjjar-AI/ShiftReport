# Desktop troubleshooting

[Setup and complete workflow](README.md) · [Wire contract](PROTOCOL.md)

- **Startup:** use CPython 3.8.10 with Tcl/Tk, pip and the Windows py launcher.
  On Linux use Python 3.8–3.11 with Tcl/Tk and venv, run Setup_Linux.sh, then
  Start_Linux.sh in a graphical session. Select an installed compatible interpreter
  with PYTHON_BIN if necessary. Keep all desktop modules together. Setup uses
  binary-only pins in a virtual environment; Python 3.12+ is outside those pins.
  The launchers leave failures visible. Excel is not involved.
- **Configuration:** optional .env accepts only the documented literal settings;
  no shell expansion/interpolation. Default `CONNECTION_MODE=join` uses a protected
  join file and prompts for its password. Optional admin `CONNECTION_MODE=raw`
  uses the commented raw section of [.env.example](.env.example); fill the exact existing credentials and
  data key, leave JOIN_FILE blank, and verify an admin by Telegram before opening
  local drafts. Raw mode needs connectivity; passwords are never `.env` settings.
- **Offline:** saved drafts reopen after opening the protected join file. Form
  changes require Save; incomplete or invalid fields stay in the editor. New
  clinical records require diagnosis, treatment and follow-up, matching Android.
- **Decryption:** preserve the encrypted workspace and, for unencrypted Telegram
  projects, its Windows .key/original profile or Linux .linux-key/local passphrase.
  A wrong passphrase, damaged key or missing protection file stops opening; no
  automatic replacement key is generated for an existing Linux database. Never
  delete files to bypass a decryption error. The restore action accepts only the
  same project/key. Local passphrases are distinct from the join-file password.
- **Reconnect:** use a new protected join file before login. An open session rejects
  cross-project/key/topic changes. Candidate credentials must validate live before
  replacing the client. Token revocation is in BotFather.
- **Login:** publish the current doctor registry from Android, link your Telegram
  identity and press Start on the bot. Codes expire after three minutes/five
  attempts. Verified sessions expire after thirty minutes; mutations recheck live
  membership. Doctor/role administration stays in Android.
- **CSV:** republish old incorrectly quoted metadata using the current Android
  source. Keep schema versions unchanged and do not pin an XLSX in place of JSON.
- **Review:** match Android's timezone and 08:30 boundary. Archived shifts are
  view/export-only. New remote data invalidates prior review; choose every conflict
  explicitly. Restore does not restore approval or roll back the delivery ledger.
- **PDF:** PDF_FONT selects an Arabic-capable TTF; otherwise Windows Arial or
  common Linux DejaVu Sans/Noto paths are used. Install xdg-utils and configure a
  default PDF viewer on Linux. Missing/failed openers stop sending. Inspect the
  actual PDF in the default viewer before confirming delivery. A local
  export is readable. Temporary previews are cleared on normal interpreter exit;
  a crash can leave a readable preview in the system temp directory.
- **UNKNOWN:** inspect the ledger and the actual Telegram destination before retry.
  A matching publication-journal snapshot confirms publication; absence from the
  bounded journal does not prove failure. A confirmed send followed by local disk
  failure is still a send. Mark not-sent only after verifying it did not arrive.
  Confirmed reports require explicit approval for another copy. Other PCs and
  restored draft files do not establish whether a report was sent. Desktop receipts
  do not update Android's local audit/sent UI.

Linux workspaces default to XDG_DATA_HOME/ShiftReportDesktop or
~/.local/share/ShiftReportDesktop. The instance lock is in the default data folder
even when DATA_DIR selects a different workspace; its empty file can remain after
exit, while the lock itself releases on process exit. Do not remove a held lock
file to start a second instance. File-based locks require a normal local filesystem.

Windows 7/DPAPI, graphical Linux/Tk and live Android/Telegram operation remain
unverified on this host. Dependency wheel resolution and source checks are not target certification.
Use this folder and README as the current desktop entry point.

If Arabic labels appear reversed on Linux, keep TEXT_RENDERING=auto (or use shaped).
If a Tk build already performs bidi rendering, use native to prevent applying
layout twice. Restart the app after changing this setting. Clinical edit buffers
remain logical; use the live Arabic readback beneath form fields when native Tk
shows input backwards. A multiline readback follows the current line/caret context.
Copy from the readonly review with its full-text copy action to retain logical
Unicode. Passwords never appear in readbacks.
