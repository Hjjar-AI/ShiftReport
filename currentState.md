# ShiftReport current technical state

Snapshot updated: 2026-10-07.

This document describes the implementation as it exists now. Future implementation work belongs in [workRemains.md](workRemains.md); completed history belongs in [workDone.md](workDone.md). Accepted design/architecture decisions belong in [designAndArchitecture.md](designAndArchitecture.md), diagnostics in [debugging.md](debugging.md), and contribution rules in [AGENTS.md](AGENTS.md).

## Product shape

ShiftReport is a single-module, Arabic-first, RTL Android ward and shift-handoff application. It is hospital-agnostic and configured on-device during first launch. Telegram provides transport, report delivery, immutable document storage, and shared pointers; local Room data remains the working clinical store on each device. The intended workflow is retrieve, edit, and publish, with last-write-wins at the shared pinned-state boundary. Guaranteed concurrent collaboration is outside the required scope, and a separate backend is not required.

The app supports API 23 through target/compile SDK 34. It uses Java 17, Kotlin 1.9.0, Android Gradle Plugin 8.5.0, and Jetpack Compose with the 2023.10.01 BOM. Do not infer that these versions should be upgraded merely because newer versions exist.

## Startup and navigation flow

```text
MainActivity
  -> immediate RTL intro (tap to skip; 10-second maximum)
  -> saved appearance, accent, and configured font scale
  -> WardAppRoot
      -> project setup when uninitialized
          -> Demo (in-memory dummy ward)
          -> Join (default; encrypted file only)
          -> Create (new Telegram-backed project)
      -> clinician login
      -> PIN/biometric unlock when required
      -> WardNavHost
          -> Ward
          -> Report preview/send
          -> Admin / doctors / announcement
          -> Settings
          -> About
```

The ward drawer places Patients, report preview, and Settings directly below a compact two-column header (hospital/app opposite user/role/date/count); Activity is visible only to admins. The header opens data tools, and navigation rows/submenu headers use 48dp minimum targets with 4dp vertical padding and wrapping labels. A compact live sync/offline/conflict status remains visible; detailed synchronization/recovery controls are in Data, ward controls in Ward, and administrator/import/About destinations in Administration. Settings has a fixed horizontally scrollable section index, report defaults before appearance, and force-publication recovery beside backup/restore with its existing confirmation.

The ward uses persistent Patients and Dashboard tabs, with Activity available only to admins. Its drawer, dashboard, navigation rail, and support-pane entry points follow the same restriction; refresh rechecks live admin authorization in the ViewModel. Per-patient history remains available in patient details. Compact windows use inline icon/label bottom navigation at 48dp plus the system inset (growing with font scale); widths from 600dp use a navigation rail. Activity renders full content with saved Arabic search/filter state and separate audit/publication scroll state; refresh failures retain cached entries and show an error. The side drawer keeps secondary actions in accordion submenus and groups clinician/role/shift context with a data-health panel for freshness, connectivity, synchronization, and conflicts. Connectivity uses validated Android network callbacks exposed by the ward ViewModel and collected with the screen lifecycle; callbacks are released when collection stops. Connectivity does not establish that clinical data is verified or current. The default patient header stays focused on patient count and compact filter presets. The compact «إرسال تقرير» button opens a readiness sheet before report review/send; adding a patient remains available from the top bar.

Patient browsing uses a simultaneous list-detail layout when the ward content has at least 760dp width and 360dp height, after navigation space. The list is a single patient column in panes. A 48dp divider retains its width fraction during the session, clamps to readable minimums after resizing, and supports RTL-aware dragging, keyboard adjustment/reset, visible focus, and accessibility resizing. Without a selected patient, the second pane displays existing report readiness and, for admins, recent local activity. Tall medium content can show that support below the list; compact/short layouts use a supporting sheet and full-screen detail. Phone card taps continue to edit; pane card taps select detail.

Jetpack WindowManager 1.2.0 is newly added for lifecycle-observed separating/occluding folds. Shared ward layout code translates window hinge bounds into local physical coordinates, reserves margins, and positions suitable list/detail regions on opposite sides of vertical or horizontal folds. Small regions fall back to the larger safe region. Ward bars/navigation, patient details, report/support sheets, and the add/edit/copy form container use this geometry. Selected patient, detail section/scroll, and list state are retained across presentation changes; deletion or switching to a shift without that patient clears selection. The latest owner-supplied debug build succeeded on 2026-10-07; real-device folding/keyboard/accessibility validation remains pending.

