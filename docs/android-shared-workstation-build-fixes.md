# Android builds on a shared Linux workstation

This records the Gradle problems encountered on 20 October 2026 while building
this checkout as `work`, with `mhmmd` also using the same project. The fixes keep
the project's Gradle version unchanged and keep the everyday command simple.

## The command to use now

From `android/`, as either account:

```bash
./gradlew assembleDebug
```

The POSIX launcher now selects the appropriate cache and output directories.
No `sudo` or extra environment variable is needed for the normal command.

Successful builds now copy APKs into the current account's Desktop folder:

```text
Desktop/ShiftReport/debug/ShiftReport-v1.1.1.20261007-debug.apk
Desktop/ShiftReport/release/ShiftReport-v1.1.1.20261007-release.apk
```

The version comes from the existing app configuration. Project/version/build-type
names keep these copies separate from other projects and from one another;
rebuilding the same version/type refreshes its named copy. Split/unsigned suffixes,
if present in the packaged filename, are retained. Linux shell builds use
`xdg-user-dir DESKTOP` when available, falling back to `$HOME/Desktop`.

The original debug APK remains in the account-local generated directory:

```text
$HOME/.gradle/shiftreport-project-cache/build-output/app/outputs/apk/debug/app-debug.apk
```

Both the original and Desktop copy require successful packaging. Export consumes
the packaged APK artifact; it does not run as a failure finalizer. APK naming does
not change Android application IDs, signing, versions or installation/update behavior.
Debug and release still use the existing ShiftReport application identity.

## Problems and fixes

| Symptom | Cause found | Implemented fix |
| --- | --- | --- |
| `Downloading ... gradle-8.7-bin.zip` despite Gradle already being installed | `./gradlew` uses the project's pinned distribution and selected user cache, not an arbitrary Gradle installation in `/opt` | Reuse the writable shared cache containing Gradle 8.7 |
| Download continued after adding automatic shared-cache selection | The environment already exported `GRADLE_USER_HOME=/home/work/.gradle`; the first implementation preserved every existing value | Treat the ordinary `$HOME/.gradle` value like an unset value; still preserve custom cache locations |
| Failure creating `buildfingerprint...tmp` under `android/.gradle/configuration-cache/...` | The existing cache directory was owned by `nobody` with owner-only directory access | Give each account its own project cache under its home |
| `packageDebugResources` and `mergeDebugResources` could not delete generated files | Resource intermediates under shared `android/app/build` were owned by another account and lacked the required write access | Give each account its own generated build output as well |

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

The shell launcher uses `/opt/android-shared/.gradle` when it exists, is writable,
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

## What is shared, and what is private

| Layer | Default for this shell launcher | Purpose |
| --- | --- | --- |
| Gradle user home | `/opt/android-shared/.gradle`, when eligible | Reuse the installed distribution and cached dependencies |
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
7. Preserve the original generated APK as well as the convenient Desktop copy.
   Do not copy old APKs after a failed build and present them as new output.
8. Keep these preferences in the project's documentation, including the actual
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
5. Check shell syntax, quoting with spaces, explicit overrides and filename
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
> signing and application IDs. Document the paths and actual checks; do not run
> builds or change toolchain versions without my explicit authorization.

## Files implementing the fixes

- [POSIX launcher](../android/gradlew): shared-cache selection and per-account
  project-cache/build-root arguments.
- [Root build script](../android/build.gradle.kts): maps the supplied build root
  to project-specific generated directories.
- [App build script](../android/app/build.gradle.kts): versioned variant APK export.
- [Wrapper properties](../android/gradle/wrapper/gradle-wrapper.properties): the
  existing Gradle 8.7 version and checksum remain unchanged.
- [General troubleshooting](troubleshooting.md): current cache/output behavior.

## Verification and remaining limits

The agent performed shell syntax checks, disposable argument-selection probes,
source inspection and Git whitespace checks. Later APK-export checks covered
Desktop discovery/fallback/custom paths and distinct versioned variant filenames;
the Gradle export task itself remains unexecuted. The probes covered unset and
pre-exported normal-home caches, custom overrides, unavailable shared caches,
per-account project/output arguments and paths containing spaces.

The user supplied real build logs showing the download, configuration-cache and
resource-output failures, followed by a build reaching Kotlin compilation and
reporting a missing Material API opt-in (since corrected in source).
**No successful APK build after these changes has been recorded.** The agent did not launch Gradle, compile, package or
run a test suite. The next owner-run `./gradlew assembleDebug` is needed to verify
that the build proceeds and reveal any later, independent source errors.
