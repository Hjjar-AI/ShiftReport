# Android UI, reliability and security review disposition

Reviewed against the working source on 2026-10-22, replacing the supplied
`android/cceckk.md`. This is a source review, not a build, device assessment or
confirmation of the supplied file counts. Repeated executive-summary, roadmap,
snippet and device-check recommendations refer to the decisions below.

## Reliability and security

| Finding | Source decision and result |
|---|---|
| C1: hidden login seeder | Confirmed and removed from LoginScreen/ViewModel/state, including its now-unused hold control. BootstrapSeeder remains necessary for Create; it rejects initialized/demo projects and checks for an existing pin before upload and again before pinning. Setup already rejects an existing pin. Login retry only retrieves. Telegram still cannot make the final check/write atomic. |
| C2: encrypted preference failure and missing database key | Confirmed. StorageAccessGate checks key/project/session storage on IO before repository-backed ViewModels. An existing live database is opened on IO to catch unusable storage. Failure shows an Arabic, scrollable retry/preservation screen without exception details or deletion. DatabaseKeyProvider refuses a new key if the database or WAL/SHM/journal exists. |
| C2: restore/refetch and security-crypto replacement | An inaccessible key cannot be recovered by inventing another key. The screen preserves files and explains retry/restart/admin recovery; it does not claim to recover lost unsent edits, offer an automatic reset, or bypass encryption. No crypto-library replacement or storage conversion was performed under the no-version/no-migration rules. |
| C3: screenshots/Recents | Confirmed absent in MainActivity. FLAG_SECURE is now set before content; the existing lifecycle cover remains. External PDF viewers/exported files are outside this window's control. Device/OEM behavior remains unverified. |
| C4: suite coverage | The supplied coverage counts were not verified: test-suite inspection/work was excluded by the repository instructions. No coverage claim or new suite was made. |
| C4: unverified build and old opt-in failure | Historical facts, not proof of a current compilation failure. Existing source fixes remain; no build/device run was authorized. |
| C4: debug-signed release | Confirmed. Release now selects a separate signing configuration from ignored local properties or CI environment variables. No debug-key fallback remains. An owner-supplied production key and actual signing validation are still required. |
| H1: readable Telegram PDFs/captions | Deliberate documented product behavior. No PDF password/identifier/protocol change; hospital policy cannot be established from source. |
| H1: PDF preview cache retention | Confirmed. Reviewed files are deleted asynchronously on replacement, failed/unavailable preview, review invalidation and ViewModel clearance; cancellation cleans unadopted files. App startup and sign-out clean private preview/shared-report caches. Active reviewed PDFs remain available for the existing hash-checked send. Explicit exports are retained. Cleanup is best effort, not revocation of copies already opened elsewhere. |
| H2: last-write-wins | True and explicitly accepted. Existing replacement review and local/registry-pointer checks remain; no atomicity/backend claim. A remote pointer is rechecked during publication, not a permanently live UI feed. A new publisher identity/history feature is outside a defect fix and would require trustworthy existing data. |
| H3: PBKDF2 120,000 versus desktop 600,000 | Source values confirmed. Blindly replacing the Android constant would invalidate all current PIN hashes: PasswordHasher stores salt/hash without an iteration field. No hash-format conversion, credential policy change or speculative crypto rewrite was made. Persistent login throttling remains. |
| H3: four-to-eight-digit PINs | Owner explicitly chose to retain 4–8 digits. No enrollment/verification limit or hash format changed. |
| H3: SDK/compiler age and Play policy | Versions are source facts, not evidence of this app's distribution target or a current source defect. No SDK/Kotlin/Compose/library versions changed, as instructed. No current Play compliance claim is made. |

## Design components (D1–D10)

