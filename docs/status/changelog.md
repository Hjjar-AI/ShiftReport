# Completed work

## Splash before encrypted-storage loading — 25 October 2026

- Show the intro immediately instead of the encrypted-storage progress message while the existing IO gate initializes storage. Repository-backed content still requires success; storage failure/retry and data preservation remain intact. A movable intro preserves animation state when storage becomes ready and saved appearance is applied.
- Refined the entrance with a soft emblem glow/scale, quicker fades, eased settling and staggered credit sections. Reduced-motion handling, five-second launch deadline, tap skip and original logo/credits remain; no timing hint or hold interaction.
- Source startup/animation wiring and Git whitespace checked. Owner-authorized offline `installDebug` compiled/packaged and installed these splash and assignments-PDF adjustments on PPA-LX2/Android10 (6m52s,44tasks:9executed/35up-to-date). Java21/Gradle8.7/SDK34, one worker/in-process Kotlin/2GB, Kotlin/KSP incremental disabled, autoVersion=false; host preflight passed. Logs/receipt `local/verification/splash-first-centered-assignments-install-2026-10-25/`. No tests/app launch/startup timing/animation/rendered PDF/live verification.

## Assignments PDF alignment and text — 25 October 2026

- Android assignments now measure/paginate before drawing and vertically center each page’s complete content block. Slightly larger body/team/resident text (10/11/9.5pt), title and supporting text apply through the shared real/demo renderer; footer/DEMO labels and final-page timestamp remain. Desktop unchanged.
- Source pagination/drawing review and Git whitespace passed. Compiled/installed with the splash-first build above; no tests/rendered PDF or runtime verification.

## Shared demo assignments and navigation — 25 October 2026

- Real and demo sessions now share assignments Preview→Save UI and PDF layout. Demo exposes the same sidebar button and exports dummy names-only `Assignments_YYYYMMDD_DEMO.pdf` with DEMO on every page, without requiring shift doctors or upload. Production keeps current-admin checks around the renderer.
- About now opens the production About/credits screen in demo. One exhaustive drawer dispatcher serves both modes; required assignments callbacks replace the hidden demo button/no-op route. Shared presentation changes flow through automatically; new state/actions still need an explicit memory adapter. Documented the shared-screen rule in the demo guide.
- Demo preview regeneration invalidates opening state; file hashes/current dummy snapshots guard local copying and simulated delivery, with disk reads off-main. Resource XML/string references, source wiring and Git whitespace checked. Owner-authorized offline `installDebug` compiled/packaged and installed on PPA-LX2/Android10 (6m36s,44tasks:11executed/33up-to-date). Java21/Gradle8.7/SDK34, one worker/in-process Kotlin/2GB, Kotlin/KSP incremental disabled, autoVersion=false; host preflight passed. Logs/receipt `local/verification/shared-demo-install-2026-10-25/`. No tests/app launch/runtime isolation/rendered PDF/live verification.

## Admin assignments local export — 25 October 2026

- Moved «تصدير توزيع المرضى (PDF)» to admin side navigation directly below upload/send, opening a separate local Preview→Save screen. Removed assignments from clinical report pages/chooser and the empty-roster report-page bypass.
- Names-only assignments save the opened bytes as `Assignments_YYYYMMDD.pdf` without DRAFT or publication/delivery requirements. Local current admin/data/account/project checks remain; desktop local export avoids fetching/uploading Telegram data and retains authenticated cached admin checks. Other live privileged actions are unchanged.
- Production Python3.8 grammar, resource XML/string references and Git whitespace checked. Owner-authorized offline `installDebug` compiled/packaged and installed on PPA-LX2/Android10 (5m7s,44tasks:17executed/27up-to-date). Java21/Gradle8.7/SDK34, one worker/in-process Kotlin/2GB, Kotlin/KSP incremental disabled, autoVersion=false; host preflight passed. Logs/receipt `local/verification/assignments-sidebar-install-2026-10-25/`. No tests/app launch/runtime verification; desktop source-only.

## Save on device after Telegram delivery — 25 October 2026