The intro is rendered in MainActivity before theme/root ViewModel construction. Application repositories/configuration are injected lazily and resolved by the existing IO startup job. The intro dismisses on any tap or after 10 seconds and retains its dismissal/deadline across Activity recreation. Splash and About share the owner-edited title «تقرير المناوبة (للطب النفسي)» and credits in the same order: supervision/development by الطبيب محمد علي حجار, the existing white-field emblem, technical contribution by الأطباء أيهم شيخة and محمد زاهر شقير, and the initial Excel/report structure credit to الطبيب الاختصاصي نديم العباس. Both screens use the same automatic `BuildConfig.VERSION_NAME` footer. Splash alone shows an Arabic-numeral countdown derived from the saved monotonic deadline in MainActivity, rather than a separate timer; it resumes the remaining time across recreation and still dismisses on any tap or expiry. Debug compilation/packaging is confirmed by the 2026-10-07 owner-supplied build; startup timing and device validation remain pending.

## Visual design system

- `AppAppearance` stores System/Light/Dark independently of `AppThemePreset`. All five accents retain neutral white/charcoal surfaces; teal is the default and wallpaper-derived colors are disabled by default. Preset identifiers remain readable, but navy no longer forces dark mode and the other accents no longer force light mode. `ThemeViewModel` observes the saved appearance, accent, and font scale; no schema/version change is involved. Accents are now more distinct (teal, green, blue, gold, purple), and primary/secondary/tertiary controls and containers, form section headers, and Settings headings share the selected accent.
- Theme typography explicitly defines every Material text role with zero letter spacing, 14–16sp body text, and 12–14sp labels. `UiSpacing` supplies a 4/8/12/16/24dp rhythm and a 48dp interaction target; compact cards reduce padding instead of text size. Theme shapes use a shared radius scale.
- `patientBadgeColors` is shared by badge editing and patient cards: high is urgent red, medium is warning amber, and low/unclassified badges are neutral with explicit labels. Diagnosis categories use neutral chips rather than error colors. Cards use a neutral border, with primary emphasis for selection.
- Collapsed cards show identity/admission, responsible clinicians, a two-line diagnosis summary, warning/unclassified badges, and nonzero pending/overdue counts. Low-level badges are summarized by an additional-badge count. Expanded cards retain the full diagnosis, badges, task records, and clinical/last-edit details; no saved content is removed.
- Individual card expansion uses 160ms expansion and 130ms collapse. The ward passes animation permission only to the individually toggled card; bulk expansion clears it and uses immediate transitions. The redundant top-bar readiness/activity icon was removed; report readiness remains on the FAB and Activity remains an admin-only primary tab.
- Settings uses plain section headings within neutral, unelevated bordered sections. Common PDF defaults remain visible; orientation, paper, colors, dark PDF, and per-supervisor output are grouped under an expandable Advanced control. All existing actions remain available.
- Fixed the supplied `PatientTaskEditor` Material API opt-in errors in source. Delimiter/import checks and a static audit of 20 new accent text/background pairs passed (minimum 6.46:1). The earlier supplied build failed at `PatientTaskEditor.kt:131` and `:138` because of missing experimental Material API opt-ins. Both task-editor composables now declare the opt-in, and the owner supplied a successful debug build on 2026-10-07. Rendered/device verification remains unconfirmed.

### Latest form and report adjustments

- General editing now exposes a multiline laboratory field using the same lab items/draft as Advanced mode. Switching modes preserves values and date markers; Save, persistence, synchronization, and exports use the existing laboratory field.
- Report preview starts with an actionable shift-doctor card even when an empty roster prevents report construction. Current-shift selection uses the shared picker and repository transaction with expected-revision review, active-doctor checks, pending-sync state, and audit; saving rebuilds the reviewed report. Archived shifts stay read-only.
- Shift doctors have no fixed maximum; at least one active doctor remains required. The old three-doctor limit was removed from both picker entry points and synchronization validation. PDF roster headings wrap and paginate, text rosters split between complete doctor links, and PDF captions summarize the roster count with full names inside the file.
- These follow-up changes passed the latest authorized debug build. Accent text pairs were rechecked after strengthening the colors (minimum 6.28:1). The latest debug build covers these changes; device validation remains unperformed.

