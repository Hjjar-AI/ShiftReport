# Android build, cache and signing guide

Run/open `android/` as your ordinary account. These commands require [agent build approval/preflight](../../../AGENTS.md). [Evidence](../../status/current-state.md#verification-and-limits) · [Diagnostics](troubleshooting.md).

## Daily commands

Prerequisites: pinned Gradle 8.7/checksum, Java 17 target, Python 3, SDK 34/build tools, cached dependencies. System Gradle cannot replace the pin. Preserve versions; release needs [production signing](#production-release-signing).

```bash
./gradlew assembleDebug
./gradlew assembleRelease
# Both variants, one version reservation:
./gradlew assembleDebug assembleRelease
```

## Cached verification without packaging

Before every build, follow [AGENTS preflight](../../../AGENTS.md#authorization-and-execution): cross-account activity/RAM/swap, durable Java/Gradle/SDK/dependencies. Defer for active work/pressure/unreliable visibility; preserve others' work/caches. Install missing prerequisites once with approval. Keep full local logs and report task/path/result; compilation proves neither runtime nor test approval.

```bash
# Compile production source; no APK export or version reservation.
./gradlew --offline -Pshiftreport.autoVersion=false compileDebugKotlin > /tmp/shiftreport-compile.log 2>&1
```

## What is shared, and what is private

Inspect daemon-path access with `namei -l`/`getfacl`; shared-root writability is insufficient. Select private caches instead of repairing others' permissions. Custom `GRADLE_USER_HOME="$HOME/.gradle-shiftreport"` bypasses sharing and needs its pinned distribution. No sudo. Project/output paths are per-account: isolate parallel checkouts; preserve old shared caches/output.

| State/control | POSIX launcher default / override |
|---|---|
| Distribution/dependencies/daemons: `GRADLE_USER_HOME` | `/opt/android-shared/.gradle` only when unset or `$HOME/.gradle` and root/existing daemon directories allow read/write/traverse; otherwise normal/custom selection |
| Project/configuration cache: `--project-cache-dir /path` | `$HOME/.gradle/shiftreport-project-cache`; explicit option wins |
| Generated output: `-Pshiftreport.buildRoot=/path` | `$HOME/.gradle/shiftreport-project-cache/build-output`; projects map to `root`, `app`, etc. |
| APK copies: `-Pshiftreport.apkExportDir=/path` | XDG Desktop (`xdg-user-dir DESKTOP`), otherwise `$HOME/Desktop`, plus `/ShiftReport`; each variant has a subfolder |
| Debug identity | Selected shared key, independently of caches; see [debug signing](#shared-debug-signing) |

### Optional read-only dependency snapshot

For quiescent publication, ACLs/account checks/recovery, use the [workstation shared-cache procedure](/home/mhmmd/Desktop/%D8%AA%D9%82%D9%86%D9%8A%D8%A9/Programming/Android/Gradle%20Caches%20Across%20Linux%20Accounts.md). Keep writes account-local; never repair others' permissions or copy live metadata.

Standard `GRADLE_RO_DEP_CACHE` points to the parent of `modules-2`; no `gradle.readOnlyCache` alias. Gradle 8.7 needs compatible artifacts/`metadata-2.106`. Optional seed: `/opt/android-shared/dependency-seeds/shiftreport-v1`, with private `GRADLE_USER_HOME="$HOME/.gradle-shiftreport"` and pinned wrapper. Switch between builds only; missing artifacts need an approved persistent fill. This differs from the conditional shared-home default above.

## Find the APK

Assemble exports `Desktop/ShiftReport/<variant>/ShiftReport-v<version>-<variant>.apk`; split/unsigned suffixes remain, matching copies may be replaced. Release: same build root, `app/outputs/apk/release/`. Compile-only creates no APK; stale exports prove nothing. Debug/release share app ID, not necessarily signer. Open fallback: `xdg-open "$HOME/Desktop/ShiftReport"`; redirect: `-Pshiftreport.apkExportDir=/your/Desktop/ShiftReport`.

```text
$HOME/.gradle/shiftreport-project-cache/build-output/app/outputs/apk/debug/app-debug.apk
```

## Shared debug signing

Per-account `~/.android/debug.keystore` files differ; shared caches/groups do not align certificates. This checkout selects the matching `mhmmd` key through ignored `android/local.properties` below. Nonblank `SHIFTREPORT_DEBUG_KEYSTORE` wins; absolute/Android-relative paths accepted. Unreadable selection fails; no selection uses normal per-account signing. Standard debug alias/passwords stay separate from release.

Privately back up the original; never regenerate/overwrite it for updates. Recorded `.signing`: mhmmd:developer 2750, key 0640, inherited ACLs removed; trusted group/traversable parents required. Restart Studio after group/environment changes; compare SHA-256 with installed APK. Certificates/mhmmd access passed; work login/new build/update remain unverified. Reinstall risks data loss. [Android signing](https://developer.android.com/studio/publish/app-signing).

```properties
debug.keystore=.signing/debug.keystore
```

```bash
namei -l .signing/debug.keystore
getfacl -cp .signing .signing/debug.keystore
test -r .signing/debug.keystore
keytool -list -keystore .signing/debug.keystore -alias androiddebugkey -storepass android
/path/to/sdk/build-tools/VERSION/apksigner verify --print-certs /path/to/app-debug.apk
```

## Production release signing

Use protected `android/keystore.properties` or nonblank environment inputs; paths absolute/Android-relative. No debug fallback. Keep secrets in private local/CI storage outside Git/logs/history. Production key/artifact unverified; different-signer distribution needs an owner decision.

| Property | Environment |
|---|---|
| `storeFile` | `SHIFTREPORT_KEYSTORE_FILE` |
| `storePassword` | `SHIFTREPORT_KEYSTORE_PASSWORD` |
| `keyAlias` | `SHIFTREPORT_KEY_ALIAS` |
| `keyPassword` | `SHIFTREPORT_KEY_PASSWORD` |

## Automatic app patch and version-code increments

POSIX assemble/build/bundle/package/export (including `:app:`) atomically reserves one patch/code pair through [counter](../../../android/gradle/app-version.properties)/[helper](../../../android/gradle/next_app_version.py). Variants share it; failures/cancellation consume it. Parent-directory lock/atomic replace avoids private lockfiles. Format: `major.minor.patch` (e.g. `1.1.50`), debug suffix `-debug`; Android version code stays monotonic across display-format changes. Preserve gaps; recognize new flavor names. Synchronize counters across PCs before packaging; offline checkouts cannot guarantee unique codes. No auto-commit; counter changes may invalidate configuration cache.

| Control | Effect |
|---|---|
| `-Pshiftreport.autoVersion=false` | No reservation; use recorded version |
| Both `-Pshiftreport.versionCode=N` and `-Pshiftreport.versionName=X.Y.Z` | Explicit owner-managed pair; no counter advance; supplying only one fails |
| Help/version/listing, `--dry-run` / `-m` | No reservation |

## Overrides and Android Studio

Studio/direct Gradle/Windows `gradlew.bat` bypass POSIX defaults/reservation, using recorded versions. Configure JDK/home/cache/output separately; absent `shiftreport.buildRoot`, output is `build/`. Assemble/export still copies to Java user-home `Desktop/ShiftReport`; XDG discovery is POSIX-only.

## Reuse these preferences in future Android projects and PCs

Adapt using the workstation [tooling map](/home/mhmmd/Desktop/%D8%AA%D9%82%D9%86%D9%8A%D8%A9/%D9%85%D8%B4%D8%A7%D8%B1%D9%8A%D8%B9/Shared%20Android%20Tooling.md), [cache](/home/mhmmd/Desktop/%D8%AA%D9%82%D9%86%D9%8A%D8%A9/Programming/Android/Gradle%20Caches%20Across%20Linux%20Accounts.md) and [debug-signing](/home/mhmmd/Desktop/%D8%AA%D9%82%D9%86%D9%8A%D8%A9/Programming/Android/Shared%20Debug%20Signing%20Across%20Accounts.md) notes. Keep launcher/output/signing/export/counter blocks together; do not routinely regenerate a customized wrapper.

## Problems and fixes

| Symptom | Action |
|---|---|
| Wrapper downloads despite installed Gradle | Check the pinned 8.7 distribution in the selected user home; system Gradle is separate |
| Daemon/configuration-cache/output permission denied | Inspect the exact path; select private user home/project/output paths above, without broad permission repair |
| Configuration cache cannot serialize a script object | APK export uses built-in regex renaming and artifact providers; retain this when adapting the export code |
| Configuration cache invalidated after version properties changed | Expected recalculation, not proof of storage failure |
| `libsqlcipher.so` cannot be stripped, packaged as-is | Diagnose the actual failing task; this message alone is nonfatal |
| Gradle 9 deprecation warning under 8.7 | Not a reason to upgrade; an authorized diagnostic can add `--warning-mode all` |
| KSP reflection/lookup-cache failure | Follow [targeted KSP diagnostics](troubleshooting.md#ksp-cannot-access-filteroutputstreamout) |
| Signature mismatch | Compare installed/new certificates; use the original matching key |

Historical permission/import/Material opt-in/export failures were corrected. [Current state](../../status/current-state.md#verification-and-limits) owns successful-build evidence and later unverified changes.