- Renamed the optional save choice to «حفظ ملف PDF على ذاكرة الهاتف بعد إرساله إلى تليجرام (اختياري)», checked by default and still captured in final confirmation. Desktop uses corresponding device-storage wording/default.
- Exact local PDF copies now run only after every selected Telegram report is confirmed delivered. Failure/uncertainty/partial delivery skips copying. Local-copy failure/cancellation preserves confirmed delivery, displays a local-save warning and never offers a send retry. Standalone Export remains draft-only.
- Production Python3.8 grammar/default XML/string/whitespace checks passed. Owner-authorized offline assembleDebug passed (4m31s,44tasks:15executed/29up-to-date), then installDebug installed on PPA-LX2/Android10 (33s,1executed/43up-to-date). Java21/Gradle8.7/SDK34, one worker/in-process Kotlin/2GB, Kotlin/KSP incremental disabled, autoVersion=false; host preflight before both. No source fixes/version changes. Logs/APK hash `local/verification/save-after-delivery-build-2026-10-25/`. No tests/app launch/runtime/live verification; desktop remains source-only.

## Draft-only Export and optional normal copies — 25 October 2026

- One Android Export entry, on Preview only, labelled «تصدير مسودة». Removed standalone upload/mode controls. A compact dialog defaults to the general draft with one Preview→Save primary action, an expandable report chooser, optional one-file Share and retained supervisor-batch/unassigned/admin names-only drafts.
- Main reviewed upload/send offers an unchecked optional normal-PDF save, included in final confirmation and applied only after confirmed publication. Removed unconditional General PDF local saving. Exact preview bytes/names and publication/delivery guards remain; local-save failure leaves publication confirmed and delivery unstarted.
- Desktop standalone exports are likewise draft-only; general/supervisor send review owns optional normal copies. Demo preserves memory-only publication and DEMO labels.
- Production Python3.8 grammar/default XML/string/whitespace checks passed. Owner-authorized offline assembleDebug passed (4m49s,44tasks:15executed/29up-to-date), then installDebug installed on PPA-LX2/Android10 (34s,1executed/43up-to-date). Java21/Gradle8.7/SDK34, one worker/in-process Kotlin/2GB, Kotlin/KSP incremental disabled, autoVersion=false; host preflight before both. No source fixes/version changes. Logs/APK hash `local/verification/simple-export-build-2026-10-25/`. No tests/app launch/runtime acceptance; desktop remains source-only.

## Export workflow and report naming debug installation — 25 October 2026

- Owner-authorized offline assembleDebug passed (5m19s,44 tasks:17 executed/27 up-to-date), then installDebug installed on PPA-LX2/Android10 (37s,1 executed/43 up-to-date). Kotlin/javac/Hilt/resources/DEX/package passed, covering official/draft export, descriptive filenames and Telegram doctor-title source.
- Java21/Gradle8.7/SDK34, one worker/in-process Kotlin/2GB, Kotlin/KSP incremental disabled and autoVersion=false. Host activity/memory checked before both invocations. No source fixes required; versions unchanged. Logs/APK hash: `local/verification/export-names-build-2026-10-25/`.
- No tests/app launch/export workflow/PDF visual/live Telegram runtime acceptance; desktop remains source-only.

## Descriptive filenames and Telegram doctor titles — 25 October 2026

- Android/desktop local exports, previews/shares and Telegram PDF uploads use the captured report day: `Report_20261025.pdf`, `Assignments_20261025.pdf`, supervisor-name/Unassigned suffixes and explicit DRAFT/DEMO markers. Internal IDs and random preview filenames no longer become public report names. Multi-file desktop saves and older Android PDF saves use numbered collision suffixes instead of replacing existing exports.
- General/supervisor Telegram PDF captions list the reviewed shift doctors by name; supervisor scope is separate. PDF interior labels stay unchanged. Oversized titles continue as replies to the same PDF; partial title delivery retains uncertainty and blocks automatic resend. Only public PDF title metadata uses that readable continuation route; ordinary clinical encryption remains.
- Selected production Python3.8 grammar, default XML/string references and Git whitespace checks passed. Android subsequently compiled/packaged and installed in the export-naming build above; no tests/rendered PDF/live Telegram verification. Desktop remains source-only.

## Reviewed official exports and local drafts — 25 October 2026

- Android/desktop official local export now guides report selection→opened previews→explicit full-ward upload confirmation→Save/Share (desktop Save). Upload does not send a report; existing final confirmation and uncertainty handling for Telegram report delivery remain.
- Save/Share checks exact reviewed bytes, current report/assignment/team content, account/project and matching confirmed publication below UI. Approval is memory-only; repeat copies remain available for the same confirmed data during that owner lifetime. Supervisor/unassigned/admin names-only exports copy opened previews rather than rendering at Save.
- Offline local drafts use separate previews, DRAFT filenames and an explicit draft label on every page. Demo uses memory-only simulated publication and retains every-page DEMO labels. Empty-roster Android admin names-only drafts remain available.
- Production Python3.8 grammar, default XML/string-reference and Git whitespace checks passed. Android subsequently compiled/packaged and installed in the export-naming build above; no tests/runtime/live/PDF visual checks. Desktop remains source-only. External viewers can still copy readable previews; the application export guard is not PDF copy protection.