## Main technology

| Concern | Current implementation |
|---|---|
| UI | Jetpack Compose Material 3, RTL-first; WindowManager folding features |
| State | Hilt ViewModels with StateFlow |
| Dependency injection | Hilt |
| Local persistence | Room 2.6.1 over SQLCipher |
| Secrets/preferences | EncryptedSharedPreferences |
| Networking | OkHttp and Telegram Bot API |
| Serialization | kotlinx.serialization JSON plus CSV codecs |
| Background work | WorkManager and coroutines |
| Authentication | Clinician identity, PBKDF2 PIN hashes, optional biometric unlock |
| Reports | Reviewed text reports (encrypted documents when enabled), plus readable Classic and Elegant Row PDFs |
| Data formats | Room schema 5; patient CSV schema 7; doctor-registry CSV schema 1 |
| Shared data protection | Optional project-key AES-256-GCM before Telegram upload, automatic local decryption |
| Minimum Android | API 23 |
| Application version | 1.1.1.20261007 (code 2); debug adds `-debug`, release has no suffix |

## Source layout

- `config/`: application constants, project configuration, Telegram topic resolution, protected provisioning, and shared project-data encryption.
- `data/db/`: Room database, entities, and DAOs.
- `data/model/`: domain-facing patient, doctor, shift, and synchronization models.
- `data/repository/`: persistence boundaries and local optimistic mutation rules.
- `domain/auth/`: session handling, PIN hashing, biometric support, bootstrap, and live admin authorization.
- `domain/backup/`: password-encrypted local backup and restore.
- `domain/patient/`: patient validation and card-related domain rules.
- `domain/doctor/`: doctor naming/validation plus the versioned UTF-8 CSV interchange codec.
- `domain/report/`: report assembly, readiness checks, and text generation.
- `domain/export/`: patient CSV export; the source directory matches its declared package.
- `domain/sort/`: stable patient sorting and grouping rules.
- `domain/task/`: structured task validation, pending/overdue/unassigned counts, carry-forward rules, and human-readable summaries.
- `network/telegram/`: Telegram Bot API client and protocol models.
- `sync/`: CSV codecs, merge/conflict logic, publication journal, and synchronization orchestration. `SyncService` retains the public API and patient workflows; `DoctorSyncCoordinator` owns doctor workflows and review state. Both use the singleton `RemoteSyncStateStore` for pinned-state IO and serialized updates. Pure patient merging and CSV snapshot rules are separate helpers.
- `pdf/`: Shared non-splitting row renderer used by Classic and Elegant Row PDF styles.
- `ui/`: Compose screens, navigation, themes, dialogs, and ViewModels. Ward drawer, dashboard, report sheet, navigation/filter components, and review dialogs are extracted from `WardScreen`; add/copy entrypoints delegate to `PatientFormDialog`, with reusable controls in `PatientFormFields`.
- `migration/`: legacy import functionality; excluded from routine review unless explicitly requested.

The unused version catalog and redundant source-package `.gitignore` were removed. Active dependency versions remain in the existing Gradle scripts. Obsolete build-time Telegram keys were removed from `local.properties`, preserving `sdk.dir`; the duplicate legacy credentials file was removed. Telegram credentials continue to come from runtime project configuration. The source/configuration refactor received static checks only; the earlier successful debug build predates it.

## Data model and persistence

The encrypted Room database uses schema 5 and contains patients (including serialized structured tasks), doctors, shifts, settings, synchronization state, and audit entries. Patient and shift records include revisions used for local stale-write protection.

Current patient mutation behavior:

