# Git and GitHub operations

ShiftReport adapts the repository tools and manual workflow infrastructure from Aqaqeer and Mukhtabir. Workflow files live at the repository root; Android commands use the nested `android/` project. [Build/signing](build.md) owns the existing local commands and identity; [remaining verification](../../status/testing-handoff.md) owns pending execution. Source preparation does not authorize dispatch.

## Local Git tools

`.gitattributes` defines LF text, CRLF Windows scripts and binary assets; `.editorconfig` guides future edits. Existing files were not mass-renormalized; an explicit attribute preserves the Windows wrapper’s existing CRLF Git blob byte-for-byte. CODEOWNERS routes review to the current remote owner; it does not configure required reviews or branch protection.

The source checker reads current tracked and unignored working-tree Android production files, shared documentation and repository configuration. It checks selected credential patterns, XML/JSON/TOML/Python syntax, repository-local Markdown links, the wrapper checksum and existing POSIX version-helper integration. Other clients, suites, migrations, schemas, private files, generated output and binary assets are excluded. Absolute workstation/sibling links are counted as external dependencies rather than checked. Findings redact credential values; detection is not a guarantee against arbitrary passwords or every secret format.

From the repository root:

```sh
python3 .github/scripts/check-repository.py
python3 .github/scripts/check-ci-policy.py
```

The second command needs PyYAML. Local syntax/policy checks do not install or download dependencies. Hosted workflow 00 uses a checksum-pinned YAML dependency and actionlint archive.

The optional source-only pre-push hook is active in the implementation checkout through local `core.hooksPath=.githooks`. A fresh clone must opt in explicitly:

```sh
git config --local core.hooksPath .githooks
```

The hook consumes push references, skips deletion-only pushes, and checks the **working tree**, not the exact commits being pushed. It performs no builds, tests or network operations. A clean working tree check cannot establish secret-free Git history. Keep source checks and reviewed diffs alongside the optional history scanner.

## Manual workflows

All definitions use `workflow_dispatch`; no push, pull-request or scheduled workflow triggers are configured. Most operations require `confirm=true`, default false, and the repository default branch. Repository integrity is a manual read-only selection without an extra confirmation. Weekly grouped Dependabot proposals cover GitHub Actions only; accepting changes remains a review decision. No automatic merge, dependency/toolchain upgrade or automatic cleanup is configured.

Jobs are restricted to public repositories and standard Ubuntu runners. Repository visibility, default-branch availability, billing eligibility and code-scanning settings were not queried. Private repositories deliberately skip these definitions until a separately reviewed policy change. Android work, signing/publication and debug-release cleanup share non-cancelling serialization; run cleanup has its own serialization. GitHub concurrency is not a remote multi-writer transaction guarantee.

| Workflow | Selection and result |
|---|---|
| 00 Workflow syntax | Checksum-pinned actionlint and parsed safeguards, including referenced composite actions. |
| 01 Repository integrity | Production/config/document checks described above; no Gradle/network. |
| 02 Dependency vulnerabilities | Resolve debug/release runtime, production KSP processors and build-plugin Maven coordinates; query OSV with pagination. Incomplete resolution/scans fail. Exact-coordinate exceptions require a reason and future UTC expiry; no upgrades. |
| 03 Compilation and JVM | `compile-debug` (default), `compile-release`, `jvm`, `all`. JVM selections exclude `LiveBootstrapOverrideTest` and clear live configuration inputs. Existing test sources are unchanged. |
| 04 Production lint | Production debug lint with test sources excluded. |
| 05 Security analysis | Java/Kotlin production extraction, Python helpers and Actions. Compilation extraction disables build-cache/task reuse; code-scanning eligibility remains external. |
| 06 Debug APK | Ephemeral-signer preview, checksums and source/run/attempt/version receipt. It cannot update a differently signed installed app. |
| 07 Emulator | `capture` (default), `instrumentation`, `both`; `api=both` (default), `23` or `35` selects serial AVDs. Visual capture and per-case test outcomes are separate phases. |
| 08 Release output | Unsigned minified release inspection, followed by API 35 startup/demo capture using a throwaway signer on a disposable AVD. No production distribution. |
| 09 Workflow-run cleanup | Manual preview-first cleanup of completed runs, default seven days; all workflows or emulator only. |
| 10 Signed debug delivery | Existing intended debug signer in an isolated job; artifact-only default with explicit optional publication. |
| 11 Signed production delivery | Separate production signer, unsigned build/inspection followed by isolated signing; artifact-only default. |
| 12 Build tooling | Two configuration-cache `help` invocations and an assembly dry-run; requires cache reuse and unchanged source version. This still invokes Gradle. |
| 13 History secrets | Checksum-pinned Gitleaks over fetched Android/shared-configuration history; suites/migrations/schemas and other clients excluded. Count-only public findings; raw reports/logs remain ephemeral. No all-history/unreachable-object guarantee. |
| 14 Debug Release cleanup | Preview-first, marker/run/attempt/source-bound published debug Releases and lightweight tags; default older than 30 days, retain at least five newest. |
| 15 Actionlint updates | Propose a newer stable upstream pin as an artifact patch; do not apply it or execute the proposed tool. |

Every Gradle operation preserves the existing source version with `-Pshiftreport.autoVersion=false`, explicit hosted build/cache/export directories, one worker and resource/process preflight. The shared workstation is not reconfigured. Toolchain pins remain Gradle 8.7, the existing Android/Kotlin plugins, SDK/build tools 34 and Java target 17; hosted execution selects JDK 21.

The hosted-only init script disables release signing until the isolated signing job and excludes the live-bootstrap JVM class without editing app/test sources. Wrapper and application version files are preserved. Builds fail if the recorded version bytes change. Fresh reports come from the redirected module output, not stale checkout-local `app/build` folders.