## Splash entrance and simpler dismissal — 25 October 2026

- Staggered fade/rise for emblem, title, subtitle and credits, plus subtle emblem scale; settles within one second. Reduced motion displays contents immediately; saved entrance state prevents replay after recreation.
- Removed countdown/timing/gesture footer and hold-to-pause behavior at owner request. Five-second launch deadline still includes loading and survives recreation; tap skips.
- Source/call-site review and Git whitespace checks passed. Owner-authorized offline assembleDebug passed (6m29s,44 tasks:13 executed/31 up-to-date), then installDebug installed on PPA-LX2/Android10 (41s,1 executed/43 up-to-date). One worker/in-process Kotlin/2GB heap/autoVersion=false; Kotlin/KSP incremental processing disabled to resolve stale retired-Announcements generated Hilt output. Restored read-only countdown property for debug caller compatibility; no visible countdown or hold behavior. Earlier failed logs retained beside the resolving pass in `local/verification/splash-a5-2026-10-25/`. No tests/app launch/device visual checks.

## Splash, A5 defaults and patient totals — 25 October 2026

- Removed splash version; existing Help→About remains the version location. Splash credits/logo/timing unchanged.
- Unset Android/desktop PDF preferences now choose A5 cards with linked index; saved choices and legacy A3/Letter→A4 remain. Android options/state defaults also use A5.
- A5 index on both clients and Android report preview show a larger bold total, with psychiatric title/count inline in brackets; desktop classic summary also brackets that subtotal.
- XML parsing, selected desktop Python grammar and Git whitespace checks passed. Android source subsequently compiled/packaged and installed in the splash follow-up above; no tests/app launch/rendered PDF checks. Desktop remains source-only.

## Direct report previews and ordered actions — 25 October 2026

- Shared live/demo report footer has one full-width primary action: Next→next required preview→combined upload/send, with secondary Cancel/Previous below. Every selected General/supervisor report has a direct top-of-review preview button and opened status/count; removed the extra supervisor-preview chooser dialog.
- `ReportFlowGate` supplies the same per-route readiness map to progress/next-preview/send gating. Existing exact-artifact/controller checks, busy guards, destination review and final confirmation remain.
- Production346-file Kotlin delimiter/default XML9-file/string/whitespace checks passed. Owner-authorized offline installDebug compiled/packaged and installed on PPA-LX2/Android10 (5m1s,44tasks:15executed/29up-to-date). No suites/app launch/report UI/PDF/live-delivery runtime verification. Evidence `local/verification/report-actions-2026-10-25/`.

## Stacked patient doctor pickers — 25 October 2026

- Resident/supervisor pickers always occupy full-width stacked rows in the shared Add/Edit form, including demo. Removed forced equal-height pairing for names; shared picker values fill their available width, wrap as paragraphs without hyphenation and retain the user's text size. No adaptive-pair setting or font-shrink logic.
- Production delimiter/XML/string/whitespace checks passed. Offline debug assemble passed (4m21s); final stacked source recompiled/packaged by installDebug and installed on PPA-LX2/Android10 (4m30s,44 tasks:7 executed/37 up-to-date). No suites/app launch/on-device visual verification. Evidence `local/verification/picker-name-layout-2026-10-25/`.

## Shared Android demo screens — 25 October 2026

- Production `ReportWorkspace`/`DoctorsWorkspace` now accept controller state/actions; live adapters retain existing ViewModels/authorization/durable delivery. Demo uses those screens alongside the existing real ward/forms/cards/dashboard and shared appearance controls. Removed separate demo report/team layouts; production drawer has a small demo session-control slot.
- Memory-only fake snapshot/registry/receipts supports fetch/pending chooser/reviewed publication/replacement/Undo, report destination selection/preview/final simulated send, doctor/team edits and canonical pending counts. Explicit simulation notices; no production repositories/settings/session/Telegram dependencies or entered doctor PIN/Telegram identity retention. Connection/security/admin exports and CSV transfers remain unavailable.
- Explicit PDFs use the production Classic/Cards renderer with DEMO every page. Save/Share uses opened previews; supervisor saving requires each opened preview. Cleanup follows invalidation/reset/exit/disposal. Demo day labels refresh at08:30/foreground; report review captures its day and sample admission dates use the work day.
- Production-only delimiter/XML/string/controller-contract/import/document-link/whitespace checks passed. Owner-authorized offline debug assemble passed (4m11s,44 tasks:15 executed/29 up-to-date); installDebug recompiled final cleanup and installed on PPA-LX2/Android10 (4m,44 tasks:7 executed/37 up-to-date). No suites/app launch/on-device workflow/isolation/PDF visual verification. Evidence `local/verification/shared-demo-2026-10-25/`.

