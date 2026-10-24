# Architecture

[Implementation/limits](../status/current-state.md) · [Protocol](telegram-protocol.md) · [Roadmap](../status/roadmap.md)

## Accepted synchronization scope

Manually retrieved/published complete Telegram snapshots and reviewed reports; last successful pinned write wins. No automatic sync/remote merge/atomic multi-writer guarantee/required backend. Local revisions/review/authority protect local work, not competing remote writes. Timestamps record known events, not freshness. Patient publication retains clinical/registry/delivery guards without publishing unrelated doctor edits.

## Ownership and local commits

Screens own navigation/drafts; lifecycle ViewModels delegate. Repositories atomically commit revisions/audit/pending; patient/doctor sync share singleton store/mutex. Extraction must not duplicate sessions/reviews/artifacts/drafts/locks; desktop facades preserve imports and defer optional cyclic imports. Blocking work off-main; cancellation separate.

Privacy/PIN removes clinical composition but retains signed-in navigation/ViewModels and `SaveableStateHolder`: stable ActivityResult keys require the holder, not just controller. Sign-out discards both; credentials never saveable.

## Access and project boundaries

Protected-file join; manual configuration at creation. Pre-login reconnect validates before replacing credentials, preserves clinical/key/topics and blocks cross-project changes. Sign-out retains enrollment; local PIN supports offline return, first enrollment/shared privileges retain identity checks. BotFather revokes; token holders can bypass client gates.

Android pin override binds reviewed pin/config/live first-admin authority and rechecks before seeding; import only newly seeded reference. No old-data import/history deletion/automatic device transfer/initialized-local replacement; pin checks non-atomic. Desktop requires private challenge/live group admin/empty pin+workspace, with encrypted UNKNOWN before writes. Raw desktop settings stay private. Optional authenticated encryption protects keys, while PDF/metadata stays readable; missing device keys preserve files. Admin checks below UI. [Setup](../guides/telegram-setup.md).

## Clinical and presentation boundaries

Explicit optional-owner tasks; overdue ⊂ pending; completion stamped at Save, never publication. Reviewed rollover into empty target rechecks source/target and assignments. Teams independent of patient cover; CSV retains omitted active doctors. Backup/restore never publishes; transactional live role/task-owner checks protect Save/recycle/rollover/replacement. Restore advances revisions; rejection rolls back clinical/pending state.

Shared fields/tokens standardize presentation; callers own drafts/validation/busy/selection/review. RTL/growing48dp/adaptive layouts, clinical-first edit/demographic-first create, labs and fixed keyboard-safe actions. Appearance/accent/severity/PDF independent; saved text unchanged. Brief individual/immediate bulk or reduced motion. Stable visible IDs define rounds; buttons supplement gestures. [UI guide](../guides/android-ui.md).

## Reviewed PDF and delivery identity

Review binds actor/date/revisions/destination/exact opened artifact/options/SHA-256; missing/changed file requires preview. Publish before delivery, including subsets. UNKNOWN before network blocks shift/destination retry until authorized resolution; extra confirmed copy needs permission. Local receipts cannot prove non-delivery or cross-device exactly-once.

Android/desktop A5 cards share canvas/fonts/sections and full-page links preserving headers. Full clinical pagination remains; admin A4 names-only labels historical current-roster limits. Export/open is separate from sending authority.

## Isolation and platform choices

Demo injects memory-only state into real forms/cards/renderer; explicit every-page-labelled PDF only, reset/exit discards, no production store/session/transport. [Demo](../guides/android-demo.md).

Desktop fingerprint-bound AES-GCM before SQLite, DPAPI/Linux-passphrase keys and platform path/lock/PDF adapters. Logical Arabic input/caret/clipboard/Save separate from shaping; unfinished drafts/cached enrollment/shared authority distinct. [Operations](../../desktop/README.md).

Design basis: Android adaptive/Compose accessibility, I-PASS,NHS warnings,WCAG2.2; rationale, not verification. Voice/templates remain deferred in Roadmap; unselected UI stays in [decisions](../decisions/android-ui-options.md).