Artifact names include source commit, run and attempt; retention is one day. Export needed APKs, mappings and evidence before expiry or cleanup. Actions pins and scanner checksums are adopted source pins, not evidence that their downloads executed successfully in this repository.

## Emulator selections and evidence

Workflow 07 uses only owned ephemeral AVDs at `emulator-5554`. It verifies hosted runner, AVD/API/QEMU/user identity, absence of existing packages, APK signer/package and installed bytes. Android6 uses its supported user-list command, normalizes legacy shell CR/LF, and requires exactly one registered/running user0 record; newer APIs query the current user directly. Installation identity handles the documented `userId`/`appId` dump fields without relaxing UID, code-path or timestamp checks. Safe `ownership.json` and `phase-errors.json` diagnostics retain failures that occur before a phase can produce results; user-list observations include parsed IDs/running states and carriage-return presence, never user names. The [official ADB source](https://android.googlesource.com/platform/tools/base/+/HEAD/adblib/src/com/android/adblib/AdbDeviceServices.kt) documents CRLF normalization for API23 and earlier. Preserve unchanged successful jobs by selecting only the affected API on the updated default-branch source; invalid API input fails matrix evaluation. Cleanup rechecks installation identity; ambiguous outcomes fail and never justify clearing another installation.

Capture follows the actual fresh-install setup screen's default demo choice and visible start button. It records startup/demo/large-text PNGs, a short video and provenance. It does not inject a new navigation shortcut or alter production UI. Release startup additionally observes process survival for 30 seconds; this is not update acceptance.

Instrumentation uses seven reviewed classes enumerated in `.github/instrumentation-selection.json`: project cipher, protected provisioning, password hashing, the two demo classes, Android PBKDF2 compatibility and report-selection components. The inventory contains 59 explicitly selected methods per API (118 invocations across API23/35, 59 unique methods). Missing, unexpected, skipped or failed cases fail the phase. Neither instrumentation nor this selection covers physical biometrics, interactive document providers, live Telegram, database/migration suites or all application behavior. The owner-authorized focused test expansion adds new cases and repairs stale fixtures; these source selections are not executed results. Network radios are requested off and read back; this is not packet-level isolation proof. No competing UiAutomator observation runs during instrumentation.

## Signing configuration

Local verification on 15 November 2026 successfully opened the existing configured debug alias and verified private-key access with Android's standard debug credentials. No private key was exported, modified or uploaded, and no password is recorded here. Private receipt: `local/verification/github-adoption-2026-11-15/debug-key-verification.json`. This establishes local key access only; hosted secrets and installed-update continuity remain separate.

Preserve that original debug identity for installed-app updates. Release signing may use a different certificate and therefore cannot automatically update a debug-signed installation with the same package. Do not uninstall or clear clinical data to bypass a mismatch. Keep protected off-device key backups.

The owner configures GitHub environments `debug-signing` and `release-signing` later, restricting each to the actual default branch and applying any desired approval reviewers. Each environment uses these same names with its own values:

| Type | Name |
|---|---|
| Secret | `SHIFTREPORT_SIGN_KEYSTORE_BASE64` |
| Secret | `SHIFTREPORT_SIGN_KEY_ALIAS` |
| Secret | `SHIFTREPORT_SIGN_STORE_PASSWORD` |
| Secret | `SHIFTREPORT_SIGN_KEY_PASSWORD` |
| Environment variable | `SHIFTREPORT_SIGN_CERT_SHA256` — public SHA-256 certificate fingerprint, not a key/APK hash |

Set repository variable `SHIFTREPORT_DEBUG_SIGNING_READY=true` or `SHIFTREPORT_RELEASE_SIGNING_READY=true` only after the corresponding environment is ready. Missing/other values stop delivery before building. Never put keys/passwords in Git, issues, artifacts, command output or this guide.

Delivery builds without private signing inputs. A separate environment-backed signing job downloads only its current run/attempt/source-named artifact, verifies checksums and packaged identity/version, signs using a private temporary keystore, verifies the expected certificate, then removes that temporary key. Signing jobs run neither Gradle nor Gradle caches. Signed bundles contain the APK, public certificate/version/source/run receipt, checksums and release mapping where applicable.

Publication modes are `artifact` (default), `draft`, `prerelease`, `release`. Only publication receives repository write permission; it receives no signing secrets. Existing Releases/tags are never overwritten. All debug modes use `latest=false`; only a regular production release may become latest. Debug tags bind run/attempt; production tags bind source version/code. Repeated source versions are not a reservation system: a production version increase remains separately authorized work. Unknown publication outcomes require inspection before a new explicit attempt.

## Cleanup boundaries

Workflow 09 defaults to `dry_run=true`, `older_than_days=7`, `scope=all-workflows`. Days 0 removes only the age restriction. Deleting a completed run deletes its attempts/logs/summaries and attached artifacts, including APKs. Active/current runs, repository files, Releases/assets and caches are preserved. The helper snapshots paginated candidates and rechecks repository/run/workflow/attempt/update identity immediately before deletion, then confirms absence with reads only. Separate REST reads/deletion cannot eliminate races with an external rerun; keep candidates quiescent.

Workflow 14 defaults to `preview=true`, `older_than_days=30`, `keep=5`. It selects only bot-authored published debug Releases with this setup's ownership marker and a matching lightweight tag/source identity. Drafts, production, immutable, recent, retained-newest and foreign/unmarked Releases remain. Release assets are part of deletion. Fresh release/asset/tag checks precede deletion; ambiguous outcomes fail without automatic mutation retries.

A preview is not a frozen approval for a later run. Inspect each selected deletion dispatch and export needed evidence first. No cleanup, publication, hosted settings change or workflow execution was performed while preparing this setup.