## Desktop simplification — 25 October 2026

- Fixed nonempty-ward publication by decoding each row's tasks before normalization/encoding. Removed retired completion-evidence parameters and computations; snapshot/authority/route checks and durable receipts remain.
- Main actions are Fetch and «رفع للشبكة وإرسال تقرير» using existing reviewed delivery. Standalone reviewed publication remains under Data Management. Diagnosis/treatment/follow-up/labs use plain multiline fields; retired structured-field module removed. Four Android-matching accents with legacy preference mapping.
- Encrypted recycle entries store local deletion time. Idle daily sweeps and opening the bin remove entries after at least seven days; undated existing entries receive a seven-day grace period. Cleanup runs off-main, skips demo/locked/busy work, retries failures, and preserves active patients/backups/receipts/Telegram data.
- Captured08:30 report day drives text/PDF headings, PDF age/admission days, filenames/captions and final confirmation; text revalidation uses that captured day. Stored shift identity/date stay unchanged. Live ward/dashboard/admission filters use the work day; timer redraws only on a day change.
- Removed no-op task time/overdue/assignment-metadata APIs and discarded clinical audit/completion payload construction, preserving legacy stored fields. Production-only source checks/limits: current state; evidence `local/verification/desktop-simplification-2026-10-25/`. No app execution, suites or packaging.

## Desktop follow-up source review — 25 October 2026

- Corrected the missed assignment-date status integration: desktop workflow status now uses the same workspace pending predicate as the fetch guard (demo remains memory-only). Python3.8 grammar/whitespace checks passed; no runtime or suites. Broader desktop differences were reported to the owner without implementing a redesign.

## Hidden assignment date and save recovery — 25 October 2026

- Approved Android DB5→6 migration adds nullable assignment day; no backfill. First patient Save and actual supervisor changes stamp the08:30 work day; other saves preserve the live stored day. Fetch/restore/backup carry it without restamping. Demo applies the same rule in memory.
- Assignment PDFs highlight that report day's new/reassigned patients with a pale-yellow name cell and bold text, including unassigned patients; repeated export does not clear it. Every page includes «المظلّل: مريض جديد أو تغيّر المشرف المسؤول». Android/desktop source updated; no renderer-parity/runtime claim.
- CSV7 adds optional final `assignmentChangedOn` ISO-date column; updated readers accept legacy22/current23 columns. Desktop pending/publication retains it separately from ordinary report identity. Android explicit report fingerprints remain unchanged; historical receipt keys use the frozen original23-field Patient string shape. Older writers can drop the field and older desktop readers reject the extension; update participating clients.
- Report-changed notice survives reload as a separate warning. Draft save failures keep input and show a patient-scoped warning inside Add/Edit; draft reads/writes handle failure/cancellation. Theme/appearance/text-size writes serialize and update selection only after successful persistence; failures retain the prior selection and show a warning.
- Debug assemble passed (5m1s) and install on PPA-LX2/Android10 passed (9s). Kotlin/default XML/string/whitespace checks, seven desktop Python3.8 grammar checks and frozen legacy receipt field-order source check passed. No suites/app launch/runtime migration/PDF visual verification/desktop execution. Local evidence `local/verification/assignment-highlighting-2026-10-25/`.

## Ward status and recycle-bin follow-up — 25 October 2026

- Sync details expand in the dashboard while retaining Data Management navigation. Last confirmed report delivery shows its calendar date and time.
- Recycle Bin observes deleted rows while open, refreshes after restore/background purge, stops observation on disposal, and shows loading or an actionable read-failure/Retry state. Missing ward fails visibly; cancellation remains separate. Restore controls follow ward busy/read-only state; demo remains memory-only. No schema/version change.
- Debug assemble passed after callback/controller wiring corrections (4m8s); install passed on PPA-LX2/Android10 (10s). Source checks passed; no suites/app launch/runtime behavior verification. Failed/resolving/install evidence: `local/verification/ward-status-fixes-2026-10-25/`.