| Finding | Source decision and result |
|---|---|
| D1: line heights/font/12sp clinical text | Confirmed short body/label line heights; increased shared body/title-small/label spacing. Notices now use 14sp body text even when compact. Clinical body content remains available at its existing body roles. The owner chose a bundled font. Shared Compose typography now uses the existing licensed Tajawal Regular/Bold faces, relocated to res/font and shared with PDF export. UI font loading is asynchronous and offline; explicit monospace fields remain monospace. Small secondary labels are retained. Font binaries/license are unchanged; rendered shaping and wrapping remain unverified. |
| D2: tight density | Confirmed. Normal cards now use 12dp horizontal/8dp vertical padding and 8dp section/list gaps. Compact internal density stays 4/2dp with 4dp section gaps. |
| D3: name width | Confirmed 48dp noninteractive ordinal plus two 48dp actions. Ordinal is now 32dp; expand/menu targets remain 48dp. Pinning was already in the menu, so no duplicate pin button was removed or merged. |
| D4: star-only priority | Confirmed; added a visible Arabic priority label beside the decorative star. Identity metadata can wrap. |
| D5: count grammar | Confirmed; card badge/hidden-badge/pending/overdue counts and task-board total use six-form Arabic resources. uiPlural explicitly selects Arabic grammar even on a non-Arabic system locale and passes Arabic-formatted digits. |
| D6: literal strings | True as a general source characteristic; the supplied counts were not used as evidence of defects. Count grammar is corrected. Repository-wide resource extraction is an optional localization refactor, not a correctness fix; no parallel string system was imposed. |
| D7: identical Material color roles/tinted dark surfaces | Confirmed; secondary is neutral, tertiary distinct, and Android dark foundations neutral across accents. Primary accent IDs, appearance choices and clinical severity mappings are retained. |
| D8: transparent diagnosis chip | Confirmed; added a neutral outline. No clinical error color is used. |
| D9: notices | Confirmed polite-only announcements and 12sp compact style; errors now announce assertively and messages use body text. Automatic dismissal/one-banner enforcement would hide caller-owned clinical/error information; not applied. Dismissibility belongs to individual message lifecycle, including the existing dismissible fetch reminder. |
| D10: metric fills | Nonurgent rows now use neutral surfaces and accented counts; urgent/warning rows retain semantic fills. INFO previously used a neutral variant, not a severity warning color. |

## Layout and interaction (section 4)

| Area / individual recommendations | Source decision and result |
|---|---|
| 4.2 FAB/report/Add priority | Owner chose Add patient. Ward/demo floating actions now open the existing patient-entry form with «إضافة مريض». Ward respects current-shift/read-only/loading/save/sync guards and measured dock clearance; the empty ward retains its single inline Add action. Toolbar Add is hidden when the floating Add is present. «إرسال تقرير» remains in the drawer and opens report review. |
| 4.2 dock height/hide on scroll | The supplied height is not a device measurement. Reduced decorative indicator/gutter padding; retained labelled 48dp items. No hide-on-scroll behavior that removes frequent navigation. |
| 4.2 top-bar expand-all/separation | Expand-all already has a meaningful accessibility label; its top-bar placement is explicitly required. No move to list controls. Divider/elevation-on-scroll is an optional visual proposal, not a demonstrated defect. |
| 4.2 drawer count/duplicates/filter location | Source has one action enum/handler and five collapsed categories with admin filtering. Direct navigation and the filter shortcut are deliberate; no loss of these entry points. The supplied action count does not establish unusability. |
| 4.2 drawer icon reuse/20dp | Reuse beside distinct Arabic labels is not a correctness bug. Replaced the three raw 20dp dimensions with UiSize.iconMedium. |
| 4.2 rail/dock | Retained the appropriate Material rail; rail icons now use the dock's outlined-inactive/filled-selected convention. |
| 4.2 dashboard | Removed duplicate heading. Attention metrics already lead and suppress zero urgent/overdue rows; ordinary totals remain grouped and actionable. No invented combined urgency score. |
| 4.2 workflow strip | Removed repeated condition. Current toolbar already limits this to busy/uncertain cases; routine state lives with list content. Uncertain delivery keeps its warning/action rather than being hidden in a status chip. |
| 4.2 patient editor | Fixed keyboard-safe Save/Cancel, completion hints and section shortcuts already exist. A new scroll-spy is an optional feature, not a missing save guard. |
| 4.2 report state/stepper/disabled reason | Owner chose Doctors → Destination → Preview → Send. ReportFlowStep and ReportPreviewScreen now show these four stages with fixed Previous/Next or final Send controls, per-stage scroll positions and visible disabled reasons. Roster editing remains available first; destination/format and optional PDF customization retain their existing handlers. Progressing past Preview requires the text or all selected current PDF files to have been opened. Existing publication confirmation and backend stale/exact-file/delivery guards remain. |
| 4.2 tablet/fold thresholds | Existing purpose-specific thresholds and fold handling retained. Flicker is a device observation not established by source. |
| 4.3 task board: overdue/grouping | Added urgent surface/icon and sticky Overdue/Today/Later/No-deadline sections. Uses shared overdue rules; overdue remains a subset of pending. |
| 4.3 task board: filters/close/component/cache/numbers | Filter chips retain dropdown selection and add optional My tasks for the current doctor. A growing top bar contains Close. Uses AppCard, uiNumber/uiPlural and remembered names, rows and grouping. No task ownership requirement or completion mutation added. |
| 4.4 details bars/title/counts | Replaced wrapping section chips with one horizontally scrollable row; rounds navigation stays accessible. Replaced fixed-height TopAppBar with AppCompactTopBar. Counts use uiNumber and History counts actual matching entries, without inventing one. Rounds auto-collapse is optional and unimplemented. |
| 4.5 disabled doctor rows/filtering | Dimmed disabled rows; clickable supplies disabled semantics and Button role. Matching doctors are remembered. Last-login storage and team-grouping are optional login features; no new device identity history added. |
| 4.5 verification nonce/timer/actions | Nonce now has LTR text direction; countdown uses uiNumber. Kept the exact nonce for both display/copy to avoid whitespace changing manually sent codes. Circular progress and copy/open-Telegram are optional features, not broken verification. |
| 4.5 bootstrap failure | Replaced loose content with scrollable padded AppCenteredContent and an error notice plus ordinary retry. |
| 4.6 splash | White emblem field, credits and launch behavior are explicit owner preferences. No shrink/recolor/credits frequency/system-splash dependency change. Current source uses the saved three-second deadline, not the older five-second description. |
| 4.7 setup encryption/help/chat ID | Encryption explanation is essential to the deliberate PDF exception and already sits in a focused section with optional setup help. No extra modal. Read-only group ID now uses uiNumber; editable technical IDs remain unchanged. |
| 4.7 sort wording | Removed legacy Excel wording from the default-order button; ordering behavior unchanged. |
| 4.8 AppField IME/error | Confirmed. Focused-only snapshotFlow/collectLatest observes IME outside composition. Real validation messages now reach error semantics through patient fields/pickers, task description and doctor Telegram ID; generic fallback remains for callers without a message. Shared section/card/destination components and RTL chevrons retained. |
| 4.9 filters | Existing result/active-filter summary and clear action are intentional (see current-state visual-polish notes). No duplicate removable-chip row. |
| 4.9 sticky patient groups/scroll-to-top/position | Group headers already expose collapse/count; return position/reveal behavior already exists. New sticky grid headers and a top button are optional, not evidenced defects. Task-board headers are sticky. |
| 4.9 Undo/skeletons/grid motion/swipes/haptics | Optional interaction additions. Existing reviewed mutations, deletion Undo, brief individual/immediate bulk expansion, and menus remain. No new clinical undo protocol or gesture-only actions. System animation behavior/TalkBack require runtime verification; no platform compliance claim. |
| 4.10 surfaces/icons/shapes/dividers/spacing | Existing surface/card/selected roles and shape tokens are retained. Neutral dark/card roles and metric emphasis improved above; rail selected icons aligned. Global icon/divider/shape replacement is a redesign. Added 16/24dp section tokens to the catalog; card/action spacing remains compact. |
| 4.11 numbers/dates/microcopy | Corrected the named login/detail/setup/task-board misses, Arabic task metadata presentation and full card edit date (removed the yearless MM-dd override). Existing shared labels/publication wording and explicit absolute deadline data remain. No stored/exported dates or clinical text rewritten. Broad copy shortening is subjective. |