- Edit (including custom badge changes), delete, restore, and priority changes compare the caller's expected revision inside a Room transaction.
- Successful patient mutations increment the revision.
- Shift doctor and sort changes use revision-checked DAO updates. Applying a sort also writes consecutive patient positions in the same transaction, incrementing changed patient revisions. Ward lists, text/PDF reports, local CSV, and Telegram CSV bundles share priority-first ordering followed by the saved sort rules.
- Guided rollover refuses to insert when the target shift is no longer empty, preventing duplicate local application.
- `WardMutationRepository` commits ward patient add/edit (including badges and priority), delete/restore, rollover, and shift doctor/sort changes with the pending-sync marker and audit records in one Room transaction on the IO dispatcher. Stale or failed mutations roll back the entire operation.
- Audit before-values are read within that transaction; new-patient ID collisions, capacity, and sort-order assignment are checked there as well. Current-shift editability is rechecked at this repository boundary.
- Stale saves in the patient editor, shift-doctor picker, and sort sheet open a draft/saved-value review. The rejected draft remains available during the open editor session, even after loading the saved values. Choosing a version returns to editing without saving; the next save uses the explicitly reviewed revision and can reject another intervening edit. Missing/deleted patients cannot be rebased for editing.
- Shift controls capture their opening revision instead of using a newer observed revision with an older draft. The sort sheet remains open on save failure. These review drafts are session-local, not a new persisted clinical record.
- `DoctorMutationRepository` commits doctor add/edit/delete, CSV import, admin promotion/demotion, and supervisor-group changes with live admin authorization, stale snapshot checks, pending state, and audit together. New doctors are checked for capacity and identity collisions inside the transaction. Clinical-role changes with active patient references are refused; deletion protects active references and the current shift roster. Stale doctor edits offer session-local draft/saved review, with a separate subsequent save and no PIN display or draft persistence.
- These boundaries cover local ward mutations and basic doctor edits; remote synchronization and backup restore retain separate orchestration. CSV import retains a session-local plan containing the reviewed registry, actor identity, and proposed records; confirmation rejects an intervening registry/actor change and revalidates clinical references and permission rules inside the transaction. Administrator dialogs pass their selected doctor snapshot, prevent repeated submission, and audit the verified live actor. Supervisor-group drafts retain their baseline until explicitly reloaded after an observed change; they cannot overwrite newer role, identity, or destination data.

## Project configuration and onboarding

`ProjectConfigStore` holds the hospital name, bot token, group ID, optional topic IDs, initialization state, demo flag, and optional shared Telegram-data key in encrypted device-local preferences.

- Demo creates no database patients and starts no background synchronization. `DemoWardViewModel` depends only on application context and the PDF renderer; it has no repositories, session, settings store, or Telegram client. Its eight hot-beverage patients and six tree-named doctors support temporary add/edit/copy/delete/restore, labs/tasks with completion stamps, search/filter/sort, pins/expansion, per-patient detail, dashboard counts, and shift-doctor selection. The shared real forms/cards/picker/sort sheet are reused. Appearance/accent previews remain screen-local. ViewModel data survives Activity recreation; explicit exit/reset discards changes, and process death starts a new demo.
- Demo report options cover Classic/Elegant Rows, portrait/landscape, and PDF colors. Explicit `CreateDocument` export renders the demo snapshot with the production PDF renderer, adds a demo footer on every page, writes/closes the output, and checks saved bytes. This is the deliberate local-file exception to the otherwise in-memory demo; no upload/publication path is exposed.
- Join is the default path and requires an encrypted `.srjoin.json` file, followed by a non-secret project review. Manual joining is removed, and the ViewModel also rejects joining without a successful import. The file supplies connection details, topic IDs, and the shared data key automatically. Create retains connection, optional topics, optional Telegram encryption, and first-administrator sections. Setup retains full instructions behind Help and keeps validation/status and the final action outside the scrolling content.
- Join export uses `application/json` with a `.srjoin.json` name. The wrapper contains only a format marker and Base64-encoded AES-GCM ciphertext; the encrypted payload schema and password derivation are unchanged. Import recognizes the JSON wrapper or existing binary files by content. Before opening the save picker, ciphertext is staged in app-private, non-backed-up storage; only its random file ID enters `SavedStateHandle`, allowing the pending export to be recovered after process recreation. Saving rechecks admin authority, closes the stream, and compares all saved bytes before success. Success, cancellation, and failure remove the staged payload; failures also attempt to delete the newly created destination. Abandoned staged files older than 24 hours are pruned on the next export. These changes have source/static verification only; Android file-provider and process-recreation behavior have not been validated on a device.

