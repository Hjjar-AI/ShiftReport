# Android build, cache and signing guide

Run from `android/` as your ordinary account; open that directory in Studio.
[Current verification](../status/current-state.md#verification-and-limits) ·
[App diagnostics](android-troubleshooting.md) · [Windows EXE](windows-exe-packaging.md).
Agent builds require explicit owner permission; these commands are reference only.

## Daily commands

```bash
./gradlew assembleDebug
./gradlew assembleRelease
# Both variants, one version reservation:
./gradlew assembleDebug assembleRelease
```

Use the pinned wrapper (Gradle 8.7/checksum), Java 17, Python 3 and Android SDK 34
with installed build tools/dependencies. A system `gradle` of another version does
not replace the wrapper distribution. Keep toolchain/app versions unchanged unless
requested. Release requires [production signing](#production-release-signing).

## Cached verification without packaging

Before an authorized compile, check active builds/compiler work across visible
accounts/projects, available RAM/swap and configured heap needs. An idle daemon or
cached RAM alone is not pressure. If another build is active, memory is tight or
visibility is unreliable, defer verification; never kill others' work or clear caches.
Preflight Java, the pinned distribution, SDK/tools and durable cached dependencies.
Missing prerequisites need one approved persistent installation, not temporary downloads.

```bash
# Compile production source; no APK export or version reservation.
./gradlew --offline -Pshiftreport.autoVersion=false compileDebugKotlin > /tmp/shiftreport-compile.log 2>&1
```

Keep the full log locally; report the failing task/path and concise result. Use the
cache/output overrides below if isolation is needed. Compilation does not verify
rendering, device behavior or Telegram. Builds do not authorize test-suite work.

## What is shared, and what is private

| State/control | POSIX launcher default / override |
|---|---|
| Distribution/dependencies/daemons: `GRADLE_USER_HOME` | Uses `/opt/android-shared/.gradle` only if unset or `$HOME/.gradle`, and the shared root plus existing daemon directories are readable/writable/traversable; otherwise retains normal/custom selection |
| Project/configuration cache: `--project-cache-dir /path` | `$HOME/.gradle/shiftreport-project-cache`; explicit option wins |
| Generated output: `-Pshiftreport.buildRoot=/path` | `$HOME/.gradle/shiftreport-project-cache/build-output`; projects map to `root`, `app`, etc. |
| APK copies: `-Pshiftreport.apkExportDir=/path` | XDG Desktop (`xdg-user-dir DESKTOP`), otherwise `$HOME/Desktop`, plus `/ShiftReport`; each variant has a subfolder |
| Debug identity | Selected shared key, independently of caches; see [debug signing](#shared-debug-signing) |

A writable shared root can contain another account's private `daemon/8.7` registry.
Inspect the failing path with `namei -l`/`getfacl`; use your own writable cache rather
than changing another account's permissions. To bypass automatic shared selection,
choose a custom user home, e.g. `export GRADLE_USER_HOME="$HOME/.gradle-shiftreport"`.
It needs its own accessible pinned distribution; a dependency snapshot does not
contain wrapper distributions. No ordinary build needs `sudo`.

Project caches/output are per account, not automatically per checkout. Give parallel
checkouts distinct `--project-cache-dir` and `shiftreport.buildRoot` paths. Leave old
shared `.gradle`/`app/build` directories untouched; other callers may still use them.

### Optional read-only dependency snapshot

For two accounts, prefer private writable user homes with a published read-only
snapshot. This is an **optional setup**, not the launcher's current automatic mode.
Gradle's standard `GRADLE_RO_DEP_CACHE` points to the parent of `modules-2`; ShiftReport
has no `gradle.readOnlyCache` local-property alias. [Official cache guidance](https://docs.gradle.org/current/userguide/dependency_caching.html).

A maintainer prepares a shared parent outside private homes, accessible to the trusted
group. From a quiescent compatible cache, copy artifacts **and metadata**, excluding
locks/GC state; Gradle 8.7 uses `metadata-2.106`. Coordinate writers without stopping
unrelated builds. Example publisher recipe; adapt paths/group and use a fresh name:

```bash
# Parent must already be writable by the publisher and traversable by android-dev.
python3 - <<'PY'
from pathlib import Path
import grp, os, shutil, subprocess, tempfile
source = Path('/path/to/quiescent/cache/modules-2')
parent = Path('/opt/android-shared/dependency-seeds')
target = parent / 'shiftreport-v1'
assert (source / 'files-2.1').is_dir(), 'Select a populated cache'
assert (source / 'metadata-2.106').is_dir(), 'Select Gradle 8.7-compatible metadata'
assert not target.exists(), 'Choose a fresh snapshot name'
gid = grp.getgrnam('android-dev').gr_gid
staging = Path(tempfile.mkdtemp(prefix='.preparing-', dir=parent))
shutil.copytree(source, staging / 'modules-2',
                ignore=shutil.ignore_patterns('*.lock', 'gc.properties'))
# Strip inherited/copied ACLs only on the new staging tree; requires setfacl.
subprocess.run(['setfacl', '-Rb', str(staging)], check=True)
subprocess.run(['setfacl', '-Rk', str(staging)], check=True)
for item in [*staging.rglob('*'), staging]:
    os.chown(item, -1, gid)
    os.chmod(item, 0o550 if item.is_dir() else 0o440)
staging.rename(target)
PY
```

Publish only when copying/access setup succeeds; retain failed staging for diagnosis.
Check inherited ACLs so only trusted users can read and consumers cannot write.
In **each account's login**, select and inspect the snapshot before starting Studio:

```bash
export GRADLE_USER_HOME="$HOME/.gradle-shiftreport"
export GRADLE_RO_DEP_CACHE=/opt/android-shared/dependency-seeds/shiftreport-v1
namei -l "$GRADLE_RO_DEP_CACHE/modules-2/files-2.1"
getfacl -cp "$GRADLE_RO_DEP_CACHE" "$GRADLE_RO_DEP_CACHE/modules-2/files-2.1"
test -r "$GRADLE_RO_DEP_CACHE/modules-2/files-2.1"
```

Confirm metadata/artifact readability too; group changes need a fresh login. Do not
select a live writable cache as the snapshot. Missing dependencies need an approved
online fill into a durable writable cache, then a fresh snapshot; switch between
builds and retain the old one until unused. Private dependencies require trusted access.

## Find the APK

Successful assemble exports `Desktop/ShiftReport/<variant>/ShiftReport-v<version>-<variant>.apk`,
using the selected Desktop above. Split/unsigned suffixes remain; the same
version/variant may replace its previous copy. Original debug output defaults to:

```text
$HOME/.gradle/shiftreport-project-cache/build-output/app/outputs/apk/debug/app-debug.apk
```

Release is under `app/outputs/apk/release/` of the same build root. Compile-only work
creates no APK; an old Desktop copy after failure is not new output. Filenames do
not change application ID or signer: debug/release currently share the app ID.
Use your file manager, or `xdg-open "$HOME/Desktop/ShiftReport"` for the fallback
Desktop. For redirected paths, supply `"-Pshiftreport.apkExportDir=/your/Desktop/ShiftReport"`.

## Shared debug signing

Android normally creates each account's `~/.android/debug.keystore`; shared groups
or caches do not align certificates. Updates need the same application ID and a
compatible signer. [Official Android signing guidance](https://developer.android.com/studio/publish/app-signing).

This checkout selects a protected copy of the existing `mhmmd` key through ignored
`android/local.properties` (preserve its other settings):

```properties
debug.keystore=.signing/debug.keystore
```

A nonblank `SHIFTREPORT_DEBUG_KEYSTORE` overrides that property. Both accept absolute
or Android-root-relative paths; selected missing/unreadable keys fail rather than
silently changing identity. Without a selection, normal per-account signing applies.
Standard Android debug alias/password defaults remain; production uses separate inputs.

For another checkout/PC, copy the **existing matching key** once to a protected
ignored location; never overwrite/regenerate it to repair an update. Keep a protected
backup outside Git. Current `.signing` directory is `mhmmd:developer` mode 2750,
key 0640, with inherited ACLs removed; `mhmmd`/`work` need active trusted-group
membership and parent traversal. Restrict only the key location, not entire homes/caches.
Restart Studio after environment/group changes. Read-only checks from `android/`:

```bash
namei -l .signing/debug.keystore
getfacl -cp .signing .signing/debug.keystore
test -r .signing/debug.keystore
keytool -list -keystore .signing/debug.keystore -alias androiddebugkey -storepass android
/path/to/sdk/build-tools/VERSION/apksigner verify --print-certs /path/to/app-debug.apk
```

Compare SHA-256 certificates with the original installed build's APK. Existing local
APKs match the selected key; `mhmmd` access passed. `work` membership/path checks
passed, but its login-level check requires authentication and remains unverified,
as does a new build/device update. Clean/cache deletion/version changes cannot
repair different signers; recover the installed signer before any data-losing reinstall.

## Production release signing

Release has no debug-key fallback. Supply a private production key through ignored
`android/keystore.properties` or environment (nonblank environment takes precedence):

| Property | Environment |
|---|---|
| `storeFile` | `SHIFTREPORT_KEYSTORE_FILE` |
| `storePassword` | `SHIFTREPORT_KEYSTORE_PASSWORD` |
| `keyAlias` | `SHIFTREPORT_KEY_ALIAS` |
| `keyPassword` | `SHIFTREPORT_KEY_PASSWORD` |

Paths are absolute or relative to `android/`. Keep key/configuration/passwords out
of Git, logs and command histories; use protected local/CI secret storage. No
production key/artifact is verified. Missing inputs do not produce a distributable
signed release. Updating installations with another certificate requires the owner's
deliberate distribution decision; a debug key is unsuitable for production.

## Automatic app patch and version-code increments

The POSIX launcher recognizes normal assemble/build/bundle/package/export task names,
including `:app:` prefixes. Each packaging **invocation** atomically reserves one
patch/code pair in [app-version.properties](../../android/gradle/app-version.properties)
using [next_app_version.py](../../android/gradle/next_app_version.py). Both variants in
one invocation share it; failures/cancellation consume it. Do not reset gaps.
The helper locks the parent directory and atomically replaces metadata, avoiding a
private account-owned lock file; concurrent callers receive their own reserved pair.

Version names use `major.minor.patch.YYYYMMDD` with the build-start date in
Asia/Damascus from the timezone database; major/minor remain, patch/code increase.
Debug adds `-debug`. New flavor task names would need launcher recognition.

| Control | Effect |
|---|---|
| `-Pshiftreport.autoVersion=false` | No reservation; use recorded version |
| Both `-Pshiftreport.versionCode=N` and `-Pshiftreport.versionName=X.Y.Z.YYYYMMDD` | Explicit owner-managed pair; no counter advance; supplying only one fails |
| Help/version/listing, `--dry-run` / `-m` | No reservation |

Synchronize the tracked counter across PCs/checkouts before packaging; independent
offline copies cannot coordinate globally unique codes. Builds do not auto-commit.
Changed version properties can normally invalidate the configuration cache.

## Overrides and Android Studio

Studio/direct Gradle and Windows `gradlew.bat` do not execute POSIX launcher defaults
or reserve versions; they use the recorded counter. Configure their JDK/user home,
seed environment and output properties separately. Without `shiftreport.buildRoot`,
standard `build/` paths apply. App assemble/export tasks still copy APKs, defaulting
to Java user-home `Desktop/ShiftReport`; only the POSIX launcher discovers XDG Desktop.

## Reuse these preferences in future Android projects and PCs

Carry the [launcher](../../android/gradlew), [root build mapping](../../android/build.gradle.kts),
[app version/signing/artifact export](../../android/app/build.gradle.kts), counter/helper
and [pinned wrapper settings](../../android/gradle/wrapper/gradle-wrapper.properties)
together. Keep custom blocks when regenerating a wrapper. Adapt the project slug,
property prefix, cache/output/export paths and APK prefix; isolate concurrent checkouts.
Preserve the destination project's app identity, version format, toolchain and signing.

Preflight Python/Java/SDK/pinned distribution, group access and Desktop on a new PC;
`/opt/android-shared` is optional. Transfer the current counter and protected matching
key separately; never copy whole private caches or connection secrets. Packaging,
installation and distribution still require their own authorization.

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

Historical permission/import/Material opt-in/export failures were corrected; they
are not an active checklist. Successful-build evidence and subsequent unverified
changes have one home: [Current state](../status/current-state.md#verification-and-limits).