## Palette adjustment — 25 October 2026

- Replaced Iris with Rose (the existing magenta palette), including light/dark accents, containers and picker label. Four choices remain; stored Iris IDs select Rose. Lapis is unchanged. Debug assemble passed (3m24s) and install passed on PPA-LX2/Android10 (6s); source checks passed, no suites/app launch/on-device visual review. Local evidence `local/verification/rose-palette-2026-10-25/`.

## Recycle bin and appearance — 25 October 2026

- Android daily background sweep removes local deleted rows after at least seven days, with deferred execution allowed and a retention notice. Active/restored rows and backups/Undo/Telegram artifacts remain outside the delete query; no schema/version change. Demo remains memory-only.
- Palette picker reduced from eight to four distinct colors with legacy saved-choice mapping. Removed «مسافات مريحة» and normalized saved density to normal. Removed duplicate header publication chip; dashboard retains synchronization status and adds the known pending-change count. Delivery uncertainty warning remains.
- Debug assemble passed (4m39s), followed by install on connected PPA-LX2/Android10 (9s). Source checks passed; no suites/app launch/scheduled-cleanup or UI runtime verification. Verification results and limits: [current state](current-state.md#verification-and-limits); local evidence `local/verification/recycle-appearance-2026-10-25/`.


[Current behavior/evidence](current-state.md) · [Remaining implementation](roadmap.md) · [Checks](testing-handoff.md). Source completion ≠ runtime verification; superseded intermediate designs omitted.

## 25 October 2026

- **Four follow-up corrections:** current-ward-only pending/fetch guards preserve historical rows; an empty roster-free ward without a baseline no longer becomes pending from its sort preference. Publication summaries use neutral display names. Extra-copy permits restrict the route to the approved report/format, restore original UNKNOWN during attempts, retain it after failure/interruption, and allow history resolution/revocation with in-flight protection. No receipt/schema/version change. Reused owner build authorization: debug assemble passed (Kotlin/javac/Hilt/package,3m20s); latest APK not installed because ADB had no connected device. No suites/runtime/live verification. Logs: `local/verification/followup-fixes-2026-10-25/`.

- **Owner-authorized build/install:** debug APK assembled and installed on connected PPA-LX2/Android10. Fixed compile failures by restoring used Copy/priority callbacks and updating the bundled debug scroll activity after task-time API removal. Offline/one worker/in-process Kotlin/autoVersion=false; ordinary incremental settings. Kotlin/javac/Hilt/package passed; prior orphan Hilt failure did not recur. No app launch/tests/runtime verification. Full failed/resolving/install logs: `local/verification/phone-build-2026-10-25/`.

- **Source review fixes:** removed misleading optimistic/revision/force arguments and no-op clinical tracking; row/shift/deletion/assignment guards remain. One canonical clinical/order comparison, using report ordering for priority/presets, drives pending status, counts, briefing and fetch guards; edit→revert clears derived pending, and unchanged fetched patients retain revisions. Ward/Settings reviewed publication no longer downloads its own upload; successful publication exposes local-bookkeeping failure separately.
- **Sync/delivery cleanup:** absent/unrecognized/valid pinned state, unknown-state write refusal, two-read state updates/shared CSV history helper; immutable registry decode cache with live alias/identity validation; centralized CSV high-water bookkeeping. Typed receipt statuses and definite400/401/403/404/429→NOT_SENT, partial/network uncertainty preserved. Explicit fingerprint fields plus legacy receipt-key checks, captured-date age/admission rendering, shared report busy gate, pure failure outcomes, separate doctor-fetch state, structured registry briefing and typed legacy doctor options. Checklist APIs no longer take time/assignment metadata; day-label state refreshes at08:30. Named report/sync resources replace opaque report keys and transport-layer literals.
- **Retained by review:** snapshot lineage and revision storage have bookkeeping/history/UI readers; General-chat sent pointers cannot hold supervisor-chat message IDs. Delivery receipts remain unpruned to preserve unresolved outcomes and exact-content duplicate protection. Readiness UI already delegates to domain rules; existing cancellation handling remains where it is correct. No schema/interchange/app/toolchain version change. Production delimiter/XML/string-reference/call-site and whitespace checks only; no build, suite/device/live verification. Local source receipt: `local/verification/source-review-2026-10-25/`.

- **Recovery:** upload/fetch/CSV/Undo busy cleanup and cancellation/error handling; cleanup before follow-up fetch. Local reload failure retains read-only list/Retry; initial/missing-ward errors remain full-screen. Settings keeps workspace scroll/Advanced state; current-workspace search no longer resets it.
- **UI:** themed responsive splash preserves emblem/credits/timer/hold/tap; grouped dashboard, quieter neutral cards, shared spacing/empty states, direct Recycle Bin/Admin, operation-specific progress and Back to top. Existing saved-patient reveal/highlight reused.
- **Editors:** shared full-screen sections/equal paired fields; clinical-first Edit/demographics-first Add; plain multiline fields and retained old drafts; inline tasks including unfinished text; explicit Save/Save and next, optional profiles/neutral unnamed labels. Retired persistent entry mode/custom doctor-title display; legacy columns retained.
- **Clinical simplification:** last-save-wins, no new clinical revision prompts/audit/editor/task-completion stamps. Plain task text/done, grouped board/2-second completion/Undo; readiness links and priority Undo. Legacy metadata retained without new controls/counters.
- **Continuous ward:**08:30 day label only, no rollover/date gate. Retired dated-list browsing/copy/local previous-publication viewing; current-ward publication, old multi-list decoding/newest fetch, stored rows/Undo/admin rollback preserved.
- **Reports/settings:** combined label «رفع للشبكة وإرسال تقرير»; short final confirmation; retired announcements/history loads. Preview-byte Save/Share under Export, explicit supervisor batches/names-only sheets, Start empty under Advanced. A4/A5 portrait/light/Medium separators; old values ignored. Four sort presets, View Options owns cards; simplified appearance. General-send local-copy coupling remains unchanged/unselected.
- **Network/auth:**25second retry budget, definite429→NOT_SENT versus partial UNKNOWN; explicit back-online fetch. Persisted5/15/60-minute PIN escalation/five-minute default lock and neutral foreground cover.
- **Build fixes:** missing `dp`, obsolete DAO revision argument, invalid LABS enum references and unused optional-fields argument. Owner had successful earlier installs; later Kotlin passed but javac hit orphan announcement Hilt output. Agent did not clean/build; latest source uncompiled.
- **Checks:** applicable production Python3.8 grammar/XML/string/delimiter/diff checks only; no new suite/device/live runs or schema/toolchain changes.

## Earlier retained implementation

- Protected join/encryption/reconnect, PIN/biometrics/live authority, reviewed existing-pin Android creation,200% fixed-action warning; automatic lock-screen biometric prompt and preview-open state survive intended lifecycle boundaries.
- Captured doctor/patient publication, recoverable fetch/Undo, live-admin prior-publication rollback/older-remote override, bounded cache, pending counts and durable exact-artifact UNKNOWN/extra-copy controls.
- Doctor Publish/Fetch/CSV Import and full-screen roster/team/import reviews; atomic current-roster detachment on safe deletion. Stable details dialog/Previous-Edit-Next footer/per-patient scroll; card long-press Edit; saved drafts/picker keys/privacy owners.
- Desktop standalone Windows/Linux encrypted workflow, logical Arabic input, local PIN, reviewed administration/recovery, demo, retained widgets/shortcuts; visible connection/login/destinations and adjacent join/env discovery.
- A5 shared geometry/fonts/sections, RTL column-first index/full-page links, conditional two-column treatment and complete pagination. Current artifact counts/hashes in `samples/artifact-notes.json`; superseded page-count chronology removed.
- Selected O2/O6/O10/O11/O13/O16 and production-preview part of O23 implemented; automatic biometric O20 also implemented. Remaining proposals stay in Decisions.
- Historical full JVM/device/desktop/interoperability/PDF/live receipts and exact snapshot limits live only in [Current state](current-state.md#verification-and-limits). Existing task-related tests were updated in earlier authorized work, not rerun for later simplification.

## Documentation consolidation

Repository-owned Markdown reviewed; repeated behavior/history/checklists consolidated, stale retired-feature descriptions corrected against production source. AGENTS compacted without removing permission/safety/product constraints; command blocks and private originals retained. Plan: [documentation-plan.md](documentation-plan.md). Local audit: `local/verification/documentation-2026-10-25/`. Public documentation reduced about32% by Unicode characters; AGENTS about26% (not measured model tokens). All30 public documents pass local link/anchor checks;45 fenced blocks preserved against HEAD;10 private/archive documents unchanged. Completed-only usability plan removed; O20 moved out of proposals after source confirmation. No builds/tests/source changes; external URLs not checked.
