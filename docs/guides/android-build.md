# Android build, cache and signing guide

Run from `android/` as your ordinary account; open that directory in Studio. [Current verification](../status/current-state.md#verification-and-limits) · [App diagnostics](android-troubleshooting.md) · [Windows EXE](windows-exe-packaging.md). Agent builds require explicit owner permission; these commands are reference only.

## Daily commands

From `android/`: pinned Gradle 8.7/checksum, Java 17 target, Python 3, SDK 34/build tools and cached dependencies. System Gradle does not replace the pinned distribution. Preserve versions; release needs [production signing](#production-release-signing).

```bash
./gradlew assembleDebug
./gradlew assembleRelease
# Both variants, one version reservation:
./gradlew assembleDebug assembleRelease
```

## Cached verification without packaging

Owner approval required. Check visible builds/compilers/IDE across accounts, available RAM/swap against heaps, Java/pinned Gradle/SDK/dependencies. Defer for active work, pressure or unreliable visibility; never kill others or clear caches. Missing prerequisites need one approved durable installation. Keep full logs locally; report task/path/result. Compilation proves neither rendering nor runtime and does not authorize test work.

```bash
# Compile production source; no APK export or version reservation.
./gradlew --offline -Pshiftreport.autoVersion=false compileDebugKotlin > /tmp/shiftreport-compile.log 2>&1
```

## What is shared, and what is private

Shared-root writability does not imply access to another account’s daemon registry: inspect `namei -l`/`getfacl`; choose private caches rather than repairing others’ permissions. Custom `GRADLE_USER_HOME="$HOME/.gradle-shiftreport"` bypasses automatic sharing and needs its own pinned distribution. No normal build needs sudo. Project/output paths are per-account, not per-checkout: isolate parallel checkouts; leave old shared caches/output untouched.

| State/control | POSIX launcher default / override |
|---|---|
| Distribution/dependencies/daemons: `GRADLE_USER_HOME` | Uses `/opt/android-shared/.gradle` only if unset or `$HOME/.gradle`, and the shared root plus existing daemon directories are readable/writable/traversable; otherwise retains normal/custom selection |
| Project/configuration cache: `--project-cache-dir /path` | `$HOME/.gradle/shiftreport-project-cache`; explicit option wins |
| Generated output: `-Pshiftreport.buildRoot=/path` | `$HOME/.gradle/shiftreport-project-cache/build-output`; projects map to `root`, `app`, etc. |
| APK copies: `-Pshiftreport.apkExportDir=/path` | XDG Desktop (`xdg-user-dir DESKTOP`), otherwise `$HOME/Desktop`, plus `/ShiftReport`; each variant has a subfolder |
| Debug identity | Selected shared key, independently of caches; see [debug signing](#shared-debug-signing) |

### Optional read-only dependency snapshot

For publishing a quiescent snapshot, ACLs, account checks and recovery, use the workstation notes' [shared-cache procedure](/home/mhmmd/Desktop/%D8%AA%D9%82%D9%86%D9%8A%D8%A9/Programming/Android/Gradle%20Caches%20Across%20Linux%20Accounts.md). Keep writable state account-local; never repair another user's home/cache permissions or copy live metadata.

ShiftReport selects standard `GRADLE_RO_DEP_CACHE` at the parent of `modules-2`; it has no `gradle.readOnlyCache` alias. Gradle 8.7 needs compatible artifacts and metadata (`metadata-2.106`). Example shared seed: `/opt/android-shared/dependency-seeds/shiftreport-v1`, with private `GRADLE_USER_HOME="$HOME/.gradle-shiftreport"`. A new user home still needs its pinned wrapper distribution. Switch only between builds; missing artifacts need an approved persistent cache fill. This is optional, not the launcher's conditional shared-home default above.

## Find the APK

Assemble exports `Desktop/ShiftReport/<variant>/ShiftReport-v<version>-<variant>.apk` using the selected Desktop; split/unsigned suffixes remain and matching copies may be replaced. Release uses the same build root’s `app/outputs/apk/release/`. Compile-only creates no APK; stale exports prove nothing. Debug/release share app ID; signer still matters. Fallback opener: `xdg-open "$HOME/Desktop/ShiftReport"`; redirect with `-Pshiftreport.apkExportDir=/your/Desktop/ShiftReport`.

```text
$HOME/.gradle/shiftreport-project-cache/build-output/app/outputs/apk/debug/app-debug.apk
```

## Shared debug signing

Accounts normally have separate `~/.android/debug.keystore`; caches/groups do not align certificates. This checkout uses the existing matching `mhmmd` key through ignored `android/local.properties` below. Nonblank `SHIFTREPORT_DEBUG_KEYSTORE` wins; paths may be absolute/Android-relative. Selected unreadable keys fail; absent selection uses normal per-account signing. Standard debug alias/passwords remain separate from release. Copy/backup the original key privately; never regenerate/overwrite it to fix updates. Current `.signing`: mhmmd:developer 2750, key 0640, inherited ACLs removed; trusted group/traversable parents required. Restart Studio after group/environment changes. Compare SHA-256 against installed APK. Existing certificates/mhmmd access passed; work login and its new build/update remain unverified. Reinstall can lose data. [Android signing](https://developer.android.com/studio/publish/app-signing).

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

No debug fallback. Protected `android/keystore.properties` or nonblank environment inputs below; paths absolute/Android-relative. Keep secrets outside Git/logs/history in private local/CI storage. No production key/artifact verified. Different-signer distribution requires an owner decision; debug signing is unsuitable.

| Property | Environment |
|---|---|
| `storeFile` | `SHIFTREPORT_KEYSTORE_FILE` |
| `storePassword` | `SHIFTREPORT_KEYSTORE_PASSWORD` |
| `keyAlias` | `SHIFTREPORT_KEY_ALIAS` |
| `keyPassword` | `SHIFTREPORT_KEY_PASSWORD` |

## Automatic app patch and version-code increments

POSIX packaging invocations (assemble/build/bundle/package/export, including `:app:`) reserve one patch/code pair atomically through [counter](../../android/gradle/app-version.properties)/[helper](../../android/gradle/next_app_version.py). Both variants share it; failures/cancellation consume it. Parent-directory lock plus atomic replace avoids private lockfiles. Format `major.minor.patch.YYYYMMDD`, build-start Asia/Damascus date; debug adds `-debug`. Preserve gaps; new flavor names need recognition. Synchronize tracked counters across PCs before packaging; offline checkouts cannot guarantee globally unique codes. No auto-commit; counter changes may invalidate configuration cache.

| Control | Effect |
|---|---|
| `-Pshiftreport.autoVersion=false` | No reservation; use recorded version |
| Both `-Pshiftreport.versionCode=N` and `-Pshiftreport.versionName=X.Y.Z.YYYYMMDD` | Explicit owner-managed pair; no counter advance; supplying only one fails |
| Help/version/listing, `--dry-run` / `-m` | No reservation |

## Overrides and Android Studio

Studio/direct Gradle/Windows `gradlew.bat` bypass POSIX defaults/reservation and use recorded versions. Configure JDK/home/cache/output separately. Without `shiftreport.buildRoot`, normal `build/` applies. Assemble/export still copies APKs to Java user-home `Desktop/ShiftReport`; XDG discovery is POSIX-only.

## Reuse these preferences in future Android projects and PCs

Adaptation details now live in the workstation notes: [cross-project tooling map](/home/mhmmd/Desktop/%D8%AA%D9%82%D9%86%D9%8A%D8%A9/%D9%85%D8%B4%D8%A7%D8%B1%D9%8A%D8%B9/Shared%20Android%20Tooling.md), [shared-cache procedure](/home/mhmmd/Desktop/%D8%AA%D9%82%D9%86%D9%8A%D8%A9/Programming/Android/Gradle%20Caches%20Across%20Linux%20Accounts.md) and [shared debug-signing procedure](/home/mhmmd/Desktop/%D8%AA%D9%82%D9%86%D9%8A%D8%A9/Programming/Android/Shared%20Debug%20Signing%20Across%20Accounts.md). Keep this project's launcher/output/signing/export/counter blocks together when adapting; do not regenerate a customized wrapper as routine repair.

## Problems and fixes

| Symptom | Action |
|---|---|
| Wrapper downloads despite installed Gradle | Check the pinned 8.7 distribution in the selected user home; system Gradle is separate |
| Daemon/configuration-cache/output permission denied | Inspect the exact path; select private user home/project/output paths above, without broad permission repair |
| Configuration cache cannot serialize a script object | APK export uses built-in regex renaming and artifact providers; retain this when adapting the export code |
| Configuration cache invalidated after version properties changed | Expected recalculation, not proof of storage failure |
| `libsqlcipher.so` cannot be stripped, packaged as-is | Diagnose the actual failing task; this message alone is nonfatal |
| Gradle 9 deprecation warning under 8.7 | Not a reason to upgrade; an authorized diagnostic can add `--warning-mode all` |
| KSP reflection/lookup-cache failure | Follow [targeted KSP diagnostics](android-troubleshooting.md#ksp-cannot-access-filteroutputstreamout) |
| Signature mismatch | Compare installed/new certificates; use the original matching key |

Historical permission/import/Material opt-in/export failures were corrected; they are not an active checklist. Successful-build evidence and subsequent unverified changes have one home: [Current state](../status/current-state.md#verification-and-limits).