## Structure and performance (P1–P6)

| Finding | Source decision and result |
|---|---|
| P1: list stability and all-card recomposition | Ordinary List parameters and many callbacks exist, but the claimed runtime impact was explicitly speculative. Memoized card task counts/badge filtering. No false Immutable annotation over mutable collections, new action/state API or compiler upgrade. |
| P2: quadratic grid membership and pin sort | Confirmed; remembered ID/pin hash sets replace deep list membership. Filtered/sorted visible groups are remembered outside the lazy content builder; ordering and collapse behavior retained. |
| P3: state-holder extraction | State counts/line counts alone are not defects. Current focused navigation/action modules already exist. No broad screen rewrite without an evidenced state-loss issue. |
| P4: previews | Confirmed no source previews; added an in-memory gallery for collapsed/expanded long-name cards, notices, empty state and real task-row rendering, with Arabic RTL, dark and 2× text variants. Previews have not been rendered. Suite/device work remains excluded. |
| P5: dimensions/resources | Corrected the specified drawer dimensions, ordinal size, shared density/spacing and plural resources. Hardcoded dp/string counts alone do not justify rewriting every purpose-specific value/literal. |
| P6: TalkBack/decorative descriptions | Potential duplicate announcements remain a device-verification item, not confirmed behavior. Existing custom actions/clinical content remain accessible; decorative adjacent icons remain undescribed. |

## Verification limits

Production-source call sites, imports, new helper parameters, XML plural forms,
protected signing inputs, cancellation/cleanup ownership and key preservation were
inspected, with lightweight delimiter/reference/contrast checks and Git whitespace
checks. No Gradle, builds, compilation, packaging, test-suite/migration review,
physical device, rendered preview, hospital-policy or live Telegram work ran.
Source changes do not establish compilation, rendered accessibility, keystore
recovery, successful file deletion or release signing on a device.
