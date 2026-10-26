# Architecture

[Implementation/limits](../status/current-state.md) · [Protocol](telegram-protocol.md) · [Roadmap](../status/roadmap.md)

## Accepted synchronization scope

Manually retrieved/published complete Telegram snapshots and reviewed reports; last successful pinned write wins. No automatic sync/remote merge/atomic multi-writer guarantee/required backend. Clinical patient/shift saves are last-save-wins; retained revision columns support bookkeeping, not stale-edit rejection. Review/authority protect publication and privileged registry mutations, not competing remote writes. Timestamps record known events, not freshness. Patient publication retains clinical/registry/delivery guards. A single explicit review may include pending doctor bytes and patients; admin publishes doctors first. Unreviewed later edits remain pending.

## Ownership and local commits

Publications contain the current ward only; older multi-list CSV containers remain readable, with Fetch selecting the newest list. No dated-list picker/copy or previous-publication local-view workflow. Keep device-local Fetch Undo and reviewed admin rollback of the prior remote publication. Existing stored clinical rows are preserved.

08:30 only labels the work day: no automatic new ward/rollover or date-based edit/publish/send gate. Existing ward identities/rows continue across days; captured report dates remain immutable after review.

Screens own presentation/navigation; ViewModels own state/mutations and delegate. Repositories atomically commit clinical changes; `KnownShiftSnapshot`/`PatientPublicationStatus` derive current-ward pending/count/briefing from the same normalized content/order, excluding retired tracking/task metadata; historical wards remain stored without blocking current fetch, and a missing baseline alone does not make an empty roster-free ward pending; new audit recording and clinical-history UI/loads are disabled, with old records retained; patient/doctor sync share singleton store/mutex. Extraction must not duplicate sessions/reviews/artifacts/drafts/locks; desktop facades preserve imports and defer optional cyclic imports. Blocking work off-main; cancellation separate.

Unknown or newer pinned state fails closed rather than becoming an empty state. Immutable doctor wire decodes are cached, while local alias normalization and live actor/authority checks remain fresh. Reviewed publication resolves pending-fetch choices directly; no redundant self-download or new Undo. Snapshot lineage remains bookkeeping/history, and patient revisions also drive saved-card reveal.

Privacy/PIN removes clinical composition but retains signed-in navigation/ViewModels and `SaveableStateHolder`: stable ActivityResult keys require the holder, not just controller. Sign-out discards both; credentials never saveable.

## Access and project boundaries

Protected-file join; manual configuration at creation. Pre-login reconnect validates before replacing credentials, preserves clinical/key/topics and blocks cross-project changes. Sign-out retains enrollment; local PIN supports offline return, first enrollment/shared privileges retain identity checks. BotFather revokes; token holders can bypass client gates.

Android pin override binds reviewed pin/config/live first-admin authority and rechecks before seeding; import only newly seeded reference. No old-data import/history deletion/automatic device transfer/initialized-local replacement; pin checks non-atomic. Desktop requires private challenge/live group admin/empty pin+workspace, with encrypted UNKNOWN before writes. Raw desktop settings stay private. Optional authenticated encryption protects keys, while PDF/metadata stays readable; missing device keys preserve files. Admin checks below UI. [Setup](../guides/telegram-setup.md).

## Clinical and presentation boundaries

Hidden assignment day is business metadata for assignment-PDF highlighting: stamp local creation/actual supervisor changes at Save, preserve on unrelated edits/fetch/restore, compare to captured report day. Nullable date persists in approved DB6/optional CSV7 column; no editor control or author stamp. Publication equality includes it; ordinary report delivery identity excludes it and freezes legacy receipt serialization.

Plain checklist tasks; legacy owner/deadline/priority fields remain compatible wire/storage only. No new completion/editor identity or time stamps; old fields remain readable. Teams independent of patient cover; CSV retains omitted active doctors. Backup/restore never publishes; transactional live responsible-doctor role checks protect Save/recycle/replacement. Restore advances revisions; rejection rolls back clinical/pending state.

Shared fields/tokens standardize presentation; callers own drafts/validation/busy/selection/review. Report review exposes each selected artifact directly and derives opened counts/next preview/send gating from one readiness projection; its fixed primary action advances through Next/Preview/Upload-send, with secondary navigation below. Resident/supervisor pickers always use full-width stacked rows with natural wrapped-value height, preserving the user's font size. RTL/growing48dp/adaptive layouts, clinical-first edit/demographic-first create, labs and fixed keyboard-safe actions. Appearance exposes four accent choices/text size/normal spacing/reduced motion with consistent remaining defaults; A4/A5 remain. Appearance/accent/severity/PDF independent; saved text unchanged. Brief individual/immediate bulk or reduced motion. Stable visible IDs define rounds; buttons supplement gestures. [UI guide](../guides/android-ui.md).

