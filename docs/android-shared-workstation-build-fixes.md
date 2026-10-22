# Android builds on a shared Linux workstation

This records the Gradle problems encountered on 20 October 2026 while building
this checkout as `work`, with `mhmmd` also using the same project. The fixes keep
the project's Gradle version unchanged and keep the everyday command simple.

## Daily commands

From `android/`, as either `work` or `mhmmd`:

```bash
./gradlew assembleDebug
```

For a release APK:

```bash
./gradlew assembleRelease
```

To build both with **one shared version reservation**:

```bash
./gradlew assembleDebug assembleRelease
```

The POSIX launcher handles cache selection, per-account generated directories,
app version reservation and the Desktop export destination. No `sudo` or extra
flags are needed for the normal commands. It prints the reserved version/code
before Gradle starts. Release now reads separate protected signing inputs; supply
production credentials as described under [Production release signing](#production-release-signing).
No production-signed release has been verified.

## Cached verification without packaging

For agent/source verification, use the existing toolchain and durable dependency
cache. Check Java, the pinned Gradle 8.7 installation, Android platform 34 and build
tools before starting. Do not replace them with a newly downloaded temporary SDK.
The normal wrapper still selects the existing cache; a shared daemon directory
owned by another account requires the current account's existing personal cache.

```bash
# From android/: compile production Kotlin/resources, without APK export or version reservation.
./gradlew --offline -Pshiftreport.autoVersion=false compileDebugKotlin
```

Use an existing Java installation compatible with the pinned Gradle/AGP. When a
sandbox needs separate generated output, pass a writable `--project-cache-dir`
and `-Pshiftreport.buildRoot=...`; reuse the durable dependency cache. Keep complete
logs in a local file and inspect concise failures. Recompile after a relevant fix;
stop after the relevant check passes.

If offline resolution reports a missing prerequisite, name its exact package/
version and request one-time installation into the normal system/shared cache.
Do not loop on downloads, install another toolchain in `/tmp`, change versions or
reserve app build numbers merely to verify edits. Builds do not automatically
authorize test-suite inspection, migrations, signed release packaging or publishing.
The daily assemble commands above retain their ordinary owner packaging behavior.

## Find the APK

Successful assemble tasks copy APKs into the current account's Desktop folder.
For example, using the first two reservations on 20 October 2026:

```text
Desktop/ShiftReport/debug/ShiftReport-v1.1.2.20261020-debug.apk
Desktop/ShiftReport/release/ShiftReport-v1.1.3.20261020-release.apk
```

These are illustrative names, not fixed versions. The patch, date and version
code are generated as described below. Project/version/variant naming keeps
these APKs separate from other projects and one another. Rebuilding an explicitly
fixed version/type refreshes its named copy. Split/unsigned suffixes are retained.
Linux shell builds use `xdg-user-dir DESKTOP` when available, falling back to
`$HOME/Desktop`. Each account gets its own Desktop copies.

Open the export folder on Linux:

```bash
xdg-open "$HOME/Desktop/ShiftReport"
```

That short command uses the usual Desktop path. If your Desktop is localized or
redirected, open its `ShiftReport` folder in the file manager; the launcher follows
`xdg-user-dir DESKTOP`. If discovery is unavailable or returns the home directory,
it uses `$HOME/Desktop`. Set `shiftreport.apkExportDir` to choose an exact root.

The original, generically named APK remains in the generated output directory:

```text
$HOME/.gradle/shiftreport-project-cache/build-output/app/outputs/apk/debug/app-debug.apk
$HOME/.gradle/shiftreport-project-cache/build-output/app/outputs/apk/release/app-release.apk
```

Both the original and Desktop copy require successful packaging. Export consumes
the packaged APK artifact; it does not run as a failure finalizer. APK naming does
not change application IDs or signing. Debug/release still share the existing
ShiftReport application identity, so installation/update behavior is separate
from keeping output filenames distinct.

## Problems and fixes

| Symptom | Cause found | Implemented fix |
| --- | --- | --- |
| `Downloading ... gradle-8.7-bin.zip` despite Gradle already being installed | `./gradlew` uses the project's pinned distribution and selected user cache, not an arbitrary Gradle installation in `/opt` | Reuse the writable shared cache containing Gradle 8.7 |
| Download continued after adding automatic shared-cache selection | The environment already exported `GRADLE_USER_HOME=/home/work/.gradle`; the first implementation preserved every existing value | Treat the ordinary `$HOME/.gradle` value like an unset value; still preserve custom cache locations |
| Failure creating `buildfingerprint...tmp` under `android/.gradle/configuration-cache/...` | The existing cache directory was owned by `nobody` with owner-only directory access | Give each account its own project cache under its home |
| `packageDebugResources` and `mergeDebugResources` could not delete generated files | Resource intermediates under shared `android/app/build` were owned by another account and lacked the required write access | Give each account its own generated build output as well |
| Early `java.io.IOException: Permission denied` after version reservation | Shared cache root was writable, but `daemon/8.7` belonged to the other account with mode `0700` | Check access to existing daemon directories before selecting the shared cache; otherwise keep the personal cache. The reporting account already has Gradle 8.7 installed there |
| `exportDebugApk` failed configuration-cache storage with a Gradle script-object reference | The APK rename callback read a script-level value at execution time | Use built-in regex rename strings and resolve the destination during task configuration, leaving configuration caching enabled |
| `compileDebugKotlin` rejected the appearance/font FilterChips as experimental API usage | The new appearance section lacked a Material opt-in | Added `ExperimentalMaterial3Api` to the section; no dependency or toolchain changes |

### 1. Installed Gradle and the wrapper are different things

The project requests **Gradle 8.7**, with its existing distribution checksum.
Inspection found:

- Gradle **8.13** under `/opt/android-shared/gradle/gradle-8.13`.
- An extracted **8.7** distribution in `/opt/android-shared/.gradle/wrapper/dists/`.
- A partial **8.7** download in `work`'s original home cache.
- The owner confirmed **8.7** in `mhmmd`'s personal wrapper cache using a privileged
  read-only search; that home directory was inaccessible to the agent directly.

Installing a different Gradle version does not satisfy the wrapper's request for
8.7. Changing the project to 8.13 was unnecessary: the matching 8.7 distribution
was already available in the shared cache.

The shell launcher uses `/opt/android-shared/.gradle` when its root and existing
daemon directories are readable, writable and traversable,
and `GRADLE_USER_HOME` is either unset or equals `$HOME/.gradle`. Otherwise it
retains the caller's cache selection or Gradle's normal per-user default.
It does not select `mhmmd`'s private cache for `work`.

This reuses available Gradle files; it does not promise an entirely offline build.
Missing Android plugins, libraries or SDK components may still require downloads.

### 2. A shared Gradle distribution does not fix project-cache ownership

The next failure named a temporary fingerprint file under:

```text
android/.gradle/configuration-cache/909bz6k22wjq32my2fs7hxyqe/
```

Inspection showed the directory as `drwx------+`, owned by `nobody`. The current
`work` account could not write there. Reassigning ownership was suggested as an
immediate repair, but transferring the same private cache between accounts would
not provide reliable shared use.

The durable fix is automatic, account-local project caches:

```text
$HOME/.gradle/shiftreport-project-cache
```

The launcher supplies `--project-cache-dir` unless the caller already supplied
that option. This leaves the old cache untouched and avoids its ownership problem.
Configuration caching remains enabled; Gradle/Kotlin/Android versions are unchanged.

### 3. Generated build output needs the same separation

After project-cache isolation, the user-supplied build log reached resource tasks
but failed to delete children of:

```text
android/app/build/intermediates/packaged_res/debug/packageDebugResources/
android/app/build/intermediates/merged_res/debug/mergeDebugResources/
```

Inspection again found existing directories owned by `nobody`, including a
`drwxr-xr-x+` resource directory. The build log's generic suggestion about open
files was not enough to identify the cause; filesystem permissions supplied the
concrete explanation here.

The POSIX launcher now supplies this Gradle property:

```text
-Pshiftreport.buildRoot=$HOME/.gradle/shiftreport-project-cache/build-output
```

The root build script maps each project's generated directory under that root:

| Project | Generated directory |
| --- | --- |
| Root project | `$HOME/.gradle/shiftreport-project-cache/build-output/root` |
| Android app | `$HOME/.gradle/shiftreport-project-cache/build-output/app` |

This isolates intermediate resources, Kotlin output and APKs between accounts.
Old shared output is neither deleted nor given wider permissions. No blanket
permission changes or building as root are part of the fix.

### 4. APK export must not capture a Gradle script

A later owner-run build completed its task execution but could not store the
configuration cache for `:app:exportDebugApk`. The custom filename callback
referenced a top-level Kotlin script property, retaining the Gradle script object.

The export now uses Gradle's built-in regular-expression rename rule with plain
pattern/replacement strings, and resolves the destination during configuration.
This preserves project/version/variant naming without retaining that callback.
The packaged-APK artifact dependency and separate variant folders are unchanged.
Configuration caching stays enabled; no Gradle/plugin upgrade was used as a fix.

See Gradle's [configuration-cache requirements](https://docs.gradle.org/current/userguide/configuration_cache_requirements.html)
and [file-renaming documentation](https://docs.gradle.org/current/userguide/working_with_files.html).
The original version-specific documentation URL in the error was inaccessible to
the browsing tool; these official pages explain the script-reference restriction
and built-in regex alternative. The implemented task still needs an owner-run
build to verify storage/reuse on this project's Gradle 8.7.

### 5. Kotlin compilation and informational build messages

The owner-run build subsequently reached `compileDebugKotlin` and reported two
experimental API errors in `SettingsAppearanceSection.kt`. That section now imports
and opts into `ExperimentalMaterial3Api`, alongside its existing layout opt-in.
This was a source fix; no Material/Compose/Kotlin versions were changed.

Other messages seen in the supplied logs have different meanings:

- **Configuration cannot be reused because Gradle properties changed:** expected
  after a new auto-reserved version or changed launcher properties. Gradle can
  calculate a new configuration; this message alone is not a failure.
- **Unable to strip `libsqlcipher.so`, packaging it as-is:** the shown build
  continued past this message. It was not the fatal error reported in that log;
  the SQLCipher library was not removed or replaced as a workaround.
- **Deprecated features incompatible with Gradle 9.0:** a warning in the supplied
  Gradle 8.7 output. This work did not upgrade Gradle or claim to eliminate every
  deprecation. Diagnose the actual failing task rather than assuming this warning
  is the cause.

## Automatic app patch and version-code increments

Keep using the normal commands:

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

The POSIX launcher now reserves a new patch/version-code pair before a packaging
invocation. With the initial counter in this checkout:

| Invocation | App version name, before the existing debug suffix | Android version code |
| --- | --- | --- |
| Starting counter | `1.1.1.20261007` | `2` |
| Next debug build attempt | `1.1.2.20261020` | `3` |
| Next release build attempt | `1.1.3.20261020` | `4` |
| Following attempt starting the next day | `1.1.4.20261021` | `5` |

The debug/release examples start on 20 October 2026; the final row starts the next day. The third numeric
component (patch) increments; major/minor stay unchanged and the final `YYYYMMDD`
component is regenerated from the build-start date in **Asia/Damascus**. A build
on the next calendar day gets that day's date; a same-day rebuild keeps the date
but still advances the patch and version code. The previous date is never reused
as a fixed suffix. The POSIX helper uses the PC's timezone database rather than
assuming a permanent UTC offset. Debug retains its existing `-debug` suffix.
APK filenames use the newly reserved version, for example
`ShiftReport-v1.1.2.20261020-debug.apk`.

The shared source counter is
[`android/gradle/app-version.properties`](../android/gradle/app-version.properties).
[`next_app_version.py`](../android/gradle/next_app_version.py) locks its parent
directory, validates the counter and atomically replaces the metadata before
returning a version. Directory locking avoids an account-owned private lock file;
atomic replacement uses the shared source directory's existing permissions.
The helper does not change permissions on other files or directories. Keep Python
3 available on machines using this POSIX launcher.

The launcher passes the reserved pair as Gradle properties, so concurrent accounts
cannot accidentally configure the same reserved version even if the file advances
again before their build starts. Both debug and release tasks in a single command
share one reservation. Separate packaging commands reserve separate versions.
Failed/cancelled attempts still consume their reservation; numbers may have gaps
and are never rolled back automatically.

Help/version/listing and dry-run commands do not increment the counter. The
launcher recognizes this project's normal assemble/build/bundle/package/export
task names, including `:app:`-qualified tasks. New flavor task names need equivalent
recognition if flavors are added later. A deliberately fixed-version build may use
`-Pshiftreport.autoVersion=false`, or supply **both**
`-Pshiftreport.versionCode=...` and `-Pshiftreport.versionName=...`. Explicit pairs
must be managed by the owner; they are not reserved by the counter.

Per-build version changes are configuration inputs. A message saying that Gradle
properties changed and the configuration cache cannot be reused is expected for
a newly versioned build; it is different from a cache-serialization failure.
Incremental task/build caches can still operate. No cache warnings are suppressed.

This automation runs in the POSIX `gradlew` launcher. Android Studio, direct Gradle
commands and unchanged Windows `gradlew.bat` use the most recently recorded counter
without reserving another version. Do not claim they auto-increment. Carry the
counter file to other PCs/checkouts and synchronize its latest value before building
or publishing there; isolated copies cannot coordinate unique versions offline.
The counter is tracked source metadata and will appear in Git changes after builds;
include its intended value when saving/sharing a project snapshot. No commit is
automatically created. These are app versions only: database/interchange schemas,
Gradle, Kotlin, plugins and SDK versions remain unchanged.

## What is shared, and what is private

| Layer | Default for this shell launcher | Purpose |
| --- | --- | --- |
| Gradle user home | `/opt/android-shared/.gradle`, when its daemon registries are also accessible | Reuse the installed distribution and cached dependencies |
| Project cache | Each account's home | Isolate configuration and project-state files |
| Generated output | Each account's home | Isolate intermediates and APKs |
| Project source | The existing shared checkout | Both accounts edit/build the same source |

The shared Gradle user home still requires appropriate filesystem permissions.
These changes address the observed project-cache and build-output failures; they
do not grant access to another account's private files.

## Overrides and Android Studio

The simple command is the default. Deliberate custom settings remain possible:

- A `GRADLE_USER_HOME` different from `$HOME/.gradle` keeps its selected cache.
- An explicit `--project-cache-dir` keeps its selected project cache.
- `-Pshiftreport.buildRoot=/your/path` selects a different generated-output root.
- `-Pshiftreport.apkExportDir=/your/folder` selects a different APK export folder;
  each variant gets its own subfolder there.

These automatic defaults belong to the POSIX `android/gradlew` launcher. Android
Studio and Windows `gradlew.bat` do not automatically inherit its shell logic.
Callers without `shiftreport.buildRoot` retain the standard Gradle build directories.
The app's assemble tasks also export APKs for IDE/direct Gradle callers, defaulting
to `Desktop/ShiftReport` under Java's user home when no export property is supplied.
The shell's XDG Desktop discovery does not apply to those callers.

## Complete settings reference

Normal builds use the daily commands above. These are the actual controls for
custom machines, automation or intentionally fixed-version builds:

| Control | Current behavior |
| --- | --- |
| `GRADLE_USER_HOME` | Shared `/opt/android-shared/.gradle` replaces an unset/normal-home value when its root and existing daemon registries are accessible; custom locations are preserved |
| `--project-cache-dir /path` | Overrides the account-local project cache; accepts paths containing spaces when quoted |
| `-Pshiftreport.buildRoot=/path` | Overrides generated output, with separate `root`/`app` subdirectories |
| `-Pshiftreport.apkExportDir=/path` | Overrides Desktop export root; each variant gets its own subdirectory |
| `-Pshiftreport.autoVersion=false` | Skips reservation; uses the recorded or explicitly supplied app version |
| `-Pshiftreport.versionCode=N` and `-Pshiftreport.versionName=X.Y.Z.YYYYMMDD` | Explicit pair; supply both or the launcher stops before building; does not advance the shared counter |
| `--dry-run` / `-m`, help/version/listing | No reservation; no new version is consumed |
| `gradle/app-version.properties` | Last reserved code/name; keep and synchronize this file between PCs |
| `gradle/next_app_version.py` | POSIX/Python 3 atomic counter and Asia/Damascus build-date generator |

The counter advances once per packaging **invocation**, not once per individual
Gradle task and not once per successful APK. Cancellation/failure leaves the
reservation consumed. Help, dry runs and explicit opt-out retain the current
counter. Do not reset the counter just because a build failed.

To carry the setup to another PC, include the custom launcher blocks, root/app
build logic, counter and helper together. On that PC verify Python 3, Java/SDK
setup and the chosen Desktop path. Share the latest counter before packaging;
separate offline copies cannot allocate globally unique codes automatically.
Do not copy another account's private Gradle cache or signing/connection secrets.

## Reuse these preferences in future Android projects and PCs

These are the owner's preferred build conveniences. They are implemented in this
project, but do not automatically apply to another repository or a fresh PC.
Carry over the launcher/build-script logic deliberately, adapting project names
and checking it against the target project's existing toolchain.

### Preferred everyday behavior

1. Keep the commands simple, from that project's Android/Gradle root:

   ```bash
   ./gradlew assembleDebug
   ./gradlew assembleRelease
   ```

2. Use the project's pinned Gradle version and checksum. Reuse an installed
   matching distribution when available; do not switch Gradle, Android plugins,
   Kotlin or SDK versions just to match whatever is installed on the PC.
3. Share the Gradle user-home cache only where it is deliberately configured and
   writable. Otherwise use the account's normal cache. Keep explicit custom paths.
4. Keep project caches and generated output separate **per account and per
   project**. Give different checkouts separate locations if they may build at
   the same time. Do not reuse ShiftReport's cache directory for another app.
5. Export successfully packaged APKs to the current user's Desktop, in a folder
   named for the project, with a separate subfolder for each variant.
6. Name the files with the project, version and build variant; preserve any
   split/unsigned suffix. Rebuilding the same version/variant may replace its
   previous copy, but another project's APK must have a different folder/prefix.
7. Reserve monotonically increasing app patch/version-code pairs for normal
   debug/release packaging, using a project-local atomic counter. Preserve the
   project's existing version format and signing, skip help/dry runs, and document
   consumed failed-attempt versions and which launchers perform reservation.
8. Preserve the original generated APK as well as the convenient Desktop copy.
   Do not copy old APKs after a failed build and present them as new output.
9. Keep these preferences in the project's documentation, including the actual
   APK location and the verification that has really been performed.

Release packaging still uses that project's existing signing configuration.
These conveniences do not create a production signing key or make a debug-signed
release suitable for distribution.

### Adapt the names before copying the implementation

Example: a new project named **MyWard**, using the slug **myward**:

| Setting | This project | Adapted example |
| --- | --- | --- |
| Project cache | `$HOME/.gradle/shiftreport-project-cache` | `$HOME/.gradle/myward-project-cache` |
| Generated output | `$HOME/.gradle/shiftreport-project-cache/build-output` | `$HOME/.gradle/myward-project-cache/build-output` |
| Gradle property prefix | `shiftreport` | `myward` |
| Desktop folder | `Desktop/ShiftReport` | `Desktop/MyWard` |
| APK prefix | `ShiftReport-v` | `MyWard-v` |
| Debug example | `ShiftReport-v1.1.1.20261007-debug.apk` | `MyWard-v2.4-debug.apk` |
| Release example | `ShiftReport-v1.1.1.20261007-release.apk` | `MyWard-v2.4-release.apk` |

The example version `2.4` is illustrative; read the target app's real version
rather than changing its version to match this example. Keep property names
consistent in the launcher and Gradle scripts. For multiple checkouts, append a
checkout-specific slug to the cache/output paths and, when needed, the export
folder. Application IDs are separate: renaming an APK does not change which
installed app it updates. Installing debug/release side by side requires a
separate, explicitly chosen application-ID configuration.

### What to carry into another repository

1. In its POSIX `gradlew`, adapt the small custom blocks before the generated
   wrapper body: eligible shared-cache selection, per-project/per-account
   `--project-cache-dir`, the build-root property and Desktop export property.
   Keep the target repository's generated wrapper body and wrapper version.
2. Adapt the account-local build-directory mapping from this project's
   [root build script](../android/build.gradle.kts). This moves generated output
   only; it does not move source files or alter the app's identity.
3. Adapt the APK-export block and `SingleArtifact` import from the
   [app build script](../android/app/build.gradle.kts). It uses the packaged APK
   artifact and wires the export into the variant's assemble task. Keep any
   module/flavor/split naming needed by the new project; don't collapse several
   APKs to the same filename. This block is for an Android application module.
4. Change the slug, property prefix, Desktop folder and filename prefix together
   using the table above. Do not copy this project's dependencies, signing setup,
   application ID or SDK/toolchain versions into the new project by assumption.
5. If adopting automatic versions, adapt the counter/helper and the launcher's
   packaging-task detection too. Use the target app's actual starting code/name;
   synchronize that project counter between PCs. Do not reuse ShiftReport's counter
   or assume Windows/IDE launches execute the POSIX helper.
6. Check shell syntax, quoting with spaces, explicit overrides and filename
   separation. When the owner authorizes a build, verify the actual task wiring,
   artifact destination and APK names with that project's Gradle/Android plugin.
   Source checks alone do not establish that a build works.

This is a reuse recipe for the implementation in this repository, not a promise
that its Gradle API calls work unchanged with every Android plugin version.

### Setting up another PC

- There is no requirement to reproduce `/opt/android-shared`. When it is absent
  or not writable, the launcher leaves Gradle's normal/custom user-home selection
  in place. A first build may download the project's pinned Gradle distribution
  and missing dependencies.
- On a shared Linux PC, a deliberately prepared shared cache can save downloads.
  Do not point one account at another account's private home or rely on repeated
  `sudo chown` repairs. Keep account/project caches and output under each user's
  home, even when a distribution cache is shared.
- On Linux, discover Desktop with `xdg-user-dir DESKTOP`; fall back to
  `$HOME/Desktop`. Quote paths because project and Desktop names may contain spaces.
- If the Desktop lives somewhere else, set the export property for that project.
  For this repository, for example:

  ```bash
  ./gradlew assembleDebug "-Pshiftreport.apkExportDir=/your/Desktop/ShiftReport"
  ```

  The build adds the variant subfolder. Adapt the property prefix in another app.
- Windows `gradlew.bat` and Android Studio do not execute the POSIX launcher's
  custom blocks. Configure equivalent account-local cache/output paths there,
  and use the export property for redirected/OneDrive Desktop locations. The app
  export block's default is Java's user home plus `Desktop/<ProjectName>`; it
  does not discover every platform's redirected Desktop automatically.
- After regenerating/upgrading a wrapper, check that its small custom launcher
  blocks are still present. Preserve the target project's intended wrapper
  version rather than replacing it with this repository's wrapper.

### A reusable request for the next project

> Keep the normal `./gradlew assembleDebug` and `./gradlew assembleRelease`
> commands. Use the project's current pinned toolchain. Reuse a matching local
> Gradle cache when available, with safe per-user fallback and explicit overrides.
> Isolate project caches and build output by account and project. After successful
> packaging, copy uniquely named project/version/variant APKs into this user's
> Desktop/project folder, respecting the configured Desktop location. Preserve
> signing and application IDs. Automatically reserve increasing app patch/version
> codes for debug/release builds, retaining a synchronized project counter and the
> version format with a freshly generated build-date suffix in the project's
> timezone. Document launcher coverage, failed-attempt reservations,
> the paths and actual checks; do not run
> builds or change toolchain versions without my explicit authorization.

## Files implementing the fixes

- [POSIX launcher](../android/gradlew): shared-cache selection and per-account
  project-cache/build-root arguments.
- [Root build script](../android/build.gradle.kts): maps the supplied build root
  to project-specific generated directories.
- [App build script](../android/app/build.gradle.kts): app version providers and
  configuration-cache-safe, versioned variant APK export.
- [App-version counter](../android/gradle/app-version.properties) and
  [reservation helper](../android/gradle/next_app_version.py).
- [Wrapper properties](../android/gradle/wrapper/gradle-wrapper.properties): the
  existing Gradle 8.7 version and checksum remain unchanged.
- [General troubleshooting](troubleshooting.md): current cache/output behavior.

## Verification and remaining limits

For the owner's early 20 October `Permission denied` failure, read-only checks
confirmed `/opt/android-shared/.gradle/daemon/8.7` had mode `0700`, owned by the
other account, despite a writable shared cache root. The reporting account's own
cache already contains the executable Gradle 8.7 distribution. Shell syntax and
six disposable selection probes passed accessible/inaccessible daemon directories,
unset/ordinary-home settings and preserved custom overrides. No permissions were
changed on either real cache, and Gradle was not executed. The short supplied log
contains no stack trace. The owner's subsequent `1.1.4.20261020` debug build
progressed to Kotlin compilation and stored its configuration cache, confirming
progress past the permission failure. It stopped on a missing `AppOutlinedButton`
import in `WardScreen`, since corrected in source. Its consumed app-version
reservation is preserved; the corrected source has not been rebuilt.


The agent performed shell syntax checks, disposable argument-selection probes,
source inspection and Git whitespace checks. Later APK-export checks covered
Desktop discovery/fallback/custom paths and distinct versioned variant filenames;
the revised Gradle export task itself remains unexecuted. Temporary-counter probes
also covered debug/release/both-task reservations, help/dry-run/opt-out skips,
explicit pairs, invalid input and six concurrent reservations. Date probes covered
replacement of an old suffix, same-day patch increments, next-day rollover,
Asia/Damascus midnight boundaries and restoration of the helper's timezone state. The project's real
counter was not advanced by these checks. The probes covered unset and
pre-exported normal-home caches, custom overrides, unavailable shared caches,
per-account project/output arguments and paths containing spaces.

The user supplied real build logs showing the download, configuration-cache and
resource-output failures, followed by a build reaching Kotlin compilation and
reporting a missing Material API opt-in (since corrected in source), then the
APK export configuration-cache error (also corrected in source).
**No successful APK build after these changes has been recorded.** The agent did not launch Gradle, compile, package or
run a test suite. The next owner-run `./gradlew assembleDebug` is needed to verify
that the build proceeds and reveal any later, independent source errors.

## Production release signing

Release selects its own signing configuration and no longer falls back to the
public Android debug key. Supply an existing private production keystore through
ignored `android/keystore.properties` or CI environment variables:

| Local property | Environment variable (takes precedence) |
|---|---|
| `storeFile` | `SHIFTREPORT_KEYSTORE_FILE` |
| `storePassword` | `SHIFTREPORT_KEYSTORE_PASSWORD` |
| `keyAlias` | `SHIFTREPORT_KEY_ALIAS` |
| `keyPassword` | `SHIFTREPORT_KEY_PASSWORD` |

The file path can be absolute or relative to `android/`. Keep the keystore,
passwords and properties outside tracked source; the local file and common signing
extensions are already ignored. Do not send signing passwords through build logs.
Debug retains its existing development signing. No key was created or inspected,
no build ran, and no production-signed artifact has been verified. Without complete
valid inputs production signing is unavailable; do not treat an unsigned artifact
as a distributable signed release. Existing installations signed with another
certificate need a deliberate distribution decision by the owner.