- Create validates the Telegram bot/group, creates the initial administrator registry, and initializes the project. Optional encryption generates an independent random 256-bit project key on IO before the first publication; the key is retained for retries and included only inside password-encrypted join exports. There is no existing-project conversion or key-rotation UI.
- `ProjectConnectionManager` provides same-project reconnect from protected join files, including before login when a revoked token prevents authentication. Administrators can also enter a replacement token after revoking/regenerating the same bot's token through BotFather. Manual replacement rechecks live admin authority and actor identity before saving; file recovery is authorized by possession of the protected file/passphrase.
- Replacement probes `getMe`, `getChat`, and `getChatMember` with explicit candidate credentials without changing the live token or importing records. It verifies the original bot identity, group access, admin/pinning permission, and pinned-text decryption when present. Confirmation revalidates the candidate and expected local configuration; only the encrypted token preference is updated on IO. The shared data key, topics, local clinical data, pending edits, sync bases, and session remain intact. No bootstrap or local data replacement is part of reconnect; login resumes its normal flow afterward.
- Files for different groups/bots, data keys, or topics are rejected before credential changes. Project switching is deliberately blocked to protect the existing local database; there is no destructive switch/clear workflow. Export a new protected join file after replacement. Old join files cease to connect once their token is revoked through BotFather; the app itself does not revoke Telegram credentials.
- The join-file passphrase is never stored or embedded. The current file is reusable; server-enforced one-time expiry is an optional future capability, not a prerequisite for the Telegram-based app.

## Authentication and authorization

- Clinician sessions are stored in encrypted preferences.
- PINs are PBKDF2 hashes with random salts and constant-time comparison.
- Optional biometric unlock and configurable automatic locking protect foreground access.
- `AdminAuthorizer` checks both the active encrypted session and the live doctor registry.
- Doctor mutations, promotion/demotion, announcements, supervisor-group configuration, and provisioning export enforce live admin authority below navigation.
- The legacy VBA importer is navigation-guarded; domain-level authorization there remains deferred because migration/importer files are outside routine review scope.

## Synchronization and concurrency

Patient synchronization uses CSV snapshots, a saved base snapshot, three-way field merging, explicit conflict choices, immutable uploads, recovery snapshots, and a publication journal. Patient and doctor synchronization are serialized per channel on one device. Pending local edits are uploaded before a background pull can replace them.

Telegram pinned metadata is a shared pointer without atomic compare-and-swap. The owner accepts last-write-wins for competing remote publications. Existing merge and stale-pointer checks remain implemented; this scope decision does not replace them with unconditional overwrites. Consequently:

- Local stale-write protection is strong on one device.
- Patient three-way merge reduces cross-device loss.
- Simultaneous remote publication can still race at the pinned-pointer boundary; the last successful pointer write wins. Preserving every competing publication is outside the required scope.
- Doctor registry synchronization persists a PIN-free base snapshot in encrypted Room settings and merges additions, deletions, and each field supported by the existing Telegram wire format. The name, gender, clinical role/supervisor-group pair, Telegram ID, title, and admin rank/permanent pair participate in the merge. Telegram username, portable extra options, and local PIN/alias options are not sent by that legacy format and remain local or CSV-export data.
- Conflicting fields, duplicate names/Telegram IDs, competing admin-rank assignments, and protected doctor deletions appear in the doctor-registry screen for explicit admin choices. Existing installations with pending changes and no saved base require conservative review of differences. No local registry replacement occurs while conflicts remain.
- Merge application rechecks the full local snapshot and saved base, protects permanent admins, assigned-patient and unfinished-task references, and the current shift roster, and atomically records the registry, remote base, sync cursor, pending marker, and audit before/after values. Explicit resolution requires live local admin authority plus active admin status in the reviewed remote registry, and respects higher-rank/permanent-admin restrictions.
- Doctor mutation/pending writes share a transaction. An unchanged device-local PIN is invalidated when a doctor is linked to a different Telegram identity; fresh explicitly supplied PINs are retained. Edits made during registry upload remain pending. Conflict-review state is session-local; after process death, synchronization reconstructs it from the persisted base and local registry.
- Unchanged merged doctor rows keep their existing timestamps and local name components, avoiding timestamp-only stale-editor failures after an unchanged pull.
- The full doctor pointer is checked before publication and again during pinned-state update, but this remains best-effort detection rather than an atomic cross-device transaction.
- No authoritative service is required for the accepted retrieve/edit/publish workflow. Stronger concurrent-write guarantees would be a separate future requirement.