Desktop follows the simplified clinical workflow: two main transfer actions, plain clinical text, four accent families. Report review owns a captured08:30 day across render/confirmation/transport; it never replaces stored snapshot dates/IDs. Daily idle recycle retention works through the existing IO executor, with a grace week for undated entries and no OS service. Retired task-time/audit computation is removed while old encrypted metadata stays compatible.

## Reviewed PDF and delivery identity

Android review captures CSV/text content and exact opened PDF/options/SHA-256; subsequent local edits remain pending, rather than invalidating approved content. Live actor/authority/assignment/destination checks remain; missing/changed PDF file requires preview. Publish before delivery, including subsets. UNKNOWN before report network writes blocks shift/destination retry until authorized resolution or a one-use permit for the same exact artifact. The uncertain receipt remains evidence after extra-copy permission. A pending copy permit restricts that route to the approved content/format key (including its legacy alias). Consuming it durably restores the original UNKNOWN until the copy is acknowledged; failure/interruption leaves the original actionable. Received/not-received resolution is available in delivery history before using the permit and revokes it. In-flight copy/original records cannot be resolved concurrently. Local receipts cannot prove non-delivery or cross-device exactly-once.

Android delivery identity uses an explicit versioned field list and a legacy-key compatibility check for existing receipts. A reviewed report keeps its captured label date for text/PDF day counts. Definite Telegram rejections before any delivered chunk are NOT_SENT; partial delivery stays uncertain. Receipts are not automatically pruned: removing UNKNOWN loses unresolved evidence, while removing SENT can permit duplicates. Supervisor receipt message IDs remain separate from General-chat sent pointers.

Real/demo drawer actions use one exhaustive `WardNavigation.openDrawerAction`; destination callbacks choose live or memory adapters. `AssignmentsExportWorkspace` is shared just like Ward/Report/Doctors workspaces. `PdfAssignmentRenderer` owns the common layout without session/storage/transport dependencies; production `PdfAssignmentExporter` checks current admin identity around rendering, while demo uses only its dummy snapshot and every-page DEMO label. Shared presentation changes propagate to demo automatically; new action/data contracts still need explicit memory behavior.

Android/desktop A5 cards share canvas/fonts/sections and full-page links preserving headers. Full clinical pagination remains; admin A4 names-only labels historical current-roster limits. Standalone clinical Export is draft-only and copies separately opened every-page-labelled files. Android exposes it only on Preview, with one primary Preview→Save action and a collapsed report chooser. Normal copies are an explicit, initially checked choice in the main final-confirmed upload/send flow, performed only after all selected reports have confirmed Telegram delivery and copying their exact previews; no implicit General local save. Desktop general/batch review captures the same optional choice. Local-save failure keeps delivery confirmed, reports a local-only problem and never creates a send retry. Draft mode never publishes or authorizes Telegram delivery. Supervisor/unassigned drafts remain. Admin names-only assignments have a separate side-navigation Preview→Save route, outside both report pages and all publication/delivery gates, with no draft marking or roster requirement. Local admin/data/identity and exact opened bytes remain checked; desktop uses authenticated cached admin authority for this local operation only. Ordinary send retains active roster/authority/exact destination checks.

These checks govern application Save/Share actions. PDF previews remain readable in external viewers, which can offer their own Save/Share; this is workflow guidance and client enforcement, not document copy protection or remote atomicity.

## Isolation and platform choices

Shared Ward/Report/Doctors workspaces depend on controller state/actions; production adapters forward to existing Hilt ViewModels, while demo adapters use one memory-only repository/fake snapshot and receipts. Shared drawer/appearance/forms/cards/readiness/text/PDF prevent separate demo UI drift. Explicit simulation notices and every-page DEMO PDFs; no production store/session/transport or entered identity/PIN retention. Reset/exit discards. Sample scenarios/action adapters remain maintenance points. [Demo](../guides/android-demo.md).

Desktop fingerprint-bound AES-GCM before SQLite, DPAPI/Linux-passphrase keys and platform path/lock/PDF adapters. Logical Arabic input/caret/clipboard/Save separate from shaping; unfinished drafts/cached enrollment/shared authority distinct. [Operations](../../desktop/README.md).

Design basis: Android adaptive/Compose accessibility, I-PASS,NHS warnings,WCAG2.2; rationale, not verification. Voice/templates remain deferred in Roadmap; unselected UI stays in [decisions](../decisions/android-ui-options.md).

Startup renders the intro as `StorageAccessGate` loading content while encrypted access runs on IO. Only successful access constructs repository-backed content; failure remains explicit and never resets stored data. A remembered movable intro is reused under the saved theme after readiness, retaining entrance state and the original monotonic deadline. Tap skip cannot bypass the gate; if storage is still pending the intro remains until access resolves.