## Reports and handoff

`ReportBuilder` reads the current shift, non-deleted patients, doctor registry, and sort specification together in a Room transaction on the IO dispatcher. The report preview places selected shift doctors in its first standalone card, before the patient-count/date summary.

Text/PDF sending retains the preview snapshot. `ReportReview` compares clinical content and relevant doctor/delivery fields before synchronization, after doctor synchronization, and after patient publication. A mismatch refreshes the preview and requires another explicit confirmation before report delivery. Delivery, including supervisor PDFs and destinations, uses the accepted snapshot rather than rereading individual doctor rows. Supervisor sends synchronize before delivery; a partial failure reports how many recipients were reached and requires deliberate recipient review instead of offering the combined-report retry.

These checks preserve review intent but do not create a transaction across synchronization and Telegram delivery. CSV synchronization may publish before a later mismatch stops text/PDF delivery; competing remote publications follow the accepted last-write-wins policy, while interrupted/partial delivery still needs manual recovery validation. Local preview/save/share continue to assemble a fresh snapshot on request.

Both PDF styles now use compact row layouts with multiple patients per page. A patient row is measured before drawing; when the remaining page space is insufficient, the complete row moves to the next page. Exceptionally large rows are fitted without discarding clinical text. Classic uses only restrained header and zebra colors, while Elegant Row uses white patient rows and slightly more generous typography to keep exported files print-friendly and smaller than the former card output.

Administrators can export and import a versioned UTF-8 doctor-registry CSV through Android's document picker. The file carries every portable doctor field, including stable ID, names, gender, clinical role, supervisor group, Telegram identity, title, rank, permanent-admin state, portable options, and timestamps. Import parses and validates the complete file on IO, validates its proposed merge transactionally before preview, and previews the resulting active/admin/deleted counts. It merges by stable ID/Telegram/name, preserves local aliases and PINs for unchanged Telegram identities, protects current/permanent administrators and rank restrictions, and rechecks the full reviewed registry and actor at commit. The local mutation, pending marker, and audit commit together; the session-local plan is cleared before publishing to Telegram, so upload failure leaves committed data pending without repeating the import. PIN hashes never enter the plaintext CSV; newly imported doctors establish a new PIN after their first verified Telegram login.

The ward FAB shows a readiness count derived from shared `ReportReadiness` rules plus unresolved merge conflicts. Opening it presents an express sheet with patient count, freshness, and the correct review/send action rather than publishing immediately. Current readiness checks cover missing resident, supervisor, diagnosis, and treatment. Structured patient tasks are implemented; user acknowledgment is not required; structured acuity, contingencies, guided I-PASS, extended readiness, and accepted-handoff change history were removed from the roadmap by the owner.

Ward cards have an Arabic-labelled top-bar expand/collapse-all control. Global changes clear individual overrides; individual cards can still collapse when the global preference is expanded. The global preference persists, and individual overrides survive Activity recreation. Cards support comfortable/compact density, individual expansion, session-local pinning, relative freshness, selected state, and consolidated accessibility summaries/actions. Live editable cards expose edit, badge, priority, copy, and confirmed-delete custom actions; read-only/detail-only cards omit unavailable or no-op actions. In compact layouts, tapping an editable card opens editing and long-pressing opens tabbed details; pane layouts use taps to select detail. The list keeps its existing structured grouping and adds quick filter presets. Patient details keep the section navigator sticky, expose quick actions, and show patient-specific audit history when available. Patient add/edit/copy uses a full-screen surface with fixed title and Save/Cancel actions, a scrolling form, keyboard/system insets, and hinge-safe content. Editing starts with clinical fields and structured tasks, responsible clinicians, then priority and optional badge editing; admission and personal details come last. New-patient entry starts with admission and personal details before the clinical sections. The badge editor expands on demand while saved and draft badge text remains summarized when collapsed. Section headings and empty text fields are shorter, the general/advanced controls sit with clinical fields, and clinician names wrap. Unsaved state, completion markers, validation summaries, stale-edit review, and shared draft/save logic remain in place. The latest debug build succeeds; device validation remains pending. Patient status supports an ordered list of free-text badges, each with an optional low/medium/high level; legacy fixed-warning and single-badge data is converted on read through the existing persistence columns.

## Structured patient tasks

`Patient.tasks` stores explicit task records separately from follow-up text: stable ID, description, optional owner ID/name, priority, optional deadline, pending/done state, and completion actor/time. New tasks start unassigned, and assigning a doctor is optional. No user acknowledgment is required. The dedicated form section supports adding, editing, assigning, completing, reopening, and removing tasks in the patient draft. Repository save rechecks ownership, stamps the live completing doctor's identity/time, preserves unchanged completion metadata, and commits task changes with the normal patient revision, pending-sync marker, and audit. Saved tasks are included in stale-edit review. Patient copies start with no tasks.

Shared `domain/task/PatientTasks` rules count all unfinished tasks as pending; overdue is the subset with a reached deadline, and unassigned is the subset without an owner. Cards show per-patient nonzero pending/overdue counts even while collapsed; zero-count chips are omitted. Expanded cards and the patient task tab show records, while dashboard metrics/filters show ward totals. My patients includes unfinished task ownership. Counts refresh through the existing minute timer. Date/time deadlines are stored as absolute instants; a shift-end deadline is calculated from the shared 08:30 local shift boundary and retains its original instant after rollover.

Rollover explicitly carries unfinished tasks, preserving deadlines and task IDs within the new patient's task list. Reassignment clears their owner ID/name; completed tasks stay in the source shift. The rollover dialog explains this before confirmation. No free-text follow-up is converted or counted as tasks.

Tasks use one serialized JSON representation in the encrypted Room patient row and patient CSV format. Database schema 5 adds `tasksJson` with an empty-list default and a registered 4-to-5 additive migration. Patient CSV schema 7 includes the task column; the existing bundle, backup, and optional Telegram-encryption paths carry it automatically. Malformed task records fail decoding, and task lists participate in existing three-way field-conflict review. Task owners are normalized with doctor aliases. Unassigned tasks are valid; assigned unfinished tasks referencing an unknown doctor reject import. Doctor deletion and registry merge guards include unfinished assignments. Text/PDF reports include task descriptions, owners, priorities, deadlines, and completion details, and report review covers task content and relevant owner records. Toolchain and dependency versions are unchanged. Source/static SQL checks and the owner-supplied debug build passed; task behavior on a device remains unverified.

## Security boundaries

- Room is encrypted through SQLCipher.
- Project configuration and sessions use encrypted preferences.
- Android application backup is disabled.
- Backup and project-join exports use authenticated AES-GCM encryption with PBKDF2-derived keys.
- New projects can opt into shared AES-256-GCM encryption at the Telegram client boundary: patient bundles, doctor/bootstrap registries, pinned synchronization state, announcements, and text reports are encrypted before upload and downloaded records/pinned state are decrypted before existing parsing. Each envelope uses a fresh 12-byte random nonce, a 128-bit authentication tag, and authenticated format/purpose bytes. Text envelopes are compressed before encryption; text reports use encrypted documents to avoid message-size expansion. Encrypted documents use a generic filename/caption. Missing/wrong keys, tampering, or plaintext records in encrypted projects fail rather than falling back to plaintext. Bootstrap cache files are deleted after reading, including failure cleanup.
- PDF contents and captions remain readable by design. Telegram metadata, such as group membership, sender, timestamps, and file sizes, remains visible. Local previews and explicit local exports keep their existing formats. Existing projects without a data key remain plaintext on Telegram. Keep the protected join file and its passphrase for key recovery. Implementation follows [Android cryptography guidance](https://developer.android.com/privacy-and-security/cryptography); debug compilation/packaging is confirmed, while interoperability and device verification remain pending.
- Credentials and signing material are ignored by Git and must remain local.
- The release build currently references the debug signing configuration. Production distribution requires a protected release keystore and a deliberate signing setup.
- Ordinary clients still hold a broadly privileged reusable Telegram bot token. Removing it from client devices would require an optional token-proxy service; it is not a prerequisite for the current Telegram-based design.

## Implementation status and remaining scope

- Structured tasks and the former P3 card counts are implemented. Tasks start unassigned, assignment is optional, and no user acknowledgment workflow is required.
- Remaining near-term implementation work: production signing. P2 same-project token replacement and file-based reconnect are implemented; cross-project switching is blocked to protect local clinical state.
- Voice dictation is an optional far-future proposal; specialty templates are an optional far-far-future proposal. Neither is implemented.
- The owner supplied a successful `./gradlew assembleDebug` result on 2026-10-07 after the design and task-editor fixes: 3m 23s, 43 actionable tasks (11 executed, 32 up-to-date), configuration cache reused. This confirms debug compilation and packaging for the supplied working snapshot, not runtime correctness. Generated `RushdApplication_MembersInjector.java` reports use/override of a deprecated API; no generated file was edited. Earlier delimiter/import/wiring, task-column SQL, contrast, and whitespace checks remain recorded. Device workflows, rendered accessibility, and animation smoothness remain unverified. A subsequent authorized agent debug build passed, as recorded below.
- Production releases still use debug signing. Ordinary clients hold reusable Telegram bot credentials and join files are reusable. Revocation is performed through BotFather; the app validates and applies replacement credentials and distributes them through fresh join files. An optional future token proxy or stronger concurrency backend is outside the accepted required scope.
- Verification limitations remain documented here rather than reintroduced as verification-only tasks in the roadmap. Fresh-project development is the current preference; historical-project conversion is not a standing requirement.

## Latest export/navigation refinements

- Doctor CSV export prepares a nonempty UTF-8/BOM snapshot before opening the document picker, retains it in the ViewModel across Activity recreation, and consumes the picker result without the former busy-state early return. It writes/closes the byte stream and reads it back before success; cancellation, picker failures, and lost export sessions reset state or give an explicit retry message. PINs remain excluded and live admin authorization is retained.
- The doctor-export, persisted sorting, Activity access, and compact navigation changes passed source checks and the latest authorized debug build. Device/document-provider validation remains unperformed.

## Latest demo and drawer refinements

- Expanded offline demo and compact drawer passed source-level isolation/wiring, delimiter/import, and whitespace checks, plus the latest authorized debug build. Device interaction, rendered layout, and document-provider validation remain unperformed.

## Form, PDF, and expansion refinements

- Native theme, Activity decor, and patient form dialog/decor use RTL from the first layout; Compose direction is explicitly RTL on both sides of the dialog boundary. Patient birth-year/age inputs show «أو» between them. Admission numbers request a numeric keyboard; PIN creation/confirmation/unlock request a numeric password keyboard while ordinary file passphrases remain unrestricted.
- Empty initial-diagnosis fields show examples specific to Psychiatric, Addiction, or Dual selection. These are placeholders only and never become saved clinical findings automatically.
- Task time selection uses the native 24-hour time picker with a `LocalTime` draft; date/shift-end rules and persisted absolute deadlines remain unchanged.
- PDF task summaries keep every task but omit unassigned-owner and normal/low-priority metadata; high priority and assigned owners remain visible. Missing clinician assignments produce no filler labels, and routine badge priority labels are omitted. Classic/Elegant/demo PDFs omit patient last-edit identity/time. UI, text reports, stored data, and completion history retain their existing information.
- Bulk expansion sets both override lists to the requested result before saving the global preference, so collapse/expand is immediate even while the preference observer still has the previous value. Individual transitions remain brief and bulk transitions immediate. A static truth-table check covered 128 mixed initial override states against both possible observed base preferences.

## Latest build verification

On **2026-10-07**, the authorized `./gradlew assembleDebug` completed successfully in **2m 55s**: 43 tasks, 21 executed and 22 up-to-date. This covers the current Kotlin/resources, demo, form input/RTL changes, PDF cleanup, export/roster/navigation, and card controls. APK output metadata independently confirms `versionName = 1.1.1.20261007-debug` and `versionCode = 2`. Release is configured as `1.1.1.20261007` without a suffix; no release build was run. No toolchain/dependency or data-format versions changed. Gradle reports deprecated-feature usage; runtime/device/rendered behavior remains unverified. Earlier owner-supplied build notes are historical snapshots, superseded by this compilation/package result.

## Documentation discipline

- Update this file when the technical reality changes.
- Keep unfinished work in `workRemains.md` and completed work in `workDone.md`.
- Keep secrets and machine-specific instructions in ignored local files.
- Follow [AGENTS.md](AGENTS.md) for automated contribution rules.

The subsequent owner-requested Splash/About wording/order, shared automatic version, and visible countdown edits received source and whitespace checks only; the debug APK/build above predates these edits.
